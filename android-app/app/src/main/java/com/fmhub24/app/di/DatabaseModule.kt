package com.fmhub24.app.di

import android.content.Context
import androidx.room.Room
import com.fmhub24.app.data.local.AppDatabase
import com.fmhub24.app.data.local.dao.CachedExtensionDao
import com.fmhub24.app.data.local.dao.DownloadedContentDao
import com.fmhub24.app.data.local.dao.FavoriteDao
import com.fmhub24.app.data.local.dao.WatchProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fmhub24.db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()

    @Provides
    fun provideCachedExtensionDao(db: AppDatabase): CachedExtensionDao = db.cachedExtensionDao()

    @Provides
    fun provideDownloadedContentDao(db: AppDatabase): DownloadedContentDao = db.downloadedContentDao()
}
