package com.coulterpeterson.floatnative.ui.screens

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.coulterpeterson.floatnative.viewmodels.LivePlayerState
import com.coulterpeterson.floatnative.viewmodels.LivePlayerViewModel
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerScreen(
    liveStreamId: String,
    onClose: () -> Unit = {},
    viewModel: LivePlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Hide system bars in landscape
    val window = (context as? Activity)?.window
    LaunchedEffect(isLandscape, window) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isLandscape) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(liveStreamId) {
        viewModel.loadStream(liveStreamId)
    }

    val exoPlayer = viewModel.player

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Video Player Area
            Box(
            modifier = if (isLandscape) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            }
        ) {
            LiveVideoPlayerView(exoPlayer)
            


            // Cast Session Logic
            val castSessionListener = remember(state) {
                object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
                    override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {
                        loadRemoteMedia(session)
                    }
                    override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) {
                        loadRemoteMedia(session)
                    }
                    override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
                    override fun onSessionStarting(session: com.google.android.gms.cast.framework.CastSession) {}
                    override fun onSessionSuspended(session: com.google.android.gms.cast.framework.CastSession, reason: Int) {}
                    override fun onSessionEnding(session: com.google.android.gms.cast.framework.CastSession) {}
                    override fun onSessionResumeFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
                    override fun onSessionStartFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {}
                    override fun onSessionResuming(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {}

                    private fun loadRemoteMedia(session: com.google.android.gms.cast.framework.CastSession) {
                        if (state is LivePlayerState.Content) {
                            val content = state as LivePlayerState.Content
                            val metadata = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE)
                            metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, content.streamTitle)
                            metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, content.creatorTitle)
                            // Image? Live streams might not have a static thumb handy in state or we use creator icon
                            
                            val customData = org.json.JSONObject()
                            try {
                                customData.put("type", "LIVE")
                            } catch (e: Exception) {}

                            val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(liveStreamId)
                                .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                                .setContentType("application/x-mpegurl") // HLS usually
                                .setMetadata(metadata)
                                .setCustomData(customData)
                                .build()
                                
                            val remoteMediaClient = session.remoteMediaClient
                            remoteMediaClient?.load(mediaInfo, true)
                        }
                    }
                }
            }

            DisposableEffect(Unit) {
                 val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                 castContext.sessionManager.addSessionManagerListener(castSessionListener, com.google.android.gms.cast.framework.CastSession::class.java)
                 onDispose {
                     castContext.sessionManager.removeSessionManagerListener(castSessionListener, com.google.android.gms.cast.framework.CastSession::class.java)
                 }
            }
            
            // Overlay for loading/error
            when (val currentState = state) {
                is LivePlayerState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is LivePlayerState.Error -> {
                    Text(
                        text = currentState.message,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {}
            }
            
            // Back button overlay in landscape
            if (isLandscape) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }

        // Portrait Content (Info + Chat)
        if (!isLandscape) {
            /*
                Split view: 
                - Metadata
                - Chat List (filling remaining space)
                */
                
                val contentState = state as? LivePlayerState.Content
                
                if (contentState != null) {
                    // Metadata
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = contentState.streamTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (contentState.creatorTitle.isNotEmpty()) {
                            Text(
                                text = contentState.creatorTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Chat Placeholder
                    Box(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Live Comments Coming Soon", color = Color.Gray)
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
        }
    }
}

@Composable
fun LiveVideoPlayerView(exoPlayer: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            android.util.Log.d("LivePlayer", ">>> Factory: Creating PlayerView wrapper")
            
            // Create a wrapper FrameLayout that clips its children
            FrameLayout(ctx).apply {
                clipChildren = true
                clipToPadding = true
                
                val playerView = PlayerView(ctx).apply {
                    player = exoPlayer
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    useController = true
                    
                    android.util.Log.d("LivePlayer", ">>> Factory: PlayerView created, resizeMode=$resizeMode")
                    
                    // Store reference to AspectRatioFrameLayout for direct manipulation
                    var aspectRatioFrameLayout: androidx.media3.ui.AspectRatioFrameLayout? = null
                    (this as? android.view.ViewGroup)?.let { vg ->
                        for (i in 0 until vg.childCount) {
                            val child = vg.getChildAt(i)
                            if (child is androidx.media3.ui.AspectRatioFrameLayout) {
                                aspectRatioFrameLayout = child
                                child.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                android.util.Log.d("LivePlayer", ">>> AspectRatioFrameLayout found and stored reference")
                            }
                        }
                    }
                    
                    // Helper function to force layout recalculation by setting aspect ratio
                    val forceLayoutUpdate: (String, Int, Int) -> Unit = { source, width, height ->
                        if (width > 0 && height > 0) {
                            val aspectRatio = width.toFloat() / height.toFloat()
                            android.util.Log.d("LivePlayer", ">>> $source: ${width}x${height} (ratio=$aspectRatio) - Setting aspect ratio")
                            
                            // CRITICAL: Directly set the aspect ratio on AspectRatioFrameLayout
                            // This is what triggers the actual resize, not just requestLayout()
                            aspectRatioFrameLayout?.let { arf ->
                                arf.setAspectRatio(aspectRatio)
                                arf.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                android.util.Log.d("LivePlayer", ">>> Set AspectRatioFrameLayout aspectRatio=$aspectRatio")
                            }
                            
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            requestLayout()
                        }
                    }
                    
                    // CRITICAL FIX: Track format changes and re-attach surface after first frame at new size
                    val analyticsListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                        private var lastVideoWidth = 0
                        private var lastVideoHeight = 0
                        private var pendingFormatWidth = 0
                        private var pendingFormatHeight = 0
                        private var waitingForNewFrame = false
                        
                        override fun onVideoSizeChanged(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            videoSize: androidx.media3.common.VideoSize
                        ) {
                            android.util.Log.d("LivePlayer", ">>> onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
                            
                            // Check if this is the size we're waiting for
                            if (waitingForNewFrame && videoSize.width == pendingFormatWidth && videoSize.height == pendingFormatHeight) {
                                android.util.Log.d("LivePlayer", ">>> Video size now matches pending format, updating layout")
                                waitingForNewFrame = false
                            }
                            
                            forceLayoutUpdate("onVideoSizeChanged", videoSize.width, videoSize.height)
                            lastVideoWidth = videoSize.width
                            lastVideoHeight = videoSize.height
                        }
                        
                        override fun onVideoInputFormatChanged(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            format: androidx.media3.common.Format,
                            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
                        ) {
                            val newWidth = format.width
                            val newHeight = format.height
                            
                            if (newWidth != lastVideoWidth || newHeight != lastVideoHeight) {
                                android.util.Log.d("LivePlayer", ">>> onVideoInputFormatChanged: ${newWidth}x${newHeight} - Format changed from ${lastVideoWidth}x${lastVideoHeight}")
                                pendingFormatWidth = newWidth
                                pendingFormatHeight = newHeight
                                waitingForNewFrame = true
                                
                                forceLayoutUpdate("onVideoInputFormatChanged", newWidth, newHeight)
                            }
                        }
                        
                        override fun onRenderedFirstFrame(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            output: Any,
                            renderTimeMs: Long
                        ) {
                            android.util.Log.d("LivePlayer", ">>> onRenderedFirstFrame, waitingForNewFrame=$waitingForNewFrame, pending=${pendingFormatWidth}x${pendingFormatHeight}, last=${lastVideoWidth}x${lastVideoHeight}")
                            
                            // If we got a first frame but the video size doesn't match, force re-attach
                            if (waitingForNewFrame && (lastVideoWidth != pendingFormatWidth || lastVideoHeight != pendingFormatHeight)) {
                                android.util.Log.d("LivePlayer", ">>> First frame rendered but size mismatch! Forcing surface re-attach in 100ms")
                                
                                // Delay slightly to let the frame render, then re-attach
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    val currentPlayer = player
                                    if (currentPlayer != null && waitingForNewFrame) {
                                        android.util.Log.d("LivePlayer", ">>> Forcing delayed video surface re-attachment")
                                        player = null
                                        player = currentPlayer
                                        waitingForNewFrame = false
                                        // Also set the correct aspect ratio
                                        forceLayoutUpdate("delayedReattach", pendingFormatWidth, pendingFormatHeight)
                                        android.util.Log.d("LivePlayer", ">>> Delayed video surface re-attached")
                                    }
                                }, 100)
                            }
                        }
                    }
                    (exoPlayer as? androidx.media3.exoplayer.ExoPlayer)?.addAnalyticsListener(analyticsListener)
                    
                    // Cleanup listener when view is detached
                    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {}
                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            android.util.Log.d("LivePlayer", ">>> PlayerView detached, removing listener")
                            (exoPlayer as? androidx.media3.exoplayer.ExoPlayer)?.removeAnalyticsListener(analyticsListener)
                        }
                    })
                    
                    // Log layout changes
                    addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        val width = right - left
                        val height = bottom - top
                        val oldWidth = oldRight - oldLeft
                        val oldHeight = oldBottom - oldTop
                        if (oldWidth != width || oldHeight != height) {
                            android.util.Log.d("LivePlayer", ">>> PlayerView onLayoutChange: ${oldWidth}x${oldHeight} -> ${width}x${height}")
                        }
                    }
                }
                
                addView(playerView)
                
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { wrapper ->
            val playerView = (wrapper as? FrameLayout)?.getChildAt(0) as? PlayerView
            if (playerView != null) {
                playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
