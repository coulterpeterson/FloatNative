package middleware

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/models"
)

type contextKey string

const UserContextKey contextKey = "user"

type ErrorResponse struct {
	Error   string `json:"error"`
	Message string `json:"message"`
}

func AuthMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			respondError(w, http.StatusUnauthorized, "Unauthorized", "Missing or invalid Authorization header. Expected: Bearer {api_key}")
			return
		}

		parts := strings.Split(authHeader, " ")
		if len(parts) != 2 || parts[0] != "Bearer" {
			respondError(w, http.StatusUnauthorized, "Unauthorized", "Missing or invalid Authorization header. Expected: Bearer {api_key}")
			return
		}

		apiKey := parts[1]

		ctx := r.Context()
		
		// 1. Find device session
		var session models.DeviceSession
		// Note: We don't select 'id' here because models.DeviceSession has it but scan order matters.
		// Querying specific columns matches Scan order
		err := database.Pool.QueryRow(ctx, `
			SELECT floatplane_user_id, api_key, dpop_jkt, device_info, created_at, last_accessed_at 
			FROM device_sessions WHERE api_key = $1
		`, apiKey).Scan(
			&session.FloatplaneUserID,
			&session.APIKey,
			&session.DPoPJKT,
			&session.DeviceInfo,
			&session.CreatedAt,
			&session.LastAccessedAt,
		)

		if err != nil {
			// Check if no rows? pgx returns error on no rows
			respondError(w, http.StatusUnauthorized, "Unauthorized", "Invalid API key")
			return
		}

		// 2. Find user
		var user models.User
		err = database.Pool.QueryRow(ctx, `
			SELECT floatplane_user_id, api_key, created_at, last_accessed_at
			FROM users WHERE floatplane_user_id = $1
		`, session.FloatplaneUserID).Scan(
			&user.FloatplaneUserID,
			&user.APIKey, // Note: This might be the "old" api key if users table has one, or same.
			&user.CreatedAt,
			&user.LastAccessedAt,
		)

		if err != nil {
			respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
			return
		}

		// 3. Update last_accessed_at async (or sync if strict)
		// We'll do it sync for simplicity
		_, _ = database.Pool.Exec(ctx, "UPDATE device_sessions SET last_accessed_at = $1 WHERE api_key = $2", time.Now(), apiKey)

		// 4. Set user in context
		ctx = context.WithValue(ctx, UserContextKey, &user)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func respondError(w http.ResponseWriter, code int, errType, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(ErrorResponse{
		Error:   errType,
		Message: message,
	})
}
