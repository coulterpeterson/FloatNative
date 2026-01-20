package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel
import com.coulterpeterson.floatnative.viewmodels.HomeFeedState
import com.coulterpeterson.floatnative.ui.components.VideoCard
import com.coulterpeterson.floatnative.openapi.models.VideoAttachmentModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(
    onPlayVideo: (String) -> Unit,
    onPlayLive: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeFeedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    // TV Layout: Simple Grid of videos
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // 3 columns for TV
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Header requires span
        item(span = { GridItemSpan(3) }) { 
             Text(
                 text = "Home",
                 style = MaterialTheme.typography.displayMedium,
                 modifier = Modifier.padding(bottom = 16.dp)
             )
        }
        
        when (val currentState = state) {
            is HomeFeedState.Content -> {
                items(currentState.posts) { post ->
                    VideoCard(
                        post = post,
                        onClick = { 
                            // Extract video ID safely
                            val attachments = post.videoAttachments
                            if (attachments != null && attachments.isNotEmpty()) {
                                // Explicitly access first element
                                val attachment = attachments.first() as VideoAttachmentModel
                                onPlayVideo(attachment.id) 
                            }
                        }
                    )
                }
            }
            is HomeFeedState.Loading -> {
                item(span = { GridItemSpan(3) }) {
                     Text("Loading...")
                }
            }
            is HomeFeedState.Error -> {
                item(span = { GridItemSpan(3) }) {
                     Text("Error: ${currentState.message}")
                }
            }
            else -> {
                // Initial state
            }
        }
    }
}
