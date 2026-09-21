package com.fmhub24.app.data.provider

/** UI-facing equivalents of rust-core typed models. No catalogue is embedded in the app. */
enum class ProviderMediaKind { MOVIE, SERIES, EPISODE, UNKNOWN }

data class ProviderCatalogItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val kind: ProviderMediaKind,
    val rating: Float?,
    val description: String?,
)

data class ProviderSection(val id: String, val title: String, val items: List<ProviderCatalogItem>)
data class ProviderPage<T>(val items: List<T>, val page: Int, val hasNext: Boolean)
data class ProviderSeason(val number: Int, val title: String?, val episodes: List<ProviderEpisode>)
data class ProviderEpisode(val id: String, val number: Int, val title: String?, val description: String?)
data class ProviderDetails(
    val item: ProviderCatalogItem,
    val genres: List<String>,
    val audioLanguages: List<String>,
    val seasons: List<ProviderSeason>,
)
data class ProviderStream(
    val url: String,
    val quality: Int?,
    val mimeType: String?,
    val referer: String?,
    val headers: Map<String, String>,
)
data class ProviderSubtitle(val url: String, val language: String, val format: String?)

sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T) : ProviderResult<T>
    data class Failure(val message: String, val retryable: Boolean = true) : ProviderResult<Nothing>
}
