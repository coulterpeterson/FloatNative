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

class TvMainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    val token = FloatplaneApi.tokenManager.accessToken
                    var isLoggedIn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!token.isNullOrEmpty()) }

                    if (!isLoggedIn) {
                        com.coulterpeterson.floatnative.ui.screens.tv.TvLoginScreen(
                            onLoginSuccess = {
                                isLoggedIn = true
                            }
                        )
                    } else {
                        com.coulterpeterson.floatnative.ui.navigation.TvAppNavigation(
                            startDestination = com.coulterpeterson.floatnative.ui.navigation.TvScreen.Home.route
                        )
                    }
                }
            }
        }
    }
}
