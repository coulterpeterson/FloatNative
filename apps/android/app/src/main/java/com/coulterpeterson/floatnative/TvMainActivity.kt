package com.coulterpeterson.floatnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.ui.theme.Pink80
import com.coulterpeterson.floatnative.ui.theme.Purple80
import com.coulterpeterson.floatnative.ui.theme.PurpleGrey80
import kotlinx.coroutines.launch

class TvMainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register Cast Receiver lifecycle observer
        lifecycle.addObserver(com.coulterpeterson.floatnative.cast.CastReceiverLifecycleObserver())
        
        setContent {
            val darkColorScheme = darkColorScheme(
                primary = Purple80,
                secondary = PurpleGrey80,
                tertiary = Pink80
            )

            MaterialTheme(colorScheme = darkColorScheme) {
                Surface(
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Guard: If not on TV, redirect to MainActivity
                    val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                    if (!isTv) {
                        val intent = android.content.Intent(this@TvMainActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                        return@Surface
                    }

                    val token = FloatplaneApi.tokenManager.accessToken
                    var isLoggedIn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!token.isNullOrEmpty()) }

                    if (!isLoggedIn) {
                        com.coulterpeterson.floatnative.ui.screens.tv.TvLoginScreen(
                            onLoginSuccess = {
                                isLoggedIn = true
                            }
                        )
                    } else {
                        val navController = androidx.navigation.compose.rememberNavController()
                        
                        // Setup Cast Receiver
                        androidx.compose.runtime.LaunchedEffect(Unit) {
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
                                                
                                                val startPosition = loadRequestData.currentTime
                                                
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    android.util.Log.d("CastReceiver", "Received Load Request: $contentId, isLive=$isLive, startPos=$startPosition")
                                                    if (isLive) {
                                                        navController.navigate("live_player/$contentId")
                                                    } else {
                                                        // Pass startTimestamp to the route
                                                        navController.navigate(com.coulterpeterson.floatnative.ui.navigation.TvScreen.Player.createRoute(contentId, startPosition))
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
                            startDestination = com.coulterpeterson.floatnative.ui.navigation.TvScreen.Home.route,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}
