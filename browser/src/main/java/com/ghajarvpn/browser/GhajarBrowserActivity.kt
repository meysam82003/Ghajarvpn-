package com.ghajarvpn.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ghajarvpn.browser.media.BrowserMediaBridge
import com.ghajarvpn.browser.media.GhajarPlayerActivity
import com.ghajarvpn.browser.media.MediaCandidate
import com.ghajarvpn.browser.media.MediaPlaybackRequest
import com.ghajarvpn.browser.media.MediaSessionVault
import com.ghajarvpn.browser.media.MediaKind
import com.ghajarvpn.browser.media.MediaSourceResolver
import com.ghajarvpn.browser.network.BrowserNetworkMode
import com.ghajarvpn.browser.network.BrowserProxyController
import com.ghajarvpn.browser.network.BrowserRouteManager
import com.ghajarvpn.browser.network.BrowserRouteSnapshot
import com.ghajarvpn.browser.network.BrowserRouteState
import com.ghajarvpn.browser.network.BrowserRouteStorage
import java.io.ByteArrayInputStream

/**
 * Independent browser surface. Private tabs are never written to disk and each
 * tab owns its WebView/back-forward list. No browsing data is sent to analytics.
 */
class GhajarBrowserActivity : Activity() {
    private val repository by lazy { BrowserRepository(this) }
    private val sitePermissions by lazy { SitePermissionsStore(SharedPrefsDocumentStore(this)) }
    private val routeManager by lazy { BrowserRouteManager(BrowserRouteStorage(this)) }
    private var settings = BrowserSettings()
    private val tabs = mutableListOf<BrowserTab>()
    private val webViews = linkedMapOf<String, WebView>()
    private val mediaCandidates = linkedMapOf<String, MutableList<MediaCandidate>>()
    private lateinit var root: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tabCounter: TextView
    private lateinit var videoButton: TextView
    private lateinit var reloadButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var privateIndicator: TextView
    private var addressClearVisible = false
    private var activeId = ""
    private var fullScreenView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var pendingWebResource: String? = null
    private var pendingGeolocation: String? = null
    private var pendingGeolocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        settings = repository.settings()
        tabs += repository.restoreTabs()
        setContentView(buildUi())
        updateRouteBadge(routeManager.refresh())
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

    // ---------------------------------------------------------------- UI shell

