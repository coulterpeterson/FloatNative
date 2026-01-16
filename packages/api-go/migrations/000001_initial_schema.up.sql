-- Users table
CREATE TABLE IF NOT EXISTS users (
    floatplane_user_id TEXT PRIMARY KEY,
    api_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Playlists table
CREATE TABLE IF NOT EXISTS playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    floatplane_user_id TEXT NOT NULL REFERENCES users(floatplane_user_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    is_watch_later BOOLEAN NOT NULL DEFAULT FALSE,
    video_ids TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON playlists(floatplane_user_id);

-- Floatplane Posts table
CREATE TABLE IF NOT EXISTS fp_posts (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    creator_id TEXT NOT NULL,
    creator_name TEXT,
    channel_id TEXT,
    channel_title TEXT,
    channel_icon_url TEXT,
    thumbnail_url TEXT,
    has_video BOOLEAN NOT NULL DEFAULT FALSE,
    video_count INTEGER NOT NULL DEFAULT 0,
    video_duration INTEGER NOT NULL DEFAULT 0,
    has_audio BOOLEAN NOT NULL DEFAULT FALSE,
    audio_count INTEGER NOT NULL DEFAULT 0,
    audio_duration INTEGER NOT NULL DEFAULT 0,
    has_picture BOOLEAN NOT NULL DEFAULT FALSE,
    picture_count INTEGER NOT NULL DEFAULT 0,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    has_gallery BOOLEAN NOT NULL DEFAULT FALSE,
    gallery_count INTEGER NOT NULL DEFAULT 0,
    release_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fp_posts_title ON fp_posts(title);
CREATE INDEX IF NOT EXISTS idx_fp_posts_creator_id ON fp_posts(creator_id);
CREATE INDEX IF NOT EXISTS idx_fp_posts_release_date ON fp_posts(release_date);

-- QR Sessions table
CREATE TABLE IF NOT EXISTS qr_sessions (
    id TEXT PRIMARY KEY,
    device_info TEXT,
    floatplane_user_id TEXT,
    sails_sid TEXT,
    api_key TEXT,
    status TEXT NOT NULL DEFAULT 'pending', -- pending, completed, expired
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_qr_sessions_user_id ON qr_sessions(floatplane_user_id);
CREATE INDEX IF NOT EXISTS idx_qr_sessions_expires_at ON qr_sessions(expires_at);

-- Device Sessions table
CREATE TABLE IF NOT EXISTS device_sessions (
    id TEXT PRIMARY KEY,
    floatplane_user_id TEXT NOT NULL REFERENCES users(floatplane_user_id) ON DELETE CASCADE,
    api_key TEXT NOT NULL UNIQUE,
    dpop_jkt TEXT NOT NULL UNIQUE,
    device_info TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_sessions_user_id ON device_sessions(floatplane_user_id);
CREATE INDEX IF NOT EXISTS idx_device_sessions_jkt ON device_sessions(dpop_jkt);
