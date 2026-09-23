package com.fmhub24.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fmhub24.app.data.local.DataStoreSettingsRepository
import com.fmhub24.app.domain.model.MediaDetails
import com.fmhub24.app.domain.model.MediaItem
import com.fmhub24.app.domain.model.MediaKind
import com.fmhub24.app.domain.model.MediaSection
import com.fmhub24.app.domain.repository.CollectionRepository
import com.fmhub24.app.domain.repository.ContentRepository
import com.fmhub24.app.domain.repository.ProviderConfigurator
import com.fmhub24.app.domain.repository.RepositoryResult
import com.fmhub24.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Accent = Color(0xFFFF6B35)
private val Cyan = Color(0xFF5EE7FF)
private val Background = Color(0xFF070B12)
private val Panel = Color(0xFF101722)
private val Muted = Color(0xFF94A3B8)

private enum class AppPage { HOME, SEARCH, MY_LIST, DOWNLOADS, SETTINGS, ABOUT, DETAILS, PLAYER }

class AppViewModel(
    private val content: ContentRepository,
    private val collections: CollectionRepository,
    private val settings: SettingsRepository,
    private val configurator: ProviderConfigurator,
) : ViewModel() {
    private val _home = MutableStateFlow<List<MediaSection>>(emptyList())
    val home: StateFlow<List<MediaSection>> = _home.asStateFlow()
    private val _search = MutableStateFlow<List<MediaItem>>(emptyList())
    val search: StateFlow<List<MediaItem>> = _search.asStateFlow()
    private val _favorites = MutableStateFlow<List<MediaItem>>(emptyList())
    val favorites: StateFlow<List<MediaItem>> = _favorites.asStateFlow()
    private val _details = MutableStateFlow<MediaDetails?>(null)
    val details: StateFlow<MediaDetails?> = _details.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    val providerUrl: StateFlow<String> = MutableStateFlow("").asStateFlow()

    init { loadHome() }

    fun loadHome() = viewModelScope.launch {
        when (val result = content.home(forceRefresh = true)) {
            is RepositoryResult.Success -> { _home.value = result.value; _message.value = null }
            is RepositoryResult.Failure -> _message.value = result.message
        }
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.trim().length < 2) { _search.value = emptyList(); return@launch }
        when (val result = content.search(query)) {
            is RepositoryResult.Success -> _search.value = result.value.items
            is RepositoryResult.Failure -> _message.value = result.message
        }
    }

    fun openDetails(item: MediaItem) = viewModelScope.launch {
        when (val result = content.details(item.id)) {
            is RepositoryResult.Success -> _details.value = result.value
            is RepositoryResult.Failure -> { _details.value = MediaDetails(item); _message.value = result.message }
        }
    }

    fun toggleFavorite(item: MediaItem) = viewModelScope.launch { collections.toggleFavorite(item); _favorites.value = collections.favorites() }
    fun loadFavorites() = viewModelScope.launch { _favorites.value = collections.favorites() }
    fun clearMessage() { _message.value = null }
    fun configureProvider(url: String) = viewModelScope.launch {
        settings.setProviderBaseUrl(url)
        if (configurator.configure(url)) loadHome() else _message.value = "Provider could not be configured. Check the URL and native library."
    }
}

@Composable
fun AppRoot(container: com.fmhub24.app.data.AppContainer) {
    val vm: AppViewModel = viewModel { AppViewModel(container.contentRepository, container.collectionRepository, container.settingsRepository, container.nativeContentRepository) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var query by remember { mutableStateOf("") }
    val message by vm.message.collectAsStateCompat()

    Scaffold(containerColor = Background, bottomBar = {
        if (page !in setOf(AppPage.DETAILS, AppPage.PLAYER)) BottomNav(page) { page = it }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                AppPage.HOME -> HomePage(vm, onOpen = { selected = it; vm.openDetails(it); page = AppPage.DETAILS }, onSearch = { page = AppPage.SEARCH })
                AppPage.SEARCH -> SearchPage(query, { query = it; vm.search(it) }, vm.search.collectAsStateCompat().value) { selected = it; vm.openDetails(it); page = AppPage.DETAILS }
                AppPage.MY_LIST -> MyListPage(vm, onOpen = { selected = it; vm.openDetails(it); page = AppPage.DETAILS })
                AppPage.DOWNLOADS -> DownloadsPage()
                AppPage.SETTINGS -> SettingsPage(vm) { page = AppPage.ABOUT }
                AppPage.ABOUT -> AboutPage { page = AppPage.SETTINGS }
                AppPage.DETAILS -> DetailsPage(vm.details.collectAsStateCompat().value, onBack = { page = AppPage.HOME }, onPlay = { page = AppPage.PLAYER }, onFavorite = { selected?.let(vm::toggleFavorite) })
                AppPage.PLAYER -> PlayerPage(vm.details.collectAsStateCompat().value, onBack = { page = AppPage.DETAILS })
            }
            message?.let { text ->
                Card(Modifier.align(Alignment.BottomCenter).padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C18))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)); TextButton(onClick = vm::clearMessage) { Text("Dismiss", color = Accent) } }
                }
            }
        }
    }
}

