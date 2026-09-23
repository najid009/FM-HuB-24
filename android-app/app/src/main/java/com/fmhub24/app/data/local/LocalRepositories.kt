package com.fmhub24.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fmhub24.app.domain.model.MediaItem
import com.fmhub24.app.domain.repository.CollectionRepository
import com.fmhub24.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore("fmhub_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    private val key = stringPreferencesKey("provider_base_url")
    override val providerBaseUrl: Flow<String> = context.settingsDataStore.data.map { it[key].orEmpty() }
    override suspend fun setProviderBaseUrl(value: String) {
        context.settingsDataStore.edit { it[key] = value.trim().removeSuffix("/") }
    }
}

class InMemoryCollectionRepository : CollectionRepository {
    private val favoritesState = MutableStateFlow<List<MediaItem>>(emptyList())
    private val watchState = MutableStateFlow<List<MediaItem>>(emptyList())

    override suspend fun favorites(): List<MediaItem> = favoritesState.value
    override suspend fun isFavorite(id: String): Boolean = favoritesState.value.any { it.id == id }
    override suspend fun toggleFavorite(item: MediaItem): Boolean {
        val exists = favoritesState.value.any { it.id == item.id }
        favoritesState.value = if (exists) favoritesState.value.filterNot { it.id == item.id } else favoritesState.value + item
        return !exists
    }
    override suspend fun continueWatching(): List<MediaItem> = watchState.value
}
