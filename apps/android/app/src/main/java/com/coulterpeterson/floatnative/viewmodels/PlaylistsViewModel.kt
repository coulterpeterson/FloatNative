package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.api.Playlist
import com.coulterpeterson.floatnative.data.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlaylistsState {
    object Initial : PlaylistsState()
    object Loading : PlaylistsState()
    data class Content(
        val playlists: List<Playlist>,
        val thumbnails: Map<String, String?> = emptyMap() // playlistId -> thumbnailUrl
    ) : PlaylistsState()
    data class Error(val message: String) : PlaylistsState()
}

class PlaylistsViewModel : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsState>(PlaylistsState.Initial)
    val state = _state.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _state.value = PlaylistsState.Loading
            android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Starting to load playlists")
            
            try {
                android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Calling ensureCompanionLogin")
                val loginSuccess = FloatplaneApi.ensureCompanionLogin()
                
                if (!loginSuccess) {
                    android.util.Log.e("PlaylistsViewModel", "loadPlaylists: ensureCompanionLogin failed")
                    _state.value = PlaylistsState.Error("Failed to authenticate with companion API")
                    return@launch
                }
                
                android.util.Log.d("PlaylistsViewModel", "loadPlaylists: ensureCompanionLogin completed")
                
                android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Calling getPlaylists API")
                val response = FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Got response with code ${response.code()}")
                
                if (response.isSuccessful && response.body() != null) {
                    val playlists = response.body()!!.playlists
                    android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Success - got ${playlists.size} playlists")
                    // Set initial content without thumbnails
                    _state.value = PlaylistsState.Content(playlists)
                    
                    // Fetch thumbnails asynchronously
                    loadThumbnails(playlists)
                } else if (response.code() == 401) {
                    android.util.Log.w("PlaylistsViewModel", "loadPlaylists: Got 401 error, attempting re-authorization")
                    // 401 error - retry with fresh companion login
                    android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Calling ensureCompanionLogin with forceRefresh=true")
                    val retryLoginSuccess = FloatplaneApi.ensureCompanionLogin(forceRefresh = true)
                    
                    if (!retryLoginSuccess) {
                        android.util.Log.e("PlaylistsViewModel", "loadPlaylists: Re-authorization failed")
                        _state.value = PlaylistsState.Error("Failed to re-authenticate")
                        return@launch
                    }
                    
                    android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Re-authorization complete, retrying getPlaylists")
                    
                    val retryResponse = FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                    android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Retry response code: ${retryResponse.code()}")
                    
                    if (retryResponse.isSuccessful && retryResponse.body() != null) {
                        val playlists = retryResponse.body()!!.playlists
                        android.util.Log.d("PlaylistsViewModel", "loadPlaylists: Retry success - got ${playlists.size} playlists")
                        _state.value = PlaylistsState.Content(playlists)
                        loadThumbnails(playlists)
                    } else {
                        android.util.Log.e("PlaylistsViewModel", "loadPlaylists: Retry failed with code ${retryResponse.code()}")
                        _state.value = PlaylistsState.Error("Failed to fetch playlists: ${retryResponse.code()}")
                    }
                } else {
                    android.util.Log.e("PlaylistsViewModel", "loadPlaylists: Failed with code ${response.code()}")
                    _state.value = PlaylistsState.Error("Failed to fetch playlists: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaylistsViewModel", "loadPlaylists: Exception occurred", e)
                _state.value = PlaylistsState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private suspend fun loadThumbnails(playlists: List<Playlist>) {
        // Collect first video ID from each playlist
        val playlistsWithVideos = playlists.filter { it.videoIds.isNotEmpty() }
        
        // Use a mutable map to accumulate thumbnails
        val thumbnailMap = mutableMapOf<String, String?>()
        
        // For existing thumbnails in state, preserve them? 
        // For simplicity, we just fetch new ones.
        
        // We could run these in parallel
        // iOS does: await withTaskGroup
        
        playlistsWithVideos.forEach { playlist ->
             val firstVideoId = playlist.videoIds.first()
             try {
                 // We use getBlogPost to get the thumbnail. 
                 // Note: This is an extra API call per playlist.
                 val response = FloatplaneApi.contentV3.getBlogPost(firstVideoId)
                 if (response.isSuccessful && response.body() != null) {
                     val thumbnail = response.body()!!.thumbnail
                     if (thumbnail != null) {
                         // Pick a suitable quality, e.g., "jpeg" generic or specific child
                         // ImageModel structure: path, width, height, childImages
                         // Let's assume fetching the `path` from the top level or a child is logic we need.
                         // Helper function `getThumbnailPath` would be good, but for now let's grab top level path
                         thumbnailMap[playlist.id] = thumbnail.path.toString()
                     }
                 }
             } catch (e: Exception) {
                 // Ignore individual failures
             }
        }
        
        // Update state with thumbnails
        val currentState = _state.value
        if (currentState is PlaylistsState.Content) {
            _state.value = currentState.copy(thumbnails = thumbnailMap)
        }
    }

    fun deletePlaylist(playlistId: String) {
        val currentState = _state.value
        if (currentState !is PlaylistsState.Content) return

        // Optimistic update
        val oldPlaylists = currentState.playlists
        val newPlaylists = oldPlaylists.filter { it.id != playlistId }
        _state.value = currentState.copy(playlists = newPlaylists)

        viewModelScope.launch {
            try {
                FloatplaneApi.ensureCompanionLogin()
                val response = FloatplaneApi.companionApi.deletePlaylist(playlistId)
                if (response.code() == 401) {
                    // 401 error - retry with fresh companion login
                    try {
                        FloatplaneApi.ensureCompanionLogin(forceRefresh = true)
                        val retryResponse = FloatplaneApi.companionApi.deletePlaylist(playlistId)
                        if (!retryResponse.isSuccessful) {
                            // Revert on failure
                            _state.value = currentState
                        }
                    } catch (e: Exception) {
                        _state.value = currentState // Revert
                    }
                } else if (!response.isSuccessful) {
                    // Revert
                     _state.value = currentState // Or fetch fresh
                     // Could show ephemeral error
                }
            } catch (e: Exception) {
                _state.value = currentState // Revert
            }
        }
    }
    
    // Refresh function needed for swipe-to-refresh
    fun refresh() {
        loadPlaylists()
    }
}
