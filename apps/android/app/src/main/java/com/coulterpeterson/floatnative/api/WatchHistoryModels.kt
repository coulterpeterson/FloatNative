package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.openapi.models.ChannelModel
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV2
import com.coulterpeterson.floatnative.openapi.models.ImageModel
import com.coulterpeterson.floatnative.openapi.models.PostMetadataModel
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class WatchHistoryResponse(
    val userId: String,
    val contentId: String,
    val contentType: String,
    val progress: Int,
    val updatedAt: String, // Keep as String for simplicity unless Moshi Date adapter is confirmed
    val blogPost: WatchHistoryBlogPost
)

@JsonClass(generateAdapter = true)
data class WatchHistoryBlogPost(
    val id: String,
    val guid: String,
    val title: String,
    val text: String,
    val type: String,
    val channel: ChannelModel,
    val tags: List<String>,
    val attachmentOrder: List<String>,
    val metadata: PostMetadataModel,
    val releaseDate: String,
    val likes: Int,
    val dislikes: Int,
    val score: Int,
    val comments: Int,
    val creator: CreatorModelV2,
    val wasReleasedSilently: Boolean,
    val thumbnail: ImageModel?,
    val isAccessible: Boolean,
    val videoAttachments: List<String>?,
    val audioAttachments: List<String>?,
    val pictureAttachments: List<String>?,
    val galleryAttachments: List<String>?
)
