package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.openapi.models.VideoAttachmentModel
import com.coulterpeterson.floatnative.ui.components.tv.TvVideoCard
import com.coulterpeterson.floatnative.viewmodels.SearchState
import com.coulterpeterson.floatnative.viewmodels.SearchViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    onPlayVideo: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        // Search Input
        // Using standard Material 3 TextField as TV doesn't have a specific one yet
        androidx.compose.material3.TextField(
            value = query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = { Text("Search videos...") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.performSearch() }),
            singleLine = true
        )
        
        // Results
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (val s = state) {
                is SearchState.Idle -> {
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = "Enter a search term to begin",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SearchState.Loading -> {
                     item(span = { GridItemSpan(3) }) {
                        Text("Searching...")
                    }
                }
                is SearchState.Empty -> {
                     item(span = { GridItemSpan(3) }) {
                        Text("No results found for '${s.query}'")
                    }
                }
                is SearchState.Error -> {
                     item(span = { GridItemSpan(3) }) {
                        Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is SearchState.Content -> {
                    items(s.results) { post ->
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
            }
        }
    }
}
