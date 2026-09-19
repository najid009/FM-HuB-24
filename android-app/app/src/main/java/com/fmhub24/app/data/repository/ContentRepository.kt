package com.fmhub24.app.data.repository

import android.util.Log
import com.fmhub24.app.plugins.PluginManager
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the loaded plugin providers to the UI.
 *
 * Two things matter here that the first version got wrong:
 *
 *  1. A provider's homepage is a *list of sections* (`mainPage: List<MainPageData>`) and each
 *     section is one `getMainPage(page, request)` call. Calling it with an empty request only
 *     ever worked for trivial providers — and, worse, with the wrong `MainPageRequest` field
 *     order, which silently produced empty rows.
 *  2. `loadLinks` hands back links that sometimes still have to be *unpacked* by an
 *     `ExtractorApi` (`extractorData != null`). Skipping that step is why a provider "worked"
 *     in CloudStream but showed nothing here.
 *
 * Everything is defensive: one broken provider must never take a screen down.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val pluginManager: PluginManager,
    private val settingsRepository: SettingsRepository,
) {

    /** Providers filtered by the user's adult-content setting. */
    private suspend fun visibleProviders(): List<MainAPI> {
        val showAdult = settingsRepository.isShowAdult()
        val all = pluginManager.getLoadedProviders()
        if (showAdult) return all
        return all.filter { provider ->
            val types = runCatching { provider.supportedTypes }.getOrNull().orEmpty()
            types.isEmpty() || !types.all { it == TvType.NSFW }
        }
    }

    suspend fun getMainPageContent(page: Int = 1): Result<List<Pair<String, HomePageList>>> =
        withContext(Dispatchers.IO) {
            try {
                val providers = visibleProviders()
                if (providers.isEmpty()) return@withContext Result.success(emptyList())

                val results = providers.map { provider ->
                    async {
                        runCatching { provider.mainPageSections(page) }
                            .onFailure { Log.d(TAG, "${provider.name}: main page failed: $it") }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()

                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Requests every `mainPage` entry of [provider] and returns (provider, section) pairs. */
    private suspend fun MainAPI.mainPageSections(page: Int): List<Pair<String, HomePageList>> {
        if (!hasMainPage) return emptyList()

        val requests = runCatching { mainPage }
            .getOrDefault(emptyList())
            .filter { it.data.isNotBlank() }
            .map { MainPageRequest(name = it.name, data = it.data, horizontalImages = it.horizontalImages) }
            .ifEmpty { listOf(MainPageRequest(name = name, data = "", horizontalImages = false)) }

        val out = mutableListOf<Pair<String, HomePageList>>()
        for (request in requests) {
            val response: HomePageResponse? = try {
                getMainPage(page, request)
            } catch (t: Throwable) {
                Log.d(TAG, "$name.getMainPage($page, ${request.data}) failed", t)
                null
            }
            response?.items?.forEach { list ->
                if (list.list.isNotEmpty()) out += this.name to list
            }
        }
        return out
    }

    suspend fun searchAllProviders(query: String): Result<List<SearchResponse>> =
        withContext(Dispatchers.IO) {
            try {
                val providers = visibleProviders()
                // Scraping 40 providers at once gets most of them rate-limited; 8 at a time is
                // the trade CloudStream itself makes.
                val limiter = Semaphore(8)
                val results = providers.map { provider ->
                    async {
                        limiter.withPermit {
                            runCatching { provider.search(query) ?: emptyList() }
                                .onFailure { Log.d(TAG, "${provider.name}.search failed: $it") }
                                .getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten()
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun searchProvider(apiName: String, query: String): Result<List<SearchResponse>> =
        withContext(Dispatchers.IO) {
            val provider = pluginManager.getProviderByName(apiName)
                ?: return@withContext Result.failure(Exception("Provider not found: $apiName"))
            runCatching { provider.search(query) ?: emptyList() }
        }

    suspend fun loadContent(url: String, apiName: String): Result<LoadResponse> =
        withContext(Dispatchers.IO) {
            try {
                val provider = pluginManager.getProviderByName(apiName)
                    ?: return@withContext Result.failure(Exception("Provider not found: $apiName"))
                val result = provider.load(url)
                    ?: return@withContext Result.failure(
                        Exception("$apiName returned no details for ${url.take(80)}")
                    )
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Resolve all playable links for [data] (the `dataUrl`/`Episode.data` a provider gave us).
     *
     * Sub-links returned as `extractorData` are handed to the matching [ExtractorApi] — the
     * library registers the built-in ones globally and plugins add their own on load, so both
     * sets are consulted.
     */
    suspend fun loadLinks(
        data: String,
        apiName: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        linkCallback: (ExtractorLink) -> Unit,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val provider = pluginManager.getProviderByName(apiName)
                ?: return@withContext Result.failure(Exception("Provider not found: $apiName"))

            val raw = mutableListOf<ExtractorLink>()
            val ok = try {
                provider.loadLinks(data, false, subtitleCallback) { link -> raw += link }
            } catch (t: Throwable) {
                Log.w(TAG, "$apiName.loadLinks threw", t)
                false
            }

            val resolved = mutableListOf<ExtractorLink>()
            raw.forEach { link ->
                if (link.extractorData.isNullOrBlank()) {
                    resolved += link
                    return@forEach
                }
                val extractor = (pluginManager.getExtractors() + extractorApis)
                    .firstOrNull { it.name == link.source }
                if (extractor == null) {
                    resolved += link
                    return@forEach
                }
                val before = resolved.size
                try {
                    extractor.getUrl(link.url, link.referer.ifBlank { null }, subtitleCallback) { out ->
                        resolved += out
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "extractor ${extractor.name} failed for ${link.url.take(60)}: $t")
                }
                // Extractor produced nothing useful: keep the original link rather than losing it.
                if (resolved.size == before) resolved += link
            }

            val accepted = resolved.filter { it.url.isNotBlank() }
            accepted.forEach(linkCallback)
            Result.success(accepted.isNotEmpty() || ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLoadedProviders(): List<MainAPI> = pluginManager.getLoadedProviders()

    private companion object {
        const val TAG = "FMHubContent"
    }
}
