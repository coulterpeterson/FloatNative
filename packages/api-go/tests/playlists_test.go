package tests

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPlaylistsCRUD(t *testing.T) {
	clearDatabase(t)
	r := setupRouter()
	apiKey := createTestUser(t)

	// 1. Create Playlist
	payload := map[string]interface{}{
		"name": "My List",
		"video_ids": []string{"vid1"},
	}
	body, _ := json.Marshal(payload)
	req, _ := http.NewRequest("POST", "/playlists", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code)
	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	playlistID := created["id"].(string)
	assert.NotEmpty(t, playlistID)
	assert.Equal(t, "My List", created["name"])

	// 2. Get Playlists
	req, _ = http.NewRequest("GET", "/playlists", nil)
	req.Header.Set("Authorization", "Bearer "+apiKey)
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Body.String(), "My List")

	// 3. Add Video
	addPayload := map[string]string{"video_id": "vid2"}
	body, _ = json.Marshal(addPayload)
	req, _ = http.NewRequest("PATCH", "/playlists/"+playlistID+"/add", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	
	// Verify video added
	var updated map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &updated)
	vids := updated["video_ids"].([]interface{})
	assert.Len(t, vids, 2) // vid1 + vid2

	// 4. Delete Playlist
	req, _ = http.NewRequest("DELETE", "/playlists/"+playlistID, nil)
	req.Header.Set("Authorization", "Bearer "+apiKey)
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNoContent, w.Code)

	// Verify Gone
	req, _ = http.NewRequest("GET", "/playlists", nil)
	req.Header.Set("Authorization", "Bearer "+apiKey)
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)
	var listResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &listResp)
	assert.Equal(t, float64(0), listResp["count"])
}
