package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.api.Playlist
import com.coulterpeterson.floatnative.viewmodels.PlaylistsState
import com.coulterpeterson.floatnative.viewmodels.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (String) -> Unit = {},
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
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentState.playlists) { playlist ->
                        PlaylistGlobalItem(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistGlobalItem(playlist: Playlist, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.name) },
        supportingContent = { Text("${playlist.videoIds.size} videos") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
