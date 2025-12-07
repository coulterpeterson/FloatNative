import { drizzle } from 'drizzle-orm/d1';
import * as schema from './schema';

// Type for D1 database binding from Cloudflare Workers
export interface Env {
  DB: D1Database;
  FLOATPLANE_API_URL: string;
  API_BASE_URL: string;
  FLOATPLANE_SAILS_SID: string;
  MAILGUN_API_KEY?: string;
  MAILGUN_DOMAIN?: string;
  ALERT_EMAIL?: string;
  NODE_ENV?: string;
  // Rate limiters
  HEALTH_CHECK_LIMITER: RateLimit;
  AUTH_STRICT_LIMITER: RateLimit;
  QR_GENERATE_LIMITER: RateLimit;
  QR_POLL_LIMITER: RateLimit;
  PLAYLIST_READ_LIMITER: RateLimit;
  PLAYLIST_WRITE_LIMITER: RateLimit;
  SEARCH_LIMITER: RateLimit;
  PUBLIC_PAGE_LIMITER: RateLimit;
}

// Type for Cloudflare Rate Limiter
export interface RateLimit {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

// Create database connection for Workers (D1)
// In local development (wrangler dev --env dev), this uses a local SQLite database
// In production, this uses Cloudflare D1
export function createD1Database(d1: D1Database) {
  return drizzle(d1, { schema });
}

// Type for database instance
export type DrizzleDB = ReturnType<typeof createD1Database>;
