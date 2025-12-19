-- Migration: Add device_sessions table for per-device API keys
-- Created: 2025-12-19

CREATE TABLE `device_sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`floatplane_user_id` text NOT NULL,
	`api_key` text NOT NULL,
	`dpop_jkt` text NOT NULL,
	`device_info` text,
	`created_at` text DEFAULT (datetime('now')) NOT NULL,
	`last_accessed_at` text DEFAULT (datetime('now')) NOT NULL,
	FOREIGN KEY (`floatplane_user_id`) REFERENCES `users`(`floatplane_user_id`) ON UPDATE no action ON DELETE cascade
);

CREATE UNIQUE INDEX `device_sessions_api_key_unique` ON `device_sessions` (`api_key`);
CREATE UNIQUE INDEX `device_sessions_dpop_jkt_unique` ON `device_sessions` (`dpop_jkt`);
CREATE INDEX `device_sessions_user_id_idx` ON `device_sessions` (`floatplane_user_id`);
CREATE INDEX `device_sessions_jkt_idx` ON `device_sessions` (`dpop_jkt`);
