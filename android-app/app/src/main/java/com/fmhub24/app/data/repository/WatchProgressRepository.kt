package com.fmhub24.app.data.repository

import com.fmhub24.app.data.local.dao.WatchProgressDao
import com.fmhub24.app.data.local.entity.WatchProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepository @Inject constructor(
    private val dao: WatchProgressDao
) {
    fun getAllProgress(): Flow<List<WatchProgress>> = dao.getAllProgress()

    fun getProgressFlow(url: String): Flow<WatchProgress?> = dao.getProgressFlow(url)

    suspend fun getProgress(url: String): WatchProgress? = dao.getProgress(url)

    suspend fun saveProgress(
        url: String,
        name: String,
        posterUrl: String?,
        apiName: String,
        position: Long,
        duration: Long,
        episodeData: String? = null,
        episodeName: String? = null
    ) {
        val progress = WatchProgress(
            url = url,
            name = name,
            posterUrl = posterUrl,
            apiName = apiName,
            position = position,
            duration = duration,
            episodeData = episodeData,
            episodeName = episodeName,
            updatedAt = System.currentTimeMillis()
        )
        dao.saveProgress(progress)
    }

    suspend fun deleteProgress(url: String) = dao.deleteProgress(url)
}