@Composable private fun BottomNav(page: AppPage, onPage: (AppPage) -> Unit) {
    NavigationBar(containerColor = Panel, modifier = Modifier.navigationBarsPadding()) {
        listOf(AppPage.HOME to "⌂\nHome", AppPage.SEARCH to "⌕\nSearch", AppPage.MY_LIST to "＋\nMy list", AppPage.DOWNLOADS to "↓\nDownloads", AppPage.SETTINGS to "⚙\nSettings").forEach { (target, label) ->
            NavigationBarItem(selected = page == target, onClick = { onPage(target) }, icon = { Text(label, fontSize = 12.sp, lineHeight = 14.sp) }, label = null)
        }
    }
}

@Composable private fun PageHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Text("‹", color = Color.White, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        Column { Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold); subtitle?.let { Text(it, color = Muted, fontSize = 12.sp) } }
    }
}

@Composable private fun HomePage(vm: AppViewModel, onOpen: (MediaItem) -> Unit, onSearch: () -> Unit) {
    val sections by vm.home.collectAsStateCompat()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("Discover", "Your next great watch") }
        item { SearchBar("Search movies, series and people", onSearch) }
        item { HeroCard(sections.firstOrNull()?.items?.firstOrNull(), onOpen) }
        if (sections.isEmpty()) item { EmptyState("Connect a provider in Settings to load your catalogue.") }
        sections.forEach { section -> item { SectionRow(section, onOpen) } }
    }
}

@Composable private fun SearchPage(query: String, onQuery: (String) -> Unit, results: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize()) { PageHeader("Search", "Find something worth watching"); OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Movie, series or episode") }, singleLine = true); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(results) { SearchResult(it, onOpen) } }; if (query.length > 1 && results.isEmpty()) EmptyState("No matching titles found.") }
}

@Composable private fun MyListPage(vm: AppViewModel, onOpen: (MediaItem) -> Unit) { val list by vm.favorites.collectAsStateCompat(); LaunchedEffect(Unit) { vm.loadFavorites() }; Column(Modifier.fillMaxSize()) { PageHeader("My list", "Your saved titles"); if (list.isEmpty()) EmptyState("Save a title from its details page to see it here.") else LazyColumn(contentPadding = PaddingValues(16.dp)) { items(list) { SearchResult(it, onOpen) } } } }
@Composable private fun DownloadsPage() { Column(Modifier.fillMaxSize()) { PageHeader("Downloads", "Offline library"); EmptyState("Downloads will appear here when you save a title for offline viewing.") } }
@Composable private fun SettingsPage(vm: AppViewModel, onAbout: () -> Unit) { var url by remember { mutableStateOf("") }; Column(Modifier.fillMaxSize().padding(16.dp)) { PageHeader("Settings", "Make FM HuB 24 yours"); Text("Provider source", color = Color.White, fontWeight = FontWeight.Bold); Text("The app uses a typed native provider. No extension packages or executable plugins are installed.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)); OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Provider base URL") }, placeholder = { Text("https://your-provider.example/api") }, singleLine = true); Button({ vm.configureProvider(url) }, Modifier.padding(top = 12.dp)) { Text("Save and refresh") }; Spacer(Modifier.height(28.dp)); Text("About", color = Color.White, fontWeight = FontWeight.Bold); Text("FM HuB 24 · clean native provider architecture · v3.0", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)); TextButton(onAbout) { Text("About us", color = Accent) } } }

