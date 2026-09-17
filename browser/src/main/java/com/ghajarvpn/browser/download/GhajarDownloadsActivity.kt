package com.ghajarvpn.browser.download

import com.ghajarvpn.browser.BrowserContract
import com.ghajarvpn.browser.BrowserUi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File

/** Native Ghajar downloads page: live progress, pause/resume/retry/cancel, share, delete. */
class GhajarDownloadsActivity : Activity(), DownloadEngine.Listener {

    private lateinit var list: LinearLayout
    private lateinit var storage: DownloadStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = DownloadStorage(this)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BrowserUi.IVORY)
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BrowserUi.NAVY)
            setPadding(BrowserUi.dp(this@GhajarDownloadsActivity, 16), 0, BrowserUi.dp(this@GhajarDownloadsActivity, 16), BrowserUi.dp(this@GhajarDownloadsActivity, 10))
        }
        topBar.addView(DownloadUi.headerTitle(this, "دانلودهای قاجار"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = BrowserUi.dp(this@GhajarDownloadsActivity, 8)
        })

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(BrowserUi.dp(this@GhajarDownloadsActivity, 14), BrowserUi.dp(this@GhajarDownloadsActivity, 12), BrowserUi.dp(this@GhajarDownloadsActivity, 14), BrowserUi.dp(this@GhajarDownloadsActivity, 12))
        }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        DownloadUi.applyInsets(root, topBar)
        return root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val items = storage.load().sortedByDescending { it.updatedAt }
        if (items.isEmpty()) {
            list.addView(BrowserUi.centered(this, "هنوز دانلودی وجود ندارد", 14f, BrowserUi.MUTED_ON_LIGHT).apply {
                setPadding(0, BrowserUi.dp(this@GhajarDownloadsActivity, 48), 0, 0)
            })
            return
        }
        items.forEach { item ->
            val row = DownloadUi.row(this, item, ::onAction)
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = BrowserUi.dp(this@GhajarDownloadsActivity, 8) })
        }
    }

    private fun onAction(item: DownloadItem, action: String) {
        when (action) {
            "pause" -> sendService(GhajarDownloadService.ACTION_PAUSE, item)
            "resume" -> sendService(GhajarDownloadService.ACTION_RESUME, item)
            "retry" -> sendService(GhajarDownloadService.ACTION_RETRY, item)
            "cancel" -> sendService(GhajarDownloadService.ACTION_CANCEL, item)
            "share" -> share(item)
            "delete" -> confirmDelete(item)
        }
        list.postDelayed({ refresh() }, 400)
    }

    private fun sendService(action: String, item: DownloadItem) {
        startService(Intent(this, GhajarDownloadService::class.java).setAction(action).putExtra(GhajarDownloadService.EXTRA_ID, item.id))
    }

    private fun share(item: DownloadItem) {
        val file = File(directoryOf(item), item.fileName)
        if (!file.exists()) { return }
        val uri = FileProvider.getUriForFile(this, "${packageName}.ghajar_downloads", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "اشتراک‌گذاری فایل"))
    }

    private fun confirmDelete(item: DownloadItem) {
        AlertDialog.Builder(this).setTitle("حذف دانلود")
            .setMessage("«${item.fileName}» از فهرست و فایل آن حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                val file = File(directoryOf(item), item.fileName)
                file.delete()
                val items = storage.load().filterNot { it.id == item.id }
                storage.save(items)
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    private fun directoryOf(item: DownloadItem): File =
        File(item.directory.ifBlank { getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath })

    override fun onUpdate(item: DownloadItem, speedBps: Long) {
        list.post { refresh() }
    }

    override fun onTerminal(item: DownloadItem) {
        list.post { refresh() }
    }
}
