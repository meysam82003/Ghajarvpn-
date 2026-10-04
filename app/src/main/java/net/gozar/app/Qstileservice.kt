package net.gozar.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
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
                // The tile and the widget report the same measurement; the
                // tile is simply the one that has a loop to take it in.
                GhajarWidget.lastPingMs = livePingMs
                GhajarWidget.refresh(applicationContext)
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
        tile.label = BrandConfig.APP_NAME_FA
        tile.contentDescription = "${BrandConfig.APP_NAME_FA}، ${if (active) "روشن" else "خاموش"}"
        runCatching { tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ghajar) }
        tile.updateTile()
    }

    /**
     * The connect itself lives in QuickConnect, shared with the home-screen
     * widget. Only the tile-specific reactions stay here.
     */
    private fun startTunnel() {
        when (QuickConnect.start(this)) {
            QuickConnectResult.NO_CONFIG -> android.widget.Toast.makeText(
                this, "ابتدا یک کانفیگ اضافه کن؛ برای بازکردن اپ آیکون را نگه دار.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            // Only first-time Android VPN consent requires an Activity.
            QuickConnectResult.NEEDS_CONSENT -> openApp()
            QuickConnectResult.STARTED, QuickConnectResult.FAILED -> Unit
        }
    }

    private fun stopTunnel() {
        QuickConnect.stop(this) {
            scope?.launch { GhajarOpenVpnBridge.disconnect(this@QsTileService) }
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
        tile.label = if (lang == Lang.FA) BrandConfig.APP_NAME_FA else BrandConfig.APP_NAME_EN
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
