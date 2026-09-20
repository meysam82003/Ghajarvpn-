package net.gozar.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver.PendingResult
import android.content.Intent
import android.widget.RemoteViews
import gozarcore.Gozarcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        // goAsync() has to be called on the instance the system handed this
        // broadcast to, and only while onReceive is still on the stack - the
        // PendingResult it returns belongs to this delivery. Taking it here and
        // passing it in is why these run on a receiver, at all.
        when (intent.action) {
            ACTION_PING -> { runAsync(context, intent, goAsyncOrNull()) { measurePing(context) }; return }
            ACTION_SUBS -> { runAsync(context, intent, goAsyncOrNull()) { refreshSubs(context) }; return }
            ACTION_NEXT -> { runAsync(context, intent, goAsyncOrNull()) { nextService(context) }; return }
        }
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

    /** Null when the platform refuses (already finished, or not a real
     *  broadcast delivery), in which case the work still runs - it just is not
     *  protected from the process being killed. */
    private fun goAsyncOrNull(): PendingResult? = runCatching { goAsync() }.getOrNull()

    companion object {
        private const val TAG = "GhajarWidget"
        const val ACTION_TOGGLE = "net.gozar.app.WIDGET_TOGGLE"
        const val ACTION_PING = "net.gozar.app.WIDGET_PING"
        const val ACTION_SUBS = "net.gozar.app.WIDGET_SUBS"
        const val ACTION_NEXT = "net.gozar.app.WIDGET_NEXT"

        /** The last ping the app measured, so the widget shows a real number
         *  or nothing at all - never a stale one presented as current. */
        @Volatile
        var lastPingMs: Int? = null

        /** Pushes the current state into every placed widget. Safe to call
         *  from anywhere, including a process with no widgets placed. */
        fun refresh(context: Context) {
            runCatching {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app) ?: return@runCatching
                val ids = manager.getAppWidgetIds(ComponentName(app, GhajarWidget::class.java))
                if (ids.isNotEmpty()) {
                    val views = build(app)
                    ids.forEach { manager.updateAppWidget(it, views) }
                }
            }.onFailure { GhajarLog.e(TAG, "refresh failed: ${it.javaClass.simpleName}") }
            // The small widget moves with it. Driving both from one call means
            // every existing caller - the service, the tile, the state collector
            // - keeps the one-cell widget current without knowing it exists.
            GhajarWidgetSmall.refresh(context)
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
            // The server line, and the reason it used to be wrong.
            //
            // ConfigStore fills its lists from disk in a coroutine, so for the
            // first moments of a cold process `configs` is empty and every
            // lookup above misses. The widget is refreshed on exactly those
            // moments - a boot, a reinstall, a process the system just started
            // to deliver a broadcast - so it kept drawing "no server chosen"
            // over a phone with twenty-seven of them, and the rail's buttons
            // looked broken because they were acting on an empty list.
            //
            // Two halves to the fix: remember the last name that did resolve,
            // and ask for another refresh once the store is actually ready.
            // The state, the toggle and the dot are never cached - those come
            // from VpnState, which is correct from the first instant.
            val prefs = app.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val resolved = name?.let(BrandConfig::sanitizePublicText)
            val loading = store != null && store.configs.value.isEmpty()
            if (resolved != null) {
                prefs.edit().putString(KEY_LAST_SERVER, resolved).apply()
            } else if (!loading) {
                prefs.edit().remove(KEY_LAST_SERVER).apply()
            }
            views.setTextViewText(
                R.id.widget_server,
                resolved
                    ?: prefs.getString(KEY_LAST_SERVER, null)?.takeIf { loading }
                    ?: t("hub_no_server")
            )
            if (loading) awaitStoreThenRefresh(app, store)

            // A ping only means something while a tunnel is up, and only if it
            // was actually measured.
            val ping = lastPingMs
            views.setTextViewText(
                R.id.widget_ping,
                if (state == Connection.CONNECTED && ping != null)
                    localizeDigits("$ping ${t("unit_ms")}", lang) else ""
            )

            // Session traffic. Blank rather than zero when nothing has been
            // measured: a widget reading "0 B" beside a live tunnel looks like
            // an answer, and it is the absence of one.
            val down = sessionDownBytes
            val up = sessionUpBytes
            views.setTextViewText(
                R.id.widget_down,
                if (down > 0) "\u2193 " + widgetBytes(down, lang) else ""
            )
            views.setTextViewText(
                R.id.widget_up,
                if (up > 0) "\u2191 " + widgetBytes(up, lang) else ""
            )

            views.setTextViewText(R.id.widget_ping_btn, if (busy) "..." else "پینگ")
            views.setTextViewText(R.id.widget_subs_btn, if (busy) "..." else "آپدیت ساب")
            views.setTextViewText(R.id.widget_next_btn, if (busy) "..." else "سرویس بعدی")
            bindAction(app, views, R.id.widget_ping_btn, ACTION_PING, 3)
            bindAction(app, views, R.id.widget_subs_btn, ACTION_SUBS, 4)
            bindAction(app, views, R.id.widget_next_btn, ACTION_NEXT, 5)

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

        private fun bindAction(app: Context, views: RemoteViews, viewId: Int, action: String, requestCode: Int) {
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(
                    app, requestCode,
                    Intent(app, GhajarWidget::class.java).setAction(action),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        /**
         * Session traffic, pushed from wherever the app is already counting it.
         *
         * The widget does not measure anything itself. TrafficStats is
         * device-wide, so a widget reading it directly would report every
         * app's traffic as this tunnel's - a number that is always wrong and
         * always plausible, which is the worst kind.
         */
        @Volatile var sessionUpBytes: Long = 0L
        @Volatile var sessionDownBytes: Long = 0L

        /** True while one of the three rail actions is running, so the labels
         *  can say so instead of looking like a tap that did nothing. */
        @Volatile private var busy: Boolean = false

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private const val WIDGET_PREFS = "ghajarvpn_widget"
        private const val KEY_LAST_SERVER = "last_server"

        /**
         * One wait per process, and it is never cleared.
         *
         * Several widgets on one screen all build in the same pass, so this
         * stops a wait per widget. It stays set after the wait finishes on
         * purpose: the redraw below calls build() again, which would queue
         * another wait if the store still had nothing, and a store that never
         * loads would have this rescheduling itself every ten seconds forever.
         */
        @Volatile private var awaitingStore = false

        /**
         * Redraws once the config store has finished loading from disk.
         *
         * Without this the widget stayed on whatever it could see during the
         * cold start until something else happened to refresh it, which on a
         * phone that is not connecting or disconnecting can be a very long
         * time.
         */
        private fun awaitStoreThenRefresh(app: Context, store: ConfigStore?) {
            if (store == null || awaitingStore) return
            awaitingStore = true
            widgetScope.launch {
                withTimeoutOrNull(10_000) { store.awaitReady() }
                withContext(Dispatchers.Main) { refresh(app) }
            }
        }

        private fun widgetBytes(bytes: Long, lang: Lang): String {
            val (num, unit) = when {
                bytes < 1024 -> "$bytes" to Strings.get(lang, "unit_b")
                bytes < 1024 * 1024 -> "%.1f".format(bytes / 1024.0) to Strings.get(lang, "unit_kb")
                bytes < 1024L * 1024 * 1024 -> "%.1f".format(bytes / (1024.0 * 1024)) to Strings.get(lang, "unit_mb")
                else -> "%.2f".format(bytes / (1024.0 * 1024 * 1024)) to Strings.get(lang, "unit_gb")
            }
            return localizeDigits(num, lang) + " " + unit
        }

        /**
         * Runs a rail action outside onReceive, and keeps the process alive
         * for it.
         *
         * `goAsync()` is what makes this legal: a BroadcastReceiver is killed
         * the moment onReceive returns, so a coroutine started there would be
         * torn down mid-request. Every action is also capped - a subscription
         * refresh against a dead host would otherwise hold the receiver open
         * until the system kills it, and the widget would sit on "..." for as
         * long as that took.
         */
        private fun runAsync(
            context: Context,
            intent: Intent,
            pending: PendingResult?,
            block: suspend () -> Unit
        ) {
            val app = context.applicationContext
            busy = true
            refresh(app)
            widgetScope.launch {
                try {
                    withTimeoutOrNull(25_000) { block() }
                } catch (t: Throwable) {
                    GhajarLog.e(TAG, "widget action ${intent.action} failed: ${t.javaClass.simpleName}")
                } finally {
                    busy = false
                    withContext(Dispatchers.Main) { refresh(app) }
                    runCatching { pending?.finish() }
                }
            }
        }

        /** Measures the active config and publishes the result to the widget. */
        private suspend fun measurePing(context: Context) {
            val app = context.applicationContext
            val store = runCatching { ConfigStore.get(app) }.getOrNull() ?: return
            // Every rail action waits for the store, because a widget tap is
            // very often the thing that started this process: acting on the
            // empty list a still-loading store exposes is what made all three
            // buttons look like they did nothing.
            store.awaitReady()
            val id = VpnState.activeId.value ?: store.selectedId.value
            val cfg = store.configs.value.firstOrNull { it.id == id } ?: return
            val ms: Long = withContext(Dispatchers.IO) {
                runCatching { Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg)) }
                    .getOrDefault(-1L)
            }
            // A failure clears the number rather than leaving the last good one
            // on screen, which would be presenting a stale measurement as now.
            lastPingMs = if (ms >= 0) ms.toInt() else null
        }

        private suspend fun refreshSubs(context: Context) {
            val store = runCatching { ConfigStore.get(context.applicationContext) }.getOrNull() ?: return
            store.awaitReady()
            SubscriptionRefresher.refreshStale(store, force = true)
        }

        /**
         * Moves to the next service in the list, and connects to it if a
         * tunnel is already up.
         *
         * Ordered by the stored list rather than by ping: this is "give me a
         * different one", and re-measuring every server first would make a
         * one-tap action take half a minute. Picking the fastest is what the
         * app's own button is for.
         */
        private suspend fun nextService(context: Context) {
            val app = context.applicationContext
            val store = runCatching { ConfigStore.get(app) }.getOrNull() ?: return
            store.awaitReady()
            val configs = store.configs.value
            if (configs.size < 2) return
            val currentId = VpnState.activeId.value ?: store.selectedId.value
            val index = configs.indexOfFirst { it.id == currentId }
            val next = configs[(if (index < 0) 0 else index + 1) % configs.size]
            store.setSelectedId(next.id)
            lastPingMs = null
            if (VpnState.state.value == Connection.CONNECTED) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        app.startActivity(connectIntent(app))
                    }.onFailure { GhajarLog.e(TAG, "next service connect failed: ${it.javaClass.simpleName}") }
                }
            }
        }
    }
}

