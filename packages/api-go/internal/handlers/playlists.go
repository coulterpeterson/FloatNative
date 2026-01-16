package handlers

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/middleware"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/models"
	"github.com/go-chi/chi/v5"
)

type GetPlaylistsResponse struct {
	Playlists []models.Playlist `json:"playlists"`
	Count     int               `json:"count"`
}

type CreatePlaylistRequest struct {
	Name     string   `json:"name"`
	VideoIDs []string `json:"video_ids"`
}

func GetPlaylists(w http.ResponseWriter, r *http.Request) {
	// 1. Get user from context
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found in context")
		return
	}

	// 2. Query playlists
	rows, err := database.Pool.Query(r.Context(), `
		SELECT id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
		FROM playlists
		WHERE floatplane_user_id = $1
		ORDER BY created_at DESC
	`, user.FloatplaneUserID)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to fetch playlists")
		return
	}
	defer rows.Close()

	var playlists []models.Playlist
	for rows.Next() {
		var p models.Playlist
		if err := rows.Scan(
			&p.ID,
			&p.FloatplaneUserID,
			&p.Name,
			&p.IsWatchLater,
			&p.VideoIDs,
			&p.CreatedAt,
			&p.UpdatedAt,
		); err != nil {
			respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to scan playlist")
			return
		}
		playlists = append(playlists, p)
	}

	if playlists == nil {
		playlists = []models.Playlist{}
	}

	respondJSON(w, http.StatusOK, GetPlaylistsResponse{
		Playlists: playlists,
		Count:     len(playlists),
	})
}

func CreatePlaylist(w http.ResponseWriter, r *http.Request) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found in context")
		return
	}

	var req CreatePlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid request body")
		return
	}

	if req.Name == "" || len(req.Name) > 255 {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid name")
		return
	}

	if req.VideoIDs == nil {
		req.VideoIDs = []string{} // Initialize as empty array
	}
	// TODO: Handle "Watch Later" reserved name check

	var p models.Playlist
	err := database.Pool.QueryRow(r.Context(), `
		INSERT INTO playlists (floatplane_user_id, name, video_ids, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $4)
		RETURNING id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
	`, user.FloatplaneUserID, req.Name, req.VideoIDs, time.Now()).Scan(
		&p.ID,
		&p.FloatplaneUserID,
		&p.Name,
		&p.IsWatchLater,
		&p.VideoIDs,
		&p.CreatedAt,
		&p.UpdatedAt,
	)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to create playlist")
		return
	}

	respondJSON(w, http.StatusCreated, p)
}


