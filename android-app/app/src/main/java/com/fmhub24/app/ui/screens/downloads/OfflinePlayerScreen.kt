package com.fmhub24.app.ui.screens.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fmhub24.app.download.FMHubDownloadService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePlayerScreen(
    sourceUrl: String,
    title: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(sourceUrl) {
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(FMHubDownloadService.downloadCache(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(sourceUrl))
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
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
