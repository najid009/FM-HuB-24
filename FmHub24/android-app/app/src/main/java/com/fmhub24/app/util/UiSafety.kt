package com.fmhub24.app.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A ViewModel that launches work must not be able to kill the process.
 *
 * `viewModelScope.launch { }` sends anything it does not catch to the thread's uncaught handler,
 * which on Android means: the app closes. No error screen, no state, nothing to screenshot — the
 * exact symptom ("open the app and it throws me out") that is impossible to debug from a phone.
 *
 * [safeLaunch] keeps the crash visible (logcat + [CrashLog], so it is reported on the next launch)
 * while letting the screen render its error state instead.
 */
fun ViewModel.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val handler = CoroutineExceptionHandler { _, error ->
        // CancellationException never reaches a CoroutineExceptionHandler, so this is only for
        // real failures. Report first, then let the caller show it in its own UI.
        CrashLog.record("uncaught in ${this.javaClass.simpleName}", error)
        try {
            onError(error)
        } catch (_: Throwable) {
        }
    }
    return viewModelScope.launch(context + handler, start = start, block = block)
}
