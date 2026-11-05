<?php

declare(strict_types=1);

// Runs one scripted session against the Go API and the PHP API and diffs
// every response (status, Content-Type, Allow, key order, values). Generated
// values (API keys, UUIDs, timestamps) are replaced with numbered placeholders
// per side, and timestamps must use Go's RFC 3339 form on both.
//
//   php tests/parity.php http://127.0.0.1:18080 http://127.0.0.1:18081
//
// Both servers need empty databases, plus the LTT index filled from
// tests/mock-floatplane.php (see README "Parity tests").

if ($argc < 3) {
    fwrite(STDERR, "Usage: php tests/parity.php <go-base-url> <php-base-url>\n");
    exit(1);
}

function b64url(string $s): string
{
    return rtrim(strtr(base64_encode($s), '+/', '-_'), '=');
}

function jwt(array $header, mixed $claims): string
{
    return b64url(json_encode($header)) . '.' . b64url(json_encode($claims)) . '.' . b64url('sig');
}

function accessToken(string $sub, int $ttl = 3600): string
{
    return jwt(['alg' => 'HS256', 'typ' => 'JWT'], ['sub' => $sub, 'exp' => time() + $ttl]);
}

function dpopProof(): string
{
    $key = openssl_pkey_new(['private_key_type' => OPENSSL_KEYTYPE_EC, 'curve_name' => 'prime256v1']);
    $ec = openssl_pkey_get_details($key)['ec'];
    $jwk = ['kty' => 'EC', 'crv' => 'P-256', 'x' => b64url($ec['x']), 'y' => b64url($ec['y'])];
    return jwt(['typ' => 'dpop+jwt', 'alg' => 'ES256', 'jwk' => $jwk], [
        'jti' => bin2hex(random_bytes(8)), 'htm' => 'GET', 'htu' => 'https://www.floatplane.com/api/v3/user/self', 'iat' => time(),
    ]);
}

final class Side
{
    public array $vars = [];
    private array $placeholders = [];

    public function __construct(public string $base)
    {
    }

    /** @return array{status:int, type:string, allow:string, raw:string, json:mixed} */
    public function call(string $method, string $path, mixed $body = null, ?string $auth = null): array
    {
        $headers = [];
        if ($auth !== null) {
            $headers[] = 'Authorization: ' . $auth;
        }
        $ch = curl_init($this->base . $path);
        curl_setopt_array($ch, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HEADER => true,
            CURLOPT_NOBODY => $method === 'HEAD',
            CURLOPT_TIMEOUT => 10,
        ]);
        if ($body !== null) {
            $headers[] = 'Content-Type: application/json';
            curl_setopt($ch, CURLOPT_POSTFIELDS, is_string($body) ? $body : json_encode($body, JSON_UNESCAPED_SLASHES));
        }
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        $response = curl_exec($ch);
        $headerSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
        $rawHeaders = substr($response, 0, $headerSize);
        $raw = substr($response, $headerSize);
        preg_match('/^Content-Type:\s*(.*?)\r$/mi', $rawHeaders, $type);
        preg_match_all('/^Allow:\s*(.*?)\r$/mi', $rawHeaders, $allow);
        $allowed = $allow[1];
        sort($allowed);
        return [
            'status' => curl_getinfo($ch, CURLINFO_RESPONSE_CODE),
            'type' => $type[1] ?? '',
            'allow' => implode(',', $allowed),
            'raw' => $raw,
            'json' => json_decode($raw, true),
        ];
    }

    /** Replaces generated values with stable per-side placeholders. */
    public function normalize(array $response, array &$problems): string
    {
        $raw = $response['raw'];
        $json = $response['json'];
        if ($json === null) {
            return json_encode(['status' => $response['status'], 'type' => $response['type'], 'allow' => $response['allow'], 'raw' => $raw]);
        }
        array_walk_recursive($json, function (&$value, $key) use (&$problems) {
            if (!is_string($value)) {
                return;
            }
            if (in_array($key, ['created_at', 'updated_at', 'expires_at', 'release_date'], true)) {
                if (!preg_match('/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d*[1-9])?Z$/', $value)) {
                    $problems[] = "$this->base: bad timestamp $key=$value";
                }
                // Scraped release dates are deterministic, so compare them exactly.
                if ($key !== 'release_date') {
                    $value = '<ts>';
                }
                return;
            }
            if (preg_match('/^[0-9a-f]{64}$|^[0-9a-f]{32}$|^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/', $value)) {
                $this->placeholders[$value] ??= '<gen' . (count($this->placeholders) + 1) . '>';
                $value = $this->placeholders[$value];
            }
        });
        return json_encode([
            'status' => $response['status'],
            'type' => $response['type'],
            'allow' => $response['allow'],
            'newline' => str_ends_with($raw, "\n"),
            'body' => $json,
        ], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    }
}