@Composable private fun AboutPage(onBack: () -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) { item { PageHeader("About us", "What you should know", onBack) }; item { Card(Modifier.padding(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(18.dp)) { Text("FM HuB 24", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("A native catalogue and playback client.", color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)); Text("FM HuB 24 uses external content providers to retrieve catalogue, playback, and subtitle information. Availability depends on provider authorization, regional availability, and network conditions.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp)); Text("The app does not install executable extension packages. Provider responses are validated through a typed Rust boundary before they reach the UI.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)); Text("Your provider settings and local collections are handled by the app. Do not enter credentials unless you trust the provider endpoint.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)); Text("Version 3.0.0", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp)) } } } }

@Composable private fun DetailsPage(details: MediaDetails?, onBack: () -> Unit, onPlay: () -> Unit, onFavorite: () -> Unit) { if (details == null) { EmptyState("Loading details…", onBack); return }; LazyColumn(Modifier.fillMaxSize()) { item { DetailsHero(details.item, onBack, onPlay, onFavorite) }; item { Text(details.item.description ?: "No description available.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(18.dp)); Text(details.genres.joinToString("  ·  ").ifBlank { "Details" }, color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp)); if (details.seasons.isNotEmpty()) Text("Seasons", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp)); details.seasons.forEach { season -> Text("Season ${season.number}", color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)); season.episodes.forEach { episode -> Text("Episode ${episode.number} · ${episode.title ?: "Untitled"}", color = Muted, modifier = Modifier.padding(horizontal = 28.dp, vertical = 5.dp)) } } } } }
@Composable private fun PlayerPage(details: MediaDetails?, onBack: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader("Now playing", details?.item?.title, onBack); Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.linearGradient(listOf(Color(0xFF17304A), Color(0xFF5B2E43)))), contentAlignment = Alignment.Center) { Text("▶", color = Accent, fontSize = 58.sp) }; Text("Playback source will appear here after the provider resolves a stream.", color = Muted, modifier = Modifier.padding(18.dp)); Spacer(Modifier.height(12.dp)); Text("━━━●━━━━━━━━", color = Accent, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Text("↶", fontSize = 28.sp, color = Color.White, modifier = Modifier.padding(18.dp)); Text("▶", fontSize = 42.sp, color = Accent, modifier = Modifier.padding(12.dp)); Text("↷", fontSize = 28.sp, color = Color.White, modifier = Modifier.padding(18.dp)) } } }

@Composable private fun HeroCard(item: MediaItem?, onOpen: (MediaItem) -> Unit) { Box(Modifier.fillMaxWidth().padding(16.dp).height(230.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF39212A), Color(0xFF152F42)))).clickable(enabled = item != null) { item?.let(onOpen) }) { Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) { Text("FEATURED TONIGHT", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(item?.title ?: "Your catalogue awaits", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text(item?.year?.toString() ?: "Configure a provider source", color = Muted, fontSize = 12.sp); if (item != null) Button({ onOpen(item) }, Modifier.padding(top = 8.dp)) { Text("Watch now") } } } }
@Composable private fun SectionRow(section: MediaSection, onOpen: (MediaItem) -> Unit) { Column(Modifier.padding(top = 8.dp)) { Text(section.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(section.items) { PosterCard(it, onOpen) } } } }
@Composable private fun PosterCard(item: MediaItem, onOpen: (MediaItem) -> Unit) { Column(Modifier.width(116.dp).clickable { onOpen(item) }) { Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF27405D), Color(0xFF812E3C))))) { Text(item.kind.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) }; Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp)); Text(item.year?.toString() ?: "", color = Muted, fontSize = 11.sp) } }
@Composable private fun SearchResult(item: MediaItem, onOpen: (MediaItem) -> Unit) { Card(Modifier.fillMaxWidth().clickable { onOpen(item) }, colors = CardDefaults.cardColors(containerColor = Panel)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp, 76.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF27405D), Color(0xFF812E3C))))); Column(Modifier.padding(start = 12.dp)) { Text(item.title, color = Color.White, fontWeight = FontWeight.Bold); Text("${item.year ?: ""}  ·  ${item.kind.name.lowercase()}", color = Muted, fontSize = 12.sp) } } } }
@Composable private fun DetailsHero(item: MediaItem, onBack: () -> Unit, onPlay: () -> Unit, onFavorite: () -> Unit) { Box(Modifier.fillMaxWidth().height(330.dp).background(Brush.linearGradient(listOf(Color(0xFF39212A), Color(0xFF172D43))))) { Text("‹", color = Color.White, fontSize = 32.sp, modifier = Modifier.padding(18.dp).clickable(onClick = onBack)); Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) { Text(item.kind.name, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(item.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold); Row { Button(onPlay) { Text("▶ Play") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onFavorite) { Text("＋ List") } } } } }
@Composable private fun SearchBar(placeholder: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel)) { Text("⌕  $placeholder", color = Muted, modifier = Modifier.padding(15.dp)) } }
@Composable private fun EmptyState(message: String, onBack: (() -> Unit)? = null) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("○", color = Cyan, fontSize = 36.sp); Text(message, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)); onBack?.let { TextButton(it) { Text("Go back", color = Accent) } } } }

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> = this.collectAsState()
