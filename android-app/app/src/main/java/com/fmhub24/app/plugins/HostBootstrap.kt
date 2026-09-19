package com.fmhub24.app.plugins

import android.content.Context
import android.util.Log
import com.fmhub24.app.BuildConfig
import com.fmhub24.app.util.CrashLog
import com.lagradost.api.setContext
import com.lagradost.cloudstream3.app
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * The one place the host touches CloudStream's own runtime, and it is deliberately *not* in
 * `Application.onCreate`.
 *
 * Two things must exist before any provider can do useful work:
 *
 *  * the library's global context (`com.lagradost.api.setContext`) — WebView, cookie jar and
 *    anything calling `getContext()` resolves through it; without it a provider just returns nothing;
 *  * `app.baseClient`, the [OkHttpClient] every provider inherits (`Requests.baseClient`). CloudStream
 *    installs both from `CloudStreamApp.attachBaseContext`/`onCreate`; a host that copies that into its
 *    `Application` looks correct and then fails in the worst way: the very first class load of a
 *    library type happens while the process is still starting, so a linking or verification problem
 *    kills the app before any activity, handler or screen exists — "opens and closes", with nothing
 *    to read.
 *
 * Doing it here instead means a failure is an ordinary, reportable load error: it runs on a worker
 * thread inside the loader, is caught as a [Throwable], lands in [CrashLog] and reaches the Splash and
 * Settings screens as text.
 */
object HostBootstrap {

    const val TAG = "FMHubBootstrap"

    @Volatile
    private var done = false

    @Volatile
    private var lastError: String? = null

    /** True once the library has a context and a client. False means providers cannot work yet. */
    val isReady: Boolean get() = done

    /** Why it failed, for the UI. Null when nothing has gone wrong. */
    val failureReason: String? get() = lastError

    /**
     * Idempotent and total: every step is guarded, and the return value says what happened instead of
     * throwing. Safe to call from every load path.
     */
    fun ensure(context: Context): Boolean {
        if (done) return true
        synchronized(this) {
            if (done) return true
            try {
                // WeakReference<Any> on purpose: the library signature is `setContext(WeakReference<Any>)`
                // and WeakReference is invariant, so a WeakReference<Context> would not type-check.
                setContext(WeakReference<Any>(context.applicationContext))
            } catch (t: Throwable) {
                lastError = "The CloudStream host context could not be installed: ${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "setContext failed", t)
                CrashLog.record("HostBootstrap.setContext", t)
                return false
            }
            try {
                app.baseClient = buildHttpClient(context)
                done = true
                Log.i(TAG, "Host runtime ready (CloudStream ${BuildConfig.CLOUDSTREAM_VERSION})")
            } catch (t: Throwable) {
                lastError = "The HTTP client used by plugins could not be installed: " +
                    "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "app.baseClient failed", t)
                CrashLog.record("HostBootstrap.baseClient", t)
                return false
            }
            return true
        }
    }

    /**
     * The client plugins get: redirects (many hosts 301 to a mirror), a small response cache, and
     * generous timeouts because scraping sites are frequently slow rather than dead.
     */
    private fun buildHttpClient(context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "plugin_http_cache"), 50L * 1024 * 1024))
            .addInterceptor(logging)
            .build()
    }
}
