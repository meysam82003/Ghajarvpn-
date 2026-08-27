package net.gozar.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.os.Message
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import org.json.JSONObject

/**
 * Checkout WebView with a strict host policy. Card-to-card never enters this
 * activity; it is handled by the native shop screen and Android photo picker.
 */
class SecurePaymentActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var initialHost: String? = null
    private var accountToken: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 27, 46)
        window.navigationBarColor = Color.rgb(7, 27, 46)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val checkoutUrl = intent.getStringExtra(EXTRA_URL)?.toUri()
        initialHost = checkoutUrl?.host
        if (checkoutUrl == null || !BrandConfig.isTrustedPaymentUri(checkoutUrl, initialHost)) {
            finishWithError("آدرس پرداخت امن یا معتبر نیست")
            return
        }
        accountToken = GhajarAccountStore(this).token()

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(7, 27, 46))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.userAgentString = settings.userAgentString + " Ghajarvpn/${BuildConfig.VERSION_NAME}"
            isLongClickable = false
            setOnLongClickListener { true }
            webViewClient = CheckoutClient()
            webChromeClient = CheckoutChrome()
        }
        progress = ProgressBar(this)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(7, 27, 46))
            setPadding(dp(12), dp(8), dp(8), dp(8))
            addView(ImageView(this@SecurePaymentActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = "نشان قاجار وی پی ان"
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(TextView(this@SecurePaymentActivity).apply {
                text = "قاجار وی پی ان  •  پرداخت امن"
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(dp(10), 0, dp(10), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageButton(this@SecurePaymentActivity).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "بازگشت به برنامه و بررسی پرداخت"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, dp(60)))
            addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val root = FrameLayout(this).apply {
            addView(column, FrameLayout.LayoutParams(-1, -1))
            addView(progress, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) webView.loadUrl(checkoutUrl.toString())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private inner class CheckoutChrome : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            if (!isUserGesture || resultMsg == null) return false
            val popup = WebView(this@SecurePaymentActivity)
            popup.settings.allowFileAccess = false
            popup.settings.allowContentAccess = false
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (!route(uri)) webView.loadUrl(uri.toString())
                    popup.post { popup.destroy() }
                    return true
                }
            }
            (resultMsg.obj as? WebView.WebViewTransport)?.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }

    private fun route(uri: Uri): Boolean {
        // The bot-return button closes checkout; the native client will verify
        // the invoice via its authenticated API, even if the user pressed Back.
        val botReturn = (uri.scheme.equals("https", true) && uri.host.equals("t.me", true) &&
            uri.path.orEmpty().trim('/').equals("Ghajar_vpnbot", true)) ||
            (uri.scheme.equals("tg", true) && uri.getQueryParameter("domain").equals("Ghajar_vpnbot", true))
        if (botReturn) { setResult(RESULT_OK); finish(); return true }
        if (uri.scheme.equals("https", true)) {
            if (BrandConfig.isTrustedPaymentUri(uri, initialHost) && uri.userInfo == null) return false
            Toast.makeText(this, "این نشانی در فهرست دامنه‌های پرداخت نیست.", Toast.LENGTH_LONG).show()
            return true
        }
        if (uri.scheme?.lowercase() in setOf("http", "file", "content", "javascript", "data", "about")) return true
        return openInstalledBankApp(uri)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private inner class CheckoutClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            progress.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progress.visibility = View.GONE
            val uri = url?.toUri() ?: return
            val onStore = BrandConfig.isTrustedStoreUri(uri)
            if (onStore) {
                val tokenLiteral = JSONObject.quote(accountToken)
                view?.evaluateJavascript(
                    "try{if($tokenLiteral)localStorage.setItem('faoxima.token',$tokenLiteral);}catch(e){};$BRAND_STORE_SCRIPT",
                    null
                )
            }

            // Only the controlled store callback may complete the native flow.
            if (!onStore) return
            val path = uri.path.orEmpty().lowercase()
            val state = sequenceOf("status", "payment", "result")
                .mapNotNull(uri::getQueryParameter)
                .firstOrNull()?.lowercase()
            when {
                path.contains("payment/success") || path.contains("successful") ||
                    state in setOf("success", "successful", "paid", "ok", "1") -> {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_URL, url))
                    finish()
                }
                path.contains("payment/cancel") || path.contains("payment/failed") ||
                    state in setOf("failed", "cancel", "canceled") -> setResult(RESULT_CANCELED)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (request?.isForMainFrame != true) return false
            return request.url?.let(::route) ?: true
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            handler?.cancel()
            finishWithError("گواهی امنیتی درگاه معتبر نیست")
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) {
                progress.visibility = View.GONE
                Toast.makeText(this@SecurePaymentActivity, "صفحهٔ درگاه بارگذاری نشد؛ برگرد و «ادامهٔ همین پرداخت» را بزن.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openInstalledBankApp(uri: Uri): Boolean {
        val clean = uri.toString()
            .replace(Regex("(?i);S\\.browser_fallback_url=[^;]*"), "")
            .toUri()
        val intent = Intent(Intent.ACTION_VIEW, clean).addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved = packageManager.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.packageName != packageName }
            ?: return true
        intent.setPackage(resolved.activityInfo.packageName)
        runCatching { startActivity(intent) }
        return true
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "checkout_url"
        const val EXTRA_RESULT_URL = "checkout_result_url"

        private val BRAND_STORE_SCRIPT = """
            (() => {
              const replacements = [
                new RegExp('Fao' + 'xima', 'gi'),
                new RegExp('GR' + 'oute', 'gi'),
                new RegExp('Gozar' + 'Net', 'gi'),
                new RegExp('Oracle' + '\\s*VPN', 'gi')
              ];
              const brand = 'قاجار وی پی ان';
              const clean = root => {
                if (!root) return;
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
                let node;
                while ((node = walker.nextNode())) {
                  let value = node.nodeValue || '';
                  replacements.forEach(pattern => value = value.replace(pattern, brand));
                  node.nodeValue = value;
                }
                root.querySelectorAll && root.querySelectorAll('.window-url').forEach(item => item.style.display = 'none');
              };
              document.title = brand;
              clean(document.body);
              if (!window.__ghajarBrandObserver && document.body) {
                window.__ghajarBrandObserver = new MutationObserver(records =>
                  records.forEach(record => record.addedNodes.forEach(node => {
                    if (node.nodeType === Node.ELEMENT_NODE) clean(node);
                  }))
                );
                window.__ghajarBrandObserver.observe(document.body, { childList: true, subtree: true });
              }
            })();
        """.trimIndent()
    }
}
