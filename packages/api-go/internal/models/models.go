package models

import (
	"time"
)

// User represents a user in the system.
type User struct {
	FloatplaneUserID string    `json:"floatplane_user_id" db:"floatplane_user_id"`
	APIKey           string    `json:"api_key" db:"api_key"`
	CreatedAt        time.Time `json:"created_at" db:"created_at"`
	LastAccessedAt   time.Time `json:"last_accessed_at" db:"last_accessed_at"`
}

// Playlist represents a user created playlist.
// VideoIDs is stored as a string array (TEXT[]) in Postgres.
type Playlist struct {
	ID               string    `json:"id" db:"id"`
	FloatplaneUserID string    `json:"floatplane_user_id" db:"floatplane_user_id"`
	Name             string    `json:"name" db:"name"`
	IsWatchLater     bool      `json:"is_watch_later" db:"is_watch_later"`
	VideoIDs         []string  `json:"video_ids" db:"video_ids"`
	CreatedAt        time.Time `json:"created_at" db:"created_at"`
	UpdatedAt        time.Time `json:"updated_at" db:"updated_at"`
}

// FPPost represents a Floatplane post/video.
type FPPost struct {
	ID              string    `json:"id" db:"id"`
	Title           string    `json:"title" db:"title"`
	CreatorID       string    `json:"creator_id" db:"creator_id"`
	CreatorName     string    `json:"creator_name,omitempty" db:"creator_name"`
	ChannelID       string    `json:"channel_id,omitempty" db:"channel_id"`
	ChannelTitle    string    `json:"channel_title,omitempty" db:"channel_title"`
	ChannelIconURL  string    `json:"channel_icon_url,omitempty" db:"channel_icon_url"`
	ThumbnailURL    string    `json:"thumbnail_url,omitempty" db:"thumbnail_url"`
	HasVideo        bool      `json:"has_video" db:"has_video"`
	VideoCount      int       `json:"video_count" db:"video_count"`
	VideoDuration   int       `json:"video_duration" db:"video_duration"`
	HasAudio        bool      `json:"has_audio" db:"has_audio"`
	AudioCount      int       `json:"audio_count" db:"audio_count"`
	AudioDuration   int       `json:"audio_duration" db:"audio_duration"`
	HasPicture      bool      `json:"has_picture" db:"has_picture"`
	PictureCount    int       `json:"picture_count" db:"picture_count"`
	IsFeatured      bool      `json:"is_featured" db:"is_featured"`
	HasGallery      bool      `json:"has_gallery" db:"has_gallery"`
	GalleryCount    int       `json:"gallery_count" db:"gallery_count"`
	ReleaseDate     time.Time `json:"release_date" db:"release_date"`
	CreatedAt       time.Time `json:"created_at" db:"created_at"`
	UpdatedAt       time.Time `json:"updated_at" db:"updated_at"`
}

// QRSession represents a QR code login session.
type QRSession struct {
	ID               string     `json:"id" db:"id"`
	DeviceInfo       *string    `json:"device_info,omitempty" db:"device_info"`
	FloatplaneUserID *string    `json:"floatplane_user_id,omitempty" db:"floatplane_user_id"`
	SailsSID         *string    `json:"sails_sid,omitempty" db:"sails_sid"`
	APIKey           *string    `json:"api_key,omitempty" db:"api_key"`
	Status           string     `json:"status" db:"status"` // pending, completed, expired
	ExpiresAt        time.Time  `json:"expires_at" db:"expires_at"`
	CreatedAt        time.Time  `json:"created_at" db:"created_at"`
	CompletedAt      *time.Time `json:"completed_at,omitempty" db:"completed_at"`
}

// DeviceSession represents a per-device session/API key.
type DeviceSession struct {
	ID               string    `json:"id" db:"id"`
	FloatplaneUserID string    `json:"floatplane_user_id" db:"floatplane_user_id"`
	APIKey           string    `json:"api_key" db:"api_key"`
	DPoPJKT          string    `json:"dpop_jkt" db:"dpop_jkt"`
	DeviceInfo       string    `json:"device_info,omitempty" db:"device_info"`
	CreatedAt        time.Time `json:"created_at" db:"created_at"`
	LastAccessedAt   time.Time `json:"last_accessed_at" db:"last_accessed_at"`
}
