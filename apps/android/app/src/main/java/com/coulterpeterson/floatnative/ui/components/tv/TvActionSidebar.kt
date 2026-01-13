package com.coulterpeterson.floatnative.ui.components.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import com.coulterpeterson.floatnative.viewmodels.SidebarState

data class SidebarActions(
    val onPlay: () -> Unit,
    val onLike: () -> Unit,
    val onDislike: () -> Unit,
    val onWatchLater: () -> Unit,
    val onMarkWatched: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onShowPlaylists: () -> Unit,
    val onBack: () -> Unit,
    val onTogglePlaylist: (com.coulterpeterson.floatnative.api.Playlist) -> Unit,
    val isInWatchLater: Boolean,
    val userPlaylists: List<com.coulterpeterson.floatnative.api.Playlist>
)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvActionSidebar(
    state: SidebarState,
    actions: SidebarActions,
    onDismiss: () -> Unit
) {
    // Intercept Back Press to handle navigation or dismiss
    BackHandler {
        if (state.currentView == com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists) {
            actions.onBack()
        } else {
            onDismiss()
        }
    }

    val focusRequester = remember { FocusRequester() }
    
    // Track if the initial long-press release has been handled
    var initialReleaseHandled by remember { mutableStateOf(false) }

    fun Modifier.consumeFirstKeyRelease(): Modifier = this.onPreviewKeyEvent { event ->
        if (!initialReleaseHandled) {
             if (event.type == KeyEventType.KeyUp && 
                 (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                 initialReleaseHandled = true
                 return@onPreviewKeyEvent true // Consume the release event
             }
        }
        false
    }

    // Thumbnail Logic
    val thumbnailUrl = state.post.thumbnail?.path?.toString()
    val fullThumbnailUrl = if (thumbnailUrl?.startsWith("http") == true) thumbnailUrl else "https://pbs.floatplane.com${thumbnailUrl}"

    // Content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)) // Dim background
            .clickable(onClick = onDismiss) // Click outside to dismiss
            ,
        contentAlignment = Alignment.CenterEnd
    ) {
        // Prevent click propagation to background content
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(350.dp)
                .background(Color(0xFF1E1E1E))
                .clickable(enabled = false, onClick = {}) // Consume clicks
        ) {
            AnimatedVisibility(
                visible = state.currentView == com.coulterpeterson.floatnative.viewmodels.SidebarView.Main,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
                modifier = Modifier.fillMaxSize()
            ) {
                 TvLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeFirstKeyRelease()
                        .focusProperties { exit = { FocusRequester.Cancel } }, // Trap focus locally
                    pivotOffsets = PivotOffsets(0.7f),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    item {
                        AsyncImage(
                            model = fullThumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    item {
                        Text(
                            text = state.post.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Actions
                    item {
                        SidebarButton(
                            text = "Play",
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                actions.onPlay()
                                onDismiss()
                            },
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                    }

                    // Like
                    item {
                        val isLiked = state.interaction == ContentPostV3Response.UserInteraction.like
                        SidebarButton(
                            text = if (isLiked) "Liked" else "Like",
                            icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            onClick = actions.onLike
                        )
                    }

                    // Dislike
                    item {
                        val isDisliked = state.interaction == ContentPostV3Response.UserInteraction.dislike
                        SidebarButton(
                            text = "Dislike",
                            icon = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                            onClick = actions.onDislike
                        )
                    }

                    // Watch Later
                    item {
                        val isInWatchLater = actions.isInWatchLater
                        SidebarButton(
                            text = if (isInWatchLater) "Remove from Watch Later" else "Save to Watch Later",
                            icon = if (isInWatchLater) Icons.Default.Check else Icons.Default.WatchLater, 
                            onClick = {
                                 actions.onWatchLater()
                                 onDismiss()
                            }
                        )
                    }
                    
                    // Mark as Watched
                    item {
                        SidebarButton(
                            text = "Mark as Watched",
                            icon = Icons.Default.CheckCircle,
                            onClick = {
                                actions.onMarkWatched()
                                onDismiss()
                            }
                        )
                    }

                    // Save to Playlist
                    item {
                        SidebarButton(
                            text = "Save to Playlist",
                            icon = Icons.Default.List,
                            onClick = actions.onShowPlaylists
                        )
                    }
                }
            }

            // Playlist Sub-View
            AnimatedVisibility(
                visible = state.currentView == com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.fillMaxSize()
            ) {
                 TvLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { exit = { FocusRequester.Cancel } }, 
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Save to Playlist",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    items(actions.userPlaylists.size) { index ->
                        val playlist = actions.userPlaylists[index]
                        // Don't show Watch Later here, handled separately
                        if (!playlist.isWatchLater) {
                            val isAdded = playlist.videoIds.contains(state.post.id)
                            SidebarButton(
                                text = playlist.name,
                                icon = if (isAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                                onClick = { actions.onTogglePlaylist(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Request Focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SidebarButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // decorative
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
