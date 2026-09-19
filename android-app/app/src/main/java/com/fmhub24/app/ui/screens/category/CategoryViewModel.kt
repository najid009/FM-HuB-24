package com.fmhub24.app.ui.screens.category

import androidx.lifecycle.ViewModel
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.plugins.cloudstream.HomePageList
import com.fmhub24.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    sealed class UiState {
        object Loading : UiState()
        data class Success(val list: HomePageList) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun load(providerName: String, categoryName: String) {
        safeLaunch(onError = { error ->
            _uiState.value = UiState.Error(error.message ?: "Could not load this category")
        }) {
            _uiState.value = UiState.Loading
            contentRepository.getProviderCategory(providerName, categoryName)
                .onSuccess { _uiState.value = UiState.Success(it) }
                .onFailure { _uiState.value = UiState.Error(it.message ?: "Could not load this category") }
        }
    }
}
