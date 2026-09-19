package com.fmhub24.app.ui.screens.downloads

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fmhub24.app.ui.theme.OrangeAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePlayerScreen(
    path: String,
    title: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    BackHandler(onBack = onNavigateBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)
        )
    }
}
