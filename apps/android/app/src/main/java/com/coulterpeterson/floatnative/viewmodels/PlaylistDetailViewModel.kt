package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import com.coulterpeterson.floatnative.openapi.models.GetProgressRequest
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.api.PlaylistRemoveRequest
import com.coulterpeterson.floatnative.api.PlaylistAddRequest
import com.coulterpeterson.floatnative.api.PlaylistCreateRequest
import com.coulterpeterson.floatnative.api.Playlist
import com.coulterpeterson.floatnative.openapi.models.UpdateProgressRequest
import com.coulterpeterson.floatnative.openapi.models.ImageModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

sealed class PlaylistDetailState {
    object Initial : PlaylistDetailState()
    object Loading : PlaylistDetailState()
    data class Content(val posts: List<ContentPostV3Response>) : PlaylistDetailState()
    data class Error(val message: String) : PlaylistDetailState()
}

class PlaylistDetailViewModel : ViewModel() {
    private val _state = MutableStateFlow<PlaylistDetailState>(PlaylistDetailState.Initial)
    val state = _state.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val userPlaylists = _userPlaylists.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress = _watchProgress.asStateFlow()

    fun loadPlaylistPosts(playlistId: String) {
        viewModelScope.launch {
            _state.value = PlaylistDetailState.Loading
            
            try {
                // First get the playlist object to get video IDs? 
                // Or do we assume we just need to list them?
                // The iOS app: `companionAPI.getPlaylists` -> finds playlist -> gets videoIds -> fetches posts.
                // We should probably optimize this in the future, but for now let's fetch playlist fresh.
                
                val playlistResponse = withCompanionRetry {
                    FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                }
                val playlists = playlistResponse.body()?.playlists ?: emptyList()
                val playlist = playlists.find { it.id == playlistId }
                
                if (playlist == null) {
                    _state.value = PlaylistDetailState.Error("Playlist not found")
                    return@launch
                }
                
                if (playlist.videoIds.isEmpty()) {
                    _state.value = PlaylistDetailState.Content(emptyList())
                    return@launch
                }
                
                // Fetch posts in parallel
                val posts = playlist.videoIds.map { videoId ->
                    async {
                        try {
                            // Using contentV3 for blog post or manual?
                            // Assuming fetchBlogPost logic exists in similar VM.
                            // Looking at HomeFeedViewModel or VideoPlayerViewModel logic might help.
                            // But usually it's contentV3.getBlogPost(id)
                            val res = FloatplaneApi.contentV3.getBlogPost(videoId) // Retrofit suspend
                            // It returns BlogPostResponseV3 or similar?
                            // Actually earlier used `FloatplaneApi.api.getBlogPost`. `api` was invalid.
                            // If `ContentV3Api` is generated, it likely has `getBlogPost`.
                            // Let's assume it returns Response<ContentPostV3Response> or direct object.
                            // Generated API usually returns object directly if suspend, or Call.
                            // If using `apiClient.createService`, it might be Retrofit.
                            
                            // Let's try `FloatplaneApi.contentV3.getBlogPost(videoId)` 
                            // If it's `ContentV3Api`, check methods.
                            // Error said `Unresolved reference 'api'`. 
                            if (res.isSuccessful) res.body() else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                
                // Sort by release date (newest first)
                val sortedPosts = posts.sortedByDescending { it.releaseDate }
                
                _state.value = PlaylistDetailState.Content(sortedPosts)
                fetchWatchProgress(sortedPosts)
                
            } catch (e: Exception) {
                _state.value = PlaylistDetailState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
         val currentState = _state.value
         if (currentState !is PlaylistDetailState.Content) return
         
         // Optimistic update
         val oldPosts = currentState.posts
         val newPosts = oldPosts.filter { 
             // Check video attachments ID or post ID? 
             // getBlogPost returns ContentPostV3Response which has `post` and `videoAttachments`.
             // videoId passed here usually refers to the ID in `videoAttachments`.
             // But floatplane video URLs use the BLOG POST ID usually? 
             // Wait, `playlist.videoIds` are likely VIDEO IDs or BLOG POST IDs?
             // Looking at `loadThumbnails` in iOS: `api.getBlogPost(id: playlist.videoIds[0])`.
             // This implies `playlist.videoIds` contains IDs suitable for `getBlogPost`.
             
             // Floatplane structure: a BlogPost has an ID.
             val postId = it.id
             postId != videoId
         }
         _state.value = PlaylistDetailState.Content(newPosts)
         
         viewModelScope.launch {
             try {
                 withCompanionRetry {
                     FloatplaneApi.companionApi.removeFromPlaylist(
                         id = playlistId,
                         request = PlaylistRemoveRequest(videoId)
                     )
                 }
             } catch (e: Exception) {
                 // Revert or re-fetch
                 loadPlaylistPosts(playlistId)
             }
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

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val response = withCompanionRetry {
                    FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                }
                if (response.isSuccessful && response.body() != null) {
                    _userPlaylists.value = response.body()!!.playlists
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markAsWatched(post: ContentPostV3Response) {
        viewModelScope.launch {
            try {
                 // Optimistic update
                 _watchProgress.value = _watchProgress.value + (post.id to 1.0f)

                 // ContentPostV3Response doesn't explicitly have videoDuration in root, but metadata has it.
                 // also videoAttachments is List<VideoAttachmentModel>?
                 val videoId = post.videoAttachments?.firstOrNull()?.id ?: return@launch
                 val durationSeconds = post.metadata.videoDuration.toInt()
                 
                 FloatplaneApi.contentV3.updateProgress(
                     updateProgressRequest = UpdateProgressRequest(
                         id = videoId,
                         contentType = UpdateProgressRequest.ContentType.video,
                         progress = durationSeconds
                     )
                 )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleWatchLater(post: ContentPostV3Response, onResult: (Boolean) -> Unit) {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeFromPlaylistApi(playlistId: String, videoId: String) {
         viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.removeFromPlaylist(
                        id = playlistId,
                        request = PlaylistRemoveRequest(videoId)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            } catch (e: Exception) {
                    e.printStackTrace()
            }
        }
    }
    
    private fun fetchWatchProgress(posts: List<ContentPostV3Response>) {
        val videoPosts = posts.filter { it.metadata.hasVideo }
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
}
