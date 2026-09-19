package com.fmhub24.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.hilt.navigation.compose.hiltViewModel
import com.fmhub24.app.BuildConfig
import com.fmhub24.app.plugins.PluginManager
import com.fmhub24.app.ui.theme.OrangeAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // App info header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF121212))),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.linearGradient(listOf(OrangeAccent, Color(0xFFEA580C))),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "FM", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = "FMHuB24", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Color.Gray, fontSize = 12.sp)
                            Text(text = "Package: ${BuildConfig.APPLICATION_ID}", color = Color.Gray.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                    }
                }
            }

                        item {
                SettingsSection(title = "Playback") {
                    val resumePlayback by viewModel.resumePlayback.collectAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Resume playback", color = Color.White, fontSize = 14.sp)
                            Text(text = "Continue from last position (saved in Room)", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = resumePlayback,
                            onCheckedChange = { viewModel.onResumePlaybackChange(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OrangeAccent, checkedTrackColor = OrangeAccent.copy(alpha = 0.5f))
                        )
                    }

                    val showAdult by viewModel.showAdult.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Show adult providers", color = Color.White, fontSize = 14.sp)
                            Text(
                                text = "Providers whose supported types are NSFW-only stay hidden until this is on",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = showAdult,
                            onCheckedChange = { viewModel.onShowAdultChange(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OrangeAccent, checkedTrackColor = OrangeAccent.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            item {
                ExtensionsSection(viewModel)
            }

            item {
                SettingsSection(title = "About") {
                    Text(
                        text = "FMHuB24 shows real, up-to-date content only — no demo or placeholder entries. If a title is missing, it simply is not available right now; pull to refresh and try again.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Made for FMHuB24 • Dark theme by default",
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            color = OrangeAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(text = subtitle, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ExtensionsSection(viewModel: SettingsViewModel) {
    val extensions by viewModel.extensions.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val providers by viewModel.providers.collectAsState()

    SettingsSection(title = "Status") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (loadState) {
                        is PluginManager.LoadingState.Success -> "Ready"
                        is PluginManager.LoadingState.Error -> "Not available right now"
                        is PluginManager.LoadingState.Loading,
                        is PluginManager.LoadingState.Progress -> "Please wait"
                        else -> "Not ready yet"
                    },
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${providers.size} source(s) available",
                    color = Color.Gray, fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.syncFromAdmin() }) { Text("Refresh") }
            OutlinedButton(onClick = { viewModel.reloadFromDisk() }) { Text("Try again") }
        }

        if (extensions.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No sources are available yet.",
                color = Color.Gray, fontSize = 12.sp
            )
        }

        extensions.forEach { ext ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = ext.name, color = Color.White, fontSize = 13.sp)
                    // The version, size, API level and the loader's technical reason are all
                    // deliberately hidden; `ext.lastError` is written to the app log instead.
                    Text(
                        text = if (ext.enabled) "On" else "Off",
                        color = Color.Gray, fontSize = 10.sp
                    )
                }
                Switch(
                    checked = ext.enabled,
                    onCheckedChange = { viewModel.setEnabled(ext.id, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = OrangeAccent, checkedTrackColor = OrangeAccent.copy(alpha = 0.5f))
                )
            }
        }

        if (extensions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { viewModel.clearExtensions() }) {
                Text("Reset", color = Color(0xFFFF5252), fontSize = 12.sp)
            }
        }
    }
}
