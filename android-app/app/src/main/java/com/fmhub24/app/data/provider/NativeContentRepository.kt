package com.fmhub24.app.data.provider

import android.util.Log
import com.fmhub24.app.domain.model.Episode
import com.fmhub24.app.domain.model.MediaDetails
import com.fmhub24.app.domain.model.MediaItem
import com.fmhub24.app.domain.model.MediaKind
import com.fmhub24.app.domain.model.MediaPage
import com.fmhub24.app.domain.model.MediaSection
import com.fmhub24.app.domain.model.Stream
import com.fmhub24.app.domain.model.Subtitle
import com.fmhub24.app.domain.repository.ContentRepository
import com.fmhub24.app.domain.repository.ProviderConfigurator
import com.fmhub24.app.domain.repository.RepositoryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal object RustProviderBridge {
    private const val TAG = "RustProviderBridge"
    private val loaded = runCatching {
        System.loadLibrary("fmhub_provider_core")
        true
    }.onFailure { Log.i(TAG, "Rust provider library unavailable: ${it.message}") }.getOrDefault(false)

    fun isAvailable() = loaded

    @JvmStatic external fun configure(baseUrl: String, userAgent: String): String
    @JvmStatic external fun home(page: Int, forceRefresh: Boolean): String
    @JvmStatic external fun search(query: String, page: Int): String
    @JvmStatic external fun details(id: String): String
    @JvmStatic external fun episodes(id: String, season: Int, page: Int): String
    @JvmStatic external fun streams(episodeId: String): String
    @JvmStatic external fun subtitles(episodeId: String): String
}

class NativeContentRepository : ContentRepository, ProviderConfigurator {
    override suspend fun configure(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (!RustProviderBridge.isAvailable() || baseUrl.isBlank()) return@withContext false
        runCatching {
            RustProviderBridge.configure(baseUrl.trim().removeSuffix("/"), "FM-HuB-24/3.0")
        }.map { it.contains("\"configured\":true") }.getOrDefault(false)
    }

    private suspend fun call(block: () -> String): String? = withContext(Dispatchers.IO) {
        if (!RustProviderBridge.isAvailable()) null else runCatching(block).getOrNull()
    }

    override suspend fun home(page: Int, forceRefresh: Boolean): RepositoryResult<List<MediaSection>> =
        call { RustProviderBridge.home(page.coerceAtLeast(1), forceRefresh) }?.let(ProviderJsonCodec::home) ?: unavailable()

    override suspend fun search(query: String, page: Int): RepositoryResult<MediaPage<MediaItem>> =
        call { RustProviderBridge.search(query.trim(), page.coerceAtLeast(1)) }?.let(ProviderJsonCodec::search) ?: unavailable()

    override suspend fun details(id: String): RepositoryResult<MediaDetails> =
        call { RustProviderBridge.details(id) }?.let(ProviderJsonCodec::details) ?: unavailable()

    override suspend fun episodes(id: String, season: Int, page: Int): RepositoryResult<MediaPage<Episode>> =
        call { RustProviderBridge.episodes(id, season.coerceAtLeast(0), page.coerceAtLeast(1)) }?.let(ProviderJsonCodec::episodes) ?: unavailable()

    override suspend fun streams(episodeId: String): RepositoryResult<List<Stream>> =
        call { RustProviderBridge.streams(episodeId) }?.let(ProviderJsonCodec::streams) ?: unavailable()

    override suspend fun subtitles(episodeId: String): RepositoryResult<List<Subtitle>> =
        call { RustProviderBridge.subtitles(episodeId) }?.let(ProviderJsonCodec::subtitles) ?: unavailable()

    private fun <T> unavailable(): RepositoryResult<T> = RepositoryResult.Failure(
        "Provider is not configured yet. Add a provider source in Settings.",
    )
}

