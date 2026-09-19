package com.fmhub24.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.data.repository.DownloadRepository
import com.fmhub24.app.data.repository.SettingsRepository
import com.fmhub24.app.data.repository.WatchProgressRepository
import com.fmhub24.app.media.DrmConfig
import com.fmhub24.app.plugins.cloudstream.AnimeLoadResponse
import com.fmhub24.app.plugins.cloudstream.Episode
import com.fmhub24.app.plugins.cloudstream.ExtractorLink
import com.fmhub24.app.plugins.cloudstream.ExtractorLinkType
import com.fmhub24.app.plugins.cloudstream.SearchResponse
import com.fmhub24.app.plugins.cloudstream.SubtitleFile
import com.fmhub24.app.plugins.cloudstream.TvSeriesLoadResponse
import com.fmhub24.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadRepository: DownloadRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState
    private val _downloadState = MutableStateFlow<String?>(null)
    val downloadState: StateFlow<String?> = _downloadState

    val resumeEnabled: StateFlow<Boolean> = settingsRepository.resumePlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    sealed class PlayerUiState {
        object Loading : PlayerUiState()
        data class Success(
            val links: List<ExtractorLink>,
            val subtitles: List<SubtitleFile>,
            val selectedLink: ExtractorLink? = null,
            val recommendations: List<SearchResponse> = emptyList()
        ) : PlayerUiState()
        data class Error(val message: String) : PlayerUiState()
    }

    data class EpisodeTarget(val data: String, val name: String)

    private var loadJob: Job? = null
    private var currentUrl = ""
    private var currentApiName = ""
    private var currentName = ""
    private var currentPoster: String? = null
    private var currentEpisodeData: String? = null
    private var currentEpisodeName: String? = null
    private var episodeQueue: List<Episode> = emptyList()

    fun loadLinks(
        url: String,
        apiName: String,
        name: String,
        posterUrl: String?,
        episodeData: String?,
        episodeName: String?
    ) {
        currentUrl = url
        currentApiName = apiName
        currentName = name
        currentPoster = posterUrl
        currentEpisodeData = episodeData
        currentEpisodeName = episodeName
        loadJob?.cancel()
        loadJob = safeLaunch {
            _uiState.value = PlayerUiState.Loading
            launch { loadEpisodeQueue() }
            val recommendations = loadRecommendations()

            val links = mutableListOf<ExtractorLink>()
            val subtitles = mutableListOf<SubtitleFile>()
            val result = contentRepository.loadLinks(
                data = episodeData ?: url,
                apiName = apiName,
                subtitleCallback = { subtitles.add(it) },
                linkCallback = { links.add(it) }
            )
            result.onSuccess {
                val playable = links
                    .filter { it.type != ExtractorLinkType.TORRENT && it.type != ExtractorLinkType.MAGNET }
                    .distinctBy { "${it.type}:${it.url}" }
                if (playable.isEmpty()) {
                    _uiState.value = PlayerUiState.Error(
                        if (links.isEmpty()) "No playable links found"
                        else "Only unsupported download links were returned (${links.size})."
                    )
                } else {
                    val sorted = playable.sortedWith(
                        compareByDescending<ExtractorLink> { it.quality }.thenBy { it.name }
                    )
                    _uiState.value = PlayerUiState.Success(
                        links = sorted,
                        subtitles = subtitles,
                        selectedLink = sorted.firstOrNull(),
                        recommendations = recommendations
                    )
                }
            }.onFailure { error ->
                _uiState.value = PlayerUiState.Error(error.message ?: "Failed to load links")
            }
        }
    }

    private suspend fun loadEpisodeQueue() {
        val response = contentRepository.loadContent(currentUrl, currentApiName).getOrNull()
        episodeQueue = when (response) {
            is TvSeriesLoadResponse -> response.episodes
            is AnimeLoadResponse -> response.episodes.values.flatten()
            else -> emptyList()
        }.filter { it.data.isNotBlank() }
            .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
    }

    private suspend fun loadRecommendations(): List<SearchResponse> {
        return contentRepository.getMainPageContent()
            .getOrDefault(emptyList())
            .flatMap { it.second.list }
            .filter { it.url != currentUrl }
            .distinctBy { "${it.apiName}:${it.url}" }
            .take(12)
    }

    fun selectLink(link: ExtractorLink) {
        val state = _uiState.value
        if (state is PlayerUiState.Success) _uiState.value = state.copy(selectedLink = link)
    }

    /**
     * Tries the next provider/quality link after a playback failure. This only
     * walks links already returned by the authorized provider; it does not
     * re-resolve or bypass protected streams.
     */
    fun nextFallback(failed: ExtractorLink): ExtractorLink? {
        val state = _uiState.value as? PlayerUiState.Success ?: return null
        val failedIndex = state.links.indexOfFirst { it.url == failed.url }
        val next = state.links
            .drop((failedIndex + 1).coerceAtLeast(0))
            .firstOrNull { it.url != failed.url }
            ?: return null
        _uiState.value = state.copy(selectedLink = next)
        return next
    }

    fun downloadSelected(link: ExtractorLink) {
        safeLaunch {
            _downloadState.value = "Adding to downloads…"
            downloadRepository.enqueue(
                sourceUrl = link.url,
                name = currentName,
                posterUrl = currentPoster,
                apiName = currentApiName,
                episodeName = currentEpisodeName,
                streamType = link.type,
                headers = link.headers,
                referer = link.referer,
                drm = DrmConfig.from(link),
            ).onSuccess { _downloadState.value = "Added to downloads" }
                .onFailure { _downloadState.value = it.message ?: "Download failed" }
        }
    }

    fun nextEpisodeTarget(): EpisodeTarget? {
        val currentData = currentEpisodeData ?: return null
        val index = episodeQueue.indexOfFirst { it.data == currentData }
        val next = episodeQueue.getOrNull(index + 1) ?: return null
        return EpisodeTarget(next.data, next.name ?: "Episode ${next.episode ?: ""}")
    }

    fun saveProgress(position: Long, duration: Long) {
        safeLaunch {
            watchProgressRepository.saveProgress(
                url = currentUrl,
                name = currentName,
                posterUrl = currentPoster,
                apiName = currentApiName,
                position = position,
                duration = duration,
                episodeData = currentEpisodeData,
                episodeName = currentEpisodeName
            )
        }
    }

    suspend fun getResumePosition(): Long {
        val progress = watchProgressRepository.getProgress(currentUrl)
        return if (currentEpisodeData != null && progress?.episodeData != currentEpisodeData) 0L
        else progress?.position ?: 0L
    }
}
