package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.ui.components.VideoCard
import com.coulterpeterson.floatnative.viewmodels.HomeFeedState
import com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    viewModel: HomeFeedViewModel = viewModel(),
    onPlayVideo: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() }
    ) {
        when (val currentState = state) {
            is HomeFeedState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...") // Replace with Skeleton loading later
                }
            }
            is HomeFeedState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${currentState.message}")
                }
            }
            is HomeFeedState.Content -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(currentState.posts) { post ->
                        VideoCard(
                            post = post,
                            onClick = {
                                onPlayVideo(post.id)
                            }
                        )
                    }
                    
                    item {
                        // Load More Trigger
                        val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                // Invisible trigger
                                // Only trigger if we have posts (don't trigger on empty state)
                                if (currentState.posts.isNotEmpty()) {
                                    androidx.compose.runtime.LaunchedEffect(Unit) {
                                        viewModel.loadMore()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is HomeFeedState.Initial -> {
                 // Should arguably launch loading in init block of ViewModel
            }
        }
    }
}
