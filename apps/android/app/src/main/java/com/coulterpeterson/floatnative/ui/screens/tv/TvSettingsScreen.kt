@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.coulterpeterson.floatnative.ui.screens.tv

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.viewmodels.SettingsState
import com.coulterpeterson.floatnative.viewmodels.SettingsViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onLogoutSuccess: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val enhancedSearchEnabled by viewModel.enhancedSearchEnabled.collectAsState()
    val isLttOnlySubscriber by viewModel.isLttOnlySubscriber.collectAsState()
    val fakeLiveStreamEnabled by viewModel.fakeLiveStreamEnabled.collectAsState()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state) {
        if (state is SettingsState.LoggedOut) {
            onLogoutSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(horizontal = 50.dp, vertical = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- User Profile Header ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    userProfile?.let { user ->
                        AsyncImage(
                            model = user.profileImage?.path?.toString() ?: "",
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.Gray) // Placeholder bg
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = user.username ?: "User",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Text(
                            text = user.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }



            // --- Search ---
            item {
               SettingsSectionHeader("Search")
            }
            item {
                 // Only show if eligible or if we want to show it disabled
                 // Design usually shows it.
                 val subText = if (isLttOnlySubscriber) "Use specialized search for LTT content" else "Only available if subscribed solely to Linus Tech Tips"
                 
                 TvSettingsItem(
                     title = "Use Enhanced LTT Search",
                     subtitle = subText,
                     rightContent = {
                         Switch(
                             checked = enhancedSearchEnabled,
                             onCheckedChange = null, // Handled by item click
                             enabled = isLttOnlySubscriber
                         )
                     },
                     enabled = isLttOnlySubscriber,
                     onClick = { 
                         if (isLttOnlySubscriber) {
                             viewModel.setEnhancedSearchEnabled(!enhancedSearchEnabled)
                         }
                     }
                 )
            }

            // --- Support ---
            item {
                SettingsSectionHeader("Support")
            }
            item {
                Text(
                    text = "Say thanks by subscribing to the Coulter Peterson channel :)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // --- Debug ---
            if (com.coulterpeterson.floatnative.BuildConfig.DEBUG) {
                 item {
                     SettingsSectionHeader("Debug")
                 }
                 item {
                     TvSettingsItem(
                         title = "Enable Fake Live Stream",
                         subtitle = "Injects a fake live stream into the main feed for UI testing",
                         rightContent = {
                             Switch(
                                 checked = fakeLiveStreamEnabled,
                                 onCheckedChange = null
                             )
                         },
                         onClick = { viewModel.setFakeLiveStreamEnabled(!fakeLiveStreamEnabled) }
                     )
                 }
            }

            // --- Logout ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = "Log Out",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsItem(
    title: String,
    subtitle: String? = null,
    rightContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }
            if (rightContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                rightContent()
            }
        }
    }
}
