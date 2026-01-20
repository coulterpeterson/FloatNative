@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.coulterpeterson.floatnative.ui.screens.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.viewmodels.LivePlayerState
import com.coulterpeterson.floatnative.viewmodels.LivePlayerViewModel
import com.coulterpeterson.floatnative.viewmodels.PlayerSidebarMode

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLivePlayerScreen(
    liveStreamId: String,
    onBack: () -> Unit,
    viewModel: LivePlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val exoPlayer = viewModel.player
    val context = LocalContext.current

    LaunchedEffect(liveStreamId) {
        viewModel.loadStream(liveStreamId)
    }

    // Handle Player state updates
    // In LivePlayerViewModel, player prep is handled inside loadStream/Content state logic mostly,
    // but we ensure it's playing here if needed.
    // LivePlayerViewModel sets playWhenReady = true inside loadStream.

    // Clean up only when leaving the screen completely
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.pause()
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
                is LivePlayerState.Loading -> {
                     Text("Loading Live Stream...", color = Color.White)
                }
                is LivePlayerState.Error -> {
                    Text(
                        text = "Error: ${(state as LivePlayerState.Error).message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    // Content or Idle - Show Player
                    var showSettings by remember { mutableStateOf(false) }

                    AndroidView(
                        factory = { ctx ->
                            // Inflate the wrapper layout which sets the custom controller attribute
                            // We reuse the SAME layout as TvVideoPlayerScreen but hide buttons dynamically
                            val view = android.view.LayoutInflater.from(ctx).inflate(
                                com.coulterpeterson.floatnative.R.layout.tv_player_wrapper, 
                                null
                            ) as PlayerView
                            
                            view.apply {
                                player = exoPlayer
                                // Handle D-pad wakeup using KeyListener
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
                                    if (visibility == android.view.View.GONE && sidebarMode == PlayerSidebarMode.None) {
                                      requestFocus()
                                    }
                                })

                                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                requestFocus()
                            }
                        },
                        update = { playerView ->
                            // Bind Custom Controls & Hide Unsupported Ones
                            val btnLike = playerView.findViewById<android.view.View>(com.coulterpeterson.floatnative.R.id.btn_like)
                            val btnDislike = playerView.findViewById<android.view.View>(com.coulterpeterson.floatnative.R.id.btn_dislike)
                            val btnComments = playerView.findViewById<android.view.View>(com.coulterpeterson.floatnative.R.id.btn_comments)
                            
                            val btnDesc = playerView.findViewById<android.view.View>(com.coulterpeterson.floatnative.R.id.btn_description)
                            val btnSettings = playerView.findViewById<android.view.View>(com.coulterpeterson.floatnative.R.id.btn_settings)

                            // HIDE unsupported buttons
                            btnLike?.visibility = android.view.View.GONE
                            btnDislike?.visibility = android.view.View.GONE
                            btnComments?.visibility = android.view.View.GONE

                            // Set Listeners for supported buttons
                            btnDesc?.setOnClickListener { 
                                viewModel.openDescription()
                            }
                             // Settings (Quality) - Live streams might support quality depending on backend
                            btnSettings?.setOnClickListener { 
                                // showSettings = true // Logic for quality selection needed? 
                                // LivePlayerViewModel doesn't seem to expose qualities yet easily?
                                // For MVP, let's hide settings or leave it no-op if logic missing
                                // Re-using existing ViewModel doesn't have quality logic exposed same way as VideoPlayerViewModel
                                // Hiding for now to be safe/clean as per request "copy... modified... no comments/like/dislike"
                                // User didn't explicitly ask to remove settings but "modified ... that doesn't include comments, like, or dislike buttons".
                                // Preserving Description.
                            }
                            // Hide settings if no logic
                             btnSettings?.visibility = android.view.View.GONE
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        // Sidebar
        AnimatedVisibility(
            visible = sidebarMode != PlayerSidebarMode.None,
            enter = slideInHorizontally { it } + expandHorizontally(),
            exit = slideOutHorizontally { it } + shrinkHorizontally()
        ) {
            val contentState = state as? LivePlayerState.Content
            if (contentState != null) {
                // Reuse TvVideoPlayerSidebar but pass empty comments
                com.coulterpeterson.floatnative.ui.components.tv.TvVideoPlayerSidebar(
                    mode = sidebarMode,
                    descriptionHtml = contentState.description,
                    title = contentState.streamTitle,
                    publishDate = null, // Live streams don't really have a release date in this context
                    comments = emptyList(),
                    onDismiss = { viewModel.closeSidebar() },
                    onSeek = { /* Seek not supported in live usually, or handled differently */ }
                )
            }
        }
    }
}
