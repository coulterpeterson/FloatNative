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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeFeedScreen(
    onPlayVideo: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val sidebarState by viewModel.sidebarState.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val context = LocalContext.current
    
    // Top Navigation Items
    val navItems = listOf("Home", "Creators", "Playlists", "Search", "History", "Settings")
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) { // Dark background
        Column(modifier = Modifier.fillMaxSize()) {
        
        // 1. Top Navigation Bar (Pill shapes)
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
                    if (title == "Search") {
                        onSearchClick()
                    } else {
                        selectedNavIndex = index
                    }
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

        // 2. Content Grid
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
                    items(s.posts) { post ->
                       TvVideoCard(
                           post = post,
                           onClick = {
                                // VideoPlayerViewModel expects a BlogPost ID, NOT a VideoAttachment ID.
                                // It will fetch the post and extract the video attached to it.
                                if (viewModel.sidebarState.value == null && !post.videoAttachments.isNullOrEmpty()) {
                                    onPlayVideo(post.id)
                                }
                           },
                           onLongClick = {
                               viewModel.openSidebar(post)
                           }
                       ) 
                    }
                }
                else -> {}
            }
        }
        } // End Column

        // Sidebar Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = sidebarState != null,
            enter = androidx.compose.animation.slideInHorizontally { it },
            exit = androidx.compose.animation.slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd) 
        ) {
            val currentSidebarState = sidebarState
            if (currentSidebarState != null) {
                // Determine Watch Later status
                val watchLaterPlaylist = userPlaylists.find { it.isWatchLater }
                val isInWatchLater = watchLaterPlaylist?.videoIds?.contains(currentSidebarState.post.id) == true

                TvActionSidebar(
                    state = currentSidebarState,
                    actions = SidebarActions(
                        onPlay = { 
                            if (!currentSidebarState.post.videoAttachments.isNullOrEmpty()) {
                                onPlayVideo(currentSidebarState.post.id)
                            }
                        },
                        onLike = { viewModel.toggleSidebarLike() },
                        onDislike = { viewModel.toggleSidebarDislike() },
                        onWatchLater = { 
                             viewModel.toggleWatchLater(currentSidebarState.post) { wasAdded ->
                                 val msg = if (wasAdded) "Added to Watch Later" else "Removed from Watch Later"
                                 Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                             }
                        },
                        onMarkWatched = { viewModel.markAsWatched(currentSidebarState.post) },
                        onAddToPlaylist = { 
                            Toast.makeText(context, "Playlist selection coming soon to TV", Toast.LENGTH_SHORT).show()
                        },
                        isInWatchLater = isInWatchLater
                    ),
                    onDismiss = { viewModel.closeSidebar() }
                )
            }
        }
    }
}
