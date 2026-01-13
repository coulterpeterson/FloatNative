package com.coulterpeterson.floatnative.ui.components.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable

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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
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

    val playFocusRequester = remember { FocusRequester() }
    val saveToPlaylistFocusRequester = remember { FocusRequester() }
    val firstPlaylistFocusRequester = remember { FocusRequester() }
    
    val mainListState = rememberTvLazyListState()
    val playlistListState = rememberTvLazyListState()

    // State to track previous view for focus restoration logic
    var lastView by remember { mutableStateOf(state.currentView) }
    
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
            androidx.compose.animation.AnimatedContent(
                targetState = state.currentView,
                transitionSpec = {
                    if (targetState == com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists) {
                         // Main -> Playlists: Slide In from Right
                         (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                             slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                         // Playlists -> Main: Slide In from Left
                         (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                             slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "SidebarTransition"
            ) { targetView ->
                when (targetView) {
                    com.coulterpeterson.floatnative.viewmodels.SidebarView.Main -> {
                         // Local Focus Management for Main View
                         LaunchedEffect(Unit) {
                             if (lastView == com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists) {
                                  try {
                                      // Scroll to Save button (last item ~ index 7) to ensure it's composed
                                      mainListState.scrollToItem(7) 
                                      saveToPlaylistFocusRequester.requestFocus()
                                  } catch (e: Exception) { 
                                       // If specific focus fails, standard restoration kicks in
                                  }
                             } else {
                                  try {
                                      mainListState.scrollToItem(2) // Play button index
                                      playFocusRequester.requestFocus()
                                  } catch (e: Exception) { }
                             }
                             lastView = com.coulterpeterson.floatnative.viewmodels.SidebarView.Main
                         }

                         TvLazyColumn(
                            state = mainListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeFirstKeyRelease()
                                .focusProperties { exit = { FocusRequester.Cancel } }, 
                            pivotOffsets = PivotOffsets(0.7f),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header (0)
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
                            
                            // Title (1)
                            item {
                                Text(
                                    text = state.post.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 2,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            // Actions - Play (2)
                            item {
                                SidebarButton(
                                    text = "Play",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = {
                                        actions.onPlay()
                                        onDismiss()
                                    },
                                    modifier = Modifier.focusRequester(playFocusRequester)
                                )
                            }

                            // Like (3)
                            item {
                                val isLiked = state.interaction == ContentPostV3Response.UserInteraction.like
                                SidebarButton(
                                    text = if (isLiked) "Liked" else "Like",
                                    icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                    onClick = actions.onLike
                                )
                            }

                            // Dislike (4)
                            item {
                                val isDisliked = state.interaction == ContentPostV3Response.UserInteraction.dislike
                                SidebarButton(
                                    text = "Dislike",
                                    icon = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                    onClick = actions.onDislike
                                )
                            }

                            // Watch Later (5)
                            item {
                                val isInWatchLater = actions.isInWatchLater
                                SidebarButton(
                                    text = if (isInWatchLater) "Remove from Watch Later" else "Save to Watch Later",
                                    icon = if (isInWatchLater) Icons.Default.Check else Icons.Default.WatchLater, 
                                    onClick = {
                                         actions.onWatchLater()
                                    }
                                )
                            }
                            
                            // Mark as Watched (6)
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

                            // Save to Playlist (7)
                            item {
                                SidebarButton(
                                    text = "Save to Playlist",
                                    icon = Icons.Default.List,
                                    onClick = actions.onShowPlaylists,
                                    modifier = Modifier.focusRequester(saveToPlaylistFocusRequester)
                                )
                            }
                        }
                    }
                    com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists -> {
                         // Local Focus Management for Playlist View
                         LaunchedEffect(Unit, actions.userPlaylists) {
                             if (actions.userPlaylists.isNotEmpty()) {
                                 try {
                                     // Ensure top is visible
                                     playlistListState.scrollToItem(0)
                                     // Focus the first specific item
                                     firstPlaylistFocusRequester.requestFocus()
                                 } catch (e: Exception) { }
                             }
                             lastView = com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists
                         }

                         TvLazyColumn(
                            state = playlistListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties { exit = { FocusRequester.Cancel } }, 
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Title Header
                            item {
                                Text(
                                    text = "Save to...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }

                            val displayablePlaylists = actions.userPlaylists
                            
                            if (displayablePlaylists.isEmpty()) {
                                item {
                                    Text(
                                        text = "No playlists found.\nCreate one on the web or mobile app.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            } else {
                                items(displayablePlaylists.size) { index ->
                                    val playlist = displayablePlaylists[index]
                                    val isAdded = playlist.videoIds.contains(state.post.id)
                                    
                                    val itemModifier = if (index == 0) Modifier.focusRequester(firstPlaylistFocusRequester) else Modifier
                                    
                                    SidebarButton(
                                        text = playlist.name,
                                        icon = if (isAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                                        onClick = { actions.onTogglePlaylist(playlist) },
                                        modifier = itemModifier
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