$go = new Side(rtrim($argv[1], '/'));
$php = new Side(rtrim($argv[2], '/'));
$failures = 0;
$checks = 0;

/**
 * Runs the same request on both sides. Arguments may be closures taking the
 * Side so each server can use its own API keys and IDs.
 */
function both(string $label, string $method, string|Closure $path, mixed $body = null, string|Closure|null $auth = null, ?Closure $after = null): void
{
    global $go, $php, $failures, $checks;
    $checks++;
    $out = [];
    $problems = [];
    foreach (['go' => $go, 'php' => $php] as $name => $side) {
        $p = $path instanceof Closure ? $path($side) : $path;
        $b = $body instanceof Closure ? $body($side) : $body;
        $a = $auth instanceof Closure ? $auth($side) : $auth;
        $response = $side->call($method, $p, $b, $a);
        $after?->__invoke($side, $response);
        $out[$name] = $side->normalize($response, $problems);
    }
    if ($out['go'] !== $out['php'] || $problems) {
        $failures++;
        echo "FAIL  $label\n  go:  {$out['go']}\n  php: {$out['php']}\n";
        foreach ($problems as $problem) {
            echo "  $problem\n";
        }
    } else {
        echo "ok    $label\n";
    }
}

$key = fn (string $name) => fn (Side $s) => 'Bearer ' . ($s->vars[$name] ?? 'missing');
$save = fn (string $name, string $field = 'api_key') => function (Side $s, array $r) use ($name, $field) {
    if (isset($r['json'][$field])) {
        $s->vars[$name] = $r['json'][$field];
    }
};

$userA = 'parity_' . bin2hex(random_bytes(4));
$userB = $userA . '_b';
$tokenA = accessToken($userA);
$tokenB = accessToken($userB);
$proofA1 = dpopProof();
$proofA2 = dpopProof();
$proofB = dpopProof();

// Basics and routing
both('GET /', 'GET', '/');
both('GET /health', 'GET', '/health');
both('unknown path', 'GET', '/nope');
both('trailing slash', 'GET', '/playlists/');
both('wrong method on /auth/login', 'GET', '/auth/login');
both('wrong method on /playlists', 'DELETE', '/playlists');
both('wrong method on /playlists/{id}', 'GET', '/playlists/abc');
both('HEAD /', 'HEAD', '/');
both('double slash path', 'GET', '//playlists');
both('only slashes', 'GET', '//');
both('colon in unknown path', 'GET', '/x:80');
both('colon in QR id', 'GET', '/auth/qr/poll/a:1');
both('colon in playlist id without auth', 'PATCH', '/playlists/x:80/add');

// Auth middleware
both('no Authorization', 'GET', '/playlists');
both('Basic auth', 'GET', '/playlists', null, 'Basic abc');
both('Bearer without key', 'GET', '/playlists', null, 'Bearer');
both('Bearer with extra part', 'GET', '/playlists', null, 'Bearer a b');
both('lowercase bearer', 'GET', '/playlists', null, 'bearer abc');
both('unknown key', 'GET', '/watch-later', null, 'Bearer nope');

