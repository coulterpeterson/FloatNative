package handlers

import (
	"net/http"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/models"
)

func SearchLTT(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if query == "" {
		respondJSON(w, http.StatusOK, []models.FPPost{})
		return
	}

	searchQuery := "%" + query + "%"
	rows, err := database.Pool.Query(r.Context(), `
		SELECT id, title, creator_id, creator_name, channel_id, channel_title, channel_icon_url, thumbnail_url,
		       has_video, video_count, video_duration, has_audio, audio_count, audio_duration,
		       has_picture, picture_count, is_featured, has_gallery, gallery_count, release_date, created_at, updated_at
		FROM fp_posts
		WHERE title ILIKE $1
		ORDER BY release_date DESC
		LIMIT 50
	`, searchQuery)

	if err != nil {
		respondError(w, http.StatusInternalServerError, "Internal Server Error", "Failed to search posts")
		return
	}
	defer rows.Close()

	var posts []models.FPPost
	for rows.Next() {
		var p models.FPPost
		if err := rows.Scan(
			&p.ID, &p.Title, &p.CreatorID, &p.CreatorName, &p.ChannelID, &p.ChannelTitle, &p.ChannelIconURL, &p.ThumbnailURL,
			&p.HasVideo, &p.VideoCount, &p.VideoDuration, &p.HasAudio, &p.AudioCount, &p.AudioDuration,
			&p.HasPicture, &p.PictureCount, &p.IsFeatured, &p.HasGallery, &p.GalleryCount, &p.ReleaseDate, &p.CreatedAt, &p.UpdatedAt,
		); err != nil {
			continue // Skip bad rows? or error out. SKipping for robustness.
		}
		posts = append(posts, p)
	}

	if posts == nil {
		posts = []models.FPPost{}
	}

	respondJSON(w, http.StatusOK, posts)
}
