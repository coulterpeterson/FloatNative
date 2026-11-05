<?php

declare(strict_types=1);

namespace FloatNative\Api;

// Port of api-go/internal/services/floatplane_auth.go, including the checks
// golang-jwt v5's ParseUnverified applies (three segments, unpadded
// base64url, JSON object header with a known "alg"). Signatures are not
// verified, exactly as in the Go service.
final class Jwt
{
    private const KNOWN_ALGS = [
        'HS256', 'HS384', 'HS512', 'RS256', 'RS384', 'RS512',
        'PS256', 'PS384', 'PS512', 'ES256', 'ES384', 'ES512', 'EdDSA', 'none',
    ];

    /**
     * Returns the Floatplane user ID (sub) from an unexpired access token,
     * or null if Go's ValidateFloatplaneTokenLocally would have failed.
     */
    public static function floatplaneUserId(string $accessToken): ?string
    {
        $token = self::parseUnverified($accessToken);
        if ($token === null) {
            return null;
        }
        $claims = $token['claims'];

        $exp = $claims->exp ?? null;
        if (!is_int($exp) && !is_float($exp)) {
            return null; // token missing exp claim
        }
        if (time() > (int) $exp) {
            return null; // token expired
        }

        $sub = $claims->sub ?? null;
        if (!is_string($sub) || $sub === '') {
            return null;
        }
        return $sub;
    }

    /** JWK thumbprint of the DPoP proof's header key, or null if unusable. */
    public static function dpopJkt(string $dpopProof): ?string
    {
        $token = self::parseUnverified($dpopProof);
        if ($token === null) {
            return null;
        }
        $jwk = $token['header']->jwk ?? null;
        if (!$jwk instanceof \stdClass) {
            return null;
        }

        // Only crv, kty, x, y, serialized with sorted keys (Go's map ordering).
        $canonical = new \stdClass();
        foreach (['crv', 'kty', 'x', 'y'] as $field) {
            if (property_exists($jwk, $field)) {
                $canonical->$field = self::sortKeys($jwk->$field);
            }
        }

        $hash = hash('sha256', Http::encode($canonical), true);
        return rtrim(strtr(base64_encode($hash), '+/', '-_'), '=');
    }

    /** @return array{header: \stdClass, claims: \stdClass}|null */
    private static function parseUnverified(string $token): ?array
    {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            return null;
        }

        $header = self::decodeObject($parts[0], false);
        if ($header === null) {
            return null;
        }
        $claims = self::decodeObject($parts[1], true);
        if ($claims === null) {
            return null;
        }

        $alg = $header->alg ?? null;
        if (!is_string($alg) || !in_array($alg, self::KNOWN_ALGS, true)) {
            return null;
        }
        return ['header' => $header, 'claims' => $claims];
    }

    private static function decodeObject(string $segment, bool $allowNull): ?\stdClass
    {
        $json = self::base64UrlDecode($segment);
        if ($json === null) {
            return null;
        }
        try {
            $value = json_decode($json, false, 512, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            return null;
        }
        if ($value === null) {
            return $allowNull ? new \stdClass() : null;
        }
        return $value instanceof \stdClass ? $value : null;
    }

    /** base64.RawURLEncoding.DecodeString: no padding, \r and \n ignored. */
    private static function base64UrlDecode(string $segment): ?string
    {
        $segment = str_replace(["\r", "\n"], '', $segment);
        if (!preg_match('/^[A-Za-z0-9_-]*$/', $segment) || strlen($segment) % 4 === 1) {
            return null;
        }
        $decoded = base64_decode(str_pad(strtr($segment, '-_', '+/'), (int) ceil(strlen($segment) / 4) * 4, '='), true);
        return $decoded === false ? null : $decoded;
    }

    private static function sortKeys(mixed $value): mixed
    {
        if ($value instanceof \stdClass) {
            $props = get_object_vars($value);
            ksort($props, SORT_STRING);
            $sorted = new \stdClass();
            foreach ($props as $k => $v) {
                $sorted->$k = self::sortKeys($v);
            }
            return $sorted;
        }
        if (is_array($value)) {
            return array_map([self::class, 'sortKeys'], $value);
        }
        return $value;
    }
}
