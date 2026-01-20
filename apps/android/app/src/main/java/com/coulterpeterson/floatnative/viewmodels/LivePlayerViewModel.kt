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

    val player: ExoPlayer = run {
        // Create a custom RenderersFactory with decoder fallback
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(application)
            .setEnableDecoderFallback(true) // Allow fallback to software decoder if hardware has issues
        
        // Create a TrackSelector with tunneling explicitly disabled
        // Tunneling is a known cause of video zooming/cropping to top-left on certain devices
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(application).apply {
            parameters = buildUponParameters()
                .setTunnelingEnabled(false) // CRITICAL: Disable tunneling to prevent zoom issue
                .build()
        }
        
        ExoPlayer.Builder(application, renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                // Set video scaling mode for SurfaceView
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                
                // Add comprehensive logging to diagnose the issue
                addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onVideoSizeChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        videoSize: androidx.media3.common.VideoSize
                    ) {
                        android.util.Log.d("LivePlayer", ">>> onVideoSizeChanged: ${videoSize.width}x${videoSize.height}, " +
                            "pixelWidthHeightRatio=${videoSize.pixelWidthHeightRatio}, " +
                            "unappliedRotationDegrees=${videoSize.unappliedRotationDegrees}")
                    }
                    
                    override fun onSurfaceSizeChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        width: Int,
                        height: Int
                    ) {
                        android.util.Log.d("LivePlayer", ">>> onSurfaceSizeChanged: ${width}x${height}")
                    }
                    
                    override fun onRenderedFirstFrame(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        output: Any,
                        renderTimeMs: Long
                    ) {
                        android.util.Log.d("LivePlayer", ">>> onRenderedFirstFrame: output=$output, renderTimeMs=$renderTimeMs")
                    }
                    
                    override fun onVideoDecoderInitialized(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        android.util.Log.d("LivePlayer", ">>> onVideoDecoderInitialized: decoder=$decoderName")
                    }
                    
                    override fun onVideoInputFormatChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        format: androidx.media3.common.Format,
                        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
                    ) {
                        android.util.Log.d("LivePlayer", ">>> onVideoInputFormatChanged: ${format.width}x${format.height}, " +
                            "codecs=${format.codecs}, frameRate=${format.frameRate}")
                    }
                })
            }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    fun loadStream(liveStreamId: String) {
        if (_state.value is LivePlayerState.Content) return // Already loaded

        // Check for Fake Live Stream
        if (liveStreamId == "fake_live_stream_id" || liveStreamId == "fake_creator_id") {
             val fakeThumbnail = "https://pbs.floatplane.com/stream_thumbnails/5c13f3c006f1be15e08e05c0/510600934781497_1768590626092_400x225.jpeg"
             _state.value = LivePlayerState.Content(
                streamUrl = "", // Empty or invalid URL to prevent playback
                creatorTitle = "Fake Creator",
                streamTitle = "Fake Live Stream",
                description = "This is a fake live stream for testing.",
                icon = fakeThumbnail,
                creatorId = "fake_creator_id"
            )
            // Do not call player.prepare() / player.playWhenReady = true
            return
        }

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
