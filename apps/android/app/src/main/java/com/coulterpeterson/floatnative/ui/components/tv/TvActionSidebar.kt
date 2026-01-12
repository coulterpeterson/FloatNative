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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
    val isInWatchLater: Boolean
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvActionSidebar(
    state: SidebarState,
    actions: SidebarActions,
    onDismiss: () -> Unit
) {
    // Intercept Back Press to close sidebar
    BackHandler(onBack = onDismiss)

    val focusRequester = remember { FocusRequester() }
    
    // Track if the initial long-press release has been handled
    var initialReleaseHandled by remember { mutableStateOf(false) }

    fun Modifier.consumeFirstKeyRelease(): Modifier = this.onPreviewKeyEvent { event ->
        android.util.Log.d("SidebarButton", "PreviewKeyEvent: type=${event.type} key=${event.key} handled=$initialReleaseHandled")
        if (!initialReleaseHandled) {
             if (event.type == KeyEventType.KeyUp && 
                 (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                 android.util.Log.d("SidebarButton", "Consuming initial release (Preview)")
                 initialReleaseHandled = true
                 return@onPreviewKeyEvent true // Consume the release event
             }
             // We DO NOT consume other events here, let them pass to children
             // Unless it's the specific release we want to block.
             // Wait, if we are holding the button, we might see repeats?
             // Actually, if we consume the Release, the Click won't happen (usually triggered on Up).
        }
        false
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .consumeFirstKeyRelease(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (Optional: Title/Thumbnail)
                Text(
                    text = state.post.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Actions
                SidebarButton(
                    text = "Play",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        android.util.Log.d("SidebarButton", "Play Clicked")
                        actions.onPlay()
                        onDismiss()
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )

                // Like
                val isLiked = state.interaction == ContentPostV3Response.UserInteraction.like
                SidebarButton(
                    text = if (isLiked) "Liked" else "Like", // Or just "Like" with filled icon
                    icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    onClick = actions.onLike
                )

                // Dislike
                val isDisliked = state.interaction == ContentPostV3Response.UserInteraction.dislike
                SidebarButton(
                    text = "Dislike",
                    icon = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                    onClick = actions.onDislike
                )

                // Watch Later
                val isInWatchLater = actions.isInWatchLater
                SidebarButton(
                    text = if (isInWatchLater) "Remove from Watch Later" else "Save to Watch Later",
                    icon = if (isInWatchLater) Icons.Default.Check else Icons.Default.WatchLater, // Check or WatchLater icon
                    onClick = {
                         actions.onWatchLater()
                         onDismiss()
                    }
                )
                
                // Mark as Watched
                SidebarButton(
                    text = "Mark as Watched",
                    icon = Icons.Default.CheckCircle,
                    onClick = {
                        actions.onMarkWatched()
                        onDismiss()
                    }
                )

                // Save to Playlist
                SidebarButton(
                    text = "Save to Playlist",
                    icon = Icons.Default.List,
                    onClick = {
                        actions.onAddToPlaylist()
                        onDismiss()
                    }
                )
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
