package com.fmhub24.app.download.hls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/**
 * Downloads a non-DRM HLS presentation for which the caller is authorized.
 *
 * This intentionally does not decrypt AES-128/SAMPLE-AES/DRM playlists or bypass
 * authentication controls. Such streams return [HlsDownloadResult.Unsupported].
 * The output is a byte-concatenated transport stream or fragmented MP4, depending
 * on the playlist's segments; it is not transcoded or remuxed.
 */
class HlsSegmentDownloader(
    private val maxRetries: Int = 3,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    suspend fun download(
        playlistUrl: String,
        outputFile: File,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        onProgress: suspend (downloadedSegments: Int, totalSegments: Int, bytes: Long) -> Unit = { _, _, _ -> },
    ): HlsDownloadResult = withContext(Dispatchers.IO) {
        val requestHeaders = buildMap {
            put("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)
            headers.forEach { (key, value) -> if (key.isNotBlank() && value.isNotBlank()) put(key, value) }
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        }

        val tempFile = File(outputFile.parentFile ?: File("."), "${outputFile.name}.part")
        try {
            val root = fetchText(playlistUrl, requestHeaders)
            val parsedRoot = HlsParser.parse(playlistUrl, root)
            if (parsedRoot.unsupportedReason != null) {
                return@withContext HlsDownloadResult.Unsupported(parsedRoot.unsupportedReason)
            }
            val mediaUrl = parsedRoot.variants
                .maxWithOrNull(compareBy<HlsVariant> { it.bandwidth }.thenBy { it.height })
                ?.url
                ?: playlistUrl

            val mediaText = if (mediaUrl == playlistUrl) root else fetchText(mediaUrl, requestHeaders)
            val media = HlsParser.parse(mediaUrl, mediaText)
            if (media.unsupportedReason != null) {
                return@withContext HlsDownloadResult.Unsupported(media.unsupportedReason)
            }
            if (media.segments.isEmpty()) {
                return@withContext HlsDownloadResult.Failed("The HLS media playlist contains no segments")
            }

            tempFile.parentFile?.mkdirs()
            if (tempFile.exists()) tempFile.delete()
            var bytes = 0L
            FileOutputStream(tempFile).use { output ->
                for ((index, segment) in media.segments.withIndex()) {
                    coroutineContext.ensureActive()
                    var lastError: Throwable? = null
                    var copied = false
                    repeat(max(1, maxRetries)) { attempt ->
                        if (copied) return@repeat
                        try {
                            bytes += fetchTo(segment.url, output, requestHeaders)
                            copied = true
                        } catch (error: Throwable) {
                            lastError = error
                            if (attempt + 1 < max(1, maxRetries)) kotlinx.coroutines.delay((attempt + 1) * 500L)
                        }
                    }
                    if (!copied) throw IOException("Segment ${index + 1} failed: ${lastError?.message}", lastError)
                    onProgress(index + 1, media.segments.size, bytes)
                }
            }

            outputFile.parentFile?.mkdirs()
            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    outputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    outputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
            HlsDownloadResult.Completed(outputFile, media.segments.size, bytes)
        } catch (error: kotlinx.coroutines.CancellationException) {
            tempFile.delete()
            throw error
        } catch (error: UnsupportedHlsException) {
            tempFile.delete()
            HlsDownloadResult.Unsupported(error.message ?: "Unsupported HLS feature")
        } catch (error: Throwable) {
            tempFile.delete()
            HlsDownloadResult.Failed(error.message ?: "HLS download failed")
        }
    }

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val connection = open(url, headers)
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchTo(url: String, output: FileOutputStream, headers: Map<String, String>): Long {
        val connection = open(url, headers)
        return try {
            var total = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            total
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.connect()
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $status for ${url.take(160)}")
        }
        return connection
    }

    companion object {
        private const val DEFAULT_USER_AGENT = "FMHuB24/2.0 (Android)"
    }
}

sealed interface HlsDownloadResult {
    data class Completed(val file: File, val segments: Int, val bytes: Long) : HlsDownloadResult
    data class Unsupported(val reason: String) : HlsDownloadResult
    data class Failed(val reason: String) : HlsDownloadResult
}

private class UnsupportedHlsException(message: String) : IOException(message)

private data class HlsVariant(val url: String, val bandwidth: Long, val height: Int)
private data class HlsSegment(val url: String)
private data class ParsedHls(
    val variants: List<HlsVariant> = emptyList(),
    val segments: List<HlsSegment> = emptyList(),
    val unsupportedReason: String? = null,
)

private object HlsParser {
    fun parse(baseUrl: String, text: String): ParsedHls {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull() != "#EXTM3U") throw IOException("Not an HLS playlist")

        val variants = mutableListOf<HlsVariant>()
        val segments = mutableListOf<HlsSegment>()
        var pendingVariant: Map<String, String>? = null
        var initSegment: String? = null
        var unsupported: String? = null

        for (index in lines.indices) {
            val line = lines[index]
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    pendingVariant = attributes(line.substringAfter(':'))
                }
                pendingVariant != null && !line.startsWith("#") -> {
                    val attrs = pendingVariant!!
                    variants += HlsVariant(
                        url = resolve(baseUrl, line),
                        bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                        height = attrs["RESOLUTION"]?.substringAfter('x')?.toIntOrNull() ?: 0,
                    )
                    pendingVariant = null
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = attributes(line.substringAfter(':'))["METHOD"].orEmpty()
                    if (!method.equals("NONE", ignoreCase = true)) {
                        unsupported = "Encrypted HLS is not supported by this downloader"
                    }
                }
                line.startsWith("#EXT-X-SESSION-KEY:") -> {
                    unsupported = "Encrypted HLS is not supported by this downloader"
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val uri = attributes(line.substringAfter(':'))["URI"]?.trim('"')
                    if (uri != null) initSegment = resolve(baseUrl, uri)
                }
                !line.startsWith("#") -> segments += HlsSegment(resolve(baseUrl, line))
            }
        }

        val orderedSegments = if (initSegment != null) listOf(HlsSegment(initSegment)) + segments else segments
        return ParsedHls(variants = variants, segments = orderedSegments, unsupportedReason = unsupported)
    }

    private fun attributes(value: String): Map<String, String> =
        value.split(Regex(",(?=[A-Z0-9-]+=)"))
            .mapNotNull { part ->
                val key = part.substringBefore('=').trim()
                val raw = part.substringAfter('=', "").trim()
                if (key.isBlank()) null else key to raw
            }
            .toMap()

    private fun resolve(baseUrl: String, child: String): String =
        URI(baseUrl).resolve(child).toString()
}
