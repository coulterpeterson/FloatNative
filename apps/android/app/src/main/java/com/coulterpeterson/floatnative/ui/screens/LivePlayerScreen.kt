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

    Scaffold(
        topBar = {
             // Only show top bar in portrait
             if (!isLandscape) {
                 TopAppBar(
                     title = { Text("Live") },
                     navigationIcon = {
                         IconButton(onClick = onClose) {
                             Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                         }
                     }
                 )
             }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
}

@kotlin.OptIn(UnstableApi::class)
@Composable
fun LiveVideoPlayerView(exoPlayer: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
