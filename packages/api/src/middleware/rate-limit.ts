import { Context, Next } from 'hono';
import { type Env, type RateLimit } from '../db';

/**
 * Rate limiting middleware for Cloudflare Workers
 *
 * Creates middleware that applies rate limiting using Cloudflare's Rate Limiting API.
 * The key used for rate limiting varies based on authentication:
 * - Authenticated requests: Uses the API key from the authenticated user
 * - Unauthenticated requests: Uses the client's IP address
 *
 * @param limiterName - Name of the rate limiter binding from Env (e.g., 'AUTH_STRICT_LIMITER')
 * @param keyExtractor - Optional function to extract the rate limit key from the request
 * @returns Hono middleware function
 */
export function rateLimit(
  limiterName: keyof Pick<
    Env,
    | 'HEALTH_CHECK_LIMITER'
    | 'AUTH_STRICT_LIMITER'
    | 'QR_GENERATE_LIMITER'
    | 'QR_POLL_LIMITER'
    | 'PLAYLIST_READ_LIMITER'
    | 'PLAYLIST_WRITE_LIMITER'
    | 'SEARCH_LIMITER'
    | 'PUBLIC_PAGE_LIMITER'
  >,
  keyExtractor?: (c: Context<{ Bindings: Env }>) => string
) {
  return async (c: Context<{ Bindings: Env }>, next: Next) => {
    const limiter = c.env[limiterName] as RateLimit;

    // Extract the key for rate limiting
    let key: string;
    if (keyExtractor) {
      key = keyExtractor(c);
    } else {
      // Default: Try to use authenticated user's API key, fall back to IP
      const user = c.get('user');
      if (user && user.api_key) {
        key = user.api_key;
      } else {
        // Use IP address for unauthenticated requests
        key = c.req.header('cf-connecting-ip') || c.req.header('x-forwarded-for') || 'unknown';
      }
    }

    // Check rate limit
    const { success } = await limiter.limit({ key });

    if (!success) {
      return c.json(
        {
          error: 'Rate limit exceeded',
          message: 'Too many requests. Please try again later.',
        },
        429
      );
    }

    await next();
  };
}

/**
 * Helper function to create a key extractor that uses IP address
 * Useful for unauthenticated endpoints where we want to rate limit by IP
 */
export function byIP(c: Context<{ Bindings: Env }>): string {
  return c.req.header('cf-connecting-ip') || c.req.header('x-forwarded-for') || 'unknown';
}

/**
 * Helper function to create a key extractor that uses a path parameter
 * Useful for endpoints like /auth/qr/poll/:sessionId where we want to rate limit by session
 */
export function byParam(paramName: string) {
  return (c: Context<{ Bindings: Env }>): string => {
    const value = c.req.param(paramName);
    if (!value) {
      // Fallback to IP if param not found
      return byIP(c);
    }
    return value;
  };
}

/**
 * Helper function to create a key extractor that uses the authenticated user's API key
 * Useful for authenticated endpoints
 */
export function byAPIKey(c: Context<{ Bindings: Env }>): string {
  const user = c.get('user');
  if (user && user.api_key) {
    return user.api_key;
  }
  // Fallback to IP if no user
  return byIP(c);
}