// Login validation
both('login empty body', 'POST', '/auth/login', '');
both('login array body', 'POST', '/auth/login', '[]');
both('login null body', 'POST', '/auth/login', 'null');
both('login non-string token', 'POST', '/auth/login', ['access_token' => 1, 'dpop_proof' => 'x']);
both('login missing proof', 'POST', '/auth/login', ['access_token' => $tokenA]);
both('login proof with 2 segments', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => 'a.b']);
both('login proof unknown alg', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => jwt(['alg' => 'XX', 'jwk' => ['kty' => 'EC']], [])]);
both('login proof without jwk', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => jwt(['alg' => 'ES256'], [])]);
both('login proof jwk not object', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => jwt(['alg' => 'ES256', 'jwk' => 'x'], [])]);
both('login proof padded base64', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => 'eyJhbGciOiJFUzI1NiJ9==.e30.c2ln']);
both('login expired token', 'POST', '/auth/login', ['access_token' => accessToken($userA, -60), 'dpop_proof' => $proofA1]);
both('login token without exp', 'POST', '/auth/login', ['access_token' => jwt(['alg' => 'HS256'], ['sub' => $userA]), 'dpop_proof' => $proofA1]);
both('login token string exp', 'POST', '/auth/login', ['access_token' => jwt(['alg' => 'HS256'], ['sub' => $userA, 'exp' => (string) (time() + 99)]), 'dpop_proof' => $proofA1]);
both('login token without sub', 'POST', '/auth/login', ['access_token' => jwt(['alg' => 'HS256'], ['exp' => time() + 99]), 'dpop_proof' => $proofA1]);
both('login token not a JWT', 'POST', '/auth/login', ['access_token' => 'abc', 'dpop_proof' => $proofA1]);

// Login flows
both('register user A', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => $proofA1, 'device_info' => 'Parity A1'], null, $save('a1'));
both('same device logs in with same key', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => $proofA1], null, function (Side $s, array $r) {
    if (($r['json']['api_key'] ?? null) !== $s->vars['a1']) {
        echo "  $s->base: same DPoP key returned a different api_key\n";
    }
});
both('second device for A', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => $proofA2], null, $save('a2'));
both('register user B', 'POST', '/auth/login', ['access_token' => $tokenB, 'dpop_proof' => $proofB], null, $save('b'));

// QR
both('QR generate', 'POST', '/auth/qr/generate', null, null, $save('qr', 'id'));
both('QR poll pending', 'GET', fn (Side $s) => '/auth/qr/poll/' . $s->vars['qr']);
both('QR poll unknown', 'GET', '/auth/qr/poll/doesnotexist');

// Watch Later
both('watch later created at registration', 'GET', '/watch-later', null, $key('a1'));
both('watch later add v1', 'PATCH', '/watch-later/add', ['video_id' => 'v1'], $key('a1'));
both('watch later add v1 again', 'PATCH', '/watch-later/add', ['video_id' => 'v1'], $key('a1'));
both('watch later add v2 from other device', 'PATCH', '/watch-later/add', ['video_id' => 'v2'], $key('a2'));
both('watch later remove v1', 'PATCH', '/watch-later/remove', ['video_id' => 'v1'], $key('a1'));
both('watch later remove missing', 'PATCH', '/watch-later/remove', ['video_id' => 'zzz'], $key('a1'));
both('watch later add empty id', 'PATCH', '/watch-later/add', ['video_id' => ''], $key('a1'));
both('watch later add bad body', 'PATCH', '/watch-later/add', '{', $key('a1'));
both('watch later add numeric id', 'PATCH', '/watch-later/add', ['video_id' => 5], $key('a1'));
both('watch later replace', 'PUT', '/watch-later', ['video_ids' => ['a', 'b', 'a']], $key('a1'));
both('watch later replace empty', 'PUT', '/watch-later', ['video_ids' => []], $key('a1'));
both('watch later replace null', 'PUT', '/watch-later', ['video_ids' => null], $key('a1'));
both('watch later replace missing', 'PUT', '/watch-later', [], $key('a1'));
both('watch later replace mixed types', 'PUT', '/watch-later', ['video_ids' => ['a', 1]], $key('a1'));
both('watch later after replace', 'GET', '/watch-later', null, $key('a1'));

