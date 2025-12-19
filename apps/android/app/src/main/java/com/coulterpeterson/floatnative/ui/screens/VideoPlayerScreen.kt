package com.coulterpeterson.floatnative.ui.screens

import android.content.res.Configuration
import android.text.format.DateUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.coulterpeterson.floatnative.ui.components.CommentSection
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import com.coulterpeterson.floatnative.ui.components.VideoActionButtons
import com.coulterpeterson.floatnative.ui.components.VideoDescription
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerState
import com.coulterpeterson.floatnative.viewmodels.VideoPlayerViewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import com.coulterpeterson.floatnative.LocalPipMode

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    postId: String,
    onClose: () -> Unit = {},
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val configuration = LocalConfiguration.current
    // Check PiP mode
    val isInPipMode = LocalPipMode.current
    
    // Landscape or PiP means fullscreen player usually
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
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
    
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCommentInput by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setDownloadLocation(uri.toString())
        }
    }

    // ExoPlayer is now managed by ViewModel to survive configuration changes
    val exoPlayer = viewModel.player

    // Load video
    LaunchedEffect(postId) {
        viewModel.loadVideo(postId)
    }
    
    // Handle Permission Request
    LaunchedEffect(Unit) {
        viewModel.requestPermissionEvent.collect {
             // Show Toast explaining why?
             android.widget.Toast.makeText(context, "Select a folder to save downloads", android.widget.Toast.LENGTH_LONG).show()
             folderPickerLauncher.launch(null)
        }
    }

    // Handle Download Completion
    LaunchedEffect(Unit) {
        viewModel.downloadCompletedEvent.collect { fileName ->
             android.widget.Toast.makeText(context, "Saved $fileName", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Handle Player Actions
    LaunchedEffect(Unit) {
        viewModel.playerAction.collect { action ->
            when (action) {
                is com.coulterpeterson.floatnative.viewmodels.PlayerAction.Seek -> {
                    exoPlayer.seekTo(action.position)
                    exoPlayer.play() // Auto-play after seek
                }
            }
        }
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

    // Monitor Video Size for PiP Aspect Ratio
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                super.onVideoSizeChanged(videoSize)
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = android.util.Rational(videoSize.width, videoSize.height)
                    (context as? com.coulterpeterson.floatnative.MainActivity)?.updatePipParams(ratio)
                }
            }
        }
        exoPlayer.addListener(listener)
        // Check initial size
        val format = exoPlayer.videoFormat
        if (format != null && format.width > 0 && format.height > 0) {
             val ratio = android.util.Rational(format.width, format.height)
             (context as? com.coulterpeterson.floatnative.MainActivity)?.updatePipParams(ratio)
        }
        
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isInPipMode || isLandscape) {
            // Fullscreen Player (Landscape or PiP)
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)) {
                VideoPlayerView(exoPlayer)
            }
        } else {
            // Portrait Layout
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    onQualityClick = { showQualityDialog = true },
                                    onPlaylistClick = { viewModel.togglePlaylistSheet(true) },
                                    qualityLabel = currentState.currentQuality?.label ?: "Auto"
                                )
                                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            
                            // Description
                            item {
                                VideoDescription(
                                    title = "Description", 
                                    descriptionHtml = currentState.blogPost.text, 
                                    releaseDate = currentState.blogPost.releaseDate,
                                    onSeek = { pos -> viewModel.seekTo(pos) }
                                )
                                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            
                            // Comments
                            item {
                                CommentSection(
                                    comments = currentState.comments,
                                    isLoading = currentState.isLoadingComments,
                                    totalComments = currentState.blogPost.comments, // Or currentState.comments.size if flattened?
                                    onLikeComment = viewModel::likeComment,
                                    onDislikeComment = viewModel::dislikeComment,
                                    onReplyComment = { comment ->
                                        viewModel.startReply(comment)
                                        showCommentInput = true
                                    },
                                    onSeek = { pos -> viewModel.seekTo(pos) }
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
                
                // FAB for Comments
                FloatingActionButton(
                    onClick = { showCommentInput = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Comment")
                }
            }
        }
    }
    
    // Comment Input Sheet
    if (showCommentInput) {
        ModalBottomSheet(
            onDismissRequest = { 
                showCommentInput = false
                viewModel.cancelReply() 
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    // Ensure padding for keyboard
                     .navigationBarsPadding()
                    .imePadding()
            ) {
                val replyingTo = (state as? VideoPlayerState.Content)?.replyingToComment
                
                if (replyingTo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replying to ${replyingTo.user.username}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { viewModel.cancelReply() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Reply")
                        }
                    }
                } else {
                    Text(
                        text = "Add a comment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write something...") },
                    trailingIcon = {
                        IconButton(
                            onClick = { 
                                if (commentText.isNotBlank()) {
                                    viewModel.postComment(commentText)
                                    showCommentInput = false
                                    commentText = ""
                                }
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post")
                        }
                    },
                    singleLine = false,
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    // Playlist Sheet
    val contentState = state as? VideoPlayerState.Content
    if (contentState?.showPlaylistSheet == true) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.togglePlaylistSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            com.coulterpeterson.floatnative.ui.components.PlaylistSelectionSheet(
                playlists = contentState.userPlaylists,
                videoId = contentState.blogPost.videoAttachments?.firstOrNull()?.id ?: "",
                onToggleWatchLater = { viewModel.toggleWatchLater() },
                onAddToPlaylist = { id -> viewModel.addToPlaylist(id) },
                onRemoveFromPlaylist = { id -> viewModel.removeFromPlaylist(id) },
                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                onDismiss = { viewModel.togglePlaylistSheet(false) }
            )
        }
    }

    if (downloadState) {
        Dialog(onDismissRequest = { /* Prevent dismiss */ }) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Downloading...", style = MaterialTheme.typography.bodyLarge)
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
