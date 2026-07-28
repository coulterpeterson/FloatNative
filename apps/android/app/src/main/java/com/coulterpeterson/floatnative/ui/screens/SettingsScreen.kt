package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Subscriptions
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
    onOpenDebugLog: () -> Unit = {},
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

    var showDonateDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var currentCookie by remember { mutableStateOf(com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.authCookie ?: "") }
    if (showDonateDialog) {
        com.coulterpeterson.floatnative.ui.components.DonateDialog(
            onDismiss = { showDonateDialog = false }
        )
    }

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

            // --- Authentication & Session Section ---
            item {
                Text("Authentication & Session", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                val storedCookie = com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.authCookie
                ListItem(
                    headlineContent = { Text("Session Cookie (sails.sid)") },
                    supportingContent = { Text(if (storedCookie.isNullOrEmpty()) "Not set (Tap to enter manually)" else "Present (Synced)") },
                    modifier = Modifier.clickable {
                        currentCookie = storedCookie ?: ""
                        showCookieDialog = true
                    }
                )
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

            // --- Playback Section ---
            item {
                Text("Playback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                val sleepRemaining by com.coulterpeterson.floatnative.utils.SleepTimerRepository.remainingSeconds.collectAsState()
                var showSleepPicker by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = { Text("Sleep Timer") },
                    supportingContent = {
                        if (sleepRemaining != null) {
                            Text(
                                "Pauses in ${com.coulterpeterson.floatnative.utils.SleepTimerRepository.formatRemaining(sleepRemaining!!)}",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text("Pause playback automatically after a chosen interval.")
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { showSleepPicker = true }) {
                            Text(if (sleepRemaining != null) "Change" else "Set")
                        }
                    },
                    modifier = Modifier.clickable { showSleepPicker = true }
                )

                if (showSleepPicker) {
                    AlertDialog(
                        onDismissRequest = { showSleepPicker = false },
                        title = { Text("Sleep Timer") },
                        text = {
                            Column {
                                TextButton(onClick = {
                                    com.coulterpeterson.floatnative.utils.SleepTimerRepository.cancel()
                                    showSleepPicker = false
                                }) { Text("Off") }
                                com.coulterpeterson.floatnative.utils.SleepTimerRepository.options.forEach { duration ->
                                    TextButton(onClick = {
                                        com.coulterpeterson.floatnative.utils.SleepTimerRepository.arm(duration)
                                        showSleepPicker = false
                                    }) {
                                        Text(com.coulterpeterson.floatnative.utils.SleepTimerRepository.formatDuration(duration))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSleepPicker = false }) { Text("Cancel") }
                        },
                    )
                }
            }

            // --- Support Section ---
            item {
                Text("Support", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                val isTv = remember(context) {
                    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                }
                ListItem(
                    headlineContent = { Text("Donate") },
                    supportingContent = {
                        Text(if (isTv) "Show appreciation via Stripe — opens a QR code" else "Show appreciation via Stripe")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFE91E63),  // pink
                        )
                    },
                    modifier = Modifier.clickable {
                        if (isTv) {
                            // TV browsers can't navigate to external URLs cleanly,
                            // so show a QR for the user to scan with their phone.
                            showDonateDialog = true
                        } else {
                            uriHandler.openUri(com.coulterpeterson.floatnative.utils.DonationUrl.STRIPE)
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("What's New in This Version") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable {
                        com.coulterpeterson.floatnative.utils.WhatsNewRepository.presentManually(context)
                    }
                )
                ListItem(
                    headlineContent = { Text("Send Feedback via Discord") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Forum,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFF7C4DFF),  // discord-purple
                        )
                    },
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://discord.gg/VvgCsKBwpP")
                    }
                )
                ListItem(
                    headlineContent = { Text("Subscribe to Coulter Peterson on YouTube") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Subscriptions,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFFF0000),  // youtube red
                        )
                    },
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.youtube.com/@CoulterPeterson")
                    }
                )
            }

            // --- Debug Section ---
            item {
                val debugLogEntries by com.coulterpeterson.floatnative.utils.DebugLogManager.entries.collectAsState()
                Text("Debug", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))

                // Debug log accessible in all builds — handy for users to
                // grab the last decode/auth error and paste it into a bug report.
                ListItem(
                    headlineContent = { Text("Debug Log") },
                    supportingContent = {
                        Text(
                            if (debugLogEntries.isEmpty())
                                "No diagnostic events yet."
                            else
                                "${debugLogEntries.size} recent event${if (debugLogEntries.size == 1) "" else "s"}"
                        )
                    },
                    trailingContent = {
                        if (debugLogEntries.isNotEmpty()) {
                            Badge { Text(debugLogEntries.size.toString()) }
                        }
                    },
                    modifier = Modifier.clickable { onOpenDebugLog() }
                )

                if (com.coulterpeterson.floatnative.BuildConfig.DEBUG) {
                    val fakeLiveDtreamEnabled by viewModel.fakeLiveStreamEnabled.collectAsState()
                    ListItem(
                        headlineContent = { Text("Enable Fake Live Stream") },
                        supportingContent = { Text("Injects a fake live stream into the main feed for UI testing.") },
                        trailingContent = {
                            Switch(
                                checked = fakeLiveDtreamEnabled,
                                onCheckedChange = { viewModel.setFakeLiveStreamEnabled(it) }
                            )
                        }
                    )
                }

                val storedCookie = com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.authCookie
                ListItem(
                    headlineContent = { Text("Session Cookie (sails.sid)") },
                    supportingContent = { Text(if (storedCookie.isNullOrEmpty()) "Not set (Tap to enter manually)" else "Present (Synced)") },
                    modifier = Modifier.clickable {
                        currentCookie = storedCookie ?: ""
                        showCookieDialog = true
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

        if (showCookieDialog) {
            AlertDialog(
                onDismissRequest = { showCookieDialog = false },
                title = { Text("Session Cookie (sails.sid)") },
                text = {
                    Column {
                        Text("Paste your Floatplane sails.sid cookie if automatic sync was missed:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentCookie,
                            onValueChange = { currentCookie = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("s%3A...") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clean = currentCookie.trim().removePrefix("sails.sid=").trim()
                        com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.authCookie = clean.ifEmpty { null }
                        showCookieDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCookieDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
