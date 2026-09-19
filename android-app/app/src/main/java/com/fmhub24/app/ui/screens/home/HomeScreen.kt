package com.fmhub24.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fmhub24.app.ui.components.ContentCard
import com.fmhub24.app.data.aggregation.ContentDeduplicator
import com.fmhub24.app.ui.theme.OrangeAccent
import com.fmhub24.app.ui.util.yearOrNull
import com.lagradost.cloudstream3.HomePageList

private val HomeBackground = Color(0xFF0E1014)
private val SurfaceDark = Color(0xFF1B1E24)
private val Mint = Color(0xFF24F39A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetails: (String, String) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (String, String) -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        containerColor = HomeBackground,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF20252B), contentColor = Color.White) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Color.White, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToDownloads,
                    icon = { Icon(Icons.Default.Download, "Downloads") },
                    label = { Text("Downloads") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.White, unselectedTextColor = Color.LightGray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToFavorites,
                    icon = { Icon(Icons.Default.FavoriteBorder, "Favorites") },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.White, unselectedTextColor = Color.LightGray, indicatorColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        when (val state = uiState) {
            HomeViewModel.HomeUiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            HomeViewModel.HomeUiState.Empty -> EmptyState(Modifier.fillMaxSize().padding(padding), viewModel)
            is HomeViewModel.HomeUiState.NoContent -> EmptyState(
                Modifier.fillMaxSize().padding(padding),
                viewModel,
                state.reason,
            )
            is HomeViewModel.HomeUiState.Error -> ErrorState(Modifier.fillMaxSize().padding(padding), state.message, viewModel)
            is HomeViewModel.HomeUiState.Success -> {
                val featured = state.sections.asSequence().flatMap { it.second.list.asSequence() }.firstOrNull()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).background(HomeBackground),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        HomeHeader(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { onNavigateToSearch(query.trim()) },
                            onSettings = onNavigateToSettings,
                            onRefresh = { viewModel.refresh() },
                        )
                    }
                    item { HomeTabs(state.sections, onNavigateToCategory) }
                    if (featured != null) {
                        item {
                            FeaturedBanner(
                                item = featured,
                                onClick = { onNavigateToDetails(featured.url, featured.apiName) }
                            )
                        }
                    }
                    items(state.sections, key = { "${it.first}:${it.second.name}" }) { (providerName, homeList) ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    homeList.name,
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onNavigateToCategory(providerName, homeList.name) }) {
                                    Text("All  ›", color = Color.LightGray, fontSize = 14.sp)
                                }
                            }
                            ProviderChips(providerName, state.sections, onNavigateToCategory)
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(homeList.list, key = { ContentDeduplicator.key(it) }) { item ->
                                    ContentCard(item = item) { onNavigateToDetails(item.url, item.apiName) }
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
private fun HomeHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Mint),
                contentAlignment = Alignment.Center
            ) { Text("FM", color = Color(0xFF07120D), fontWeight = FontWeight.Black, fontSize = 14.sp) }
            Spacer(Modifier.width(12.dp))
            Text("FMHuB24", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh catalogue", tint = Color.White) }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF81776F)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(25.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search movies, series...", color = Color.White.copy(alpha = .85f), fontSize = 16.sp)
                    inner()
                }
            )
            TextButton(onClick = onSearch) { Text("Search", color = Mint, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun HomeTabs(
    sections: List<Pair<String, HomePageList>>,
    onNavigateToCategory: (String, String) -> Unit,
) {
    val tabs = listOf("Trending", "Movies", "Anime", "Series")
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        items(tabs) { tab ->
            val target = findCategoryTarget(sections, tab)
            Text(
                text = tab,
                color = if (target != null) Color.White else Color.DarkGray,
                fontSize = 17.sp,
                fontWeight = if (tab == "Trending") FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable(enabled = target != null) {
                    target?.let { onNavigateToCategory(it.first, tab) }
                    }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FeaturedBanner(item: com.fmhub24.app.plugins.cloudstream.SearchResponse, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(275.dp).padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
    ) {
        AsyncImage(model = item.posterUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.yearOrNull ?: ""}  •  ${item.type?.name ?: "Series"}", color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            }
            FloatingActionButton(onClick = onClick, containerColor = Mint, contentColor = Color.Black, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.PlayArrow, "Play")
            }
        }
    }
}

@Composable
private fun ProviderChips(
    providerName: String,
    sections: List<Pair<String, HomePageList>>,
    onNavigateToCategory: (String, String) -> Unit,
) {
    val categories = sections
        .filter { it.first == providerName }
        .map { it.second.name }
        .distinct()
    if (categories.size < 2) return

    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(categories) { label ->
            Surface(
                modifier = Modifier.clickable {
                    onNavigateToCategory(providerName, label)
                },
                shape = RoundedCornerShape(22.dp),
                color = if (label == categories.first()) Color(0xFF30363D) else Color.Transparent,
            ) {
                Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

private fun findCategoryTarget(
    sections: List<Pair<String, HomePageList>>,
    tab: String,
): Pair<String, HomePageList>? {
    if (sections.isEmpty()) return null
    if (tab == "Trending") return sections.firstOrNull()

    val terms = when (tab) {
        "Movies" -> listOf("movie", "film")
        "Anime" -> listOf("anime", "animation")
        "Series" -> listOf("tv", "series", "show", "drama")
        else -> listOf(tab.lowercase())
    }
    return sections.firstOrNull { (_, list) ->
        terms.any { term -> list.name.lowercase().contains(term) }
    } ?: sections.firstOrNull()
}

@Composable private fun LoadingState(modifier: Modifier) = Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = OrangeAccent) }

@Composable private fun EmptyState(modifier: Modifier, viewModel: HomeViewModel, reason: String? = null) = Box(modifier, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text("Your catalogue is empty", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Content sources may still be loading. Refresh and try again.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        reason?.let { Text(it, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent), modifier = Modifier.padding(top = 16.dp)) { Text("Refresh sources") }
    }
}

@Composable private fun ErrorState(modifier: Modifier, message: String, viewModel: HomeViewModel) = Box(modifier, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text("We could not refresh your catalogue", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Check your connection and try again.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent), modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
    }
}
