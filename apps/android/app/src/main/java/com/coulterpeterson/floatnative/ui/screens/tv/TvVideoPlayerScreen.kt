@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.coulterpeterson.floatnative.ui.screens.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerState
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerViewModel

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
                var showSettings by remember { mutableStateOf(false) }

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

                            setOnKeyListener { _, keyCode, event ->
                                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                                    when (keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                        android.view.KeyEvent.KEYCODE_ENTER,
                                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            if (!isControllerFullyVisible) {
                                                showController()
                                                // If we just showed the controller, we might want to consume the event
                                                // so it doesn't accidentally trigger an action on a button immediately.
                                                // However, for navigation keys (arrows), we usually want them to navigate immediately if possible.
                                                // Standard behavior is often: first press shows UI, subsequent navigate.
                                                // ExoPlayer's default behavior often handles this if useController=true.
                                                // But since the user says it's not working, let's force show and maybe consume if it was hidden.
                                                return@setOnKeyListener true
                                            }
                                        }
                                    }
                                }
                                false
                            }
                        }
                    },
                    update = { playerView ->
                        // Bind Custom Controls
                        val btnLike = playerView.findViewById<android.widget.ImageButton>(com.coulterpeterson.floatnative.R.id.btn_like)
                        val btnDislike = playerView.findViewById<android.widget.ImageButton>(com.coulterpeterson.floatnative.R.id.btn_dislike)
                        val btnDesc = playerView.findViewById<android.widget.ImageButton>(com.coulterpeterson.floatnative.R.id.btn_description)
                        val btnComments = playerView.findViewById<android.widget.ImageButton>(com.coulterpeterson.floatnative.R.id.btn_comments)
                        val btnSettings = playerView.findViewById<android.widget.ImageButton>(com.coulterpeterson.floatnative.R.id.btn_settings)

                        // Update UI State (Colors)
                        val redColor = android.graphics.Color.RED
                        val whiteColor = android.graphics.Color.WHITE
                        val grayColor = android.graphics.Color.LTGRAY

                        val interaction = (state as? VideoPlayerState.Content)?.userInteraction
                        
                        btnLike?.setColorFilter(if (interaction == com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction.like) 
                            redColor else whiteColor)
                        
                        btnDislike?.setColorFilter(if (interaction == com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction.dislike) 
                            redColor else whiteColor)

                        // Set Listeners (only need to set once, but Compose update might be frequent, safe to re-set or memoize if needed. 
                        // For simplicity setting here is fine)
                        btnLike?.setOnClickListener { viewModel.toggleLike() }
                        btnDislike?.setOnClickListener { viewModel.toggleDislike() }
                        
                        btnDesc?.setOnClickListener { 
                            // TODO: Show Description
                        }
                        btnComments?.setOnClickListener { 
                            // TODO: Show Comments
                        }
                         btnSettings?.setOnClickListener { 
                            showSettings = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                if (showSettings) {
                   val contentState = state as VideoPlayerState.Content
                   Dialog(onDismissRequest = { showSettings = false }) {
                       Box(
                           modifier = Modifier
                               .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                               .padding(16.dp)
                               .width(300.dp)
                       ) {
                           Column {
                               Text("Quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                               LazyColumn {
                                   items(contentState.availableQualities.size) { index ->
                                       val quality = contentState.availableQualities[index]
                                       val isSelected = quality == contentState.currentQuality
                                       ListItem(
                                           selected = isSelected,
                                           onClick = {
                                               viewModel.changeQuality(quality)
                                               showSettings = false
                                           },
                                           headlineContent = { Text(quality.label) }
                                       )
                                   }
                               }
                           }
                       }
                   }
                }
            }
        }
    }
}
