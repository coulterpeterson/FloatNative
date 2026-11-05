<?php

declare(strict_types=1);

// Shared setup for the web front controller and the bin/ CLI scripts.
// No Composer: classes under FloatNative\Api\ map to files under src/.

if (PHP_VERSION_ID < 80400) {
    error_log('FloatNative API requires PHP 8.4+, running ' . PHP_VERSION);
    http_response_code(500);
    exit(1);
}

date_default_timezone_set('UTC');

spl_autoload_register(function (string $class): void {
    $prefix = 'FloatNative\\Api\\';
    if (strncmp($class, $prefix, strlen($prefix)) !== 0) {
        return;
    }
    $file = __DIR__ . '/' . str_replace('\\', '/', substr($class, strlen($prefix))) . '.php';
    if (is_file($file)) {
        require $file;
    }
});

FloatNative\Api\Env::load(dirname(__DIR__) . '/.env');
