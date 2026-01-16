package tests

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/stretchr/testify/assert"
)

func TestQRFlow(t *testing.T) {
	clearDatabase(t)
	r := setupRouter()

	// 1. Generate QR Session
	req, _ := http.NewRequest("POST", "/auth/qr/generate", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code)
	var genResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &genResp)
	sessionID := genResp["id"].(string)
	assert.NotEmpty(t, sessionID)

	// 2. Poll (Should be pending)
	req, _ = http.NewRequest("GET", "/auth/qr/poll/"+sessionID, nil)
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), "pending")

	// 3. Manually simulate completion (Since we can't easily mock the external web page submission in this flow without more logic)
	// We'll update the DB directly to simulate the user scanning and logging in.
	// First, ensure user exists
	userID := "manual_qr_user"
	database.Pool.Exec(context.Background(), `INSERT INTO users (floatplane_user_id, created_at, updated_at) VALUES ($1, NOW(), NOW()) ON CONFLICT DO NOTHING`, userID)
	
	// Update session to completed
	mockAPIKey := "mock_qr_api_key"
	_, err := database.Pool.Exec(context.Background(), 
		`UPDATE qr_sessions SET status = 'completed', floatplane_user_id = $1, api_key = $2, sails_sid = 'sid' WHERE id = $3`, 
		userID, mockAPIKey, sessionID)
	assert.NoError(t, err)

	// 4. Poll (Should be completed)
	req, _ = http.NewRequest("GET", "/auth/qr/poll/"+sessionID, nil)
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	
	var pollResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &pollResp)
	assert.Equal(t, "completed", pollResp["status"])
	assert.Equal(t, mockAPIKey, pollResp["api_key"])
}

func TestLogout(t *testing.T) {
	clearDatabase(t)
	r := setupRouter()
	apiKey := createTestUser(t)

	// Perform Logout
	req, _ := http.NewRequest("POST", "/auth/logout", nil)
	req.Header.Set("Authorization", "Bearer "+apiKey)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify Check: Try to access protected route (Playlists)
	req, _ = http.NewRequest("GET", "/playlists", nil)
	req.Header.Set("Authorization", "Bearer "+apiKey) // Old key
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}
