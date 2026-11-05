<?php

declare(strict_types=1);

namespace FloatNative\Api\Handlers;

use FloatNative\Api\Auth;
use FloatNative\Api\BadBody;
use FloatNative\Api\Database;
use FloatNative\Api\Http;
use FloatNative\Api\Jwt;

// Port of api-go/internal/handlers/auth.go.
final class AuthHandlers
{
    public static function generateQR(array $params): void
    {
        $id = Database::randomHex(16);
        $now = Database::now();
        $expiresAt = (new \DateTimeImmutable($now))->modify('+5 minutes')->format('Y-m-d H:i:s.u');

        try {
            Database::run(
                "INSERT INTO qr_sessions (id, status, expires_at, created_at) VALUES (?, 'pending', ?, ?)",
                [$id, $expiresAt, $now]
            );
        } catch (\PDOException) {
            Http::error(500, 'Internal Server Error', 'Failed to create QR session');
            return;
        }

        Http::json(201, [
            'expires_at' => Http::time($expiresAt),
            'id' => $id,
        ]);
    }

    public static function pollQR(array $params): void
    {
        try {
            $session = Database::row(
                'SELECT id, status, floatplane_user_id, api_key, expires_at FROM qr_sessions WHERE id = ?',
                [$params['id']]
            );
        } catch (\PDOException) {
            $session = null; // Go answers 404 for any error here.
        }
        if ($session === null) {
            Http::error(404, 'Not Found', 'Session not found');
            return;
        }

        if (Database::now() > $session['expires_at']) {
            Http::json(200, ['status' => 'expired']);
            return;
        }

        if ($session['status'] === 'completed') {
            Http::json(200, [
                'api_key' => $session['api_key'] ?? '',
                'floatplane_user_id' => $session['floatplane_user_id'] ?? '',
                'status' => 'completed',
            ]);
            return;
        }

        Http::json(200, ['status' => $session['status']]);
    }

    public static function login(array $params): void
    {
        $body = Http::body();
        try {
            if ($body === null) {
                throw new BadBody();
            }
            $accessToken = Http::stringField($body, 'access_token');
            $dpopProof = Http::stringField($body, 'dpop_proof');
            $deviceInfo = Http::stringField($body, 'device_info');
        } catch (BadBody) {
            Http::error(400, 'Bad Request', 'Invalid body');
            return;
        }

        if ($accessToken === '' || $dpopProof === '') {
            Http::error(400, 'Bad Request', 'Missing access_token or dpop_proof');
            return;
        }

        // 1. Extract DPoP JKT
        $dpopJkt = Jwt::dpopJkt($dpopProof);
        if ($dpopJkt === null) {
            Http::error(400, 'Bad Request', 'Invalid DPoP proof');
            return;
        }

        // 2. Validate Token
        $fpUserId = Jwt::floatplaneUserId($accessToken);
        if ($fpUserId === null) {
            Http::error(401, 'Unauthorized', 'Invalid Floatplane token');
            return;
        }

        // 3. Ensure User Exists
        $isNewUser = false;
        try {
            $user = Database::row('SELECT floatplane_user_id FROM users WHERE floatplane_user_id = ?', [$fpUserId]);
        } catch (\PDOException) {
            $user = null; // Go treats any error as "not found" and goes on to create.
        }
        if ($user === null) {
            $now = Database::now();
            try {
                Database::run(
                    'INSERT INTO users (floatplane_user_id, api_key, created_at, last_accessed_at) VALUES (?, ?, ?, ?)',
                    [$fpUserId, Database::randomHex(32), $now, $now]
                );
            } catch (\PDOException) {
                Http::error(500, 'Internal Server Error', 'Failed to create user');
                return;
            }
            $isNewUser = true;

            // Create Watch Later
            try {
                Database::run(
                    "INSERT INTO playlists (id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
                     VALUES (?, ?, 'Watch Later', 1, '[]', ?, ?)",
                    [Database::uuid(), $fpUserId, $now, $now]
                );
            } catch (\PDOException) {
                // Go ignored this error too.
            }
        }

        // 4. Check Device Session
        try {
            $session = Database::row('SELECT api_key FROM device_sessions WHERE dpop_jkt = ?', [$dpopJkt]);
        } catch (\PDOException) {
            $session = null; // Same as the user lookup above.
        }
        if ($session !== null) {
            $finalApiKey = $session['api_key'];
            try {
                Database::run('UPDATE device_sessions SET last_accessed_at = ? WHERE dpop_jkt = ?', [Database::now(), $dpopJkt]);
            } catch (\PDOException) {
                // Go ignored this error too.
            }
        } else {
            $finalApiKey = Database::randomHex(32);
            $now = Database::now();
            try {
                Database::run(
                    'INSERT INTO device_sessions (id, floatplane_user_id, api_key, dpop_jkt, device_info, created_at, last_accessed_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)',
                    [Database::randomHex(16), $fpUserId, $finalApiKey, $dpopJkt, $deviceInfo, $now, $now]
                );
            } catch (\PDOException) {
                Http::error(500, 'Internal Server Error', 'Failed to create device session');
                return;
            }
        }

        Http::json(200, [
            'api_key' => $finalApiKey,
            'floatplane_user_id' => $fpUserId,
            'message' => $isNewUser ? 'User registered successfully' : 'User logged in successfully',
        ]);
    }

    public static function logout(array $params, array $user): void
    {
        $currentApiKey = Auth::bearerKey();
        if ($currentApiKey === null) {
            Http::error(401, 'Unauthorized', 'Invalid Authorization header');
            return;
        }

        // Rotate the device session's key, which invalidates the current one.
        $newKey = Database::randomHex(32);
        try {
            $updated = Database::run('UPDATE device_sessions SET api_key = ? WHERE api_key = ?', [$newKey, $currentApiKey]);
        } catch (\PDOException) {
            Http::error(500, 'Internal Server Error', 'Failed to logout');
            return;
        }
        if ($updated->rowCount() === 0) {
            // Legacy fallback kept from the Go service, which ignored its error too.
            try {
                Database::run('UPDATE users SET api_key = ? WHERE api_key = ?', [$newKey, $currentApiKey]);
            } catch (\PDOException) {
            }
        }

        Http::json(200, ['message' => 'Logged out successfully. API key invalidated.']);
    }
}
