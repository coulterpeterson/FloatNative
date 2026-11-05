<?php

declare(strict_types=1);

namespace FloatNative\Api;

// Request/response helpers that reproduce the Go service's wire format:
// json.Encoder output (trailing newline, <>& escaped, Go key order) and
// RFC 3339 timestamps with trailing fractional zeros trimmed.
final class Http
{
    private const JSON_FLAGS = JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE
        | JSON_HEX_TAG | JSON_HEX_AMP | JSON_INVALID_UTF8_SUBSTITUTE;

    public static function json(int $status, mixed $payload): void
    {
        http_response_code($status);
        header('Content-Type: application/json');
        echo json_encode($payload, self::JSON_FLAGS), "\n";
    }

    /** Raw JSON body without the encoder's newline (used by GET / and /health). */
    public static function rawJson(int $status, string $body): void
    {
        http_response_code($status);
        header('Content-Type: application/json');
        echo $body;
    }

    public static function error(int $status, string $error, string $message): void
    {
        self::json($status, ['error' => $error, 'message' => $message]);
    }

    public static function noContent(): void
    {
        http_response_code(204);
    }

    public static function encode(mixed $value): string
    {
        return json_encode($value, self::JSON_FLAGS);
    }

    /** Formats a DATETIME(6) value the way Go marshals a UTC time.Time. */
    public static function time(?string $value): string
    {
        if ($value === null) {
            return '0001-01-01T00:00:00Z';
        }
        [$date, $clock] = explode(' ', $value, 2);
        $fraction = '';
        if (str_contains($clock, '.')) {
            [$clock, $fraction] = explode('.', $clock, 2);
            $fraction = rtrim($fraction, '0');
        }
        return $date . 'T' . $clock . ($fraction !== '' ? '.' . $fraction : '') . 'Z';
    }

    public static function header(string $name): string
    {
        $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
        if (isset($_SERVER[$key])) {
            return (string) $_SERVER[$key];
        }
        // Some FastCGI setups only expose Authorization after a rewrite.
        if (isset($_SERVER['REDIRECT_' . $key])) {
            return (string) $_SERVER['REDIRECT_' . $key];
        }
        if (function_exists('getallheaders')) {
            foreach (getallheaders() as $k => $v) {
                if (strcasecmp($k, $name) === 0) {
                    return (string) $v;
                }
            }
        }
        return '';
    }

    /**
     * Decodes the request body as a JSON object, or returns null when Go's
     * json.Decoder would have rejected it for a struct target. A literal
     * `null` body decodes to an empty object, as it does in Go.
     */
    public static function body(): ?\stdClass
    {
        $raw = file_get_contents('php://input');
        try {
            $value = json_decode((string) $raw, false, 512, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            return null;
        }
        if ($value === null) {
            return new \stdClass();
        }
        return $value instanceof \stdClass ? $value : null;
    }

    /**
     * Reads a string field the way Go decodes into a `string`: absent or null
     * is "", any other non-string type is a decode error (BadBody).
     */
    public static function stringField(\stdClass $body, string $key): string
    {
        $value = $body->$key ?? null;
        if ($value === null) {
            return '';
        }
        if (!is_string($value)) {
            throw new BadBody();
        }
        return $value;
    }

    /** Like stringField, but keeps "absent/null" distinct (Go's *string). */
    public static function optionalString(\stdClass $body, string $key): ?string
    {
        $value = $body->$key ?? null;
        if ($value !== null && !is_string($value)) {
            throw new BadBody();
        }
        return $value;
    }

    /** Decodes a []string field; null when absent or null (Go's nil slice). */
    public static function stringList(\stdClass $body, string $key): ?array
    {
        $value = $body->$key ?? null;
        if ($value === null) {
            return null;
        }
        if (!is_array($value)) {
            throw new BadBody();
        }
        $list = [];
        foreach ($value as $item) {
            if ($item !== null && !is_string($item)) {
                throw new BadBody();
            }
            $list[] = $item ?? '';
        }
        return $list;
    }
}
