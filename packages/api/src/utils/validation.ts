/**
 * Validation utilities for playlist operations
 */

export class ValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ValidationError';
  }
}

/**
 * Check if a name is "Watch Later" (case-insensitive)
 */
export function isWatchLaterName(name: string): boolean {
  return name.trim().toLowerCase() === 'watch later';
}

/**
 * Validate playlist name
 * @throws ValidationError if name is invalid
 */
export function validatePlaylistName(name: any): void {
  if (typeof name !== 'string') {
    throw new ValidationError('Playlist name must be a string');
  }

  const trimmed = name.trim();

  if (trimmed.length === 0) {
    throw new ValidationError('Playlist name cannot be empty');
  }

  if (trimmed.length > 255) {
    throw new ValidationError('Playlist name cannot exceed 255 characters');
  }

  if (isWatchLaterName(trimmed)) {
    throw new ValidationError(
      'Cannot create playlist named "Watch Later" - this name is reserved'
    );
  }
}

/**
 * Validate video IDs array
 * @throws ValidationError if video_ids is invalid
 */
export function validateVideoIds(videoIds: any): void {
  if (!Array.isArray(videoIds)) {
    throw new ValidationError('video_ids must be an array');
  }

  for (const id of videoIds) {
    if (typeof id !== 'string') {
      throw new ValidationError('All video IDs must be strings');
    }
  }
}

/**
 * Validate UUID format
 */
export function isValidUUID(uuid: string): boolean {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  return uuidRegex.test(uuid);
}
