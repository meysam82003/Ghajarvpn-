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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class GhajarWidgetPhase { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

/** Android-free widget contracts kept deterministic for local regression tests. */
internal object GhajarWidgetRules {
    fun phase(connection: Connection, operation: String?): GhajarWidgetPhase = when {
        operation == "disconnecting" && connection != Connection.DISCONNECTED -> GhajarWidgetPhase.DISCONNECTING
        connection == Connection.CONNECTED -> GhajarWidgetPhase.CONNECTED
        connection == Connection.CONNECTING || operation == "connecting" -> GhajarWidgetPhase.CONNECTING
        else -> GhajarWidgetPhase.DISCONNECTED
    }

    fun status(phase: GhajarWidgetPhase): String = when (phase) {
        GhajarWidgetPhase.DISCONNECTED -> "قطع"
        GhajarWidgetPhase.CONNECTING -> "در حال اتصال…"
        GhajarWidgetPhase.CONNECTED -> "متصل"
        GhajarWidgetPhase.DISCONNECTING -> "در حال قطع…"
    }

    fun connectLabel(phase: GhajarWidgetPhase): String = when (phase) {
        GhajarWidgetPhase.DISCONNECTED -> "وصل"
        GhajarWidgetPhase.CONNECTING -> "لغو"
        GhajarWidgetPhase.CONNECTED -> "قطع"
        GhajarWidgetPhase.DISCONNECTING -> "صبر کنید"
    }

    fun nextId(ids: List<String>, currentId: String?): String? {
        if (ids.isEmpty()) return null
        val index = ids.indexOf(currentId)
        return ids[(if (index < 0) 0 else (index + 1) % ids.size)]
    }
}

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
                val message = runCatching {
                    when (intent.action) {
                        ACTION_TOGGLE -> toggle(context.applicationContext)
                        ACTION_PING -> ping(context.applicationContext)
                        ACTION_REFRESH -> refresh(context.applicationContext)
                        ACTION_NEXT_LOCATION -> nextLocation(context.applicationContext)
                        else -> null
                    }
                }.getOrElse { "عملیات ویجت انجام نشد" }
                if (message != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_MESSAGE, message).remove(KEY_OPERATION).apply()
                updateEveryWidget(context.applicationContext)
            } finally { pending.finish() }
        }
    }

    companion object {
        private val WIDGET_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val PREFS = "ghajar_widget"
        private const val KEY_MESSAGE = "message"
        private const val KEY_OPERATION = "operation"
        internal const val ACTION_TOGGLE = "net.gozar.app.widget.TOGGLE"
        internal const val ACTION_PING = "net.gozar.app.widget.PING"
        internal const val ACTION_REFRESH = "net.gozar.app.widget.REFRESH"
        internal const val ACTION_NEXT_LOCATION = "net.gozar.app.widget.NEXT_LOCATION"
        private val ACTIONS = setOf(ACTION_TOGGLE, ACTION_PING, ACTION_REFRESH, ACTION_NEXT_LOCATION)
        const val EXTRA_DESTINATION = "net.gozar.app.widget.DESTINATION"
        const val DEST_SERVERS = "servers"

        private suspend fun toggle(context: Context): String {
            VpnState.initialize(context)
            val snapshot = VpnConnectionStore.read(context)
            val store = ConfigStore.get(context)
            store.awaitReady()
            if (snapshot.state == Connection.CONNECTED || snapshot.state == Connection.CONNECTING) {
                mark(context, "در حال قطع اتصال…", "disconnecting")
                disconnect(context, snapshot, store)
                return "قطع شد"
            }
            val config = store.configs.value.firstOrNull { it.id == store.selectedId.value }
                ?: return "ابتدا سرور را انتخاب کن"
            mark(context, "در حال اتصال…", "connecting")
            return connect(context, store, config)
        }

        private suspend fun connect(context: Context, store: ConfigStore, config: ProxyConfig): String {
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

        private fun disconnect(context: Context, snapshot: VpnConnectionStore.Snapshot, store: ConfigStore) {
            when {
                snapshot.activeId?.startsWith("ovpn:") == true -> GhajarOpenVpnBridge.disconnect(context)
                store.configs.value.firstOrNull { it.id == snapshot.activeId }?.protocol == "ikev2" ->
                    IkeController.disconnect(context)
                else -> context.startService(Intent(context, GozarVpnService::class.java)
                    .setAction(GozarVpnService.ACTION_STOP))
            }
            VpnState.setDisconnected()
        }

        private suspend fun nextLocation(context: Context): String {
            VpnState.initialize(context)
            val store = ConfigStore.get(context)
            store.awaitReady()
            val configs = store.configs.value
            val nextId = GhajarWidgetRules.nextId(configs.map { it.id }, store.selectedId.value)
                ?: return "لوکیشنی برای انتخاب وجود ندارد"
            val next = configs.first { it.id == nextId }
            val snapshot = VpnConnectionStore.read(context)
            store.setSelectedId(next.id)
            if (snapshot.state == Connection.CONNECTED || snapshot.state == Connection.CONNECTING) {
                mark(context, "تغییر به ${publicName(next)}…", "disconnecting")
                disconnect(context, snapshot, store)
                // Let protocol services release their tunnel before the selected
                // location is started again from this same widget action.
                delay(650)
                mark(context, "اتصال به ${publicName(next)}…", "connecting")
                return connect(context, store, next)
            }
            return "لوکیشن: ${publicName(next)}"
        }

        private suspend fun ping(context: Context): String {
            mark(context, "در حال سنجش پینگ واقعی…", null)
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
            mark(context, "در حال بروزرسانی ساب…", "refreshing")
            val store = ConfigStore.get(context)
            store.awaitReady()
            return runCatching { SubscriptionRefresher.refreshAll(store) }.fold(
                onSuccess = { summary ->
                    if (summary.failed == 0) "بروزرسانی موفق · ${summary.configs} کانفیگ"
                    else "خطا · ${summary.updated}/${summary.attempted} ساب بروزرسانی شد"
                },
                onFailure = { "بروزرسانی ساب ناموفق" }
            )
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int,
            layout: Int, provider: Class<*>) {
            val snapshot = VpnConnectionStore.read(context)
            val store = ConfigStore.get(context)
            val selected = store.configs.value.firstOrNull { it.id == (snapshot.activeId ?: store.selectedId.value) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val operation = prefs.getString(KEY_OPERATION, null)
            val phase = GhajarWidgetRules.phase(snapshot.state, operation)
            val status = if (snapshot.state == Connection.ERROR) "خطای اتصال" else GhajarWidgetRules.status(phase)
            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_server,
                selected?.let { "لوکیشن: ${publicName(it)}" }
                    ?: GhajarOpenVpnBridge.activeName(context)?.let { "لوکیشن: ${BrandConfig.sanitizePublicText(it)}" }
                    ?: "لوکیشن انتخاب نشده")
            views.setTextViewText(R.id.widget_connect, GhajarWidgetRules.connectLabel(phase))
            views.setTextViewText(R.id.widget_message,
                prefs.getString(KEY_MESSAGE, "آماده") ?: "آماده")
            views.setTextColor(R.id.widget_status, when (phase) {
                GhajarWidgetPhase.CONNECTED -> 0xFF78D6A8.toInt()
                GhajarWidgetPhase.CONNECTING -> 0xFFFFD166.toInt()
                GhajarWidgetPhase.DISCONNECTING -> 0xFFFFA36C.toInt()
                GhajarWidgetPhase.DISCONNECTED -> 0xFFF7F2E8.toInt()
            })
            views.setOnClickPendingIntent(R.id.widget_connect, broadcast(context, provider, id, ACTION_TOGGLE))
            if (layout == R.layout.ghajar_widget_control) {
                views.setOnClickPendingIntent(R.id.widget_ping, broadcast(context, provider, id, ACTION_PING))
                views.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, provider, id, ACTION_REFRESH))
                views.setOnClickPendingIntent(R.id.widget_servers, broadcast(context, provider, id, ACTION_NEXT_LOCATION))
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

        private fun mark(context: Context, message: String, operation: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MESSAGE, message).putString(KEY_OPERATION, operation).apply()
            updateEveryWidget(context)
        }

        private fun publicName(config: ProxyConfig): String =
            BrandConfig.sanitizePublicText(config.name).ifBlank { config.protocol.uppercase() }
    }
}

class GhajarSmallWidgetProvider : GhajarWidgetProvider() {
    override val layoutId: Int = R.layout.ghajar_widget_small
}

class GhajarControlWidgetProvider : GhajarWidgetProvider() {
    override val layoutId: Int = R.layout.ghajar_widget_control
}
