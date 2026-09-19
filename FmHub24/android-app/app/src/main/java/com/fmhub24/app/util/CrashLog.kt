package com.fmhub24.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.fmhub24.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reporting that survives the failure mode this app is most exposed to: dying before any
 * screen exists.
 *
 * A plugin host has a class of bug that no amount of compile-time checking catches — a class the
 * device cannot link (`NoClassDefFoundError`), a dex the loader refuses, a library built for a newer
 * Android than `minSdk`. Those do not produce an error state in the UI; they produce a launch that
 * opens and immediately closes, with the explanation only in a logcat buffer most users never open.
 *
 * So the trace goes to a file in the app's own storage *before* the process dies, and the next
 * launch shows it on the splash screen (see [com.fmhub24.app.ui.screens.splash.SplashScreen]).
 * Nothing here swallows the crash: the previous handler still runs, so the system still records it
 * in logcat and in whatever the store's reporting is.
 */
object CrashLog {

    /** Same tag for every entry, so `adb logcat -s FMHubCrash` is the whole story. */
    const val TAG = "FMHubCrash"

    private const val FILE_NAME = "crash.log"
    private const val COUNTER_NAME = "crash-count"

    /**
     * Two crashes this close together is a loop, not a hiccup — enough for [SafeMode] to stop
     * loading plugin files so the app stays usable long enough to be read and fixed.
     */
    private const val LOOP_WINDOW_MILLIS = 10 * 60 * 1000L

    /** Do not let the report screen restart itself in a loop: one pop per this many millis. */
    private const val REPORT_THROTTLE_MILLIS = 5_000L

    /** Keep the log small enough that reading it back on the splash screen is never a hitch. */
    private const val MAX_BYTES = 256 * 1024
    private const val MAX_SHOWN_CHARS = 20_000

    @Volatile
    private var installed = false

