-- MySQL 8 / MariaDB 10.6+ port of api-go/migrations/000001_initial_schema.up.sql.
-- utf8mb4_bin keeps ID and API key comparisons case-sensitive, like Postgres TEXT.

CREATE TABLE IF NOT EXISTS users (
    floatplane_user_id VARCHAR(191) NOT NULL PRIMARY KEY,
    api_key VARCHAR(191) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_accessed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- video_ids holds a JSON array of strings (Postgres used TEXT[]).
CREATE TABLE IF NOT EXISTS playlists (
    id CHAR(36) NOT NULL PRIMARY KEY,
    floatplane_user_id VARCHAR(191) NOT NULL,
    name MEDIUMTEXT NOT NULL, -- PUT /playlists/{id} has no length limit; TEXT stops at 64 KB
    is_watch_later TINYINT(1) NOT NULL DEFAULT 0,
    video_ids LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_playlists_user_id (floatplane_user_id),
    CONSTRAINT fk_playlists_user FOREIGN KEY (floatplane_user_id)
        REFERENCES users (floatplane_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS fp_posts (
    id VARCHAR(191) NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    creator_id VARCHAR(191) NOT NULL,
    creator_name TEXT,
    channel_id VARCHAR(191),
    channel_title TEXT,
    channel_icon_url TEXT,
    thumbnail_url TEXT,
    has_video TINYINT(1) NOT NULL DEFAULT 0,
    video_count INT NOT NULL DEFAULT 0,
    video_duration INT NOT NULL DEFAULT 0,
    has_audio TINYINT(1) NOT NULL DEFAULT 0,
    audio_count INT NOT NULL DEFAULT 0,
    audio_duration INT NOT NULL DEFAULT 0,
    has_picture TINYINT(1) NOT NULL DEFAULT 0,
    picture_count INT NOT NULL DEFAULT 0,
    is_featured TINYINT(1) NOT NULL DEFAULT 0,
    has_gallery TINYINT(1) NOT NULL DEFAULT 0,
    gallery_count INT NOT NULL DEFAULT 0,
    release_date DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_fp_posts_creator_id (creator_id),
    INDEX idx_fp_posts_release_date (release_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS qr_sessions (
    id VARCHAR(191) NOT NULL PRIMARY KEY,
    device_info TEXT,
    floatplane_user_id VARCHAR(191),
    sails_sid TEXT,
    api_key VARCHAR(191),
    status VARCHAR(32) NOT NULL DEFAULT 'pending', -- pending, completed, expired
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    INDEX idx_qr_sessions_user_id (floatplane_user_id),
    INDEX idx_qr_sessions_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS device_sessions (
    id VARCHAR(191) NOT NULL PRIMARY KEY,
    floatplane_user_id VARCHAR(191) NOT NULL,
    api_key VARCHAR(191) NOT NULL UNIQUE,
    dpop_jkt VARCHAR(191) NOT NULL UNIQUE,
    device_info TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_accessed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_device_sessions_user_id (floatplane_user_id),
    CONSTRAINT fk_device_sessions_user FOREIGN KEY (floatplane_user_id)
        REFERENCES users (floatplane_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