internal object ProviderJsonCodec {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable private data class ErrorWire(val kind: String = "provider", val message: String)
    @Serializable private data class Envelope<T>(val ok: Boolean, val data: T? = null, val error: ErrorWire? = null)
    @Serializable private data class ItemWire(val id: String, val title: String, @SerialName("poster_url") val posterUrl: String? = null, @SerialName("backdrop_url") val backdropUrl: String? = null, val year: Int? = null, val kind: String = "unknown", val rating: Float? = null, val description: String? = null)
    @Serializable private data class SectionWire(val id: String, val title: String, val items: List<ItemWire> = emptyList())
    @Serializable private data class PageWire<T>(val items: List<T> = emptyList(), val page: Int = 1, @SerialName("has_next") val hasNext: Boolean = false)
    @Serializable private data class EpisodeWire(val id: String, val number: Int, val title: String? = null, val description: String? = null)
    @Serializable private data class SeasonWire(val number: Int, val title: String? = null, val episodes: List<EpisodeWire> = emptyList())
    @Serializable private data class DetailsWire(val item: ItemWire, val genres: List<String> = emptyList(), @SerialName("audio_languages") val audioLanguages: List<String> = emptyList(), val seasons: List<SeasonWire> = emptyList())
    @Serializable private data class StreamWire(val url: String, val quality: Int? = null, @SerialName("mime_type") val mimeType: String? = null, val referer: String? = null, val headers: List<List<String>> = emptyList())
    @Serializable private data class SubtitleWire(val url: String, val language: String, val format: String? = null)

    fun home(payload: String) = parse<List<SectionWire>, List<MediaSection>>(payload) { sections -> sections.map { MediaSection(it.id, it.title, it.items.map(::item)) } }
    fun search(payload: String) = parse<PageWire<ItemWire>, MediaPage<MediaItem>>(payload) { page -> MediaPage(page.items.map(::item), page.page.coerceAtLeast(1), page.hasNext) }
    fun details(payload: String) = parse<DetailsWire, MediaDetails>(payload) { d -> MediaDetails(item(d.item), d.genres, d.audioLanguages, d.seasons.map { com.fmhub24.app.domain.model.Season(it.number, it.title, it.episodes.map(::episode)) }) }
    fun episodes(payload: String) = parse<PageWire<EpisodeWire>, MediaPage<Episode>>(payload) { page -> MediaPage(page.items.map(::episode), page.page.coerceAtLeast(1), page.hasNext) }
    fun streams(payload: String) = parse<List<StreamWire>, List<Stream>>(payload) { it.map { s -> Stream(s.url, s.quality, s.mimeType, s.referer, s.headers.mapNotNull { p -> p.takeIf { it.size >= 2 }?.let { it[0] to it[1] } }.toMap()) } }
    fun subtitles(payload: String) = parse<List<SubtitleWire>, List<Subtitle>>(payload) { it.map { s -> Subtitle(s.url, s.language, s.format) } }

    private fun item(item: ItemWire) = MediaItem(item.id, item.title, item.posterUrl, item.backdropUrl, item.year, when (item.kind.lowercase()) { "movie" -> MediaKind.MOVIE; "series" -> MediaKind.SERIES; "episode" -> MediaKind.EPISODE; else -> MediaKind.UNKNOWN }, item.rating, item.description)
    private fun episode(e: EpisodeWire) = Episode(e.id, e.number, e.title, e.description)

    private inline fun <reified T, R> parse(payload: String, transform: (T) -> R): RepositoryResult<R> = try {
        val envelope = json.decodeFromString<Envelope<T>>(payload)
        if (!envelope.ok) RepositoryResult.Failure(envelope.error?.message ?: "Provider request failed", envelope.error?.kind != "configuration")
        else envelope.data?.let { RepositoryResult.Success(transform(it)) } ?: RepositoryResult.Failure("Provider returned no data", false)
    } catch (e: Exception) {
        RepositoryResult.Failure("Invalid provider response: ${e.message ?: "malformed JSON"}", false)
    }
}