/**
 * The one-button widget: the logo, and the tunnel behind it.
 *
 * A separate provider rather than a size variant of the one above, because a
 * RemoteViews layout is chosen per provider and the two have nothing in common
 * but the toggle - one is a readout with a rail of actions, this is a button.
 */
class GhajarWidgetSmall : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GhajarWidget.ACTION_TOGGLE) {
            // Handled by the full widget's provider so there is exactly one
            // implementation of "toggle the tunnel", and then both widgets are
            // refreshed - a phone can have either, or both.
            GhajarWidget().onReceive(context, intent)
            refresh(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val TAG = "GhajarWidgetSmall"

        fun refresh(context: Context) {
            runCatching {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(app, GhajarWidgetSmall::class.java))
                if (ids.isEmpty()) return
                val views = build(app)
                ids.forEach { manager.updateAppWidget(it, views) }
            }.onFailure { GhajarLog.e(TAG, "refresh failed: ${it.javaClass.simpleName}") }
        }

        private fun build(context: Context): RemoteViews {
            val app = context.applicationContext
            val views = RemoteViews(app.packageName, R.layout.widget_ghajar_small)
            views.setImageViewResource(
                R.id.widget_small_dot,
                when (VpnState.state.value) {
                    Connection.CONNECTED -> R.drawable.widget_dot_on
                    Connection.CONNECTING, Connection.DISCONNECTING -> R.drawable.widget_dot_busy
                    Connection.ERROR -> R.drawable.widget_dot_error
                    Connection.DISCONNECTED -> R.drawable.widget_dot_off
                }
            )
            views.setOnClickPendingIntent(
                R.id.widget_small_button,
                PendingIntent.getBroadcast(
                    app, 11,
                    Intent(app, GhajarWidgetSmall::class.java).setAction(GhajarWidget.ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return views
        }
    }
}
