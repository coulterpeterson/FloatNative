package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Content(val results: List<BlogPostModelV3>) : SearchState()
    data class Empty(val query: String) : SearchState()
    data class Error(val message: String) : SearchState()
}

class SearchViewModel : TvSidebarViewModel() {

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _lastFocusedId = MutableStateFlow<String?>(null)
    val lastFocusedId = _lastFocusedId.asStateFlow()

    fun setLastFocusedId(id: String?) {
        _lastFocusedId.value = id
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isEmpty()) {
            _state.value = SearchState.Idle
        }
    }

    fun performSearch() {
        val currentQuery = _query.value
        if (currentQuery.isBlank()) return

        val LTT_CREATOR_ID = "59f94c0bdd241b70349eb72b"

        viewModelScope.launch {
            _state.value = SearchState.Loading
            
            try {
                // 1. Get subscriptions
                val subsResponse = FloatplaneApi.manual.getSubscriptions()
                if (!subsResponse.isSuccessful || subsResponse.body() == null) {
                    _state.value = SearchState.Error("Failed to load subscriptions")
                    return@launch
                }
                
                val subscriptions = subsResponse.body()!!
                
                // Check Enhanced Search Conditions
                val isLttOnlySubscriber = subscriptions.size == 1 && subscriptions.first().creator == LTT_CREATOR_ID
                val enhancedSearchEnabled = FloatplaneApi.tokenManager.enhancedLttSearchEnabled
                
                if (enhancedSearchEnabled && isLttOnlySubscriber) {
                    // --- ENHANCED SEARCH ---
                    try {
                        val companionResponse = FloatplaneApi.companionApi.searchLTT(currentQuery)
                        if (companionResponse.isSuccessful && companionResponse.body() != null) {
                            val results = companionResponse.body()!!.results
                            if (results.isEmpty()) {
                                 _state.value = SearchState.Empty(currentQuery)
                            } else {
                                // Convert to BlogPostModelV3
                                val blogPosts = results.map { convertLttResultToBlogPost(it, LTT_CREATOR_ID) }
                                // Sort by release date (newest first) - string comparison usually works for ISO8601
                                val sorted = blogPosts.sortedByDescending { it.releaseDate }
                                _state.value = SearchState.Content(sorted)
                                fetchWatchProgress(sorted)
                            }
                            return@launch
                        } else {
                            // Fallback to standard search if companion fails
                            // Log warning?
                        }
                    } catch (e: Exception) {
                        // Fallback to standard
                    }
                }

                // --- STANDARD SEARCH ---
                val creatorIds = subscriptions.map { it.creator }

                // 2. Search each creator in parallel
                val allResults = coroutineScope {
                    creatorIds.map { creatorId ->
                        async {
                            try {
                                val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                                    id = creatorId,
                                    limit = 10,
                                    search = currentQuery
                                )
                                if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                            } catch (e: Exception) {
                                emptyList<BlogPostModelV3>()
                            }
                        }
                    }.awaitAll().flatten()
                }

                if (allResults.isEmpty()) {
                    _state.value = SearchState.Empty(currentQuery)
                } else {
                    val sorted = allResults.sortedByDescending { it.releaseDate }
                    _state.value = SearchState.Content(sorted)
                    fetchWatchProgress(sorted)
                }

            } catch (e: Exception) {
                _state.value = SearchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun convertLttResultToBlogPost(result: com.coulterpeterson.floatnative.api.LTTSearchResult, creatorId: String): BlogPostModelV3 {
        // Construct minimal BlogPostModelV3 from search result
        // This is a partial mapping sufficient for the UI to display the card
        
        val thumbnail = if (result.thumbnailUrl != null) {
            com.coulterpeterson.floatnative.openapi.models.ImageModel(
                width = 1920, height = 1080, path = java.net.URI.create(result.thumbnailUrl), childImages = null
            )
        } else null
        
        val creatorOwner = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3CreatorOwner(id = creatorId, username = result.creatorName)
        
        // Minimal Creator object
        val creator = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3Creator(
            id = creatorId,
            owner = creatorOwner,
            title = result.creatorName,
            urlname = result.creatorName.lowercase().replace(" ", ""),
            description = result.creatorName,
            about = "",
            category = com.coulterpeterson.floatnative.openapi.models.CreatorModelV3Category("tech", "Technology"),
            cover = null, 
            icon = com.coulterpeterson.floatnative.openapi.models.ImageModel(512, 512, java.net.URI.create("/creator/icon/placeholder"), null),
            liveStream = null, subscriptionPlans = emptyList(), discoverable = true, subscriberCountDisplay = "", incomeDisplay = false,
            defaultChannel = null, channels = null, card = null
        )

        // Channel
        val channelIcon = if (result.channelIconUrl != null) {
            com.coulterpeterson.floatnative.openapi.models.ImageModel(512, 512, java.net.URI.create(result.channelIconUrl), null)
        } else {
            com.coulterpeterson.floatnative.openapi.models.ImageModel(512, 512, java.net.URI.create("/channel/icon/placeholder"), null)
        }

        val channel = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3Channel(
             id = result.channelTitle.lowercase().replace(" ", ""),
             creator = creatorId,
             title = result.channelTitle,
             urlname = result.channelTitle.lowercase().replace(" ", ""),
             about = "", order = null, cover = null, card = null, icon = channelIcon, socialLinks = null
        )
        
        // Metadata
        val metadata = com.coulterpeterson.floatnative.openapi.models.PostMetadataModel(
            hasVideo = result.hasVideo,
            videoCount = if (result.hasVideo) 1 else 0,
            videoDuration = java.math.BigDecimal.valueOf(result.videoDuration.toLong()),
            hasAudio = false, audioCount = null, audioDuration = java.math.BigDecimal.ZERO, hasPicture = false, pictureCount = null, hasGallery = null, galleryCount = null, isFeatured = false
        )

        // Parse Date
        val parsedDate = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.OffsetDateTime.parse(result.releaseDate)
            } else {
                // Should use ThreeTenABP or similar if targeting older androids, but minSdk likely 26+ for this project?
                // Planing safe:
                null
            }
        } catch (e: Exception) {
            null
        }

        return BlogPostModelV3(
            id = result.id,
            guid = result.id,
            title = result.title,
            text = "",
            type = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3.Type.blogPost,
            channel = channel,
            tags = emptyList(),
            attachmentOrder = if (result.hasVideo) listOf(result.id) else emptyList(),
            metadata = metadata,
            releaseDate = parsedDate ?: java.time.OffsetDateTime.now(), 
            likes = 0, dislikes = 0, score = 0, comments = 0,
            creator = creator,
            wasReleasedSilently = false,
            thumbnail = thumbnail,
            isAccessible = true,
            videoAttachments = if (result.hasVideo) listOf(result.id) else null,
            audioAttachments = null, pictureAttachments = null, galleryAttachments = null
        )
    }
}
