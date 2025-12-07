import { Hono } from 'hono';
import { eq, like, desc, sql } from 'drizzle-orm';
import { fpPosts } from '../db/schema';
import type { Env } from '../db';
import type { DrizzleDB } from '../db';
import { logError } from '../utils/logger';
import { authenticateAPIKey, type AuthContext } from '../middleware/auth';
import { rateLimit, byAPIKey } from '../middleware/rate-limit';

const ltt = new Hono<{ Bindings: Env } & AuthContext>();

// Apply authentication to all routes
ltt.use('*', authenticateAPIKey);

/**
 * Helper to convert SQLite datetime to ISO 8601 format (matching Fastify/TypeORM behavior)
 */
function formatDateToISO(sqliteDate: string | null | undefined): string | null {
  if (!sqliteDate) return null;

  // Check if the date is already in ISO 8601 format (contains 'T')
  // ISO 8601: "2025-11-21T22:20:00.000Z"
  // SQLite: "2025-11-21 22:20:00"
  let date: Date;
  if (sqliteDate.includes('T')) {
    // Already in ISO 8601 format, parse directly
    date = new Date(sqliteDate);
  } else {
    // SQLite format, add 'UTC' to ensure correct timezone handling
    date = new Date(sqliteDate + ' UTC');
  }

  // Validate the date before calling toISOString()
  if (isNaN(date.getTime())) {
    console.warn({
      level: 'warn',
      message: 'Invalid date value encountered',
      invalid_date: sqliteDate,
      timestamp: new Date().toISOString()
    });
    return null;
  }

  return date.toISOString();
}

/**
 * GET /ltt/search
 * Search LTT posts by title
 * Rate limited: 50 req/min by API key
 */
ltt.get('/search', rateLimit('SEARCH_LIMITER', byAPIKey), async (c) => {
  const q = c.req.query('q');

  if (!q || typeof q !== 'string' || q.trim().length === 0) {
    return c.json(
      {
        error: 'Bad Request',
        message: 'Query parameter "q" is required and must be a non-empty string',
      },
      400
    );
  }

  const searchQuery = q.trim();

  try {
    const db = c.get('db');

    // Search for exact match (case-insensitive)
    // SQLite LIKE is case-insensitive by default
    const exactMatch = await db
      .select()
      .from(fpPosts)
      .where(sql`lower(${fpPosts.title}) = lower(${searchQuery})`)
      .limit(1);

    // Search for partial matches
    const partialMatches = await db
      .select()
      .from(fpPosts)
      .where(sql`lower(${fpPosts.title}) LIKE lower(${'%' + searchQuery + '%'})`)
      .orderBy(desc(fpPosts.release_date))
      .limit(50);

    // Combine results: exact match first, then partial matches
    let results = [];

    if (exactMatch.length > 0) {
      results.push(exactMatch[0]);
      // Add partial matches that aren't the exact match
      const filteredPartialMatches = partialMatches.filter(
        (post) => post.id !== exactMatch[0].id
      );
      results.push(...filteredPartialMatches);
    } else {
      results = partialMatches;
    }

    return c.json({
      query: searchQuery,
      count: results.length,
      results: results.map((post) => ({
        id: post.id,
        title: post.title,
        creator_name: post.creator_name,
        channel_title: post.channel_title,
        channel_icon_url: post.channel_icon_url,
        thumbnail_url: post.thumbnail_url,
        video_duration: post.video_duration,
        has_video: post.has_video,
        release_date: formatDateToISO(post.release_date),
      })),
    });
  } catch (error) {
    logError(c, error, 'Error searching LTT posts');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to search posts',
      },
      500
    );
  }
});

export default ltt;
