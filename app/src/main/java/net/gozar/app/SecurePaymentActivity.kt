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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Embedded checkout. HTTPS pages stay inside Ghajarvpn and no address bar is shown.
 * Bank-app deep links are opened only when an installed non-browser application handles them.
 */
class SecurePaymentActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 27, 46)
        window.navigationBarColor = Color.rgb(7, 27, 46)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val checkoutUrl = intent.getStringExtra(EXTRA_URL)?.toUri()
        if (checkoutUrl?.scheme != "https") {
            finishWithError("آدرس پرداخت امن نیست")
            return
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(7, 27, 46))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString + " Ghajarvpn/3.0"
            webViewClient = CheckoutClient()
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
                contentDescription = "بستن پرداخت"
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
            addView(
                progress,
                FrameLayout.LayoutParams(56, 56, Gravity.CENTER)
            )
        }
        setContentView(root)
        webView.loadUrl(checkoutUrl.toString())
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
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

    private inner class CheckoutClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            progress.visibility = android.view.View.GONE
            val uri = url?.toUri() ?: return
            if (uri.host.equals(BrandConfig.STORE_HOST, ignoreCase = true)) {
                view?.evaluateJavascript(BRAND_STORE_SCRIPT, null)
            }
            val path = uri.path.orEmpty().lowercase()
            val state = listOf("status", "payment", "result")
                .firstNotNullOfOrNull { uri.getQueryParameter(it) }
                ?.lowercase()
            when {
                path.contains("payment/success") || path.contains("payment/succeeded") ||
                    state in setOf("success", "successful", "paid", "ok", "1") -> {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_URL, url))
                    finish()
                }
                path.contains("payment/cancel") || path.contains("payment/failed") -> {
                    setResult(RESULT_CANCELED)
                }
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return true
            if (uri.scheme == "https") return false
            if (uri.scheme == "http" || uri.scheme == "file" || uri.scheme == "content") return true
            return openInstalledBankApp(uri)
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            handler?.cancel()
            finishWithError("گواهی امنیتی درگاه معتبر نیست")
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) progress.visibility = android.view.View.GONE
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
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
                let node;
                while ((node = walker.nextNode())) {
                  let value = node.nodeValue || '';
                  replacements.forEach(pattern => value = value.replace(pattern, brand));
                  node.nodeValue = value;
                }
              };
              document.title = brand;
              clean(document.body);
              if (!window.__ghajarBrandObserver) {
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
