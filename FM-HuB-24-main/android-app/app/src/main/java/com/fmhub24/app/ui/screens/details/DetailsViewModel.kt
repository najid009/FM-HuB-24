package com.fmhub24.app.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.data.repository.FavoriteRepository
import com.fmhub24.app.data.repository.WatchProgressRepository
import com.fmhub24.app.plugins.cloudstream.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fmhub24.app.util.safeLaunch

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val favoriteRepository: FavoriteRepository,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val _watchProgress = MutableStateFlow<Long?>(null)

    sealed class DetailsUiState {
        object Loading : DetailsUiState()
        data class Success(val data: LoadResponse) : DetailsUiState()
        data class Error(val message: String) : DetailsUiState()
    }

    fun loadDetails(url: String, apiName: String) {
        safeLaunch {
            _uiState.value = DetailsUiState.Loading

            // Check favorite
            launch {
                favoriteRepository.isFavorite(url).collect { fav ->
                    _isFavorite.value = fav
                }
            }

            // Check watch progress
            launch {
                watchProgressRepository.getProgressFlow(url).collect { progress ->
                    _watchProgress.value = progress?.position
                }
            }

            val result = contentRepository.loadContent(url, apiName)
            result.onSuccess { loadResponse ->
                _uiState.value = DetailsUiState.Success(loadResponse)
            }.onFailure { e ->
                _uiState.value = DetailsUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun toggleFavorite() {
        safeLaunch {
            val currentState = _uiState.value
            if (currentState is DetailsUiState.Success) {
                val data = currentState.data
                if (_isFavorite.value) {
                    favoriteRepository.removeFavorite(data.url)
                } else {
                    favoriteRepository.addFavoriteFromLoad(
                        url = data.url,
                        name = data.name,
                        posterUrl = data.posterUrl,
                        apiName = data.apiName,
                        type = data.type.name
                    )
                }
            }
        }
    }
}
