package com.coulterpeterson.floatnative.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String? = null, val icon: ImageVector? = null) {
    object Login : Screen("login")
    
    // Main Tabs
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Creators : Screen("creators", "Creators", Icons.Filled.Person)
    object Playlists : Screen("playlists", "Playlists", Icons.Filled.List)
    object Search : Screen("search", "Search", Icons.Filled.Search)
    
    // Secondary Screens
    object History : Screen("history")
    object Settings : Screen("settings")
    object VideoPlayer : Screen("player/{videoId}") {
        fun createRoute(videoId: String) = "player/$videoId"
    }
}

val mainTabs = listOf(
    Screen.Home,
    Screen.Creators,
    Screen.Playlists,
    Screen.Search
)
