# Floatplane Companion API

A lightweight API service that provides additional features (playlists, watch later) for the FloatNative iOS/tvOS app.

## Features

- **Authentication:**
  - Piggy-backs on Floatplane token authentication and verifies user before handing out our own API key
  - QR code authentication flow for seamless device login
  - Secure session management with automatic expiration
- **Playlist Features:**
  - Full CRUD operations for user playlists
  - Ownership validation (users can only access their own playlists)
  - Watch Later restrictions:
    - Cannot be deleted (403 Forbidden)
    - Cannot be renamed (403 Forbidden)
    - Can have video_ids updated
  - "Watch Later" name is reserved (case-insensitive)
  - Proper error codes (400, 403, 404, 500)
- Watch Later Features:
  - Automatic playlist creation on first access
  - No playlist ID needed in URLs (cleaner API)
  - Idempotent add/remove operations
  - Database constraint ensures one Watch Later per user
  - Returns only essential fields for mobile app
- Deploys serverless on Cloudflare Workers

## Architecture Overview

**Before (Traditional Server):**
- Fastify web server
- TypeORM + SQLite/MySQL
- Node.js runtime
- Requires VPS/server hosting

**After (Cloudflare Workers):**
- Hono web framework (Workers-optimized)
- Drizzle ORM + D1 database (SQLite-compatible)
- Cloudflare Workers runtime (V8 isolates)
- Serverless, edge-deployed globally

## Deployment

Quick deploy to Cloudflare Workers:

```bash
# 1. Login to Cloudflare
pnpm wrangler login

# 2. Create database
pnpm wrangler d1 create floatnative-db

# 3. Update wrangler.toml with database_id

# 4. Run migrations
pnpm db:migrate:remote

# 5. Deploy
pnpm run deploy
```

### Common Commands

```bash
# Development
pnpm dev                    # Start local development server

# Deployment
pnpm wrangler login         # Authenticate with Cloudflare
pnpm run deploy             # Deploy to production

# Database
pnpm db:generate            # Generate new migration files
pnpm db:migrate:local       # Run migrations on local database
pnpm db:migrate:remote      # Run migrations on production database

# Monitoring
pnpm tail                   # Stream production logs

# Secrets Management
pnpm wrangler secret put FLOATPLANE_SAILS_SID  # Update Floatplane token
pnpm wrangler secret list                      # List all secrets
pnpm wrangler secret delete FLOATPLANE_SAILS_SID  # Delete a secret
```

### Maintaining the Floatplane Token

The API uses a scheduled task to scrape LTT Floatplane posts hourly. This requires a valid Floatplane `sails.sid` cookie stored as a Worker Secret.

**When the token expires**, you'll receive email notifications (see Email Alerting for Token Expiration). To update:

```bash
# Update the token
pnpm wrangler secret put FLOATPLANE_SAILS_SID

# When prompted, paste your new sails.sid cookie value
```

**To get your sails.sid cookie:**
1. Log in to floatplane.com in your browser
2. Open DevTools (F12) → Application/Storage → Cookies
3. Copy the value of the `sails.sid` cookie
4. Paste it when prompted

**Note:** No redeployment needed - secrets update immediately!

### Email Alerting for Token Expiration

The API can automatically send email alerts when the Floatplane token becomes invalid or expires. This uses the Mailgun API for email delivery.

**Setup:**

