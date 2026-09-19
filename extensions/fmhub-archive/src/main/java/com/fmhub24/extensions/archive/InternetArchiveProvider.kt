package com.fmhub24.extensions.archive

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fmhub.plugin.api.FMHubProvider
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLEncoder

/**
 * Internet Archive (archive.org) — public-domain films and series.
 *
 * Reference implementation for an FMHub provider: three endpoints, no WebView, and every failure
 * path returns `null`/empty instead of throwing, because a throwing provider makes the whole row
 * vanish and looks to the user like a broken app.
 *
 * Endpoints (both public JSON APIs):
 *   search    https://archive.org/advancedsearch.php?q=…&page=N&output=json
 *   metadata  https://archive.org/metadata/<identifier>
 *   file      https://archive.org/download/<identifier>/<file.name>
 */
class InternetArchiveProvider : FMHubProvider() {

    override val siteUrl = "https://archive.org"
    override var mainUrl = siteUrl
    override var name = "Internet Archive"
    // `var`, not `val`: MainAPI declares `open var lang` — overriding a mutable property with a
    // read-only one is a compile error ("cannot be overridden by 'val' property").
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Documentary)

    /** Each entry is (query, row label); `data` is handed back as `request.data`. */
    override val mainPage = mainPageOf(
        "collection:(prelinger) AND mediatype:(movies)" to "Prelinger Archives",
        "collection:(feature_films) AND mediatype:(movies)" to "Feature Films",
        "collection:(opensource_movies) AND mediatype:(movies)" to "Community Video",
        "collection:(9/11) AND mediatype:(movies)" to "History",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val docs = searchDocs(query = request.data, page = page) ?: return null
        val items = docs.mapNotNull { it.toSearchResponse() }
        if (items.isEmpty()) return null
        return newHomePageResponse(request.name, items, hasNext = docs.size >= PAGE_SIZE)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val safe = query.trim().ifBlank { return null }
        val docs = searchDocs(
            query = "(${safe.replace("\"", "\\\"")}) AND mediatype:(movies)",
            page = 1,
        ) ?: return null
        return docs.mapNotNull { it.toSearchResponse() }.ifEmpty { null }
    }

    override suspend fun load(url: String): LoadResponse? {
        val identifier = url
            .substringAfterLast("/details/", "")
            .substringBefore('?')
            .substringBefore('/')
            .trim('/')
        if (identifier.isBlank()) return null

        val doc = searchDocs(query = "identifier:($identifier)", page = 1)?.firstOrNull()

        return newMovieLoadResponse(
            name = cleanName(doc?.title).ifBlank { identifier },
            url = "$siteUrl/details/$identifier",
            type = TvType.Movie,
            dataUrl = identifier,
        ) {
            posterUrl = "$siteUrl/services/img/$identifier"
            plot = cleanName(doc?.description).takeIf { it.isNotBlank() }?.take(1200)
            year = doc?.year?.toIntOrNull()
        }
    }

    /**
     * The archive stores several encodes per item; keep the mp4/h.264 renditions, biggest first,
     * and never more than four (the host shows one row per link and huge files do not stream well).
     */
    @Suppress("DEPRECATION") // SubtitleFile(lang, url) — the two-arg form extensions still use
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val identifier = data.trim('/')
        if (identifier.isBlank()) return false
        val metadata = getJson<ArchiveMetadata>("$siteUrl/metadata/$identifier") ?: return false
        val files = metadata.files.orEmpty()

        val videos = files
            .filter { it.isVideoFile() && !it.name.isNullOrBlank() }
            .sortedByDescending { it.sizeBytes() }
        if (videos.isEmpty()) return false

        videos.take(MAX_LINKS).forEachIndexed { index, file ->
            callback(
                buildLink(
                    url = "$siteUrl/download/$identifier/${file.name}",
                    label = "$name — ${file.format ?: file.name?.substringAfterLast('.') ?: "mp4"}",
                    referer = siteUrl,
                    quality = if (index == 0) Qualities.P720.value else Qualities.P480.value,
                    type = ExtractorLinkType.VIDEO,
                )
            )
        }

        files.filter { it.isSubtitleFile() && !it.name.isNullOrBlank() }.forEach { sub ->
            subtitleCallback(SubtitleFile(lang = sub.sourceLangFromName() ?: "en", url = "$siteUrl/download/$identifier/${sub.name}"))
        }

        return true
    }

    // ------------------------------------------------------------------ internals

    private suspend fun searchDocs(query: String, page: Int): List<ArchiveDoc>? {
        val url = buildString {
            append("$siteUrl/advancedsearch.php?q=").append(query.encode())
            append("&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&fl%5B%5D=description")
            append("&sort%5B%5D=downloads+desc")
            append("&rows=").append(PAGE_SIZE)
            append("&page=").append(page)
            append("&output=json")
        }
        return getJson<ArchiveSearch>(url)?.response?.docs
    }

    private fun ArchiveDoc.toSearchResponse(): SearchResponse? {
        val id = identifier?.takeIf { it.isNotBlank() } ?: return null
        return newMovieSearchResponse(
            name = cleanName(title).ifBlank { id },
            url = "$siteUrl/details/$id",
            type = TvType.Movie,
            fix = false, // already absolute — do not re-resolve against mainUrl
        ) {
            posterUrl = "$siteUrl/services/img/$id"
            year = this@toSearchResponse.year?.toIntOrNull()
        }
    }

    private fun String.encode(): String = URLEncoder.encode(this, "UTF-8")

    private companion object {
        const val PAGE_SIZE = 24
        const val MAX_LINKS = 4
    }

    // ------------------------------------------------------------------ DTOs
    //
    // Jackson (the mapper the host already owns) instead of kotlinx.serialization: no compiler
    // plugin to keep in sync with the host, and unknown fields are simply ignored.

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ArchiveSearch(
        @JsonProperty("response") val response: ArchiveResponse? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ArchiveResponse(
        // `docs` is sometimes a single object rather than a list — a List is what the API
        // returns for row queries, and getJson() returns null on a mismatch rather than crashing.
        @JsonProperty("docs") val docs: List<ArchiveDoc> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ArchiveDoc(
        @JsonProperty("identifier") val identifier: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: String? = null,
        @JsonProperty("description") val description: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ArchiveMetadata(
        @JsonProperty("files") val files: List<ArchiveFile>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ArchiveFile(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("format") val format: String? = null,
        @JsonProperty("size") val size: String? = null,
    ) {
        fun isVideoFile(): Boolean {
            val n = name?.lowercase().orEmpty()
            val f = format?.lowercase().orEmpty()
            return n.endsWith(".mp4") || n.endsWith(".mkv") ||
                f.contains("mpeg4") || f.contains("h.264")
        }

        fun isSubtitleFile(): Boolean {
            val n = name?.lowercase().orEmpty()
            return n.endsWith(".srt") || n.endsWith(".vtt")
        }

        fun sizeBytes(): Long = size?.toLongOrNull() ?: 0L

        /** `foo.en.srt` / `foo.eng.srt` -> `en` / `eng`; the archive names sidecars this way. */
        fun sourceLangFromName(): String? = name
            ?.removeSuffix(".srt")
            ?.removeSuffix(".vtt")
            ?.substringAfterLast('.', "")
            ?.takeIf { it.length in 2..3 && it.all(Char::isLetter) }
    }
}
