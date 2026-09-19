package com.fmhub24.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.plugins.cloudstream.SearchResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fmhub24.app.util.safeLaunch

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    sealed class SearchUiState {
        object Idle : SearchUiState()
        object Loading : SearchUiState()
        data class Success(val results: List<SearchResponse>) : SearchUiState()
        data class Error(val message: String) : SearchUiState()
        object Empty : SearchUiState()
    }

    init {
        safeLaunch {
            _query
                .debounce(500)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { q ->
                    search(q)
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _uiState.value = SearchUiState.Idle
        } else if (newQuery.length < 2) {
            _uiState.value = SearchUiState.Idle
        }
    }

    private fun search(q: String) {
        safeLaunch {
            _uiState.value = SearchUiState.Loading
            val result = contentRepository.searchAllProviders(q)
            result.onSuccess { list ->
                _uiState.value = if (list.isEmpty()) SearchUiState.Empty else SearchUiState.Success(list)
            }.onFailure { e ->
                _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun clearSearch() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }
}
