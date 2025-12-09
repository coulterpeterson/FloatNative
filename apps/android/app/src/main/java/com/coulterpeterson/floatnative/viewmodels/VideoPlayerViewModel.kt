package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.apis.DeliveryV3Api
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class VideoPlayerState {
    object Idle : VideoPlayerState()
    object Loading : VideoPlayerState()
    data class Content(
        val blogPost: ContentPostV3Response,
        val videoUrl: String
    ) : VideoPlayerState()
    data class Error(val message: String) : VideoPlayerState()
}

class VideoPlayerViewModel : ViewModel() {

    private val _state = MutableStateFlow<VideoPlayerState>(VideoPlayerState.Idle)
    val state = _state.asStateFlow()

    fun loadVideo(postId: String) {
        if (_state.value is VideoPlayerState.Content && (_state.value as VideoPlayerState.Content).blogPost.id == postId) {
            return // Already loaded
        }

        viewModelScope.launch {
            _state.value = VideoPlayerState.Loading

            try {
                // 1. Fetch Blog Post details
                val postResponse = FloatplaneApi.contentV3.getBlogPost(postId)
                if (!postResponse.isSuccessful || postResponse.body() == null) {
                    _state.value = VideoPlayerState.Error("Failed to load post details")
                    return@launch
                }
                val post = postResponse.body()!!

                // 2. Get Video ID (first attachment)
                val videoId = post.videoAttachments?.firstOrNull()?.id
                if (videoId == null) {
                    _state.value = VideoPlayerState.Error("No video found in this post")
                    return@launch
                }

                // 3. Get Delivery Info
                val deliveryResponse = FloatplaneApi.deliveryV3.getDeliveryInfoV3(
                    scenario = DeliveryV3Api.ScenarioGetDeliveryInfoV3.onDemand,
                    entityId = videoId,
                    outputKind = DeliveryV3Api.OutputKindGetDeliveryInfoV3.hlsPeriodFmp4
                )

                if (!deliveryResponse.isSuccessful || deliveryResponse.body() == null) {
                    _state.value = VideoPlayerState.Error("Failed to load video stream")
                    return@launch
                }

                val deliveryInfo = deliveryResponse.body()!!
                val group = deliveryInfo.groups.firstOrNull()
                val variant = group?.variants?.firstOrNull()

                if (group != null && variant != null) {
                    var streamUrl = variant.url
                    // If relative URL, resolve with origin
                    if (!streamUrl.startsWith("http")) {
                        val origin = variant.origins?.firstOrNull() ?: group.origins?.firstOrNull()
                        val baseUrl = origin?.url?.toString()?.trimEnd('/') ?: "https://www.floatplane.com"
                        val relativePath = streamUrl.trimStart('/')
                        streamUrl = "$baseUrl/$relativePath"
                    }
                    _state.value = VideoPlayerState.Content(post, streamUrl)
                } else {
                    _state.value = VideoPlayerState.Error("No stream URL found")
                }

            } catch (e: Exception) {
                _state.value = VideoPlayerState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
