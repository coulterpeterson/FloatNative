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
import com.google.android.gms.cast.CastDevice
import org.json.JSONObject

class TvMainActivity : ComponentActivity() {
    
    // Pending cast load data - reactive state so Compose can observe changes
    private var pendingCastLoadState = androidx.compose.runtime.mutableStateOf<CastLoadData?>(null)
    
    // Helper to set state from non-compose contexts
    private var pendingCastLoad: CastLoadData?
        get() = pendingCastLoadState.value
        set(value) { pendingCastLoadState.value = value }
    
    data class CastLoadData(
        val contentId: String,
        val isLive: Boolean,
        val startPosition: Long
    )
    
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRITICAL: Initialize Cast Connect synchronously in onCreate() BEFORE UI setup
        // The order MUST be:
        // 1. CastReceiverContext.initInstance(this)
        // 2. CastReceiverContext.start()
        // 3. Set up MediaLoadCommandCallback
        // 4. Call mediaManager.onNewIntent(intent)
        
        // Explicitly initialize context to ensure options are loaded correctly
        try {
            CastReceiverContext.initInstance(this)
            Log.d("CastReceiver", "CastReceiverContext initialized via initInstance")
            
            // Custom Message Listener
            val namespace = "urn:x-cast:com.floatnative.cast"
            CastReceiverContext.getInstance().setMessageReceivedListener(namespace) { p1: String, p2: String?, p3: String ->
                Log.d("CastReceiver", "Received Custom Message: p1=$p1, p2=$p2, p3=$p3")
                // Assuming p3 is message based on typical (namespace, senderId, message) or similar.
                // Let's try to parse the one that looks like JSON.
                val message = if (p3.trim().startsWith("{")) p3 else if (p1.trim().startsWith("{")) p1 else ""
                
                try {
                    val json = JSONObject(message)
                    val videoId = json.getString("videoId")
                    val timestamp = json.optLong("timestamp", 0L)
                    val isLive = false // Assume VOD for now via this channel
                    
                    Log.d("CastReceiver", "Parsed Custom Message: videoId=$videoId, timestamp=$timestamp")
                    
                    // Directly set pending load - LaunchedEffect in setContent will pick it up
                    pendingCastLoad = CastLoadData(videoId, isLive, timestamp)
                } catch (e: Exception) {
                    Log.e("CastReceiver", "Failed to parse custom message", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e("CastReceiver", "Failed to init CastReceiverContext", e)
        }

        try {
            CastReceiverContext.getInstance().start()
            Log.d("CastReceiver", "CastReceiverContext started")
        } catch (e: Exception) {
            Log.e("CastReceiver", "Failed to start CastReceiverContext", e)
        }
        


        // Set up the callback BEFORE calling onNewIntent
        setupCastMediaCallback()
        
        // Now process the launch intent - callback is ready to receive it
        logIntentExtras(intent)
        try {
            val mediaManager = CastReceiverContext.getInstance().mediaManager
            if (mediaManager.onNewIntent(intent)) {
                Log.d("CastReceiver", "onCreate: MediaManager handled the launch intent")
            }
        } catch (e: Exception) {
            Log.e("CastReceiver", "onCreate: Error passing intent to MediaManager", e)
        }
        
        // Register lifecycle observer for stop/cleanup only (not for initial start)
        lifecycle.addObserver(
            com.coulterpeterson.floatnative.cast.CastReceiverLifecycleObserver()
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
                        // We use a side-effect that triggers whenever our 'pendingCastLoadState' changes
                        androidx.compose.runtime.LaunchedEffect(pendingCastLoadState.value) {
                            pendingCastLoadState.value?.let { loadData ->
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
                                // Reset after consumption
                                pendingCastLoadState.value = null
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
    
    // Log intent for debugging
    private fun logIntentExtras(intent: Intent?) {
        if (intent == null) {
            Log.d("CastReceiver", "Intent is null")
            return
        }
        val extras = intent.extras
        if (extras != null) {
            Log.d("CastReceiver", "Intent Extras:")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                Log.d("CastReceiver", "Key: $key, Value: $value")
            }
        } else {
            Log.d("CastReceiver", "Intent has no extras")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("CastReceiver", "onNewIntent called")
        logIntentExtras(intent)
        
        // For cases where a new load intent triggers onNewIntent() instead of onStart()
        try {
            val mediaManager = CastReceiverContext.getInstance().mediaManager
            if (mediaManager.onNewIntent(intent)) {
                Log.d("CastReceiver", "onNewIntent: MediaManager handled the intent")
            } else {
                Log.d("CastReceiver", "onNewIntent: MediaManager did NOT handle the intent") 
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
