package com.fmhub24.app.ui.screens.player

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.fmhub24.app.plugins.cloudstream.ExtractorLink
import com.fmhub24.app.ui.components.LargeContentCard
import com.fmhub24.app.ui.theme.OrangeAccent
import kotlinx.coroutines.delay

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
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var currentEpisodeTitle by remember { mutableStateOf(episodeName) }
    var selectedDub by remember { mutableStateOf<String?>(null) }
    var autoNext by remember { mutableStateOf(true) }

    LaunchedEffect(url, apiName, episodeData) {
        currentEpisodeTitle = episodeName
        viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName)
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun startLink(link: ExtractorLink, resumePosition: Long = 0L) {
        releasePlayer()
        exoPlayer = createPlayer(context, link).apply {
            setMediaItem(MediaItem.fromUri(link.url))
            prepare()
            if (resumePosition > 1000) seekTo(resumePosition)
            playWhenReady = true
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is PlayerViewModel.PlayerUiState.Success && exoPlayer == null) {
            val resume = if (viewModel.resumeEnabled.value) viewModel.getResumePosition() else 0L
            startLink(state.selectedLink ?: state.links.first(), resume)
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoNext) {
                    val next = viewModel.nextEpisodeTarget()
                    if (next != null) {
                        currentEpisodeTitle = next.name
                        viewModel.loadLinks(url, apiName, name, posterUrl, next.data, next.name)
                        releasePlayer()
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            delay(5000)
            if (player.isPlaying && player.duration > 0) {
                viewModel.saveProgress(player.currentPosition, player.duration)
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        if (isFullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }
    DisposableEffect(Unit) { onDispose { releasePlayer(); isFullscreen = false } }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            currentEpisodeTitle?.let { Text(it, color = Color.Gray, fontSize = 12.sp, maxLines = 1) }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        if (isFullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PlayerSurface(uiState, exoPlayer, Modifier.fillMaxSize(), onRetry = {
                    viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName)
                })
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color.White)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)
            ) {
                PlayerSurface(
                    uiState = uiState,
                    player = exoPlayer,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    onRetry = { viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName) }
                )
                when (val state = uiState) {
                    is PlayerViewModel.PlayerUiState.Success -> {
                        val dubOptions = state.links.map { it.name.ifBlank { "Original" } }.distinct()
                        val filteredLinks = state.links.filter {
                            selectedDub == null || it.name.ifBlank { "Original" } == selectedDub
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                PlayerDropdown(
                                    label = "Quality",
                                    value = state.selectedLink?.let { "${it.quality}p • ${it.source}" } ?: "Select quality",
                                    options = filteredLinks.map { "${it.quality}p • ${it.source}" }.distinct(),
                                    onSelected = { value ->
                                        val link = filteredLinks.firstOrNull { "${it.quality}p • ${it.source}" == value }
                                        if (link != null) {
                                            viewModel.selectLink(link)
                                            startLink(link, exoPlayer?.currentPosition ?: 0L)
                                        }
                                    }
                                )
                            }
                            if (dubOptions.size > 1) {
                                item {
                                    PlayerDropdown(
                                        label = "Audio / Dub",
                                        value = selectedDub ?: dubOptions.first(),
                                        options = dubOptions,
                                        onSelected = {
                                            selectedDub = it
                                            val link = state.links.firstOrNull { link -> link.name.ifBlank { "Original" } == it }
                                            if (link != null) {
                                                viewModel.selectLink(link)
                                                startLink(link, exoPlayer?.currentPosition ?: 0L)
                                            }
                                        }
                                    )
                                }
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto next episode", color = Color.White, modifier = Modifier.weight(1f))
                                    Switch(checked = autoNext, onCheckedChange = { autoNext = it }, colors = SwitchDefaults.colors(checkedThumbColor = OrangeAccent))
                                }
                            }
                            if (state.subtitles.isNotEmpty()) {
                                item { Text("Subtitles", color = Color.White, fontWeight = FontWeight.Bold) }
                                items(state.subtitles) { sub ->
                                    Text("${sub.lang}: ${sub.url}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            if (state.recommendations.isNotEmpty()) {
                                item {
                                    Text("Recommended for you", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
                                }
                                items(state.recommendations) { item ->
                                    LargeContentCard(item = item, onClick = { onNavigateToDetails(item.url, item.apiName) })
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun PlayerSurface(
    uiState: PlayerViewModel.PlayerUiState,
    player: ExoPlayer?,
    modifier: Modifier,
    onRetry: () -> Unit
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when (uiState) {
            is PlayerViewModel.PlayerUiState.Loading -> CircularProgressIndicator(color = OrangeAccent)
            is PlayerViewModel.PlayerUiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                Text(uiState.message, color = Color(0xFFFF5252), fontSize = 13.sp)
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent), modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            is PlayerViewModel.PlayerUiState.Success -> {
                if (player == null) CircularProgressIndicator(color = OrangeAccent)
                else AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = true; this.player = player; layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlayerDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember(value) { mutableStateOf(false) }
    Column {
        Text(label, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(value, modifier = Modifier.weight(1f))
                Text("▾", color = OrangeAccent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF1A1A1A))) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = { onSelected(option); expanded = false }
                    )
                }
            }
        }
    }
}

private fun createPlayer(context: android.content.Context, link: ExtractorLink): ExoPlayer {
    val headers = linkedMapOf<String, String>()
    if (link.referer.isNotEmpty()) headers["Referer"] = link.referer
    headers.putAll(link.headers)
    val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
    val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    return ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()
}
