package net.gozar.app

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class QsTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var pingJob: Job? = null
    private var livePingMs: Int? = null

    override fun onCreate() {
        super.onCreate()
        VpnBridge.register(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        collectJob?.cancel()
        pingJob?.cancel()
        collectJob = s.launch {
            VpnState.state.collect { render() }
        }
        s.launch {
            ConfigStore.get(applicationContext).awaitReady()
            render()
        }
        pingJob = s.launch { pingLoop() }
        render()
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        pingJob?.cancel(); pingJob = null; livePingMs = null
        // Keep an already requested connection alive when the shade closes.
        super.onStopListening()
    }

    private suspend fun pingLoop() {
        while (true) {
            if (VpnState.state.value == Connection.CONNECTED) {
                val store = runCatching { ConfigStore.get(applicationContext) }.getOrNull()
                val id = VpnState.activeId.value
                val config = store?.configs?.value?.firstOrNull { it.id == id }
                livePingMs = if (config != null && config.address.isNotBlank() && config.port > 0) {
                    (Pinger.ping(config.address, config.port, timeoutMs = 4000) as? PingResult.Ok)?.ms
                } else null
                render()
                kotlinx.coroutines.delay(8000)
            } else {
                if (livePingMs != null) { livePingMs = null; render() }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { toggle() }
        } else {
            toggle()
        }
    }

    private fun toggle() {
        when (VpnState.state.value) {
            Connection.DISCONNECTING -> return
            Connection.CONNECTED, Connection.CONNECTING -> {
                stopTunnel()
                renderOptimistic(false)
                return
            }
            else -> Unit
        }
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        s.launch {
            withTimeoutOrNull(3000) { ConfigStore.get(applicationContext).awaitReady() }
            runCatching { startTunnel() }.onFailure {
                VpnState.setError("اتصال انجام نشد؛ تنظیمات کانفیگ را بررسی کن.")
                android.widget.Toast.makeText(this@QsTileService, "اتصال انجام نشد؛ آیکون را نگه دار تا تنظیمات باز شود.", android.widget.Toast.LENGTH_LONG).show()
            }
            render()
        }
    }

    private fun renderOptimistic(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        runCatching { tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ghajar) }
        tile.updateTile()
    }

    private fun startTunnel() {
        val store = ConfigStore.get(applicationContext)
        val selectedId = store.selectedId.value
        val config = store.configs.value.firstOrNull { it.id == selectedId } ?: store.configs.value.firstOrNull()
        if (config == null) {
            android.widget.Toast.makeText(this, "ابتدا یک کانفیگ اضافه کن؛ برای بازکردن اپ آیکون را نگه دار.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (config.id != selectedId) store.setSelectedId(config.id)
        if (VpnService.prepare(this) != null) { openApp(); return }
        if (config.protocol == "ikev2") {
            IkeController.claim(config)
            if (!IkeController.connect(this, config)) VpnState.setError("اتصال IKEv2 انجام نشد")
            return
        }
        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            mux = store.mux.value, muxConcurrency = store.muxConcurrency.value,
            torBase = store.configs.value.firstOrNull { it.id == config.torBaseId },
            chainBase = store.configs.value.firstOrNull { it.id == config.chainId },
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value
        )
        VpnCommandCoordinator.onConnectRequested(config.id, if (config.protocol == "psiphon") 290_000L else if (config.protocol == "aether") 200_000L else 45_000L) { VpnState.setConnecting(config.id) }
        val intent = Intent(this, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_CONFIG, json)
            .putExtra(GozarVpnService.EXTRA_AETHER, AetherController.spec(config))
            .putExtra(GozarVpnService.EXTRA_PSIPHON, PsiphonSpec.from(config)?.toJson())
            .putExtra(
                GozarVpnService.EXTRA_TOR,
                if (config.protocol == "tor")
                    config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") else if (store.onionRouting.value) "|1" else null
            )
            .putExtra(GozarVpnService.EXTRA_NAME, config.name)
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
        runCatching { ContextCompat.startForegroundService(this, intent) }
            .onFailure { VpnState.setError("شروع سرویس VPN ناموفق بود") }
    }

    private fun stopTunnel() {
        VpnCommandCoordinator.onDisconnectRequested {
            if (IkeController.active) { IkeController.disconnect(this); return@onDisconnectRequested }
            if (VpnState.activeId.value.orEmpty().startsWith("ovpn:") || GhajarOpenVpnBridge.status.value != GhajarOvpnState.DISCONNECTED) {
                scope?.launch { GhajarOpenVpnBridge.disconnect(this@QsTileService) }
                return@onDisconnectRequested
            }
            runCatching {
                startService(Intent(this, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP))
            }
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        super.onDestroy()
    }

    // Only first-time Android VPN consent requires an Activity.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /**
     * Tile STATE_ACTIVE means the actual tunnel is CONNECTED — nothing else.
     * A running service is not proof of a working tunnel, so CONNECTING /
     * DISCONNECTING render their own state but never count as active.
     */
    private fun activeNow(): Boolean = VpnState.state.value == Connection.CONNECTED

    private fun render() {
        val tile = qsTile ?: return
        val active = activeNow()
        val store = runCatching { ConfigStore.get(applicationContext) }.getOrNull()
        val lang = store?.lang?.value ?: Lang.EN
        val selectedName = store?.let { st ->
            val id = st.selectedId.value
            st.configs.value.firstOrNull { it.id == id }?.name
        }
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = Strings.get(lang, "app_title")
        val status = when (VpnState.state.value) {
            Connection.CONNECTING -> Strings.get(lang, "status_connecting")
            Connection.DISCONNECTING -> Strings.get(lang, "status_disconnected")
            Connection.ERROR -> Strings.get(lang, "status_error")
            Connection.CONNECTED -> Strings.get(lang, "status_connected")
            else -> Strings.get(lang, "status_disconnected")
        }
        tile.contentDescription = "${tile.label}، $status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                active && livePingMs != null -> "${livePingMs}ms"
                active -> status
                selectedName != null -> BrandConfig.sanitizePublicText(selectedName)
                else -> Strings.get(lang, "tap_choose")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = status
        // Update both the declared icon and runtime icon so cached tiles refresh.
        runCatching { tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ghajar) }
        // Clear the old click override cached by SystemUI. Long press is
        // resolved through ACTION_QS_TILE_PREFERENCES in the manifest instead.
        if (Build.VERSION.SDK_INT >= 34) tile.setActivityLaunchForClick(null)
        tile.updateTile()
    }
}
