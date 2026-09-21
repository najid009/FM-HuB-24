package com.fmhub24.app.data.provider

/**
 * Stable Android boundary for the compiled Rust provider core.
 *
 * The implementation will be supplied by the native bridge once the Android ABI build is added.
 * Keeping this contract separate from Compose and Room lets Home/Search/Details migrate without
 * bringing CloudStream types across the UI boundary.
 */
interface ProviderCoreRepository {
    suspend fun home(page: Int = 1, forceRefresh: Boolean = false): ProviderResult<List<ProviderSection>>
    suspend fun search(query: String, page: Int = 1): ProviderResult<ProviderPage<ProviderCatalogItem>>
    suspend fun details(id: String): ProviderResult<ProviderDetails>
    suspend fun episodes(id: String, season: Int, page: Int = 1): ProviderResult<ProviderPage<ProviderEpisode>>
    suspend fun streams(episodeId: String): ProviderResult<List<ProviderStream>>
    suspend fun subtitles(episodeId: String): ProviderResult<List<ProviderSubtitle>>
}

/** Safe empty implementation used until a configured authorized provider endpoint is available. */
class UnavailableProviderCoreRepository : ProviderCoreRepository {
    private fun unavailable(): ProviderResult.Failure =
        ProviderResult.Failure("Content provider is not configured or is temporarily unavailable")

    override suspend fun home(page: Int, forceRefresh: Boolean) = unavailable()
    override suspend fun search(query: String, page: Int) = unavailable()
    override suspend fun details(id: String) = unavailable()
    override suspend fun episodes(id: String, season: Int, page: Int) = unavailable()
    override suspend fun streams(episodeId: String) = unavailable()
    override suspend fun subtitles(episodeId: String) = unavailable()
}
