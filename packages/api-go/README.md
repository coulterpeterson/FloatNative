# Floatplane Companion API (Go)

A high-performance, containerized API service that provides additional features (playlists, watch later, LTT search) for the FloatNative app.

This is a **Go** reimplementation of the previous Cloudflare Workers API, designed for self-hosting on standard Linux servers (e.g., DigitalOcean) using Docker and PostgreSQL.

## Features

-   **High Performance**: Written in Go 1.22+ using `chi` router and `pgx` for PostgreSQL.
-   **Authentication**:
    -   **Floatplane Auth**: Validates Floatplane tokens (including DPoP) to issue secure API keys.
    -   **QR Code Login**: Seamless device authentication flow.
-   **Playlist Management**:
    -   Full CRUD for user playlists.
    -   "Watch Later" reserved playlist with lazy creation.
    -   Idempotent add/remove video operations.
-   **LTT Search**:
    -   Background worker scrapes LTT posts from Floatplane API (hourly).
    -   Fast, DB-backed search endpoint.

## Architecture

-   **Runtime**: Go 1.22+ (Alpine Linux in Docker)
-   **Database**: PostgreSQL 16
-   **Structure**:
    -   `cmd/api`: Entry point.
    -   `internal/handlers`: HTTP request handlers.
    -   `internal/middleware`: Auth and logging middleware.
    -   `internal/models`: Database structs and API types.
    -   `internal/services`: Business logic (Floatplane integration).

## Getting Started

### Prerequisites

-   Docker & Docker Compose

### Local Development

1.  **Clone & Navigate**:
    ```bash
    cd packages/api-go
    ```

2.  **Configuration**:
    Copy `.env.example` to `.env`. For local Docker usage, the default DB credentials work out of the box.
    ```bash
    cp .env.example .env
    ```
    *Note*: To enable the LTT Search scraper, you must provide a valid `FLOATPLANE_SAILS_SID` in `.env`.

3.  **Run with Docker Compose**:
    # Start services (detached)
    ```bash
    docker-compose up -d
    ```
    -   API will be available at `http://localhost:8080`.
    -   PostgreSQL will be available at `localhost:5432`.

4.  **Database Migrations**:
    Migrations in the `migrations/` directory are automatically applied on container startup.

### 4. CloudPanel / NGINX Configuration

If you are using **CloudPanel**, set up a **Reverse Proxy** to expose the API (running on port 8080) to the public internet (port 80/443).

1.  **Create a Site**:
    -   Go to **Sites** -> **Add Site** -> **Create a Generic Node.js / Python Site** (or just a Reverse Proxy if available).
    -   Domain: `api.yourdomain.com`
    -   Port: `8080` (This tells CloudPanel where the app is listening internally)

2.  **NGINX VHost Configuration**:
    If you need to manually configure the NGINX VHost, ensure the `location /` block looks like this:

    ```nginx
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Websocket support (if needed in future)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
    ```

### 5. Updates

To deploy an update:
1.  **Local**: `docker-compose build` and `docker-compose push`.
2.  **Server**: `docker-compose pull` and `docker-compose up -d` (Docker automatically recreates the container with the new image).

## Testing
    
    A helper script is provided to verify the DPoP (Demonstrating Proof-of-Possession) login flow, as it requires generating a valid JWT and DPoP proof.
    
    1.  **Ensure Stack is Running**:
        ```bash
        docker-compose up -d --build
        ```
    
    2.  **Run DPoP Test Script**:
        This runs the test script inside a disposable Go container attached to the API's network.
        ```bash
        docker run --rm -v $(pwd):/app -w /app --network api-go_app_network golang:1.23-alpine go run test_dpop.go
        ```
    
    *Success Output:*
    ```json
    Status: 200 OK
    Response: {"api_key":"...","floatplane_user_id":"...","message":"User logged in successfully"}
    ```

    ### Run Full Integration Suite
    
    To run the comprehensive integration test suite (covering Auth, Playlists, Watch Later, and Database interactions):
    
    ```bash
    docker run --rm -v $(pwd):/app -w /app \
      -e DB_HOST=postgres \
      -e DB_PORT=5432 \
      -e DB_USER=postgres \
      -e DB_PASSWORD=postgres \
      -e DB_NAME=floatnative \
      --network api-go_app_network \
      golang:1.23-alpine \
      go test -v ./tests/...
    ```
    
    ## Deployment (DigitalOcean / Linux Server)

