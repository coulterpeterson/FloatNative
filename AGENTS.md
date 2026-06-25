# AGENTS.md

## Cursor Cloud specific instructions

FloatNative is a pnpm + Gradle monorepo. The native apps in `apps/ios` (SwiftUI)
and `apps/android` (Jetpack Compose) require Xcode / Android Studio and cannot be
built or run on the Linux cloud VM. The runnable, testable backend here is the
**TypeScript Companion API** (`packages/api`). Standard commands live in
`README.md`, the root `package.json` scripts, and `packages/api/README.md`.

### Running the Companion API locally (important gotchas)

- Run it from `packages/api` with `npx wrangler dev` (Cloudflare Workers local,
  port 8787, auto local SQLite via miniflare). Bindings show "local".
- GOTCHA — do **not** rely on `pnpm api:dev` (i.e. `wrangler dev --env dev`) for
  local testing as-is: `[[ratelimits]]` and several `[vars]` are defined only at
  the top level of `packages/api/wrangler.toml`, and Wrangler does **not** inherit
  them into the named `[env.dev]`. As a result every rate-limited route (including
  `/` and `/health`) throws and returns `500` under `--env dev`. The default
  (top-level) environment defines the rate limiters, so `npx wrangler dev` (no
  `--env`) works for local testing.
- The default env's local D1 database is `floatnative-db`; the `dev` env's is
  `floatnative-db-dev`. Migrate whichever name matches the env you run.
- The DB migration npm script only applies `migrations/0000_curious_ink.sql`.
  For the full schema (the `device_sessions` table that auth depends on), also
  apply `0001_drop_system_config.sql` and `0002_add_device_sessions.sql`, e.g.:
  `npx wrangler d1 execute floatnative-db --local --file=./migrations/0002_add_device_sessions.sql`

### Auth for local testing

Authenticated endpoints (`/playlists/*`, `/watch-later/*`, `/ltt/*`) require an
API key stored in `device_sessions`; obtaining one normally requires a real
Floatplane OAuth login (external service). For local end-to-end testing, seed a
`users` row plus a matching `device_sessions` row directly and use that
`api_key` as the `Authorization: Bearer <key>` token, e.g.:

```
npx wrangler d1 execute floatnative-db --local --command "INSERT INTO users (floatplane_user_id, api_key) VALUES ('local-user','unused'); INSERT INTO device_sessions (id, floatplane_user_id, api_key, dpop_jkt) VALUES ('s1','local-user','local-api-key','jkt1');"
```

The hourly LTT-search scraper additionally needs a real `FLOATPLANE_SAILS_SID`
secret; the rest of the API runs fine without it.

### Other components

- `apps/chrome-extension`: `pnpm extension:build` (webpack). Buildable here.
- `packages/api-go`: an alternative self-hosting variant of the API. It targets
  Go 1.23 (`go.mod`) and a Postgres stack via `docker-compose`. The base VM has
  Go 1.22 and no Docker, so it does not build/run as-is — prefer the TS API for
  local work unless Go 1.23 + Docker are provisioned.
- `packages/openapi`: one-off Swift/Kotlin model codegen (needs Java + network).
- `tools/floatcli`: Python diagnostic CLI; hits real Floatplane + the companion
  API, so it needs live credentials.
