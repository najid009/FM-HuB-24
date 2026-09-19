package com.fmhub24.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.data.repository.ExtensionRepository
import com.fmhub24.app.plugins.PluginManager
import com.lagradost.cloudstream3.HomePageList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fmhub24.app.util.safeLaunch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val extensionRepository: ExtensionRepository,
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    sealed class HomeUiState {
        object Loading : HomeUiState()
        data class Success(val sections: List<Pair<String, HomePageList>>) : HomeUiState()
        object Empty : HomeUiState()

        /**
         * Providers exist but produced nothing, or none could be loaded. Carrying the reason
         * here is what makes the difference between "the app is broken" and "this provider is
         * down / not implemented".
         */
        data class NoContent(val providerCount: Int, val reason: String?) : HomeUiState()
        data class Error(val message: String) : HomeUiState()
    }

    init {
        loadHomeContent()
    }

    fun loadHomeContent() {
        safeLaunch {
            _uiState.value = HomeUiState.Loading

            if (pluginManager.getLoadedProviders().isEmpty()) {
                // Re-load from disk rather than re-downloading: the splash screen already synced,
                // and a cold Compose navigation must not depend on the network.
                extensionRepository.reloadFromCache()
            }

            val providers = pluginManager.getLoadedProviders()
            if (providers.isEmpty()) {
                _uiState.value = HomeUiState.Empty
                return@safeLaunch
            }

            val result = contentRepository.getMainPageContent()
            result.onSuccess { sections ->
                _uiState.value = if (sections.isEmpty()) {
                    HomeUiState.NoContent(
                        providerCount = providers.size,
                        reason = (pluginManager.loadingStateValue() as? PluginManager.LoadingState.Success)
                            ?.warnings?.firstOrNull()
                    )
                } else {
                    HomeUiState.Success(sections)
                }
            }.onFailure { e ->
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load content")
            }
        }
    }

    /** Pull the admin-selected list again (download only what changed) and rebuild content. */
    fun refresh() {
        safeLaunch {
            _uiState.value = HomeUiState.Loading
            extensionRepository.getActiveExtensions().collect { result ->
                result.onSuccess { remote ->
                    if (remote.isNotEmpty()) {
                        extensionRepository.syncFromRemote(remote)
                    } else {
                        extensionRepository.reloadFromCache()
                    }
                }
                loadHomeContent()
            }
        }
    }

    fun getProviderCount(): Int = pluginManager.getLoadedProviders().size
}
