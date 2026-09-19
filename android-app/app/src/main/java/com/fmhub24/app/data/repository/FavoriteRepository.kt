package com.fmhub24.app.data.repository

import com.fmhub24.app.data.local.dao.FavoriteDao
import com.fmhub24.app.data.local.entity.Favorite
import com.fmhub24.app.plugins.cloudstream.SearchResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao
) {
    fun getAllFavorites(): Flow<List<Favorite>> = favoriteDao.getAllFavorites()

    fun isFavorite(url: String): Flow<Boolean> = favoriteDao.isFavorite(url)

    suspend fun addFavorite(searchResponse: SearchResponse) {
        val fav = Favorite(
            url = searchResponse.url,
            name = searchResponse.name,
            posterUrl = searchResponse.posterUrl,
            apiName = searchResponse.apiName,
            type = searchResponse.type?.name
        )
        favoriteDao.addFavorite(fav)
    }

    suspend fun addFavoriteFromLoad(url: String, name: String, posterUrl: String?, apiName: String, type: String?) {
        val fav = Favorite(
            url = url,
            name = name,
            posterUrl = posterUrl,
            apiName = apiName,
            type = type
        )
        favoriteDao.addFavorite(fav)
    }

    suspend fun removeFavorite(url: String) {
        favoriteDao.removeFavorite(url)
    }
}
