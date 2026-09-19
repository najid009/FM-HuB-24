package com.fmhub24.app.data.repository

import android.content.Context
import com.fmhub24.app.data.local.dao.DownloadedContentDao
import com.fmhub24.app.data.local.entity.DownloadedContent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadedContentDao,
    private val httpClient: OkHttpClient,
) {
    fun observeDownloads(): Flow<List<DownloadedContent>> = dao.observeAll()

    suspend fun download(
        sourceUrl: String,
        name: String,
        posterUrl: String?,
        apiName: String,
        episodeName: String?,
    ): Result<DownloadedContent> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val id = UUID.nameUUIDFromBytes(sourceUrl.toByteArray()).toString()
            val safeName = (name + (episodeName?.let { "_$it" } ?: ""))
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(100)
            val target = File(downloadsDir, "${safeName}_$id.mp4")
            val temp = File(downloadsDir, "${target.name}.part")
            val request = Request.Builder().url(sourceUrl).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty download response")
                body.byteStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            }
            check(temp.length() > 0L) { "Downloaded file is empty" }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not save downloaded video" }
            DownloadedContent(
                id = id,
                name = name,
                posterUrl = posterUrl,
                apiName = apiName,
                episodeName = episodeName,
                localPath = target.absolutePath,
                sourceUrl = sourceUrl,
            ).also { dao.upsert(it) }
        }
    }

    suspend fun delete(item: DownloadedContent) = withContext(Dispatchers.IO) {
        File(item.localPath).delete()
        dao.delete(item)
    }
}
