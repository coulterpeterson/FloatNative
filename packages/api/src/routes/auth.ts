import { Hono, Context } from 'hono';
import { eq } from 'drizzle-orm';
import { users, deviceSessions } from '../db/schema';
import { validateFloatplaneCookie, validateFloatplaneToken, validateFloatplaneTokenLocally, extractDPoPJKT, FloatplaneAPIError } from '../services/floatplane';
import { generateAPIKey } from '../utils/auth';
import { getOrCreateWatchLater } from '../services/playlist';
import type { Env } from '../db';
import type { DrizzleDB } from '../db';
import { logError, logWarn } from '../utils/logger';
import { rateLimit, byIP } from '../middleware/rate-limit';
import { authenticateAPIKey, type AuthContext } from '../middleware/auth';

const auth = new Hono<{ Bindings: Env; Variables: { db: DrizzleDB } }>();

/**
 * POST /auth/login
 * Login a user by validating their Floatplane OAuth access token
 * Creates a device-specific API key based on the DPoP key thumbprint
 * Rate limited: 20 req/min by IP
 */
auth.post('/login', rateLimit('AUTH_STRICT_LIMITER', byIP), async (c) => {
  try {
    const db = c.get('db');
    const body = await c.req.json();
    const { access_token, dpop_proof, device_info } = body;

    if (!access_token || typeof access_token !== 'string') {
      return c.json(
        {
          error: 'Bad Request',
          message: 'Missing or invalid access_token in request body',
        },
        400
      );
    }

    if (!dpop_proof || typeof dpop_proof !== 'string') {
      return c.json(
        {
          error: 'Bad Request',
          message: 'Missing or invalid dpop_proof in request body',
        },
        400
      );
    }

    // Extract DPoP JKT to identify the device
    let dpopJkt: string;
    try {
      dpopJkt = extractDPoPJKT(dpop_proof);
    } catch (error) {
      logError(c, error, 'Failed to extract DPoP JKT');
      return c.json(
        {
          error: 'Bad Request',
          message: 'Invalid DPoP proof format',
        },
        400
      );
    }

    // Validate Floatplane token locally by decoding JWT
    // We can't call Floatplane's API because the token is DPoP-bound to the device
    // and we don't have the device's private key to generate valid DPoP proofs
    let floatplaneUserId: string;
    try {
      floatplaneUserId = validateFloatplaneTokenLocally(access_token);
    } catch (error) {
      if (error instanceof FloatplaneAPIError) {
        if (error.statusCode === 401) {
          return c.json(
            {
              error: 'Unauthorized',
              message: 'Invalid or expired Floatplane token',
            },
            401
          );
        }
        logError(c, error, 'JWT validation error');
        return c.json(
          {
            error: 'Bad Request',
            message: 'Failed to validate Floatplane token',
          },
          400
        );
      }
      throw error;
    }

    // Ensure user exists in users table
    const existingUser = await db
      .select()
      .from(users)
      .where(eq(users.floatplane_user_id, floatplaneUserId))
      .limit(1);

    let isNewUser = false;

    if (existingUser.length === 0) {
      // Create new user (with placeholder API key)
      isNewUser = true;
      const placeholderApiKey = generateAPIKey();
      await db.insert(users).values({
        floatplane_user_id: floatplaneUserId,
        api_key: placeholderApiKey,
      });

      // Create Watch Later playlist for new users
      try {
        await getOrCreateWatchLater(db, floatplaneUserId);
      } catch (error) {
        logWarn(c, error, 'Failed to create Watch Later playlist during login');
      }
    }

    // Check if this device already has a session
    const existingSession = await db
      .select()
      .from(deviceSessions)
      .where(eq(deviceSessions.dpop_jkt, dpopJkt))
      .limit(1);

    let apiKey: string;

    if (existingSession.length > 0) {
      // Device already has an API key
      apiKey = existingSession[0].api_key;

      // Update last accessed
      await db
        .update(deviceSessions)
        .set({ last_accessed_at: new Date().toISOString() })
        .where(eq(deviceSessions.dpop_jkt, dpopJkt));
    } else {
      // Create new device session
      apiKey = generateAPIKey();
      await db.insert(deviceSessions).values({
        id: crypto.randomUUID(),
        floatplane_user_id: floatplaneUserId,
        api_key: apiKey,
        dpop_jkt: dpopJkt,
        device_info: device_info || null,
        created_at: new Date().toISOString(),
        last_accessed_at: new Date().toISOString(),
      });
    }

    return c.json({
      api_key: apiKey,
      floatplane_user_id: floatplaneUserId,
      message: isNewUser ? 'User registered successfully' : 'User logged in successfully',
    });
  } catch (error) {
    logError(c, error, 'Login error');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to login user',
      },
      500
    );
  }
});

