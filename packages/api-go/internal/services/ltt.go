package services

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"time"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/models"
)

const LTT_CREATOR_ID = "59f94c0bdd241b70349eb72b"

func UpdateLTTPosts() error {
	apiUrl := os.Getenv("FLOATPLANE_API_URL")
	sailsSid := os.Getenv("FLOATPLANE_SAILS_SID")

	if sailsSid == "" {
		return fmt.Errorf("FLOATPLANE_SAILS_SID is not set")
	}
	if apiUrl == "" {
		apiUrl = "https://www.floatplane.com/api" // Default
	}

	posts, err := fetchCreatorPosts(apiUrl, sailsSid, LTT_CREATOR_ID, 20, 0)
	if err != nil {
		return err
	}

	for _, post := range posts {
		releaseDate, _ := time.Parse(time.RFC3339, post.ReleaseDate)

		fpPost := models.FPPost{
			ID:             post.ID,
			Title:          post.Title,
			CreatorID:      post.Creator.ID,
			CreatorName:    post.Creator.Title,
			ChannelID:      post.Channel.ID,
			ChannelTitle:   post.Channel.Title,
			ChannelIconURL: getChannelIconUrl(post),
			ThumbnailURL:   getThumbnailUrl(post),
			HasVideo:       post.Metadata.HasVideo,
			VideoCount:     post.Metadata.VideoCount,
			VideoDuration:  post.Metadata.VideoDuration,
			HasAudio:       post.Metadata.HasAudio,
			AudioCount:     post.Metadata.AudioCount,
			AudioDuration:  post.Metadata.AudioDuration,
			HasPicture:     post.Metadata.HasPicture,
			PictureCount:   post.Metadata.PictureCount,
			IsFeatured:     post.Metadata.IsFeatured,
			HasGallery:     post.Metadata.HasGallery,
			GalleryCount:   post.Metadata.GalleryCount,
			ReleaseDate:    releaseDate,
			UpdatedAt:      time.Now(),
		}

		// Upsert logic
		_, err := database.Pool.Exec(context.Background(), `
			INSERT INTO fp_posts (
				id, title, creator_id, creator_name, channel_id, channel_title, channel_icon_url, thumbnail_url,
				has_video, video_count, video_duration, has_audio, audio_count, audio_duration,
				has_picture, picture_count, is_featured, has_gallery, gallery_count, release_date, updated_at
			) VALUES (
				$1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21
			)
			ON CONFLICT (id) DO UPDATE SET
				title = EXCLUDED.title,
				thumbnail_url = EXCLUDED.thumbnail_url,
				video_count = EXCLUDED.video_count,
				updated_at = EXCLUDED.updated_at
		`, fpPost.ID, fpPost.Title, fpPost.CreatorID, fpPost.CreatorName, fpPost.ChannelID, fpPost.ChannelTitle, fpPost.ChannelIconURL, fpPost.ThumbnailURL,
			fpPost.HasVideo, fpPost.VideoCount, fpPost.VideoDuration, fpPost.HasAudio, fpPost.AudioCount, fpPost.AudioDuration,
			fpPost.HasPicture, fpPost.PictureCount, fpPost.IsFeatured, fpPost.HasGallery, fpPost.GalleryCount, fpPost.ReleaseDate, fpPost.UpdatedAt,
		)

		if err != nil {
			log.Printf("Error upserting LTT post %s: %v", post.ID, err)
		}
	}
	
	log.Printf("Updated %d LTT posts", len(posts))
	return nil
}

func fetchCreatorPosts(baseUrl, sid, creatorId string, limit, offset int) ([]models.FloatplanePost, error) {
	u, _ := url.Parse(baseUrl + "/content/creator")
	q := u.Query()
	q.Set("id", creatorId)
	q.Set("limit", strconv.Itoa(limit))
	q.Set("fetchAfter", strconv.Itoa(offset))
	u.RawQuery = q.Encode()

	req, err := http.NewRequest("GET", u.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Cookie", "sails.sid="+sid)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API request failed with status: %d", resp.StatusCode)
	}

	var posts []models.FloatplanePost
	if err := json.NewDecoder(resp.Body).Decode(&posts); err != nil {
		return nil, err
	}
	return posts, nil
}

func getThumbnailUrl(post models.FloatplanePost) string {
	if post.Thumbnail == nil {
		return ""
	}
	if len(post.Thumbnail.ChildImages) > 0 {
		// logic to find largest image
		best := post.Thumbnail.ChildImages[0]
		for _, img := range post.Thumbnail.ChildImages {
			if img.Width > best.Width {
				best = img
			}
		}
		return best.Path
	}
	return post.Thumbnail.Path
}

func getChannelIconUrl(post models.FloatplanePost) string {
	if post.Channel.Icon == nil {
		return ""
	}
	if len(post.Channel.Icon.ChildImages) > 0 {
		best := post.Channel.Icon.ChildImages[0]
		for _, img := range post.Channel.Icon.ChildImages {
			if img.Width > best.Width {
				best = img
			}
		}
		return best.Path
	}
	return post.Channel.Icon.Path
}
