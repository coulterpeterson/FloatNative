package tests

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestWatchLater(t *testing.T) {
	clearDatabase(t)
	r := setupRouter()
	apiKey := createTestUser(t)

	// 1. Get Watch Later (Should be created lazily)
	req, _ := http.NewRequest("GET", "/watch-later", nil)
	req.Header.Set("Authorization", "Bearer "+apiKey)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var wl map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &wl)
	assert.NotEmpty(t, wl["id"])
	// In database model it might default to empty array or null, check API response format
	// Our API returns { "id": "...", "video_ids": [], ... }

	// 2. Add Video
	addPayload := map[string]string{"video_id": "wl_vid_1"}
	body, _ := json.Marshal(addPayload)
	req, _ = http.NewRequest("PATCH", "/watch-later/add", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify added
	var updated map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &updated)
	vids := updated["video_ids"].([]interface{})
	assert.Len(t, vids, 1)
	assert.Equal(t, "wl_vid_1", vids[0])
}
