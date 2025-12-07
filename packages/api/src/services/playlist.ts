import { eq, and, desc } from 'drizzle-orm';
import { randomUUID } from 'crypto';
import { playlists, type Playlist, type NewPlaylist } from '../db/schema';
import { validatePlaylistName, validateVideoIds, ValidationError } from '../utils/validation';
import type { DrizzleDB } from '../db';

export class PlaylistNotFoundError extends Error {
  constructor(message: string = 'Playlist not found') {
    super(message);
    this.name = 'PlaylistNotFoundError';
  }
}

export class PlaylistPermissionError extends Error {
  constructor(message: string = 'Permission denied') {
    super(message);
    this.name = 'PlaylistPermissionError';
  }
}

// Helper to serialize video_ids array to string
export function serializeVideoIds(videoIds: string[]): string {
  return videoIds.join(',');
}

// Helper to deserialize video_ids string to array
export function deserializeVideoIds(videoIdsStr: string): string[] {
  if (!videoIdsStr || videoIdsStr === '') {
    return [];
  }
  return videoIdsStr.split(',');
}

// Helper to convert SQLite datetime to ISO 8601 format (matching Fastify/TypeORM behavior)
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

// Helper to convert Date to SQLite datetime format
function toSQLiteDate(date: Date = new Date()): string {
  // SQLite datetime format: "YYYY-MM-DD HH:MM:SS"
  return date.toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, '');
}

// Helper to convert DB playlist to API format
export function formatPlaylist(playlist: Playlist) {
  return {
    ...playlist,
    video_ids: deserializeVideoIds(playlist.video_ids),
    is_watch_later: Boolean(playlist.is_watch_later),
    created_at: formatDateToISO(playlist.created_at),
    updated_at: formatDateToISO(playlist.updated_at),
  };
}

/**
 * Verify playlist ownership
 */
async function verifyOwnership(
  db: DrizzleDB,
  playlistId: string,
  userId: string
): Promise<Playlist> {
  const result = await db
    .select()
    .from(playlists)
    .where(and(eq(playlists.id, playlistId), eq(playlists.floatplane_user_id, userId)))
    .limit(1);

  if (result.length === 0) {
    throw new PlaylistNotFoundError('Playlist not found or access denied');
  }

  return result[0];
}

/**
 * Get all playlists for a user
 */
export async function getAllPlaylists(
  db: DrizzleDB,
  userId: string,
  includeWatchLater: boolean = false
) {
  let query = db
    .select()
    .from(playlists)
    .where(eq(playlists.floatplane_user_id, userId))
    .orderBy(desc(playlists.created_at));

  if (!includeWatchLater) {
    query = db
      .select()
      .from(playlists)
      .where(
        and(eq(playlists.floatplane_user_id, userId), eq(playlists.is_watch_later, false))
      )
      .orderBy(desc(playlists.created_at));
  }

  const result = await query;
  return result.map(formatPlaylist);
}

/**
 * Create a new playlist
 */
export async function createPlaylist(
  db: DrizzleDB,
  userId: string,
  name: string,
  videoIds: string[] = []
) {
  validatePlaylistName(name);
  validateVideoIds(videoIds);

  const newPlaylist: NewPlaylist = {
    id: randomUUID(),
    floatplane_user_id: userId,
    name: name.trim(),
    is_watch_later: false,
    video_ids: serializeVideoIds(videoIds),
  };

  await db.insert(playlists).values(newPlaylist);

  const result = await db.select().from(playlists).where(eq(playlists.id, newPlaylist.id!));
  return formatPlaylist(result[0]);
}

/**
 * Update an existing playlist
 */
export async function updatePlaylist(
  db: DrizzleDB,
  playlistId: string,
  userId: string,
  name?: string,
  videoIds?: string[]
) {
  const playlist = await verifyOwnership(db, playlistId, userId);

  const updates: Partial<Playlist> = {
    updated_at: toSQLiteDate(),
  };

  if (name !== undefined) {
    validatePlaylistName(name);

    if (playlist.is_watch_later) {
      throw new PlaylistPermissionError('Cannot rename Watch Later playlist');
    }

    updates.name = name.trim();
  }

  if (videoIds !== undefined) {
    validateVideoIds(videoIds);
    updates.video_ids = serializeVideoIds(videoIds);
  }

  await db.update(playlists).set(updates).where(eq(playlists.id, playlistId));

  const result = await db.select().from(playlists).where(eq(playlists.id, playlistId));
  return formatPlaylist(result[0]);
}

/**
 * Delete a playlist
 */
export async function deletePlaylist(db: DrizzleDB, playlistId: string, userId: string) {
  const playlist = await verifyOwnership(db, playlistId, userId);

  if (playlist.is_watch_later) {
    throw new PlaylistPermissionError('Cannot delete Watch Later playlist');
  }

  await db.delete(playlists).where(eq(playlists.id, playlistId));
}

/**
 * Get or create Watch Later playlist for a user
 */
export async function getOrCreateWatchLater(db: DrizzleDB, userId: string) {
  let result = await db
    .select()
    .from(playlists)
    .where(and(eq(playlists.floatplane_user_id, userId), eq(playlists.is_watch_later, true)))
    .limit(1);

  if (result.length === 0) {
    const newPlaylist: NewPlaylist = {
      id: randomUUID(),
      floatplane_user_id: userId,
      name: 'Watch Later',
      is_watch_later: true,
      video_ids: '',
    };

    try {
      await db.insert(playlists).values(newPlaylist);
      result = await db.select().from(playlists).where(eq(playlists.id, newPlaylist.id!));
    } catch (error: any) {
      // Handle race condition
      result = await db
        .select()
        .from(playlists)
        .where(and(eq(playlists.floatplane_user_id, userId), eq(playlists.is_watch_later, true)))
        .limit(1);

      if (result.length === 0) {
        throw error;
      }
    }
  }

  return formatPlaylist(result[0]);
}
