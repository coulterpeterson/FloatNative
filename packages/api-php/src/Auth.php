<?php

declare(strict_types=1);

namespace FloatNative\Api;

// Port of api-go/internal/middleware/auth.go: resolves a Bearer API key to a
// device session and its user.
final class Auth
{
    /** The authenticated user row, or null after an error response was sent. */
    public static function user(): ?array
    {
        $apiKey = self::bearerKey();
        if ($apiKey === null) {
            Http::error(401, 'Unauthorized', 'Missing or invalid Authorization header. Expected: Bearer {api_key}');
            return null;
        }

        try {
            $session = Database::row(
                'SELECT floatplane_user_id FROM device_sessions WHERE api_key = ?',
                [$apiKey]
            );
        } catch (\PDOException) {
            $session = null;
        }
        if ($session === null) {
            Http::error(401, 'Unauthorized', 'Invalid API key');
            return null;
        }

        try {
            $user = Database::row(
                'SELECT floatplane_user_id, api_key, created_at, last_accessed_at FROM users WHERE floatplane_user_id = ?',
                [$session['floatplane_user_id']]
            );
        } catch (\PDOException) {
            $user = null;
        }
        if ($user === null) {
            Http::error(401, 'Unauthorized', 'User not found');
            return null;
        }

        try {
            Database::run('UPDATE device_sessions SET last_accessed_at = ? WHERE api_key = ?', [Database::now(), $apiKey]);
        } catch (\PDOException) {
            // Go ignored this error too.
        }

        return $user;
    }

    /** The key from "Authorization: Bearer {key}" (exactly two space-separated parts). */
    public static function bearerKey(): ?string
    {
        $header = Http::header('Authorization');
        if ($header === '') {
            return null;
        }
        $parts = explode(' ', $header);
        if (count($parts) !== 2 || $parts[0] !== 'Bearer') {
            return null;
        }
        return $parts[1];
    }
}
