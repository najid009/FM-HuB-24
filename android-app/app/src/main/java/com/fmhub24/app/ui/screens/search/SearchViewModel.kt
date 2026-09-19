package com.fmhub24.app.ui.screens.search

import androidx.lifecycle.ViewModel
import com.fmhub24.app.data.aggregation.ContentDeduplicator
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.plugins.cloudstream.SearchResponse
import com.fmhub24.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
        safeLaunch(onError = { error ->
            _uiState.value = SearchUiState.Error(error.message ?: "Search failed")
        }) {
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

    fun setInitialQuery(value: String) {
        if (value.isNotBlank() && _query.value != value) _query.value = value
    }

    private suspend fun performSearch(query: String) {
        searchJob?.cancel()
        _uiState.value = SearchUiState.Loading
        val result = contentRepository.searchAllProviders(query)
        result.onSuccess { items ->
            val unique = ContentDeduplicator.dedupe(items)
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

    fun retry() {
        val current = _query.value.trim()
        if (current.length < 2) return
        safeLaunch(onError = { error ->
            _uiState.value = SearchUiState.Error(error.message ?: "Search failed")
        }) { performSearch(current) }
    }
}