    private fun buildUi(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BrowserUi.IVORY)
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BrowserUi.NAVY)
            setPadding(0, 0, 0, dp(4))
        }
        topBar.addView(buildToolbarRow(), LinearLayout.LayoutParams(-1, -2))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(BrowserUi.GOLD)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(BrowserUi.NAVY_RAISED)
        }
        topBar.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))

        webContainer = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }

        bottomBar = buildBottomBar() as LinearLayout

        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))
        root.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))
        BrowserInsets.apply(root, topBar, bottomBar)
        return root
    }

    private fun buildToolbarRow(): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(4))
        }

        backButton = navIcon(R.drawable.ic_back, "بازگشت") {
            val web = currentWebView()
            if (web?.canGoBack() == true) web.goBack() else finish()
        }
        row.addView(backButton)

        forwardButton = navIcon(R.drawable.ic_forward, "جلو") { currentWebView()?.goForward() }
        row.addView(forwardButton)

        address = EditText(this).apply {
            setSingleLine(true)
            hint = "جست‌وجو یا نشانی وب"
            textSize = 15f
            setTextColor(BrowserUi.INK)
            setHintTextColor(BrowserUi.MUTED_ON_LIGHT)
            setBackgroundResource(0)
            background = BrowserUi.pill(this@GhajarBrowserActivity, Color.WHITE, 22)
            setPadding(dp(14), 0, dp(14), 0)
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, action, event ->
                val go = action == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (go) {
                    load(BrowserRequestPolicy.normalizeInput(text.toString(), settings))
                    clearFocus()
                    true
                } else false
            }
            setOnFocusChangeListener { _, focused ->
                if (focused) {
                    setText(currentTab()?.url?.takeIf { it != BrowserTab.HOME_URL }.orEmpty())
                    selectAll()
                    showAddressClear(true)
                    refreshAddressIcon()
                } else {
                    showAddressClear(false)
                    updateAddress(currentTab()?.url)
                }
            }
            setOnTouchListener { view, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val pad = dp(14)
                    val box = address.compoundDrawablesRelative.getOrNull(2)
                    if (box != null && event.rawX >= width - box.intrinsicWidth - pad) {
                        setText(""); showAddressClear(true); requestFocus()
                        return@setOnTouchListener true
                    }
                }
                view.performClick()
            }
        }
        row.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(4); marginEnd = dp(4)
        })

        reloadButton = navIcon(R.drawable.ic_reload, "بارگذاری دوباره") { currentWebView()?.reload() }
        row.addView(reloadButton)

        tabCounter = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = BrowserUi.pill(this@GhajarBrowserActivity, BrowserUi.NAVY_RAISED, 12)
            setOnClickListener { showTabSwitcher() }
            contentDescription = "مدیریت تب‌ها"
        }
        row.addView(tabCounter, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4) })

        videoButton = TextView(this).apply {
            text = "🎬"
            textSize = 18f
            gravity = Gravity.CENTER
            visibility = View.GONE
            contentDescription = "پخش در پلیر قاجار"
            setOnClickListener { chooseMedia() }
        }
        row.addView(videoButton, LinearLayout.LayoutParams(dp(40), dp(40)))

        row.addView(navIcon(R.drawable.ic_menu, "منوی مرورگر") { showMenu(it) })

        privateIndicator = TextView(this).apply {
            text = "گشت خصوصی"
            textSize = 11f
            setTextColor(BrowserUi.GOLD_SOFT)
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = BrowserUi.pill(this@GhajarBrowserActivity, 0x22D9B15C.toInt(), 10)
        }
        return row
    }

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BrowserUi.NAVY)
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val size = dp(if (compact) 40 else 46)
        row.addView(bottomIcon(R.drawable.ic_home, "خانهٔ مرورگر") { goHome() })
        row.addView(bottomIcon(R.drawable.ic_incognito, "تب خصوصی جدید") { newTab(private = true) })
        routeButton = bottomIcon(R.drawable.ic_shield, "مسیر ترافیک مرورگر") { showRouteSelector() }
        row.addView(routeButton)
        row.addView(bottomIcon(R.drawable.ic_download, "دانلودهای قاجار") { openDownloads() })
        row.addView(bottomIcon(R.drawable.ic_tabs, "تب‌ها") { showTabSwitcher() })
        bar.addView(row, LinearLayout.LayoutParams(-1, -2))
        return bar
    }

    private lateinit var routeButton: ImageButton

    private fun bottomIcon(icon: Int, description: String, action: (View) -> Unit): ImageButton {
        val button = ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(BrowserUi.MUTED_ON_DARK)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = description
            setOnClickListener(action)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        button.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        return button
    }

    private fun navIcon(icon: Int, description: String, action: (View) -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description
        setOnClickListener(action)
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(46))
    }

    private fun showAddressClear(visible: Boolean) {
        if (addressClearVisible == visible && ::address.isInitialized) return
        addressClearVisible = visible
        address.setCompoundDrawablesRelativeWithIntrinsicBounds(addressStartDrawable(), 0, if (visible) R.drawable.ic_close else 0, 0)
    }

    private fun refreshAddressIcon() {
        address.setCompoundDrawablesRelativeWithIntrinsicBounds(addressStartDrawable(), 0, if (addressClearVisible) R.drawable.ic_close else 0, 0)
    }

    private fun addressStartDrawable(): Int {
        if (!::address.isInitialized) return 0
        val url = currentTab()?.url.orEmpty()
        if (address.hasFocus() || url == BrowserTab.HOME_URL) return R.drawable.ic_search
        return if (url.startsWith("https://", true)) R.drawable.ic_lock else R.drawable.ic_globe
    }

    // ------------------------------------------------------------- tab engine

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
        refreshChrome(tab)
        repository.saveTabs(tabs.filterNot(BrowserTab::private))
    }

    private fun refreshChrome(tab: BrowserTab) {
        tabCounter.text = tabs.size.toString()
        tabCounter.setTextColor(if (tab.private) BrowserUi.GOLD_SOFT else Color.WHITE)
        tabCounter.background = BrowserUi.pill(this, if (tab.private) 0x33D9B15C.toInt() else BrowserUi.NAVY_RAISED, 12)
        privateIndicator.visibility = if (tab.private) View.VISIBLE else View.GONE
        forwardButton.isEnabled = currentWebView()?.canGoForward() == true
        forwardButton.alpha = if (forwardButton.isEnabled) 1f else .35f
        backButton.alpha = 1f
        videoButton.visibility = if (mediaCandidates[tab.id].isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun closeTab(tab: BrowserTab) {
        val wasActive = tab.id == activeId
        tabs.remove(tab)
        mediaCandidates.remove(tab.id)
        errorTabs.remove(tab.id)
        webViews.remove(tab.id)?.let(::destroyWebView)
        if (tabs.isEmpty()) tabs += BrowserTab()
        if (wasActive) showTab(tabs.last()) else {
            repository.saveTabs(tabs.filterNot(BrowserTab::private))
            refreshChrome(currentTab() ?: return)
        }
        if (tabs.none(BrowserTab::private)) {
            // Last private tab closed: wipe the throwaway session completely.
            PrivateMode.clearSession()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tab: BrowserTab): WebView = WebView(this).apply {
        setBackgroundColor(Color.WHITE)
        configureSettings(this, tab.private)
        if (tab.private) PrivateMode.attach(this)
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
        val values = mediaCandidates[tab.id].orEmpty().filter { it.kind != MediaKind.UNKNOWN }
        if (values.isEmpty()) { toast("ویدیوی استاندارد قابل دسترسی پیدا نشد"); return }
        val options = arrayOf("پخش در پلیر قاجار", "دانلود با قاجار", "اطلاعات رسانه")
        AlertDialog.Builder(this).setTitle("رسانه در این صفحه (${values.size})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (values.size == 1) openMedia(tab, values.first()) else pickMedia(values) { openMedia(tab, it) }
                    1 -> if (values.size == 1) downloadMedia(tab, values.first()) else pickMedia(values) { downloadMedia(tab, it) }
                    else -> showMediaInfo(values.first())
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun pickMedia(values: List<MediaCandidate>, action: (MediaCandidate) -> Unit) {
        val labels = values.mapIndexed { index, item ->
            item.title.ifBlank { Uri.parse(item.url).lastPathSegment ?: "ویدیو ${index + 1}" }.take(64) + "  ·  " + kindLabel(item)
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("انتخاب رسانه")
            .setItems(labels) { _, index -> action(values[index]) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun downloadMedia(tab: BrowserTab, candidate: MediaCandidate) {
        if (candidate.url.contains("drm", true) || candidate.url.contains("widevine", true)) {
            toast("این رسانه با DRM محافظت شده و دانلود آن ممکن نیست"); return
        }
        if (tab.private) { toast("دانلود در تب خصوصی غیرفعال است"); return }
        val handoff = Intent(BrowserContract.ACTION_ENQUEUE_DOWNLOAD).setPackage(packageName).apply {
            putExtra(BrowserContract.EXTRA_URL, candidate.url)
            putExtra(BrowserContract.EXTRA_COOKIES, CookieManager.getInstance().getCookie(candidate.url).orEmpty())
            putExtra(BrowserContract.EXTRA_REFERER, tab.url)
            putExtra(BrowserContract.EXTRA_USER_AGENT, currentWebView()?.settings?.userAgentString.orEmpty())
            putExtra(BrowserContract.EXTRA_CONTENT_DISPOSITION, "")
            putExtra(BrowserContract.EXTRA_CONTENT_TYPE, candidate.mimeType)
        }
        if (packageManager.queryBroadcastReceivers(handoff, 0).isNotEmpty()) {
            sendBroadcast(handoff)
            toast("دانلود با قاجار آغاز شد")
        } else toast("مدیر دانلود در دسترس نیست")
    }

    private fun showMediaInfo(candidate: MediaCandidate) {
        val host = runCatching { Uri.parse(candidate.url).host }.getOrDefault("")
        AlertDialog.Builder(this).setTitle("اطلاعات رسانه")
            .setMessage(
                "عنوان: ${candidate.title.ifBlank { "نامشخص" }}\n" +
                    "نوع: ${kindLabel(candidate)}\n" +
                    "میزبان: $host\n" +
                    "زیرنویس‌ها: ${candidate.subtitles.size}"
            )
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun kindLabel(candidate: MediaCandidate) = when (candidate.kind) {
        MediaKind.HLS -> "HLS"
        MediaKind.DASH -> "DASH"
        MediaKind.PROGRESSIVE -> "MP4/WebM"
        MediaKind.UNKNOWN -> "نامشخص"
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
        CookieManager.getInstance().setAcceptCookie(settings.cookies)
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

    // --------------------------------------------------------------- home page

    private fun showHome(web: WebView) {
        val private = currentTab()?.private == true
        web.loadDataWithBaseURL(BrowserTab.HOME_URL, homeHtml(private), "text/html", "UTF-8", null)
    }

    private fun homeHtml(private: Boolean): String {
        val recent = repository.history().take(6)
            .filter { BrowserRequestPolicy.safeExternal(it.url) }
        val bookmarks = repository.bookmarks().toList().take(6)
            .filter { BrowserRequestPolicy.safeExternal(it) }
        val crown = """<svg viewBox="0 0 24 24" width="64" height="64"><path fill="#D9B15C" d="M2 19h20v2H2zM3 17l2.5-8L10 13l2-8 2 8 4.5-4L21 17z"/></svg>"""
        val engine = BrowserUi.jsString(settings.searchEngine)
        val recentHtml = recent.joinToString("") { item ->
            val host = Uri.parse(item.url).host.orEmpty()
            """<a class="site" href="${BrowserUi.htmlEscape(item.url)}"><span class="host">${BrowserUi.htmlEscape(host)}</span><span class="ttl">${BrowserUi.htmlEscape(item.title.ifBlank { host })}</span></a>"""
        }.ifBlank { """<div class="empty">هنوز سایتی باز نشده است</div>""" }
        val bookmarkHtml = bookmarks.joinToString("") { url ->
            val host = Uri.parse(url).host.orEmpty()
            """<a class="site" href="${BrowserUi.htmlEscape(url)}"><span class="host">★ ${BrowserUi.htmlEscape(host)}</span></a>"""
        }.ifBlank { """<div class="empty">نشانکی ذخیره نشده است</div>""" }
        val badge = if (private) "🕵 گشت خصوصی فعال — بدون تاریخچه" else "مرور امن و سبک"
        return """
            <!doctype html><html dir="rtl" lang="fa"><meta name=viewport content="width=device-width,initial-scale=1">
            <style>
              body{background:linear-gradient(180deg,#071B2E 0%,#0B2440 55%,#0E2C49 100%);color:#F6F1E4;font-family:sans-serif;margin:0;padding:32px 20px 40px;text-align:center}
              h1{font-size:26px;margin:10px 0 4px;letter-spacing:.5px}
              .sub{opacity:.75;font-size:13px;line-height:1.8}
              .badge{display:inline-block;margin-top:12px;padding:7px 14px;border:1px solid #B48B32;border-radius:999px;color:#D9B15C;font-size:12px}
              form{margin:26px auto 0;max-width:560px;display:flex;gap:8px}
              input{flex:1;padding:14px 18px;border-radius:16px;border:1px solid #2A4A6B;background:#0A1F35;color:#F6F1E4;font-size:15px;outline:none}
              input:focus{border-color:#B48B32}
              button{padding:12px 20px;border-radius:16px;border:none;background:linear-gradient(135deg,#B48B32,#D9B15C);color:#071B2E;font-weight:bold;font-size:14px}
              .grid{display:flex;gap:10px;justify-content:center;margin-top:22px;flex-wrap:wrap}
              .act{padding:11px 16px;border-radius:14px;background:#12314F;color:#F6F1E4;font-size:13px;text-decoration:none;border:1px solid #1E3F61}
              h2{font-size:14px;color:#D9B15C;margin:30px 0 10px;text-align:right}
              .list{max-width:560px;margin:0 auto;text-align:right}
              .site{display:block;padding:12px 16px;margin-bottom:8px;background:#0A1F35;border:1px solid #16334F;border-radius:14px;text-decoration:none}
              .host{display:block;color:#D9B15C;font-size:13px;font-weight:bold}
              .ttl{display:block;color:#C9D6E2;font-size:13px;margin-top:2px}
              .empty{color:#5F7customized90}
            </style>
            <div>$crown</div><h1>قاجار</h1><div class="sub">دروازهٔ امن اینترنت ایرانی</div><div class="badge">$badge</div>
            <form onsubmit="try{location.href='$engine'.replace('%s',encodeURIComponent(document.getElementById('q').value))}catch(e){};return false">
              <input id="q" placeholder="جست‌وجو در وب…" autocomplete="off"><button type="submit">جست‌وجو</button>
            </form>
            <div class="grid">
              <a class="act" href="ghajar-home://private">🕵 تب خصوصی</a>
              <a class="act" href="ghajar-home://bookmarks">★ نشانک‌ها</a>
              <a class="act" href="ghajar-home://history">🕘 تاریخچه</a>
            </div>
            <h2>بازدیدهای اخیر</h2><div class="list">$recentHtml</div>
            <h2>نشانک‌ها</h2><div class="list">$bookmarkHtml</div>
        """.trimIndent()
    }

    // -------------------------------------------------------------- error page

    /** Tabs whose next render is the local error page; guards the page lifecycle. */
    private val pendingErrorRender = mutableSetOf<String>()
    /** Tabs currently displaying the local error page. */
    private val errorTabs = mutableSetOf<String>()

    private fun showErrorPage(tab: BrowserTab, description: String) {
        pendingErrorRender += tab.id
        val web = webViews[tab.id] ?: return
        web.loadDataWithBaseURL(BrowserTab.HOME_URL, errorHtml(description, tab.url), "text/html", "UTF-8", null)
    }

    private fun errorHtml(description: String, pageUrl: String): String {
        val host = Uri.parse(pageUrl).host ?: ""
        return """
            <!doctype html><html dir="rtl" lang="fa"><meta name=viewport content="width=device-width,initial-scale=1">
            <style>
              body{background:#F6F1E4;color:#12202B;font-family:sans-serif;text-align:center;padding:48px 24px;margin:0}
              .card{max-width:420px;margin:0 auto;background:#fff;border:1px solid #E3D9BF;border-radius:20px;padding:28px 20px}
              .shield{width:56px;height:56px;margin:0 auto 14px;border-radius:18px;background:#FBEFD9;color:#B48B32;font-size:28px;line-height:56px}
              h1{font-size:19px;margin:0 0 8px}
              p{font-size:13px;color:#5A6068;line-height:2;margin:0 0 6px}
              .host{color:#B48B32;font-weight:bold;direction:ltr}
              .row{display:flex;gap:10px;justify-content:center;margin-top:22px}
              a{padding:11px 18px;border-radius:13px;text-decoration:none;font-size:13px}
              .retry{background:#B48B32;color:#fff}
              .soft{background:#EEE7D4;color:#12202B}
            </style>
            <div class="card">
              <div class="shield">⚠</div>
              <h1>$description</h1>
              <p>اتصال به <span class="host">${BrowserUi.htmlEscape(host)}</span> برقرار نشد.</p>
              <p>اتصال اینترنت یا مسیر ترافیک را بررسی کنید و دوباره تلاش کنید.</p>
              <div class="row">
                <a class="retry" href="ghajar-error://retry">تلاش دوباره</a>
                <a class="soft" href="ghajar-error://back">بازگشت</a>
                <a class="soft" href="ghajar-error://home">خانه</a>
              </div>
            </div>
        """.trimIndent()
    }

    private fun updateAddress(url: String?) {
        if (!::address.isInitialized || address.hasFocus()) return
        val value = url.orEmpty()
        if (value == BrowserTab.HOME_URL || value.isBlank()) {
            address.setText("")
        } else {
            val host = Uri.parse(value).host.orEmpty()
            if (host.isBlank()) address.setText(value)
            else {
                val start = value.indexOf(host)
                if (start >= 0) {
                    val span = SpannableString(value)
                    span.setSpan(StyleSpan(Typeface.BOLD), start, start + host.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                    address.setText(span)
                } else address.setText(value)
            }
        }
        refreshAddressIcon()
    }

    // ----------------------------------------------------------------- actions

    private fun goHome() {
        val tab = currentTab() ?: return
        tab.url = BrowserTab.HOME_URL
        loadInto(currentWebView() ?: return, BrowserTab.HOME_URL)
    }

    // ------------------------------------------------------------ route switch

    private fun openQr(payload: String = "", scan: Boolean = false) {
        startActivityForResult(
            Intent(this, com.ghajarvpn.browser.qr.BrowserQrActivity::class.java)
                .putExtra(com.ghajarvpn.browser.qr.BrowserQrActivity.EXTRA_PAYLOAD, payload)
                .putExtra(com.ghajarvpn.browser.qr.BrowserQrActivity.EXTRA_SCAN, scan),
            QR_REQUEST
        )
    }

    private fun openDownloads() {
        startActivity(Intent(this, com.ghajarvpn.browser.download.GhajarDownloadsActivity::class.java))
    }

    private fun showRouteSelector() {
        dialog?.dismiss()
        val snapshot = routeManager.refresh()
        val pad = dp(18)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad + dp(6), pad, pad)
            setBackgroundColor(BrowserUi.IVORY)
        }
        container.addView(BrowserUi.label(this, "مسیر ترافیک مرورگر", 17f, BrowserUi.NAVY).apply { setTypeface(typeface, Typeface.BOLD) })
        container.addView(BrowserUi.label(this, statusLine(snapshot), 12f, statusColor(snapshot.state)).apply {
            setPadding(0, dp(6), 0, dp(2))
        })
        if (snapshot.detail.isNotBlank()) {
            container.addView(BrowserUi.label(this, snapshot.detail, 11f, BrowserUi.MUTED_ON_LIGHT).apply { setPadding(0, 0, 0, dp(4)) })
        }
        BrowserNetworkMode.entries.forEach { mode ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = BrowserUi.pill(this@GhajarBrowserActivity, if (mode == snapshot.mode) 0x1FB48B32.toInt() else Color.WHITE, 14)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { switchRoute(mode) }
            }
            val selected = mode == snapshot.mode
            row.addView(BrowserUi.label(this, modeTitle(mode) + if (selected) "  ✓" else "", 14f, if (selected) BrowserUi.GOLD else BrowserUi.INK, bold = selected))
            row.addView(BrowserUi.label(this, modeDescription(mode), 11f, BrowserUi.MUTED_ON_LIGHT))
            container.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("بستن", null)
            .create()
        dialog?.show()
    }

    private fun switchRoute(mode: BrowserNetworkMode) {
        dialog?.dismiss()
        val engine = com.ghajarvpn.browser.network.BrowserRouteEngineHost.get()
        val applied = routeManager.select(mode) { selected ->
            when (selected) {
                BrowserNetworkMode.DIRECT -> {
                    runCatching { engine?.stopBrowserOnly() }
                    BrowserProxyController.clear { detail -> toast(detail) }
                    BrowserProxyController.setLegacySystemProxy(null)
                    BrowserRouteSnapshotHolder.connected(selected, "ترافیک مرورگر مستقیم و بدون تونل است")
                }
                BrowserNetworkMode.FULL_DEVICE -> {
                    runCatching { engine?.stopBrowserOnly() }
                    BrowserProxyController.clear { detail -> toast(detail) }
                    BrowserProxyController.setLegacySystemProxy(null)
                    val connected = engine?.isFullDeviceConnected() == true
                    if (connected) BrowserRouteSnapshotHolder.connected(selected, "ترافیک مرورگر از تونل VPN کل دستگاه می‌گذرد")
                    else BrowserRouteSnapshotHolder.unavailable(selected, "VPN کل دستگاه وصل نیست؛ از صفحهٔ اصلی برنامه اتصال را وصل کنید")
                }
                BrowserNetworkMode.BROWSER_ONLY -> {
                    if (engine?.isFullDeviceConnected() == true) {
                        BrowserRouteSnapshotHolder.unavailable(selected, "تونل کل دستگاه فعال است؛ برای مسیر مستقل مرورگر ابتدا VPN را قطع کنید.")
                    } else if (engine == null) {
                        BrowserRouteSnapshotHolder.unavailable(selected, "مسیر مرورگر در این نسخه در دسترس نیست.")
                    } else {
                        routeManager.recordConnecting()
                        val endpoint = runCatching { engine.startBrowserOnly() }
                        endpoint.fold(
                            onSuccess = { value ->
                                BrowserProxyController.apply(value) { detail -> toast(detail) }
                                BrowserProxyController.setLegacySystemProxy(value)
                                routeManager.recordResult(value)
                                reloadWebViewsForProxy()
                            },
                            onFailure = { routeManager.recordResult(null) }
                        )
                        val snapshot = routeManager.snapshot()
                        if (snapshot.state == BrowserRouteState.ERROR) {
                            snapshot.copy(detail = friendlyRouteError(endpoint.exceptionOrNull()))
                        } else snapshot
                    }
                }
            }
        }
        toast(statusLine(applied))
        updateRouteBadge(applied)
    }

    private fun friendlyRouteError(error: Throwable?): String = when {
        error == null -> "شروع مسیر مرورگر ناموفق بود"
        error.message?.startsWith("FULL_DEVICE_ACTIVE") == true -> "VPN کل دستگاه فعال است؛ برای مسیر مستقل، ابتدا آن را قطع کنید"
        error.message?.startsWith("NO_PROFILE") == true -> "هیچ کانکشن فعالی انتخاب نشده؛ از صفحهٔ اصلی برنامه یکی انتخاب کنید"
        else -> "شروع مسیر مرورگر ناموفق بود"
    }

    private fun reloadWebViewsForProxy() {
        // Proxy changes reliably apply to fresh WebView instances; recreate all tabs.
        val currentUrls = tabs.associate { it.id to (webViews[it.id]?.url ?: it.url) }
        webViews.values.toList().forEach(::destroyWebView)
        webViews.clear()
        tabs.forEach { tab ->
            val web = buildWebView(tab)
            webViews[tab.id] = web
            if (tab.id == activeId) {
                webContainer.removeAllViews()
                webContainer.addView(web, FrameLayout.LayoutParams(-1, -1))
            }
            currentUrls[tab.id]?.let { loadInto(web, it) }
        }
    }

    private fun updateRouteBadge(snapshot: BrowserRouteSnapshot) {
        routeButton.setColorFilter(BrowserUi.colorForState(snapshot.state))
        routeButton.contentDescription = "مسیر ترافیک مرورگر: ${modeTitle(snapshot.mode)} · ${stateTitle(snapshot.state)}"
    }

    private fun statusLine(snapshot: BrowserRouteSnapshot) =
        "${modeTitle(snapshot.mode)} · ${stateTitle(snapshot.state)}"

    private fun statusColor(state: BrowserRouteState) = BrowserUi.colorForState(state)

    private fun modeTitle(mode: BrowserNetworkMode) = when (mode) {
        BrowserNetworkMode.DIRECT -> "مستقیم"
        BrowserNetworkMode.BROWSER_ONLY -> "تونل مرورگر قاجار"
        BrowserNetworkMode.FULL_DEVICE -> "VPN کل دستگاه"
    }

    private fun stateTitle(state: BrowserRouteState) = when (state) {
        BrowserRouteState.CONNECTED -> "وصل"
        BrowserRouteState.CONNECTING -> "در حال اتصال"
        BrowserRouteState.UNAVAILABLE -> "در دسترس نیست"
        BrowserRouteState.ERROR -> "خطا"
        BrowserRouteState.IDLE -> "آماده"
    }

    private fun modeDescription(mode: BrowserNetworkMode) = when (mode) {
        BrowserNetworkMode.DIRECT -> "بدون تونل؛ مناسب شبکه‌های آزاد"
        BrowserNetworkMode.BROWSER_ONLY -> "فقط ترافیک مرورگر از تونل اختصاصی می‌گذرد"
        BrowserNetworkMode.FULL_DEVICE -> "ترافیک کل دستگاه (همان اتصال اصلی برنامه)"
    }

    /** Tiny helper so switchRoute can hand complete snapshots back to the manager. */
    private object BrowserRouteSnapshotHolder {
        fun connected(mode: BrowserNetworkMode, detail: String) = BrowserRouteSnapshot(
            mode = mode, state = BrowserRouteState.CONNECTED, detail = detail, updatedAt = System.currentTimeMillis()
        )

        fun unavailable(mode: BrowserNetworkMode, detail: String) = BrowserRouteSnapshot(
            mode = mode, state = BrowserRouteState.UNAVAILABLE, detail = detail, updatedAt = System.currentTimeMillis()
        )
    }

    private fun showMenu(anchor: View) {
        val web = currentWebView() ?: return
        PopupMenu(this, anchor).apply {
            menu.add("تب جدید")
            menu.add("تب خصوصی جدید")
            val bookmarked = web.url != null && repository.bookmarks().contains(web.url)
            menu.add(if (bookmarked) "حذف نشانک" else "افزودن به نشانک‌ها")
            menu.add("یافتن در صفحه")
            menu.add("حالت مطالعه")
            menu.add(if (settings.desktopMode) "نسخهٔ موبایل" else "نسخهٔ دسکتاپ")
            menu.add("ذخیره به PDF")
            menu.add("اشتراک‌گذاری")
            menu.add("QR همین صفحه")
            menu.add("اسکن QR از گالری")
            menu.add("باز کردن در مرورگر دیگر")
            menu.add("نشانک‌ها و تاریخچه")
            menu.add("اجازه‌های سایت‌ها")
            menu.add("تنظیمات حریم خصوصی")
            menu.add("پاک‌سازی داده‌های مرور")
            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "تب جدید" -> newTab()
                    "تب خصوصی جدید" -> newTab(private = true)
                    "افزودن به نشانک‌ها", "حذف نشانک" -> toggleBookmark(web.url)
                    "یافتن در صفحه" -> findInPage(web)
                    "حالت مطالعه" -> readerMode(web)
                    "نسخهٔ موبایل", "نسخهٔ دسکتاپ" -> toggleDesktop(web)
                    "ذخیره به PDF" -> savePdf(web)
                    "اشتراک‌گذاری" -> share(web.url)
                    "QR همین صفحه" -> openQr(payload = web.url.orEmpty())
                    "اسکن QR از گالری" -> openQr(scan = true)
                    "باز کردن در مرورگر دیگر" -> openExternal(web.url)
                    "نشانک‌ها و تاریخچه" -> showLibrary()
                    "اجازه‌های سایت‌ها" -> showSitePermissions()
                    "تنظیمات حریم خصوصی" -> showSettings()
                    "پاک‌سازی داده‌های مرور" -> confirmClear()
                }; true
            }
            show()
        }
    }

    private fun showTabSwitcher() {
        dialog?.dismiss()
        val pad = dp(16)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); setBackgroundColor(BrowserUi.IVORY) }
        container.addView(BrowserUi.centered(this, "تب‌ها (${tabs.size})", 17f, BrowserUi.NAVY).apply { setTypeface(typeface, Typeface.BOLD) })

        fun actionButton(label: String, tint: Int, action: () -> Unit): TextView =
            BrowserUi.label(this, label, 13f, tint).apply {
                gravity = Gravity.CENTER
                background = BrowserUi.pill(this@GhajarBrowserActivity, if (tint == Color.WHITE) BrowserUi.GOLD else BrowserUi.NAVY_RAISED, 12)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener { action() }
            }

        val headerActions = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(6)) }
        headerActions.addView(actionButton("تب جدید", Color.WHITE) { newTab() })
        headerActions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        headerActions.addView(actionButton("تب خصوصی", Color.WHITE) { newTab(private = true) })
        headerActions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        headerActions.addView(actionButton("بستن همه", BrowserUi.RED) { tabs.toList().forEach(::closeTab); dialog?.dismiss() })
        container.addView(headerActions, LinearLayout.LayoutParams(-1, -2))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabs.forEach { tab ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = BrowserUi.pill(this@GhajarBrowserActivity, if (tab.id == activeId) BrowserUi.IVORY_DIM else Color.WHITE, 14)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textColumn.addView(BrowserUi.label(this, tab.title.ifBlank { "قاجار" }, 14f, BrowserUi.INK, bold = tab.id == activeId))
            textColumn.addView(BrowserUi.label(this,
                (if (tab.private) "🔒 خصوصی · " else "") + Uri.parse(tab.url).host.orEmpty().ifBlank { "صفحهٔ خانه" },
                11f, BrowserUi.MUTED_ON_LIGHT))
            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(BrowserUi.label(this, "بستن", 12f, BrowserUi.RED).apply {
                setPadding(dp(12), dp(8), dp(4), dp(8))
                setOnClickListener { closeTab(tab); showTabSwitcher() }
            })
            row.setOnClickListener { showTab(tab); dialog?.dismiss() }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        val scroll = android.widget.ScrollView(this).apply { addView(list) }
        container.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("بستن فهرست", null)
            .create()
        dialog?.show()
        dialog?.window?.setLayout(-1, (resources.displayMetrics.heightPixels * .72f).toInt())
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

    private fun showSitePermissions() {
        dialog?.dismiss()
        val decisions = sitePermissions.decisions()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(14))
            setBackgroundColor(BrowserUi.IVORY)
        }
        container.addView(BrowserUi.label(this, "اجازه‌های سایت‌ها", 17f, BrowserUi.NAVY).apply { setTypeface(typeface, Typeface.BOLD) })
        if (decisions.isEmpty()) {
            container.addView(BrowserUi.label(this, "هیچ تصمیم ذخیره‌شده‌ای وجود ندارد", 13f, BrowserUi.MUTED_ON_LIGHT).apply {
                setPadding(0, dp(10), 0, dp(4))
            })
        } else {
            decisions.forEach { (origin, entries) ->
                entries.forEach { (resource, state) ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
                    row.addView(BrowserUi.label(this, "$origin · ${sitePermissionTitle(resource)}", 13f, BrowserUi.INK, bold = true).apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    })
                    row.addView(BrowserUi.label(this, if (state == SitePermissionState.ALLOW) "اجازه" else "رد", 12f,
                        if (state == SitePermissionState.ALLOW) BrowserUi.GREEN else BrowserUi.RED).apply {
                        setOnClickListener { sitePermissions.forget(origin); showSitePermissions() }
                        setPadding(dp(10), dp(6), 0, dp(6))
                    })
                    container.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
            }
            container.addView(BrowserUi.label(this, "برای بازنشانی یک تصمیم، روی وضعیت آن بزنید", 11f, BrowserUi.MUTED_ON_LIGHT).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("بستن", null)
            .create()
        dialog?.show()
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
            sendBroadcast(handoff); toast("به مدیر دانلود قاجار افزوده شد")
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

    // ------------------------------------------------------------- web clients

    private inner class BrowserClient(private val tab: BrowserTab) : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (pendingErrorRender.contains(tab.id)) return
            errorTabs.remove(tab.id)
            mediaCandidates[tab.id]?.clear()
            if (tab.id == activeId) {
                videoButton.visibility = View.GONE
                progress.visibility = View.VISIBLE
                progress.progress = 10
                updateAddress(url)
                setReloading(true)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (pendingErrorRender.remove(tab.id)) {
                errorTabs += tab.id
                if (tab.id == activeId) { progress.visibility = View.GONE; setReloading(false) }
                return
            }
            tab.url = url ?: tab.url
            tab.title = view?.title?.take(100).orEmpty().ifBlank { Uri.parse(tab.url).host ?: "قاجار" }
            if (!tab.private) { repository.addHistory(tab.title, tab.url); repository.saveTabs(tabs.filterNot(BrowserTab::private)) }
            view?.evaluateJavascript(BrowserMediaBridge.DISCOVERY_SCRIPT, null)
            if (tab.id == activeId) { progress.visibility = View.GONE; updateAddress(tab.url); refreshChrome(tab) }
            setReloading(false)
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            val raw = url ?: return
            if (MediaSourceResolver.looksLikeMedia(raw)) MediaSourceResolver.resolve(raw)?.let { registerMedia(tab, it) }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request?.isForMainFrame != true) return false
            val uri = request.url
            when (uri.scheme?.lowercase()) {
                "http", "https" -> return false
                "file", "content", "data", "javascript" -> return true
                "ghajar-home" -> { handleHomeAction(uri); return true }
                "ghajar-error" -> { handleErrorAction(uri); return true }
            }
            return runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); true }.getOrDefault(true)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            return if (BrowserRequestPolicy.blocked(request?.url?.toString().orEmpty(), settings)) {
                WebResourceResponse("text/plain", "UTF-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            } else null
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            handler?.cancel()
            if (tab.id == activeId) showErrorPage(tab, "گواهی امنیتی این سایت معتبر نیست")
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame != true) return
            val description = when (error?.errorCode) {
                android.webkit.WebViewClient.ERROR_HOST_LOOKUP -> "سایت پیدا نشد"
                android.webkit.WebViewClient.ERROR_TIMEOUT -> "پاسخی دریافت نشد (مهلت تمام شد)"
                android.webkit.WebViewClient.ERROR_CONNECT, android.webkit.WebViewClient.ERROR_UNKNOWN -> "اتصال شبکه برقرار نشد"
                android.webkit.WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "این نشانی با مرورگر باز نمی‌شود"
                else -> "بارگذاری صفحه ممکن نشد"
            }
            if (tab.id == activeId) showErrorPage(tab, description) else errorTabs += tab.id
        }

        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
            view ?: return true
            (view.parent as? ViewGroup)?.removeView(view); webViews.remove(tab.id); view.destroy()
            if (tab.id == activeId) showTab(tab)
            toast("تب پس از توقف نمایشگر بازیابی شد"); return true
        }
    }

    private fun handleHomeAction(uri: Uri) = when (uri.host) {
        "private" -> newTab(private = true)
        "bookmarks", "history" -> showLibrary()
        else -> Unit
    }

    private fun handleErrorAction(uri: Uri) {
        val web = currentWebView() ?: return
        when (uri.host) {
            "retry" -> web.reload()
            "back" -> if (web.canGoBack()) web.goBack() else goHome()
            "home" -> goHome()
        }
    }

    private fun setReloading(loading: Boolean) {
        reloadButton.setImageResource(if (loading) R.drawable.ic_stop else R.drawable.ic_reload)
        reloadButton.contentDescription = if (loading) "توقف بارگذاری" else "بارگذاری دوباره"
        reloadButton.setOnClickListener { if (loading) currentWebView()?.stopLoading() else currentWebView()?.reload() }
    }

    private inner class BrowserChrome(private val tab: BrowserTab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (tab.id == activeId) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            tab.title = title?.take(100).orEmpty().ifBlank { "قاجار" }; repository.saveTabs(tabs.filterNot(BrowserTab::private))
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            if (!isUserGesture || resultMsg == null) return false
            val next = BrowserTab(private = tab.private, title = if (tab.private) "تب خصوصی" else "تب جدید")
            tabs += next
            val popup = buildWebView(next); webViews[next.id] = popup
            (resultMsg.obj as? WebView.WebViewTransport)?.webView = popup; resultMsg.sendToTarget(); showTab(next)
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            closeTab(tab)
        }

        override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
            fileCallback?.onReceiveValue(null); fileCallback = callback
            return try { startActivityForResult(params?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), FILE_REQUEST); true }
            catch (_: ActivityNotFoundException) { fileCallback = null; false }
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request ?: return
            val webResource = request.resources.firstOrNull { SITE_RESOURCES.containsKey(it) }
            val origin = sitePermissions.originOf(request.origin.toString())
            if (webResource == null || origin.isBlank()) { request.deny(); return }
            val resource = SITE_RESOURCES.getValue(webResource)

            // Stored Block decisions are honoured silently; private tabs always ask.
            val tab = currentTab()
            if (tab?.private != true) {
                when (sitePermissions.state(origin, resource)) {
                    SitePermissionState.BLOCK -> { request.deny(); return }
                    SitePermissionState.ALLOW -> { grantAfterOsPermission(request, webResource, resource, origin, prompt = false); return }
                    SitePermissionState.ASK -> Unit
                }
            }
            runOnUiThread {
                AlertDialog.Builder(this@GhajarBrowserActivity)
                    .setTitle(sitePermissionTitle(resource))
                    .setMessage("این سایت برای «${sitePermissionTitle(resource)}» اجازه می‌خواهد:\n$origin")
                    .setPositiveButton("اجازه") { _, _ ->
                        if (tab?.private != true) sitePermissions.remember(origin, resource, SitePermissionState.ALLOW)
                        grantAfterOsPermission(request, webResource, resource, origin, prompt = false)
                    }
                    .setNeutralButton("همیشه رد") { _, _ ->
                        if (tab?.private != true) sitePermissions.remember(origin, resource, SitePermissionState.BLOCK)
                        request.deny()
                    }
                    .setNegativeButton("رد") { _, _ -> request.deny() }
                    .setOnCancelListener { request.deny() }
                    .show()
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
            callback ?: return
            val host = sitePermissions.originOf(origin.orEmpty())
            if (host.isBlank()) { callback.invoke(origin, false, false); return }
            val tab = currentTab()
            if (tab?.private != true) {
                when (sitePermissions.state(host, SitePermission.GEOLOCATION)) {
                    SitePermissionState.BLOCK -> { callback.invoke(origin, false, false); return }
                    SitePermissionState.ALLOW -> { promptOsForGeolocation(origin, callback); return }
                    SitePermissionState.ASK -> Unit
                }
            }
            runOnUiThread {
                AlertDialog.Builder(this@GhajarBrowserActivity)
                    .setTitle("اجازهٔ موقعیت مکانی")
                    .setMessage("این سایت برای موقعیت مکانی اجازه می‌خواهد:\n$host")
                    .setPositiveButton("اجازه") { _, _ ->
                        if (tab?.private != true) sitePermissions.remember(host, SitePermission.GEOLOCATION, SitePermissionState.ALLOW)
                        promptOsForGeolocation(origin, callback)
                    }
                    .setNeutralButton("همیشه رد") { _, _ ->
                        if (tab?.private != true) sitePermissions.remember(host, SitePermission.GEOLOCATION, SitePermissionState.BLOCK)
                        callback.invoke(origin, false, false)
                    }
                    .setNegativeButton("رد") { _, _ -> callback.invoke(origin, false, false) }
                    .show()
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null || fullScreenView != null) { callback?.onCustomViewHidden(); return }
            fullScreenView = view; fullScreenCallback = callback
            webContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() { hideFullScreen() }
    }

    private fun hideFullScreen() {
        fullScreenView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        fullScreenView = null; fullScreenCallback?.onCustomViewHidden(); fullScreenCallback = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCallback = null
        } else if (requestCode == QR_REQUEST) {
            val url = data?.getStringExtra(com.ghajarvpn.browser.qr.BrowserQrActivity.EXTRA_RESULT_URL)
            if (resultCode == RESULT_OK && url != null && BrowserRequestPolicy.safeExternal(url)) load(url)
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    /** Grants the web resource once (optionally after) the OS-level permission exists. */
    private fun grantAfterOsPermission(request: PermissionRequest, webResource: String, resource: SitePermission, origin: String, @Suppress("UNUSED_PARAMETER") prompt: Boolean) {
        val os = SitePermissionsStore.osPermissionFor(resource)
        if (os == null) {
            request.grant(arrayOf(webResource))
            return
        }
        if (checkSelfPermission(os) == PackageManager.PERMISSION_GRANTED) {
            request.grant(arrayOf(webResource))
        } else {
            pendingPermission = request; pendingWebResource = webResource
            requestPermissions(arrayOf(os), CAMERA_REQUEST)
        }
    }

    private fun promptOsForGeolocation(origin: String?, callback: android.webkit.GeolocationPermissions.Callback) {
        val os = SitePermissionsStore.osPermissionFor(SitePermission.GEOLOCATION) ?: return callback.invoke(origin, true, false)
        if (checkSelfPermission(os) == PackageManager.PERMISSION_GRANTED) {
            callback.invoke(origin, true, false)
        } else {
            pendingGeolocation = origin; pendingGeolocationCallback = callback
            requestPermissions(arrayOf(os), GEOLOCATION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            val request = pendingPermission; pendingPermission = null
            val resource = pendingWebResource; pendingWebResource = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && resource != null) request?.grant(arrayOf(resource)) else request?.deny()
        } else if (requestCode == GEOLOCATION_REQUEST) {
            val origin = pendingGeolocation; pendingGeolocation = null
            val callback = pendingGeolocationCallback; pendingGeolocationCallback = null
            callback?.invoke(origin, grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED, false)
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (fullScreenView != null) { hideFullScreen(); return }
        currentWebView()?.let { web ->
            if (web.canGoBack()) { web.goBack(); return }
        }
        if (tabs.size > 1) closeTab(currentTab() ?: return) else super.onBackPressed()
    }

    override fun onPause() {
        repository.saveTabs(tabs.filterNot(BrowserTab::private)); CookieManager.getInstance().flush(); super.onPause()
    }

    override fun onDestroy() {
        dialog?.dismiss(); dialog = null
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
        private const val GEOLOCATION_REQUEST = 733
        private const val QR_REQUEST = 734
        private val SITE_RESOURCES = mapOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE to SitePermission.CAMERA,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE to SitePermission.MICROPHONE
        )

        fun sitePermissionTitle(resource: SitePermission): String = when (resource) {
            SitePermission.CAMERA -> "دوربین"
            SitePermission.MICROPHONE -> "میکروفون"
            SitePermission.GEOLOCATION -> "موقعیت مکانی"
        }

        private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36"
        private val READER_SCRIPT = """
            (()=>{const a=document.querySelector('article,main,[role=main]')||document.body;document.body.innerHTML='';document.body.appendChild(a);document.body.style='max-width:760px;margin:auto;padding:24px;font:19px/1.8 sans-serif';document.querySelectorAll('script,style,nav,aside,footer,iframe').forEach(x=>x.remove())})()
        """.trimIndent()
    }
}
