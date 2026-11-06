package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WatchHistoryResponse(
    val userId: String,
    val contentId: String,
    val contentType: String,
    val progress: Int,
    val updatedAt: String, // Keep as String for simplicity unless Moshi Date adapter is confirmed
    val blogPost: BlogPostModelV3?
)
