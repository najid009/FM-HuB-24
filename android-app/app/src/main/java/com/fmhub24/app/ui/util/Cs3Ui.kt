package com.fmhub24.app.ui.util

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse

/**
 * Small adapters between CloudStream's DTO shapes and what our Compose widgets want.
 *
 * `SearchResponse` is an *interface* in the real API and deliberately does not expose `year`
 * (only the concrete subtypes do) — reading it needs the subtype check below instead of the
 * `item.year` the previous stub-based model allowed.
 */

val SearchResponse.yearOrNull: Int?
    get() = when (this) {
        is MovieSearchResponse -> year
        is TvSeriesSearchResponse -> year
        is AnimeSearchResponse -> year
        else -> null
    }

/** Providers sometimes leave `apiName` empty; fall back to the plugin's class name. */
val SearchResponse.sourceLabel: String
    get() = apiName.ifBlank { url.substringBeforeLast('/').takeLast(24) }

/** "S2:E3 — Title" or just the title, for episode rows. */
fun Episode.displayLabel(): String {
    val prefix = listOfNotNull(
        season?.let { "S$it" },
        episode?.let { "E$it" },
    ).joinToString(" ")
    val title = name?.takeIf { it.isNotBlank() }
    return when {
        prefix.isNotBlank() && title != null -> "$prefix — $title"
        prefix.isNotBlank() -> prefix
        title != null -> title
        else -> data.take(48)
    }
}

/** Best playable-ish url for a detail page's main "Play" action. */
fun LoadResponse.primaryEpisodeData(): String? = dataUrlOrNull()

/**
 * `dataUrl` lives on the single-link responses while series/anime carry episode lists, and
 * `EpisodeResponse` upstream exposes no `getEpisodes()` — so each shape is handled explicitly.
 */
private fun LoadResponse.dataUrlOrNull(): String? {
    (this as? MovieLoadResponse)?.dataUrl?.takeIf { it.isNotBlank() }?.let { return it }
    (this as? LiveStreamLoadResponse)?.dataUrl?.takeIf { it.isNotBlank() }?.let { return it }
    (this as? TvSeriesLoadResponse)?.episodes?.firstOrNull()?.data?.takeIf { it.isNotBlank() }
        ?.let { return it }
    (this as? AnimeLoadResponse)?.episodes?.values?.flatten()?.firstOrNull()?.data
        ?.takeIf { it.isNotBlank() }?.let { return it }
    return null
}
