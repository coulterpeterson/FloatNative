package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
// import androidx.compose.ui.graphics.Color  <-- Removed to avoid ambiguity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
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

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.coulterpeterson.floatnative.ui.components.PlaylistSelectionSheet
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    viewModel: HomeFeedViewModel = viewModel(),
    creatorId: String? = null,
    channelId: String? = null,
    onPlayVideo: (String) -> Unit = {},
    onPlayLive: (String) -> Unit = {},
    // Added navController to handle "Clear" action (popBackStack)
    onClearFilter: () -> Unit = {}
) {
    androidx.compose.runtime.LaunchedEffect(creatorId, channelId) {
        if (channelId != null && creatorId != null) {
            viewModel.setFilter(HomeFeedViewModel.FeedFilter.Channel(channelId, creatorId, displayTitle = "Loading..."))
        } else if (creatorId != null) {
            viewModel.setFilter(HomeFeedViewModel.FeedFilter.Creator(creatorId, displayTitle = "Loading..."))
        }
    }

    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val liveCreators by viewModel.liveCreators.collectAsState()
    // State for playlists and sheet
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val watchProgress by viewModel.watchProgress.collectAsState()
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var selectedPostForMenu by remember { mutableStateOf<com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadPlaylists()
    }
    
    // Determine filter display info
    // ... (rest of logic)
    
    var filterTitle = "Filtered Feed"
    // ... (logic continues 59-69)
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
        
        // Filter Bar (74-109)
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
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        // Live Banner moved inside Grid
                        if (liveCreators.isNotEmpty() && filter is HomeFeedViewModel.FeedFilter.All) {
                            item(span = { GridItemSpan(columns) }) {
                                val liveCreator = liveCreators.first()
                                androidx.compose.material3.Card(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .padding(4.dp) // Match VideoCard padding
                                         .clickable { 
                                             liveCreator.liveStream?.id?.let { onPlayLive(it) } 
                                         },
                                     shape = MaterialTheme.shapes.extraLarge,
                                     colors = androidx.compose.material3.CardDefaults.cardColors(
                                         containerColor = MaterialTheme.colorScheme.surfaceContainer
                                     )
                                 ) {
                                     androidx.compose.foundation.layout.Column {
                                         // Thumbnail Area
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .aspectRatio(16f / 9f)
                                         ) {
                                             // Thumbnail Image
                                             androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                                                 val imageModel = liveCreator.liveStream?.thumbnail ?: liveCreator.icon
                                                 AsyncImage(
                                                     model = imageModel.path.toString(),
                                                     contentDescription = "Live Stream Thumbnail",
                                                     modifier = Modifier.fillMaxSize(),
                                                     contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                 )
                                             }
                                             
                                             // Red Dot Overlay (Top Left)
                                             Box(
                                                 modifier = Modifier
                                                     .padding(8.dp)
                                                     .align(Alignment.TopStart)
                                                     .background(androidx.compose.ui.graphics.Color.Red, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                     .padding(horizontal = 6.dp, vertical = 2.dp)
                                             ) {
                                                 androidx.compose.foundation.layout.Row(
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Box(
                                                         modifier = Modifier
                                                             .size(8.dp)
                                                             .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.CircleShape)
                                                     )
                                                     androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                                                     Text(
                                                         text = "LIVE",
                                                         color = androidx.compose.ui.graphics.Color.White,
                                                         style = MaterialTheme.typography.labelSmall,
                                                         fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                     )
                                                 }
                                             }
                                         }
 
                                         // Metadata Area
                                         androidx.compose.foundation.layout.Row(
                                             modifier = Modifier.padding(12.dp),
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             // Creator Icon
                                             AsyncImage(
                                                 model = liveCreator.icon.path.toString(),
                                                 contentDescription = liveCreator.title,
                                                 modifier = Modifier
                                                     .size(40.dp)
                                                     .clip(androidx.compose.foundation.shape.CircleShape),
                                                 contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                             )
 
                                             androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
 
                                             androidx.compose.foundation.layout.Column {
                                                 Text(
                                                     text = liveCreator.liveStream?.title ?: "Live Stream",
                                                     style = MaterialTheme.typography.titleMedium,
                                                     maxLines = 2,
                                                     minLines = 1,
                                                     overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                 )
                                                 androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                                                 Text(
                                                     text = liveCreator.title,
                                                     style = MaterialTheme.typography.bodyMedium,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                                 )
                                             }
                                         }
                                     }
                                 }
                            }
                        }

                        items(currentState.posts) { post ->
                             val watchLaterPlaylist = userPlaylists.find { it.isWatchLater }
                             val isInWatchLater = watchLaterPlaylist?.videoIds?.contains(post.id) == true

                             VideoCard(
                                post = post,
                                progress = watchProgress[post.id] ?: 0f,
                                onClick = {
                                    onPlayVideo(post.id)
                                },
                                menuItems = { onDismiss ->
                                    DropdownMenuItem(
                                        text = { Text("Mark as Watched") },
                                        onClick = {
                                            viewModel.markAsWatched(post)
                                            onDismiss()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isInWatchLater) "Remove from Watch Later" else "Save to Watch Later") },
                                        onClick = {
                                            viewModel.toggleWatchLater(post) { wasAdded ->
                                                val msg = if (wasAdded) "Added to Watch Later" else "Removed from Watch Later"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
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
                                }
                            )
                        }
                        
                        item(span = { GridItemSpan(columns) }) {
                            // Load More Trigger
                            val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // .padding(16.dp) // Padding handled by grid contentPadding
                                    .padding(vertical = 16.dp), // Still need some vertical padding potentially
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    if (currentState.posts.isNotEmpty()) {
                                        androidx.compose.runtime.LaunchedEffect(Unit) {
                                            viewModel.loadMore()
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
                                onRemoveFromPlaylist = { id -> viewModel.removeFromPlaylist(id, selectedPostForMenu!!.id) },
                                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                onDismiss = { showPlaylistSheet = false }
                            )
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
