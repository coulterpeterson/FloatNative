package com.coulterpeterson.floatnative.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coulterpeterson.floatnative.ui.screens.MainScreen
import com.coulterpeterson.floatnative.ui.screens.auth.LoginScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            MainScreen(
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
            arguments = listOf(androidx.navigation.navArgument("postId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            com.coulterpeterson.floatnative.ui.screens.VideoPlayerScreen(
                postId = postId,
                onClose = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            // Placeholder for Settings
        }
    }
}
