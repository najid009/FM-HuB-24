package com.fmhub24.app.ui.screens.downloads

import android.util.Base64
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fmhub24.app.download.FMHubDownloadService
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePlayerScreen(
    sourceUrl: String,
    title: String,
    drmKeySetId: String,
    drmLicenseUrl: String,
    drmScheme: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(sourceUrl, drmKeySetId) {
        // No upstream factory is configured deliberately: a missing segment fails instead of
        // silently using the network and pretending that offline playback works.
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(FMHubDownloadService.downloadCache(context))
            .setUpstreamDataSourceFactory(null)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .apply {
                setMediaItem(buildOfflineMediaItem(sourceUrl, drmKeySetId, drmLicenseUrl, drmScheme))
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
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
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

private fun buildOfflineMediaItem(url: String, keySetId: String, licenseUrl: String, scheme: String): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (keySetId.isNotBlank() && licenseUrl.isNotBlank() && scheme.isNotBlank()) {
        val drm = MediaItem.DrmConfiguration.Builder(UUID.fromString(scheme), android.net.Uri.parse(licenseUrl))
            .setKeySetId(Base64.decode(keySetId, Base64.DEFAULT))
            .setMultiSession(true)
            .build()
        builder.setDrmConfiguration(drm)
    }
    return builder.build()
}
