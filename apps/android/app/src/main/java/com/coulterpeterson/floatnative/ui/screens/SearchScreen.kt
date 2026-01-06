package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.viewmodels.SearchState
import com.coulterpeterson.floatnative.viewmodels.SearchViewModel
import com.coulterpeterson.floatnative.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    
    // SearchBar state
    var active by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = { viewModel.onQueryChange(it) },
            onSearch = { 
                viewModel.performSearch()
                active = false // Close keyboard/suggestions view
            },
            active = false, // Keep it inline for now inside the screen
            onActiveChange = { active = it },
            placeholder = { Text("Search videos") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Suggestions could go here
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val currentState = state) {
                is SearchState.Idle -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Search across all your subscribed creators", color = Color.Gray)
                    }
                }
                is SearchState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SearchState.Error -> {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is SearchState.Empty -> {
                    Text(
                        text = "No results found for \"${currentState.query}\"",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is SearchState.Content -> {
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(currentState.results) { blogPost ->
                            VideoCard(
                                post = blogPost,
                                onClick = { onVideoClick(blogPost.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
