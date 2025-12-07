import { sqliteTable, text, integer, index } from 'drizzle-orm/sqlite-core';
import { sql } from 'drizzle-orm';

// Users table
export const users = sqliteTable('users', {
  floatplane_user_id: text('floatplane_user_id').primaryKey(),
  api_key: text('api_key').notNull().unique(),
  created_at: text('created_at')
    .notNull()
    .default(sql`(datetime('now'))`),
  last_accessed_at: text('last_accessed_at')
    .notNull()
    .default(sql`(datetime('now'))`),
});

// Playlists table
export const playlists = sqliteTable(
  'playlists',
  {
    id: text('id').primaryKey(),
    floatplane_user_id: text('floatplane_user_id')
      .notNull()
      .references(() => users.floatplane_user_id, { onDelete: 'cascade' }),
    name: text('name').notNull(),
    is_watch_later: integer('is_watch_later', { mode: 'boolean' }).notNull().default(false),
    video_ids: text('video_ids').notNull().default(''), // Stored as comma-separated values
    created_at: text('created_at')
      .notNull()
      .default(sql`(datetime('now'))`),
    updated_at: text('updated_at')
      .notNull()
      .default(sql`(datetime('now'))`),
  },
  (table) => ({
    userIdIdx: index('playlists_user_id_idx').on(table.floatplane_user_id),
  })
);

// Floatplane Posts table (for LTT videos)
export const fpPosts = sqliteTable(
  'fp_posts',
  {
    id: text('id').primaryKey(),
    title: text('title').notNull(),
    creator_id: text('creator_id').notNull(),
    creator_name: text('creator_name'),
    channel_id: text('channel_id'),
    channel_title: text('channel_title'),
    channel_icon_url: text('channel_icon_url'),
    thumbnail_url: text('thumbnail_url'),
    has_video: integer('has_video', { mode: 'boolean' }).notNull().default(false),
    video_count: integer('video_count').notNull().default(0),
    video_duration: integer('video_duration').notNull().default(0),
    has_audio: integer('has_audio', { mode: 'boolean' }).notNull().default(false),
    audio_count: integer('audio_count').notNull().default(0),
    audio_duration: integer('audio_duration').notNull().default(0),
    has_picture: integer('has_picture', { mode: 'boolean' }).notNull().default(false),
    picture_count: integer('picture_count').notNull().default(0),
    is_featured: integer('is_featured', { mode: 'boolean' }).notNull().default(false),
    has_gallery: integer('has_gallery', { mode: 'boolean' }).notNull().default(false),
    gallery_count: integer('gallery_count').notNull().default(0),
    release_date: text('release_date'),
    created_at: text('created_at')
      .notNull()
      .default(sql`(datetime('now'))`),
    updated_at: text('updated_at')
      .notNull()
      .default(sql`(datetime('now'))`),
  },
  (table) => ({
    titleIdx: index('fp_posts_title_idx').on(table.title),
    creatorIdIdx: index('fp_posts_creator_id_idx').on(table.creator_id),
    releaseDateIdx: index('fp_posts_release_date_idx').on(table.release_date),
  })
);


// QR Sessions table
export const qrSessions = sqliteTable(
  'qr_sessions',
  {
    id: text('id').primaryKey(),
    device_info: text('device_info'),
    floatplane_user_id: text('floatplane_user_id'),
    sails_sid: text('sails_sid'),
    api_key: text('api_key'),
    status: text('status').notNull().default('pending'), // 'pending', 'completed', 'expired'
    expires_at: text('expires_at').notNull(),
    created_at: text('created_at')
      .notNull()
      .default(sql`(datetime('now'))`),
    completed_at: text('completed_at'),
  },
  (table) => ({
    userIdIdx: index('qr_sessions_user_id_idx').on(table.floatplane_user_id),
    expiresAtIdx: index('qr_sessions_expires_at_idx').on(table.expires_at),
  })
);

// Type exports for TypeScript
export type User = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;

export type Playlist = typeof playlists.$inferSelect;
export type NewPlaylist = typeof playlists.$inferInsert;

export type FPPost = typeof fpPosts.$inferSelect;
export type NewFPPost = typeof fpPosts.$inferInsert;

export type QRSession = typeof qrSessions.$inferSelect;
export type NewQRSession = typeof qrSessions.$inferInsert;
