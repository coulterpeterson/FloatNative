<?php

declare(strict_types=1);

// Stand-in for Floatplane's /api/content/creator, used by tests/parity.php so
// the Go and PHP scrapers ingest identical posts. Serve with:
//   php -S 127.0.0.1:18090 tests/mock-floatplane.php

$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
if ($path !== '/api/content/creator' || !str_contains($_SERVER['HTTP_COOKIE'] ?? '', 'sails.sid=parity-test-sid')) {
    http_response_code(401);
    return;
}
header('Content-Type: application/json');
readfile(__DIR__ . '/fixtures/creator-posts.json');
