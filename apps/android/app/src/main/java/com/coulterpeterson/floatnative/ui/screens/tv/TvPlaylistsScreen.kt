package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.coulterpeterson.floatnative.ui.components.tv.TvPlaylistCard
import com.coulterpeterson.floatnative.ui.components.tv.TvVideoCard
import com.coulterpeterson.floatnative.viewmodels.PlaylistDetailState
import com.coulterpeterson.floatnative.viewmodels.PlaylistDetailViewModel
import com.coulterpeterson.floatnative.viewmodels.PlaylistsState
import com.coulterpeterson.floatnative.viewmodels.PlaylistsViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlaylistsScreen(
    onPlayVideo: (String) -> Unit,
    viewModel: PlaylistsViewModel = viewModel(),
    detailViewModel: PlaylistDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val sidebarState by detailViewModel.sidebarState.collectAsState()
    val userPlaylists by detailViewModel.userPlaylists.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Focus Requesters
    val playlistListFocusRequester = remember { FocusRequester() }
    val playlistDetailFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = selectedPlaylist != null) {
        viewModel.selectPlaylist(null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedPlaylist == null) {
            // LIST VIEW
            when (val currentState = state) {
                is PlaylistsState.Loading, PlaylistsState.Initial -> {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("Loading playlists...", color = Color.White)
                     }
                }
                is PlaylistsState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${currentState.message}", color = Color.Red)
                    }
                }
                is PlaylistsState.Content -> {
                    
                    // Auto-focus first item when list is loaded
                    LaunchedEffect(currentState.playlists) {
                        if (currentState.playlists.isNotEmpty()) {
                            playlistListFocusRequester.requestFocus()
                        }
                    }

                    if (currentState.playlists.isEmpty()) {
                         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             Text("No playlists found", color = Color.Gray)
                         }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4), // 4 columns usually fits well on TV
                            contentPadding = PaddingValues(horizontal = 50.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(currentState.playlists.size) { index ->
                                val playlist = currentState.playlists[index]
                                val thumbnailPath = currentState.thumbnails[playlist.id]
                                TvPlaylistCard(
                                    playlist = playlist,
                                    thumbnailUrl = thumbnailPath,
                                    onClick = { 
                                        viewModel.selectPlaylist(playlist)
                                        detailViewModel.loadPlaylistPosts(playlist.id)
                                    },
                                    modifier = if (index == 0) Modifier.focusRequester(playlistListFocusRequester) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // DETAIL VIEW
            val detailState by detailViewModel.state.collectAsState()
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Text(
                    text = selectedPlaylist?.name ?: "Playlist",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 50.dp, top = 20.dp, bottom = 10.dp)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val currentDetailState = detailState) {
                         is PlaylistDetailState.Loading, PlaylistDetailState.Initial -> {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                 Text("Loading videos...", color = Color.White)
                             }
                        }
                        is PlaylistDetailState.Error -> {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                 Text("Error loading videos: ${currentDetailState.message}", color = Color.Red)
                             }
                        }
                        is PlaylistDetailState.Content -> {
                             
                             // Auto-focus first video when content is loaded
                             LaunchedEffect(currentDetailState.posts) {
                                 if (currentDetailState.posts.isNotEmpty()) {
                                     playlistDetailFocusRequester.requestFocus()
                                 }
                             }

                             if (currentDetailState.posts.isEmpty()) {
                                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                     Text("No videos in this playlist", color = Color.Gray)
                                 }
                             } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(horizontal = 50.dp, vertical = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(32.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(currentDetailState.posts.size) { index ->
                                         val post = currentDetailState.posts[index]
                                         TvVideoCard(
                                             post = post,
                                             onClick = { 
                                                 if (!post.videoAttachments.isNullOrEmpty()) {
                                                     onPlayVideo(post.id)
                                                 }
                                             },
                                             onLongClick = {
                                                  detailViewModel.openSidebar(post)
                                             },
                                             modifier = if (index == 0) Modifier.focusRequester(playlistDetailFocusRequester) else Modifier
                                         )
                                    }
                                }
                             }
                        }
                    }
                }
            }
        }

        // Sidebar Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = sidebarState != null,
            enter = androidx.compose.animation.slideInHorizontally { it },
            exit = androidx.compose.animation.slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val currentSidebarState = sidebarState
            if (currentSidebarState != null) {
                // Determine Watch Later status
                val watchLaterPlaylist = userPlaylists.find { it.isWatchLater }
                val isInWatchLater = watchLaterPlaylist?.videoIds?.contains(currentSidebarState.post.id) == true

                com.coulterpeterson.floatnative.ui.components.tv.TvActionSidebar(
                    state = com.coulterpeterson.floatnative.ui.components.tv.SidebarUiState(
                        title = currentSidebarState.post.title,
                        thumbnail = currentSidebarState.post.thumbnail,
                        postId = currentSidebarState.post.id,
                        interaction = currentSidebarState.interaction,
                        currentView = currentSidebarState.currentView
                    ),
                    actions = com.coulterpeterson.floatnative.ui.components.tv.SidebarActions(
                        onPlay = { 
                            if (!currentSidebarState.post.videoAttachments.isNullOrEmpty()) {
                                onPlayVideo(currentSidebarState.post.id)
                            }
                        },
                        onLike = { detailViewModel.toggleSidebarLike() },
                        onDislike = { detailViewModel.toggleSidebarDislike() },
                        onWatchLater = { 
                             detailViewModel.toggleWatchLater(currentSidebarState.post) { wasAdded ->
                                 val msg = if (wasAdded) "Added to Watch Later" else "Removed from Watch Later"
                                 android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                             }
                        },
                        onMarkWatched = { 
                            detailViewModel.markAsWatched(currentSidebarState.post)
                            android.widget.Toast.makeText(context, "Marked as watched", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onAddToPlaylist = { 
                             detailViewModel.toggleSidebarView(com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists)
                        },
                        onShowPlaylists = {
                            detailViewModel.toggleSidebarView(com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists)
                        },
                        onBack = {
                            detailViewModel.toggleSidebarView(com.coulterpeterson.floatnative.viewmodels.SidebarView.Main)
                        },
                        onTogglePlaylist = { playlist ->
                            detailViewModel.togglePlaylistMembership(playlist, currentSidebarState.post)
                        },
                        isInWatchLater = isInWatchLater,
                        userPlaylists = userPlaylists
                    ),
                    onDismiss = { detailViewModel.closeSidebar() }
                )
            }
        }
    }
}
