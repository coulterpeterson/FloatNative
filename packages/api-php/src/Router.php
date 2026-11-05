<?php

declare(strict_types=1);

namespace FloatNative\Api;

// Minimal stand-in for the chi router in api-go/internal/router/router.go:
// {param} matches one non-empty path segment, unknown paths get net/http's
// plain-text 404, and known paths with the wrong method get an empty 405
// with an Allow header.
final class Router
{
    /** @var list<array{string, string, callable, bool}> */
    private array $routes = [];

    public function get(string $pattern, callable $handler, bool $auth = false): void
    {
        $this->routes[] = ['GET', $pattern, $handler, $auth];
    }

    public function post(string $pattern, callable $handler, bool $auth = false): void
    {
        $this->routes[] = ['POST', $pattern, $handler, $auth];
    }

    public function put(string $pattern, callable $handler, bool $auth = false): void
    {
        $this->routes[] = ['PUT', $pattern, $handler, $auth];
    }

    public function patch(string $pattern, callable $handler, bool $auth = false): void
    {
        $this->routes[] = ['PATCH', $pattern, $handler, $auth];
    }

    public function delete(string $pattern, callable $handler, bool $auth = false): void
    {
        $this->routes[] = ['DELETE', $pattern, $handler, $auth];
    }

    public function dispatch(string $method, string $path): void
    {
        $allowed = [];
        foreach ($this->routes as [$routeMethod, $pattern, $handler, $auth]) {
            $params = $this->match($pattern, $path);
            if ($params === null) {
                continue;
            }
            if ($routeMethod !== $method) {
                $allowed[] = $routeMethod;
                continue;
            }
            if ($auth) {
                $user = Auth::user();
                if ($user === null) {
                    return;
                }
                $handler($params, $user);
            } else {
                $handler($params);
            }
            return;
        }

        if ($allowed !== []) {
            http_response_code(405);
            foreach ($allowed as $m) {
                header('Allow: ' . $m, false);
            }
            return;
        }

        http_response_code(404);
        header('Content-Type: text/plain; charset=utf-8');
        header('X-Content-Type-Options: nosniff');
        echo "404 page not found\n";
    }

    private function match(string $pattern, string $path): ?array
    {
        $regex = '#^' . preg_replace('#\\\\\{(\w+)\\\\\}#', '(?P<$1>[^/]+)', preg_quote($pattern, '#')) . '$#';
        if (!preg_match($regex, $path, $m)) {
            return null;
        }
        return array_map('rawurldecode', array_filter($m, 'is_string', ARRAY_FILTER_USE_KEY));
    }
}