1.  **Prepare Server**: Ensure Docker and Docker Compose are installed.

2.  **Copy Files**: Copy the entire `packages/api-go` directory to your server.

3.  **Setup Environment**:
    Create a `.env` file on the server with secure passwords.
    ```env
    PORT=8080
    DB_HOST=postgres
    DB_PORT=5432
    DB_USER=postgres
    DB_PASSWORD=SecurePassword123!
    DB_NAME=floatnative
    FLOATPLANE_SAILS_SID=your_real_cookie_value
    ```
    *Update `docker-compose.yml` to use these values or pass them via environment if strictly needed, though the provided compose file uses internal styling. Ideally, use a `.env` file that docker-compose automatically reads.*

4.  **Run**:
    ```bash
    ```bash
    docker-compose up -d --build
    ```

5.  **Auto-Restart & Service Configuration**:
    The simplest way to ensure the stack starts on boot is to use Docker's `restart: always` policy (already configured in `docker-compose.yml`).
    
    However, to ensure the Docker daemon itself starts on boot:
    ```bash
    sudo systemctl enable docker
    sudo systemctl start docker
    ```

    For a more robust production setup, you can create a systemd service for this specific app:
    
    **`sudo nano /etc/systemd/system/floatnative-api.service`**
    ```ini
    [Unit]
    Description=FloatNative API Go Service
    Requires=docker.service
    After=docker.service

    [Service]
    Restart=always
    WorkingDirectory=/path/to/floatnative/packages/api-go
    # Shutdown container (if running) when unit is stopped
    ExecStartPre=/usr/local/bin/docker-compose down -v
    ExecStart=/usr/local/bin/docker-compose up
    ExecStop=/usr/local/bin/docker-compose down -v

    [Install]
    WantedBy=multi-user.target
    ```
    
    Enable it:
    ```bash
    sudo systemctl enable floatnative-api
    sudo systemctl start floatnative-api
    ```

## CI/CD: Automated Deployment via GitHub Webhook

To automatically update your DigitalOcean droplet when you push changes to `packages/api-go`, follow these steps to set up a lightweight webhook listener.

### 1. Install & Configure Webhook Listener (On Server)

We will use the lightweight [adnanh/webhook](https://github.com/adnanh/webhook) tool.

1.  **Install**:
    ```bash
    sudo apt-get install webhook
    ```

2.  **Create Deployment Script**:
    Create `deploy.sh` in your project root (e.g. `~/FloatNative/packages/api-go/deploy.sh`):
    ```bash
    #!/bin/bash
    echo "Received deployment webhook: $(date)" >> /var/log/webhook-deploy.log
    
    # Navigate to directory
    cd /path/to/floatnative/packages/api-go || exit

    # Reset local changes (if any) and pull latest
    git reset --hard
    git pull origin main

    # Check if api-go folder was changed (optional optimization, but good for monorepos)
    # This logic assumes the webhook sends the payload, but standard 'webhook' tool simplifies this.
    # Simpler approach: Just always rebuild if the hook triggers.
    
    # Rebuild and restart containers
    docker-compose up -d --build --remove-orphans
    
    # Prune old images to save space
    docker image prune -f
    
    echo "Deployment verified: $(date)" >> /var/log/webhook-deploy.log
    ```
    Make it executable: `chmod +x deploy.sh`

3.  **Configure Webhook**:
    Create `hooks.json`:
    ```json
    [
      {
        "id": "deploy-api-go",
        "execute-command": "/path/to/floatnative/packages/api-go/deploy.sh",
        "command-working-directory": "/path/to/floatnative/packages/api-go",
        "trigger-rule": {
          "match": {
            "type": "payload-hash-sha1",
            "secret": "YOUR_SECRET_WEBHOOK_PASSWORD",
            "parameter": "X-Hub-Signature"
          }
        }
      }
    ]
    ```

4.  **Run Webhook Service**:
    Start it manually to test:
    ```bash
    webhook -hooks hooks.json -verbose
    ```
    (Once tested, run it as a background service/systemd unit).

### 2. Configure GitHub Repository

1.  Go to your Repository Settings -> **Webhooks** -> **Add webhook**.
2.  **Payload URL**: `http://your-droplet-ip:9000/hooks/deploy-api-go` (Default port is 9000).
3.  **Content type**: `application/json`.
4.  **Secret**: Enter the `YOUR_SECRET_WEBHOOK_PASSWORD` you put in `hooks.json`.
5.  **Events**: Select "Just the push event".
6.  Click **Add webhook**.

