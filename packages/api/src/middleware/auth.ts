import { Context, Next } from 'hono';
import { eq } from 'drizzle-orm';
import { users, deviceSessions, type User } from '../db/schema';
import { extractBearerToken } from '../utils/auth';
import type { Env } from '../db';
import type { DrizzleDB } from '../db';
import { logError } from '../utils/logger';

// Extend Hono context to include user and db
export type AuthContext = {
  Variables: {
    user: User;
    db: DrizzleDB;
  };
};

/**
 * Middleware to authenticate requests using API key from Authorization header
 * Expects: Authorization: Bearer {api_key}
 * Looks up the API key in device_sessions table
 * Attaches the authenticated user to c.get('user')
 */
export async function authenticateAPIKey(c: Context<{ Bindings: Env } & AuthContext>, next: Next) {
  try {
    const db = c.get('db') as DrizzleDB;

    // Extract Bearer token from Authorization header
    const authHeader = c.req.header('Authorization');
    const apiKey = extractBearerToken(authHeader);

    if (!apiKey) {
      return c.json(
        {
          error: 'Unauthorized',
          message: 'Missing or invalid Authorization header. Expected: Bearer {api_key}',
        },
        401
      );
    }

    // Query database for device session with matching API key
    const sessionResult = await db
      .select()
      .from(deviceSessions)
      .where(eq(deviceSessions.api_key, apiKey))
      .limit(1);

    if (sessionResult.length === 0) {
      return c.json(
        {
          error: 'Unauthorized',
          message: 'Invalid API key',
        },
        401
      );
    }

    const session = sessionResult[0];

    // Get the user associated with this device session
    const userResult = await db
      .select()
      .from(users)
      .where(eq(users.floatplane_user_id, session.floatplane_user_id))
      .limit(1);

    if (userResult.length === 0) {
      return c.json(
        {
          error: 'Unauthorized',
          message: 'User not found',
        },
        401
      );
    }

    const user = userResult[0];

    // Update last_accessed_at timestamp for the device session
    await db
      .update(deviceSessions)
      .set({ last_accessed_at: new Date().toISOString() })
      .where(eq(deviceSessions.api_key, apiKey));

    // Attach user to context
    c.set('user', user);

    await next();
  } catch (error) {
    logError(c, error, 'Authentication error');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to authenticate request',
      },
      500
    );
  }
}
