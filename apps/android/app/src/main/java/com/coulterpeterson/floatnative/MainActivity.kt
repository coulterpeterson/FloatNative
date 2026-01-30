package com.coulterpeterson.floatnative

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect

val LocalPipMode = compositionLocalOf { false }

class MainActivity : AppCompatActivity() {
    private var isInPipMode by mutableStateOf(false)
    // Default aspect ratio 16:9
    var pipParams: android.app.PictureInPictureParams.Builder? = null
    var isVideoPlaying: Boolean = false
    
    fun updatePipParams(aspectRatio: android.util.Rational?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ratio = aspectRatio ?: android.util.Rational(16, 9)
            // Clamp ratio to valid Android limits if necessary (Android handles 2.39:1 to 1:2.39 mostly)
            // Standard limit is often inclusive.
            
            val builder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
            
            pipParams = builder
            setPictureInPictureParams(builder.build())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Removed static enableEdgeToEdge() here to call it dynamically below
        
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Observe theme changes from TokenManager flow
            val themeMode by com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.themeFlow.collectAsState(initial = "dark")
            
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            LaunchedEffect(isDarkTheme) {
                 enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme },
                    navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme }
                )
            }

            FloatNativeTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalPipMode provides isInPipMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Phone navigation
                        val startDestination = androidx.compose.runtime.remember(Unit) {
                            val initialToken = com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.accessToken
                            if (!initialToken.isNullOrEmpty()) {
                                "home"
                            } else {
                                "login"
                            }
                        }
                        AppNavigation(startDestination = startDestination)
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (isVideoPlaying) {
                // Enter PiP mode if we are playing video
                val params = pipParams?.build() ?: android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
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