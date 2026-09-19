package com.fmhub24.app.data.local.dao

import androidx.room.*
import com.fmhub24.app.data.local.entity.Favorite
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<Favorite>>

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getFavorite(url: String): Favorite?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url)")
    fun isFavorite(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun removeFavorite(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
