package net.gozar.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.net.toUri

/**
 * Minimal in-app WebView for non-payment store pages (terms, help, support,
 * user panel): same "no address bar, no raw URL" policy as the payment
 * checkout screen, just without the payment-specific callback handling.
 * Replaces the old :browser module's embedded store mode, which this project
 * no longer ships.
 */
class GhajarStoreWebActivity : Activity() {
    private lateinit var webView: WebView
    private var originHost: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 27, 46)
        window.navigationBarColor = Color.rgb(7, 27, 46)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val url = intent.getStringExtra(EXTRA_URL)?.toUri()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "قاجار وی پی ان"
        if (url == null || !BrandConfig.isTrustedStoreUri(url)) {
            finish()
            return
        }
        originHost = url.host

        val progress = ProgressBar(this)
        val webContainer = FrameLayout(this).apply { setBackgroundColor(Color.rgb(7, 27, 46)) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(7, 27, 46))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " Ghajarvpn/${BuildConfig.VERSION_NAME}"
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, u: String?, favicon: android.graphics.Bitmap?) {
                    progress.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, u: String?) {
                    progress.visibility = View.GONE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request?.isForMainFrame != true) return false
                    val next = request.url ?: return true
                    val host = next.host?.lowercase()?.trimEnd('.') ?: return true
                    val allowed = next.scheme.equals("https", true) &&
                        (host == originHost || host.endsWith(".$originHost") || BrandConfig.isTrustedStoreUri(next))
                    return !allowed
                }
            }
        }
        webContainer.addView(webView, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(7, 27, 46))
            setPadding(dp(12), dp(8), dp(8), dp(8))
            addView(TextView(this@GhajarStoreWebActivity).apply {
                text = title
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(dp(10), 0, dp(10), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageButton(this@GhajarStoreWebActivity).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "بستن"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, dp(56)))
            addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val root = FrameLayout(this).apply {
            addView(column, FrameLayout.LayoutParams(-1, -1))
            addView(progress, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        webView.loadUrl(url.toString())
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "store_web_url"
        const val EXTRA_TITLE = "store_web_title"
    }
}
