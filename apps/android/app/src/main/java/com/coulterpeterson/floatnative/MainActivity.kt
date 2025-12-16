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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            FloatNativeTheme {
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