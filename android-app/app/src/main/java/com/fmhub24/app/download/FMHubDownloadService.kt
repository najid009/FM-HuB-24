package com.fmhub24.app.download

import android.app.Notification
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class FMHubDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    androidx.media3.exoplayer.R.string.exo_download_notification_channel_name,
    0
) {
    override fun getDownloadManager(): DownloadManager = Companion.downloadManager(this)

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = notificationHelper(this).buildProgressNotification(
        this,
        android.R.drawable.stat_sys_download,
        null,
        null,
        downloads,
        notMetRequirements
    )

    companion object {
        private const val JOB_ID = 24024
        private const val FOREGROUND_NOTIFICATION_ID = 24025
        private const val CHANNEL_ID = "fmhub24-downloads"

        @Volatile private var cache: Cache? = null
        @Volatile private var manager: DownloadManager? = null
        @Volatile private var notification: DownloadNotificationHelper? = null

        fun downloadManager(context: Context): DownloadManager = manager ?: synchronized(this) {
            manager ?: DownloadManager(
                context.applicationContext,
                StandaloneDatabaseProvider(context.applicationContext),
                downloadCache(context),
                DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
                Executors.newFixedThreadPool(2)
            ).apply {
                maxParallelDownloads = 2
            }.also { manager = it }
        }

        fun downloadCache(context: Context): Cache = cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.filesDir, "media_download_cache"),
                NoOpCacheEvictor(),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { cache = it }
        }

        /**
         * HLS is handled by the segment-aware foreground worker. Keeping this
         * facade here gives callers one download service entry point while the
         * Media3 DownloadService continues to own VIDEO/DASH cache downloads.
         */
        fun enqueueHls(context: Context, job: HlsDownloadService.HlsJob) {
            ContextCompat.startForegroundService(context, HlsDownloadService.intent(context, job))
        }

        private fun notificationHelper(context: Context): DownloadNotificationHelper =
            notification ?: synchronized(this) {
                notification ?: DownloadNotificationHelper(context.applicationContext, CHANNEL_ID)
                    .also { notification = it }
            }
    }
}
