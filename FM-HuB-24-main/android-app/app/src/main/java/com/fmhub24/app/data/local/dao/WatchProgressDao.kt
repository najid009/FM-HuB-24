package com.fmhub24.app.data.local.dao

import androidx.room.*
import com.fmhub24.app.data.local.entity.WatchProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun getAllProgress(): Flow<List<WatchProgress>>

    @Query("SELECT * FROM watch_progress WHERE url = :url LIMIT 1")
    suspend fun getProgress(url: String): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE url = :url LIMIT 1")
    fun getProgressFlow(url: String): Flow<WatchProgress?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgress)

    @Query("DELETE FROM watch_progress WHERE url = :url")
    suspend fun deleteProgress(url: String)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAll()
}
