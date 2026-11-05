<?php

declare(strict_types=1);

namespace FloatNative\Api;

// Reads KEY=value lines from .env. Real environment variables win, so the
// same names the Go service used (DB_HOST, FLOATPLANE_SAILS_SID, ...) work
// from either place.
final class Env
{
    private static array $values = [];

    public static function load(string $path): void
    {
        if (!is_readable($path)) {
            return;
        }
        foreach (file($path, FILE_IGNORE_NEW_LINES) as $line) {
            $line = trim($line);
            if ($line === '' || $line[0] === '#' || !str_contains($line, '=')) {
                continue;
            }
            [$key, $value] = array_map('trim', explode('=', $line, 2));
            if (strlen($value) >= 2 && ($value[0] === '"' || $value[0] === "'") && $value[-1] === $value[0]) {
                $value = substr($value, 1, -1);
            }
            self::$values[$key] = $value;
        }
    }

    public static function get(string $key, string $default = ''): string
    {
        $value = getenv($key);
        if ($value !== false && $value !== '') {
            return $value;
        }
        $value = self::$values[$key] ?? '';
        return $value !== '' ? $value : $default;
    }
}
