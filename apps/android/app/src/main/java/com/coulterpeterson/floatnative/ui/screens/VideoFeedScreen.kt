package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
    creatorId: String? = null,
    channelId: String? = null,
    onPlayVideo: (String) -> Unit = {},
    // Added navController to handle "Clear" action (popBackStack)
    onClearFilter: () -> Unit = {}
) {
    androidx.compose.runtime.LaunchedEffect(creatorId, channelId) {
        if (channelId != null && creatorId != null) {
            viewModel.setFilter(HomeFeedViewModel.FeedFilter.Channel(channelId, creatorId))
        } else if (creatorId != null) {
            viewModel.setFilter(HomeFeedViewModel.FeedFilter.Creator(creatorId))
        }
    }

    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val filter by viewModel.filter.collectAsState()

    // Determine filter display info
    // For now, since we don't have the creator object passed in, we depend on the feed content or just generic text
    // Refinement: Check if state has content and grab creator info from first post?
    // Or fetch in ViewModel.
    // Let's use generic "Filtered Feed" if title unavailable, or try to get metadata from somewhere?
    // Actually, state.posts.firstOrNull()?.channel?.title might work if feed loaded!
    
    var filterTitle = "Filtered Feed"
    if (state is HomeFeedState.Content) {
        val firstPost = (state as HomeFeedState.Content).posts.firstOrNull()
        if (firstPost != null) {
            if (filter is HomeFeedViewModel.FeedFilter.Channel) {
                filterTitle = firstPost.channel?.title ?: filterTitle
            } else if (filter is HomeFeedViewModel.FeedFilter.Creator) {
                filterTitle = firstPost.creator?.title ?: filterTitle
            }
        }
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        
        // Filter Bar
        if (filter !is HomeFeedViewModel.FeedFilter.All) {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.List, // Or similar
                        contentDescription = "Filter",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                    
                    androidx.compose.material3.Text(
                        text = filterTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    androidx.compose.material3.IconButton(onClick = onClearFilter) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Clear Filter"
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            modifier = Modifier.weight(1f),
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() }
        ) {
            when (val currentState = state) {
                is HomeFeedState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
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
}
