package com.fmhub24.app.data.provider

import android.util.Log

/**
 * Minimal JNI surface for the future Android native build.
 *
 * The native library is optional during this migration step. If it is not packaged yet, calls
 * fail closed and the UI receives a provider-unavailable result instead of crashing at startup.
 * The native implementation must return validated JSON matching rust-core's serde models.
 */
internal object RustProviderBridge {
    private const val TAG = "RustProviderBridge"
    private val loaded: Boolean = runCatching {
        System.loadLibrary("fmhub_provider_core")
        true
    }.onFailure { Log.i(TAG, "Rust provider library is not packaged yet: ${it.message}") }.getOrDefault(false)

    fun isAvailable(): Boolean = loaded

    external fun home(page: Int, forceRefresh: Boolean): String
    external fun search(query: String, page: Int): String
    external fun details(id: String): String
    external fun episodes(id: String, season: Int, page: Int): String
    external fun streams(episodeId: String): String
    external fun subtitles(episodeId: String): String
}

class NativeProviderCoreRepository : ProviderCoreRepository {
    private fun unavailable(): ProviderResult.Failure = ProviderResult.Failure(
        "Content provider is not configured or the native provider is unavailable",
    )

    override suspend fun home(page: Int, forceRefresh: Boolean): ProviderResult<List<ProviderSection>> = unavailable()
    override suspend fun search(query: String, page: Int): ProviderResult<ProviderPage<ProviderCatalogItem>> = unavailable()
    override suspend fun details(id: String): ProviderResult<ProviderDetails> = unavailable()
    override suspend fun episodes(id: String, season: Int, page: Int): ProviderResult<ProviderPage<ProviderEpisode>> = unavailable()
    override suspend fun streams(episodeId: String): ProviderResult<List<ProviderStream>> = unavailable()
    override suspend fun subtitles(episodeId: String): ProviderResult<List<ProviderSubtitle>> = unavailable()
}
