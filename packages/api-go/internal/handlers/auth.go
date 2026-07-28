package handlers

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/middleware"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/models"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/services"
	"github.com/go-chi/chi/v5"
)

func GenerateQR(w http.ResponseWriter, r *http.Request) {
	// Generate random ID
	idBytes := make([]byte, 16)
	rand.Read(idBytes)
	id := hex.EncodeToString(idBytes)

	expiresAt := time.Now().Add(5 * time.Minute)

	var session models.QRSession
	err := database.Pool.QueryRow(r.Context(), `
		INSERT INTO qr_sessions (id, status, expires_at, created_at)
		VALUES ($1, 'pending', $2, $3)
		RETURNING id, status, expires_at, created_at
	`, id, expiresAt, time.Now()).Scan(
		&session.ID, &session.Status, &session.ExpiresAt, &session.CreatedAt,
	)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to create QR session")
		return
	}

	respondJSON(w, http.StatusCreated, map[string]interface{}{
		"id":         session.ID,
		"expires_at": session.ExpiresAt,
	})
}

func PollQR(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "Bad Request", "Missing ID")
		return
	}

	var session models.QRSession
	err := database.Pool.QueryRow(r.Context(), `
		SELECT id, status, floatplane_user_id, sails_sid, api_key, expires_at
		FROM qr_sessions WHERE id = $1
	`, id).Scan(&session.ID, &session.Status, &session.FloatplaneUserID, &session.SailsSID, &session.APIKey, &session.ExpiresAt)

	if err != nil {
		respondError(w, http.StatusNotFound, "Not Found", "Session not found")
		return
	}

	if time.Now().After(session.ExpiresAt) {
		respondJSON(w, http.StatusOK, map[string]string{"status": "expired"})
		return
	}

	if session.Status == "pending" {
		respondJSON(w, http.StatusOK, map[string]string{"status": "pending"})
		return
	}

	if session.Status == "completed" {
		fpUserID := ""
		if session.FloatplaneUserID != nil {
			fpUserID = *session.FloatplaneUserID
		}
		apiKey := ""
		if session.APIKey != nil {
			apiKey = *session.APIKey
		}
		sailsSid := ""
		if session.SailsSID != nil {
			sailsSid = *session.SailsSID
		}
		
		respondJSON(w, http.StatusOK, map[string]string{
			"status":             "completed",
			"floatplane_user_id": fpUserID,
			"sails_sid":          sailsSid,
			"api_key":            apiKey,
		})
		return
	}
	
	respondJSON(w, http.StatusOK, map[string]string{"status": session.Status})
}

type SubmitQRRequest struct {
	SessionID string `json:"session_id"`
	SailsSID  string `json:"sails_sid"`
}

func SubmitQR(w http.ResponseWriter, r *http.Request) {
	var req SubmitQRRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid request body")
		return
	}

	if req.SessionID == "" || req.SailsSID == "" {
		respondError(w, http.StatusBadRequest, "Bad Request", "Missing session_id or sails_sid")
		return
	}

	keyBytes := make([]byte, 16)
	rand.Read(keyBytes)
	apiKey := hex.EncodeToString(keyBytes)
	now := time.Now()
	_, err := database.Pool.Exec(r.Context(), `
		UPDATE qr_sessions
		SET status = 'completed', sails_sid = $1, api_key = $2, completed_at = $3
		WHERE id = $4 AND status = 'pending' AND expires_at > $3
	`, req.SailsSID, apiKey, now, req.SessionID)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Error", "Failed to complete QR session")
		return
	}

	respondJSON(w, http.StatusOK, map[string]string{"message": "QR login successful"})
}

