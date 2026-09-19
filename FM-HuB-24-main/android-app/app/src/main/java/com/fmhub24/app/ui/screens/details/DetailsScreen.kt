package com.fmhub24.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
                            text = "Loading real data via provider.load()...",
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
                        Text(text = state.message, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.Black)
                ) {
                    item {
                        // Poster header
                        Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
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
                                            startY = 200f
                                        )
                                    )
                            )
                            // Title overlay
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = data.name,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    data.year?.let {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = it.toString(), color = Color.White, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(OrangeAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = data.type.name, color = OrangeAccent, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = data.apiName, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(20.dp)) {
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
                                is TvSeriesLoadResponse -> {
                                    Text(
                                        text = "Episodes (${data.episodes.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }
                                is AnimeLoadResponse -> {
                                    Text(
                                        text = "Episodes",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
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

                    // Episodes list for series
                    when (data) {
                        is TvSeriesLoadResponse -> {
                            items(data.episodes) { episode ->
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
                        is AnimeLoadResponse -> {
                            val allEpisodes = data.episodes.values.flatten()
                            items(allEpisodes) { episode ->
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
                        else -> {}
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
