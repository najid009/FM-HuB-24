package com.fmhub24.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.app.data.local.entity.DownloadedContent
import com.fmhub24.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            while (isActive) {
                downloadRepository.syncStatuses()
                delay(1500)
            }
        }
    }
    val downloads: StateFlow<List<DownloadedContent>> = downloadRepository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(item: DownloadedContent) {
        viewModelScope.launch { downloadRepository.delete(item) }
    }
}
