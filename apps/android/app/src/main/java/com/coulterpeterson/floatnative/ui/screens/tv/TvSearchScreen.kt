package com.coulterpeterson.floatnative.ui.screens.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.ui.components.tv.SidebarActions
import com.coulterpeterson.floatnative.ui.components.tv.SidebarUiState
import com.coulterpeterson.floatnative.ui.components.tv.TvActionSidebar
import com.coulterpeterson.floatnative.ui.components.tv.TvSearchKeyboard
import com.coulterpeterson.floatnative.ui.components.tv.TvVideoCard
import com.coulterpeterson.floatnative.viewmodels.SearchState
import com.coulterpeterson.floatnative.viewmodels.SearchViewModel
import com.coulterpeterson.floatnative.viewmodels.SidebarView

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    onPlayVideo: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val sidebarState by viewModel.sidebarState.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val context = LocalContext.current
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    val lastFocusedPostId by viewModel.lastFocusedId.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Restore focus when sidebar closes
    androidx.compose.runtime.LaunchedEffect(sidebarState) {
        if (sidebarState == null && lastFocusedPostId != null) {
            try { focusRequesters[lastFocusedPostId]?.requestFocus() } catch (e: Exception) {}
        }
    }

    // Restore focus on re-entry (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                 if (lastFocusedPostId != null) {
                     try { focusRequesters[lastFocusedPostId]?.requestFocus() } catch (e: Exception) {}
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

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left: Keyboard
            Column(
                modifier = Modifier.weight(0.4f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (query.isEmpty()) "Search" else query,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                TvSearchKeyboard(
                    onCharClick = { char -> viewModel.onQueryChange(query + char) },
                    onBackspaceClick = { 
                        if (query.isNotEmpty()) {
                            viewModel.onQueryChange(query.dropLast(1))
                        }
                    },
                    onSpaceClick = { viewModel.onQueryChange(query + " ") },
                    onClearClick = { viewModel.onQueryChange("") },
                    onSearchClick = { 
                        viewModel.performSearch()
                         // Try to focus first item if exists immediately (for initial search)
                        val currentState = state
                        if (currentState is SearchState.Content && currentState.results.isNotEmpty()) {
                             val firstId = currentState.results.first().id
                             viewModel.setLastFocusedId(firstId) // Track it
                             try { focusRequesters[firstId]?.requestFocus() } catch(e: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right: Results Grid
            Column(modifier = Modifier.weight(0.6f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (val s = state) {
                        is SearchState.Idle -> {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(top = 50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Enter search term",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is SearchState.Loading -> {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(top = 50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Searching...", color = Color.White)
                                }
                            }
                        }
                        is SearchState.Empty -> {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(top = 50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No results for '$query'", color = Color.White)
                                }
                            }
                        }
                        is SearchState.Error -> {
                            item(span = { GridItemSpan(3) }) {
                                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is SearchState.Content -> {
                            items(s.results.size) { index ->
                                val post = s.results[index]
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
                                    modifier = Modifier.focusRequester(requester)
                                )
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
