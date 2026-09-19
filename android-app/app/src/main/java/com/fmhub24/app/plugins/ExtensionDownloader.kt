package com.fmhub24.app.plugins

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads extension packages into `filesDir/extensions`.
 *
 * Rules that exist because of how Android loads dex:
 *  - the finished file is made **read-only**; since Android 14 a classloader refuses a
 *    writable dex file with `SecurityException: Writable dex file ... is not allowed`
 *    (CloudStream does the same, with a comment saying "In case of Android 14+");
 *  - downloads land in `*.part` and are renamed atomically, so an interrupted transfer can
 *    never be mistaken for a valid plugin;
 *  - the original extension is kept (`.cs3`, `.apk` or `.zip`) rather than force-renamed,
 *    because the container decides whether `requiresResources` can work at all;
 *  - an optional SHA-256 from repo metadata is verified — CloudStream's `plugins.json`
 *    ships `fileHash` for exactly this.
 */
@Singleton
class ExtensionDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private val extensionsDir: File by lazy {
        File(context.filesDir, "extensions").apply { mkdirs() }
    }

    class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Downloaded(val file: File, val sha256: String?, val bytes: Long)

    suspend fun downloadExtension(
        url: String,
        fileName: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {},
    ): Downloaded = withContext(Dispatchers.IO) {
        val expectedHash = normalizeHash(expectedSha256)
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException("Download failed: HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw DownloadException("Empty response body from $url")
            val contentLength = body.contentLength()

            val target = File(extensionsDir, sanitize(fileName))
            val tmpFile = File(extensionsDir, "${target.name}.part")

            try {
                FileOutputStream(tmpFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        val digest = expectedHash?.let { MessageDigest.getInstance("SHA-256") }
                        var totalRead = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest?.update(buffer, 0, read)
                            totalRead += read
                            if (contentLength > 0) {
                                val percent =
                                    ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                        output.flush()

                        if (digest != null) {
                            val actual = digest.digest().toHex()
                            if (!actual.equals(expectedHash, ignoreCase = true)) {
                                throw DownloadException(
                                    "Checksum mismatch: expected $expectedSha256 but got $actual " +
                                        "(corrupted download, or the repo changed underneath us)"
                                )
                            }
                        }
                    }
                }

                if (tmpFile.length() == 0L) {
                    throw DownloadException("Downloaded file is empty ($url)")
                }

                replaceReadOnly(tmpFile, target)

                Downloaded(
                    file = target,
                    sha256 = expectedHash,
                    bytes = target.length(),
                )
            } finally {
                runCatching { if (tmpFile.exists()) tmpFile.delete() }
            }
        }
    }

    /**
     * Re-applies the read-only flag to cached files. A file downloaded by an older build of
     * this app is still writable, and then every load fails on Android 14+ with a message that
     * points at nothing useful — this runs at startup to heal that.
     */
    suspend fun hardenCachedFiles(): Int = withContext(Dispatchers.IO) {
        var fixed = 0
        listCachedFiles().forEach { file ->
            if (file.canWrite()) {
                if (runCatching { file.setReadOnly() }.getOrDefault(false)) fixed++
            }
        }
        if (fixed > 0) Log.i(TAG, "Marked $fixed cached extension file(s) read-only")
        fixed
    }

    fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    } catch (t: Throwable) {
        Log.w(TAG, "sha256Of(${file.name}) failed", t)
        null
    }

    fun deleteExtension(filePath: String) {
        val file = File(filePath)
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                // Read-only files delete fine on ext4, but be explicit for odd filesystems.
                file.setWritable(true)
                file.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "deleteExtension($filePath) failed", t)
        }
    }

    fun provideExtensionsDir(): File = extensionsDir

    /** Every container we know how to open — not only `.cs3`. */
    fun listCachedFiles(): List<File> =
        extensionsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in PLUGIN_EXTENSIONS }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun replaceReadOnly(source: File, target: File) {
        if (target.exists()) {
            target.setWritable(true)
            target.delete()
        }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
        }
        if (!target.setReadOnly()) {
            Log.w(TAG, "Could not mark ${target.name} read-only; dex loading may be refused on Android 14+")
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.')
        val withExt = when {
            cleaned.isBlank() -> "extension.cs3"
            cleaned.substringAfterLast('.', "").lowercase() in PLUGIN_EXTENSIONS -> cleaned
            else -> "$cleaned.cs3"
        }
        return withExt.take(120)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /**
         * `fileHash` as published by community repos is `sha256-<hex>` (sometimes `md5-<hex>`), while we
         * compare plain hex. Strip the algorithm label, otherwise every install fails "checksum
         * mismatch" through no fault of the file — and keep it here, next to the only comparison.
         */
        fun normalizeHash(value: String?): String? = value
            ?.trim()
            ?.lowercase()
            ?.substringAfterLast('-')
            ?.takeIf { it.length >= 32 && it.all { c -> c in "0123456789abcdef" } }

        private const val TAG = "FMHubDownloader"
        val PLUGIN_EXTENSIONS = setOf("cs3", "zip", "apk")
    }
}
