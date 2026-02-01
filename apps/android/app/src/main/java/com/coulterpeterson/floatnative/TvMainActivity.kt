package com.coulterpeterson.floatnative

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.google.android.gms.cast.tv.CastReceiverContext
import com.google.android.gms.cast.tv.media.MediaLoadCommandCallback
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

class TvMainActivity : ComponentActivity() {
    
    // Pending cast load data - set when a LOAD request comes in before navigation is ready
    private var pendingCastLoad: CastLoadData? = null
    
    data class CastLoadData(
        val contentId: String,
        val isLive: Boolean,
        val startPosition: Long
    )
    
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register Cast Receiver lifecycle observer with callback to set up media handling
        // The callback is invoked AFTER CastReceiverContext.start() so MediaManager is ready
        lifecycle.addObserver(
            com.coulterpeterson.floatnative.cast.CastReceiverLifecycleObserver(
                onContextStarted = { setupCastMediaCallback() }
            )
        )
        
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
                        
                        // Check for pending cast load when navigation is ready
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            pendingCastLoad?.let { loadData ->
                                Log.d("CastReceiver", "Processing pending cast load: ${loadData.contentId}")
                                if (loadData.isLive) {
                                    navController.navigate("live_player/${loadData.contentId}")
                                } else {
                                    navController.navigate(
                                        com.coulterpeterson.floatnative.ui.navigation.TvScreen.Player.createRoute(
                                            loadData.contentId, 
                                            loadData.startPosition
                                        )
                                    )
                                }
                                pendingCastLoad = null
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
    
    override fun onStart() {
        super.onStart()
        // Pass the intent to MediaManager to handle Cast LOAD intents
        try {
            val mediaManager = CastReceiverContext.getInstance().mediaManager
            if (mediaManager.onNewIntent(intent)) {
                Log.d("CastReceiver", "onStart: MediaManager handled the intent")
            }
        } catch (e: Exception) {
            Log.e("CastReceiver", "onStart: Error passing intent to MediaManager", e)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // For cases where a new load intent triggers onNewIntent() instead of onStart()
        try {
            val mediaManager = CastReceiverContext.getInstance().mediaManager
            if (mediaManager.onNewIntent(intent)) {
                Log.d("CastReceiver", "onNewIntent: MediaManager handled the intent")
            }
        } catch (e: Exception) {
            Log.e("CastReceiver", "onNewIntent: Error passing intent to MediaManager", e)
        }
    }
    
    private fun setupCastMediaCallback() {
        try {
            val castContext = CastReceiverContext.getInstance()
            val mediaManager = castContext.mediaManager
            
            mediaManager.setMediaLoadCommandCallback(object : MediaLoadCommandCallback() {
                override fun onLoad(senderId: String?, loadRequestData: MediaLoadRequestData): Task<MediaLoadRequestData> {
                    Log.d("CastReceiver", "Received Load Request from sender: $senderId")
                    
                    val mediaInfo = loadRequestData.mediaInfo
                    if (mediaInfo != null) {
                        val contentId = mediaInfo.contentId ?: ""
                        val customData = mediaInfo.customData
                        val isLive = customData?.optString("type") == "LIVE" || 
                                     mediaInfo.streamType == com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE
                        val startPosition = loadRequestData.currentTime
                        
                        Log.d("CastReceiver", "Load Request details: contentId=$contentId, isLive=$isLive, startPos=$startPosition")
                        
                        // Store pending load data - will be processed when navigation is ready
                        pendingCastLoad = CastLoadData(contentId, isLive, startPosition)
                    }
                    
                    return Tasks.forResult(loadRequestData)
                }
            })
            
            Log.d("CastReceiver", "MediaLoadCommandCallback set up successfully")
        } catch (e: Exception) {
            Log.e("CastReceiver", "Error setting up MediaLoadCommandCallback", e)
        }
    }
}
