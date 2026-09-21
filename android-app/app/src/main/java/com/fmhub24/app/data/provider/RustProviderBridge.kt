package com.fmhub24.app.data.provider

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JNI surface implemented by rust-core/src/jni_bridge.rs.
 *
 * The native library remains optional during migration. If it is not packaged, repository calls
 * fail closed and the app remains launchable with an explicit provider-unavailable state.
 */
internal object RustProviderBridge {
    private const val TAG = "RustProviderBridge"
    private val loaded: Boolean = runCatching {
        System.loadLibrary("fmhub_provider_core")
        true
    }.onFailure { Log.i(TAG, "Rust provider library is not packaged yet: ${it.message}") }.getOrDefault(false)

    fun isAvailable(): Boolean = loaded

    @JvmStatic external fun configure(baseUrl: String, userAgent: String): String
    @JvmStatic external fun home(page: Int, forceRefresh: Boolean): String
    @JvmStatic external fun search(query: String, page: Int): String
    @JvmStatic external fun details(id: String): String
    @JvmStatic external fun episodes(id: String, season: Int, page: Int): String
    @JvmStatic external fun streams(episodeId: String): String
    @JvmStatic external fun subtitles(episodeId: String): String
}

class NativeProviderCoreRepository : ProviderCoreRepository {
    private fun unavailable(): ProviderResult.Failure = ProviderResult.Failure(
        "Content provider is not configured or the native provider is unavailable",
    )

    private suspend fun payload(call: () -> String): String? = withContext(Dispatchers.IO) {
        if (!RustProviderBridge.isAvailable()) return@withContext null
        runCatching(call).getOrNull()
    }

    override suspend fun home(page: Int, forceRefresh: Boolean): ProviderResult<List<ProviderSection>> =
        payload { RustProviderBridge.home(page.coerceAtLeast(1), forceRefresh) }
            ?.let(ProviderJsonCodec::home) ?: unavailable()

    override suspend fun search(query: String, page: Int): ProviderResult<ProviderPage<ProviderCatalogItem>> =
        payload { RustProviderBridge.search(query.trim(), page.coerceAtLeast(1)) }
            ?.let(ProviderJsonCodec::search) ?: unavailable()

    override suspend fun details(id: String): ProviderResult<ProviderDetails> =
        payload { RustProviderBridge.details(id) }
            ?.let(ProviderJsonCodec::details) ?: unavailable()

    override suspend fun episodes(id: String, season: Int, page: Int): ProviderResult<ProviderPage<ProviderEpisode>> =
        payload { RustProviderBridge.episodes(id, season.coerceAtLeast(0), page.coerceAtLeast(1)) }
            ?.let(ProviderJsonCodec::episodes) ?: unavailable()

    override suspend fun streams(episodeId: String): ProviderResult<List<ProviderStream>> =
        payload { RustProviderBridge.streams(episodeId) }
            ?.let(ProviderJsonCodec::streams) ?: unavailable()

    override suspend fun subtitles(episodeId: String): ProviderResult<List<ProviderSubtitle>> =
        payload { RustProviderBridge.subtitles(episodeId) }
            ?.let(ProviderJsonCodec::subtitles) ?: unavailable()
}
