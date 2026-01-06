@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.coulterpeterson.floatnative.ui.screens.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerState
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerViewModel

import androidx.tv.material3.ExperimentalTvMaterial3Api

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoPlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val exoPlayer = viewModel.player
    val context = LocalContext.current

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    // Handle Player state updates
    LaunchedEffect(state) {
        if (state is VideoPlayerState.Content) {
            val contentState = state as VideoPlayerState.Content
            if (exoPlayer.currentMediaItem == null || exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() != contentState.videoUrl) {
                val mediaItem = MediaItem.fromUri(contentState.videoUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play() 
            }
        }
    }
    
    // Clean up only when leaving the screen completely (handled by ViewModel usually, but stop on dispose here for TV nav)
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is VideoPlayerState.Loading, VideoPlayerState.Idle -> {
                Text("Loading Video...", color = Color.White)
            }
            is VideoPlayerState.Error -> {
                Text(
                    text = "Error: ${(state as VideoPlayerState.Error).message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            is VideoPlayerState.Content -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            // Enable default controls which handle D-pad focus
                            useController = true 
                            keepScreenOn = true
                            
                            // Ensure the view takes focus to handle D-pad events
                            isFocusable = true
                            isFocusableInTouchMode = true 
                            requestFocus()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