1. **Get a Mailgun domain and API key:**
   - Sign up at [mailgun.com](https://www.mailgun.com/)
   - Set up a sending domain (or use Mailgun's sandbox domain for testing)
   - Get your API key from the dashboard

2. **Configure the domain in `wrangler.toml`:**
```toml
[vars]
MAILGUN_DOMAIN = "mg.yourdomain.com"  # Your Mailgun sending domain
```

3. **Configure the secrets:**
```bash
# Set your Mailgun API key
pnpm wrangler secret put MAILGUN_API_KEY

# Set the email address to receive alerts
pnpm wrangler secret put ALERT_EMAIL
```

4. **Deploy and verify:**
```bash
pnpm run deploy

# Check the logs after the scheduled task runs
pnpm tail
```

**Email Alert Details:**
- **Subject:** 🚨 FloatNative API: Invalid Floatplane Token
- **When sent:** Automatically when the scheduled task encounters a 401/403 error
- **Content:** Instructions on how to update the token with step-by-step guidance
- **Sender:** `alerts@{MAILGUN_DOMAIN}` (configured in wrangler.toml)

**Note:** Email alerting is optional. If `MAILGUN_API_KEY`, `MAILGUN_DOMAIN`, or `ALERT_EMAIL` are not configured, the API will log a warning but continue to operate normally.

### Cloudflare Workers Rate Limiting

The API implements comprehensive rate limiting using Cloudflare Workers Rate Limiting API to protect against abuse and ensure fair usage across all endpoints.

**Rate Limiting Strategy:**

| Endpoint | Auth Required | Rate Limit | Key Type |
|----------|---------------|------------|----------|
| `/`, `/health` | No | 200/min | IP |
| `/auth/login`, `/auth/register` | No | 20/min | IP |
| `/auth/qr/submit` | No | 20/min | IP |
| `/auth/qr/generate` | No | 30/min | IP |
| `/auth/qr/poll/:id` | No | 120/min | Session ID |
| `/playlists/*` (reads) | Yes | 100/min | API Key |
| `/playlists/*` (writes) | Yes | 60/min | API Key |
| `/watch-later/*` (reads) | Yes | 100/min | API Key |
| `/watch-later/*` (writes) | Yes | 60/min | API Key |
| `/ltt/search` | Yes | 50/min | API Key |
| `/public/qr-login.html` | No | 100/min | IP |

**Rate Limit Keys:**
- **IP**: Unauthenticated endpoints are rate limited by client IP address (from `CF-Connecting-IP` header)
- **API Key**: Authenticated endpoints are rate limited per user (by their API key)
- **Session ID**: QR polling is rate limited per QR session to allow multiple concurrent sessions

**Rate Limit Exceeded Response:**
When a rate limit is exceeded, the API returns a `429 Too Many Requests` status with:
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later."
}
```

**Configuration:**
Rate limiters are configured in `wrangler.toml` with unique namespace IDs. All limits use a 60-second period as required by Cloudflare's Rate Limiting API.

### Date & Timestamp Format

**Database Storage:**
- Format: SQLite datetime `YYYY-MM-DD HH:MM:SS`
- Example: `2025-11-21 22:20:00`
- Used in: All `created_at`, `updated_at`, `expires_at` columns

**API Responses:**
- Format: ISO 8601 `YYYY-MM-DDTHH:MM:SS.sssZ`
- Example: `2025-11-21T22:20:00.000Z`
- Conversion: Automatic via `formatDateToISO()` helper
- Used in: All timestamp fields in JSON responses

---

## API Endpoints Reference

Complete documentation of all available endpoints.

### Authentication

#### POST /auth/login
**Active - Primary Authentication Method**

Login a user using their Floatplane OAuth access token. This endpoint supports DPoP (Demonstrating Proof-of-Possession) to bind the session to the client device.

**Request:**
```json
{
  "access_token": "string",       // Floatplane OAuth access token
  "dpop_proof": "string",         // DPoP proof JWT
  "device_info": "string"         // Optional, for session tracking
}
```

**Response (200):**
```json
{
  "api_key": "uuid",
  "floatplane_user_id": "string",
  "message": "User registered successfully" | "User logged in successfully"
}
```

**Errors:**
- `400` - Missing required fields or invalid DPoP proof
- `401` - Invalid or expired Floatplane token
- `500` - Internal server error

**Rate Limit:** 20 requests per minute

#### POST /auth/logout
Invalidate the current user's API key. Requires authentication.

**Request:**
- Headers: `Authorization: Bearer {api_key}`

**Response (200):**
```json
{
  "message": "Logged out successfully. API key invalidated."
}
```

#### DEPRECATED POST /auth/register
Register or login a user using their Floatplane session cookie.

**Request:**
```json
{
  "sails_sid": "string"  // Floatplane sails.sid cookie value
}
```

**Response (200):**
```json
{
  "api_key": "uuid",
  "floatplane_user_id": "string",
  "message": "User registered successfully" | "User logged in successfully"
}
```

**Errors:**
- `400` - Missing or invalid sails_sid
- `401` - Invalid or expired Floatplane cookie
- `502` - Failed to validate with Floatplane API
- `500` - Internal server error

---

### DEPRECATED QR Code Authentication

QR code authentication provides a seamless way for users to authenticate their devices by scanning a QR code and entering their Floatplane token on their phone.

#### Flow Overview

1. **Frontend app requests a QR code session** via `POST /auth/qr/generate`
2. **Frontend displays the returned URL as a QR code** (recommend sending URL, not base64 image)
3. **User scans QR code** which opens a mobile-friendly web page
4. **User pastes their Floatplane token** (sails.sid) into the form and clicks "Login"
5. **Backend validates the token** and saves it to the session
6. **Frontend polls** `GET /auth/qr/poll/:sessionId` until the session is completed
7. **Poll returns the API key** when authentication is successful

#### DEPRECATED POST /auth/qr/generate
Generate a new QR code session.

**Request:**
```json
{
  "device_info": "string"  // Optional, for tracking purposes
}
```

**Response (201):**
```json
{
  "session_id": "uuid",
  "login_url": "https://api.example.com/public/qr-login.html?session=uuid",
  "expires_at": "ISO 8601 timestamp",
  "expires_in_seconds": 600
}
```

**Rate Limit:** 10 requests per minute

**Notes:**
- Sessions expire after 10 minutes
- The `login_url` should be encoded as a QR code and displayed to the user
- Recommended: Send the URL and let frontend render QR code (more flexible)

---

#### DEPRECATED POST /auth/qr/submit
Submit a Floatplane token for a QR session. This endpoint is called from the web page that opens when scanning the QR code.

**Request:**
```json
{
  "session_id": "uuid",
  "sails_sid": "string"  // Floatplane sails.sid cookie value
}
```

**Response (200):**
```json
{
  "message": "Login successful! You can now close this tab.",
  "success": true
}
```

**Rate Limit:** 5 requests per minute

**Errors:**
- `400` - Missing fields, session expired, or session invalid
- `401` - Invalid Floatplane token
- `404` - QR session not found
- `500` - Internal server error

---

#### DEPRECATED GET /auth/qr/poll/:sessionId
Poll for QR session completion. Frontend should call this endpoint repeatedly until status changes from "pending".

**Response (200) - Pending:**
```json
{
  "status": "pending",
  "message": "Waiting for login",
  "expires_in_seconds": 450
}
```

**Response (200) - Completed:**
```json
{
  "status": "completed",
  "api_key": "uuid",
  "sails_sid": "string",
  "floatplane_user_id": "string",
  "message": "Login completed successfully"
}
```

**Note:** The `sails_sid` field contains the Floatplane session cookie that the client can use to authenticate directly with Floatplane's API.

**Response (200) - Expired:**
```json
{
  "status": "expired",
  "message": "QR session has expired"
}
```

**Rate Limit:** 60 requests per minute (allows frequent polling)

**Errors:**
- `404` - QR session not found
- `500` - Internal server error

**Polling Recommendations:**
- Poll every 2-3 seconds
- Implement exponential backoff if needed
- Stop polling after 10 minutes or when status is "completed" or "expired"
- Consider Server-Sent Events (SSE) for real-time updates instead of polling

---

### Health Check

#### GET /health
Check API health status.

**Response (200):**
```json
{
  "status": "ok",
  "timestamp": "ISO 8601 timestamp"
}
```

#### GET /
Get API information.

**Response (200):**
```json
{
  "name": "Floatplane Companion API",
  "version": "1.0.0",
  "status": "running"
}
```

---

### Playlists

All playlist endpoints require authentication via `Authorization: Bearer {api_key}` header.

#### GET /playlists
Get all playlists for the authenticated user.

**Query Parameters:**
- `include_watch_later` (optional): `"true"` | `"false"` (default: `"false"`)

**Response (200):**
```json
{
  "playlists": [
    {
      "id": "uuid",
      "floatplane_user_id": "string",
      "name": "string",
      "is_watch_later": false,
      "video_ids": ["string"],
      "created_at": "ISO 8601",
      "updated_at": "ISO 8601"
    }
  ],
  "count": 0
}
```

**Errors:**
- `401` - Missing or invalid API key
- `500` - Internal server error

#### POST /playlists
Create a new playlist.

**Request:**
```json
{
  "name": "string",           // Required, 1-255 characters
  "video_ids": ["string"]     // Optional, defaults to []
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "floatplane_user_id": "string",
  "name": "string",
  "is_watch_later": false,
  "video_ids": ["string"],
  "created_at": "ISO 8601",
  "updated_at": "ISO 8601"
}
```

**Errors:**
- `400` - Invalid input (missing name, name too long, "Watch Later" reserved)
- `401` - Missing or invalid API key
- `500` - Internal server error

#### PUT /playlists/:id
Update an existing playlist.

**Request:**
```json
{
  "name": "string",           // Optional
  "video_ids": ["string"]     // Optional
}
```
Note: At least one field must be provided.

**Response (200):**
```json
{
  "id": "uuid",
  "floatplane_user_id": "string",
  "name": "string",
  "is_watch_later": boolean,
  "video_ids": ["string"],
  "created_at": "ISO 8601",
  "updated_at": "ISO 8601"
}
```

**Errors:**
- `400` - No fields provided or invalid data
- `401` - Missing or invalid API key
- `403` - Attempting to rename Watch Later playlist
- `404` - Playlist not found or access denied
- `500` - Internal server error

#### PATCH /playlists/:id/add
Add a single video to a playlist (idempotent).

**Request:**
```json
{
  "video_id": "string"  // Required
}
```

**Response (200):**
Returns full playlist object (same as PUT response).

**Errors:**
- `400` - Missing or invalid video_id
- `401` - Missing or invalid API key
- `404` - Playlist not found or access denied
- `500` - Internal server error

#### PATCH /playlists/:id/remove
Remove a single video from a playlist (idempotent).

**Request:**
```json
{
  "video_id": "string"  // Required
}
```

**Response (200):**
Returns full playlist object (same as PUT response).

**Errors:**
- `400` - Missing or invalid video_id
- `401` - Missing or invalid API key
- `404` - Playlist not found or access denied
- `500` - Internal server error

#### DELETE /playlists/:id
Delete a playlist.

**Response (204):**
No content.

**Errors:**
- `401` - Missing or invalid API key
- `403` - Attempting to delete Watch Later playlist
- `404` - Playlist not found or access denied
- `500` - Internal server error

---

### Watch Later

All Watch Later endpoints require authentication and automatically create the Watch Later playlist if it doesn't exist (except REMOVE).

#### GET /watch-later
Get the Watch Later playlist.

**Response (200):**
```json
{
  "id": "uuid",
  "video_ids": ["string"],
  "updated_at": "ISO 8601"
}
```

**Errors:**
- `401` - Missing or invalid API key
- `500` - Internal server error

#### PUT /watch-later
Replace the entire Watch Later video list.

**Request:**
```json
{
  "video_ids": ["string"]  // Required, can be empty array
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "video_ids": ["string"],
  "updated_at": "ISO 8601"
}
```

**Errors:**
- `400` - Missing or invalid video_ids
- `401` - Missing or invalid API key
- `500` - Internal server error

#### PATCH /watch-later/add
Add a single video to Watch Later (idempotent).

**Request:**
```json
{
  "video_id": "string"  // Required
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "video_ids": ["string"],
  "updated_at": "ISO 8601"
}
```

**Errors:**
- `400` - Missing or invalid video_id
- `401` - Missing or invalid API key
- `500` - Internal server error

#### PATCH /watch-later/remove
Remove a single video from Watch Later (idempotent).

**Request:**
```json
{
  "video_id": "string"  // Required
}
```

**Response (200):**
```json
{
  "id": "uuid",
  "video_ids": ["string"],
  "updated_at": "ISO 8601"
}
```

**Errors:**
- `400` - Missing or invalid video_id
- `401` - Missing or invalid API key
- `404` - Watch Later playlist doesn't exist yet
- `500` - Internal server error

---

### LTT Posts

All LTT Posts endpoints require authentication via `Authorization: Bearer {api_key}` header.

#### GET /ltt/search

Search LTT Floatplane posts by title. Posts are updated hourly via a scheduled task.

**Query Parameters:**
- `q` (required): Search query string

**Response (200):**
```json
{
  "query": "string",
  "count": 0,
  "results": [
    {
      "id": "string",
      "title": "string",
      "creator_name": "string",
      "channel_title": "string",
      "channel_icon_url": "string",
      "thumbnail_url": "string",
      "video_duration": 0,
      "has_video": true,
      "release_date": "ISO 8601"
    }
  ]
}
```

**Notes:**
- Returns exact matches first, followed by partial matches
- Maximum 50 results per search
- Results are ordered by release date (newest first) for partial matches
- Search is case-insensitive

**Errors:**
- `400` - Missing or empty query parameter
- `401` - Missing or invalid API key
- `500` - Internal server error
