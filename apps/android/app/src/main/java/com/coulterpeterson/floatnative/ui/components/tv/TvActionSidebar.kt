package com.coulterpeterson.floatnative.ui.components.tv

import androidx.activity.compose.BackHandler
import com.coulterpeterson.floatnative.openapi.models.ImageModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import coil.compose.AsyncImage
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction
import com.coulterpeterson.floatnative.viewmodels.SidebarView

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

data class SidebarUiState(
    val title: String,
    val thumbnail: ImageModel?,
    val postId: String,
    val interaction: UserInteraction? = null,
    val currentView: SidebarView = SidebarView.Main
)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvActionSidebar(
    state: SidebarUiState,
    actions: SidebarActions,
    onDismiss: () -> Unit
) {
    // Intercept Back Press
    BackHandler {
        if (state.currentView == SidebarView.Playlists) {
            actions.onBack()
        } else {
            onDismiss()
        }
    }

    val playFocusRequester = remember { FocusRequester() }
    val saveToPlaylistFocusRequester = remember { FocusRequester() }
    val firstPlaylistFocusRequester = remember { FocusRequester() }
    
    // SINGLE LIST STATE for the entire sidebar
    val listState = rememberTvLazyListState()

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
    
    // Focus Management Logic for View Transitions
    LaunchedEffect(state.currentView) {
        if (state.currentView == SidebarView.Playlists) {
            // Moving to Playlists: Focus the first item
            // We use a small delay or just rely on composition order, but with single list 
            // the state persistence is much better.
            try {
                // Determine scrolling needs. Usually index 0 for playlists.
                listState.scrollToItem(0)
                firstPlaylistFocusRequester.requestFocus()
            } catch(e: Exception) {}
        } else {
            // Returning to Main: Focus the "Save" button (approx index 7)
            try {
                // Item 7 is "Save to Playlist"
                listState.scrollToItem(7)
                saveToPlaylistFocusRequester.requestFocus()
            } catch(e: Exception) {}
        }
    }
    
    // Initial Focus
    LaunchedEffect(Unit) {
        if (state.currentView == SidebarView.Main) {
            try {
                listState.scrollToItem(2) // Play button
                playFocusRequester.requestFocus()
            } catch(e: Exception) {}
        }
    }

    // Thumbnail Logic
    val thumbnailUrl = state.thumbnail?.path?.toString()
    val fullThumbnailUrl = if (thumbnailUrl?.startsWith("http") == true) thumbnailUrl else "https://pbs.floatplane.com${thumbnailUrl}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Sidebar Container
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(350.dp)
                .background(Color(0xFF1E1E1E))
                .clickable(enabled = false, onClick = {})
        ) {
            // SINGLE LIST - Content Swaps Inside
            TvLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeFirstKeyRelease(),
                pivotOffsets = PivotOffsets(0.7f),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.currentView == SidebarView.Main) {
                    // --- MAIN VIEW ITEMS ---
                    
                    // Thumbnail (0)
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
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Play (2)
                    item {
                        SidebarButton(
                            text = "Play",
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                actions.onPlay()
                                onDismiss()
                            },
                            modifier = Modifier
                                .focusRequester(playFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        true // Consume the event, preventing scroll up
                                    } else {
                                        false
                                    }
                                }
                        )
                    }
                    
                    // Like (3)
                    item {
                        val isLiked = state.interaction == UserInteraction.like
                        SidebarButton(
                            text = if (isLiked) "Liked" else "Like",
                            icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            onClick = actions.onLike
                        )
                    }

                    // Dislike (4)
                    item {
                        val isDisliked = state.interaction == UserInteraction.dislike
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
                            onClick = actions.onWatchLater
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
                    
                } else {
                    // --- PLAYLIST VIEW ITEMS ---
                    
                    // Header (0)
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
                                text = "No playlists found.\nCreate one on mobile/web.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        items(displayablePlaylists.size) { index ->
                            val playlist = displayablePlaylists[index]
                            val isAdded = playlist.videoIds.contains(state.postId)
                            val isFirst = index == 0
                            val isLast = index == displayablePlaylists.lastIndex
                            
                            var itemModifier: Modifier = Modifier
                            if (isFirst) {
                                itemModifier = itemModifier.focusRequester(firstPlaylistFocusRequester)
                            }

                            itemModifier = itemModifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    if (isFirst && event.key == Key.DirectionUp) {
                                        return@onPreviewKeyEvent true // Prevent scroll up past top
                                    }
                                    if (isLast && event.key == Key.DirectionDown) {
                                        return@onPreviewKeyEvent true // Prevent scroll down past bottom
                                    }
                                }
                                false
                            }
                            
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
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
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
