package com.coulterpeterson.floatnative.ui.screens

import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.coulterpeterson.floatnative.ui.components.CommentSection
import com.coulterpeterson.floatnative.ui.components.VideoActionButtons
import com.coulterpeterson.floatnative.ui.components.VideoDescription
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerState
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerViewModel

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    postId: String,
    onClose: () -> Unit = {},
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    // Very simple check for landscape
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var showQualityDialog by remember { mutableStateOf(false) }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            com.coulterpeterson.floatnative.api.FloatplaneApi.okHttpClient
        )
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Load video
    LaunchedEffect(postId) {
        viewModel.loadVideo(postId)
    }

    // Observe state and update player
    LaunchedEffect(state) {
        if (state is VideoPlayerState.Content) {
            val contentState = state as VideoPlayerState.Content
            // Avoid re-preparing if already playing same URL
            // Simple check
            // Ideally check currentMediaItem?.mediaId or similar
            if (exoPlayer.currentMediaItem == null || exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() != contentState.videoUrl) {
                val mediaItem = MediaItem.fromUri(contentState.videoUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLandscape) {
            // Fullscreen Player
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)) {
                VideoPlayerView(exoPlayer)
            }
        } else {
            // Portrait Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 1. Video Player (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    VideoPlayerView(exoPlayer)
                }

                // 2. Scrollable Content
                when (val currentState = state) {
                    is VideoPlayerState.Loading, VideoPlayerState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is VideoPlayerState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error: ${currentState.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is VideoPlayerState.Content -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Title & Metadata
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = currentState.blogPost.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${currentState.blogPost.channel.title} • ${DateUtils.getRelativeTimeSpanString(currentState.blogPost.releaseDate.toInstant().toEpochMilli())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Actions
                            item {
                                VideoActionButtons(
                                    likes = currentState.likes,
                                    dislikes = currentState.dislikes,
                                    userInteraction = currentState.userInteraction,
                                    onLikeClick = { viewModel.toggleLike() },
                                    onDislikeClick = { viewModel.toggleDislike() },
                                    onDownloadClick = { viewModel.downloadVideo() },
                                    onShareClick = { /* Implement Share */ }
                                )
                                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            
                            // Description
                            item {
                                VideoDescription(
                                    title = "Description", 
                                    descriptionHtml = currentState.blogPost.text, 
                                    releaseDate = currentState.blogPost.releaseDate
                                )
                                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            
                            // Quality Selector (Example UI)
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            showQualityDialog = true
                                        }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Resolution")
                                    Text(
                                        text = currentState.currentQuality?.label ?: "Auto",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            
                            // Comments
                            item {
                                CommentSection(
                                    comments = currentState.comments,
                                    isLoading = currentState.isLoadingComments,
                                    totalComments = currentState.blogPost.comments // Or currentState.comments.size if flattened?
                                )
                            }
                            
                            // Add extra padding at bottom
                            item {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                        
                        // Quality Dialog
                        if (showQualityDialog) {
                            AlertDialog(
                                onDismissRequest = { showQualityDialog = false },
                                title = { Text("Select Resolution") },
                                text = {
                                    LazyColumn {
                                        items(currentState.availableQualities) { quality ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.changeQuality(quality)
                                                        showQualityDialog = false
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = quality == currentState.currentQuality,
                                                    onClick = null // Handled by Row clickable
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = quality.label)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showQualityDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(exoPlayer: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
