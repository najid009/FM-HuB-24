package com.fmhub24.app.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.fmhub24.app.media.DrmConfig
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
    val downloadState by viewModel.downloadState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsLocked by rememberSaveable { mutableStateOf(false) }
    var currentEpisodeTitle by remember { mutableStateOf(episodeName) }
    var autoNext by rememberSaveable { mutableStateOf(true) }
    var activeLink by remember { mutableStateOf<ExtractorLink?>(null) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }

    LaunchedEffect(url, apiName, episodeData) {
        currentEpisodeTitle = episodeName
        viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName)
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun startLink(link: ExtractorLink, resumePosition: Long = 0L) {
        activeLink = link
        releasePlayer()
        exoPlayer = createPlayer(context, link).apply {
            setPlaybackSpeed(playbackSpeed)
            setMediaItem(buildMediaItem(link))
            prepare()
            if (resumePosition > 1000) seekTo(resumePosition)
            playWhenReady = true
        }
    }

    fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        activity?.requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun cyclePlaybackSpeed() {
        val next = when (playbackSpeed) {
            1f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        playbackSpeed = next
        exoPlayer?.setPlaybackSpeed(next)
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
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val failedLink = activeLink ?: return
                val nextLink = viewModel.nextFallback(failedLink) ?: return
                val position = player.currentPosition.coerceAtLeast(0L)
                startLink(nextLink, position)
            }

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
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = isFullscreen) { setFullscreen(false) }
    DisposableEffect(Unit) {
        onDispose {
            releasePlayer()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            isFullscreen = false
        }
    }

    if (isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(
                uiState = uiState,
                player = exoPlayer,
                title = name,
                episodeTitle = currentEpisodeTitle,
                modifier = Modifier.fillMaxSize(),
                onRetry = { viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName) },
                onFullscreen = { setFullscreen(false) },
                onAudioSettings = { player -> showAudioTrackDialog(context, player) },
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedChange = ::cyclePlaybackSpeed,
                controlsLocked = controlsLocked,
                onToggleLock = { controlsLocked = !controlsLocked },
                isFullscreen = true
            )
        }
    } else {
        Scaffold(
            topBar = {
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
                )
            },
            containerColor = Color.Black
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
                PlayerSurface(
                    uiState = uiState,
                    player = exoPlayer,
                    title = name,
                    episodeTitle = currentEpisodeTitle,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    onRetry = { viewModel.loadLinks(url, apiName, name, posterUrl, episodeData, episodeName) },
                    onFullscreen = { setFullscreen(true) },
                    onAudioSettings = { player -> showAudioTrackDialog(context, player) },
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = ::cyclePlaybackSpeed,
                    controlsLocked = controlsLocked,
                    onToggleLock = { controlsLocked = !controlsLocked },
                    isFullscreen = false
                )
                when (val state = uiState) {
                    is PlayerViewModel.PlayerUiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                PlayerDropdown(
                                    label = "Quality",
                                    value = state.selectedLink?.let { "${it.quality}p • ${it.source}" } ?: "Select quality",
                                    options = state.links.map { "${it.quality}p • ${it.source}" }.distinct(),
                                    onSelected = { value ->
                                        val link = state.links.firstOrNull { "${it.quality}p • ${it.source}" == value }
                                        if (link != null) {
                                            viewModel.selectLink(link)
                                            startLink(link, exoPlayer?.currentPosition ?: 0L)
                                        }
                                    }
                                )
                            }
                            item {
                                Button(
                                    onClick = { state.selectedLink?.let(viewModel::downloadSelected) },
                                    enabled = state.selectedLink != null && downloadState?.contains("Adding") != true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(downloadState ?: "Download for offline viewing", color = Color.White)
                                }
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto next episode", color = Color.White, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = autoNext,
                                        onCheckedChange = { autoNext = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = OrangeAccent)
                                    )
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
                                    Text(
                                        "Recommended for you",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
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
    title: String,
    episodeTitle: String?,
    modifier: Modifier,
    onRetry: () -> Unit,
    onFullscreen: () -> Unit,
    onAudioSettings: (ExoPlayer) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: () -> Unit,
    controlsLocked: Boolean,
    onToggleLock: () -> Unit,
    isFullscreen: Boolean
) {
    var controlsVisible by rememberSaveable(player) { mutableStateOf(true) }
    var position by remember(player) { mutableLongStateOf(0L) }

    LaunchedEffect(player, controlsVisible, controlsLocked) {
        while (player != null) {
            position = player.currentPosition.coerceAtLeast(0L)
            delay(if (controlsVisible && !controlsLocked) 500L else 1_000L)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(enabled = player != null && !controlsLocked) {
                controlsVisible = !controlsVisible
            },
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is PlayerViewModel.PlayerUiState.Loading -> CircularProgressIndicator(color = OrangeAccent)
            is PlayerViewModel.PlayerUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Text(uiState.message, color = Color(0xFFFF5252), fontSize = 13.sp)
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Retry") }
            }
            is PlayerViewModel.PlayerUiState.Success -> {
                if (player == null) {
                    CircularProgressIndicator(color = OrangeAccent)
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                this.player = player
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (controlsVisible && !controlsLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = .62f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = .82f)
                                        )
                                    )
                                )
                        )
                    }

                    if (controlsVisible || controlsLocked) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onFullscreen, enabled = !controlsLocked) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                episodeTitle?.let { Text(it, color = Color.LightGray, fontSize = 12.sp, maxLines = 1) }
                            }
                            if (isFullscreen) {
                                TextButton(onClick = onToggleLock) {
                                    Icon(
                                        if (controlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (controlsLocked) "Unlock controls" else "Lock controls",
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (controlsLocked) "Unlock" else "Lock", color = Color.White)
                                }
                            }
                            IconButton(onClick = { onAudioSettings(player) }, enabled = !controlsLocked) {
                                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                            }
                        }
                    }

                    if (controlsVisible && !controlsLocked) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 44.dp else 28.dp)
                        ) {
                            PlayerControlButton(Icons.Default.FastRewind, "Rewind 10 seconds") { player.seekBack() }
                            PlayerControlButton(
                                icon = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                description = "Play or pause",
                                large = true
                            ) { if (player.isPlaying) player.pause() else player.play() }
                            PlayerControlButton(Icons.Default.FastForward, "Forward 10 seconds") { player.seekForward() }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatTime(position), color = Color.White, fontSize = 12.sp)
                                Slider(
                                    value = position.toFloat().coerceIn(0f, player.duration.coerceAtLeast(1L).toFloat()),
                                    onValueChange = { player.seekTo(it.toLong()) },
                                    valueRange = 0f..player.duration.coerceAtLeast(1L).toFloat(),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = .35f)
                                    )
                                )
                                Text(formatTime(player.duration), color = Color.White, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onToggleLock) {
                                    Icon(
                                        if (controlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        "Lock controls",
                                        tint = Color.White
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onAudioSettings(player) }) { Text("Language", color = Color.White) }
                                TextButton(onClick = onPlaybackSpeedChange) { Text("${playbackSpeed}x", color = Color.White) }
                                TextButton(onClick = onFullscreen) {
                                    Icon(
                                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        "Fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(if (large) 78.dp else 58.dp)) {
        Crossfade(targetState = icon, label = "playerControlIcon") { targetIcon ->
            Icon(targetIcon, description, tint = Color.White, modifier = Modifier.size(if (large) 58.dp else 38.dp))
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
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
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1A1A1A))
            ) {
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

private fun showAudioTrackDialog(context: android.content.Context, player: ExoPlayer) {
    TrackSelectionDialogBuilder(context, "Audio and subtitles", player, C.TRACK_TYPE_AUDIO)
        .setShowDisableOption(false)
        .build()
        .show()
}

private fun createPlayer(context: android.content.Context, link: ExtractorLink): ExoPlayer {
    val headers = linkedMapOf<String, String>()
    if (link.referer.isNotEmpty()) headers["Referer"] = link.referer
    headers.putAll(link.headers)
    val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
    val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setPreferredAudioLanguage(null).setPreferredTextLanguage(null))
    }
    return ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
}

private fun buildMediaItem(link: ExtractorLink): MediaItem {
    val builder = MediaItem.Builder().setUri(link.url)
    DrmConfig.from(link)?.let { drm ->
        val drmBuilder = MediaItem.DrmConfiguration.Builder(drm.scheme)
            .setLicenseUri(android.net.Uri.parse(drm.licenseUrl))
            .setLicenseRequestHeaders(drm.licenseHeaders)
            .setMultiSession(true)
        drm.offlineKeySetId?.let(drmBuilder::setKeySetId)
        builder.setDrmConfiguration(drmBuilder.build())
    }
    return builder.build()
}
