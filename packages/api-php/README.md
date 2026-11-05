# Floatplane Companion API (PHP)

A plain PHP 8.4 port of [`packages/api-go`](../api-go) for hosting next to other PHP sites (e.g. on CloudPanel) instead of running Docker + Postgres. It serves the same routes with the same request handling and the same JSON responses, so the apps need no changes.

- **Runtime**: PHP 8.4+ with `pdo_mysql` and `curl`. No Composer, no dependencies.
- **Database**: MySQL 8 or MariaDB 10.6+ (tested on MySQL 8.4 and MariaDB 11.4).
- **LTT search index**: refreshed by `bin/update-ltt-posts.php` from cron (the Go service ran it on boot and then every hour).

## Layout

```
public/index.php          front controller + route table (the site's web root)
src/                      handlers, auth, JWT/DPoP parsing, DB helpers (autoloaded, no Composer)
migrations/               MySQL schema
bin/migrate.php           applies migrations (idempotent, safe to re-run)
bin/update-ltt-posts.php  LTT scraper, run hourly from cron
bin/import-postgres-export.php  one-time import of the Go service's data
scripts/export-postgres.sh      dumps the Go service's Postgres tables to JSON
tests/parity.php          diffs every response against a running Go API
```

Configuration is read from `.env` next to `public/` (see `.env.example`) or from real environment variables. The variable names are the same as the Go service's.

## Deploying on CloudPanel

1. **Create the site**: *Sites → Add Site → Create a PHP Site*, application **Generic**, PHP **8.4**. To test before switching over, use a temporary hostname first (e.g. `api-php.floatnative.coulterpeterson.com`).
2. **Upload the code**: copy the contents of `packages/api-php` into the site's directory (`/home/<site-user>/htdocs/<domain>/`). In the site's *Settings*, set **Root Directory** to `<domain>/public` so only `public/` is web-reachable. `.env`, `src/` and `bin/` stay outside the web root.
3. **Create the database**: *Databases → Add Database*. Copy `.env.example` to `.env` and fill in `DB_*` (host `127.0.0.1`, port `3306`) and `FLOATPLANE_SAILS_SID`.
4. **Create the schema** (SSH in as the site user):
   ```bash
   cd ~/htdocs/<domain> && php8.4 bin/migrate.php
   ```
5. **Schedule the LTT scraper**: *Cron Jobs → Add Cron Job*, every hour (`0 * * * *`):
   ```bash
   php8.4 /home/<site-user>/htdocs/<domain>/bin/update-ltt-posts.php
   ```
   Run that command once by hand too; it should print `Updated 20 LTT posts`.
6. **Leave Varnish off** for this site. Every route is per-user or session-specific.
7. **Smoke test**:
   ```bash
   curl https://<domain>/health    # {"status":"ok"}
   ```

CloudPanel's PHP vhost already sends unknown paths to `index.php` (`try_files $uri $uri/ /index.php?$args`), and nginx passes the `Authorization` header through to PHP-FPM. No vhost edits are needed.

### Moving data over from the Go service

API keys, DPoP thumbprints and playlist IDs are copied as-is, so signed-in apps keep working without logging in again. Stop the Go API first so nothing is written after the export:

```bash
# On the server, in packages/api-go
docker compose stop api
sh ../api-php/scripts/export-postgres.sh /tmp/pg-export

# In the PHP site's directory
php8.4 bin/import-postgres-export.php /tmp/pg-export
```

The import runs in one transaction and refuses to touch a database that already has users. Pass `--replace` to wipe and re-import. `qr_sessions` is skipped because those rows expire after five minutes.

Then point the real hostname at the PHP site: remove the old reverse-proxy site for the Go API and add the domain to the PHP site (or swap domains). Cloudflare DNS doesn't change if it's the same server. Once the apps are confirmed working, retire the Docker stack with `docker compose down`. Keep the `postgres_data` volume until you're sure. CloudPanel's own database backups replace the `db-backup` container.

## Differences from the Go service

Responses are the same for the same requests. A few internals differ on purpose:

- **Storage**: Postgres `TEXT[]` → a JSON array in a `LONGTEXT` column. Tables use `utf8mb4_bin` so ID and key comparisons stay case-sensitive. Search uses `LOWER(title) LIKE LOWER(?)` to match `ILIKE`.
- **No lost updates**: `PATCH …/add` and `…/remove` lock the playlist row (`SELECT … FOR UPDATE`), so two quick adds can't overwrite each other. Go read and wrote without a lock.
- **Lenient scraper**: Go decoded Floatplane's post list into strict structs, so one changed field type failed the whole batch. The PHP version reads each field defensively.
- **Search results**: rows with a NULL creator/channel string are returned with that field omitted; Go's scan skipped them. Only rows imported from the old D1 database can have those NULLs.
- **`/health` on DB failure**: the error message is properly JSON-escaped.
- **Escapes**: `<`, `>` and `&` come out as `<` rather than Go's `<`. They decode the same.

Inherited as-is: nothing ever marks a QR session `completed` (there's no endpoint for it in either service), and DPoP/access-token signatures are not verified.

## Parity tests

`tests/parity.php` runs about 90 requests against both services and diffs the status, `Content-Type`, `Allow`, key order and values. Generated keys, IDs and timestamps are replaced with placeholders, and timestamps must be in Go's RFC 3339 form. The run covers routing (404/405), auth header handling, login validation, DPoP thumbprints, QR, Watch Later, playlists and LTT search. Both scrapers ingest the same posts from a mock Floatplane endpoint.

```bash
# Databases
docker run -d --rm --name fn-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=floatnative -p 127.0.0.1:55432:5432 postgres:16-alpine
docker run -d --rm --name fn-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=floatnative \
  -e MYSQL_USER=floatnative -e MYSQL_PASSWORD=floatnative -p 127.0.0.1:53306:3306 mysql:8.4

# Mock Floatplane (from packages/api-php)
php -S 127.0.0.1:18090 tests/mock-floatplane.php &

# Go API (from packages/api-go). TZ=UTC matches its Alpine container.
TZ=UTC DB_HOST=127.0.0.1 DB_PORT=55432 DB_USER=postgres DB_PASSWORD=postgres DB_NAME=floatnative PORT=18080 \
  FLOATPLANE_API_URL=http://127.0.0.1:18090/api FLOATPLANE_SAILS_SID=parity-test-sid go run ./cmd/api &

# PHP API (from packages/api-php)
export DB_HOST=127.0.0.1 DB_PORT=53306 DB_USER=floatnative DB_PASSWORD=floatnative DB_NAME=floatnative \
  FLOATPLANE_API_URL=http://127.0.0.1:18090/api FLOATPLANE_SAILS_SID=parity-test-sid
php bin/migrate.php && php bin/update-ltt-posts.php
php -S 127.0.0.1:18081 public/index.php &

php tests/parity.php http://127.0.0.1:18080 http://127.0.0.1:18081
```

## API reference

The endpoints and payloads are the same as the Go service's; see [`packages/api-go/README.md`](../api-go/README.md#api-endpoints-reference).
