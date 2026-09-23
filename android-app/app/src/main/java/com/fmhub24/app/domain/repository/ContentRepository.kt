package com.fmhub24.app.domain.repository

import com.fmhub24.app.domain.model.Episode
import com.fmhub24.app.domain.model.MediaDetails
import com.fmhub24.app.domain.model.MediaItem
import com.fmhub24.app.domain.model.MediaPage
import com.fmhub24.app.domain.model.MediaSection
import com.fmhub24.app.domain.model.Stream
import com.fmhub24.app.domain.model.Subtitle

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val message: String, val retryable: Boolean = true) : RepositoryResult<Nothing>
}

interface ContentRepository {
    suspend fun home(page: Int = 1, forceRefresh: Boolean = false): RepositoryResult<List<MediaSection>>
    suspend fun search(query: String, page: Int = 1): RepositoryResult<MediaPage<MediaItem>>
    suspend fun details(id: String): RepositoryResult<MediaDetails>
    suspend fun episodes(id: String, season: Int, page: Int = 1): RepositoryResult<MediaPage<Episode>>
    suspend fun streams(episodeId: String): RepositoryResult<List<Stream>>
    suspend fun subtitles(episodeId: String): RepositoryResult<List<Subtitle>>
}

interface ProviderConfigurator {
    suspend fun configure(baseUrl: String): Boolean
}

interface CollectionRepository {
    suspend fun favorites(): List<MediaItem>
    suspend fun isFavorite(id: String): Boolean
    suspend fun toggleFavorite(item: MediaItem): Boolean
    suspend fun continueWatching(): List<MediaItem>
}

interface SettingsRepository {
    val providerBaseUrl: kotlinx.coroutines.flow.Flow<String>
    suspend fun setProviderBaseUrl(value: String)
}
