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
		SELECT id, status, floatplane_user_id, api_key, expires_at
		FROM qr_sessions WHERE id = $1
	`, id).Scan(&session.ID, &session.Status, &session.FloatplaneUserID, &session.APIKey, &session.ExpiresAt)

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
		
		respondJSON(w, http.StatusOK, map[string]string{
			"status":             "completed",
			"floatplane_user_id": fpUserID,
			"api_key":            apiKey,
		})
		return
	}
	
	respondJSON(w, http.StatusOK, map[string]string{"status": session.Status})
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
