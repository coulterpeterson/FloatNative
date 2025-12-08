import { Hono } from 'hono';
import { eq } from 'drizzle-orm';
import { users } from '../db/schema';
import { validateFloatplaneCookie, validateFloatplaneToken, FloatplaneAPIError } from '../services/floatplane';
import { generateAPIKey } from '../utils/auth';
import { getOrCreateWatchLater } from '../services/playlist';
import type { Env } from '../db';
import type { DrizzleDB } from '../db';
import { logError, logWarn } from '../utils/logger';
import { rateLimit, byIP } from '../middleware/rate-limit';

const auth = new Hono<{ Bindings: Env; Variables: { db: DrizzleDB } }>();

/**
 * POST /auth/login
 * Login a user by validating their Floatplane OAuth access token
 * Rate limited: 20 req/min by IP
 */
auth.post('/login', rateLimit('AUTH_STRICT_LIMITER', byIP), async (c) => {
  try {
    const db = c.get('db');
    const body = await c.req.json();
    const { access_token } = body;

    if (!access_token || typeof access_token !== 'string') {
      return c.json(
        {
          error: 'Bad Request',
          message: 'Missing or invalid access_token in request body',
        },
        400
      );
    }

    // Validate Floatplane token and get user ID
    let floatplaneUserId: string;
    try {
      floatplaneUserId = await validateFloatplaneToken(access_token, c.env.FLOATPLANE_API_URL);
    } catch (error) {
      if (error instanceof FloatplaneAPIError) {
        if (error.statusCode === 401 || error.statusCode === 403) {
          return c.json(
            {
              error: 'Unauthorized',
              message: 'Invalid or expired Floatplane token',
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
        logWarn(c, error, 'Failed to create Watch Later playlist during login');
      }
    }

    return c.json({
      api_key: user.api_key,
      floatplane_user_id: user.floatplane_user_id,
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

export default auth;
