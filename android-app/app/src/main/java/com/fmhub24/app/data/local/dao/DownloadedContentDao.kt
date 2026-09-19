package com.fmhub24.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fmhub24.app.data.local.entity.DownloadedContent
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedContentDao {
    @Query("SELECT * FROM downloaded_content ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadedContent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadedContent)

    @Delete
    suspend fun delete(item: DownloadedContent)

    @Query("DELETE FROM downloaded_content WHERE id = :id")
    suspend fun deleteById(id: String)
}
