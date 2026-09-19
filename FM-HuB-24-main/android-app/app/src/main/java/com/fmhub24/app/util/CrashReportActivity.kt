package com.fmhub24.app.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * What to show when the process is already dying.
 *
 * The crash handler in [CrashLog] writes the trace to disk first and then starts this activity: if
 * the failure happens while the app is still in the foreground (which a crash during launch usually
 * is) the user reads it immediately instead of guessing. It runs in its own process
 * (`android:process=":crashreport"`), because the main process is about to be killed and a dialog
 * living inside it dies with it.
 *
 * Deliberately dumb: no Hilt, no Compose, no theme attributes beyond a framework one, no dependency
 * on any class that could itself be the reason for the crash. It reads the same file the splash screen
 * reads, so a background-activity-start restriction (Android 10+) that hides this screen costs
 * nothing: the log is still on disk and shows up on the next launch.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = savedInstanceState?.getString(STATE_LOG)
            ?: intent.getStringExtra(EXTRA_TEXT)
            ?: CrashLog.read(this)
            ?: "(no crash log found — the log may live in Android/data/com.fmhub24.app/files/crash.log)"

        // Named traceView, not text: a local called text shadows the TextView.text property
        // inside every nested apply { } block, so "text = ..." there means reassign the local.
        val traceView = TextView(this).apply {
            setText(log)
            setTextColor(Color.parseColor("#FFE6E6E6"))
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.parseColor("#FF141414"))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(12))
            addView(
                TextView(context).apply {
                    text = "FMHuB24 crashed"
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                }
            )
            addView(
                TextView(context).apply {
                    text = "The full trace below is also in files/crash.log inside the app's own storage " +
                        "(a copy sits in Android/data/com.fmhub24.app/files/crash.log, which a file " +
                        "manager can open). Send it with your Android version — it is the actual cause, " +
                        "not a network problem."
                    setTextColor(Color.parseColor("#FFB3B3B3"))
                    textSize = 12f
                    setPadding(0, dp(8), 0, dp(12))
                }
            )
            addView(traceView)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        Button(context).apply {
                            text = "Copy"
                            setOnClickListener {
                                val clip = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                                clip?.setPrimaryClip(ClipData.newPlainText("FMHuB24 crash", log))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    addView(
                        Button(context).apply {
                            text = "Share"
                            setOnClickListener {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "FMHuB24 crash log")
                                    putExtra(Intent.EXTRA_TEXT, log)
                                }
                                try {
                                    startActivity(Intent.createChooser(send, "Send crash log"))
                                } catch (t: Throwable) {
                                    Toast.makeText(context, "No app can take the text", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    addView(
                        Button(context).apply {
                            text = "Clear & retry"
                            setOnClickListener {
                                // Removing the log also leaves safe mode: the loader only skips .cs3
                                // files while recent crashes are on record.
                                CrashLog.clear(this@CrashReportActivity)
                                val launch = packageManager
                                    .getLaunchIntentForPackage(packageName)
                                if (launch != null) {
                                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    startActivity(launch)
                                }
                                finishAffinity()
                            }
                        }
                    )
                }
            )
        }

        setContentView(
            ScrollView(this).apply {
                addView(column)
                setBackgroundColor(Color.BLACK)
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LOG, (intent.getStringExtra(EXTRA_TEXT) ?: CrashLog.read(this)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_TEXT = "crashText"
        private const val STATE_LOG = "crashLog"

        fun intentFor(context: Context, text: String): Intent =
            Intent(context, CrashReportActivity::class.java)
                .putExtra(EXTRA_TEXT, text)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )

        /** Best effort: a crash handler must never be the thing that crashes the app. */
        fun show(context: Context, text: String) {
            try {
                context.startActivity(intentFor(context, text))
            } catch (_: Throwable) {
                // Blocked (background activity start on Android 10+) or already dying: the file is
                // still on disk, and the splash screen shows it on the next launch.
            }
        }
    }
}
