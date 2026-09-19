package com.fmhub24.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "fmhub24_settings")

/**
 * Persisted app settings (DataStore).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val showAdult = booleanPreferencesKey("show_adult")
        val autoLoadOnStart = booleanPreferencesKey("auto_load_on_start")
    }

    /** Continue from the last position when opening a player (default: on). */
    val resumePlayback: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.resumePlayback] ?: true }

    suspend fun setResumePlayback(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.resumePlayback] = value
        }
    }

    /**
     * Providers whose `supportedTypes` are NSFW-only are hidden unless this is on — the same
     * guard CloudStream implements via `MainAPI.settingsForProvider.enableAdult`, done here in
     * the host so it works for third-party extensions too.
     */
    val showAdult: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.showAdult] ?: false }

    suspend fun setShowAdult(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.showAdult] = value
        }
    }

    val autoLoadOnStart: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.autoLoadOnStart] ?: true }

    suspend fun setAutoLoadOnStart(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.autoLoadOnStart] = value
        }
    }

    suspend fun isShowAdult(): Boolean = showAdult.first()
}