/**
 * POST /auth/register
 * Register or login a user by validating their Floatplane sails.sid cookie
 * Rate limited: 20 req/min by IP
 */
auth.post('/register', rateLimit('AUTH_STRICT_LIMITER', byIP), async (c) => {
  try {
    const db = c.get('db');
    const body = await c.req.json();
    const { sails_sid } = body;

    if (!sails_sid || typeof sails_sid !== 'string') {
      return c.json(
        {
          error: 'Bad Request',
          message: 'Missing or invalid sails_sid in request body',
        },
        400
      );
    }

    // Validate Floatplane cookie and get user ID
    let floatplaneUserId: string;
    try {
      floatplaneUserId = await validateFloatplaneCookie(sails_sid, c.env.FLOATPLANE_API_URL);
    } catch (error) {
      if (error instanceof FloatplaneAPIError) {
        if (error.statusCode === 401 || error.statusCode === 403) {
          return c.json(
            {
              error: 'Unauthorized',
              message: 'Invalid or expired Floatplane cookie',
            },
            401
          );
        }
        logError(c, error, 'Floatplane API error');
        return c.json(
          {
            error: 'Bad Gateway',
            message: 'Failed to validate Floatplane credentials',
          },
          502
        );
      }
      throw error;
    }

    // Get or create user
    const existingUser = await db
      .select()
      .from(users)
      .where(eq(users.floatplane_user_id, floatplaneUserId))
      .limit(1);

    let user;
    let isNewUser = false;

    if (existingUser.length > 0) {
      // User exists, update last_accessed_at
      user = existingUser[0];
      await db
        .update(users)
        .set({ last_accessed_at: new Date().toISOString() })
        .where(eq(users.floatplane_user_id, floatplaneUserId));
    } else {
      // Create new user
      isNewUser = true;
      const apiKey = generateAPIKey();
      await db.insert(users).values({
        floatplane_user_id: floatplaneUserId,
        api_key: apiKey,
      });

      // Fetch the newly created user
      const newUser = await db
        .select()
        .from(users)
        .where(eq(users.floatplane_user_id, floatplaneUserId))
        .limit(1);
      user = newUser[0];

      // Create Watch Later playlist for new users
      try {
        await getOrCreateWatchLater(db, floatplaneUserId);
      } catch (error) {
        logWarn(c, error, 'Failed to create Watch Later playlist during registration');
      }
    }

    return c.json({
      api_key: user.api_key,
      floatplane_user_id: user.floatplane_user_id,
      message: isNewUser ? 'User registered successfully' : 'User logged in successfully',
    });
  } catch (error) {
    logError(c, error, 'Registration error');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to register user',
      },
      500
    );
  }
});

/**
 * POST /auth/logout
 * Invalidate the user's API key.
 * Rate limited: 20 req/min by API key
 */
auth.post('/logout', authenticateAPIKey, async (c: Context<{ Bindings: Env } & AuthContext>) => {
  try {
    const db = c.get('db');
    const user = c.get('user');

    // Generate a new API key to invalidate the old one
    const newApiKey = generateAPIKey();

    await db
      .update(users)
      .set({
        api_key: newApiKey,
        // Optionally, update last_accessed_at to null or a logout timestamp
        // For now, keep it updated on access
      })
      .where(eq(users.floatplane_user_id, user.floatplane_user_id));

    return c.json({
      message: 'Logged out successfully. API key invalidated.',
    });
  } catch (error) {
    logError(c, error, 'Logout error');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to logout user',
      },
      500
    );
  }
});

export default auth;

