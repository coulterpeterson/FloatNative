package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.viewmodels.SettingsState
import com.coulterpeterson.floatnative.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val enhancedSearchEnabled by viewModel.enhancedSearchEnabled.collectAsState()
    val isLttOnlySubscriber by viewModel.isLttOnlySubscriber.collectAsState()
    val downloadLocationUri by viewModel.downloadLocation.collectAsState()
    
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setDownloadLocation(uri.toString())
        }
    }

    LaunchedEffect(state) {
        if (state is SettingsState.LoggedOut) {
            onLogoutSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Profile Section ---
            item {
                userProfile?.let { user ->
                    ListItem(
                        headlineContent = { Text(user.username ?: "User") },
                        supportingContent = { Text(user.email ?: "") },
                        leadingContent = {
                            AsyncImage(
                                model = user.profileImage?.path?.toString() ?: "",
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                }
            }

            // --- Appearance Section ---
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                Column {
                    ThemeOption(
                        label = "System Default",
                        selected = themeMode == "system",
                        onClick = { viewModel.setThemeMode("system") }
                    )
                    ThemeOption(
                        label = "Light",
                        selected = themeMode == "light",
                        onClick = { viewModel.setThemeMode("light") }
                    )
                    ThemeOption(
                        label = "Dark",
                        selected = themeMode == "dark",
                        onClick = { viewModel.setThemeMode("dark") }
                    )
                }
            }

            // --- Search Section ---
            item {
                Text("Search", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                ListItem(
                    headlineContent = { Text("Enhanced LTT Search") },
                    supportingContent = { 
                        if (isLttOnlySubscriber) {
                            Text("Use specialized search for LTT content.")
                        } else {
                            Text("Only available if subscribed solely to Linus Tech Tips.")
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = enhancedSearchEnabled,
                            onCheckedChange = { viewModel.setEnhancedSearchEnabled(it) },
                            enabled = isLttOnlySubscriber
                        )
                    }
                )
            }

            // --- Downloads Section ---
            item {
                Text("Downloads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                val locationLabel = if (downloadLocationUri != null) {
                    try {
                        val treeUri = android.net.Uri.parse(downloadLocationUri)
                        DocumentFile.fromTreeUri(context, treeUri)?.name ?: downloadLocationUri
                    } catch (e: Exception) {
                        "Custom Folder"
                    }
                } else {
                    "System Default (Cache)"
                }

                ListItem(
                    headlineContent = { Text("Download Location") },
                    supportingContent = { Text(locationLabel ?: "Select Folder") },
                    modifier = Modifier.clickable {
                        folderPickerLauncher.launch(null)
                    }
                )
            }

            // --- Support Section ---
            item {
                Text("Support", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                ListItem(
                    headlineContent = { Text("Join the Discord to Share Feedback") },
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://discord.gg/VvgCsKBwpP") 
                    }
                )
                ListItem(
                    headlineContent = { Text("Subscribe to Coulter Peterson on YouTube") },
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.youtube.com/@CoulterPeterson")
                    }
                )
            }

            // --- Logout ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out")
                }
            }
        }
    }
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
