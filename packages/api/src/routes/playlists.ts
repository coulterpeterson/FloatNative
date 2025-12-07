import { Hono } from 'hono';
import { authenticateAPIKey, type AuthContext } from '../middleware/auth';
import {
  getAllPlaylists,
  createPlaylist,
  updatePlaylist,
  deletePlaylist,
  PlaylistNotFoundError,
  PlaylistPermissionError,
  serializeVideoIds,
  deserializeVideoIds,
} from '../services/playlist';
import { ValidationError } from '../utils/validation';
import type { Env } from '../db';
import { logError } from '../utils/logger';
import { rateLimit, byAPIKey } from '../middleware/rate-limit';

const playlists = new Hono<{ Bindings: Env } & AuthContext>();

// Apply authentication to all routes
playlists.use('*', authenticateAPIKey);

/**
 * GET /playlists
 * Get all playlists for the authenticated user
 * Rate limited: 100 req/min by API key
 */
playlists.get('/', rateLimit('PLAYLIST_READ_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const includeWatchLater = c.req.query('include_watch_later') === 'true';

    const playlistsList = await getAllPlaylists(db, user.floatplane_user_id, includeWatchLater);

    return c.json({
      playlists: playlistsList,
      count: playlistsList.length,
    });
  } catch (error) {
    logError(c, error, 'Error fetching playlists');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to fetch playlists',
      },
      500
    );
  }
});

/**
 * POST /playlists
 * Create a new playlist
 * Rate limited: 60 req/min by API key
 */
playlists.post('/', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const body = await c.req.json();
    const { name, video_ids = [] } = body;

    if (!name) {
      return c.json(
        {
          error: 'Bad Request',
          message: 'Playlist name is required',
        },
        400
      );
    }

    const playlist = await createPlaylist(db, user.floatplane_user_id, name, video_ids);

    return c.json(playlist, 201);
  } catch (error) {
    if (error instanceof ValidationError) {
      return c.json(
        {
          error: 'Bad Request',
          message: error.message,
        },
        400
      );
    }

    logError(c, error, 'Error creating playlist');
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Failed to create playlist',
      },
      500
    );
  }
});

/**
 * PUT /playlists/:id
 * Update an existing playlist
 * Rate limited: 60 req/min by API key
 */
playlists.put('/:id', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const playlistId = c.req.param('id');
    const body = await c.req.json();
    const { name, video_ids } = body;

    if (name === undefined && video_ids === undefined) {
      return c.json(
        {
          error: 'Bad Request',
          message: 'At least one field (name or video_ids) must be provided',
        },
        400
      );
    }

    const playlist = await updatePlaylist(db, playlistId, user.floatplane_user_id, name, video_ids);

    return c.json(playlist);
  } catch (error) {
    if (error instanceof ValidationError) {
      return c.json({ error: 'Bad Request', message: error.message }, 400);
    }
    if (error instanceof PlaylistPermissionError) {
      return c.json({ error: 'Forbidden', message: error.message }, 403);
    }
    if (error instanceof PlaylistNotFoundError) {
      return c.json({ error: 'Not Found', message: error.message }, 404);
    }

    logError(c, error, 'Error updating playlist');
    return c.json({ error: 'Internal Server Error', message: 'Failed to update playlist' }, 500);
  }
});

/**
 * PATCH /playlists/:id/add
 * Add a single video to a playlist
 * Rate limited: 60 req/min by API key
 */
playlists.patch('/:id/add', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const playlistId = c.req.param('id');
    const body = await c.req.json();
    const { video_id } = body;

    if (!video_id || typeof video_id !== 'string') {
      return c.json({ error: 'Bad Request', message: 'video_id is required' }, 400);
    }

    // Get current playlist
    const playlist = await updatePlaylist(db, playlistId, user.floatplane_user_id);
    const videoIds = playlist.video_ids; // Already deserialized by formatPlaylist()

    // Add video if not already present
    if (!videoIds.includes(video_id)) {
      videoIds.push(video_id);
    }

    const updated = await updatePlaylist(db, playlistId, user.floatplane_user_id, undefined, videoIds);

    return c.json(updated);
  } catch (error) {
    if (error instanceof PlaylistNotFoundError) {
      return c.json({ error: 'Not Found', message: error.message }, 404);
    }

    logError(c, error, 'Error adding video to playlist');
    return c.json({ error: 'Internal Server Error', message: 'Failed to add video' }, 500);
  }
});

/**
 * PATCH /playlists/:id/remove
 * Remove a single video from a playlist
 * Rate limited: 60 req/min by API key
 */
playlists.patch('/:id/remove', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const playlistId = c.req.param('id');
    const body = await c.req.json();
    const { video_id } = body;

    if (!video_id || typeof video_id !== 'string') {
      return c.json({ error: 'Bad Request', message: 'video_id is required' }, 400);
    }

    // Get current playlist
    const playlist = await updatePlaylist(db, playlistId, user.floatplane_user_id);
    const videoIds = playlist.video_ids; // Already deserialized by formatPlaylist()

    // Remove video if present
    const filteredIds = videoIds.filter((id) => id !== video_id);

    const updated = await updatePlaylist(db, playlistId, user.floatplane_user_id, undefined, filteredIds);

    return c.json(updated);
  } catch (error) {
    if (error instanceof PlaylistNotFoundError) {
      return c.json({ error: 'Not Found', message: error.message }, 404);
    }

    logError(c, error, 'Error removing video from playlist');
    return c.json({ error: 'Internal Server Error', message: 'Failed to remove video' }, 500);
  }
});

/**
 * DELETE /playlists/:id
 * Delete a playlist
 * Rate limited: 60 req/min by API key
 */
playlists.delete('/:id', rateLimit('PLAYLIST_WRITE_LIMITER', byAPIKey), async (c) => {
  try {
    const db = c.get('db');
    const user = c.get('user');
    const playlistId = c.req.param('id');

    await deletePlaylist(db, playlistId, user.floatplane_user_id);

    // Return 204 No Content with empty body (matching Fastify behavior)
    return new Response(null, { status: 204 });
  } catch (error) {
    if (error instanceof PlaylistPermissionError) {
      return c.json({ error: 'Forbidden', message: error.message }, 403);
    }
    if (error instanceof PlaylistNotFoundError) {
      return c.json({ error: 'Not Found', message: error.message }, 404);
    }

    logError(c, error, 'Error deleting playlist');
    return c.json({ error: 'Internal Server Error', message: 'Failed to delete playlist' }, 500);
  }
});

export default playlists;