// Playlists
both('playlists initial', 'GET', '/playlists', null, $key('a1'));
both('create without name', 'POST', '/playlists', ['video_ids' => []], $key('a1'));
both('create name too long', 'POST', '/playlists', ['name' => str_repeat('x', 256)], $key('a1'));
both('create name 255 bytes', 'POST', '/playlists', ['name' => str_repeat('x', 255)], $key('a1'), $save('p255', 'id'));
both('create multibyte name over 255 bytes', 'POST', '/playlists', ['name' => str_repeat('é', 128)], $key('a1'));
both('create bad body', 'POST', '/playlists', 'nope', $key('a1'));
usleep(5000);
both('create Mine', 'POST', '/playlists', ['name' => 'Mine <&>'], $key('a1'), $save('mine', 'id'));
usleep(5000);
both('create with videos', 'POST', '/playlists', ['name' => 'With videos', 'video_ids' => ['x1', 'x2']], $key('a1'), $save('withv', 'id'));
both('list after create', 'GET', '/playlists', null, $key('a1'));
both('rename Mine', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['mine'], ['name' => 'Renamed'], $key('a1'));
both('replace videos', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['mine'], ['video_ids' => ['q', 'r']], $key('a1'));
both('update with no fields', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['mine'], ['name' => null], $key('a1'));
both('update unknown playlist', 'PUT', '/playlists/00000000-0000-4000-8000-000000000000', ['name' => 'x'], $key('a1'));
both('update non-uuid id', 'PUT', '/playlists/not-a-uuid', ['name' => 'x'], $key('a1'));
both('update other user playlist', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['mine'], ['name' => 'hijack'], $key('b'));
both('colon in playlist id with auth', 'PATCH', '/playlists/x:80/add', ['video_id' => 's'], $key('a1'));
both('rename to name over 64 KB', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['p255'], ['name' => str_repeat('x', 70000)], $key('a1'));
both('add to playlist', 'PATCH', fn (Side $s) => '/playlists/' . $s->vars['mine'] . '/add', ['video_id' => 's'], $key('a1'));
both('add duplicate to playlist', 'PATCH', fn (Side $s) => '/playlists/' . $s->vars['mine'] . '/add', ['video_id' => 'q'], $key('a1'));
both('remove from playlist', 'PATCH', fn (Side $s) => '/playlists/' . $s->vars['mine'] . '/remove', ['video_id' => 'q'], $key('a1'));
both('add with bad body', 'PATCH', fn (Side $s) => '/playlists/' . $s->vars['mine'] . '/add', [], $key('a1'));
both('add to other user playlist', 'PATCH', fn (Side $s) => '/playlists/' . $s->vars['mine'] . '/add', ['video_id' => 'x'], $key('b'));
both('list playlists', 'GET', '/playlists', null, $key('a1'), function (Side $s, array $r) {
    foreach ($r['json']['playlists'] ?? [] as $p) {
        if ($p['is_watch_later']) {
            $s->vars['wl'] = $p['id'];
        }
    }
});
both('rename Watch Later', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['wl'], ['name' => 'Nope'], $key('a1'));
both('update Watch Later keeping name', 'PUT', fn (Side $s) => '/playlists/' . $s->vars['wl'], ['name' => 'Watch Later', 'video_ids' => ['w']], $key('a1'));
both('delete Watch Later', 'DELETE', fn (Side $s) => '/playlists/' . $s->vars['wl'], null, $key('a1'));
both('delete other user playlist', 'DELETE', fn (Side $s) => '/playlists/' . $s->vars['withv'], null, $key('b'));
both('delete playlist', 'DELETE', fn (Side $s) => '/playlists/' . $s->vars['withv'], null, $key('a1'));
both('delete again', 'DELETE', fn (Side $s) => '/playlists/' . $s->vars['withv'], null, $key('a1'));
both('user B playlists', 'GET', '/playlists', null, $key('b'));
both('user B remove from Watch Later', 'PATCH', '/watch-later/remove', ['video_id' => 'x'], $key('b'));

// LTT search
foreach (['', 'linus', 'LINUS', 'ünïcödé', '%', '_test', '100%', 'zzz-no-match', '<b>'] as $q) {
    both("ltt search q=" . json_encode($q, JSON_UNESCAPED_UNICODE), 'GET', '/ltt/search?q=' . rawurlencode($q), null, $key('a1'));
}
both('ltt search without auth', 'GET', '/ltt/search?q=linus');

// Logout
both('logout device 2', 'POST', '/auth/logout', null, $key('a2'));
both('old device 2 key rejected', 'GET', '/watch-later', null, $key('a2'));
both('device 1 still works', 'GET', '/watch-later', null, $key('a1'));
both('device 2 login after logout gets new key', 'POST', '/auth/login', ['access_token' => $tokenA, 'dpop_proof' => $proofA2], null, function (Side $s, array $r) {
    if (($r['json']['api_key'] ?? null) === $s->vars['a2']) {
        echo "  $s->base: logout did not rotate the key\n";
    }
});

echo "\n", $checks - $failures, "/$checks requests matched\n";
exit($failures === 0 ? 0 : 1);
