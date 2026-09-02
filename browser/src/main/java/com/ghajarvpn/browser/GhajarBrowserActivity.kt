package com.ghajarvpn.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.ghajarvpn.browser.media.BrowserMediaBridge
import com.ghajarvpn.browser.media.GhajarPlayerActivity
import com.ghajarvpn.browser.media.MediaCandidate
import com.ghajarvpn.browser.media.MediaPlaybackRequest
import com.ghajarvpn.browser.media.MediaSessionVault
import com.ghajarvpn.browser.media.MediaSourceResolver
import java.io.ByteArrayInputStream

/**
 * Independent browser surface. Private tabs are never written to disk and each
 * tab owns its WebView/back-forward list. No browsing data is sent to analytics.
 */
class GhajarBrowserActivity : Activity() {
    private val repository by lazy { BrowserRepository(this) }
    private var settings = BrowserSettings()
    private val tabs = mutableListOf<BrowserTab>()
    private val webViews = linkedMapOf<String, WebView>()
    private lateinit var webContainer: FrameLayout
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tabCounter: TextView
    private lateinit var videoButton: TextView
    private val mediaCandidates = linkedMapOf<String, MutableList<MediaCandidate>>()
    private var activeId = ""
    private var fullScreenView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 27, 46)
        window.navigationBarColor = Color.rgb(7, 27, 46)
        settings = repository.settings()
        tabs += repository.restoreTabs()
        setContentView(buildUi())
        val requested = intent.getStringExtra(BrowserContract.EXTRA_URL)
        val private = intent.getBooleanExtra(BrowserContract.EXTRA_PRIVATE, false)
        if (private || !requested.isNullOrBlank()) {
            newTab(requested ?: BrowserTab.HOME_URL, private)
        } else {
            showTab(tabs.maxByOrNull { it.lastUsed } ?: tabs.first())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.getStringExtra(BrowserContract.EXTRA_URL) ?: return
        newTab(url, intent.getBooleanExtra(BrowserContract.EXTRA_PRIVATE, false))
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 239, 226))
        }
        val titleBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setBackgroundColor(Color.rgb(7, 27, 46))
        }
        titleBar.addView(iconButton(android.R.drawable.ic_media_previous, "بازگشت") {
            val web = currentWebView()
            if (web?.canGoBack() == true) web.goBack() else finish()
        })
        titleBar.addView(iconButton(android.R.drawable.ic_popup_sync, "بارگذاری دوباره") {
            currentWebView()?.reload()
        })
        address = EditText(this).apply {
            setSingleLine(true)
            hint = "جست‌وجو یا نشانی وب"
            textSize = 15f
            setTextColor(Color.rgb(18, 30, 39))
            setHintTextColor(Color.rgb(100, 104, 108))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                    load(BrowserRequestPolicy.normalizeInput(text.toString(), settings)); clearFocus(); true
                } else false
            }
            setOnFocusChangeListener { _, focused -> if (focused) selectAll() }
        }
        titleBar.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(5); marginEnd = dp(5)
        })
        tabCounter = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setOnClickListener { showTabsDialog() }
            contentDescription = "مدیریت تب‌ها"
        }
        titleBar.addView(tabCounter, LinearLayout.LayoutParams(dp(42), dp(44)))
        videoButton = TextView(this).apply {
            text = "🎬"
            textSize = 20f
            gravity = Gravity.CENTER
            visibility = View.GONE
            contentDescription = "پخش در پلیر قاجار"
            setOnClickListener { chooseMedia() }
        }
        titleBar.addView(videoButton, LinearLayout.LayoutParams(dp(42), dp(44)))
        titleBar.addView(iconButton(android.R.drawable.ic_menu_more, "منوی مرورگر") { showMenu(it) })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }
        webContainer = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        root.addView(titleBar, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        root.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun iconButton(icon: Int, description: String, action: (View) -> Unit) = ImageButton(this).apply {
        setImageResource(icon); setColorFilter(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description; setOnClickListener(action)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    private fun newTab(raw: String = BrowserTab.HOME_URL, private: Boolean = false) {
        if (tabs.size >= BrowserRepository.MAX_TABS) {
            tabs.firstOrNull { !it.private && it.id != activeId }?.let(::closeTab)
        }
        val tab = BrowserTab(url = raw, private = private, title = if (private) "تب خصوصی" else "قاجار")
        tabs += tab
        showTab(tab)
    }

    private fun showTab(tab: BrowserTab) {
        activeId = tab.id
        tab.lastUsed = System.currentTimeMillis()
        val web = webViews.getOrPut(tab.id) { buildWebView(tab) }
        webContainer.removeAllViews()
        (web.parent as? ViewGroup)?.removeView(web)
        webContainer.addView(web, FrameLayout.LayoutParams(-1, -1))
        if (web.url == null) loadInto(web, tab.url)
        updateAddress(tab.url)
        tabCounter.text = if (tab.private) "◉ ${tabs.size}" else tabs.size.toString()
        videoButton.visibility = if (mediaCandidates[tab.id].isNullOrEmpty()) View.GONE else View.VISIBLE
        repository.saveTabs(tabs)
    }

    private fun closeTab(tab: BrowserTab) {
        val wasActive = tab.id == activeId
        tabs.remove(tab)
        mediaCandidates.remove(tab.id)
        webViews.remove(tab.id)?.let(::destroyWebView)
        if (tabs.isEmpty()) tabs += BrowserTab()
        if (wasActive) showTab(tabs.last()) else repository.saveTabs(tabs)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tab: BrowserTab): WebView = WebView(this).apply {
        setBackgroundColor(Color.WHITE)
        configureSettings(this, tab.private)
        webViewClient = BrowserClient(tab)
        webChromeClient = BrowserChrome(tab)
        setDownloadListener(downloadListener(tab))
        addJavascriptInterface(BrowserMediaBridge { candidate ->
            runOnUiThread { registerMedia(tab, candidate) }
        }, "GhajarMedia")
    }

    private fun registerMedia(tab: BrowserTab, candidate: MediaCandidate) {
        val values = mediaCandidates.getOrPut(tab.id) { mutableListOf() }
        if (values.none { it.url == candidate.url }) values += candidate
        if (tab.id == activeId && values.isNotEmpty()) videoButton.visibility = View.VISIBLE
    }

    private fun chooseMedia() {
        val tab = currentTab() ?: return
        val values = mediaCandidates[tab.id].orEmpty()
        if (values.isEmpty()) { toast("ویدیوی استاندارد قابل دسترسی پیدا نشد"); return }
        if (values.size == 1) openMedia(tab, values.first()) else {
            val labels = values.mapIndexed { index, item ->
                item.title.ifBlank { Uri.parse(item.url).lastPathSegment ?: "ویدیو ${index + 1}" }.take(80) + " · " + item.kind.name
            }.toTypedArray()
            AlertDialog.Builder(this).setTitle("انتخاب ویدیو برای Ghajar Player")
                .setItems(labels) { _, index -> openMedia(tab, values[index]) }.setNegativeButton("لغو", null).show()
        }
    }

    private fun openMedia(tab: BrowserTab, candidate: MediaCandidate) {
        val web = currentWebView() ?: return
        val headers = linkedMapOf(
            "User-Agent" to web.settings.userAgentString,
            "Referer" to tab.url
        )
        CookieManager.getInstance().getCookie(candidate.url)?.takeIf(String::isNotBlank)?.let { headers["Cookie"] = it }
        val token = MediaSessionVault.put(MediaPlaybackRequest(candidate, headers, tab.private))
        // Prevent duplicate audio; returning to the site keeps the page and tab intact.
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v=>v.pause())", null)
        startActivity(Intent(this, GhajarPlayerActivity::class.java).putExtra(GhajarPlayerActivity.EXTRA_SESSION, token))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings(web: WebView, private: Boolean) = web.settings.apply {
        javaScriptEnabled = settings.javaScript
        domStorageEnabled = !private
        databaseEnabled = !private
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = if (private) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        userAgentString = if (settings.desktopMode) DESKTOP_UA else WebSettings.getDefaultUserAgent(this@GhajarBrowserActivity)
        if (Build.VERSION.SDK_INT >= 33) isAlgorithmicDarkeningAllowed = settings.darkPages
        else if (Build.VERSION.SDK_INT >= 29) forceDark = if (settings.darkPages) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        CookieManager.getInstance().setAcceptCookie(settings.cookies && !private)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
    }

    private fun load(raw: String) {
        val tab = currentTab() ?: return
        tab.url = raw
        loadInto(currentWebView() ?: return, raw)
    }

    private fun loadInto(web: WebView, raw: String) {
        if (raw == BrowserTab.HOME_URL) showHome(web) else web.loadUrl(BrowserRequestPolicy.normalizeInput(raw, settings))
    }

    private fun showHome(web: WebView) {
        val private = currentTab()?.private == true
        val heading = if (private) "جاسوس قاجار · گشت خصوصی" else "شاه قاجار · دروازهٔ اینترنت"
        val note = if (private) "این تب در تاریخچه و بازیابی نشست ذخیره نمی‌شود." else "جست‌وجوی سریع، سبک و محافظت‌شده"
        val html = """
            <!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
            <style>body{background:#071b2e;color:#f8edd2;font-family:sans-serif;text-align:center;padding:12vh 24px}h1{font-size:25px}.c{font-size:72px}.n{opacity:.8;line-height:1.8}.b{margin-top:36px;padding:15px;border:1px solid #b48b32;border-radius:18px}</style>
            <div class=c>${if (private) "🕵🏻" else "👑"}</div><h1>$heading</h1><div class=n>$note</div><div class=b>نشانی یا عبارت جست‌وجو را در نوار بالا وارد کنید</div>
        """.trimIndent()
        web.loadDataWithBaseURL(BrowserTab.HOME_URL, html, "text/html", "UTF-8", null)
    }

    private fun updateAddress(url: String?) {
        if (!::address.isInitialized || address.hasFocus()) return
        address.setText(if (url == BrowserTab.HOME_URL || url == "about:blank") "" else url.orEmpty())
    }

    private fun showMenu(anchor: View) {
        val web = currentWebView() ?: return
        PopupMenu(this, anchor).apply {
            menu.add("تب جدید")
            menu.add("تب خصوصی جدید")
            menu.add("افزودن/حذف نشانک")
            menu.add("یافتن در صفحه")
            menu.add("حالت مطالعه")
            menu.add(if (settings.desktopMode) "نسخهٔ موبایل" else "نسخهٔ دسکتاپ")
            menu.add("ذخیره به PDF")
            menu.add("اشتراک‌گذاری")
            menu.add("باز کردن در مرورگر دیگر")
            menu.add("تاریخچه و نشانک‌ها")
            menu.add("تنظیمات حریم خصوصی")
            menu.add("پاک‌سازی داده‌های مرور")
            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "تب جدید" -> newTab()
                    "تب خصوصی جدید" -> newTab(private = true)
                    "افزودن/حذف نشانک" -> toggleBookmark(web.url)
                    "یافتن در صفحه" -> findInPage(web)
                    "حالت مطالعه" -> readerMode(web)
                    "نسخهٔ موبایل", "نسخهٔ دسکتاپ" -> toggleDesktop(web)
                    "ذخیره به PDF" -> savePdf(web)
                    "اشتراک‌گذاری" -> share(web.url)
                    "باز کردن در مرورگر دیگر" -> openExternal(web.url)
                    "تاریخچه و نشانک‌ها" -> showLibrary()
                    "تنظیمات حریم خصوصی" -> showSettings()
                    "پاک‌سازی داده‌های مرور" -> confirmClear()
                }; true
            }
            show()
        }
    }

    private fun showTabsDialog() {
        val labels = tabs.map { (if (it.private) "🕵  " else "") + it.title.take(48) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("تب‌ها (${tabs.size})")
            .setItems(labels) { _, index -> showTab(tabs[index]) }
            .setPositiveButton("تب جدید") { _, _ -> newTab() }
            .setNeutralButton("خصوصی") { _, _ -> newTab(private = true) }
            .setNegativeButton("بستن تب فعلی") { _, _ -> currentTab()?.let(::closeTab) }
            .show()
    }

    private fun toggleBookmark(url: String?) {
        if (!BrowserRequestPolicy.safeExternal(url.orEmpty()) || currentTab()?.private == true) {
            toast("این صفحه قابل ذخیره نیست"); return
        }
        toast(if (repository.toggleBookmark(url!!)) "نشانک ذخیره شد" else "نشانک حذف شد")
    }

    private fun findInPage(web: WebView) {
        val input = EditText(this).apply { hint = "عبارت داخل صفحه"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("یافتن در صفحه").setView(input)
            .setPositiveButton("یافتن") { _, _ -> web.findAllAsync(input.text.toString()) }
            .setNegativeButton("بستن") { _, _ -> web.clearMatches() }.show()
    }

    private fun readerMode(web: WebView) {
        if (!settings.javaScript) { toast("برای حالت مطالعه، JavaScript باید فعال باشد"); return }
        web.evaluateJavascript(READER_SCRIPT, null)
    }

    private fun toggleDesktop(web: WebView) {
        settings = settings.copy(desktopMode = !settings.desktopMode)
        repository.saveSettings(settings)
        web.settings.userAgentString = if (settings.desktopMode) DESKTOP_UA else WebSettings.getDefaultUserAgent(this)
        web.reload()
    }

    private fun savePdf(web: WebView) {
        if (web.url == BrowserTab.HOME_URL || currentTab()?.private == true) { toast("این صفحه به PDF ذخیره نمی‌شود"); return }
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Ghajar-${System.currentTimeMillis()}", web.createPrintDocumentAdapter("Ghajar page"), PrintAttributes.Builder().build())
    }

    private fun share(url: String?) {
        if (!BrowserRequestPolicy.safeExternal(url.orEmpty())) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
        }, "اشتراک‌گذاری صفحه"))
    }

    private fun openExternal(url: String?) {
        if (!BrowserRequestPolicy.safeExternal(url.orEmpty())) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
            .onFailure { toast("مرورگر دیگری پیدا نشد") }
    }

    private fun showLibrary() {
        val bookmarks = repository.bookmarks().map { "★  $it" }
        val history = repository.history().take(100).map { "${it.title}\n${it.url}" }
        val values = (bookmarks + history).ifEmpty { listOf("هنوز موردی وجود ندارد") }
        AlertDialog.Builder(this).setTitle("نشانک‌ها و تاریخچه")
            .setItems(values.toTypedArray()) { _, index ->
                val line = values[index]
                val url = if (line.startsWith("★  ")) line.removePrefix("★  ") else line.substringAfterLast('\n')
                if (BrowserRequestPolicy.safeExternal(url)) load(url)
            }.setNegativeButton("بستن", null).show()
    }

    private fun showSettings() {
        val labels = arrayOf("JavaScript", "Cookieهای اصلی", "مسدودسازی ردیاب", "مسدودسازی تبلیغ", "ترجیح HTTPS", "تیره‌کردن صفحات")
        val values = booleanArrayOf(settings.javaScript, settings.cookies, settings.trackerBlocking, settings.adBlocking, settings.httpsPreference, settings.darkPages)
        AlertDialog.Builder(this).setTitle("حریم خصوصی و سایت‌ها")
            .setMultiChoiceItems(labels, values) { _, which, checked -> values[which] = checked }
            .setPositiveButton("اعمال") { _, _ ->
                settings = settings.copy(javaScript = values[0], cookies = values[1], trackerBlocking = values[2], adBlocking = values[3], httpsPreference = values[4], darkPages = values[5])
                repository.saveSettings(settings)
                webViews.forEach { (id, web) -> configureSettings(web, tabs.firstOrNull { it.id == id }?.private == true) }
                currentWebView()?.reload()
            }.setNegativeButton("لغو", null).show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("جلاد قاجار · پاک‌سازی")
            .setMessage("تاریخچه، نشانک‌ها، Cookieها و Cache مرورگر پاک شوند؟ گذرواژه‌های سیستم پاک نمی‌شوند.")
            .setPositiveButton("پاک شود") { _, _ ->
                repository.clearBrowsingData(); CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush(); WebView(this).clearCache(true)
                toast("داده‌های مرور پاک شد")
            }.setNegativeButton("لغو", null).show()
    }

    private fun downloadListener(tab: BrowserTab) = DownloadListener { url, userAgent, disposition, mime, _ ->
        if (!BrowserRequestPolicy.safeExternal(url) || tab.private) {
            toast(if (tab.private) "دانلود در تب خصوصی غیرفعال است" else "نشانی دانلود امن نیست"); return@DownloadListener
        }
        val handoff = Intent(BrowserContract.ACTION_ENQUEUE_DOWNLOAD).setPackage(packageName).apply {
            putExtra(BrowserContract.EXTRA_URL, url)
            putExtra(BrowserContract.EXTRA_COOKIES, CookieManager.getInstance().getCookie(url).orEmpty())
            putExtra(BrowserContract.EXTRA_REFERER, tab.url)
            putExtra(BrowserContract.EXTRA_USER_AGENT, userAgent.orEmpty())
            putExtra(BrowserContract.EXTRA_CONTENT_DISPOSITION, disposition.orEmpty())
            putExtra(BrowserContract.EXTRA_CONTENT_TYPE, mime.orEmpty())
        }
        if (packageManager.queryBroadcastReceivers(handoff, 0).isNotEmpty()) {
            sendBroadcast(handoff); toast("به مدیر دانلود افزوده شد")
        } else {
            fallbackDownload(url, userAgent, disposition, mime, tab.url)
        }
    }

    private fun fallbackDownload(url: String, userAgent: String?, disposition: String?, mime: String?, referer: String) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime); setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setTitle(android.webkit.URLUtil.guessFileName(url, disposition, mime))
                userAgent?.takeIf(String::isNotBlank)?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { addRequestHeader("Cookie", it) }
                if (BrowserRequestPolicy.safeExternal(referer)) addRequestHeader("Referer", referer)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }.onSuccess { toast("دانلود آغاز شد") }.onFailure { toast("شروع دانلود ممکن نشد") }
    }

    private inner class BrowserClient(private val tab: BrowserTab) : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            mediaCandidates[tab.id]?.clear()
            if (tab.id == activeId) { videoButton.visibility = View.GONE; progress.visibility = View.VISIBLE; progress.progress = 10; updateAddress(url) }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            tab.url = url ?: tab.url; tab.title = view?.title?.take(100).orEmpty().ifBlank { Uri.parse(tab.url).host ?: "قاجار" }
            if (!tab.private) { repository.addHistory(tab.title, tab.url); repository.saveTabs(tabs) }
            view?.evaluateJavascript(BrowserMediaBridge.DISCOVERY_SCRIPT, null)
            if (tab.id == activeId) { progress.visibility = View.GONE; updateAddress(tab.url) }
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            val raw = url ?: return
            if (MediaSourceResolver.looksLikeMedia(raw)) MediaSourceResolver.resolve(raw)?.let { registerMedia(tab, it) }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request?.isForMainFrame != true) return false
            val uri = request.url
            if (uri.scheme?.lowercase() in setOf("http", "https")) return false
            if (uri.scheme?.lowercase() in setOf("file", "content", "data", "javascript")) return true
            return runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); true }.getOrDefault(true)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            return if (BrowserRequestPolicy.blocked(request?.url?.toString().orEmpty(), settings)) {
                WebResourceResponse("text/plain", "UTF-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            } else null
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            handler?.cancel(); if (tab.id == activeId) toast("گواهی TLS این سایت معتبر نیست")
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true && tab.id == activeId) toast("صفحه بارگذاری نشد")
        }

        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
            view ?: return true
            (view.parent as? ViewGroup)?.removeView(view); webViews.remove(tab.id); view.destroy()
            if (tab.id == activeId) showTab(tab)
            toast("تب پس از توقف نمایشگر بازیابی شد"); return true
        }
    }

    private inner class BrowserChrome(private val tab: BrowserTab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (tab.id == activeId) { progress.progress = newProgress; progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            tab.title = title?.take(100).orEmpty().ifBlank { "قاجار" }; repository.saveTabs(tabs)
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            if (!isUserGesture || resultMsg == null) return false
            val next = BrowserTab(private = tab.private, title = if (tab.private) "تب خصوصی" else "تب جدید")
            tabs += next
            val popup = buildWebView(next); webViews[next.id] = popup
            (resultMsg.obj as? WebView.WebViewTransport)?.webView = popup; resultMsg.sendToTarget(); showTab(next)
            return true
        }

        override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
            fileCallback?.onReceiveValue(null); fileCallback = callback
            return try { startActivityForResult(params?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), FILE_REQUEST); true }
            catch (_: ActivityNotFoundException) { fileCallback = null; false }
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request ?: return
            val video = request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }.toTypedArray()
            if (video.isEmpty() || Uri.parse(request.origin.toString()).scheme != "https") { request.deny(); return }
            runOnUiThread {
                AlertDialog.Builder(this@GhajarBrowserActivity).setTitle("اجازهٔ دوربین")
                    .setMessage("این سایت برای دوربین اجازه می‌خواهد:\n${request.origin.host.orEmpty()}")
                    .setPositiveButton("اجازه") { _, _ ->
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(video)
                        else { pendingPermission = request; requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST) }
                    }.setNegativeButton("رد") { _, _ -> request.deny() }.setOnCancelListener { request.deny() }.show()
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null || fullScreenView != null) { callback?.onCustomViewHidden(); return }
            fullScreenView = view; fullScreenCallback = callback
            webContainer.addView(view, FrameLayout.LayoutParams(-1, -1)); window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        override fun onHideCustomView() { hideFullScreen() }
    }

    private fun hideFullScreen() {
        fullScreenView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        fullScreenView = null; fullScreenCallback?.onCustomViewHidden(); fullScreenCallback = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCallback = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            val request = pendingPermission; pendingPermission = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) request?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request?.deny()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (fullScreenView != null) hideFullScreen()
        else currentWebView()?.let { if (it.canGoBack()) it.goBack() else closeTab(currentTab() ?: return) }
        if (tabs.size == 1 && currentTab()?.url == BrowserTab.HOME_URL && !currentWebView()!!.canGoBack()) super.onBackPressed()
    }

    override fun onPause() { repository.saveTabs(tabs); CookieManager.getInstance().flush(); super.onPause() }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null); pendingPermission?.deny()
        webViews.values.toList().forEach(::destroyWebView); webViews.clear(); super.onDestroy()
    }

    private fun destroyWebView(web: WebView) {
        (web.parent as? ViewGroup)?.removeView(web); web.stopLoading(); web.loadUrl("about:blank")
        web.clearHistory(); web.removeAllViews(); web.destroy()
    }

    private fun currentTab() = tabs.firstOrNull { it.id == activeId }
    private fun currentWebView() = webViews[activeId]
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILE_REQUEST = 731
        private const val CAMERA_REQUEST = 732
        private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36"
        private val READER_SCRIPT = """
            (()=>{const a=document.querySelector('article,main,[role=main]')||document.body;document.body.innerHTML='';document.body.appendChild(a);document.body.style='max-width:760px;margin:auto;padding:24px;font:19px/1.8 sans-serif';document.querySelectorAll('script,style,nav,aside,footer,iframe').forEach(x=>x.remove())})()
        """.trimIndent()
    }
}
