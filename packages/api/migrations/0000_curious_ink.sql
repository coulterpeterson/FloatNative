CREATE TABLE `fp_posts` (
	`id` text PRIMARY KEY NOT NULL,
	`title` text NOT NULL,
	`creator_id` text NOT NULL,
	`creator_name` text,
	`channel_id` text,
	`channel_title` text,
	`channel_icon_url` text,
	`thumbnail_url` text,
	`has_video` integer DEFAULT false NOT NULL,
	`video_count` integer DEFAULT 0 NOT NULL,
	`video_duration` integer DEFAULT 0 NOT NULL,
	`has_audio` integer DEFAULT false NOT NULL,
	`audio_count` integer DEFAULT 0 NOT NULL,
	`audio_duration` integer DEFAULT 0 NOT NULL,
	`has_picture` integer DEFAULT false NOT NULL,
	`picture_count` integer DEFAULT 0 NOT NULL,
	`is_featured` integer DEFAULT false NOT NULL,
	`has_gallery` integer DEFAULT false NOT NULL,
	`gallery_count` integer DEFAULT 0 NOT NULL,
	`release_date` text,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`updated_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE INDEX `fp_posts_title_idx` ON `fp_posts` (`title`);--> statement-breakpoint
CREATE INDEX `fp_posts_creator_id_idx` ON `fp_posts` (`creator_id`);--> statement-breakpoint
CREATE INDEX `fp_posts_release_date_idx` ON `fp_posts` (`release_date`);--> statement-breakpoint
CREATE TABLE `playlists` (
	`id` text PRIMARY KEY NOT NULL,
	`floatplane_user_id` text NOT NULL,
	`name` text NOT NULL,
	`is_watch_later` integer DEFAULT false NOT NULL,
	`video_ids` text DEFAULT '' NOT NULL,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`updated_at` text DEFAULT (datetime('now')) NOT NULL,
	FOREIGN KEY (`floatplane_user_id`) REFERENCES `users`(`floatplane_user_id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `playlists_user_id_idx` ON `playlists` (`floatplane_user_id`);--> statement-breakpoint
CREATE TABLE `qr_sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`device_info` text,
	`floatplane_user_id` text,
	`sails_sid` text,
	`api_key` text,
	`status` text DEFAULT 'pending' NOT NULL,
	`expires_at` text NOT NULL,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`completed_at` text
);
--> statement-breakpoint
CREATE INDEX `qr_sessions_user_id_idx` ON `qr_sessions` (`floatplane_user_id`);--> statement-breakpoint
CREATE INDEX `qr_sessions_expires_at_idx` ON `qr_sessions` (`expires_at`);--> statement-breakpoint
CREATE TABLE `system_config` (
	`id` text PRIMARY KEY DEFAULT 'system' NOT NULL,
	`floatplane_sails_sid` text,
	`cookie_created_at` text,
	`cookie_expires_at` text,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`updated_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE TABLE `users` (
	`floatplane_user_id` text PRIMARY KEY NOT NULL,
	`api_key` text NOT NULL,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`last_accessed_at` text DEFAULT (datetime('now')) NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `users_api_key_unique` ON `users` (`api_key`);