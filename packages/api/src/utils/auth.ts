import { randomUUID } from 'crypto';

/**
 * Generate a UUID v4 API key for user authentication
 */
export function generateAPIKey(): string {
  return randomUUID();
}

/**
 * Extract Bearer token from Authorization header
 * @param authHeader The Authorization header value
 * @returns The extracted token or null if invalid
 */
export function extractBearerToken(authHeader: string | undefined): string | null {
  if (!authHeader) {
    return null;
  }

  const parts = authHeader.split(' ');
  if (parts.length !== 2 || parts[0] !== 'Bearer') {
    return null;
  }

  return parts[1];
}
