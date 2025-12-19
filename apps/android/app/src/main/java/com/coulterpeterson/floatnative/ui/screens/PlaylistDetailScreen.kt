package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.ui.components.PlaylistVideoCard
import com.coulterpeterson.floatnative.viewmodels.PlaylistDetailState
import com.coulterpeterson.floatnative.viewmodels.PlaylistDetailViewModel
import com.coulterpeterson.floatnative.ui.components.PlaylistSelectionSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    onBackClick: () -> Unit,
    onPlayVideo: (String) -> Unit,
    viewModel: PlaylistDetailViewModel = viewModel()
) {
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylistPosts(playlistId)
    }

    val state by viewModel.state.collectAsState()
    val watchProgress by viewModel.watchProgress.collectAsState()
    
    // State to manage which video's menu is open (No longer needed with internal menu)
    // var openMenuVideoId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    var showPlaylistSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedPostForMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.loadPlaylists()
    }

    // Scaffold removed to reuse MainScreen TopBar
    /*
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
    */
        Box(
            modifier = Modifier
                .fillMaxSize()
                // .padding(paddingValues) // Padding processed by MainScreen scaffold content
        ) {
            when (val currentState = state) {
                is PlaylistDetailState.Loading, PlaylistDetailState.Initial -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PlaylistDetailState.Error -> {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PlaylistDetailState.Content -> {
                    if (currentState.posts.isEmpty()) {
                         Text(
                            text = "No videos in playlist",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(currentState.posts) { post ->
                                Box {
                                    PlaylistVideoCard(
                                        post = post,
                                        progress = watchProgress[post.id] ?: 0f,
                                        onClick = { onPlayVideo(post.id) },
                                         menuItems = { onDismiss -> 
                                             DropdownMenuItem(
                                                text = { Text("Mark as Watched") },
                                                onClick = {
                                                    viewModel.markAsWatched(post)
                                                    onDismiss()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Save to Playlist") },
                                                onClick = {
                                                    selectedPostForMenu = post
                                                    showPlaylistSheet = true
                                                    onDismiss()
                                                }
                                            )
                                             DropdownMenuItem(
                                                text = { Text("Remove from Playlist") },
                                                onClick = {
                                                    viewModel.removeVideoFromPlaylist(playlistId, post.id)
                                                    onDismiss()
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, contentDescription = null)
                                                }
                                            )
                                         }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showPlaylistSheet && selectedPostForMenu != null) {
                 ModalBottomSheet(
                    onDismissRequest = { showPlaylistSheet = false },
                     sheetState = sheetState
                 ) {
                     PlaylistSelectionSheet(
                         playlists = userPlaylists,
                         videoId = selectedPostForMenu!!.id,
                         onToggleWatchLater = { 
                              selectedPostForMenu?.let { p ->
                                  viewModel.toggleWatchLater(p) { wasAdded ->
                                      val msg = if (wasAdded) "Added to Watch Later" else "Removed from Watch Later"
                                      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() 
                                  }
                              }
                         },
                         onAddToPlaylist = { id -> viewModel.addToPlaylist(id, selectedPostForMenu!!.id) },
                         onRemoveFromPlaylist = { id -> viewModel.removeFromPlaylistApi(id, selectedPostForMenu!!.id) },
                         onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                         onDismiss = { showPlaylistSheet = false }
                     )
                 }
            }
        }
    // }
}
