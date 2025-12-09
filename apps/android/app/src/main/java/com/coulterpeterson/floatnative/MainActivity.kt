package com.coulterpeterson.floatnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.coulterpeterson.floatnative.ui.screens.auth.LoginScreen
import com.coulterpeterson.floatnative.ui.theme.FloatNativeTheme
import com.coulterpeterson.floatnative.ui.navigation.AppNavigation

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
                    if (isTv) {
                        com.coulterpeterson.floatnative.ui.navigation.TvAppNavigation()
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }
}