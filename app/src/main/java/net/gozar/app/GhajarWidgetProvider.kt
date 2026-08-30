package net.gozar.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object GhajarNavigationBus {
    private val _destination = MutableStateFlow<String?>(null)
    val destination = _destination
    fun offer(value: String?) { if (!value.isNullOrBlank()) _destination.value = value }
    fun consume() { _destination.value = null }
}

abstract class GhajarWidgetProvider : AppWidgetProvider() {
    protected abstract val layoutId: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it, layoutId, javaClass) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        WIDGET_SCOPE.launch {
            try {
                val message = when (intent.action) {
                    ACTION_TOGGLE -> toggle(context.applicationContext)
                    ACTION_PING -> ping(context.applicationContext)
                    ACTION_REFRESH -> refresh(context.applicationContext)
                    else -> null
                }
                if (message != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_MESSAGE, message).apply()
                updateEveryWidget(context.applicationContext)
            } finally { pending.finish() }
        }
    }

    companion object {
        private val WIDGET_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val PREFS = "ghajar_widget"
        private const val KEY_MESSAGE = "message"
        private const val ACTION_TOGGLE = "net.gozar.app.widget.TOGGLE"
        private const val ACTION_PING = "net.gozar.app.widget.PING"
        private const val ACTION_REFRESH = "net.gozar.app.widget.REFRESH"
        private val ACTIONS = setOf(ACTION_TOGGLE, ACTION_PING, ACTION_REFRESH)
        const val EXTRA_DESTINATION = "net.gozar.app.widget.DESTINATION"
        const val DEST_SERVERS = "servers"

        private suspend fun toggle(context: Context): String {
            VpnState.initialize(context)
            val snapshot = VpnConnectionStore.read(context)
            val store = ConfigStore.get(context)
            store.awaitReady()
            if (snapshot.state == Connection.CONNECTED || snapshot.state == Connection.CONNECTING) {
                when {
                    snapshot.activeId?.startsWith("ovpn:") == true -> GhajarOpenVpnBridge.disconnect(context)
                    store.configs.value.firstOrNull { it.id == snapshot.activeId }?.protocol == "ikev2" ->
                        IkeController.disconnect(context)
                    else -> context.startService(Intent(context, GozarVpnService::class.java)
                        .setAction(GozarVpnService.ACTION_STOP))
                }
                VpnState.setDisconnected()
                return "قطع شد"
            }
            val id = store.selectedId.value
            val config = store.configs.value.firstOrNull { it.id == id }
                ?: return "ابتدا سرور را انتخاب کن"
            if (VpnService.prepare(context) != null) return "برای مجوز، برنامه را باز کن"
            if (config.protocol == "openvpn" || config.id.startsWith("ovpn:")) {
                return GhajarOpenVpnBridge.connectSaved(context, config).fold(
                    onSuccess = { "در حال اتصال OVPN…" },
                    onFailure = { it.message ?: "اتصال OVPN شروع نشد" }
                )
            }
            if (config.protocol == "ikev2") {
                IkeController.bind(context)
                IkeController.claim(config)
                return if (IkeController.connect(context, config)) "در حال اتصال…" else "اتصال شروع نشد"
            }
            val json = ConfigBuilder.build(config, store.fragment.value, store.splitRouting.value,
                store.sniffing.value, store.sniffTypes.value, adBlock = store.adBlock.value,
                fakeDns = store.fakeDns.value, encryptedDns = store.encryptedDns.value,
                onionRouting = store.onionRouting.value, coreLogLevel = store.coreLogLevel.value)
            VpnState.setConnecting(config.id)
            val intent = Intent(context, GozarVpnService::class.java)
                .putExtra(GozarVpnService.EXTRA_CONFIG, json)
                .putExtra(GozarVpnService.EXTRA_AETHER, AetherSpec.from(config)?.toJson())
                .putExtra(GozarVpnService.EXTRA_TOR, if (config.protocol == "tor")
                    config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") else null)
                .putExtra(GozarVpnService.EXTRA_NAME, config.name)
                .putExtra(GozarVpnService.EXTRA_STOP_LABEL, "قطع اتصال")
            return runCatching { ContextCompat.startForegroundService(context, intent); "در حال اتصال…" }
                .getOrElse { VpnState.setDisconnected(); "اتصال شروع نشد" }
        }

        private suspend fun ping(context: Context): String {
            val store = ConfigStore.get(context)
            store.awaitReady()
            val active = VpnConnectionStore.read(context).activeId
            val config = store.configs.value.firstOrNull { it.id == active }
                ?: store.configs.value.firstOrNull { it.id == store.selectedId.value }
                ?: return "سروری انتخاب نشده"
            val result = withContext(Dispatchers.IO) {
                if (config.protocol.equals("ikev2", true)) Pinger.pingIke(config.address)
                else Pinger.ping(config.address, config.port, 3500)
            }
            return when (result) {
                is PingResult.Ok -> "پینگ ${result.ms} ms"
                else -> "پینگ ناموفق"
            }
        }

        private suspend fun refresh(context: Context): String {
            val store = ConfigStore.get(context)
            store.awaitReady()
            val summary = SubscriptionRefresher.refreshAll(store)
            return if (summary.failed == 0) "${summary.configs} کانفیگ بروزرسانی شد"
            else "${summary.updated}/${summary.attempted} ساب بروزرسانی شد"
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int,
            layout: Int, provider: Class<*>) {
            val snapshot = VpnConnectionStore.read(context)
            val store = ConfigStore.get(context)
            val selected = store.configs.value.firstOrNull { it.id == (snapshot.activeId ?: store.selectedId.value) }
            val status = when (snapshot.state) {
                Connection.CONNECTED -> "متصل"
                Connection.CONNECTING -> "در حال اتصال…"
                Connection.ERROR -> "خطای اتصال"
                else -> "قطع"
            }
            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_server,
                selected?.name?.let(BrandConfig::sanitizePublicText)
                    ?: GhajarOpenVpnBridge.activeName(context) ?: "انتخاب سرور")
            views.setTextViewText(R.id.widget_connect,
                if (snapshot.state in setOf(Connection.CONNECTED, Connection.CONNECTING)) "قطع" else "وصل")
            views.setTextViewText(R.id.widget_message,
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MESSAGE, "آماده") ?: "آماده")
            views.setOnClickPendingIntent(R.id.widget_connect, broadcast(context, provider, id, ACTION_TOGGLE))
            if (layout == R.layout.ghajar_widget_control) {
                views.setOnClickPendingIntent(R.id.widget_ping, broadcast(context, provider, id, ACTION_PING))
                views.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, provider, id, ACTION_REFRESH))
                views.setOnClickPendingIntent(R.id.widget_servers, openApp(context, id, DEST_SERVERS))
            }
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, id, null))
            manager.updateAppWidget(id, views)
        }

        private fun broadcast(context: Context, provider: Class<*>, id: Int, action: String): PendingIntent {
            val intent = Intent(context, provider).setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(context, id * 10 + action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun openApp(context: Context, id: Int, destination: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_DESTINATION, destination)
            return PendingIntent.getActivity(context, id * 100 + (destination?.hashCode() ?: 0), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun updateEveryWidget(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(GhajarSmallWidgetProvider::class.java to R.layout.ghajar_widget_small,
                GhajarControlWidgetProvider::class.java to R.layout.ghajar_widget_control).forEach { (provider, layout) ->
                manager.getAppWidgetIds(ComponentName(context, provider)).forEach {
                    render(context, manager, it, layout, provider)
                }
            }
        }
    }
}

class GhajarSmallWidgetProvider : GhajarWidgetProvider() {
    override val layoutId: Int = R.layout.ghajar_widget_small
}

class GhajarControlWidgetProvider : GhajarWidgetProvider() {
    override val layoutId: Int = R.layout.ghajar_widget_control
}
