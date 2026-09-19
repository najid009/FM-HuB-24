package com.fmhub24.app.ui.screens.splash

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fmhub24.app.ui.theme.CyanAccent
import com.fmhub24.app.ui.theme.OrangeAccent
import com.fmhub24.app.BuildConfig
import kotlinx.coroutines.delay

// OptIn kept even where the clipboard API is already stable: it costs a warning at worst and keeps
// this screen compiling across Compose bumps.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val previousCrash by viewModel.previousCrash.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val latestRelease by viewModel.latestRelease.collectAsState()
    val context = LocalContext.current
    var showNotice by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(notice) { if (notice != null) showNotice = true }
    LaunchedEffect(latestRelease) {
        if (latestRelease?.version_code ?: 0 > BuildConfig.VERSION_CODE) showUpdate = true
    }

    // Logo animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
    }

    LaunchedEffect(uiState, latestRelease) {
        when (uiState) {
            is SplashViewModel.SplashUiState.Success,
            is SplashViewModel.SplashUiState.Empty -> {
                if (latestRelease?.let { it.is_required && it.version_code > BuildConfig.VERSION_CODE } != true) {
                    delay(800)
                    onNavigateToHome()
                }
            }
            is SplashViewModel.SplashUiState.Error -> {
                delay(2000)
                // Still navigate to home to show empty/error state there
                onNavigateToHome()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF000000))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(if (showContent) 1f else 0f)
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(OrangeAccent, Color(0xFFEA580C))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "FM",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "24",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "FMHuB24",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Free Movies 24 • Watch what you love",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (val state = uiState) {
                is SplashViewModel.SplashUiState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = OrangeAccent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        // No status text here: the spinner is the whole message.
                    }
                }
                is SplashViewModel.SplashUiState.Downloading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                        CircularProgressIndicator(
                            color = OrangeAccent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Please wait",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        if (state.progress > 0) {
                            LinearProgressIndicator(
                                progress = state.progress / 100f,
                                color = OrangeAccent,
                                trackColor = Color(0xFF1A1A1A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .height(4.dp)
                            )
                            Text(
                                text = "${state.progress}%",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                is SplashViewModel.SplashUiState.Success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00C853).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "✓ ${state.providerCount} providers loaded",
                                color = Color(0xFF00C853),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                is SplashViewModel.SplashUiState.Empty -> {
                    Text(
                        text = "No content available",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                is SplashViewModel.SplashUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        // Per-file loader reasons are NOT rendered here (they name classes, files and
                        // the publishing pipeline). They are appended to the app log and mirrored to
                        // Download/FMHub24-crash.txt, which is what a developer needs to see.
                        if (state.detail.isNotEmpty()) {
                            Text(
                                text = "${state.detail.size} of the available sources did not start.",
                                color = Color(0xFF9E9E9E),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp)
                            )
                        }
                        Text(
                            text = "Continuing with cached content...",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------- previous crash report
        // Only the splash screen is guaranteed to exist when the app fails, so a crash that
        // happened before any error state could be shown is surfaced here on the next launch.
        previousCrash?.let { log ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1414))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .heightIn(max = 230.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "FMHuB24 crashed the last time it ran",
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "That log is the real cause \u2014 copy it and send it together with this " +
                            "device's Android version. It is the difference between a guess and a fix.",
                        color = Color(0xFFB3B3B3),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 15.sp
                    )
                    SelectionContainer {
                        Text(
                            text = log,
                            color = Color(0xFFE6E6E6),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val clipboard = LocalClipboardManager.current
                        TextButton(onClick = { clipboard.setText(AnnotatedString(log)) }) {
                            Text("Copy", color = OrangeAccent, fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.dismissCrashLog() }) {
                            Text("Clear", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Bottom branding — no mention of where the content comes from.
        Text(
            text = "FMHuB24",
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        if (showNotice) {
            notice?.let { item ->
                AlertDialog(
                    onDismissRequest = { showNotice = false },
                    title = { Text(item.title) },
                    text = { Text(item.message) },
                    confirmButton = { TextButton(onClick = { showNotice = false }) { Text("OK") } },
                )
            }
        }
        if (showUpdate) {
            latestRelease?.let { release ->
                val required = release.is_required && release.version_code > BuildConfig.VERSION_CODE
                AlertDialog(
                    onDismissRequest = { if (!required) showUpdate = false },
                    title = { Text("Update available: ${release.version_name}") },
                    text = { Text(release.release_notes ?: "A newer FMHub24 version is available.") },
                    confirmButton = {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.download_url)))
                        }) { Text("Update") }
                    },
                    dismissButton = if (required) null else ({ TextButton(onClick = { showUpdate = false }) { Text("Later") } }),
                )
            }
        }
    }
}
