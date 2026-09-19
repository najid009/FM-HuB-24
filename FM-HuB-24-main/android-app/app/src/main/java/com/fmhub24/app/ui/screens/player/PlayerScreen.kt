package com.fmhub24.app.ui.screens.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.fmhub24.app.plugins.cloudstream.ExtractorLink
import com.fmhub24.app.ui.theme.OrangeAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    url: String,
    apiName: String,
    name: String,
    posterUrl: String?,
    episodeData: String?,
    episodeName: String?,
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    LaunchedEffect(url, apiName, episodeData) {
        viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName)
    }

    // Cleanup player on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Save progress periodically - cancellable and tied to player lifecycle
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            delay(5000)
            if (player.isPlaying) {
                val pos = player.currentPosition
                val dur = player.duration
                if (dur > 0 && dur != androidx.media3.common.C.TIME_UNSET) {
                    viewModel.saveProgress(pos, dur)
                    currentPosition = pos
                    duration = dur
                }
            }
        }
    }

    // Initialize player when link becomes available
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is PlayerViewModel.PlayerUiState.Success) {
            val link = state.selectedLink
            if (link != null && exoPlayer == null) {
                val player = createPlayer(context, link).apply {
                    setMediaItem(MediaItem.fromUri(link.url))
                    // Resume (only when the user has enabled it in Settings)
                    try {
                        if (viewModel.resumeEnabled.value) {
                            val resumePos = viewModel.getResumePosition()
                            if (resumePos > 1000) seekTo(resumePos)
                        }
                    } catch (_: Exception) {}
                    prepare()
                    playWhenReady = true
                }
                exoPlayer = player
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (episodeName != null) {
                            Text(text = episodeName, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            // Player View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                when (val state = uiState) {
                    is PlayerViewModel.PlayerUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = OrangeAccent)
                                Text(
                                    text = "Fetching real stream via loadLinks()...",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                    }
                    is PlayerViewModel.PlayerUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text(text = "⚠️", fontSize = 32.sp)
                                Text(
                                    text = state.message,
                                    color = Color(0xFFFF5252),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                                Button(
                                    onClick = { viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName) },
                                    modifier = Modifier.padding(top = 16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    is PlayerViewModel.PlayerUiState.Success -> {
                        // ExoPlayer View
                        if (exoPlayer != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        useController = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    view.player = exoPlayer
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = OrangeAccent)
                            }
                        }
                    }
                }
            }

            // Quality selector & links
            when (val state = uiState) {
                is PlayerViewModel.PlayerUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Text(
                                text = "Available Qualities (${state.links.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        items(state.links) { link ->
                            QualityItem(
                                link = link,
                                isSelected = link.url == state.selectedLink?.url,
                                onClick = {
                                    // Switch quality - rebuild the player so the new
                                    // link's headers (referer etc.) apply to requests
                                    viewModel.selectLink(link)
                                    val previousPos = exoPlayer?.currentPosition ?: 0L
                                    exoPlayer?.release()
                                    val player = createPlayer(context, link).apply {
                                        setMediaItem(MediaItem.fromUri(link.url))
                                        prepare()
                                        if (previousPos > 0) seekTo(previousPos)
                                        playWhenReady = true
                                    }
                                    exoPlayer = player
                                }
                            )
                        }

                        if (state.subtitles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Subtitles (${state.subtitles.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                                )
                            }
                            items(state.subtitles) { sub ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(text = "${sub.lang}: ${sub.url}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun QualityItem(
    link: ExtractorLink,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) OrangeAccent.copy(alpha = 0.15f) else Color(0xFF121212)
        ),
        shape = RoundedCornerShape(10.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.name.ifBlank { link.source },
                    color = if (isSelected) OrangeAccent else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${link.quality}p • ${link.source} • ${link.type}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .background(OrangeAccent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Playing", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Builds an ExoPlayer that sends the extractor's required headers (Referer and any
 * custom headers returned by loadLinks) with every HTTP request. Many streams 403
 * without them. Media3 applies per-request headers at the DataSource level.
 */
private fun createPlayer(context: android.content.Context, link: ExtractorLink): ExoPlayer {
    val headers = linkedMapOf<String, String>()
    if (link.referer.isNotEmpty()) headers["Referer"] = link.referer
    headers.putAll(link.headers)

    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(headers)
    val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
}