func QRLoginHTML(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write([]byte(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FloatNative TV Sign In</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0a0a0a; color: #fff; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; }
        .card { background: #1a1a1a; padding: 36px 28px; border-radius: 16px; max-width: 420px; width: 100%; box-shadow: 0 10px 40px rgba(0,0,0,0.6); text-align: center; }
        h1 { font-size: 24px; margin-bottom: 8px; color: #0099ff; font-weight: 700; }
        p { font-size: 14px; color: #aaa; margin-bottom: 24px; line-height: 1.5; }
        .btn-primary { width: 100%; padding: 16px; border-radius: 10px; border: none; background: #0099ff; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; transition: background 0.2s; display: block; text-decoration: none; }
        .btn-primary:hover { background: #0088ee; }
        .divider { margin: 24px 0; border-top: 1px solid #333; position: relative; }
        .divider span { background: #1a1a1a; padding: 0 10px; color: #666; font-size: 12px; position: absolute; top: -9px; left: 50%; transform: translateX(-50%); }
        input { width: 100%; padding: 14px; border-radius: 8px; border: 1px solid #333; background: #262626; color: #fff; margin-bottom: 14px; font-size: 14px; font-family: monospace; }
        .btn-secondary { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid #444; background: transparent; color: #ccc; font-size: 14px; cursor: pointer; }
        .success { color: #4caf50; font-size: 18px; font-weight: 600; margin-top: 20px; display: none; }
        .error { color: #f44336; font-size: 14px; margin-top: 15px; display: none; }
    </style>
</head>
<body>
    <div class="card" id="mainCard">
        <h1>FloatNative TV Sign In</h1>
        <p>Sign in on Floatplane.com to automatically connect your TV app!</p>

        <button class="btn-primary" id="autoBtn" onclick="tryAutoSync()">Sign In with Floatplane</button>

        <div class="divider"><span>OR PASTE MANUALLY</span></div>

        <form id="manualForm">
            <input type="text" id="cookieInput" placeholder="Paste sails.sid cookie (s%3A...)" required />
            <button type="submit" class="btn-secondary" id="submitBtn">Submit Cookie</button>
        </form>

        <div id="success" class="success">✓ Signed in! Your TV is connecting now.</div>
        <div id="error" class="error"></div>
    </div>
    <script>
        const params = new URLSearchParams(window.location.search);
        const session = params.get('session');
        const autoBtn = document.getElementById('autoBtn');
        const manualForm = document.getElementById('manualForm');
        const cookieInput = document.getElementById('cookieInput');
        const submitBtn = document.getElementById('submitBtn');
        const successEl = document.getElementById('success');
        const errorEl = document.getElementById('error');

        async function tryAutoSync() {
            autoBtn.disabled = true;
            autoBtn.textContent = 'Checking Floatplane session...';
            errorEl.style.display = 'none';

            try {
                const res = await fetch('https://www.floatplane.com/api/v3/user/self', { credentials: 'include' });
                if (res.ok) {
                    let match = document.cookie.match(/(?:^|; )sails\.sid=([^;]*)/);
                    if (match && match[1]) {
                        submitCookie(decodeURIComponent(match[1]));
                        return;
                    }
                }
            } catch (err) {
                console.log('CORS/Cookie check redirect needed');
            }

            window.location.href = 'https://www.floatplane.com/login';
        }

        async function submitCookie(sailsSid) {
            try {
                const res = await fetch('/auth/qr/submit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ session_id: session, sails_sid: sailsSid })
                });
                if (res.ok) {
                    autoBtn.style.display = 'none';
                    manualForm.style.display = 'none';
                    document.querySelector('.divider').style.display = 'none';
                    successEl.style.display = 'block';
                } else {
                    const data = await res.json();
                    showError(data.message || 'Failed to connect TV.');
                }
            } catch (err) {
                showError('Network error. Please try again.');
            }
        }

        manualForm.addEventListener('submit', (e) => {
            e.preventDefault();
            let cookie = cookieInput.value.trim();
            if (cookie.startsWith('sails.sid=')) {
                cookie = cookie.replace('sails.sid=', '').trim();
            }
            if (cookie) submitCookie(cookie);
        });

        function showError(msg) {
            errorEl.textContent = msg;
            errorEl.style.display = 'block';
            autoBtn.disabled = false;
            autoBtn.textContent = 'Sign In with Floatplane';
        }
    </script>
</body>
</html>`))
}

type LoginRequest struct {
	AccessToken string `json:"access_token"`
	DPoPProof   string `json:"dpop_proof"`
	DeviceInfo  string `json:"device_info"`
}

func Login(w http.ResponseWriter, r *http.Request) {
	var req LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid body")
		return
	}

	if req.AccessToken == "" || req.DPoPProof == "" {
		respondError(w, http.StatusBadRequest, "Bad Request", "Missing access_token or dpop_proof")
		return
	}

	// 1. Extract DPoP JKT
	dpopJkt, err := services.ExtractDPoPJKT(req.DPoPProof)
	if err != nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid DPoP proof")
		return
	}

	// 2. Validate Token
	fpUserID, err := services.ValidateFloatplaneTokenLocally(req.AccessToken)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "Invalid Floatplane token")
		return
	}

	// 3. Ensure User Exists
	var user models.User
	err = database.Pool.QueryRow(r.Context(), `
		SELECT floatplane_user_id, api_key FROM users WHERE floatplane_user_id = $1
	`, fpUserID).Scan(&user.FloatplaneUserID, &user.APIKey)

	isNewUser := false
	if err != nil {
		// Create User
		newAPIKey := generateRandomKey()
		err = database.Pool.QueryRow(r.Context(), `
			INSERT INTO users (floatplane_user_id, api_key, created_at, last_accessed_at)
			VALUES ($1, $2, $3, $3)
			RETURNING floatplane_user_id, api_key
		`, fpUserID, newAPIKey, time.Now()).Scan(&user.FloatplaneUserID, &user.APIKey)
		
		if err != nil {
			respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to create user")
			return
		}
		isNewUser = true

		// Create WatchLater
		_, _ = database.Pool.Exec(r.Context(), `
			INSERT INTO playlists (floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
			VALUES ($1, 'Watch Later', true, '{}', $2, $2)
			ON CONFLICT DO NOTHING
		`, fpUserID, time.Now())
	}

	// 4. Check Device Session
	var session models.DeviceSession
	err = database.Pool.QueryRow(r.Context(), `
		SELECT api_key FROM device_sessions WHERE dpop_jkt = $1
	`, dpopJkt).Scan(&session.APIKey)

	finalAPIKey := ""

	if err == nil {
		// Session Exists
		finalAPIKey = session.APIKey
		_, _ = database.Pool.Exec(r.Context(), `
			UPDATE device_sessions SET last_accessed_at = $1 WHERE dpop_jkt = $2
		`, time.Now(), dpopJkt)
	} else {
		// Create Session
		finalAPIKey = generateRandomKey()
		// Use request device_info
		devInfo := ""
		if req.DeviceInfo != "" {
			devInfo = req.DeviceInfo
		}

		idBytes := make([]byte, 16)
		rand.Read(idBytes)
		sessID := hex.EncodeToString(idBytes)

		_, err = database.Pool.Exec(r.Context(), `
			INSERT INTO device_sessions (id, floatplane_user_id, api_key, dpop_jkt, device_info, created_at, last_accessed_at)
			VALUES ($1, $2, $3, $4, $5, $6, $6)
		`, sessID, fpUserID, finalAPIKey, dpopJkt, devInfo, time.Now())

		if err != nil {
			respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to create device session")
			return
		}
	}

	msg := "User logged in successfully"
	if isNewUser {
		msg = "User registered successfully"
	}

	respondJSON(w, http.StatusOK, map[string]string{
		"api_key":            finalAPIKey,
		"floatplane_user_id": fpUserID,
		"message":            msg,
	})
}

func Logout(w http.ResponseWriter, r *http.Request) {
	// Authenticate via context (AuthMiddleware must run first)
	_, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}

	// Extract API Key from Header to identify which session to logout
	authHeader := r.Header.Get("Authorization")
	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "Invalid Authorization header")
		return
	}
	currentAPIKey := parts[1]

	// Rotate API Key in device_sessions
	// This effectively invalidates the current key
	newKey := generateRandomKey()
	
	result, err := database.Pool.Exec(r.Context(), `
		UPDATE device_sessions SET api_key = $1 WHERE api_key = $2
	`, newKey, currentAPIKey)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to logout")
		return
	}
	
	rows := result.RowsAffected()
	if rows == 0 {
		// Fallback: If not found in device_sessions, maybe try users table?
		// But middleware checks device_sessions first. 
		// If we are here, middleware passed, so it MUST be in device_sessions (or users).
		// Let's also try updating users table just in case it was a legacy session.
		_, _ = database.Pool.Exec(r.Context(), `
			UPDATE users SET api_key = $1 WHERE api_key = $2
		`, newKey, currentAPIKey)
	}

	respondJSON(w, http.StatusOK, map[string]string{
		"message": "Logged out successfully. API key invalidated.",
	})
}

func generateRandomKey() string {
	b := make([]byte, 32)
	rand.Read(b)
	return hex.EncodeToString(b)
}
