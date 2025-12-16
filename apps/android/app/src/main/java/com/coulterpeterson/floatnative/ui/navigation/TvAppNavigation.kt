package com.coulterpeterson.floatnative.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.coulterpeterson.floatnative.ui.screens.auth.LoginScreen
import com.coulterpeterson.floatnative.ui.screens.TvMainScreen
import com.coulterpeterson.floatnative.ui.screens.VideoPlayerScreen

@androidx.annotation.OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TvAppNavigation(startDestination: String = Screen.Login.route) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        
        composable(Screen.Login.route) {
            // Reusing standard LoginScreen. 
            // In a real TV app, you'd likely use a "Code Login" flow (displayed on TV, user enters on phone)
            // But for this port, we'll try to use the on-screen form.
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
             TvMainScreen(
                onPlayVideo = { videoId ->
                    navController.navigate("video/$videoId")
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
             )
        }

        composable(
            route = "video/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            // Reusing the same VideoPlayerScreen as it uses generic AndroidView + ExoPlayer
            // which works well on TV (ExoPlayer PlayerView has built-in D-pad support).
            VideoPlayerScreen(
                postId = postId,
                onClose = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
             // Placeholder
             androidx.compose.material3.Text("Settings Placeholder")
        }
    }
}
