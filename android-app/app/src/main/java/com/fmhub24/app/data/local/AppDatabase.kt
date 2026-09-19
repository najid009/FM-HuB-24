package com.fmhub24.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fmhub24.app.data.local.dao.CachedExtensionDao
import com.fmhub24.app.data.local.dao.DownloadedContentDao
import com.fmhub24.app.data.local.dao.FavoriteDao
import com.fmhub24.app.data.local.dao.WatchProgressDao
import com.fmhub24.app.data.local.entity.CachedExtension
import com.fmhub24.app.data.local.entity.DownloadedContent
import com.fmhub24.app.data.local.entity.Favorite
import com.fmhub24.app.data.local.entity.WatchProgress

@Database(
    entities = [Favorite::class, WatchProgress::class, CachedExtension::class, DownloadedContent::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun cachedExtensionDao(): CachedExtensionDao
    abstract fun downloadedContentDao(): DownloadedContentDao

    companion object {
        /**
         * v1 -> v2: the cache row now carries everything the loader needs to decide and explain —
         * `fileUrl` (re-download source), `status`, the user's own `enabled` switch, and the
         * manifest/hash fields that turn "0 providers" into a named cause.
         *
         * Rebuilt rather than ALTERed, and deliberately lossy: `cached_extensions` is a
         * description of downloadable, re-derivable state, while v1 rows cannot supply the new
         * NOT NULL `fileUrl` at all. Dropping it costs one re-download on next launch; a partial
         * ALTER would either fail Room's schema identity check or leave rows the loader cannot use.
         * Favorites and watch progress are untouched by this migration.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS cached_extensions")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_extensions (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        localFilePath TEXT NOT NULL,
                        loadedAt INTEGER NOT NULL,
                        fileUrl TEXT NOT NULL,
                        status TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        pluginClassName TEXT,
                        apiVersion INTEGER,
                        fileHash TEXT,
                        sizeBytes INTEGER,
                        sourceRepoUrl TEXT,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS downloaded_content (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        posterUrl TEXT,
                        apiName TEXT NOT NULL,
                        episodeName TEXT,
                        localPath TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmScheme TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmLicenseUrl TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmKeySetId TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN requestHeadersJson TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN referer TEXT")
            }
        }
    }
}
