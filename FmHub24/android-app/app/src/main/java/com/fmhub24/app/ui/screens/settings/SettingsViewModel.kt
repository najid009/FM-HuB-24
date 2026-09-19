package com.fmhub24.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.local.entity.CachedExtension
import com.fmhub24.app.data.repository.ExtensionRepository
import com.fmhub24.app.data.repository.SettingsRepository
import com.fmhub24.app.plugins.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fmhub24.app.util.safeLaunch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val extensionRepository: ExtensionRepository,
    private val pluginManager: PluginManager,
) : ViewModel() {

    val resumePlayback: StateFlow<Boolean> = settingsRepository.resumePlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showAdult: StateFlow<Boolean> = settingsRepository.showAdult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onResumePlaybackChange(value: Boolean) {
        safeLaunch { settingsRepository.setResumePlayback(value) }
    }

    fun onShowAdultChange(value: Boolean) {
        safeLaunch { settingsRepository.setShowAdult(value) }
    }

    /** Every downloaded extension, with the user's own enabled switch persisted in Room. */
    val extensions: StateFlow<List<CachedExtension>> = extensionRepository.observeCachedExtensions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadState: StateFlow<PluginManager.LoadingState> = pluginManager.loadingState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PluginManager.LoadingState.Idle)

    val providers: StateFlow<List<PluginManager.ProviderEntry>> = pluginManager.providerEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(id: String, enabled: Boolean) {
        safeLaunch { extensionRepository.setExtensionEnabled(id, enabled) }
    }

    /** Re-pull the admin's list (repo syncs included) and reload. */
    fun syncFromAdmin() {
        safeLaunch {
            extensionRepository.getActiveExtensions().collect { result ->
                result.onSuccess { remote ->
                    if (remote.isNotEmpty()) extensionRepository.syncFromRemote(remote)
                    else extensionRepository.reloadFromCache()
                }
            }
        }
    }

    fun reloadFromDisk() {
        safeLaunch { extensionRepository.reloadFromCache() }
    }

    fun clearExtensions() {
        safeLaunch { extensionRepository.clearAll() }
    }
}
