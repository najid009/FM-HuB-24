package com.fmhub.plugin.api

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Convenience base class for FMHub's own providers.
 *
 * Everything here is optional sugar on top of [MainAPI]: a plain `MainAPI` subclass works
 * exactly as well, which is what lets community `.cs3` files (that never heard of FMHub)
 * run in this host. The helpers exist so a provider is usually ~120 lines — fetch, parse,
 * emit — with every network/parse failure degraded to `null` instead of throwing, because a
 * throwing provider takes its whole plugin down while a `null` one only hides a row.
 *
 * Response objects should still be built with CloudStream's own builders
 * (`newHomePageResponse`, `newMovieLoadResponse`, `newTvSeriesLoadResponse`, `newEpisode`)
 * — the direct constructors are deprecated to `DeprecationLevel.ERROR` upstream.
 */
abstract class FMHubProvider : MainAPI() {

    /** Site root used to resolve relative hrefs, e.g. "https://example.com". */
    abstract val siteUrl: String

    /** Headers sent with every request (the host already sets a browser User-Agent). */
    open val defaultHeaders: Map<String, String> = emptyMap()

    /** Per-request timeout in seconds; 0 = NiceHttp/host default. */
    open val requestTimeoutSeconds: Long = 0L

    // ---------------------------------------------------------------- network

    /** GET and return the response body, or null on any failure. */
    protected suspend fun getText(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        referer: String? = null,
    ): String? = try {
        app.get(
            url = url,
            referer = referer,
            headers = defaultHeaders + extraHeaders,
            timeout = requestTimeoutSeconds,
        ).text
    } catch (t: Throwable) {
        log("getText failed for $url: $t")
        null
    }

    /** GET and parse as HTML (jsoup), with `baseUri` set so relative links resolve. */
    protected suspend fun getDocument(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        referer: String? = null,
    ): Document? = getText(url, extraHeaders, referer)?.let { html ->
        try {
            Jsoup.parse(html, url)
        } catch (t: Throwable) {
            log("Jsoup.parse failed for $url: $t")
            null
        }
    }

    /** GET and deserialize JSON with the shared Jackson mapper. */
    protected suspend inline fun <reified T : Any> getJson(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): T? = getText(url, extraHeaders)?.let { body ->
        try {
            mapper.readValue(body, T::class.java)
        } catch (t: Throwable) {
            log("getJson<${T::class.simpleName}> failed for $url: $t")
            null
        }
    }

    /** POST a raw body (JSON by default) and deserialize the response. */
    protected suspend inline fun <reified T : Any> postJson(
        url: String,
        body: String,
        contentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap(),
    ): T? = try {
        // NiceHttp's `post` takes an okhttp RequestBody, not (body, mediaType) pairs — the
        // content type therefore belongs to the body itself.
        app.post(
            url = url,
            requestBody = body.toRequestBody(contentType.toMediaTypeOrNull()),
            headers = defaultHeaders + extraHeaders,
            timeout = requestTimeoutSeconds,
        ).text.let { response ->
            mapper.readValue(response, T::class.java)
        }
    } catch (t: Throwable) {
        log("postJson<${T::class.simpleName}> failed for $url: $t")
        null
    }

    // ------------------------------------------------------------------ misc

    /** Resolve a possibly-relative href against [siteUrl] (or [base]). */
    protected fun absUrl(url: String?, base: String = siteUrl): String = when {
        url.isNullOrBlank() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> base.trimEnd('/') + url
        else -> base.trimEnd('/') + "/" + url
    }

    /** Strip the noise scraping targets carry: markup, entities, doubled whitespace. */
    protected fun cleanName(name: String?): String = name
        ?.replace(Regex("<[^>]*>"), " ")
        ?.replace("&amp;", "&")
        ?.replace("&#39;", "'")
        ?.replace("&quot;", "\"")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.trim(':', '-', '|', ',')
        .orEmpty()

    protected fun log(message: String) {
        android.util.Log.d(TAG, "[$apiTag] $message")
    }

    private val apiTag: String get() = name.ifBlank { this::class.simpleName ?: "?" }

    // ------------------------------------------------------- link construction

    /**
     * Build an [ExtractorLink] via `newExtractorLink` (the plain constructor is deprecated
     * upstream). `type = null` lets CloudStream infer M3U8/DASH/VIDEO from the url.
     */
    protected suspend fun buildLink(
        url: String,
        label: String = name,
        referer: String? = null,
        quality: Int = Qualities.Unknown.value,
        headers: Map<String, String> = emptyMap(),
        type: ExtractorLinkType? = null,
        extractorData: String? = null,
    ): ExtractorLink = newExtractorLink(
        source = name,
        name = label,
        url = url,
        type = type,
    ) {
        this.referer = referer ?: ""
        this.quality = quality
        this.headers = headers
        this.extractorData = extractorData
    }

    companion object {
        private const val TAG = "FMHubProvider"
    }
}
