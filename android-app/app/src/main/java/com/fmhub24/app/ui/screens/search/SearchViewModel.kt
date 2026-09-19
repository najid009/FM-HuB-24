package com.fmhub24.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.plugins.cloudstream.SearchResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState
    private var searchJob: Job? = null

    sealed class SearchUiState {
        object Idle : SearchUiState()
        object Loading : SearchUiState()
        data class Success(val results: List<SearchResponse>) : SearchUiState()
        data class Error(val message: String) : SearchUiState()
        object Empty : SearchUiState()
    }

    init {
        viewModelScope.launch {
            _query
                .debounce(350)
                .distinctUntilChanged()
                .filter { it.trim().length >= 2 }
                .collectLatest { query -> performSearch(query.trim()) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.trim().length < 2) {
            searchJob?.cancel()
            _uiState.value = if (newQuery.isBlank()) SearchUiState.Idle else SearchUiState.Idle
        }
    }

    private suspend fun performSearch(query: String) {
        searchJob?.cancel()
        _uiState.value = SearchUiState.Loading
        val result = contentRepository.searchAllProviders(query)
        result.onSuccess { items ->
            val unique = items.distinctBy { "${it.apiName}:${it.url}" }
            _uiState.value = if (unique.isEmpty()) SearchUiState.Empty else SearchUiState.Success(unique)
        }.onFailure { error ->
            _uiState.value = SearchUiState.Error(error.message ?: "Search failed")
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }
}
