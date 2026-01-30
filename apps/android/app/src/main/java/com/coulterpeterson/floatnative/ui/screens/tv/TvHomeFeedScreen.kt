package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.coulterpeterson.floatnative.viewmodels.HomeFeedState
import com.coulterpeterson.floatnative.viewmodels.SidebarView
import com.coulterpeterson.floatnative.ui.components.tv.TvVideoCard
import com.coulterpeterson.floatnative.ui.components.tv.TvActionSidebar
import com.coulterpeterson.floatnative.ui.components.tv.SidebarActions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeFeedScreen(
    onPlayVideo: (String) -> Unit = {},
    onPlayLive: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val liveCreators by viewModel.liveCreators.collectAsState()
    val sidebarState by viewModel.sidebarState.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val watchProgress by viewModel.watchProgress.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val context = LocalContext.current
    
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val lastFocusedId by viewModel.lastFocusedId.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Restore focus when sidebar closes
    androidx.compose.runtime.LaunchedEffect(sidebarState) {
        if (sidebarState == null && lastFocusedId != null) {
            try { focusRequesters[lastFocusedId]?.requestFocus() } catch (e: Exception) {}
        }
    }

    // Restore focus on re-entry (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                 viewModel.loadPlaylists() // Refresh playlists on resume
                 viewModel.checkLiveCreators()
                 if (lastFocusedId != null) {
                     try { focusRequesters[lastFocusedId]?.requestFocus() } catch (e: Exception) {}
                 }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Top Navigation Items
    val navItems = listOf("Home", "Creators", "Playlists", "Search", "History", "Settings")
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) { // Dark background
        Column(modifier = Modifier.fillMaxSize()) {
        
        // 1. Top Navigation Bar (Pill shapes)
        // Show only if no filter is active (or if we want to allow nav while filtered? 
        // Screenshot shows "Home, Creators..." pill bar IS visible in filtered view, 
        // BUT the header "TechLinked" replaces the "Home" text? 
        // No, the screenshot shows "TechLinked" roughly where the Row of pills was?
        // Actually the screenshot shows a Header "TechLinked" at the top left, and NO pill bar.
        // Wait, the screenshot ID 0 (filtered view) shows "TechLinked" header and a "Clear" button.
        // It does NOT show the navigation pills "Home, Creators...".
        // Screenshot ID 1 (creators list) shows the navigation pills.
        // So: If Filter is active -> Show Filter Header. If Filter is inactive -> Show Navigation Pills.
        val activeFilter = filter
        if (activeFilter !is com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel.FeedFilter.All) {
             // Filter Header
             androidx.compose.material3.Surface(
                color = Color(0xFF1E1E1E), // Darker surface
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 32.dp, start = 50.dp, end = 50.dp)
            ) {
                Row(
                   modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    val (iconPath, title) = when (activeFilter) {
                        is com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel.FeedFilter.Creator -> activeFilter.icon to activeFilter.displayTitle
                        is com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel.FeedFilter.Channel -> activeFilter.icon to activeFilter.displayTitle
                        else -> null to ""
                    }

                    if (iconPath != null) {
                        coil.compose.AsyncImage(
                            model = iconPath,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    androidx.tv.material3.Button(
                        onClick = { viewModel.setFilter(com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel.FeedFilter.All) },
                        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.1f)
                    ) {
                        Text("Clear")
                    }
                }
            }
        } else {
             // Navigation Pills
             Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
            navItems.forEachIndexed { index, title ->
                val isSelected = index == selectedNavIndex
                
                val onClick = {
                    selectedNavIndex = index
                }

                Surface(
                    onClick = onClick,
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                    ),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        // Selected: White background, Black text (High Contrast)
                        // Unselected: Transparent background, White text
                        containerColor = if (isSelected) Color(0xFFE0E0E0) else Color.Transparent,
                        contentColor = if (isSelected) Color.Black else Color.White,
                        
                        // Focused: White background (brighter), Black text
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.tv.material3.Icon(
                            imageVector = when(title) {
                                "Home" -> androidx.compose.material.icons.Icons.Default.Home
                                "Creators" -> androidx.compose.material.icons.Icons.Default.Person
                                "Playlists" -> androidx.compose.material.icons.Icons.Default.List
                                "Search" -> androidx.compose.material.icons.Icons.Default.Search
                                "History" -> androidx.compose.material.icons.Icons.Default.DateRange
                                "Settings" -> androidx.compose.material.icons.Icons.Default.Settings
                                else -> androidx.compose.material.icons.Icons.Default.Home
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        }

        // 2. Content
        if (selectedNavIndex == 1) {
             // Creators Screen
             com.coulterpeterson.floatnative.ui.screens.tv.TvCreatorsScreen(
                 onFilterSelected = { filter ->
                     viewModel.setFilter(filter)
                     selectedNavIndex = 0 // Switch to Home tab
                 }
             )
        } else if (selectedNavIndex == 0) {
            // Home Feed
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), 
                contentPadding = PaddingValues(horizontal = 50.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (val s = state) {
                    is HomeFeedState.Loading -> {
                         item(span = { GridItemSpan(4) }) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text("Loading content...", color = Color.White)
                            }
                        }
                    }
                    is HomeFeedState.Error -> {
                        item(span = { GridItemSpan(4) }) {
                             Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                             }
                        }
                    }

                    is HomeFeedState.Content -> {
                        // Live Card at the start
                        if (filter is com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel.FeedFilter.All) {
                            val liveList = liveCreators
                            if (liveList.isNotEmpty()) {
                                item {
                                    val creator = liveList.first()
                                    com.coulterpeterson.floatnative.ui.components.tv.TvLiveVideoCard(
                                        creator = creator,
                                        onClick = {
                                            creator.liveStream?.id?.let { onPlayLive(it) }
                                        }
                                    )
                                }
                            }
                        }

                        items(s.posts.size) { index ->
                           val post = s.posts[index]
                           val requester = focusRequesters.getOrPut(post.id) { FocusRequester() }

                           val isBookmarked = userPlaylists.any { it.videoIds.contains(post.id) }

                           TvVideoCard(
                               post = post,
                               onClick = {
                                    viewModel.setLastFocusedId(post.id)
                                    // VideoPlayerViewModel expects a BlogPost ID, NOT a VideoAttachment ID.
                                    // It will fetch the post and extract the video attached to it.
                                    if (viewModel.sidebarState.value == null) {
                                        onPlayVideo(post.id)
                                    }
                               },
                               onLongClick = {
                                   viewModel.setLastFocusedId(post.id)
                                   viewModel.openSidebar(post)
                               },
                               modifier = Modifier.focusRequester(requester),
                               progress = watchProgress[post.id] ?: 0f,
                               isBookmarked = isBookmarked
                           )

                           // Prefetch logic: Load more when we are 8 items (approx 2 rows) from the end
                           if (index >= s.posts.size - 8) {
                               androidx.compose.runtime.LaunchedEffect(Unit) {
                                   viewModel.loadMore()
                               }
                           }
                        }

                        item(span = { GridItemSpan(4) }) {
                             val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                             if (isLoadingMore) {
                                  Box(
                                      modifier = Modifier
                                          .fillMaxWidth()
                                          .padding(vertical = 24.dp),
                                      contentAlignment = Alignment.Center
                                  ) {
                                      androidx.tv.material3.Text("Loading...", color = Color.Gray)
                                  }
                             }
                        }
                    }
                    else -> {}
                }
            }
        } else if (selectedNavIndex == 2) {
            com.coulterpeterson.floatnative.ui.screens.tv.TvPlaylistsScreen(
                onPlayVideo = { videoId ->
                    onPlayVideo(videoId)
                }
            )
        } else if (selectedNavIndex == 3) {
            // Search
            com.coulterpeterson.floatnative.ui.screens.tv.TvSearchScreen(
                onPlayVideo = onPlayVideo
            )
        } else if (selectedNavIndex == 4) {
            // History
            com.coulterpeterson.floatnative.ui.screens.tv.TvHistoryScreen(
                onPlayVideo = onPlayVideo
            )
        } else if (selectedNavIndex == 5) {
            // Settings
            com.coulterpeterson.floatnative.ui.screens.tv.TvSettingsScreen()
        } else {
             // Other tabs placeholders
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Text("Feature coming soon", color = Color.Gray)
             }
        }
        } // End Column

        // Sidebar Overlay
        // Sidebar Overlay
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = sidebarState != null,
                enter = androidx.compose.animation.slideInHorizontally { it },
                exit = androidx.compose.animation.slideOutHorizontally { it }
            ) {
                val currentSidebarState = sidebarState
                if (currentSidebarState != null) {
                    // Determine Watch Later status
                    val watchLaterPlaylist = userPlaylists.find { it.isWatchLater }
                    val isInWatchLater = watchLaterPlaylist?.videoIds?.contains(currentSidebarState.post.id) == true

                    TvActionSidebar(
                        state = com.coulterpeterson.floatnative.ui.components.tv.SidebarUiState(
                            title = currentSidebarState.post.title,
                            thumbnail = currentSidebarState.post.thumbnail,
                            postId = currentSidebarState.post.id,
                            interaction = currentSidebarState.interaction,
                            currentView = currentSidebarState.currentView
                        ),
                        actions = SidebarActions(
                            onPlay = { 
                                onPlayVideo(currentSidebarState.post.id)
                            },
                            onLike = { viewModel.toggleSidebarLike() },
                            onDislike = { viewModel.toggleSidebarDislike() },
                            onWatchLater = { 
                                 viewModel.toggleWatchLater(currentSidebarState.post) { wasAdded ->
                                     val msg = if (wasAdded) "Added to Watch Later" else "Removed from Watch Later"
                                     Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                 }
                            },
                            onMarkWatched = { 
                                viewModel.markAsWatched(currentSidebarState.post)
                                Toast.makeText(context, "Marked as watched", Toast.LENGTH_SHORT).show()
                            },
                            onAddToPlaylist = { 
                                 viewModel.toggleSidebarView(com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists)
                            },
                            onShowPlaylists = {
                                viewModel.toggleSidebarView(com.coulterpeterson.floatnative.viewmodels.SidebarView.Playlists)
                            },
                            onBack = {
                                viewModel.toggleSidebarView(com.coulterpeterson.floatnative.viewmodels.SidebarView.Main)
                            },
                            onTogglePlaylist = { playlist ->
                                viewModel.togglePlaylistMembership(playlist, currentSidebarState.post)
                            },
                            isInWatchLater = isInWatchLater,
                            userPlaylists = userPlaylists
                        ),
                        onDismiss = { viewModel.closeSidebar() }
                    )
                }
            }
        }
    }
}
