package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.coulterpeterson.floatnative.openapi.models.ContentCreatorListLastItems
import com.coulterpeterson.floatnative.openapi.models.UserSubscriptionModel
import com.coulterpeterson.floatnative.api.Playlist
import com.coulterpeterson.floatnative.api.PlaylistAddRequest
import com.coulterpeterson.floatnative.api.PlaylistRemoveRequest
import com.coulterpeterson.floatnative.api.PlaylistCreateRequest
import com.coulterpeterson.floatnative.openapi.models.UpdateProgressRequest
import com.coulterpeterson.floatnative.openapi.models.GetProgressRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeFeedState {
    object Initial : HomeFeedState()
    object Loading : HomeFeedState()
    data class Content(val posts: List<BlogPostModelV3>) : HomeFeedState()
    data class Error(val message: String) : HomeFeedState()
}

data class SidebarState(
    val post: BlogPostModelV3,
    val interaction: com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction? = null,
    val isLoadingInteraction: Boolean = false
)

class HomeFeedViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeFeedState>(HomeFeedState.Initial)
    val state = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private var subscriptions: List<UserSubscriptionModel> = emptyList()
    private var lastCursors: List<ContentCreatorListLastItems>? = null
    private var currentPosts: MutableList<BlogPostModelV3> = mutableListOf()

    sealed class FeedFilter {
        object All : FeedFilter()
        data class Creator(val id: String) : FeedFilter()
        data class Channel(val id: String, val creatorId: String) : FeedFilter()
    }

    private val _filter = MutableStateFlow<FeedFilter>(FeedFilter.All)
    val filter = _filter.asStateFlow()

    private val _sidebarState = MutableStateFlow<SidebarState?>(null)
    val sidebarState = _sidebarState.asStateFlow()

    private var lastFetchAfter: Int = 0 // For single creator/channel offset pagination

    init {
        loadFeed()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadFeed(isRefresh = true)
            _isRefreshing.value = false
        }
    }

    fun setFilter(filter: FeedFilter) {
        _filter.value = filter
        loadFeed(isRefresh = true)
    }

    private fun loadFeed(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _state.value = HomeFeedState.Loading
            
            try {
                // 1. Get Subscriptions if needed (only for All filter)
                if (_filter.value is FeedFilter.All && subscriptions.isEmpty()) {
                    val subResponse = FloatplaneApi.manual.getSubscriptions()
                    if (subResponse.isSuccessful && subResponse.body() != null) {
                        subscriptions = subResponse.body()!!
                    } else {
                        // If loading all feed and subs fail, we can't show anything
                        if (_filter.value is FeedFilter.All) {
                            _state.value = HomeFeedState.Error("Failed to load subscriptions")
                            return@launch
                        }
                    }
                }

                if (_filter.value is FeedFilter.All && subscriptions.isEmpty()) {
                     _state.value = HomeFeedState.Error("No subscriptions found")
                     return@launch
                }

                // 2. Get Feed based on filter
                if (isRefresh) {
                    currentPosts.clear()
                    lastCursors = null
                    lastFetchAfter = 0
                }

                when (val currentFilter = _filter.value) {
                    is FeedFilter.All -> {
                        val creatorIds = subscriptions.mapNotNull { it.creator }
                        val response = FloatplaneApi.contentV3.getMultiCreatorBlogPosts(
                            ids = creatorIds,
                            limit = 20,
                            fetchAfter = emptyMap()
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val feedData = response.body()!!
                            val newPosts = feedData.blogPosts
                            if (newPosts.isNullOrEmpty()) {
                                _state.value = HomeFeedState.Content(posts = emptyList())
                            } else {
                                currentPosts.addAll(newPosts)
                                lastCursors = feedData.lastElements
                                _state.value = HomeFeedState.Content(currentPosts.toList())
                                fetchWatchProgress(newPosts)
                            }
                        } else {
                            _state.value = HomeFeedState.Error("Failed to load feed")
                        }
                    }
                    is FeedFilter.Creator -> {
                        val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.id,
                            limit = 20,
                            fetchAfter = 0
                        )
                         if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                            currentPosts.addAll(newPosts)
                            lastFetchAfter = newPosts.size
                            _state.value = HomeFeedState.Content(currentPosts.toList())
                            fetchWatchProgress(newPosts)
                        } else {
                            _state.value = HomeFeedState.Error("Failed to load creator feed")
                        }
                    }
                    is FeedFilter.Channel -> {
                        val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.creatorId,
                            channel = currentFilter.id,
                            limit = 20,
                            fetchAfter = 0
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                            currentPosts.addAll(newPosts)
                            lastFetchAfter = newPosts.size
                            _state.value = HomeFeedState.Content(currentPosts.toList())
                            fetchWatchProgress(newPosts)
                        } else {
                            _state.value = HomeFeedState.Error("Failed to load channel feed")
                        }
                    }
                }

            } catch (e: Exception) {
                _state.value = HomeFeedState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        
        // Validation based on filter type
        when (_filter.value) {
            is FeedFilter.All -> {
                 if (lastCursors == null || lastCursors!!.isEmpty()) return
                 val hasMoreToFetch = lastCursors!!.any { it.moreFetchable == true }
                 if (!hasMoreToFetch) return
            }
            else -> {
                // For single creator/channel, we infer more content if we have some posts
                // A better check would be if the last fetch returned full limit, but we'll assume yes for now
                if (currentPosts.isEmpty()) return
            }
        }

        viewModelScope.launch {
            _isLoadingMore.value = true
            
            try {
                // ... (Auth/Sub checks if needed) ...

                when (val currentFilter = _filter.value) {
                    is FeedFilter.All -> {
                        // Ensure subscriptions are loaded
                        if (subscriptions.isEmpty()) {
                             _isLoadingMore.value = false
                             return@launch
                        }
                        
                        val creatorIds = subscriptions.mapNotNull { it.creator }
                        
                        // Manually serialize fetchAfter cursors to query map
                        val fetchAfterMap = lastCursors!!.flatMapIndexed { index, item ->
                            val list = mutableListOf(
                                "fetchAfter[$index][creatorId]" to item.creatorId,
                                "fetchAfter[$index][moreFetchable]" to item.moreFetchable.toString()
                            )
                            item.blogPostId?.let {
                                list.add("fetchAfter[$index][blogPostId]" to it)
                            }
                            list
                        }.toMap()

                        val response = FloatplaneApi.contentV3.getMultiCreatorBlogPosts(
                            ids = creatorIds,
                            limit = 20,
                            fetchAfter = fetchAfterMap
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val feedData = response.body()!!
                            val newPosts = feedData.blogPosts
                            
                            if (!newPosts.isNullOrEmpty()) {
                                handleNewPosts(newPosts)
                                fetchWatchProgress(newPosts)
                            }
                            lastCursors = feedData.lastElements
                        }
                    }
                    is FeedFilter.Creator -> {
                         val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.id,
                            limit = 20,
                            fetchAfter = lastFetchAfter
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                            if (newPosts.isNotEmpty()) {
                                handleNewPosts(newPosts)
                                lastFetchAfter += newPosts.size
                                fetchWatchProgress(newPosts)
                            }
                        }
                    }
                    is FeedFilter.Channel -> {
                        val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.creatorId,
                            channel = currentFilter.id,
                            limit = 20,
                            fetchAfter = lastFetchAfter
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                             if (newPosts.isNotEmpty()) {
                                handleNewPosts(newPosts)
                                lastFetchAfter += newPosts.size
                                fetchWatchProgress(newPosts)
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                // Silently fail for load more
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
    
    private fun handleNewPosts(newPosts: List<BlogPostModelV3>) {
        // Filter out duplicates based on ID
        val existingIds = currentPosts.map { it.id }.toSet()
        val uniqueNewPosts = newPosts.filter { !existingIds.contains(it.id) }
        
        if (uniqueNewPosts.isNotEmpty()) {
            currentPosts.addAll(uniqueNewPosts)
             _state.value = HomeFeedState.Content(currentPosts.toList())
        }
    }

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val userPlaylists = _userPlaylists.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress = _watchProgress.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val response = withCompanionRetry {
                    FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                }
                if (response.isSuccessful && response.body() != null) {
                    _userPlaylists.value = response.body()!!.playlists ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun markAsWatched(post: BlogPostModelV3) {
        viewModelScope.launch {
            try {
                 // Optimistic update
                 _watchProgress.value = _watchProgress.value + (post.id to 1.0f)
                 
                 val videoId = post.videoAttachments?.firstOrNull() ?: return@launch
                 // duration is BigDecimal, progress expects Int (seconds)
                 val durationSeconds = post.metadata.videoDuration.toInt()
                 
                 FloatplaneApi.contentV3.updateProgress(
                     updateProgressRequest = UpdateProgressRequest(
                         id = videoId,
                         contentType = UpdateProgressRequest.ContentType.video,
                         progress = durationSeconds
                     )
                 )
            } catch (e: Exception) {
                // Revert optimistic update? For now sticking with user request for immediate feedback
                e.printStackTrace()
            }
        }
    }

    fun toggleWatchLater(post: BlogPostModelV3, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val dummyRes = withCompanionRetry {
                    FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                }
                val playlists = dummyRes.body()?.playlists ?: _userPlaylists.value
                val watchLater = playlists.find { it.isWatchLater } ?: return@launch
                val isAdded = watchLater.videoIds.contains(post.id)
                var wasAdded = false
                
                if (isAdded) {
                    withCompanionRetry {
                        FloatplaneApi.companionApi.removeFromPlaylist(
                            id = watchLater.id,
                            request = PlaylistRemoveRequest(post.id)
                        )
                    }
                    wasAdded = false
                } else {
                    withCompanionRetry {
                        FloatplaneApi.companionApi.addToPlaylist(
                            id = watchLater.id,
                            request = PlaylistAddRequest(post.id)
                        )
                    }
                    wasAdded = true
                }
                loadPlaylists()
                onResult(wasAdded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun addToPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.addToPlaylist(
                        id = playlistId,
                        request = PlaylistAddRequest(videoId)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) { }
        }
    }
    
    fun removeFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.removeFromPlaylist(
                        id = playlistId,
                        request = PlaylistRemoveRequest(videoId)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) { }
        }
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.createPlaylist(
                        request = PlaylistCreateRequest(name)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) { }
        }
    }

    private suspend fun ensureCompanionLogin() {
        FloatplaneApi.ensureCompanionLogin()
    }
    
    /**
     * Helper function to wrap companion API calls with automatic 401 retry logic
     */
    private suspend fun <T> withCompanionRetry(block: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> {
        ensureCompanionLogin()
        val response = block()
        
        // If 401, retry with fresh login
        if (response.code() == 401) {
            FloatplaneApi.ensureCompanionLogin(forceRefresh = true)
            return block()
        }
        
        return response
    }

    private fun fetchWatchProgress(posts: List<BlogPostModelV3>) {
        val videoPosts = posts.filter { it.metadata.hasVideo == true }
        if (videoPosts.isEmpty()) return

        val ids = videoPosts.map { it.id }
        viewModelScope.launch {
            try {
                // Batch requests in chunks of 20
                ids.chunked(20).forEach { batchIds ->
                    val response = FloatplaneApi.contentV3.getProgress(
                        getProgressRequest = GetProgressRequest(
                            ids = batchIds,
                            contentType = GetProgressRequest.ContentType.blogPost
                        )
                    )
                    
                    if (response.isSuccessful && response.body() != null) {
                        val progressMap = response.body()!!.associate { 
                             it.id to (it.progress.toFloat() / 100f).coerceIn(0f, 1f)
                        }
                        
                        _watchProgress.value = _watchProgress.value + progressMap
                    }
                }
            } catch (e: Exception) {
               e.printStackTrace()
            }
        }
    }

    fun openSidebar(post: BlogPostModelV3) {
        _sidebarState.value = SidebarState(post = post, isLoadingInteraction = true)
        viewModelScope.launch {
            try {
                val response = FloatplaneApi.contentV3.getBlogPost(post.id)
                if (response.isSuccessful && response.body() != null) {
                    val fullPost = response.body()!!
                    val interaction = fullPost.userInteraction?.firstOrNull()
                    val currentState = _sidebarState.value
                    if (currentState != null && currentState.post.id == post.id) {
                         _sidebarState.value = currentState.copy(
                             interaction = interaction,
                             isLoadingInteraction = false
                         )
                    }
                } else {
                     val currentState = _sidebarState.value
                     if (currentState != null && currentState.post.id == post.id) {
                         _sidebarState.value = currentState.copy(isLoadingInteraction = false)
                     }
                }
            } catch (e: Exception) {
                 val currentState = _sidebarState.value
                 if (currentState != null && currentState.post.id == post.id) {
                     _sidebarState.value = currentState.copy(isLoadingInteraction = false)
                 }
            }
        }
    }

    fun closeSidebar() {
        _sidebarState.value = null
    }

    fun toggleSidebarLike() {
        val currentState = _sidebarState.value ?: return
        val currentInteraction = currentState.interaction

        val newInteraction = if (currentInteraction == com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction.like) null else com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction.like

        _sidebarState.value = currentState.copy(interaction = newInteraction)

        viewModelScope.launch {
            try {
                FloatplaneApi.contentV3.likeContent(
                    com.coulterpeterson.floatnative.openapi.models.ContentLikeV3Request(
                        id = currentState.post.id,
                        contentType = com.coulterpeterson.floatnative.openapi.models.ContentLikeV3Request.ContentType.blogPost
                    )
                )
            } catch (e: Exception) { }
        }
    }

    fun toggleSidebarDislike() {
        val currentState = _sidebarState.value ?: return
        val currentInteraction = currentState.interaction

        val newInteraction = if (currentInteraction == com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction.dislike) null else com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction.dislike

        _sidebarState.value = currentState.copy(interaction = newInteraction)

        viewModelScope.launch {
            try {
                FloatplaneApi.contentV3.dislikeContent(
                    com.coulterpeterson.floatnative.openapi.models.ContentLikeV3Request(
                        id = currentState.post.id,
                        contentType = com.coulterpeterson.floatnative.openapi.models.ContentLikeV3Request.ContentType.blogPost
                    )
                )
            } catch (e: Exception) { }
        }
    }
}
