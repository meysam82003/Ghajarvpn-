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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class QsTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        VpnBridge.register(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(Dispatchers.Main)
        scope = s
        collectJob = s.launch {
            VpnState.state.collect { render() }
        }
        s.launch {
            ConfigStore.get(applicationContext).awaitReady()
            render()
        }
        render()
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        scope = null
        super.onStopListening()
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
        GhajarLog.i("QsTile", "toggle tapped, current state=${VpnState.state.value}")
        when (VpnState.state.value) {
            Connection.CONNECTED, Connection.CONNECTING, Connection.DISCONNECTING -> {
                stopTunnel()
                // VpnCommandCoordinator dispatches the actual disconnect asynchronously
                // (it hops through a coroutine + mutex before VpnState changes), so at
                // this exact point in the call stack VpnState.state.value is still the
                // OLD value. Calling render() here used to read that stale "connected"
                // state and redraw the tile as active — the icon looked stuck on until
                // the notification shade was closed and reopened. Reflect the user's
                // intent immediately instead of reading state that hasn't caught up yet;
                // the collectJob below will correct/confirm it the moment it does.
                renderOptimistic(active = false)
                return
            }
            else -> Unit
        }
        // Same reasoning in the other direction: onConnectRequested() is also async,
        // so show "off" immediately (render() below will already know to say
        // "در حال اتصال…" once the real CONNECTING state lands).
        renderOptimistic(active = false)
        val s = scope ?: CoroutineScope(Dispatchers.Main).also { scope = it }
        s.launch {
            withTimeoutOrNull(3000) { ConfigStore.get(applicationContext).awaitReady() }
            startTunnel()
            render()
        }
    }

    /** Draws the tile from a known intent instead of (possibly stale) VpnState. */
    private fun renderOptimistic(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        runCatching { tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ghajar) }
        tile.updateTile()
    }

    private fun startTunnel() {
        val store = ConfigStore.get(applicationContext)
        val selectedId = store.selectedId.value
        val config = store.configs.value.firstOrNull { it.id == selectedId }
        if (config == null) { openApp(); return }
        if (VpnService.prepare(this) != null) { openApp(); return }
        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value
        )
        VpnCommandCoordinator.onConnectRequested(config.id) { VpnState.setConnecting(config.id) }
        val intent = Intent(this, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_CONFIG, json)
            .putExtra(GozarVpnService.EXTRA_AETHER, AetherSpec.from(config)?.toJson())
            .putExtra(
                GozarVpnService.EXTRA_TOR,
                if (config.protocol == "tor")
                    config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") else null
            )
            .putExtra(GozarVpnService.EXTRA_NAME, config.name)
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
        runCatching { ContextCompat.startForegroundService(this, intent) }
            .onFailure { VpnState.setDisconnected(); openApp() }
    }

    private fun stopTunnel() {
        VpnCommandCoordinator.onDisconnectRequested {
            runCatching {
                startService(Intent(this, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP))
            }
        }
    }

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
                active -> status
                selectedName != null -> BrandConfig.sanitizePublicText(selectedName)
                else -> Strings.get(lang, "tap_choose")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = status
        // Without this, Android's default long-press behaviour on a QS tile opens
        // the system "App Info" settings page instead of the app itself. Setting
        // the tile's own launch intent replaces that default with our app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val openAppIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            runCatching { tile.setActivityLaunchForClick(openAppIntent) }
        }
        // Update both the declared icon and runtime icon so cached tiles refresh.
        runCatching { tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ghajar) }
        tile.updateTile()
    }
}
