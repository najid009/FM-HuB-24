package com.fmhub24.app.data.aggregation

import com.fmhub24.app.plugins.cloudstream.AnimeSearchResponse
import com.fmhub24.app.plugins.cloudstream.HomePageList
import com.fmhub24.app.plugins.cloudstream.MovieSearchResponse
import com.fmhub24.app.plugins.cloudstream.SearchResponse
import com.fmhub24.app.plugins.cloudstream.TvSeriesSearchResponse

/**
 * Provider-agnostic identity for display aggregation.
 *
 * The selected representative keeps its original apiName/url, so opening a card
 * still uses a real provider. This is display deduplication, not destructive
 * provider merging: the other providers can be reloaded later if the selected
 * source fails.
 */
object ContentDeduplicator {
    const val AGGREGATED_PROVIDER = "__all_providers__"
    fun key(item: SearchResponse): String {
        val explicitYear = when (item) {
            is MovieSearchResponse -> item.year
            is TvSeriesSearchResponse -> item.year
            is AnimeSearchResponse -> item.year
            else -> null
        }
        val nameYear = Regex("\\b(?:19|20)\\d{2}\\b").find(item.name)?.value?.toIntOrNull()
        val year = explicitYear ?: nameYear ?: 0
        val normalizedName = normalize(item.name).replace(Regex("(?:19|20)\\d{2}"), "")
        return "${item.type?.name ?: "other"}:$normalizedName:$year"
    }

    fun dedupe(items: Iterable<SearchResponse>): List<SearchResponse> =
        items.filter { it.name.isNotBlank() && it.url.isNotBlank() }.distinctBy(::key)

    fun dedupeSections(sections: List<Pair<String, HomePageList>>): List<Pair<String, HomePageList>> {
        val seen = mutableSetOf<String>()
        return sections.mapNotNull { (_, section) ->
            val unique = section.list.filter { item ->
                val key = key(item)
                item.name.isNotBlank() && item.url.isNotBlank() && seen.add(key)
            }
            if (unique.isEmpty()) null else AGGREGATED_PROVIDER to HomePageList(section.name, unique)
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("\\[[^]]*]|\\([^)]*\\)|\\{[^}]*}"), " ")
        .replace(Regex("[^a-z0-9\\u00c0-\\u024f]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
