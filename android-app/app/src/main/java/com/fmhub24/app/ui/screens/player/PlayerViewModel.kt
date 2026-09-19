package com.fmhub24.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.data.repository.SettingsRepository
import com.fmhub24.app.data.repository.WatchProgressRepository
import com.fmhub24.app.plugins.cloudstream.ExtractorLink
import com.fmhub24.app.plugins.cloudstream.ExtractorLinkType
import com.fmhub24.app.plugins.cloudstream.SubtitleFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fmhub24.app.util.safeLaunch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val watchProgressRepository: WatchProgressRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState

    /** Mirrors the persisted "Resume playback" setting from Settings. */
    val resumeEnabled: StateFlow<Boolean> = settingsRepository.resumePlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    sealed class PlayerUiState {
        object Loading : PlayerUiState()
        data class Success(
            val links: List<ExtractorLink>,
            val subtitles: List<SubtitleFile>,
            val selectedLink: ExtractorLink? = null
        ) : PlayerUiState()
        data class Error(val message: String) : PlayerUiState()
    }

    private var currentUrl: String = ""
    private var currentApiName: String = ""
    private var currentName: String = ""
    private var currentPoster: String? = null
    private var currentEpisodeData: String? = null
    private var currentEpisodeName: String? = null

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

        safeLaunch {
            _uiState.value = PlayerUiState.Loading

            val dataToLoad = episodeData ?: url
            val links = mutableListOf<ExtractorLink>()
            val subtitles = mutableListOf<SubtitleFile>()

            val result = contentRepository.loadLinks(
                data = dataToLoad,
                apiName = apiName,
                subtitleCallback = { sub -> subtitles.add(sub) },
                linkCallback = { link -> links.add(link) }
            )

            result.onSuccess {
                // TORRENT links carry a magnet/info-hash in `url`; ExoPlayer cannot open them, so
                // they must not be auto-selected — that is what looks like "player shows black
                // screen" when a provider returns one first.
                val playable = links.filter { it.type != ExtractorLinkType.TORRENT }
                if (playable.isEmpty()) {
                    _uiState.value = PlayerUiState.Error(
                        if (links.isEmpty()) {
                            "No playable links found via loadLinks()"
                        } else {
                            "Only torrent/magnet links returned (${links.size}) — the built-in player cannot open them. Pick another provider or an external player."
                        }
                    )
                } else {
                    // Sort by quality descending
                    val sorted = playable.sortedByDescending { it.quality }
                    _uiState.value = PlayerUiState.Success(
                        links = sorted,
                        subtitles = subtitles,
                        selectedLink = sorted.firstOrNull()
                    )
                }
            }.onFailure { e ->
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to load links")
            }
        }
    }

    fun selectLink(link: ExtractorLink) {
        val current = _uiState.value
        if (current is PlayerUiState.Success) {
            _uiState.value = current.copy(selectedLink = link)
        }
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
        // If episode-specific, check episode data
        return if (currentEpisodeData != null) {
            // For episodes, we save progress per main url + episodeData
            // Simplified: return progress if episode matches
            if (progress?.episodeData == currentEpisodeData) progress?.position ?: 0L else 0L
        } else {
            progress?.position ?: 0L
        }
    }
}
