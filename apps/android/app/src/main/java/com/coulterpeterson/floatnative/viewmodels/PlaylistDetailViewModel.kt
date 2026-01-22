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

class PlaylistDetailViewModel : TvSidebarViewModel() {
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
                            val res = FloatplaneApi.contentV3.getBlogPost(videoId) // Retrofit suspend
                            if (res.isSuccessful) res.body() else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                
                // Sort by release date (newest first)
                val sortedPosts = posts.sortedByDescending { it.releaseDate }
                
                _state.value = PlaylistDetailState.Content(sortedPosts)
                
                // Convert ContentPostV3Response to BlogPostModelV3 for watch progress fetching
                val blogPosts = sortedPosts.map { post ->
                     toBlogPostModel(post)
                }
                fetchWatchProgress(blogPosts)
                
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
}

