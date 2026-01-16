package handlers

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/middleware"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/models"
)

// GetWatchLater gets or creates the Watch Later playlist
func GetWatchLater(w http.ResponseWriter, r *http.Request) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}

	var p models.Playlist
	err := database.Pool.QueryRow(r.Context(), `
		SELECT id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
		FROM playlists WHERE floatplane_user_id = $1 AND is_watch_later = true
	`, user.FloatplaneUserID).Scan(&p.ID, &p.FloatplaneUserID, &p.Name, &p.IsWatchLater, &p.VideoIDs, &p.CreatedAt, &p.UpdatedAt)

	if err != nil {
		// Not found? Create it
		// If DB error is actually "no rows", we create.
		// For simplicity assuming no rows. In prod check err type.
		err = database.Pool.QueryRow(r.Context(), `
			INSERT INTO playlists (floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
			VALUES ($1, 'Watch Later', true, '{}', $2, $2)
			RETURNING id, floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at
		`, user.FloatplaneUserID, time.Now()).Scan(
			&p.ID, &p.FloatplaneUserID, &p.Name, &p.IsWatchLater, &p.VideoIDs, &p.CreatedAt, &p.UpdatedAt,
		)
		if err != nil {
			respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to create Watch Later playlist")
			return
		}
	}

	// Response format for GetWatchLater is simplified per README:
	// { "id": "uuid", "video_ids": ["string"], "updated_at": "ISO 8601" }
	resp := map[string]interface{}{
		"id":         p.ID,
		"video_ids":  p.VideoIDs,
		"updated_at": p.UpdatedAt,
	}
	respondJSON(w, http.StatusOK, resp)
}

// UpdateWatchLater replaces the entire video list
func UpdateWatchLater(w http.ResponseWriter, r *http.Request) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}

	var req struct {
		VideoIDs []string `json:"video_ids"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.VideoIDs == nil {
		respondError(w, http.StatusBadRequest, "Bad Request", "Missing video_ids")
		return
	}

	// Ensure exists first (lazy create if not exists? README says "automatically create... except REMOVE")
	// UpdateWatchLater is PUT /watch-later -> Replace. So we should create if not exists.
	// We can use UPSERT logic or just ensure it exists.
	// Let's reuse GetOrCreate logic inline or helper.
	
	// Just do an UPDATE. If 0 rows affected, INSERT.
	// Postgres ON CONFLICT requires a constraint. We have unique index on ID, but not (user, is_watch_later).
	// But app logic enforces one per user.
	// Let's try update.
	var p models.Playlist
	err := database.Pool.QueryRow(r.Context(), `
		UPDATE playlists 
		SET video_ids = $1, updated_at = $2
		WHERE floatplane_user_id = $3 AND is_watch_later = true
		RETURNING id, video_ids, updated_at
	`, req.VideoIDs, time.Now(), user.FloatplaneUserID).Scan(&p.ID, &p.VideoIDs, &p.UpdatedAt)

	if err != nil {
		// Assume no rows -> Insert
		err = database.Pool.QueryRow(r.Context(), `
			INSERT INTO playlists (floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
			VALUES ($1, 'Watch Later', true, $2, $3, $3)
			RETURNING id, video_ids, updated_at
		`, user.FloatplaneUserID, req.VideoIDs, time.Now()).Scan(&p.ID, &p.VideoIDs, &p.UpdatedAt)
		
		if err != nil {
			respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to update Watch Later")
			return
		}
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"id":         p.ID,
		"video_ids":  p.VideoIDs,
		"updated_at": p.UpdatedAt,
	})
}

func AddVideoToWatchLater(w http.ResponseWriter, r *http.Request) {
	modifyWatchLater(w, r, "add")
}

func RemoveVideoFromWatchLater(w http.ResponseWriter, r *http.Request) {
	modifyWatchLater(w, r, "remove")
}

func modifyWatchLater(w http.ResponseWriter, r *http.Request, action string) {
	user, ok := r.Context().Value(middleware.UserContextKey).(*models.User)
	if !ok {
		respondError(w, http.StatusUnauthorized, "Unauthorized", "User not found")
		return
	}

	var req struct {
		VideoID string `json:"video_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.VideoID == "" {
		respondError(w, http.StatusBadRequest, "Bad Request", "Invalid video_id")
		return
	}

	// Get current list
	var p models.Playlist
	err := database.Pool.QueryRow(r.Context(), `
		SELECT id, video_ids FROM playlists WHERE floatplane_user_id = $1 AND is_watch_later = true
	`, user.FloatplaneUserID).Scan(&p.ID, &p.VideoIDs)

	if err != nil {
		if action == "remove" {
			respondError(w, http.StatusNotFound, "Not Found", "Watch Later playlist doesn't exist yet")
			return
		}
		// logic for Add: Create empty if not exists
		err = database.Pool.QueryRow(r.Context(), `
			INSERT INTO playlists (floatplane_user_id, name, is_watch_later, video_ids, created_at, updated_at)
			VALUES ($1, 'Watch Later', true, '{}', $2, $2)
			RETURNING id, video_ids
		`, user.FloatplaneUserID, time.Now()).Scan(&p.ID, &p.VideoIDs)
		if err != nil {
			respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to create Watch Later")
			return
		}
	}

	// Modify
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

	// Update
	var updated models.Playlist
	err = database.Pool.QueryRow(r.Context(), `
		UPDATE playlists SET video_ids=$1, updated_at=$2 WHERE id=$3
		RETURNING id, video_ids, updated_at
	`, p.VideoIDs, time.Now(), p.ID).Scan(&updated.ID, &updated.VideoIDs, &updated.UpdatedAt)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to update Watch Later")
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"id":         updated.ID,
		"video_ids":  updated.VideoIDs,
		"updated_at": updated.UpdatedAt,
	})
}
