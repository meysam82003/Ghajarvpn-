package com.ghajarvpn.web;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONObject;

/**
 * The Ghajar web app, full screen. Pages of the app stay inside; everything
 * else (Telegram, payment pages, VPN clients' import links, store pages)
 * goes to the app that handles it.
 */
public class MainActivity extends Activity {
    static final String EXTRA_ROUTE = "route";
    private static final int FILE_REQUEST = 7;
    private static final int PAY_REQUEST = 8;
    /** Pages of other apps and stores; every other web page is a payment page and stays in the app. */
    private static final String EXTERNAL_HOSTS = "(?i)^https?://([^/]*\\.)?(t\\.me|telegram\\.me|telegram\\.org|github\\.com|githubusercontent\\.com|google\\.com|apple\\.com|happ\\.su)(/.*)?$";
    private static final int NOTIFY_REQUEST = 3;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF050807);
        web = new WebView(this);
        web.setBackgroundColor(0xFF050807);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Android 15+ draws apps edge to edge: keep the page clear of the
        // status bar, the navigation bar and the keyboard.
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        // An ordinary mobile Chrome to the server, not "; wv".
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(this), "ghajarNative");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        if (state != null) web.restoreState(state);
        else web.loadUrl(Config.APP_URL + routeOf(getIntent()));

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFY_REQUEST);
            }
        }
        Notices.channels(this);
        NoticeJob.schedule(this);
    }

    private static String routeOf(Intent intent) {
        String r = intent == null ? null : intent.getStringExtra(EXTRA_ROUTE);
        return r != null && r.startsWith("#/") ? r : "";
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String route = routeOf(intent);
        if (!route.isEmpty() && web != null) {
            web.evaluateJavascript("location.hash=" + JSONObject.quote(route), null);
        }
    }

    private void goBack() {
        if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    void reload() {
        runOnUiThread(() -> { if (web != null) web.loadUrl(Config.APP_URL); });
    }

    static boolean inApp(String url) {
        return url != null && url.startsWith(Config.APP_URL);
    }

    static boolean isPaymentPage(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://")) && !inApp(url) && !url.matches(EXTERNAL_HOSTS);
    }

    @SuppressWarnings("deprecation")
    void openPayment(String url) {
        runOnUiThread(() -> startActivityForResult(new Intent(this, PaymentActivity.class).putExtra(PaymentActivity.EXTRA_URL, url), PAY_REQUEST));
    }

    void openOutside(String url) {
        if (isPaymentPage(url)) {
            openPayment(url);
            return;
        }
        runOnUiThread(() -> {
            try {
                if (url.startsWith("intent:")) {
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setComponent(null);
                    intent.setSelector(null);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        String fallback = intent.getStringExtra("browser_fallback_url");
                        if (fallback != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                        else if (intent.getPackage() != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + intent.getPackage())));
                        else throw e;
                    }
                    return;
                }
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if (scheme.equals("file") || scheme.equals("javascript") || scheme.equals("content")) return;
                startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
            } catch (Exception e) {
                Toast.makeText(this, "برنامه‌ای برای باز کردن این لینک نصب نیست", Toast.LENGTH_LONG).show();
            }
        });
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (inApp(url)) return false;
            openOutside(url);
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            String html = "<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>html,body{margin:0;min-height:100vh;background:#050807;color:#E9F4EF;font-family:sans-serif}"
                    + "body{display:grid;place-items:center;padding:24px;box-sizing:border-box;text-align:center}"
                    + "p{color:#9DB3AA;line-height:1.9}button{background:#00A86B;color:#02120B;border:0;border-radius:999px;padding:12px 28px;font-size:16px;font-weight:bold}"
                    + "small{color:#6B807A;direction:ltr;display:block;margin-top:16px}</style></head><body><main>"
                    + "<h2>اتصال برقرار نشد</h2><p>اینترنت را بررسی کن و دوباره تلاش کن. اگر VPN روشن است، یک بار آن را خاموش کن و دوباره بزن.</p>"
                    + "<button onclick='ghajarNative.retry()'>تلاش دوباره</button><small>error " + error.getErrorCode() + "</small></main></body></html>";
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), FILE_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PAY_REQUEST && web != null) {
            // Back from the payment screen: the app checks the order, as after the Android app's payment page.
            web.evaluateJavascript("location.hash='#/pay'", null);
            return;
        }
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }
}
