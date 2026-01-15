package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.api.Playlist
import com.coulterpeterson.floatnative.viewmodels.PlaylistsState
import com.coulterpeterson.floatnative.viewmodels.PlaylistsViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (String, String) -> Unit = { _, _ -> }, // id, name
    viewModel: PlaylistsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentState = state) {
            is PlaylistsState.Loading, PlaylistsState.Initial -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is PlaylistsState.Error -> {
                Text(
                    text = currentState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is PlaylistsState.Content -> {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = false, // Add refreshing state later if needed
                    onRefresh = { viewModel.refresh() }
                ) {
                    // Responsive Grid Logic
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val screenWidth = configuration.screenWidthDp.dp
                    val columns = when {
                        screenWidth >= 840.dp -> 3 // Landscape Tablet
                        screenWidth >= 600.dp -> 2 // Portrait Tablet
                        else -> 1 // Phone
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        items(currentState.playlists) { playlist ->
                            val thumbnailPath = currentState.thumbnails[playlist.id]
                            PlaylistItemCard(
                                playlist = playlist,
                                thumbnailPath = thumbnailPath,
                                onClick = { onPlaylistClick(playlist.id, playlist.name) },
                                onDelete = { viewModel.deletePlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistItemCard(
    playlist: Playlist, 
    thumbnailPath: String?, 
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                if (thumbnailPath != null) {
                    androidx.compose.ui.platform.LocalContext.current.let { context ->
                        // Simple AsyncImage if available, or coil
                        coil.compose.AsyncImage(
                            model = thumbnailPath,
                            contentDescription = playlist.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playlist.videoIds.isEmpty()) {
                            Text("No Videos", style = MaterialTheme.typography.bodySmall)
                        } else {
                            // Loading or no thumb
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                
                // Menu Button
                 if (!playlist.isWatchLater) { // Watch Later usually can't be deleted in this view
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                         androidx.compose.material3.IconButton(
                             onClick = { showMenu = true }
                         ) {
                             androidx.compose.material3.Icon(
                                 imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                                 contentDescription = "Options",
                                 tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) // Ensure visibility
                             )
                         }
                         
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Delete Playlist") },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    androidx.compose.material3.Icon(
                                        androidx.compose.material.icons.Icons.Default.Delete,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                 }
            }

            // Metadata
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.videoIds.size} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
