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
    var pipParams: android.app.PictureInPictureParams.Builder? = null
    var isVideoPlaying: Boolean = false
    /**
     * Aspect ratio of the *currently loaded* video, set by VideoPlayerScreen's
     * Player.Listener.onVideoSizeChanged. Null until the player has resolved
     * the video dimensions, and cleared when the player screen disposes or
     * loads a different video — so we never carry a stale ratio from the
     * previous video into a new PiP entry. This is what fixed GH #41: before,
     * `pipParams` was either stale or unset when the user pressed Home before
     * ExoPlayer reported the size, and PiP entered at 16:9 with a broken
     * layout that only a manual resize would un-stick.
     */
    var currentVideoRatio: android.util.Rational? = null

    fun updatePipParams(aspectRatio: android.util.Rational?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ratio = aspectRatio ?: android.util.Rational(16, 9)
            val builder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
            pipParams = builder
            // setPictureInPictureParams is safe both before and during PiP;
            // during PiP it live-updates the window's aspect ratio, which is
            // why the size listener calling this from a running PiP session
            // typically corrects the layout on its own.
            setPictureInPictureParams(builder.build())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sync isInPipMode with the activity's actual state. If Android
        // recreated this activity while already in PiP, the new instance
        // would otherwise start at `false` and render the portrait layout
        // into the mini-player (GH #41).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode) {
            isInPipMode = true
        }

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
            val ratio = currentVideoRatio
            // Skip PiP entry when we haven't yet resolved the current video's
            // aspect ratio (GH #41). Entering with a stale or hardcoded 16:9
            // produced a broken layout that the user could only fix by
            // resizing the PiP window.
            if (isVideoPlaying && ratio != null) {
                val params = (pipParams ?: android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(ratio))
                    .build()
                // Pre-flip isInPipMode BEFORE calling Android's PiP entry —
                // `onPictureInPictureModeChanged(true)` is delivered ~700ms
                // after the activity is already resized into the PiP window
                // (confirmed via diagnostic logs while fixing GH #41). Until
                // that callback fires Compose otherwise thinks we're still
                // in portrait and renders the full portrait layout into the
                // mini-player. onResume rolls this back if PiP never engaged.
                isInPipMode = true
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            if (isInPipMode && !isInPictureInPictureMode) {
                isInPipMode = false
            }
        }
        syncCookiesFromBrowser()
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
             syncCookiesFromBrowser()
             val code = data.getQueryParameter("code")
             if (code != null) {
                 // Emit to global flow
                 lifecycleScope.launch {
                     com.coulterpeterson.floatnative.api.FloatplaneApi.authCodeFlow.emit(code)
                 }
             }
        }
    }

    private fun syncCookiesFromBrowser() {
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val rawCookies = cookieManager.getCookie("https://www.floatplane.com")
                ?: cookieManager.getCookie("https://auth.floatplane.com")
            if (!rawCookies.isNullOrEmpty()) {
                val parts = rawCookies.split(";")
                for (part in parts) {
                    val pair = part.trim().split("=")
                    if (pair.size == 2 && pair[0] == "sails.sid") {
                        val cookieVal = pair[1]
                        if (cookieVal.isNotEmpty()) {
                            com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.authCookie = cookieVal
                            android.util.Log.i("MainActivity", "Automagically synced sails.sid cookie from web session")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to sync cookies from web session", e)
        }
    }
}