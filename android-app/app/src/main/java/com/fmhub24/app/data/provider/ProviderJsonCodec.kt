package com.fmhub24.app.data.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

internal object ProviderJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class ErrorWire(val kind: String = "provider", val message: String)

    @Serializable
    private data class Envelope<T>(
        val ok: Boolean,
        val data: T? = null,
        val error: ErrorWire? = null,
    )

    @Serializable
    private data class CatalogItemWire(
        val id: String,
        val title: String,
        @SerialName("poster_url") val posterUrl: String? = null,
        @SerialName("backdrop_url") val backdropUrl: String? = null,
        val year: Int? = null,
        val kind: String = "unknown",
        val rating: Float? = null,
        val description: String? = null,
    )

    @Serializable
    private data class SectionWire(val id: String, val title: String, val items: List<CatalogItemWire> = emptyList())

    @Serializable
    private data class PageWire<T>(val items: List<T> = emptyList(), val page: Int = 1, @SerialName("has_next") val hasNext: Boolean = false)

    @Serializable
    private data class EpisodeWire(
        val id: String,
        val number: Int,
        val title: String? = null,
        val description: String? = null,
    )

    @Serializable
    private data class SeasonWire(
        val number: Int,
        val title: String? = null,
        val episodes: List<EpisodeWire> = emptyList(),
    )

    @Serializable
    private data class DetailsWire(
        val item: CatalogItemWire,
        val genres: List<String> = emptyList(),
        @SerialName("audio_languages") val audioLanguages: List<String> = emptyList(),
        val seasons: List<SeasonWire> = emptyList(),
    )

    @Serializable
    private data class StreamWire(
        val url: String,
        val quality: Int? = null,
        @SerialName("mime_type") val mimeType: String? = null,
        val referer: String? = null,
        val headers: List<List<String>> = emptyList(),
    )

    @Serializable
    private data class SubtitleWire(
        val url: String,
        val language: String,
        val format: String? = null,
    )

    fun home(payload: String): ProviderResult<List<ProviderSection>> = parse<List<SectionWire>, List<ProviderSection>>(payload) { sections ->
        sections.map { section -> ProviderSection(section.id, section.title, section.items.map(::item)) }
    }

    fun search(payload: String): ProviderResult<ProviderPage<ProviderCatalogItem>> = parse<PageWire<CatalogItemWire>, ProviderPage<ProviderCatalogItem>>(payload) { page ->
        ProviderPage(page.items.map(::item), page.page.coerceAtLeast(1), page.hasNext)
    }

    fun details(payload: String): ProviderResult<ProviderDetails> = parse<DetailsWire, ProviderDetails>(payload) { details ->
        ProviderDetails(
            item = item(details.item),
            genres = details.genres,
            audioLanguages = details.audioLanguages,
            seasons = details.seasons.map { season ->
                ProviderSeason(season.number, season.title, season.episodes.map(::episode))
            },
        )
    }

    fun episodes(payload: String): ProviderResult<ProviderPage<ProviderEpisode>> = parse<PageWire<EpisodeWire>, ProviderPage<ProviderEpisode>>(payload) { page ->
        ProviderPage(page.items.map(::episode), page.page.coerceAtLeast(1), page.hasNext)
    }

    fun streams(payload: String): ProviderResult<List<ProviderStream>> = parse<List<StreamWire>, List<ProviderStream>>(payload) { streams ->
        streams.map { stream ->
            ProviderStream(
                url = stream.url,
                quality = stream.quality,
                mimeType = stream.mimeType,
                referer = stream.referer,
                headers = stream.headers.mapNotNull { pair ->
                    if (pair.size >= 2) pair[0] to pair[1] else null
                }.toMap(),
            )
        }
    }

    fun subtitles(payload: String): ProviderResult<List<ProviderSubtitle>> = parse<List<SubtitleWire>, List<ProviderSubtitle>>(payload) { subtitles ->
        subtitles.map { ProviderSubtitle(it.url, it.language, it.format) }
    }

    private fun item(item: CatalogItemWire) = ProviderCatalogItem(
        id = item.id,
        title = item.title,
        posterUrl = item.posterUrl,
        backdropUrl = item.backdropUrl,
        year = item.year,
        kind = when (item.kind.lowercase()) {
            "movie" -> ProviderMediaKind.MOVIE
            "series" -> ProviderMediaKind.SERIES
            "episode" -> ProviderMediaKind.EPISODE
            else -> ProviderMediaKind.UNKNOWN
        },
        rating = item.rating,
        description = item.description,
    )

    private fun episode(episode: EpisodeWire) = ProviderEpisode(
        id = episode.id,
        number = episode.number,
        title = episode.title,
        description = episode.description,
    )

    private inline fun <reified T, R> parse(payload: String, transform: (T) -> R): ProviderResult<R> = try {
        val envelope = json.decodeFromString<Envelope<T>>(payload)
        if (!envelope.ok) {
            ProviderResult.Failure(envelope.error?.message ?: "Provider request failed", retryable = envelope.error?.kind != "configuration")
        } else {
            envelope.data?.let { ProviderResult.Success(transform(it)) }
                ?: ProviderResult.Failure("Provider returned no data", retryable = false)
        }
    } catch (error: Exception) {
        ProviderResult.Failure("Invalid provider response: ${error.message ?: "malformed JSON"}", retryable = false)
    }
}
