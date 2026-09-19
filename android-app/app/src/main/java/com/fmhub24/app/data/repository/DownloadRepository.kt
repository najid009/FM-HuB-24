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
import com.fmhub24.app.media.DrmConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
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

    /** Queues progressive, HLS (.m3u8), and DASH (.mpd) URLs in Media3's persistent manager. */
    suspend fun enqueue(
        sourceUrl: String,
        name: String,
        posterUrl: String?,
        apiName: String,
        episodeName: String?,
        drm: DrmConfig? = null,
    ): Result<DownloadedContent> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.nameUUIDFromBytes(sourceUrl.toByteArray()).toString()
            val request = DownloadRequest.Builder(id, Uri.parse(sourceUrl))
                .setMimeType(mimeTypeFor(sourceUrl))
                .setData(Util.getUtf8Bytes("$name|${episodeName.orEmpty()}"))
                .apply { drm?.offlineKeySetId?.let(::setKeySetId) }
                .build()
            DownloadService.sendAddDownload(
                context,
                FMHubDownloadService::class.java,
                request,
                /* foreground= */ true,
            )
            DownloadedContent(
                id = id,
                name = name,
                posterUrl = posterUrl,
                apiName = apiName,
                episodeName = episodeName,
                // Media3 owns the bytes in SimpleCache; sourceUrl is the playback key.
                localPath = sourceUrl,
                sourceUrl = sourceUrl,
                drmScheme = drm?.scheme?.toString(),
                drmLicenseUrl = drm?.licenseUrl,
                drmKeySetId = drm?.offlineKeySetId?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                status = if (drm?.offlineKeySetId != null) "queued-drm" else "queued",
            ).also { dao.upsert(it) }
        }
    }

    suspend fun delete(item: DownloadedContent) = withContext(Dispatchers.IO) {
        DownloadService.sendRemoveDownload(
            context,
            FMHubDownloadService::class.java,
            item.id,
            /* foreground= */ false,
        )
        dao.delete(item)
    }

    private fun mimeTypeFor(url: String): String? = when {
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
        else -> null
    }
}
