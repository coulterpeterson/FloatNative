#!/usr/bin/env php
<?php

declare(strict_types=1);

// Applies migrations/*.sql in order. Every statement is idempotent
// (CREATE TABLE IF NOT EXISTS), so this is safe to re-run after each deploy,
// the way the Go service ran its migrations on every start.

use FloatNative\Api\Database;

require dirname(__DIR__) . '/src/bootstrap.php';

$files = glob(dirname(__DIR__) . '/migrations/*.sql');
sort($files, SORT_STRING);

foreach ($files as $file) {
    echo 'Running migration: ', basename($file), "\n";
    // Strip line comments, then run one statement at a time.
    $sql = preg_replace('/--[^\n]*/', '', file_get_contents($file));
    foreach (array_filter(array_map('trim', explode(';', $sql))) as $statement) {
        Database::pdo()->exec($statement);
    }
}

echo "Migrations complete\n";
