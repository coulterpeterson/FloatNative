<?php

declare(strict_types=1);

namespace FloatNative\Api\Handlers;

use FloatNative\Api\Database;
use FloatNative\Api\Http;

// Port of api-go/internal/handlers/ltt.go.
final class LttHandlers
{
    public static function search(array $params, array $user): void
    {
        $query = is_string($_GET['q'] ?? null) ? $_GET['q'] : '';
        if ($query === '') {
            Http::json(200, []);
            return;
        }

        // LOWER() LIKE LOWER() mirrors Postgres ILIKE on the utf8mb4_bin column.
        // Postgres sorts NULL release dates first under DESC and Go then skipped
        // those rows, so they still count toward the 50-row limit here.
        try {
            $rows = Database::run(
                'SELECT id, title, creator_id, creator_name, channel_id, channel_title, channel_icon_url, thumbnail_url,
                        has_video, video_count, video_duration, has_audio, audio_count, audio_duration,
                        has_picture, picture_count, is_featured, has_gallery, gallery_count, release_date, created_at, updated_at
                 FROM fp_posts
                 WHERE LOWER(title) LIKE LOWER(?)
                 ORDER BY release_date IS NULL DESC, release_date DESC
                 LIMIT 50',
                ['%' . $query . '%']
            )->fetchAll();
        } catch (\PDOException) {
            Http::error(500, 'Internal Server Error', 'Failed to search posts');
            return;
        }

        $posts = [];
        foreach ($rows as $row) {
            if ($row['release_date'] === null) {
                continue;
            }
            $posts[] = self::present($row);
        }
        Http::json(200, $posts);
    }

    /** Field order of Go's models.FPPost; empty strings dropped where it had omitempty. */
    private static function present(array $row): array
    {
        $post = [
            'id' => $row['id'],
            'title' => $row['title'],
            'creator_id' => $row['creator_id'],
            'creator_name' => (string) $row['creator_name'],
            'channel_id' => (string) $row['channel_id'],
            'channel_title' => (string) $row['channel_title'],
            'channel_icon_url' => (string) $row['channel_icon_url'],
            'thumbnail_url' => (string) $row['thumbnail_url'],
            'has_video' => (bool) $row['has_video'],
            'video_count' => (int) $row['video_count'],
            'video_duration' => (int) $row['video_duration'],
            'has_audio' => (bool) $row['has_audio'],
            'audio_count' => (int) $row['audio_count'],
            'audio_duration' => (int) $row['audio_duration'],
            'has_picture' => (bool) $row['has_picture'],
            'picture_count' => (int) $row['picture_count'],
            'is_featured' => (bool) $row['is_featured'],
            'has_gallery' => (bool) $row['has_gallery'],
            'gallery_count' => (int) $row['gallery_count'],
            'release_date' => Http::time($row['release_date']),
            'created_at' => Http::time($row['created_at']),
            'updated_at' => Http::time($row['updated_at']),
        ];
        foreach (['creator_name', 'channel_id', 'channel_title', 'channel_icon_url', 'thumbnail_url'] as $key) {
            if ($post[$key] === '') {
                unset($post[$key]);
            }
        }
        return $post;
    }
}
