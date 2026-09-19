package com.fmhub24.app.ui.screens.home

import androidx.lifecycle.ViewModel
import com.fmhub24.app.data.repository.ContentRepository
import com.fmhub24.app.data.repository.ExtensionRepository
import com.fmhub24.app.plugins.PluginManager
import com.fmhub24.app.util.safeLaunch
import com.lagradost.cloudstream3.HomePageList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

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

        /** Providers exist but produced no home rows. */
        data class NoContent(val providerCount: Int, val reason: String?) : HomeUiState()
        data class Error(val message: String) : HomeUiState()
    }

    init {
        loadHomeContent()
    }

    fun loadHomeContent(forceRefresh: Boolean = false) {
        safeLaunch(onError = ::showError) {
            loadHomeContentInternal(forceRefresh)
        }
    }

    /**
     * Refresh the catalogue without starting a second ViewModel coroutine from inside the first
     * one. The old nested launch could leave Home in Loading while the refresh job was still
     * working, and a failed Supabase request was ignored even when cached providers were usable.
     */
    fun refresh() {
        safeLaunch(onError = ::showError) {
            _uiState.value = HomeUiState.Loading
            val remoteResult = extensionRepository.getActiveExtensions().first()
            remoteResult.fold(
                onSuccess = { remote ->
                    if (remote.isNotEmpty()) {
                        extensionRepository.syncFromRemote(remote)
                    } else {
                        extensionRepository.reloadFromCache()
                    }
                },
                onFailure = {
                    // Home must remain usable when the catalogue service is temporarily down.
                    // reloadFromCache() is also safe when there is no cache; the UI then shows a
                    // useful empty state rather than a blank screen.
                    extensionRepository.reloadFromCache()
                },
            )
            loadHomeContentInternal(forceRefresh = true)
        }
    }

    fun getProviderCount(): Int = pluginManager.getLoadedProviders().size

    private suspend fun loadHomeContentInternal(forceRefresh: Boolean) {
        _uiState.value = HomeUiState.Loading

        if (pluginManager.getLoadedProviders().isEmpty()) {
            // Re-load from disk rather than re-downloading: the splash screen already synced,
            // and a cold Compose navigation must not depend on the network.
            extensionRepository.reloadFromCache()
        }

        val providers = pluginManager.getLoadedProviders()
        if (providers.isEmpty()) {
            _uiState.value = HomeUiState.Empty
            return
        }

        contentRepository.getMainPageContent(forceRefresh = forceRefresh)
            .onSuccess { sections ->
                _uiState.value = if (sections.isEmpty()) {
                    HomeUiState.NoContent(
                        providerCount = providers.size,
                        reason = (pluginManager.loadingStateValue() as? PluginManager.LoadingState.Success)
                            ?.warnings
                            ?.firstOrNull(),
                    )
                } else {
                    HomeUiState.Success(sections)
                }
            }
            .onFailure { error ->
                _uiState.value = HomeUiState.Error(
                    error.message ?: "Could not load content from the providers",
                )
            }
    }

    private fun showError(error: Throwable) {
        _uiState.value = HomeUiState.Error(
            error.message ?: "Could not load content. Check your connection and try again.",
        )
    }
}
