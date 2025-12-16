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
    data class Content(val playlists: List<Playlist>) : PlaylistsState()
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
            
            try {
                // Determine if we need to Companion Login first (if no API key)
                // This logic could be in a repository, but ViewModel + Interceptor logic handles it implicitly via Interceptor if configured?
                // Actually my CompanionAuthInterceptor just checks if key exists. It doesn't auto-login if missing.
                // The iOS app explicitly calls `ensureLoggedIn()` before fetching.
                // I should replicate that logic here or in a repository.
                
                ensureCompanionLogin()
                
                val response = FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                if (response.isSuccessful && response.body() != null) {
                    _state.value = PlaylistsState.Content(response.body()!!.playlists)
                } else {
                    // If 401, retry once logic is in iOS app. 
                    // For now, let's keep it simple. If we fail, show error.
                    // Ideally, CompanionAuthInterceptor or a Repository should handle 401 retries.
                    _state.value = PlaylistsState.Error("Failed to fetch playlists: ${response.code()}")
                }
            } catch (e: Exception) {
                _state.value = PlaylistsState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private suspend fun ensureCompanionLogin() {
        val tokenManager = FloatplaneApi.tokenManager
        if (tokenManager.companionApiKey == null) {
            val accessToken = tokenManager.accessToken
            if (accessToken != null) {
                 val userSelfUrl = "https://www.floatplane.com/api/v3/user/self"
                 val dpopProof = FloatplaneApi.dpopManager.generateProof("GET", userSelfUrl, accessToken)
                 val loginRequest = com.coulterpeterson.floatnative.api.CompanionLoginRequest(accessToken, dpopProof)
                 val loginResponse = FloatplaneApi.companionApi.login(loginRequest)
                 if (loginResponse.isSuccessful && loginResponse.body() != null) {
                     tokenManager.companionApiKey = loginResponse.body()!!.apiKey
                 } else {
                     throw Exception("Companion Login Failed: ${loginResponse.code()}")
                 }
            } else {
                 throw Exception("Not logged in to Floatplane")
            }
        }
    }
}
