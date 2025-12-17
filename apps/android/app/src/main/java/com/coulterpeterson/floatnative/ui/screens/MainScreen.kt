package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FloatNative") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
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
                    onPlaylistClick = { playlistId ->
                        // TODO: Navigate to Playlist Detail
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
