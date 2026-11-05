<?php

declare(strict_types=1);

namespace FloatNative\Api\Handlers;

use FloatNative\Api\BadBody;
use FloatNative\Api\Database;
use FloatNative\Api\Http;

// Port of api-go/internal/handlers/playlists.go.
final class PlaylistHandlers
{
    private const COLUMNS = 'id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at';

    public static function list(array $params, array $user): void
    {
        try {
            $rows = Database::run(
                'SELECT ' . self::COLUMNS . ' FROM playlists WHERE floatplane_user_id = ? ORDER BY created_at DESC',
                [$user['floatplane_user_id']]
            )->fetchAll();
        } catch (\PDOException) {
            Http::error(500, 'Internal Server Error', 'Failed to fetch playlists');
            return;
        }

        $playlists = array_map([self::class, 'present'], $rows);
        Http::json(200, ['playlists' => $playlists, 'count' => count($playlists)]);
    }

    public static function create(array $params, array $user): void
    {
        try {
            $body = Http::body() ?? throw new BadBody();
            $name = Http::stringField($body, 'name');
            $videoIds = Http::stringList($body, 'video_ids') ?? [];
        } catch (BadBody) {
            Http::error(400, 'Bad Request', 'Invalid request body');
            return;
        }

        if ($name === '' || strlen($name) > 255) {
            Http::error(400, 'Bad Request', 'Invalid name');
            return;
        }

        $id = Database::uuid();
        $now = Database::now();
        try {
            Database::run(
                'INSERT INTO playlists (id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
                 VALUES (?, ?, ?, 0, ?, ?, ?)',
                [$id, $user['floatplane_user_id'], $name, self::encodeIds($videoIds), $now, $now]
            );
            $playlist = self::find($id, $user);
        } catch (\PDOException) {
            Http::error(500, 'Internal Server Error', 'Failed to create playlist');
            return;
        }

        Http::json(201, self::present($playlist));
    }

    public static function update(array $params, array $user): void
    {
        try {
            $body = Http::body() ?? throw new BadBody();
            $name = Http::optionalString($body, 'name');
            $videoIds = Http::stringList($body, 'video_ids');
        } catch (BadBody) {
            Http::error(400, 'Bad Request', 'Invalid request body');
            return;
        }

        if ($name === null && $videoIds === null) {
            Http::error(400, 'Bad Request', 'No fields to update');
            return;
        }

        // Fetch playlist to check ownership and watch later status
        try {
            $playlist = self::find($params['id'], $user);
        } catch (\PDOException) {
            $playlist = null; // Go answers 404 for any error here.
        }
        if ($playlist === null) {
            Http::error(404, 'Not Found', 'Playlist not found or access denied');
            return;
        }

        if ($playlist['is_watch_later'] && $name !== null && $name !== $playlist['name']) {
            Http::error(403, 'Forbidden', 'Cannot rename Watch Later playlist');
            return;
        }

        try {
            Database::run(
                'UPDATE playlists SET name = ?, video_ids = ?, updated_at = ? WHERE id = ? AND floatplane_user_id = ?',
                [
                    $name ?? $playlist['name'],
                    $videoIds !== null ? self::encodeIds($videoIds) : $playlist['video_ids'],
                    Database::now(),
                    $params['id'],
                    $user['floatplane_user_id'],
                ]
            );
            $playlist = self::find($params['id'], $user);
        } catch (\PDOException) {
            $playlist = null;
        }
        if ($playlist === null) {
            Http::error(500, 'Internal Server Error', 'Failed to update playlist');
            return;
        }

        Http::json(200, self::present($playlist));
    }

