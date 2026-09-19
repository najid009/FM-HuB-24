package com.fmhub24.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fmhub24.app.R
import com.fmhub24.app.data.local.dao.DownloadedContentDao
import com.fmhub24.app.download.hls.HlsDownloadResult
import com.fmhub24.app.download.hls.HlsSegmentDownloader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class HlsDownloadService : Service() {
    @Inject lateinit var dao: DownloadedContentDao

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_ID)?.let { activeJobs[it]?.cancel() }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val job = intent?.let(::readJob) ?: run {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIFICATION_ID, notification(job.name, "Preparing download…", 0, 0))
        val worker = scope.launch {
            val downloader = HlsSegmentDownloader()
            try {
                dao.updateStatus(job.id, "downloading")
                val result = downloader.download(
                    playlistUrl = job.playlistUrl,
                    outputFile = File(job.outputPath),
                    headers = job.headers,
                    referer = job.referer,
                ) { downloaded, total, bytes ->
                    dao.updateStatus(job.id, "downloading:$downloaded/$total:$bytes")
                    val percent = if (total == 0) 0 else downloaded * 100 / total
                    updateNotification(job.name, "Downloading HLS segments", percent, total)
                }
                when (result) {
                    is HlsDownloadResult.Completed -> dao.updateStatus(job.id, "completed")
                    is HlsDownloadResult.Unsupported -> {
                        File(job.outputPath).delete()
                        dao.updateStatus(job.id, "failed:${result.reason}")
                    }
                    is HlsDownloadResult.Failed -> {
                        File(job.outputPath).delete()
                        dao.updateStatus(job.id, "failed:${result.reason}")
                    }
                }
            } catch (_: CancellationException) {
                dao.updateStatus(job.id, "paused")
            } catch (error: Throwable) {
                File(job.outputPath).delete()
                dao.updateStatus(job.id, "failed:${error.message ?: "HLS download failed"}")
            } finally {
                activeJobs.remove(job.id)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        activeJobs[job.id] = worker
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun readJob(intent: Intent): HlsJob? {
        val id = intent.getStringExtra(EXTRA_ID) ?: return null
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Video"
        val playlistUrl = intent.getStringExtra(EXTRA_URL) ?: return null
        val outputPath = intent.getStringExtra(EXTRA_OUTPUT) ?: return null
        val keys = intent.getStringArrayListExtra(EXTRA_HEADER_KEYS).orEmpty()
        val values = intent.getStringArrayListExtra(EXTRA_HEADER_VALUES).orEmpty()
        val headers = keys.zip(values).toMap()
        return HlsJob(id, name, playlistUrl, outputPath, headers, intent.getStringExtra(EXTRA_REFERER))
    }

    private fun notification(name: String, text: String, progress: Int, max: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(name)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(progress in 0 until 100)
            .setProgress(max, progress, max == 0)
            .build()

    private fun updateNotification(name: String, text: String, progress: Int, max: Int) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(name, text, progress, max),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "HLS downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    data class HlsJob(
        val id: String,
        val name: String,
        val playlistUrl: String,
        val outputPath: String,
        val headers: Map<String, String>,
        val referer: String?,
    )

    companion object {
        private const val CHANNEL_ID = "fmhub24-hls-downloads"
        private const val NOTIFICATION_ID = 24026
        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_URL = "url"
        private const val EXTRA_OUTPUT = "output"
        private const val EXTRA_REFERER = "referer"
        private const val EXTRA_HEADER_KEYS = "header_keys"
        private const val EXTRA_HEADER_VALUES = "header_values"

        fun intent(context: android.content.Context, job: HlsJob): Intent =
            Intent(context, HlsDownloadService::class.java).apply {
                putExtra(EXTRA_ID, job.id)
                putExtra(EXTRA_NAME, job.name)
                putExtra(EXTRA_URL, job.playlistUrl)
                putExtra(EXTRA_OUTPUT, job.outputPath)
                putExtra(EXTRA_REFERER, job.referer)
                putStringArrayListExtra(EXTRA_HEADER_KEYS, ArrayList(job.headers.keys))
                putStringArrayListExtra(EXTRA_HEADER_VALUES, ArrayList(job.headers.values))
            }

        fun cancel(context: android.content.Context, id: String) {
            context.startService(Intent(context, HlsDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
            })
        }

        private const val ACTION_CANCEL = "com.fmhub24.app.download.action.CANCEL_HLS"
    }
}
