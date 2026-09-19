package com.fmhub24.app.ui.screens.details

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fmhub24.app.plugins.cloudstream.*
import com.fmhub24.app.ui.theme.OrangeAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    url: String,
    apiName: String,
    viewModel: DetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (contentUrl: String, apiName: String, name: String, posterUrl: String?, episodeData: String?, episodeName: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(url, apiName) {
        viewModel.loadDetails(url, apiName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->

        when (val state = uiState) {
            is DetailsViewModel.DetailsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = OrangeAccent)
                        Text(
                            text = "Preparing details...",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            is DetailsViewModel.DetailsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(text = "Failed to load", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(text = "This title is temporarily unavailable. Please try again.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Button(
                            onClick = { viewModel.loadDetails(url, apiName) },
                            modifier = Modifier.padding(top = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            is DetailsViewModel.DetailsUiState.Success -> {
                val data = state.data
                val allEpisodes = when (data) {
                    is TvSeriesLoadResponse -> data.episodes
                    is AnimeLoadResponse -> data.episodes.values.flatten()
                    else -> emptyList()
                }
                val seasons = allEpisodes.map { it.season ?: 1 }.distinct().sorted()
                var selectedSeason by remember(data) { mutableStateOf(seasons.firstOrNull() ?: 1) }
                val visibleEpisodes = allEpisodes.filter { (it.season ?: 1) == selectedSeason }
                val firstEpisode = visibleEpisodes.firstOrNull() ?: allEpisodes.firstOrNull()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.Black)
                ) {
                    item {
                        // Screenshot-aligned top player stage. Playback itself remains in the
                        // real PlayerScreen; this stage never pretends that a link is loaded.
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                            AsyncImage(
                                model = data.posterUrl,
                                contentDescription = data.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black),
                                            startY = 120f
                                        )
                                    )
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.Center),
                                shape = RoundedCornerShape(50),
                                color = if (data is MovieLoadResponse || firstEpisode != null) OrangeAccent else Color(0xFF3A3A3A),
                                shadowElevation = 8.dp,
                                enabled = data is MovieLoadResponse || firstEpisode != null,
                                onClick = {
                                    if (data is MovieLoadResponse) {
                                        onNavigateToPlayer(data.url, data.apiName, data.name, data.posterUrl, data.dataUrl, null)
                                    } else if (firstEpisode != null) {
                                        onNavigateToPlayer(data.url, data.apiName, data.name, data.posterUrl ?: firstEpisode.posterUrl, firstEpisode.data, firstEpisode.name)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = if (data is MovieLoadResponse || firstEpisode != null) "Play" else "Playback unavailable",
                                    tint = if (data is MovieLoadResponse || firstEpisode != null) Color.Black else Color.Gray,
                                    modifier = Modifier.padding(18.dp).size(32.dp)
                                )
                            }
                            // Title and compact metadata overlay
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                            ) {
                                Text(
                                    text = data.name,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 27.sp,
                                    maxLines = 2,
                                )
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    data.year?.let { Text(it.toString(), color = Color.LightGray, fontSize = 12.sp) }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(OrangeAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = data.type.name, color = OrangeAccent, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = if (allEpisodes.isNotEmpty()) "${allEpisodes.size} episodes" else data.apiName, color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DetailAction(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = if (isFavorite) "Saved" else "Add to list",
                                    selected = isFavorite,
                                    onClick = { viewModel.toggleFavorite() },
                                    modifier = Modifier.weight(1f),
                                )
                                DetailAction(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    onClick = {
                                        context.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, data.url)
                                                },
                                                "Share ${data.name}"
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                DetailAction(
                                    icon = Icons.Default.Download,
                                    label = "Download",
                                    enabled = data is MovieLoadResponse || firstEpisode != null,
                                    onClick = {
                                        if (data is MovieLoadResponse) {
                                            onNavigateToPlayer(data.url, data.apiName, data.name, data.posterUrl, data.dataUrl, null)
                                        } else if (firstEpisode != null) {
                                            onNavigateToPlayer(data.url, data.apiName, data.name, data.posterUrl ?: firstEpisode.posterUrl, firstEpisode.data, firstEpisode.name)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (data !is MovieLoadResponse && firstEpisode == null) {
                                Text(
                                    text = "No playable episode is available from this source yet.",
                                    color = Color(0xFFFFB86B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))

                            if (allEpisodes.isNotEmpty()) {
                                Text(
                                    text = "Resources",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Available from ${data.apiName}",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {},
                                        enabled = false,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                    ) { Text("Audio: Auto", fontSize = 12.sp) }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(visibleEpisodes, key = { "chip-${it.data}" }) { episode ->
                                        FilterChip(
                                            selected = episode == firstEpisode,
                                            onClick = {
                                                onNavigateToPlayer(
                                                    data.url,
                                                    data.apiName,
                                                    data.name,
                                                    data.posterUrl ?: episode.posterUrl,
                                                    episode.data,
                                                    episode.name ?: "Episode ${episode.episode}",
                                                )
                                            },
                                            label = { Text("${episode.episode ?: 0}") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = OrangeAccent.copy(alpha = .25f),
                                                selectedLabelColor = OrangeAccent,
                                            ),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // Plot
                            data.plot?.let { plot ->
                                Text(
                                    text = "Plot",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = plot,
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Tags
                            data.tags?.let { tags ->
                                if (tags.isNotEmpty()) {
                                    Text(
                                        text = "Genres",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        tags.take(5).forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(text = tag, color = Color.Gray, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            // Play button for movies
                            when (data) {
                                is MovieLoadResponse -> {
                                    Button(
                                        onClick = {
                                            onNavigateToPlayer(data.url, data.apiName, data.name, data.posterUrl, data.dataUrl, null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                is TvSeriesLoadResponse, is AnimeLoadResponse -> {
                                    Text(
                                        text = "Episodes (${visibleEpisodes.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    if (seasons.size > 1) {
                                        SeasonSelector(
                                            seasons = seasons,
                                            selectedSeason = selectedSeason,
                                            onSeasonSelected = { selectedSeason = it }
                                        )
                                    }
                                }
                                else -> {
                                    // Generic LoadResponse - try to play if possible
                                    Button(
                                        onClick = {
                                            onNavigateToPlayer(data.url, data.apiName, data.name, data.posterUrl, data.url, null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Play", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Episodes list for the selected season.
                    if (allEpisodes.isNotEmpty()) {
                        items(visibleEpisodes) { episode ->
                            EpisodeItem(
                                episode = episode,
                                onClick = {
                                    onNavigateToPlayer(
                                        data.url,
                                        data.apiName,
                                        data.name,
                                        data.posterUrl ?: episode.posterUrl,
                                        episode.data,
                                        episode.name ?: "Episode ${episode.episode}"
                                    )
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) OrangeAccent.copy(alpha = .22f) else Color(0xFF242424),
            contentColor = if (selected) OrangeAccent else Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, maxLines = 1, fontSize = 11.sp)
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(text = "Season $selectedSeason", modifier = Modifier.weight(1f))
            Text(text = "▾", color = OrangeAccent)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = { Text("Season $season", color = Color.White) },
                    onClick = {
                        onSeasonSelected(season)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun EpisodeItem(
    episode: Episode,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(OrangeAccent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OrangeAccent)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name ?: "Episode ${episode.episode ?: ""}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (episode.season != null || episode.episode != null) {
                    Text(
                        text = "S${episode.season ?: 1} • E${episode.episode ?: ""}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                episode.description?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
