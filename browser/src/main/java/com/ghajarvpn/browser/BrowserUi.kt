package com.ghajarvpn.browser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.ghajarvpn.browser.network.BrowserRouteState

/** Ghajar browser design tokens: dark navy, gold, ivory. Purple is never used. */
object BrowserUi {
    const val NAVY = 0xFF071B2E.toInt()
    const val NAVY_RAISED = 0xFF0E2C49.toInt()
    const val NAVY_SOFT = 0xFF12395E.toInt()
    const val GOLD = 0xFFB48B32.toInt()
    const val GOLD_SOFT = 0xFFD9B15C.toInt()
    const val IVORY = 0xFFF6F1E4.toInt()
    const val IVORY_DIM = 0xFFEDE6D3.toInt()
    const val INK = 0xFF12202B.toInt()
    const val MUTED_ON_DARK = 0xFF9DB0C2.toInt()
    const val MUTED_ON_LIGHT = 0xFF64686C.toInt()
    const val GREEN = 0xFF1E7F4F.toInt()
    const val RED = 0xFFB3261E.toInt()

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun pill(context: Context, fillColor: Int, radiusDp: Int = 22, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != 0 && strokeDp > 0) setStroke(dp(context, strokeDp), strokeColor)
        }

    fun badge(context: Context, fillColor: Int, radiusDp: Int = 10): GradientDrawable = pill(context, fillColor, radiusDp)

    fun label(context: Context, text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            if (bold) setTypeface(Typeface.DEFAULT_BOLD)
        }

    fun tint(view: View, color: Int) {
        if (view is android.widget.ImageView) view.setColorFilter(color)
    }

    fun colorForState(state: BrowserRouteState): Int = when (state) {
        BrowserRouteState.CONNECTED -> GREEN
        BrowserRouteState.CONNECTING -> GOLD_SOFT
        BrowserRouteState.ERROR, BrowserRouteState.UNAVAILABLE -> RED
        BrowserRouteState.IDLE -> MUTED_ON_DARK
    }

    fun htmlEscape(value: String): String = buildString(value.length) {
        for (ch in value) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }

    fun jsString(value: String): String = htmlEscape(value).replace("\\", "\\\\").replace("'", "\\'")

    fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576f)
        else -> "%.2f GB".format(bytes / 1073741824f)
    }

    fun formatEta(seconds: Long): String = when {
        seconds < 0 -> "—"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${(seconds % 60)}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    fun centered(context: Context, text: String, sizeSp: Float, color: Int): TextView =
        label(context, text, sizeSp, color).apply { gravity = Gravity.CENTER }
}
