<?php

declare(strict_types=1);

namespace FloatNative\Api\Handlers;

use FloatNative\Api\BadBody;
use FloatNative\Api\Database;
use FloatNative\Api\Http;

// Port of api-go/internal/handlers/watchlater.go. Responses use the
// simplified {id, updated_at, video_ids} shape.
final class WatchLaterHandlers
{
    /** GET /watch-later — creates the playlist if it doesn't exist. */
    public static function get(array $params, array $user): void
    {
        try {
            $playlist = self::find($user);
        } catch (\PDOException) {
            $playlist = null; // Go treats any error as "not found" and tries to create.
        }
        if ($playlist === null) {
            try {
                $playlist = self::create($user, []);
            } catch (\PDOException) {
                Http::error(500, 'Internal Server Error', 'Failed to create Watch Later playlist');
                return;
            }
        }
        Http::json(200, self::present($playlist));
    }

    /** PUT /watch-later — replaces the entire video list. */
    public static function replace(array $params, array $user): void
    {
        try {
            $body = Http::body() ?? throw new BadBody();
            $videoIds = Http::stringList($body, 'video_ids') ?? throw new BadBody();
        } catch (BadBody) {
            Http::error(400, 'Bad Request', 'Missing video_ids');
            return;
        }

        try {
            $playlist = self::find($user);
            if ($playlist !== null) {
                Database::run(
                    'UPDATE playlists SET video_ids = ?, updated_at = ? WHERE id = ?',
                    [PlaylistHandlers::encodeIds($videoIds), Database::now(), $playlist['id']]
                );
                $playlist = self::find($user);
            }
        } catch (\PDOException) {
            $playlist = null; // Go treats any error as "no rows" and tries to insert.
        }
        if ($playlist === null) {
            try {
                $playlist = self::create($user, $videoIds);
            } catch (\PDOException) {
                Http::error(500, 'Internal Server Error', 'Failed to update Watch Later');
                return;
            }
        }

        Http::json(200, self::present($playlist));
    }

    public static function addVideo(array $params, array $user): void
    {
        self::modify($user, 'add');
    }

    public static function removeVideo(array $params, array $user): void
    {
        self::modify($user, 'remove');
    }

    private static function modify(array $user, string $action): void
    {
        $videoId = PlaylistHandlers::videoIdFromBody();
        if ($videoId === null) {
            Http::error(400, 'Bad Request', 'Invalid video_id');
            return;
        }

        try {
            Database::pdo()->beginTransaction();
            $playlist = self::find($user, true);
        } catch (\PDOException) {
            $playlist = null; // Go treats any error as "not found".
        }
        if ($playlist === null) {
            if ($action === 'remove') {
                Database::rollBack();
                Http::error(404, 'Not Found', "Watch Later playlist doesn't exist yet");
                return;
            }
            // Add creates an empty Watch Later first.
            try {
                $playlist = self::create($user, []);
            } catch (\PDOException) {
                Database::rollBack();
                Http::error(500, 'Internal Server Error', 'Failed to create Watch Later');
                return;
            }
        }

        try {
            $videoIds = PlaylistHandlers::applyAction(
                PlaylistHandlers::decodeIds($playlist['video_ids']),
                $videoId,
                $action
            );
            Database::run(
                'UPDATE playlists SET video_ids = ?, updated_at = ? WHERE id = ?',
                [PlaylistHandlers::encodeIds($videoIds), Database::now(), $playlist['id']]
            );
            $playlist = self::find($user);
            Database::pdo()->commit();
        } catch (\PDOException) {
            Database::rollBack();
            Http::error(500, 'Internal Server Error', 'Failed to update Watch Later');
            return;
        }

        Http::json(200, self::present($playlist));
    }

    private static function find(array $user, bool $lock = false): ?array
    {
        return Database::row(
            'SELECT id, video_ids, updated_at FROM playlists WHERE floatplane_user_id = ? AND is_watch_later = 1 LIMIT 1'
                . ($lock ? ' FOR UPDATE' : ''),
            [$user['floatplane_user_id']]
        );
    }

    /** @param list<string> $videoIds */
    private static function create(array $user, array $videoIds): array
    {
        $id = Database::uuid();
        $now = Database::now();
        Database::run(
            "INSERT INTO playlists (id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
             VALUES (?, ?, 'Watch Later', 1, ?, ?, ?)",
            [$id, $user['floatplane_user_id'], PlaylistHandlers::encodeIds($videoIds), $now, $now]
        );
        return ['id' => $id, 'video_ids' => PlaylistHandlers::encodeIds($videoIds), 'updated_at' => $now];
    }

    /** Go marshals map keys alphabetically. */
    private static function present(array $row): array
    {
        return [
            'id' => $row['id'],
            'updated_at' => Http::time($row['updated_at']),
            'video_ids' => PlaylistHandlers::decodeIds($row['video_ids']),
        ];
    }
}
