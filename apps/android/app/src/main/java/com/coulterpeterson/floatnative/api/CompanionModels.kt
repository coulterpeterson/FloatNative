package com.coulterpeterson.floatnative.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class CompanionLoginRequest(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "dpop_proof") val dpopProof: String
)

@JsonClass(generateAdapter = true)
data class CompanionRegisterResponse(
    @Json(name = "api_key") val apiKey: String,
    @Json(name = "floatplane_user_id") val floatplaneUserId: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String,
    @Json(name = "floatplane_user_id") val floatplaneUserId: String,
    val name: String,
    @Json(name = "is_watch_later") val isWatchLater: Boolean,
    @Json(name = "video_ids") val videoIds: List<String>,
    @Json(name = "created_at") val createdAt: String, // Keeping as String for now, Moshi needs adapter for Date
    @Json(name = "updated_at") val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class PlaylistResponse(
    val playlists: List<Playlist>,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class PlaylistCreateRequest(
    val name: String
)

@JsonClass(generateAdapter = true)
data class LTTSearchResponse(
    val query: String,
    val count: Int,
    val results: List<LTTSearchResult>
)

@JsonClass(generateAdapter = true)
data class LTTSearchResult(
    val id: String,
    val title: String,
    @Json(name = "creator_name") val creatorName: String, // "Linus Tech Tips"
    @Json(name = "channel_title") val channelTitle: String, // "Channel Super Fun"
    @Json(name = "has_video") val hasVideo: Boolean,
    @Json(name = "video_duration") val videoDuration: Int, // seconds
    @Json(name = "release_date") val releaseDate: String, // ISO8601
    @Json(name = "thumbnail_url") val thumbnailUrl: String?, // Full URL or path? Usually path on backend, but response might differ.
    @Json(name = "channel_icon_url") val channelIconUrl: String?
)