Now, whenever you push code to the repo, GitHub will ping your server, which will pull the latest code and rebuild the containers.

## API Endpoints Reference

### Authentication

#### POST /auth/login
Login a user using their Floatplane OAuth access token. Supports DPoP (Demonstrating Proof-of-Possession).

**Request:**
```json
{
  "access_token": "string",       // Floatplane OAuth access token
  "dpop_proof": "string",         // DPoP proof JWT
  "device_info": "string"         // Optional
}
```

**Response (200):**
```json
{
  "api_key": "uuid",
  "floatplane_user_id": "string",
  "message": "User logged in successfully"
}
```

#### POST /auth/logout
Invalidate the current user's API key.

**Headers**: `Authorization: Bearer {api_key}`

**Response (200):**
```json
{ "message": "Logged out successfully. API key invalidated." }
```

### QR Code Authentication (Device Login)

#### POST /auth/qr/generate
Generate a new QR code session.

**Response (200):**
```json
{
  "id": "uuid",
  "expires_at": "ISO 8601 timestamp"
}
```

#### GET /auth/qr/poll/{id}
Check status of a QR session.

**Response (200):**
- **Pending**: `{"status": "pending"}`
- **Completed**:
  ```json
  {
    "status": "completed",
    "api_key": "uuid",
    "floatplane_user_id": "string"
  }
  ```
- **Expired**: `{"status": "expired"}`
- **Not Found**: `404`

### Playlists

**Headers**: `Authorization: Bearer {api_key}`

#### GET /playlists
Get all playlists for the authenticated user.

#### POST /playlists
Create a new playlist.
```json
{ "name": "My Playlist", "video_ids": [] }
```

#### PUT /playlists/{id}
Update a playlist.
```json
{ "name": "New Name", "video_ids": ["vid1", "vid2"] }
```

#### DELETE /playlists/{id}
Delete a playlist. (Cannot delete "Watch Later").

#### PATCH /playlists/{id}/add
Add a video (idempotent).
```json
{ "video_id": "string" }
```

#### PATCH /playlists/{id}/remove
Remove a video (idempotent).
```json
{ "video_id": "string" }
```

### Watch Later

**Headers**: `Authorization: Bearer {api_key}`

#### GET /watch-later
Get "Watch Later" playlist. Creates it if it doesn't exist.

#### PUT /watch-later
Replace video list.
```json
{ "video_ids": ["vid1", "vid2"] }
```

#### PATCH /watch-later/add
Add video to Watch Later.
```json
{ "video_id": "string" }
```

#### PATCH /watch-later/remove
Remove video from Watch Later.
```json
{ "video_id": "string" }
```

### LTT Search

**Headers**: `Authorization: Bearer {api_key}`

#### GET /ltt/search?q=query
Search LTT posts by title.

**Response (200):**
```json
[
  {
    "id": "string",
    "title": "string",
    "thumbnail_url": "string",
    "release_date": "ISO 8601",
    ...
  }
]
```
