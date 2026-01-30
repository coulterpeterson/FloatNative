@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight

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
    startTimestamp: Long = 0L,
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val exoPlayer = viewModel.player
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }
    
    // Initial Seek for Cast Resume
    // We only want to do this ONCE when the content is first loaded and matches our requested video
    // Use a remembered boolean that resets when videoId changes
    var hasPerformedInitialSeek by remember(videoId) { mutableStateOf(false) }

    // Handle Player state updates
    LaunchedEffect(state, hasPerformedInitialSeek) {
        if (state is VideoPlayerState.Content) {
            val contentState = state as VideoPlayerState.Content
            if (exoPlayer.currentMediaItem == null || exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() != contentState.videoUrl) {
                val mediaItem = MediaItem.fromUri(contentState.videoUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                
                 if (startTimestamp > 0 && !hasPerformedInitialSeek) {
                    exoPlayer.seekTo(startTimestamp)
                    hasPerformedInitialSeek = true
                }
                
                exoPlayer.play() 
            } else if (startTimestamp > 0 && !hasPerformedInitialSeek) {
                // If the player was somehow already holding this item (unlikely with new VM scope, but safe)
                 exoPlayer.seekTo(startTimestamp)
                 hasPerformedInitialSeek = true
            }
        }
    }
    
    // Clean up only when leaving the screen completely (handled by ViewModel usually, but stop on dispose here for TV nav)
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.pause()
            viewModel.saveWatchProgress()
        }
    }

    // Handle App Lifecycle (Backgrounding)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.saveWatchProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sidebar State
    val sidebarMode by viewModel.sidebarMode.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Player Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
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
                            // Inflate the wrapper layout which sets the custom controller attribute
                            val view = android.view.LayoutInflater.from(ctx).inflate(
                                com.coulterpeterson.floatnative.R.layout.tv_player_wrapper, 
                                null
                            ) as PlayerView
                            
                            view.apply {
                                player = exoPlayer
                                // XML sets resize_mode="fit", show_buffering="always", etc.
                                
                                // Handle D-pad wakeup using KeyListener instead of overriding dispatchKeyEvent
                                setOnKeyListener { _, keyCode, event ->
                                    if (event.action == android.view.KeyEvent.ACTION_DOWN && !isControllerFullyVisible) {
                                        when (keyCode) {
                                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                            android.view.KeyEvent.KEYCODE_ENTER,
                                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                showController()
                                                return@setOnKeyListener true
                                            }
                                        }
                                    }
                                    false
                                }

                                setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                                    if (visibility == android.view.View.GONE && sidebarMode == com.coulterpeterson.floatnative.viewmodels.PlayerSidebarMode.None) {
                                      requestFocus()
                                    }
                                })

                                // Ensure the view takes focus to handle D-pad events
                                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                requestFocus()
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

                            // Set Listeners
                            btnLike?.setOnClickListener { viewModel.toggleLike() }
                            btnDislike?.setOnClickListener { viewModel.toggleDislike() }
                            
                            btnDesc?.setOnClickListener { 
                                viewModel.openDescription()
                            }
                            btnComments?.setOnClickListener { 
                                viewModel.openComments()
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
        
        // Sidebar
        androidx.compose.animation.AnimatedVisibility(
            visible = sidebarMode != com.coulterpeterson.floatnative.viewmodels.PlayerSidebarMode.None,
            enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.expandHorizontally(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.shrinkHorizontally()
        ) {
            val contentState = state as? VideoPlayerState.Content
            if (contentState != null) {
                com.coulterpeterson.floatnative.ui.components.tv.TvVideoPlayerSidebar(
                    mode = sidebarMode,
                    descriptionHtml = contentState.blogPost.text ?: "",
                    title = contentState.blogPost.title,
                    publishDate = contentState.blogPost.releaseDate,
                    comments = contentState.comments,
                    onDismiss = { viewModel.closeSidebar() },
                    onSeek = { viewModel.seekTo(it) }
                )
            }
        }
    }
}
