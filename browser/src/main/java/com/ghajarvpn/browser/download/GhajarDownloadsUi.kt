package com.ghajarvpn.browser.download

import com.ghajarvpn.browser.BrowserUi
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared view builders for the downloads surface. */
object DownloadUi {

    fun formatTotal(item: DownloadItem): String =
        if (item.totalBytes > 0) BrowserUi.formatBytes(item.totalBytes) else "نامشخص"

    fun stateLabel(state: DownloadState): String = when (state) {
        DownloadState.QUEUED -> "در صف"
        DownloadState.RUNNING -> "در حال دانلود"
        DownloadState.PAUSED -> "متوقف"
        DownloadState.WAITING_WIFI -> "در انتظار Wi-Fi"
        DownloadState.COMPLETED -> "کامل شد"
        DownloadState.FAILED -> "ناموفق"
        DownloadState.CANCELLED -> "لغو شد"
    }

    fun stateColor(state: DownloadState): Int = when (state) {
        DownloadState.COMPLETED -> BrowserUi.GREEN
        DownloadState.FAILED, DownloadState.CANCELLED -> BrowserUi.RED
        DownloadState.RUNNING, DownloadState.QUEUED -> BrowserUi.GOLD
        else -> BrowserUi.MUTED_ON_LIGHT
    }

    fun timestamp(at: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(at))

    fun row(context: android.app.Activity, item: DownloadItem, actions: (DownloadItem, String) -> Unit): LinearLayout {
        val pad = BrowserUi.dp(context, 14)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = BrowserUi.pill(context, android.graphics.Color.WHITE, 16)
            setPadding(pad, pad, pad, pad)
        }

        val title = BrowserUi.label(context, item.fileName, 14f, BrowserUi.INK, bold = true).apply { maxLines = 1 }
        val host = BrowserUi.label(
            context,
            runCatching { java.net.URI(item.url).host.orEmpty() }.getOrDefault(""),
            11f, BrowserUi.MUTED_ON_LIGHT
        ).apply { maxLines = 1 }
        val status = BrowserUi.label(context, stateLabel(item.state), 12f, stateColor(item.state), bold = true)

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(title)
        headerRow.addView(host)

        val titleRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(headerRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(status)
        }
        card.addView(titleRow)

        if (item.state in setOf(DownloadState.RUNNING, DownloadState.PAUSED, DownloadState.QUEUED, DownloadState.WAITING_WIFI) && item.totalBytes > 0) {
            card.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = DownloadPlanner.progressPercent(item)
                progressTintList = android.content.res.ColorStateList.valueOf(BrowserUi.GOLD)
                layoutParams = LinearLayout.LayoutParams(-1, BrowserUi.dp(context, 4)).apply { topMargin = BrowserUi.dp(context, 8) }
            })
        }

        val detailText = when (item.state) {
            DownloadState.COMPLETED -> "${BrowserUi.formatBytes(item.totalBytes)} · ${timestamp(item.completedAt)}" + if (item.sha256.isNotBlank()) "\nSHA-256: ${item.sha256.take(16)}…" else ""
            DownloadState.FAILED -> item.error.ifBlank { "خطای شبکه" } + " · ${timestamp(item.updatedAt)}"
            else -> "${BrowserUi.formatBytes(item.downloadedBytes)} / ${formatTotal(item)} · ${timestamp(item.updatedAt)}"
        }
        card.addView(BrowserUi.label(context, detailText, 11f, BrowserUi.MUTED_ON_LIGHT).apply {
            setPadding(0, BrowserUi.dp(context, 6), 0, 0)
        })

        val actionRow = LinearLayout(context).apply { setPadding(0, BrowserUi.dp(context, 6), 0, 0) }
        fun chip(label: String, action: String, color: Int) = BrowserUi.label(context, label, 12f, color).apply {
            background = BrowserUi.pill(context, BrowserUi.IVORY_DIM, 10)
            setPadding(BrowserUi.dp(context, 12), BrowserUi.dp(context, 7), BrowserUi.dp(context, 12), BrowserUi.dp(context, 7))
            setOnClickListener { actions(item, action) }
            gravity = Gravity.CENTER
        }
        when (item.state) {
            DownloadState.RUNNING -> { actionRow.addView(chip("توقف", "pause", BrowserUi.INK)); actionRow.addView(chip("لغو", "cancel", BrowserUi.RED)) }
            DownloadState.PAUSED, DownloadState.WAITING_WIFI -> { actionRow.addView(chip("ادامه", "resume", BrowserUi.GREEN)); actionRow.addView(chip("لغو", "cancel", BrowserUi.RED)) }
            DownloadState.FAILED -> { actionRow.addView(chip("تلاش دوباره", "retry", BrowserUi.GOLD)); actionRow.addView(chip("حذف", "delete", BrowserUi.RED)) }
            DownloadState.CANCELLED -> { actionRow.addView(chip("تلاش دوباره", "retry", BrowserUi.GOLD)); actionRow.addView(chip("حذف", "delete", BrowserUi.RED)) }
            DownloadState.COMPLETED -> { actionRow.addView(chip("اشتراک", "share", BrowserUi.INK)); actionRow.addView(chip("حذف", "delete", BrowserUi.RED)) }
            DownloadState.QUEUED -> { actionRow.addView(chip("لغو", "cancel", BrowserUi.RED)) }
        }
        card.addView(actionRow, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    fun applyInsets(root: View, topBar: View) {
        WindowCompat.setDecorFitsSystemWindows((root.context as android.app.Activity).window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(
                top = maxOf(bars.top, cutout.top),
                left = maxOf(bars.left, cutout.left),
                right = maxOf(bars.right, cutout.right)
            )
            root.updatePadding(bottom = maxOf(bars.bottom, cutout.bottom))
            insets
        }
        root.requestApplyInsets()
    }

    fun headerTitle(context: android.content.Context, text: String): TextView =
        BrowserUi.label(context, text, 18f, BrowserUi.IVORY).apply {
            setTypeface(typeface, Typeface.BOLD)
        }
}
