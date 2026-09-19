package com.fmhub24.app.ui.screens.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.repository.ExtensionRepository
import com.fmhub24.app.util.CrashLog
import dagger.hilt.android.qualifiers.ApplicationContext
import com.fmhub24.app.plugins.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fmhub24.app.util.safeLaunch

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionRepository: ExtensionRepository,
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState

    sealed class SplashUiState {
        object Loading : SplashUiState()
        data class Downloading(val name: String, val progress: Int) : SplashUiState()
        data class Success(
            val providerCount: Int,
            val warnings: List<String> = emptyList(),
            val partialErrors: List<PluginManager.LoadError> = emptyList(),
        ) : SplashUiState()

        /**
         * Nothing loaded. [detail] is the per-file reason from the loader — the whole point of
         * this screen is that it must never say only "failed", because that is undiagnosable
         * from a phone.
         */
        data class Error(val message: String, val detail: List<PluginManager.LoadError> = emptyList()) :
            SplashUiState()

        object Empty : SplashUiState()
    }

    /**
     * The tail of the previous launch's crash log, if there is one. Surfaced here because this screen
     * is the only thing guaranteed to be on screen when the app fails: an exception during DI or
     * Compose setup never reaches an error state anywhere else, and the alternative is the user
     * being told nothing at all.
     */
    private val _previousCrash = MutableStateFlow<String?>(null)
    val previousCrash: StateFlow<String?> = _previousCrash

    init {
        fetchAndLoadExtensions()
        safeLaunch(Dispatchers.IO) {
            _previousCrash.value = CrashLog.read(context)
        }
    }

    fun dismissCrashLog() {
        safeLaunch(Dispatchers.IO) {
            CrashLog.clear(context)
            _previousCrash.value = null
        }
    }

    fun fetchAndLoadExtensions() {
        safeLaunch {
            _uiState.value = SplashUiState.Loading
            extensionRepository.getActiveExtensions()
                .collect { result ->
                    result.onSuccess { remote ->
                        if (remote.isEmpty()) {
                            loadFromCacheOrEmpty()
                            return@onSuccess
                        }
                        val cached = extensionRepository.syncFromRemote(remote) { name, progress ->
                            _uiState.value = SplashUiState.Downloading(name, progress)
                        }
                        publishLoadResult(fallbackToCacheRows = cached)
                    }.onFailure { e ->
                        // Offline / backend down: cached files are still perfectly loadable.
                        try {
                            val cached = extensionRepository.getCachedExtensions()
                            if (cached.isEmpty()) {
                                // Never surface the exception text: Retrofit/Supabase errors name the
                                // backend and are meaningless (and unwanted) on the user's screen.
                                _uiState.value = SplashUiState.Error(
                                    "Could not connect. Check your internet and try again."
                                )
                            } else {
                                extensionRepository.reloadFromCache()
                                publishLoadResult(fallbackToCacheRows = cached, remoteUnavailable = e.message)
                            }
                        } catch (ex: Exception) {
                            _uiState.value = SplashUiState.Error(ex.message ?: "Unknown error")
                        }
                    }
                }
        }
    }

    private suspend fun loadFromCacheOrEmpty() {
        val cached = extensionRepository.getCachedExtensions()
        if (cached.isEmpty()) {
            _uiState.value = SplashUiState.Empty
        } else {
            extensionRepository.reloadFromCache()
            publishLoadResult(fallbackToCacheRows = cached)
        }
    }

    private fun publishLoadResult(
        fallbackToCacheRows: List<*> = emptyList<Any>(),
        remoteUnavailable: String? = null,
    ) {
        // One file, once per start: every loader reason lives here instead of on the screen.
        CrashLog.publishDiagnostics(context)

        when (val state = pluginManager.loadingStateValue()) {
            is PluginManager.LoadingState.Success -> {
                _uiState.value = SplashUiState.Success(
                    providerCount = state.providerCount,
                    warnings = state.warnings,
                    partialErrors = state.errors,
                )
            }

            is PluginManager.LoadingState.Error -> {
                val message = remoteUnavailable
                    ?.let { "$it\n(also: ${state.message})" }
                    ?: state.message
                _uiState.value = SplashUiState.Error(message, state.errors)
            }

            else -> {
                val count = pluginManager.getLoadedProviders().size
                _uiState.value = if (count > 0) {
                    SplashUiState.Success(count)
                } else {
                    SplashUiState.Error("No providers are loaded yet.")
                }
            }
        }
    }

    fun retry() {
        fetchAndLoadExtensions()
    }
}
