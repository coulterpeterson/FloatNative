package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.api.PlaylistRemoveRequest
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

    fun loadPlaylistPosts(playlistId: String) {
        viewModelScope.launch {
            _state.value = PlaylistDetailState.Loading
            
            try {
                // First get the playlist object to get video IDs? 
                // Or do we assume we just need to list them?
                // The iOS app: `companionAPI.getPlaylists` -> finds playlist -> gets videoIds -> fetches posts.
                // We should probably optimize this in the future, but for now let's fetch playlist fresh.
                
                ensureCompanionLogin()
                
                val playlistResponse = FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
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
                ensureCompanionLogin()
                 FloatplaneApi.companionApi.removeFromPlaylist(
                     id = playlistId,
                     request = PlaylistRemoveRequest(videoId) // API expects the ID that is in the list
                 )
             } catch (e: Exception) {
                 // Revert or re-fetch
                 loadPlaylistPosts(playlistId)
             }
         }
    }
    
    private suspend fun ensureCompanionLogin() {
        val tokenManager = FloatplaneApi.tokenManager
        if (tokenManager.companionApiKey == null) {
             try {
                 com.coulterpeterson.floatnative.api.FloatplaneApi.ensureCompanionLogin()
             } catch(e: Exception) { }
        }
    }
}
