package net.gozar.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The widget's connect, run from an Activity because Android will not let a
 * receiver do it.
 *
 * Android 12+ refuses to start a foreground service from the background, and a
 * widget click gives a broadcast receiver no exemption - so a connect issued
 * straight from the provider would simply never start the tunnel. An Activity
 * launched by a widget click is allowed, and a foreground service started from
 * a visible Activity is too.
 *
 * It has no UI and no window animation, and finishes as soon as the command is
 * issued, so from the home screen this is invisible: the widget's button
 * works, and the app does not open.
 */
class GhajarWidgetConnectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stopping = intent?.getBooleanExtra(EXTRA_STOP, false) == true
        lifecycleScope.launch {
            if (stopping) {
                // The one case the provider hands over: OpenVPN's disconnect is
                // suspending, and a receiver has no scope that outlives it.
                runCatching { GhajarOpenVpnBridge.disconnect(this@GhajarWidgetConnectActivity) }
                    .onFailure { GhajarLog.e(TAG, "widget disconnect failed: ${it.javaClass.simpleName}") }
            } else {
                withTimeoutOrNull(3_000) {
                    ConfigStore.get(applicationContext).awaitReady()
                }
                when (QuickConnect.start(this@GhajarWidgetConnectActivity)) {
                    // Both need the app, and both say so instead of failing
                    // silently on the home screen.
                    QuickConnectResult.NO_CONFIG -> toast("add_server")
                    QuickConnectResult.NEEDS_CONSENT -> {
                        startActivity(
                            android.content.Intent(applicationContext, MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    QuickConnectResult.FAILED -> toast("status_error")
                    QuickConnectResult.STARTED -> Unit
                }
            }
            GhajarWidget.refresh(applicationContext)
            finish()
        }
    }

    private fun toast(key: String) {
        val lang = runCatching { ConfigStore.get(applicationContext).lang.value }.getOrNull() ?: Lang.FA
        Toast.makeText(this, Strings.get(lang, key), Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_STOP = "net.gozar.app.WIDGET_STOP"
        private const val TAG = "GhajarWidget"
    }
}
