import { Hono } from 'hono';
import { authenticateAPIKey, type AuthContext } from '../middleware/auth';
import { getOrCreateWatchLater, serializeVideoIds, formatPlaylist } from '../services/playlist';
import { validateVideoIds, ValidationError } from '../utils/validation';
import { eq, and } from 'drizzle-orm';
import { playlists } from '../db/schema';
import type { Env } from '../db';
import { logError } from '../utils/logger';
import { rateLimit, byAPIKey } from '../middleware/rate-limit';

const watchLater = new Hono<{ Bindings: Env } & AuthContext>();

// Apply authentication to all routes
watchLater.use('*', authenticateAPIKey);

/**
 * Helper to convert SQLite datetime to ISO 8601 format (matching Fastify/TypeORM behavior)
 */
function formatDateToISO(sqliteDate: string | null | undefined): string {
  if (!sqliteDate) return new Date().toISOString();

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
    return new Date().toISOString();
  }

  return date.toISOString();
}

/**
 * Helper to convert Date to SQLite datetime format
 */
function toSQLiteDate(date: Date = new Date()): string {
  // SQLite datetime format: "YYYY-MM-DD HH:MM:SS"
  return date.toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, '');
}

/**
 * Format Watch Later response
 */
function formatWatchLaterResponse(playlist: any) {
  return {
    id: playlist.id,
    video_ids: playlist.video_ids, // Already deserialized by formatPlaylist()
    updated_at: formatDateToISO(playlist.updated_at),
  };
}

/**
 * GET /watch-later
 * Get the user's Watch Later playlist (creates if doesn't exist)
 * Rate limited: 100 req/min by API key
 */
watchLater.get('/', rateLimit('PLAYLIST_READ_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');

    const playlist = await getOrCreateWatchLater(db, user.floatplane_user_id);

    return c.json(formatWatchLaterResponse(playlist));
  } catch (error) {
    logError(c, error, 'Error fetching Watch Later');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to fetch Watch Later playlist',
      },
      500
    );
  }
});

/**
 * PUT /watch-later
 * Replace the entire Watch Later video list
 * Rate limited: 60 req/min by API key
 */
watchLater.put('/', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const body = await c.req.json();
    const { video_ids } = body;

    if (video_ids === undefined) {
      return c.json(
        {
          error: 'Bad Request',
          message: 'video_ids is required',
        },
        400
      );
    }

    validateVideoIds(video_ids);

    // Get or create Watch Later
    const playlist = await getOrCreateWatchLater(db, user.floatplane_user_id);

    // Update video list
    await db
      .update(playlists)
      .set({
        video_ids: serializeVideoIds(video_ids),
        updated_at: toSQLiteDate(),
      })
      .where(eq(playlists.id, playlist.id));

    // Fetch updated playlist
    const updated = await db.select().from(playlists).where(eq(playlists.id, playlist.id)).limit(1);

    return c.json(formatWatchLaterResponse(updated[0]));
  } catch (error) {
    if (error instanceof ValidationError) {
      return c.json({ error: 'Bad Request', message: error.message }, 400);
    }

    logError(c, error, 'Error replacing Watch Later videos');
    return c.json({ error: 'Internal Server Error', message: 'Failed to update Watch Later' }, 500);
  }
});

/**
 * PATCH /watch-later/add
 * Add a single video to Watch Later
 * Rate limited: 60 req/min by API key
 */
watchLater.patch('/add', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const body = await c.req.json();
    const { video_id } = body;

    if (!video_id || typeof video_id !== 'string') {
      return c.json({ error: 'Bad Request', message: 'video_id is required' }, 400);
    }

    // Get or create Watch Later
    const playlist = await getOrCreateWatchLater(db, user.floatplane_user_id);
    const videoIds = playlist.video_ids; // Already deserialized by formatPlaylist()

    // Add video if not already present (idempotent)
    if (!videoIds.includes(video_id)) {
      videoIds.push(video_id);
    }

    // Update playlist
    await db
      .update(playlists)
      .set({
        video_ids: serializeVideoIds(videoIds),
        updated_at: toSQLiteDate(),
      })
      .where(eq(playlists.id, playlist.id));

    // Fetch updated playlist
    const updated = await db.select().from(playlists).where(eq(playlists.id, playlist.id)).limit(1);

    return c.json(formatWatchLaterResponse(updated[0]));
  } catch (error) {
    logError(c, error, 'Error adding video to Watch Later');
    return c.json({ error: 'Internal Server Error', message: 'Failed to add video' }, 500);
  }
});

/**
 * PATCH /watch-later/remove
 * Remove a single video from Watch Later
 * Rate limited: 60 req/min by API key
 */
watchLater.patch('/remove', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const body = await c.req.json();
    const { video_id } = body;

    if (!video_id || typeof video_id !== 'string') {
      return c.json({ error: 'Bad Request', message: 'video_id is required' }, 400);
    }

    // Get Watch Later (must exist for remove operation)
    const result = await db
      .select()
      .from(playlists)
      .where(and(eq(playlists.floatplane_user_id, user.floatplane_user_id), eq(playlists.is_watch_later, true)))
      .limit(1);

    if (result.length === 0) {
      return c.json(
        {
          error: 'Not Found',
          message: 'Watch Later playlist does not exist yet',
        },
        404
      );
    }

    const playlist = formatPlaylist(result[0]);
    const videoIds = playlist.video_ids; // Already deserialized by formatPlaylist()

    // Remove video if present (idempotent)
    const filteredIds = videoIds.filter((id) => id !== video_id);

    // Update playlist
    await db
      .update(playlists)
      .set({
        video_ids: serializeVideoIds(filteredIds),
        updated_at: toSQLiteDate(),
      })
      .where(eq(playlists.id, playlist.id));

    // Fetch updated playlist
    const updated = await db.select().from(playlists).where(eq(playlists.id, playlist.id)).limit(1);

    return c.json(formatWatchLaterResponse(updated[0]));
  } catch (error) {
    logError(c, error, 'Error removing video from Watch Later');
    return c.json({ error: 'Internal Server Error', message: 'Failed to remove video' }, 500);
  }
});

export default watchLater;
