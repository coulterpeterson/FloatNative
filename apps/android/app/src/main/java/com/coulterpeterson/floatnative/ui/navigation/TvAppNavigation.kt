package com.coulterpeterson.floatnative.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coulterpeterson.floatnative.ui.screens.tv.TvHomeFeedScreen
import com.coulterpeterson.floatnative.ui.screens.tv.TvLoginScreen
import com.coulterpeterson.floatnative.ui.screens.tv.TvVideoPlayerScreen

sealed class TvScreen(val route: String) {
    object Login : TvScreen("login")
    object Home : TvScreen("home")
    object Search : TvScreen("search")
    object LivePlayer : TvScreen("live_player/{liveStreamId}") {
        fun createRoute(liveStreamId: String) = "live_player/$liveStreamId"
    }
    object Player : TvScreen("player/{videoId}?startTimestamp={startTimestamp}") {
        fun createRoute(videoId: String, startTimestamp: Long = 0L) = "player/$videoId?startTimestamp=$startTimestamp"
    }
}

@Composable
fun TvAppNavigation(
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(TvScreen.Login.route) {
            TvLoginScreen(
                onLoginSuccess = {
                    navController.navigate(TvScreen.Home.route) {
                        popUpTo(TvScreen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(TvScreen.Home.route) {
            TvHomeFeedScreen(
                onPlayVideo = { videoId ->
                    navController.navigate(TvScreen.Player.createRoute(videoId))
                },
                onPlayLive = { liveStreamId ->
                    navController.navigate(TvScreen.LivePlayer.createRoute(liveStreamId))
                },
                onSearchClick = {
                    navController.navigate(TvScreen.Search.route)
                }
            )
        }
        
        composable(TvScreen.Search.route) {
            com.coulterpeterson.floatnative.ui.screens.tv.TvSearchScreen(
                onPlayVideo = { videoId ->
                    navController.navigate(TvScreen.Player.createRoute(videoId))
                }
            )
        }
        
        composable(
            route = TvScreen.Player.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("startTimestamp") { 
                    type = NavType.LongType 
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val startTimestamp = backStackEntry.arguments?.getLong("startTimestamp") ?: 0L
            TvVideoPlayerScreen(
                videoId = videoId,
                startTimestamp = startTimestamp,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = TvScreen.LivePlayer.route,
            arguments = listOf(navArgument("liveStreamId") { type = NavType.StringType })
        ) { backStackEntry ->
            val liveStreamId = backStackEntry.arguments?.getString("liveStreamId") ?: return@composable
            com.coulterpeterson.floatnative.ui.screens.tv.TvLivePlayerScreen(
                liveStreamId = liveStreamId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