// UpdatePlaylist updates a playlist
func UpdatePlaylist(w http.ResponseWriter, r *http.Request) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "Bad Request", "Missing playlist ID")
		return
	}

	var req struct {
		Name     *string   `json:"name"`
		VideoIDs *[]string `json:"video_ids"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid request body")
		return
	}

	if req.Name == nil && req.VideoIDs == nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "No fields to update")
		return
	}

	// Fetch playlist to check ownership and watch later status
	var p models.Playlist
	err := database.Pool.QueryRow(r.Context(), `
		SELECT id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
		FROM playlists WHERE id = $1 AND floatplane_user_id = $2
	`, id, user.FloatplaneUserID).Scan(&p.ID, &p.FloatplaneUserID, &p.Name, &p.IsWatchLater, &p.VideoIDs, &p.CreatedAt, &p.UpdatedAt)

	if err != nil {
		respondError(w, http.StatusNotFound, "Not Found", "Playlist not found or access denied")
		return
	}

	if p.IsWatchLater && req.Name != nil && *req.Name != p.Name {
		respondError(w, http.StatusForbidden, "Forbidden", "Cannot rename Watch Later playlist")
		return
	}

	// Build update query dynamically
	// updates := []string{"updated_at = $1"}
	// args := []interface{}{time.Now()}
	// argID := 2

	if req.Name != nil {
		// Cleaned up unused dynamic logic
	}
	// ... logic to update ...
	// Actually, simpler method:
	newName := p.Name
	if req.Name != nil {
		newName = *req.Name
	}
	newVideoIDs := p.VideoIDs
	if req.VideoIDs != nil {
		newVideoIDs = *req.VideoIDs
	}

	err = database.Pool.QueryRow(r.Context(), `
		UPDATE playlists
		SET name = $1, video_ids = $2, updated_at = $3
		WHERE id = $4 AND floatplane_user_id = $5
		RETURNING id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
	`, newName, newVideoIDs, time.Now(), id, user.FloatplaneUserID).Scan(
		&p.ID, &p.FloatplaneUserID, &p.Name, &p.IsWatchLater, &p.VideoIDs, &p.CreatedAt, &p.UpdatedAt,
	)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to update playlist")
		return
	}

	respondJSON(w, http.StatusOK, p)
}

// DeletePlaylist deletes a playlist
func DeletePlaylist(w http.ResponseWriter, r *http.Request) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}

	id := chi.URLParam(r, "id")

	// Check if Watch Later
	var isWatchLater bool
	err := database.Pool.QueryRow(r.Context(), `SELECT is_watch_later FROM playlists WHERE id=$1 AND floatplane_user_id=$2`, id, user.FloatplaneUserID).Scan(&isWatchLater)
	if err != nil {
		respondError(w, http.StatusNotFound, "Not Found", "Playlist not found")
		return
	}

	if isWatchLater {
		respondError(w, http.StatusForbidden, "Forbidden", "Cannot delete Watch Later playlist")
		return
	}

	commandTag, err := database.Pool.Exec(r.Context(), `DELETE FROM playlists WHERE id=$1 AND floatplane_user_id=$2`, id, user.FloatplaneUserID)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to delete playlist")
		return
	}
	if commandTag.RowsAffected() == 0 {
		respondError(w, http.StatusNotFound, "Not Found", "Playlist not found")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// AddVideoToPlaylist adds a video ID to the playlist (idempotent)
func AddVideoToPlaylist(w http.ResponseWriter, r *http.Request) {
	modifyPlaylistVideos(w, r, "add")
}

// RemoveVideoFromPlaylist removes a video ID from the playlist
func RemoveVideoFromPlaylist(w http.ResponseWriter, r *http.Request) {
	modifyPlaylistVideos(w, r, "remove")
}

func modifyPlaylistVideos(w http.ResponseWriter, r *http.Request, action string) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}
	id := chi.URLParam(r, "id")

	var req struct {
		VideoID string `json:"video_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.VideoID == "" {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid video_id")
		return
	}

	var p models.Playlist
	err := database.Pool.QueryRow(r.Context(), `
		SELECT id, video_ids FROM playlists WHERE id=$1 AND floatplane_user_id=$2
	`, id, user.FloatplaneUserID).Scan(&p.ID, &p.VideoIDs)

	if err != nil {
		respondError(w, http.StatusNotFound, "Not Found", "Playlist not found")
		return
	}

	// Modify video_ids logic
	if action == "add" {
		exists := false
		for _, vid := range p.VideoIDs {
			if vid == req.VideoID {
				exists = true; break;
			}
		}
		if !exists {
			p.VideoIDs = append(p.VideoIDs, req.VideoID)
		}
	} else if action == "remove" {
		newIDs := []string{}
		for _, vid := range p.VideoIDs {
			if vid != req.VideoID {
				newIDs = append(newIDs, vid)
			}
		}
		p.VideoIDs = newIDs
	}

	// Update DB
	err = database.Pool.QueryRow(r.Context(), `
		UPDATE playlists SET video_ids=$1, updated_at=$2 WHERE id=$3
		RETURNING id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
	`, p.VideoIDs, time.Now(), id).Scan(
		&p.ID, &p.FloatplaneUserID, &p.Name, &p.IsWatchLater, &p.VideoIDs, &p.CreatedAt, &p.UpdatedAt,
	)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to update playlist")
		return
	}

	respondJSON(w, http.StatusOK, p)
}

// Helpers
func respondJSON(w http.ResponseWriter, status int, payload interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(payload)
}

func respondError(w http.ResponseWriter, code int, errType, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{
		"error":   errType,
		"message": message,
	})
}
