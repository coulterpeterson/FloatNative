import { Hono } from 'hono';
import { html } from 'hono/html';
import { randomUUID } from 'crypto';
import { eq } from 'drizzle-orm';
import { qrSessions, users } from '../db/schema';
import { validateFloatplaneCookie } from '../services/floatplane';
import { generateAPIKey } from '../utils/auth';
import { getOrCreateWatchLater } from '../services/playlist';
import type { Env } from '../db';
import type { DrizzleDB } from '../db';
import { logError, logWarn } from '../utils/logger';
import { rateLimit, byIP, byParam } from '../middleware/rate-limit';

const qrAuth = new Hono<{ Bindings: Env; Variables: { db: DrizzleDB } }>();

const QR_SESSION_EXPIRATION_MINUTES = 10;

/**
 * POST /auth/qr/generate
 * Generate a new QR session and return the login URL
 * Rate limited: 30 req/min by IP
 */
qrAuth.post('/generate', rateLimit('QR_GENERATE_LIMITER', byIP), async (c) => {
  try {
    const db = c.get('db');
    const body = await c.req.json();
    const { device_info } = body;

    const sessionId = randomUUID();
    const expiresAt = new Date();
    expiresAt.setMinutes(expiresAt.getMinutes() + QR_SESSION_EXPIRATION_MINUTES);

    await db.insert(qrSessions).values({
      id: sessionId,
      device_info: device_info || null,
      status: 'pending',
      expires_at: expiresAt.toISOString(),
    });

    const baseUrl = c.env.API_BASE_URL || 'http://localhost:8787';
    const loginUrl = `${baseUrl}/public/qr-login.html?session=${sessionId}`;

    return c.json(
      {
        session_id: sessionId,
        login_url: loginUrl,
        expires_at: expiresAt.toISOString(),
        expires_in_seconds: QR_SESSION_EXPIRATION_MINUTES * 60,
      },
      201
    );
  } catch (error) {
    logError(c, error, 'Error generating QR session');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to generate QR code session',
      },
      500
    );
  }
});

/**
 * POST /auth/qr/submit
 * Submit Floatplane token for a QR session
 * Rate limited: 20 req/min by IP
 */
qrAuth.post('/submit', rateLimit('AUTH_STRICT_LIMITER', byIP), async (c) => {
  try {
    const db = c.get('db');
    const body = await c.req.json();
    const { session_id, sails_sid } = body;

    if (!session_id || !sails_sid) {
      return c.json({ message: 'Missing required fields: session_id and sails_sid' }, 400);
    }

    // Find the QR session
    const sessions = await db.select().from(qrSessions).where(eq(qrSessions.id, session_id)).limit(1);

    if (sessions.length === 0) {
      return c.json({ message: 'QR session not found' }, 404);
    }

    const qrSession = sessions[0];

    // Check if session is still valid
    const now = new Date();
    const expiresAt = new Date(qrSession.expires_at);

    if (now > expiresAt) {
      return c.json({ message: 'QR session has expired. Please generate a new QR code.' }, 400);
    }

    if (qrSession.status !== 'pending') {
      return c.json({ message: 'QR session is no longer valid' }, 400);
    }

    // Validate the Floatplane token
    let floatplaneUserId: string;
    try {
      floatplaneUserId = await validateFloatplaneCookie(sails_sid, c.env.FLOATPLANE_API_URL);
    } catch (error) {
      logError(c, error, 'Invalid Floatplane token');
      return c.json({ message: 'Invalid Floatplane token. Please check your sails.sid cookie.' }, 401);
    }

    // Find or create user
    let existingUser = await db
      .select()
      .from(users)
      .where(eq(users.floatplane_user_id, floatplaneUserId))
      .limit(1);

    let apiKey: string;

    if (existingUser.length === 0) {
      // Create new user
      apiKey = generateAPIKey();
      await db.insert(users).values({
        floatplane_user_id: floatplaneUserId,
        api_key: apiKey,
      });

      // Create Watch Later playlist
      try {
        await getOrCreateWatchLater(db, floatplaneUserId);
      } catch (error) {
        logWarn(c, error, 'Failed to create Watch Later playlist');
      }
    } else {
      apiKey = existingUser[0].api_key;

      // Update last accessed
      await db
        .update(users)
        .set({ last_accessed_at: new Date().toISOString() })
        .where(eq(users.floatplane_user_id, floatplaneUserId));
    }

    // Update QR session to completed
    await db
      .update(qrSessions)
      .set({
        floatplane_user_id: floatplaneUserId,
        sails_sid: sails_sid,
        api_key: apiKey,
        status: 'completed',
        completed_at: new Date().toISOString(),
      })
      .where(eq(qrSessions.id, session_id));

    return c.json({
      message: 'Login successful! You can now close this tab.',
      success: true,
    });
  } catch (error) {
    logError(c, error, 'Error submitting QR token');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to process login',
      },
      500
    );
  }
});

/**
 * GET /auth/qr/poll/:sessionId
 * Poll for QR session completion
 * Rate limited: 120 req/min by session ID
 */
qrAuth.get('/poll/:sessionId', rateLimit('QR_POLL_LIMITER', byParam('sessionId')), async (c) => {
  try {
    const db = c.get('db');
    const sessionId = c.req.param('sessionId');

    const sessions = await db.select().from(qrSessions).where(eq(qrSessions.id, sessionId)).limit(1);

    if (sessions.length === 0) {
      return c.json({ message: 'QR session not found' }, 404);
    }

    const qrSession = sessions[0];
    const now = new Date();
    const expiresAt = new Date(qrSession.expires_at);

    if (now > expiresAt) {
      return c.json({
        status: 'expired',
        message: 'QR session has expired',
      });
    }

    if (qrSession.status === 'completed') {
      return c.json({
        status: 'completed',
        api_key: qrSession.api_key,
        sails_sid: qrSession.sails_sid,
        floatplane_user_id: qrSession.floatplane_user_id,
        message: 'Login completed successfully',
      });
    }

    const expiresInSeconds = Math.floor((expiresAt.getTime() - now.getTime()) / 1000);

    return c.json({
      status: 'pending',
      message: 'Waiting for login',
      expires_in_seconds: expiresInSeconds,
    });
  } catch (error) {
    logError(c, error, 'Error polling QR session');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to poll QR session',
      },
      500
    );
  }
});

export default qrAuth;
