package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.coulterpeterson.floatnative.ui.navigation.Screen
import com.coulterpeterson.floatnative.ui.navigation.mainTabs
import com.coulterpeterson.floatnative.ui.screens.HistoryScreen
import com.coulterpeterson.floatnative.ui.screens.PlaylistsScreen
import com.coulterpeterson.floatnative.ui.screens.SearchScreen
import com.coulterpeterson.floatnative.ui.screens.VideoPlayerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onPlayVideo: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isVideo = currentRoute == "video/{postId}"
    
    val isLive = currentRoute?.startsWith("live/") == true
    val isPlaylist = currentRoute?.startsWith("playlist/") == true

    Scaffold(
        topBar = {
            // Hide TopBar in fullscreen video (landscape)
            if ((isVideo || isLive) && isLandscape) {
                // No TopBar
            } else if (currentRoute == Screen.History.route) {
                TopAppBar(
                    title = { Text("Watch History") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else if (isLive) {
                TopAppBar(
                    title = { Text("Live") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else if (isPlaylist) {
                val playlistName = navBackStackEntry?.arguments?.getString("name") ?: "Playlist"
                TopAppBar(
                    title = { Text(playlistName) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { if (!isVideo) Text("FloatNative") },
                    navigationIcon = {
                        if (isVideo) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        var showMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Watch History") },
                                    onClick = {
                                        showMenu = false
                                        navController.navigate(Screen.History.route)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToSettings()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!((isVideo || isLive) && isLandscape)) {
                NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                mainTabs.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                        label = { Text(screen.title!!) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                    }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { 
                VideoFeedScreen(
                    onPlayVideo = { postId ->
                        navController.navigate("video/$postId")
                    },
                    onPlayLive = { liveStreamId ->  // Pass the ID
                         navController.navigate("live/$liveStreamId")
                    }
                ) 
            }
            
            composable(
                route = "video/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.StringType })
            ) { backStackEntry ->
                val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
                VideoPlayerScreen(
                    postId = postId,
                    onClose = { navController.popBackStack() }
                )
                VideoPlayerScreen(
                    postId = postId,
                    onClose = { navController.popBackStack() }
                )
            }

            composable(
                route = "live/{creatorId}",
                arguments = listOf(navArgument("creatorId") { type = NavType.StringType })
            ) { backStackEntry ->
                val creatorId = backStackEntry.arguments?.getString("creatorId") ?: return@composable
                // We actually passed the creator LIVE STREAM ID as parameter in VideoFeedScreen,
                // but let's call it "liveStreamId" or just reuse "creatorId" param name for simplicity in route
                // logic in VideoFeedScreen was: onPlayVideo("live/${liveCreator.liveStream?.id}")
                
                 com.coulterpeterson.floatnative.ui.screens.LivePlayerScreen(
                    liveStreamId = creatorId,
                    onClose = { navController.popBackStack() }
                )
            }

            composable(
                route = "creator/{creatorId}",
                arguments = listOf(navArgument("creatorId") { type = NavType.StringType })
            ) { backStackEntry ->
                val creatorId = backStackEntry.arguments?.getString("creatorId")
                VideoFeedScreen(
                    creatorId = creatorId,
                    onPlayVideo = { postId ->
                        navController.navigate("video/$postId")
                    },
                    onPlayLive = { liveStreamId ->
                         navController.navigate("live/$liveStreamId")
                    },
                    onClearFilter = {
                         navController.popBackStack()
                    }
                )
            }

            composable(
                route = "channel/{channelId}?creator={creatorId}",
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("creatorId") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId")
                val creatorId = backStackEntry.arguments?.getString("creatorId")
                VideoFeedScreen(
                    channelId = channelId,
                    creatorId = creatorId,
                    onPlayVideo = { postId ->
                        navController.navigate("video/$postId")
                    },
                    onPlayLive = { liveStreamId ->
                         navController.navigate("live/$liveStreamId")
                    },
                    onClearFilter = {
                         navController.popBackStack()
                    }
                )
            }

            composable(Screen.Creators.route) { 
                CreatorsScreen(
                    onCreatorClick = { creatorId ->
                        navController.navigate("creator/$creatorId")
                    },
                    onChannelClick = { channelId, creatorId ->
                        navController.navigate("channel/$channelId?creator=$creatorId")
                    }
                ) 
            }
            composable(Screen.Playlists.route) { 
                PlaylistsScreen(
                    onPlaylistClick = { playlistId, playlistName ->
                         navController.navigate("playlist/$playlistId?name=$playlistName")
                    }
                ) 
            }
            
            composable(
                route = "playlist/{playlistId}?name={name}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "Playlist" }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                val playlistName = backStackEntry.arguments?.getString("name") ?: "Playlist"
                
                com.coulterpeterson.floatnative.ui.screens.PlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    onBackClick = { navController.popBackStack() },
                    onPlayVideo = { videoId ->
                        navController.navigate("video/$videoId")
                    }
                )
            }

            composable(Screen.History.route) { 
                HistoryScreen(
                    onVideoClick = { postId ->
                        onPlayVideo(postId)
                    }
                ) 
            }
            composable(Screen.Search.route) { 
                SearchScreen(
                    onVideoClick = { postId ->
                        onPlayVideo(postId)
                    }
                ) 
            }
        }
    }
}
