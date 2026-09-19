package com.fmhub24.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.fmhub24.app.data.local.dao.DownloadedContentDao
import com.fmhub24.app.data.local.entity.DownloadedContent
import com.fmhub24.app.download.FMHubDownloadService
import com.fmhub24.app.download.HlsDownloadService
import com.fmhub24.app.media.DrmConfig
import com.fmhub24.app.plugins.cloudstream.ExtractorLinkType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import java.io.File
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadedContentDao,
) {
    fun observeDownloads(): Flow<List<DownloadedContent>> = dao.observeAll()

    suspend fun syncStatuses() = withContext(Dispatchers.IO) {
        dao.getAll().forEach { item ->
            if (item.localPath != item.sourceUrl && item.status == "hls-queued") {
                dao.updateStatus(item.id, "hls-starting")
                FMHubDownloadService.enqueueHls(
                    context,
                    HlsDownloadService.HlsJob(
                        id = item.id,
                        name = item.name,
                        playlistUrl = item.sourceUrl,
                        outputPath = item.localPath,
                        headers = parseHeaders(item.requestHeadersJson),
                        referer = item.referer,
                    ),
                )
                return@forEach
            }
            val download = runCatching {
                FMHubDownloadService.downloadManager(context).downloadIndex.getDownload(item.id)
            }.getOrNull()
            val status = when (download?.state) {
                Download.STATE_COMPLETED -> "completed"
                Download.STATE_FAILED -> "failed"
                Download.STATE_DOWNLOADING -> "downloading"
                Download.STATE_QUEUED -> "queued"
                Download.STATE_STOPPED -> "paused"
                else -> item.status
            }
            if (status != item.status) dao.updateStatus(item.id, status)
        }
    }

    /** Queues HLS through the custom segment service and other compatible streams through Media3. */
    suspend fun enqueue(
        sourceUrl: String,
        name: String,
        posterUrl: String?,
        apiName: String,
        episodeName: String?,
        streamType: ExtractorLinkType,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        drm: DrmConfig? = null,
    ): Result<DownloadedContent> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.nameUUIDFromBytes(sourceUrl.toByteArray()).toString()
            val isHls = streamType == ExtractorLinkType.M3U8 && drm == null
            val localPath = if (isHls) {
                File(context.filesDir, "media_downloads/$id.ts").absolutePath
            } else sourceUrl
            DownloadedContent(
                id = id,
                name = name,
                posterUrl = posterUrl,
                apiName = apiName,
                episodeName = episodeName,
                // Media3 owns non-HLS bytes in SimpleCache; custom HLS stores an atomic local file.
                localPath = localPath,
                sourceUrl = sourceUrl,
                drmScheme = drm?.scheme?.toString(),
                drmLicenseUrl = drm?.licenseUrl,
                drmKeySetId = drm?.offlineKeySetId?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                requestHeadersJson = JSONObject(headers).toString(),
                referer = referer,
                status = if (isHls) "hls-queued" else if (drm?.offlineKeySetId != null) "queued-drm" else "queued",
            ).also { item ->
                dao.upsert(item)
                if (isHls) {
                    val job = HlsDownloadService.HlsJob(
                        id = id,
                        name = name,
                        playlistUrl = sourceUrl,
                        outputPath = localPath,
                        headers = headers,
                        referer = referer,
                    )
                    FMHubDownloadService.enqueueHls(context, job)
                } else {
                    val request = DownloadRequest.Builder(id, Uri.parse(sourceUrl))
                        .setMimeType(mimeTypeFor(sourceUrl, streamType))
                        .setData(Util.getUtf8Bytes("$name|${episodeName.orEmpty()}"))
                        .apply { drm?.offlineKeySetId?.let(::setKeySetId) }
                        .build()
                    DownloadService.sendAddDownload(
                        context,
                        FMHubDownloadService::class.java,
                        request,
                        /* foreground= */ true,
                    )
                }
            }
        }
    }

    suspend fun delete(item: DownloadedContent) = withContext(Dispatchers.IO) {
        if (item.localPath != item.sourceUrl) {
            HlsDownloadService.cancel(context, item.id)
            File(item.localPath).delete()
            File("${item.localPath}.part").delete()
        } else {
            DownloadService.sendRemoveDownload(
                context,
                FMHubDownloadService::class.java,
                item.id,
                /* foreground= */ false,
            )
        }
        dao.delete(item)
    }

    private fun mimeTypeFor(url: String, streamType: ExtractorLinkType): String? = when {
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
        streamType == ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
        streamType == ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
        else -> null
    }

    private fun parseHeaders(value: String?): Map<String, String> {
        val json = runCatching { JSONObject(value ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { key -> json.optString(key) }
    }
}
