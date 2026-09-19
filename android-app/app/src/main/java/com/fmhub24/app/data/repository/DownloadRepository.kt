package com.fmhub24.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.fmhub24.app.data.local.dao.DownloadedContentDao
import com.fmhub24.app.data.local.entity.DownloadedContent
import com.fmhub24.app.download.FMHubDownloadService
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

    /** Queues progressive, HLS (.m3u8), and DASH (.mpd) URLs in Media3's persistent manager. */
    suspend fun enqueue(
        sourceUrl: String,
        name: String,
        posterUrl: String?,
        apiName: String,
        episodeName: String?,
    ): Result<DownloadedContent> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.nameUUIDFromBytes(sourceUrl.toByteArray()).toString()
            val request = DownloadRequest.Builder(id, Uri.parse(sourceUrl))
                .setMimeType(mimeTypeFor(sourceUrl))
                .setData(Util.getUtf8Bytes("$name|${episodeName.orEmpty()}"))
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
                status = "queued",
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
