package com.ghajarvpn.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Insets adapter reused from the downloads surface. */
private object BrowserLibraryInsets {
    fun apply(root: View, topBar: View) = com.ghajarvpn.browser.download.DownloadUi.applyInsets(root, topBar)
}

/** Native bookmarks + history library with search, open and delete. */
class BrowserLibraryActivity : Activity() {

    private lateinit var repository: BrowserRepository
    private lateinit var list: LinearLayout
    private var query: String = ""
    private var showingBookmarks = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = BrowserRepository(this)
        setContentView(buildUi())
        refresh()
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
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        topBar.addView(BrowserUi.label(this, "کتابخانهٔ مرورگر", 18f, BrowserUi.IVORY).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun tab(label: String, target: Boolean) = BrowserUi.label(this, label, 14f, if (showingBookmarks == target) BrowserUi.GOLD_SOFT else BrowserUi.MUTED_ON_DARK).apply {
            setPadding(0, dp(10), dp(18), dp(6))
            setOnClickListener { showingBookmarks = target; refresh(); topBar.performClick() }
        }
        tabs.addView(tab("نشانک‌ها", true))
        tabs.addView(tab("تاریخچه", false))
        tabs.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        tabs.addView(BrowserUi.label(this, "پاک‌کردن همه", 13f, BrowserUi.RED).apply {
            setOnClickListener { confirmClearAll() }
        })
        topBar.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        BrowserLibraryInsets.apply(root, topBar)
        return root
    }

    private fun refresh() {
        list.removeAllViews()
        if (showingBookmarks) {
            val bookmarks = repository.bookmarks().sorted()
                .filter { it.contains(query, true) }
            if (bookmarks.isEmpty()) list.addView(empty("نشانکی ذخیره نشده است"))
            bookmarks.forEach { url -> list.addView(entry(host(url), url, deletable = true)) }
        } else {
            val history = repository.history()
                .filter { it.title.contains(query, true) || it.url.contains(query, true) }
            if (history.isEmpty()) list.addView(empty("تاریخچه خالی است"))
            history.forEach { item -> list.addView(entry(item.title.ifBlank { host(item.url) }, item.url, deletable = true)) }
        }
    }

    private fun host(url: String) = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault(url)

    private fun empty(text: String) = BrowserUi.centered(this, text, 14f, BrowserUi.MUTED_ON_LIGHT).apply {
        setPadding(0, dp(48), 0, 0)
    }

    private fun entry(title: String, url: String, deletable: Boolean): View {
        val pad = dp(14)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = BrowserUi.pill(this@BrowserLibraryActivity, android.graphics.Color.WHITE, 14)
            setPadding(pad, dp(10), pad, dp(10))
        }
        card.addView(BrowserUi.label(this, title, 14f, BrowserUi.INK, bold = true).apply { maxLines = 1 })
        card.addView(BrowserUi.label(this, url, 11f, BrowserUi.MUTED_ON_LIGHT).apply { maxLines = 1 })
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(BrowserUi.label(this, "باز کردن", 12f, BrowserUi.GOLD).apply {
            setPadding(0, dp(6), dp(14), dp(6))
            setOnClickListener {
                val result = Intent().putExtra(EXTRA_OPEN_URL, url)
                setResult(RESULT_OK, result)
                finish()
            }
        })
        if (deletable) actions.addView(BrowserUi.label(this, "حذف", 12f, BrowserUi.RED).apply {
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { confirmDelete(url) }
        })
        card.addView(actions)
        return card.also { }
    }

    private fun confirmDelete(url: String) {
        AlertDialog.Builder(this).setTitle("حذف")
            .setMessage("این مورد حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                if (showingBookmarks) repository.toggleBookmark(url)
                else repository.removeHistory(url)
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this).setTitle("پاک‌کردن همه")
            .setMessage(if (showingBookmarks) "همهٔ نشانک‌ها حذف شوند؟" else "کل تاریخچه پاک شود؟")
            .setPositiveButton("پاک شود") { _, _ ->
                if (showingBookmarks) repository.bookmarks().forEach { repository.toggleBookmark(it) }
                else repository.clearHistory()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_OPEN_URL = "library_open_url" }
}
