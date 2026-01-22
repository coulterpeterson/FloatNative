package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.ui.components.tv.TvVideoCard
import com.coulterpeterson.floatnative.viewmodels.HistoryState
import com.coulterpeterson.floatnative.viewmodels.HistoryViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.coulterpeterson.floatnative.ui.components.tv.SidebarUiState
import com.coulterpeterson.floatnative.ui.components.tv.TvActionSidebar
import com.coulterpeterson.floatnative.ui.components.tv.SidebarActions
import com.coulterpeterson.floatnative.viewmodels.SidebarView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.widget.Toast
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHistoryScreen(
    onPlayVideo: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val sidebarState by viewModel.sidebarState.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val context = LocalContext.current
    
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val lastFocusedId by viewModel.lastFocusedId.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Restore focus when sidebar closes
    androidx.compose.runtime.LaunchedEffect(sidebarState) {
        if (sidebarState == null && lastFocusedId != null) {
            try { focusRequesters[lastFocusedId]?.requestFocus() } catch (e: Exception) {}
        }
    }

    // Restore focus on re-entry (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                 if (lastFocusedId != null) {
                     try { focusRequesters[lastFocusedId]?.requestFocus() } catch (e: Exception) {}
                 }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle Back press to close sidebar if open
    BackHandler(enabled = sidebarState != null) {
        viewModel.closeSidebar()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        when (val s = state) {
            is HistoryState.Loading, HistoryState.Initial -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading history...", color = Color.White)
                }
            }
            is HistoryState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is HistoryState.Content -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 50.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    s.groupedHistory.forEach { (dateHeader, items) ->
                        // Header
                        item(span = { GridItemSpan(4) }) {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        
                        // Items
                        items(items) { historyItem ->
                            val post = historyItem.blogPost
                            if (post != null) {
                                // Progress is 0-100 int. Convert to 0.0-1.0 float.
                                val progressFloat = if (historyItem.progress > 0) historyItem.progress / 100f else 0f
                                val requester = focusRequesters.getOrPut(post.id) { FocusRequester() }
                                
                                TvVideoCard(
                                    post = post,
                                    onClick = {
                                        viewModel.setLastFocusedId(post.id)
                                        if (!post.videoAttachments.isNullOrEmpty()) {
                                            onPlayVideo(post.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.setLastFocusedId(post.id)
                                        viewModel.openSidebar(post)
                                    },
                                    modifier = Modifier.focusRequester(requester),
                                    progress = progressFloat
                                )
                            }
                        }
                    }
                    
                    if (s.groupedHistory.isEmpty()) {
                        item(span = { GridItemSpan(4) }) {
                             Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No watch history found.", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        
        // Sidebar Overlay
        AnimatedVisibility(
            visible = sidebarState != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val currentSidebarState = sidebarState
            if (currentSidebarState != null) {
                // Determine Watch Later status
                val watchLaterPlaylist = userPlaylists.find { it.isWatchLater }
                val isInWatchLater = watchLaterPlaylist?.videoIds?.contains(currentSidebarState.post.id) == true

                TvActionSidebar(
                    state = SidebarUiState(
                        title = currentSidebarState.post.title,
                        thumbnail = currentSidebarState.post.thumbnail,
                        postId = currentSidebarState.post.id,
                        interaction = currentSidebarState.interaction,
                        currentView = currentSidebarState.currentView
                    ),
                    actions = SidebarActions(
                        onPlay = { 
                            if (!currentSidebarState.post.videoAttachments.isNullOrEmpty()) {
                                onPlayVideo(currentSidebarState.post.id)
                            }
                        },
                        onLike = { viewModel.toggleSidebarLike() },
                        onDislike = { viewModel.toggleSidebarDislike() },
                        onWatchLater = { 
                             viewModel.toggleWatchLater(currentSidebarState.post) { wasAdded ->
                                 val msg = if (wasAdded) "Added to Watch Later" else "Removed from Watch Later"
                                 Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                             }
                        },
                        onMarkWatched = { 
                            viewModel.markAsWatched(currentSidebarState.post)
                            Toast.makeText(context, "Marked as watched", Toast.LENGTH_SHORT).show()
                        },
                        onAddToPlaylist = { 
                             viewModel.toggleSidebarView(SidebarView.Playlists)
                        },
                        onShowPlaylists = {
                            viewModel.toggleSidebarView(SidebarView.Playlists)
                        },
                        onBack = {
                            viewModel.toggleSidebarView(SidebarView.Main)
                        },
                        onTogglePlaylist = { playlist ->
                            viewModel.togglePlaylistMembership(playlist, currentSidebarState.post)
                        },
                        isInWatchLater = isInWatchLater,
                        userPlaylists = userPlaylists
                    ),
                    onDismiss = { viewModel.closeSidebar() }
                )
            }
        }
    }
}
