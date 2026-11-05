#!/usr/bin/env php
<?php

declare(strict_types=1);

// Refreshes the LTT search index (fp_posts). Run hourly from cron; the Go
// service did the same on boot and then every hour.

use FloatNative\Api\LttUpdater;

require dirname(__DIR__) . '/src/bootstrap.php';

try {
    $count = LttUpdater::run();
} catch (\Throwable $e) {
    fwrite(STDERR, 'LTT update failed: ' . $e->getMessage() . "\n");
    exit(1);
}

echo "Updated {$count} LTT posts\n";
