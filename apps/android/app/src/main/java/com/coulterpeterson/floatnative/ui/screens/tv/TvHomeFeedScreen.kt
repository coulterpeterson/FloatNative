package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel
import com.coulterpeterson.floatnative.viewmodels.HomeFeedState
import com.coulterpeterson.floatnative.ui.components.tv.TvVideoCard
import com.coulterpeterson.floatnative.openapi.models.VideoAttachmentModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeFeedScreen(
    onPlayVideo: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: HomeFeedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(3) }) {
             androidx.compose.foundation.layout.Row(
                 horizontalArrangement = Arrangement.SpaceBetween,
                 verticalAlignment = Alignment.CenterVertically,
                 modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
             ) {
                 Text(
                     text = "Home",
                     style = MaterialTheme.typography.headlineLarge
                 )
                 
                 androidx.tv.material3.Button(onClick = onSearchClick) {
                     Text("Search")
                 }
             }
        }
        
        when (val s = state) {
            is HomeFeedState.Loading -> {
                item(span = { GridItemSpan(3) }) {
                    Text("Loading...")
                }
            }
            is HomeFeedState.Error -> {
                item(span = { GridItemSpan(3) }) {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is HomeFeedState.Content -> {
                items(s.posts) { post ->
                   TvVideoCard(
                       post = post,
                       onClick = {
                            val videoId = post.videoAttachments?.firstOrNull()?.let { 
                                if (it is VideoAttachmentModel) it.id else null
                            } ?: return@TvVideoCard
                            
                           onPlayVideo(videoId)
                       }
                   ) 
                }
            }
            else -> {}
        }
    }
}
