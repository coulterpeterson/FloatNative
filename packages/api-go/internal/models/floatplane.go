package models

type FloatplaneImage struct {
	Width       int               `json:"width"`
	Height      int               `json:"height"`
	Path        string            `json:"path"`
	ChildImages []FloatplaneImage `json:"childImages"`
}

type FloatplaneCreator struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Owner struct {
		ID       string `json:"id"`
		Username string `json:"username"`
	} `json:"owner"`
}

type FloatplanePostMetadata struct {
	HasVideo      bool `json:"hasVideo"`
	VideoCount    int  `json:"videoCount"`
	VideoDuration int  `json:"videoDuration"`
	HasAudio      bool `json:"hasAudio"`
	AudioCount    int  `json:"audioCount"`
	AudioDuration int  `json:"audioDuration"`
	HasPicture    bool `json:"hasPicture"`
	PictureCount  int  `json:"pictureCount"`
	IsFeatured    bool `json:"isFeatured"`
	HasGallery    bool `json:"hasGallery"`
	GalleryCount  int  `json:"galleryCount"`
}

type FloatplaneChannel struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Icon  *FloatplaneImage `json:"icon"`
}

type FloatplanePost struct {
	ID          string                 `json:"id"`
	Title       string                 `json:"title"`
	Text        string                 `json:"text"`
	Type        string                 `json:"type"`
	Channel     FloatplaneChannel      `json:"channel"`
	Creator     FloatplaneCreator      `json:"creator"`
	Thumbnail   *FloatplaneImage       `json:"thumbnail"`
	Metadata    FloatplanePostMetadata `json:"metadata"`
	ReleaseDate string                 `json:"releaseDate"` // ISO string
}