    public static function delete(array $params, array $user): void
    {
        try {
            $playlist = self::find($params['id'], $user);
        } catch (\PDOException) {
            $playlist = null; // Go answers 404 for any error here.
        }
        if ($playlist === null) {
            Http::error(404, 'Not Found', 'Playlist not found');
            return;
        }

        if ($playlist['is_watch_later']) {
            Http::error(403, 'Forbidden', 'Cannot delete Watch Later playlist');
            return;
        }

        try {
            $deleted = Database::run(
                'DELETE FROM playlists WHERE id = ? AND floatplane_user_id = ?',
                [$params['id'], $user['floatplane_user_id']]
            );
        } catch (\PDOException) {
            Http::error(500, 'Internal Server Error', 'Failed to delete playlist');
            return;
        }
        if ($deleted->rowCount() === 0) {
            Http::error(404, 'Not Found', 'Playlist not found');
            return;
        }

        Http::noContent();
    }

    /** PATCH /playlists/{id}/add — idempotent. */
    public static function addVideo(array $params, array $user): void
    {
        self::modifyVideos($params, $user, 'add');
    }

    /** PATCH /playlists/{id}/remove */
    public static function removeVideo(array $params, array $user): void
    {
        self::modifyVideos($params, $user, 'remove');
    }

    private static function modifyVideos(array $params, array $user, string $action): void
    {
        $videoId = self::videoIdFromBody();
        if ($videoId === null) {
            Http::error(400, 'Bad Request', 'Invalid video_id');
            return;
        }

        try {
            Database::pdo()->beginTransaction();
            // Row lock so concurrent add/remove calls don't drop each other's change.
            $playlist = Database::row(
                'SELECT id, video_ids FROM playlists WHERE id = ? AND floatplane_user_id = ? FOR UPDATE',
                [$params['id'], $user['floatplane_user_id']]
            );
        } catch (\PDOException) {
            $playlist = null; // Go answers 404 for any error here.
        }
        if ($playlist === null) {
            Database::rollBack();
            Http::error(404, 'Not Found', 'Playlist not found');
            return;
        }

        try {
            $videoIds = self::applyAction(self::decodeIds($playlist['video_ids']), $videoId, $action);
            Database::run(
                'UPDATE playlists SET video_ids = ?, updated_at = ? WHERE id = ?',
                [self::encodeIds($videoIds), Database::now(), $params['id']]
            );
            $updated = Database::row('SELECT ' . self::COLUMNS . ' FROM playlists WHERE id = ?', [$params['id']]);
            Database::pdo()->commit();
        } catch (\PDOException) {
            Database::rollBack();
            Http::error(500, 'Internal Server Error', 'Failed to update playlist');
            return;
        }

        Http::json(200, self::present($updated));
    }

    // Shared with WatchLaterHandlers.

    public static function videoIdFromBody(): ?string
    {
        try {
            $body = Http::body() ?? throw new BadBody();
            $videoId = Http::stringField($body, 'video_id');
        } catch (BadBody) {
            return null;
        }
        return $videoId === '' ? null : $videoId;
    }

    /** @param list<string> $videoIds */
    public static function applyAction(array $videoIds, string $videoId, string $action): array
    {
        if ($action === 'add') {
            if (!in_array($videoId, $videoIds, true)) {
                $videoIds[] = $videoId;
            }
            return $videoIds;
        }
        return array_values(array_filter($videoIds, fn (string $id) => $id !== $videoId));
    }

    public static function encodeIds(array $videoIds): string
    {
        return json_encode(array_values($videoIds), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    }

    /** @return list<string> */
    public static function decodeIds(string $json): array
    {
        $ids = json_decode($json, true);
        return is_array($ids) ? array_values($ids) : [];
    }

    private static function find(string $id, array $user): ?array
    {
        return Database::row(
            'SELECT ' . self::COLUMNS . ' FROM playlists WHERE id = ? AND floatplane_user_id = ?',
            [$id, $user['floatplane_user_id']]
        );
    }

    /** Field order and types of Go's models.Playlist. */
    private static function present(array $row): array
    {
        return [
            'id' => $row['id'],
            'floatplane_user_id' => $row['floatplane_user_id'],
            'name' => $row['name'],
            'is_watch_later' => (bool) $row['is_watch_later'],
            'video_ids' => self::decodeIds($row['video_ids']),
            'created_at' => Http::time($row['created_at']),
            'updated_at' => Http::time($row['updated_at']),
        ];
    }
}
