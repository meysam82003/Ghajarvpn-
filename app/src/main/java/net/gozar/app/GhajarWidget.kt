package net.gozar.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The home-screen widget: connection state, server, ping, and one button that
 * connects or disconnects without opening the app.
 *
 * Two things about how it is driven, both deliberate:
 *
 * The provider info sets updatePeriodMillis to 0 rather than a period. The
 * platform's minimum is thirty minutes, which is meaningless for a connection
 * state, so [refresh] is called on every state change instead - the widget is
 * pushed, never polled, and nothing wakes the device on a timer for it.
 *
 * Connecting goes through [GhajarWidgetConnectActivity] rather than straight
 * from this receiver. Android 12+ refuses a foreground-service start from the
 * background, and a widget click gives a receiver no exemption - so the tunnel
 * would simply not start. An Activity start from a widget click is allowed,
 * and a foreground service started from a visible Activity is too, so the
 * connect runs there in a window with no UI that finishes immediately.
 * Disconnecting needs no trampoline: stopping a service that is already
 * running is not a background start.
 */
class GhajarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            when (VpnState.state.value) {
                // A tap during either transition would be a race with the
                // engine, not a command.
                Connection.CONNECTING, Connection.DISCONNECTING -> Unit
                Connection.CONNECTED -> QuickConnect.stop(context) {
                    // The OpenVPN disconnect is suspending and a receiver has
                    // no scope that outlives onReceive, so it is handed to the
                    // trampoline in its stop mode.
                    context.startActivity(connectIntent(context, stop = true))
                }
                else -> runCatching { context.startActivity(connectIntent(context)) }
                    .onFailure { GhajarLog.e(TAG, "toggle failed: ${it.javaClass.simpleName}") }
            }
            refresh(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val TAG = "GhajarWidget"
        const val ACTION_TOGGLE = "net.gozar.app.WIDGET_TOGGLE"

        /** The last ping the app measured, so the widget shows a real number
         *  or nothing at all - never a stale one presented as current. */
        @Volatile
        var lastPingMs: Int? = null

        /** Pushes the current state into every placed widget. Safe to call
         *  from anywhere, including a process with no widgets placed. */
        fun refresh(context: Context) {
            runCatching {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(app, GhajarWidget::class.java))
                if (ids.isEmpty()) return
                val views = build(app)
                ids.forEach { manager.updateAppWidget(it, views) }
            }.onFailure { GhajarLog.e(TAG, "refresh failed: ${it.javaClass.simpleName}") }
        }

        private fun connectIntent(context: Context, stop: Boolean = false): Intent =
            Intent(context, GhajarWidgetConnectActivity::class.java)
                .putExtra(GhajarWidgetConnectActivity.EXTRA_STOP, stop)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        private fun build(context: Context): RemoteViews {
            val app = context.applicationContext
            val store = runCatching { ConfigStore.get(app) }.getOrNull()
            val lang = store?.lang?.value ?: Lang.FA
            val t: (String) -> String = { Strings.get(lang, it) }
            val state = VpnState.state.value

            val views = RemoteViews(app.packageName, R.layout.widget_ghajar)
            views.setTextViewText(
                R.id.widget_state,
                when (state) {
                    Connection.CONNECTED -> t("status_connected")
                    Connection.CONNECTING -> t("status_connecting")
                    Connection.DISCONNECTING -> t("status_disconnected")
                    Connection.ERROR -> t("status_error")
                    Connection.DISCONNECTED -> t("status_disconnected")
                }
            )
            views.setImageViewResource(
                R.id.widget_dot,
                when (state) {
                    Connection.CONNECTED -> R.drawable.widget_dot_on
                    Connection.CONNECTING, Connection.DISCONNECTING -> R.drawable.widget_dot_busy
                    Connection.ERROR -> R.drawable.widget_dot_error
                    Connection.DISCONNECTED -> R.drawable.widget_dot_off
                }
            )

            // While OpenVPN owns the tunnel the active id is "ovpn:<uuid>",
            // which is never in the config list - the same trap the home
            // screen fell into. Its name comes from the bridge instead.
            val activeId = VpnState.activeId.value
            val name = when {
                activeId.orEmpty().startsWith("ovpn:") ->
                    runCatching {
                        val uuid = activeId!!.removePrefix("ovpn:")
                        GhajarOpenVpnBridge.profiles(app).firstOrNull { it.uuid == uuid }?.name
                    }.getOrNull() ?: "OpenVPN"
                else -> store?.let { s ->
                    val id = activeId ?: s.selectedId.value
                    s.configs.value.firstOrNull { it.id == id }?.name
                }
            }
            views.setTextViewText(
                R.id.widget_server,
                name?.let(BrandConfig::sanitizePublicText) ?: t("hub_no_server")
            )

            // A ping only means something while a tunnel is up, and only if it
            // was actually measured.
            val ping = lastPingMs
            views.setTextViewText(
                R.id.widget_ping,
                if (state == Connection.CONNECTED && ping != null)
                    localizeDigits("$ping ${t("unit_ms")}", lang) else ""
            )

            views.setTextViewText(
                R.id.widget_toggle,
                when (state) {
                    Connection.CONNECTED, Connection.CONNECTING -> t("disconnect")
                    Connection.DISCONNECTING -> t("status_disconnected")
                    else -> t("connect")
                }
            )

            val toggle = Intent(app, GhajarWidget::class.java).setAction(ACTION_TOGGLE)
            views.setOnClickPendingIntent(
                R.id.widget_toggle,
                PendingIntent.getBroadcast(
                    app, 1, toggle,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            val open = Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_open,
                PendingIntent.getActivity(
                    app, 2, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return views
        }
    }
}
