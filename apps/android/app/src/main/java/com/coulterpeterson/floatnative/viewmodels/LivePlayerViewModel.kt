package com.coulterpeterson.floatnative.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.apis.DeliveryV3Api
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LivePlayerState {
    object Idle : LivePlayerState()
    object Loading : LivePlayerState()
    data class Content(
        val streamUrl: String,
        val creatorTitle: String,
        val streamTitle: String,
        val description: String,

        val icon: String?,
        val creatorId: String // Added creatorId to content state
    ) : LivePlayerState()
    data class Error(val message: String) : LivePlayerState()
}



class LivePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LivePlayerState>(LivePlayerState.Idle)
    val state = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    fun loadStream(liveStreamId: String) {
        if (_state.value is LivePlayerState.Content) return // Already loaded

        viewModelScope.launch {
            _state.value = LivePlayerState.Loading
            try {
                // 1. Get Delivery Info
                val response = FloatplaneApi.deliveryV3.getDeliveryInfoV3(
                    scenario = DeliveryV3Api.ScenarioGetDeliveryInfoV3.live,
                    entityId = liveStreamId
                )

                if (response.isSuccessful && response.body() != null) {
                    val playback = response.body()!!
                    val group = playback.groups?.firstOrNull()
                    val variant = group?.variants?.firstOrNull()
                    val streamUrl = variant?.url

                    var creatorTitle = "Live Stream"
                    var streamTitle = "Loading..."
                    var description = ""
                    var icon: String? = null
                    var creatorId = ""

                     try {
                        val creatorsResponse = FloatplaneApi.creatorV3.getCreators("")
                         if (creatorsResponse.isSuccessful && creatorsResponse.body() != null) {
                             val creators = creatorsResponse.body()!!
                             val match = creators.find { it.liveStream?.id == liveStreamId }
                             if (match != null) {
                                 creatorTitle = match.title
                                 streamTitle = match.liveStream?.title ?: match.title
                                 description = match.liveStream?.description ?: ""
                                 icon = match.icon.path.toString()
                                 creatorId = match.id
                             }
                         }
                    } catch (e: Exception) { e.printStackTrace() }

                    if (streamUrl != null) {
                        val origin = group?.origins?.firstOrNull()?.url
                        val finalUrl = if (origin != null && streamUrl.startsWith("/")) {
                            origin.toString() + streamUrl
                        } else {
                            streamUrl
                        }

                        _state.value = LivePlayerState.Content(
                            streamUrl = finalUrl,
                            creatorTitle = creatorTitle,
                            streamTitle = streamTitle,
                            description = description,
                            icon = icon,
                            creatorId = creatorId
                        )
                        
                        val mediaItem = MediaItem.fromUri(finalUrl)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.playWhenReady = true
                    } else {
                        _state.value = LivePlayerState.Error("No stream URL found")
                    }

                } else {
                    _state.value = LivePlayerState.Error("Failed to load stream info")
                }
            } catch (e: Exception) {
                _state.value = LivePlayerState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
