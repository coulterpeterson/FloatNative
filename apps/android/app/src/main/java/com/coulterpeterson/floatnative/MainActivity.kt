package com.coulterpeterson.floatnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.coulterpeterson.floatnative.ui.screens.auth.LoginScreen
import com.coulterpeterson.floatnative.ui.theme.FloatNativeTheme
import com.coulterpeterson.floatnative.ui.navigation.AppNavigation
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Observe theme changes from TokenManager flow
            val themeMode by com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.themeFlow.collectAsState(initial = "dark")
            
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            FloatNativeTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                    
                    val token = com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.accessToken
                    val startDestination = if (!token.isNullOrEmpty()) {
                        com.coulterpeterson.floatnative.ui.navigation.Screen.Home.route
                    } else {
                        com.coulterpeterson.floatnative.ui.navigation.Screen.Login.route
                    }

                    if (isTv) {
                        com.coulterpeterson.floatnative.ui.navigation.TvAppNavigation(startDestination = startDestination)
                    } else {
                         // I will abort this specific replace, modify TokenManager to have a Flow, then come back.
                         AppNavigation(startDestination = startDestination)
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: android.content.Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "floatnative" && data.host == "auth") {
             val code = data.getQueryParameter("code")
             if (code != null) {
                 // Emit to global flow
                 lifecycleScope.launch {
                     com.coulterpeterson.floatnative.api.FloatplaneApi.authCodeFlow.emit(code)
                 }
             }
        }
    }
}