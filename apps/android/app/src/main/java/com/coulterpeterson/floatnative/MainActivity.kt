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
                        val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                        
                        // Observe Auth State for dynamic logout
                        val authToken by com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.authStateFlow.collectAsState()
                        
                        // Initial start destination
                        val startDestination = androidx.compose.runtime.remember(Unit) {
                            val initialToken = com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.accessToken
                            if (!initialToken.isNullOrEmpty()) {
                                "home" // Hardcoded to match TvScreen.Home.route / Screen.Home.route commonality
                            } else {
                                "login"
                            }
                        }

                        if (isTv) {
                            val navController = androidx.navigation.compose.rememberNavController()
                            
                            // Watch for logout
                            LaunchedEffect(authToken) {
                                if (authToken == null) {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                            
                            // Setup Cast Receiver
                            LaunchedEffect(Unit) {
                                try {
                                    val castContext = com.google.android.gms.cast.tv.CastReceiverContext.getInstance()
                                    val mediaManager = castContext.mediaManager
                                    
                                    mediaManager.setMediaLoadCommandCallback(object : com.google.android.gms.cast.tv.media.MediaLoadCommandCallback() {
                                        override fun onLoad(senderId: String?, loadRequestData: com.google.android.gms.cast.MediaLoadRequestData): com.google.android.gms.tasks.Task<com.google.android.gms.cast.MediaLoadRequestData> {
                                            if (loadRequestData != null) {
                                                val mediaInfo = loadRequestData.mediaInfo
                                                if (mediaInfo != null) {
                                                    val contentId = mediaInfo.contentId
                                                    val customData = mediaInfo.customData
                                                    val isLive = customData?.optString("type") == "LIVE" || mediaInfo.streamType == com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE
                                                    
                                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                        android.util.Log.d("CastReceiver", "Received Load Request: $contentId, isLive=$isLive")
                                                        if (isLive) {
                                                            navController.navigate("live_player/$contentId")
                                                        } else {
                                                            navController.navigate("player/$contentId")
                                                        }
                                                    }
                                                }
                                                // Return the request data as success
                                                return com.google.android.gms.tasks.Tasks.forResult(loadRequestData)
                                            }
                                            return com.google.android.gms.tasks.Tasks.forResult(null)
                                        }
                                    })
                                } catch (e: Exception) {
                                    android.util.Log.e("CastReceiver", "Error initializing Cast Context", e)
                                }
                            }
                            
                            com.coulterpeterson.floatnative.ui.navigation.TvAppNavigation(
                                startDestination = startDestination,
                                navController = navController
                            )
                        } else {
                             // Phone navigation
                             AppNavigation(startDestination = startDestination)
                        }
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