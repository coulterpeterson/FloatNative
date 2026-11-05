#!/usr/bin/env php
<?php

declare(strict_types=1);

// One-time import of the Go service's Postgres data (exported with
// scripts/export-postgres.sh) into the MySQL database. Existing API keys,
// DPoP thumbprints and playlist IDs are kept, so signed-in apps keep working.
//
//   php bin/import-postgres-export.php ./pg-export [--replace]
//
// --replace empties the target tables first; without it the import refuses
// to run against a database that already has users.

use FloatNative\Api\Database;
use FloatNative\Api\Handlers\PlaylistHandlers;

require dirname(__DIR__) . '/src/bootstrap.php';

$dir = $argv[1] ?? '';
$replace = in_array('--replace', $argv, true);
if ($dir === '' || !is_dir($dir)) {
    fwrite(STDERR, "Usage: php bin/import-postgres-export.php <export-dir> [--replace]\n");
    exit(1);
}

function rows(string $dir, string $table): array
{
    $path = "$dir/$table.json";
    $rows = json_decode((string) @file_get_contents($path), true);
    if (!is_array($rows)) {
        fwrite(STDERR, "Could not read $path\n");
        exit(1);
    }
    return $rows;
}

/** Postgres timestamptz JSON ("2026-10-30T14:16:05.123456+00:00") → UTC DATETIME(6). */
function ts(?string $value): ?string
{
    if ($value === null) {
        return null;
    }
    return (new DateTimeImmutable($value))->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.u');
}

$data = [];
foreach (['users', 'playlists', 'device_sessions', 'fp_posts'] as $table) {
    $data[$table] = rows($dir, $table);
}

$pdo = Database::pdo();
if ((int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn() > 0 && !$replace) {
    fwrite(STDERR, "Target database already has users; re-run with --replace to overwrite.\n");
    exit(1);
}

$pdo->beginTransaction();
try {
    if ($replace) {
        foreach (['device_sessions', 'playlists', 'qr_sessions', 'fp_posts', 'users'] as $table) {
            $pdo->exec("DELETE FROM $table");
        }
    }

    foreach ($data['users'] as $r) {
        Database::run(
            'INSERT INTO users (floatplane_user_id, api_key, created_at, last_accessed_at) VALUES (?, ?, ?, ?)',
            [$r['floatplane_user_id'], $r['api_key'], ts($r['created_at']), ts($r['last_accessed_at'])]
        );
    }

    foreach ($data['playlists'] as $r) {
        Database::run(
            'INSERT INTO playlists (id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)',
            [
                $r['id'], $r['floatplane_user_id'], $r['name'], (int) $r['is_watch_later'],
                PlaylistHandlers::encodeIds($r['video_ids'] ?? []), ts($r['created_at']), ts($r['updated_at']),
            ]
        );
    }

    foreach ($data['device_sessions'] as $r) {
        Database::run(
            'INSERT INTO device_sessions (id, floatplane_user_id, api_key, dpop_jkt, device_info, created_at, last_accessed_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)',
            [
                $r['id'], $r['floatplane_user_id'], $r['api_key'], $r['dpop_jkt'], $r['device_info'],
                ts($r['created_at']), ts($r['last_accessed_at']),
            ]
        );
    }

    $columns = [
        'id', 'title', 'creator_id', 'creator_name', 'channel_id', 'channel_title', 'channel_icon_url', 'thumbnail_url',
        'has_video', 'video_count', 'video_duration', 'has_audio', 'audio_count', 'audio_duration',
        'has_picture', 'picture_count', 'is_featured', 'has_gallery', 'gallery_count',
        'release_date', 'created_at', 'updated_at',
    ];
    // The scraper may already have fetched some of these posts. The imported row
    // wins because it keeps the original created_at.
    $insertPost = $pdo->prepare(
        'INSERT INTO fp_posts (' . implode(', ', $columns) . ') VALUES (' . rtrim(str_repeat('?, ', count($columns)), ', ') . ')
         ON DUPLICATE KEY UPDATE ' . implode(', ', array_map(fn (string $c) => "$c = VALUES($c)", array_diff($columns, ['id'])))
    );
    foreach ($data['fp_posts'] as $r) {
        $values = [];
        foreach ($columns as $column) {
            $value = $r[$column] ?? null;
            if (str_ends_with($column, '_date') || str_ends_with($column, '_at')) {
                $value = ts($value);
            } elseif (is_bool($value)) {
                $value = (int) $value;
            }
            $values[] = $value;
        }
        $insertPost->execute($values);
    }

    $pdo->commit();
} catch (\Throwable $e) {
    Database::rollBack();
    fwrite(STDERR, 'Import failed, nothing was written: ' . $e->getMessage() . "\n");
    exit(1);
}

foreach ($data as $table => $rows) {
    printf("Imported %d %s\n", count($rows), $table);
}