    /** Kept so a caught error can be written without threading a Context through every caller. */
    @Volatile
    private var appContext: Context? = null

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * The same file in the app's *external* files dir: `files/` in internal storage needs `adb
     * run-as`, while `Android/data/com.fmhub24.app/files/` can be opened by any file manager on the
     * phone. A diagnostic nobody can read is not a diagnostic.
     */
    private fun externalFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    private fun counterFile(context: Context): File = File(context.filesDir, COUNTER_NAME)

    /** Consecutive crashes inside [LOOP_WINDOW_MILLIS]; the signal [SafeMode] acts on. */
    fun recentCrashCount(context: Context): Int = try {
        val f = counterFile(context)
        if (!f.exists()) 0
        else {
            val parts = f.readText().trim().split(' ', '\n', '\t').filter { it.isNotBlank() }
            val count = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val at = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at > LOOP_WINDOW_MILLIS) 0 else count
        }
    } catch (_: Throwable) {
        0
    }

    /**
     * Installs the default handler. Must be the first thing the [android.app.Application] does —
     * an exception thrown while Hilt builds the graph or while the launcher activity is created has
     * nowhere else to be written.
     */
    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(context, "uncaught on thread '${thread.name}'", error)
            } catch (_: Throwable) {
                // A failing logger must never replace the real exception.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Records an error that was caught (a coroutine that would otherwise kill the process, a plugin
     * file that refuses to load) without changing control flow.
     */
    fun record(where: String, error: Throwable) {
        val ctx = appContext ?: return
        try {
            write(ctx, where, error)
        } catch (_: Throwable) {
        }
    }

    /**
     * A diagnostic line with no exception behind it. Used by the plugin loader: the screen must not
     * explain how the app is built, but the *technical* reason still has to survive somewhere a
     * developer can read — this file and `Download/FMHub24-crash.txt`.
     *
     * Deliberately does NOT bump the crash counter, so a source that is simply down cannot push the
     * app into [SafeMode] the way a real crash does.
     */
    fun note(where: String, detail: String) {
        val ctx = appContext ?: return
        try {
            val entry = buildString {
                append("\n---- ")
                append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                append(" ----\n")
                append(where).append('\n')
                append(detail.trim()).append('\n')
            }
            Log.w(TAG, entry)
            val f = file(ctx)
            f.appendText(entry)
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast(MAX_BYTES / 2)
                try {
                    f.writeText(keep)
                } catch (_: Throwable) {
                }
            }
            runCatching { externalFile(ctx)?.appendText(entry) }
        } catch (_: Throwable) {
        }
    }

    /**
     * Copies the current log into the system Downloads folder under its own name. The loader calls
     * this once per sync so "send me Download/FMHub24-crash.txt" works on a phone with no cable.
     */
    fun publishDiagnostics(context: Context) {
        val text = read(context)?.trim().orEmpty()
        if (text.isEmpty()) return
        try {
            publishToDownloads(context, text.take(MAX_SHOWN_CHARS))
        } catch (_: Throwable) {
        }
    }

    /** The tail of the log, newest entries last — what the splash screen shows. */
    fun read(context: Context): String? = try {
        val f = file(context)
        if (!f.exists() || f.length() <= 0L) null
        else f.readText().takeLast(MAX_SHOWN_CHARS)
    } catch (_: Throwable) {
        null
    }

    /** Wiping the log also leaves [SafeMode]: it is keyed on recorded crashes, not on a flag. */
    fun clear(context: Context) {
        try {
            file(context).delete()
            counterFile(context).delete()
            externalFile(context)?.delete()
        } catch (_: Throwable) {
        }
    }

    private fun write(context: Context, where: String, error: Throwable) {
        val entry = buildString {
            append("\n---- ")
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            append(" ----\n")
            append(
                "FMHuB24 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                    "${BuildConfig.BUILD_TYPE} / apiVersion ${BuildConfig.PLUGIN_API_VERSION} " +
                    "/ CloudStream ${BuildConfig.CLOUDSTREAM_VERSION}\n"
            )
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append(where).append('\n')
            append(stackOf(error))
        }
        Log.e(TAG, entry)

        val f = file(context)
        f.appendText(entry)
        if (f.length() > MAX_BYTES) {
            // Keep the newest half: the oldest entries are almost never the current bug.
            val keep = f.readText().takeLast(MAX_BYTES / 2)
            try {
                f.writeText(keep)
            } catch (_: Throwable) {
            }
        }
        // Mirrored where a file manager can reach it without a cable.
        try {
            externalFile(context)?.appendText(entry)
        } catch (_: Throwable) {
        }

        bumpCrashCount(context)

        // Also drop a copy in the system Downloads folder: on a phone without a cable this is often
        // the only way to get the text out of the app and into a chat window. Best effort - it can
        // legitimately fail (no MediaStore, user refused the one-time prompt), and that must never
        // turn a reported crash into a second one.
        try {
            publishToDownloads(context, entry.trim())
        } catch (_: Throwable) {
        }

        // If the process is still in the foreground this pops the trace on screen; if Android
        // refuses a background activity start, the files above are still the report. Throttled,
        // because the report screen itself lives in another process of this same app and shares the
        // log file: without it, a failure there would pop another report, forever.
        if (allowReport(context)) {
            CrashReportActivity.show(context, entry.trim())
        }
    }

    private fun allowReport(context: Context): Boolean {
        return try {
            val marker = File(context.filesDir, "crashreport.ts")
            val now = System.currentTimeMillis()
            val last = marker.lastModified()
            if (now - last < REPORT_THROTTLE_MILLIS) return false
            marker.parentFile?.mkdirs()
            marker.writeText(now.toString())
            true
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Downloads/FMHub24-crash.txt via MediaStore (API 29+): writable by the app without any runtime
     * permission, and readable by the user with the Files app they already have.
     */
    private fun publishToDownloads(context: Context, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "FMHub24-crash.txt")
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        values.clear()
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun bumpCrashCount(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val next = if (recentCrashCount(context) > 0) recentCrashCount(context) + 1 else 1
            counterFile(context).writeText("$next\n$now")
        } catch (_: Throwable) {
        }
    }

    /** Full cause chain — the "Failed resolution of: Lcom/foo/Bar;" line lives in the cause. */
    fun stackOf(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
