package com.fmhub24.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.fmhub24.app.util.CrashLog
import dagger.hilt.android.HiltAndroidApp

/**
 * Host bootstrap - deliberately tiny.
 *
 * `Application` is the one place where a thrown `Throwable` cannot be turned into an error screen:
 * if the process dies while the activity is being created there is nothing left to show the user, and
 * from the outside the app simply "closes on open". So this class does exactly two things, and the
 * second one is only bookkeeping.
 *
 * Everything CloudStream-related - installing the library's global context and `app.baseClient`, the
 * two things a provider needs to make a request at all - lives in
 * [com.fmhub24.app.plugins.HostBootstrap] and runs on the loader's worker thread instead. That is not
 * laziness: touching a library type during `attachBaseContext`/`onCreate` makes class verification
 * part of the boot path, and a verification or linking problem there is uncatchable in practice
 * (it can surface as a crash before this object finishes, before the handler exists, or before any
 * window can show it). A provider that cannot run is a visible error in the UI; an app that will not
 * open is a bug report nobody can file.
 */
@HiltAndroidApp
class FMHub24App : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Before onCreate, because ContentProviders (androidx.startup initializers pulled in by
        // media3/emoji2/profileinstaller) run *between* attachBaseContext and onCreate - a crash in
        // one of them would otherwise happen while nothing has been installed yet. Guarded, since a
        // logger must never be the reason for the crash it is trying to record.
        try {
            CrashLog.install(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Crash logging is unavailable", t)
        }
    }

    private companion object {
        const val TAG = "FMHubApp"
    }
}
