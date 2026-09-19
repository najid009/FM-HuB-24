package com.fmhub24.app.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The screen for when the app cannot even draw its own UI.
 *
 * Deliberately built out of plain framework Views: no Compose, no Hilt, no navigation graph, no XML,
 * no theme attributes. If one of those is what blew up, the reporter has to be something that cannot
 * fail for the same reason — otherwise the failure mode stays "tap the icon, the app disappears".
 *
 * It is not a place to hide bugs: the trace is shown verbatim, with the log file path and the logcat
 * command, so the report that reaches a developer is the real one.
 */
object CrashScreen {

    fun view(context: Context, error: Throwable): ScrollView {
        val trace = CrashLog.stackOf(error)
        val scroll = ScrollView(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 28), dp(context, 18), dp(context, 18))
        }

        column.addView(
            TextView(context).apply {
                text = "FMHuB24 could not start"
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        column.addView(
            TextView(context).apply {
                text = "${error.javaClass.name}\n${error.message ?: "(no message)"}"
                setTextColor(Color.parseColor("#FFFF8A80"))
                textSize = 13f
                setPadding(0, dp(context, 10), 0, 0)
            }
        )

        column.addView(
            TextView(context).apply {
                text =
                    "This is the real failure, not a network problem: the app died before it could " +
                        "draw a screen. Copy the text below (or the file files/crash.log in the app's " +
                        "own storage, which keeps every crash since the app was installed) and send it " +
                        "with the Android version of this device.\n\n" +
                        "adb logcat -b crash -d   ·   adb shell run-as com.fmhub24.app cat files/crash.log"
                setTextColor(Color.parseColor("#FFB3B3B3"))
                textSize = 12f
                setPadding(0, dp(context, 14), 0, 0)
            }
        )

        column.addView(
            TextView(context).apply {
                text = trace
                setTextColor(Color.parseColor("#FFE6E6E6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                gravity = Gravity.TOP
                setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
                setBackgroundColor(Color.parseColor("#FF141414"))
                setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 14) }
        )

        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            Button(context).apply {
                text = "Copy"
                setOnClickListener {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clip?.setPrimaryClip(ClipData.newPlainText("FMHuB24 crash", "${error.javaClass.name}: ${error.message}\n\n$trace"))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
        )
        buttons.addView(
            Button(context).apply {
                text = "Close"
                setOnClickListener { (context as? Activity)?.finish() }
            }
        )
        column.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
        )

        scroll.addView(column)
        scroll.setBackgroundColor(Color.BLACK)
        return scroll
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
