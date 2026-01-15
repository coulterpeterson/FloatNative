package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun VideoFeedScreen() {
    PlaceholderScreen("Home Feed")
}

@Composable
fun CreatorsScreen() {
    PlaceholderScreen("Creators")
}

@Composable
fun PlaylistsScreen() {
    PlaceholderScreen("Playlists")
}

@Composable
fun SearchScreen() {
    PlaceholderScreen("Search")
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}
