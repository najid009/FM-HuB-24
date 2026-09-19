package com.fmhub24.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.local.entity.DownloadedContent
import com.fmhub24.app.data.repository.DownloadRepository
import com.fmhub24.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    init {
        safeLaunch {
            while (isActive) {
                try {
                    downloadRepository.syncStatuses()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A stopped/removed download must not kill the polling loop.
                }
                delay(1500)
            }
        }
    }
    val downloads: StateFlow<List<DownloadedContent>> = downloadRepository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(item: DownloadedContent) {
        safeLaunch { downloadRepository.delete(item) }
    }
}
