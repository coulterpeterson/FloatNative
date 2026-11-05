<?php

declare(strict_types=1);

// Front controller: CloudPanel's PHP vhost sends every request that isn't a
// real file here (try_files $uri $uri/ /index.php?$args). Routes mirror
// api-go/internal/router/router.go.

use FloatNative\Api\Database;
use FloatNative\Api\Handlers\AuthHandlers;
use FloatNative\Api\Handlers\LttHandlers;
use FloatNative\Api\Handlers\PlaylistHandlers;
use FloatNative\Api\Handlers\WatchLaterHandlers;
use FloatNative\Api\Http;
use FloatNative\Api\Router;

require dirname(__DIR__) . '/src/bootstrap.php';

// No default text/html Content-Type on 204/405 responses, no X-Powered-By.
ini_set('default_mimetype', '');
header_remove('X-Powered-By');

// Like chi's Recoverer: log and return a bare 500.
set_exception_handler(function (\Throwable $e): void {
    error_log('Unhandled ' . $e::class . ': ' . $e->getMessage() . ' at ' . $e->getFile() . ':' . $e->getLine());
    if (!headers_sent()) {
        header_remove();
        http_response_code(500);
    }
});

$router = new Router();

$router->get('/', function (): void {
    Http::rawJson(200, '{"name":"Floatplane Companion API","status":"running"}');
});

$router->get('/health', function (): void {
    try {
        Database::pdo()->query('SELECT 1');
    } catch (\PDOException $e) {
        Http::rawJson(503, '{"status":"error","db":' . Http::encode($e->getMessage()) . '}');
        return;
    }
    Http::rawJson(200, '{"status":"ok"}');
});

// Authenticated routes
$router->post('/auth/logout', [AuthHandlers::class, 'logout'], auth: true);
$router->get('/playlists', [PlaylistHandlers::class, 'list'], auth: true);
$router->post('/playlists', [PlaylistHandlers::class, 'create'], auth: true);
$router->put('/playlists/{id}', [PlaylistHandlers::class, 'update'], auth: true);
$router->delete('/playlists/{id}', [PlaylistHandlers::class, 'delete'], auth: true);
$router->patch('/playlists/{id}/add', [PlaylistHandlers::class, 'addVideo'], auth: true);
$router->patch('/playlists/{id}/remove', [PlaylistHandlers::class, 'removeVideo'], auth: true);

$router->get('/watch-later', [WatchLaterHandlers::class, 'get'], auth: true);
$router->put('/watch-later', [WatchLaterHandlers::class, 'replace'], auth: true);
$router->patch('/watch-later/add', [WatchLaterHandlers::class, 'addVideo'], auth: true);
$router->patch('/watch-later/remove', [WatchLaterHandlers::class, 'removeVideo'], auth: true);

$router->get('/ltt/search', [LttHandlers::class, 'search'], auth: true);

// Public auth routes
$router->post('/auth/qr/generate', [AuthHandlers::class, 'generateQR']);
$router->get('/auth/qr/poll/{id}', [AuthHandlers::class, 'pollQR']);
$router->post('/auth/login', [AuthHandlers::class, 'login']);

// Not parse_url(): it gives up on paths like "//playlists" and "/playlists/x:80/add".
$router->dispatch(
    $_SERVER['REQUEST_METHOD'] ?? 'GET',
    explode('?', $_SERVER['REQUEST_URI'] ?? '/', 2)[0]
);
