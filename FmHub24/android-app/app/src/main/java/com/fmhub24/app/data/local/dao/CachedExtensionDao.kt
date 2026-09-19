package com.fmhub24.app.data.local.dao

import androidx.room.*
import com.fmhub24.app.data.local.entity.CachedExtension
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedExtensionDao {

    @Query("SELECT * FROM cached_extensions")
    suspend fun getAll(): List<CachedExtension>

    @Query("SELECT * FROM cached_extensions ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CachedExtension>>

    @Query("SELECT * FROM cached_extensions WHERE enabled = 1 AND status = 'active'")
    suspend fun getEnabled(): List<CachedExtension>

    @Query("SELECT * FROM cached_extensions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedExtension?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cached: CachedExtension)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedExtension>)

    @Query("UPDATE cached_extensions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    // Column names here are Room's defaults (field names as written), which is also exactly what
    // AppDatabase.MIGRATION_1_2 creates — keep the two in step or Room's schema check fails.
    @Query("UPDATE cached_extensions SET lastError = :error WHERE id = :id")
    suspend fun setLastError(id: String, error: String?)

    @Query("DELETE FROM cached_extensions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_extensions")
    suspend fun clearAll()
}
