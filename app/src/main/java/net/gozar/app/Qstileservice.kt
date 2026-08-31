package net.gozar.app

import android.app.PendingIntent
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
        VpnState.initialize(applicationContext)
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
        if (activeNow()) {
            stopTunnel()
            render()
            return
        }
        val s = scope ?: CoroutineScope(Dispatchers.Main).also { scope = it }
        s.launch {
            withTimeoutOrNull(3000) { ConfigStore.get(applicationContext).awaitReady() }
            startTunnel()
            render()
        }
    }

    private suspend fun startTunnel() {
        val store = ConfigStore.get(applicationContext)
        val selectedId = store.selectedId.value
        val config = store.configs.value.firstOrNull { it.id == selectedId }
        if (config == null) { openApp(); return }
        if (VpnService.prepare(this) != null) { openApp(); return }
        if (config.protocol.equals("openvpn", true) || config.id.startsWith("ovpn:")) {
            GhajarOpenVpnBridge.connectSaved(applicationContext, config)
                .onFailure { VpnState.setError(it.message ?: "OVPN failed"); openApp() }
            return
        }
        if (config.protocol.equals("ikev2", true)) {
            IkeController.bind(applicationContext)
            IkeController.claim(config)
            if (!IkeController.connect(applicationContext, config)) openApp()
            return
        }
        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value
        )
        VpnState.setConnecting(config.id)
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
        VpnState.beginDisconnecting()
        val snapshot = VpnConnectionStore.read(applicationContext)
        if (snapshot.activeId?.startsWith("ovpn:") == true) {
            GhajarOpenVpnBridge.disconnect(applicationContext)
            VpnState.setDisconnected()
            return
        }
        val store = ConfigStore.get(applicationContext)
        if (IkeController.active || store.configs.value.firstOrNull { it.id == snapshot.activeId }?.protocol == "ikev2") {
            IkeController.disconnect(applicationContext)
            VpnState.setDisconnected()
            return
        }
        // Xray: wait for the service confirmation broadcast; the VpnState
        // watchdog guarantees the UI never stays on "disconnecting".
        runCatching {
            startService(Intent(this, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP))
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

    private fun activeNow(): Boolean {
        val state = VpnConnectionStore.read(applicationContext).state
        return state == Connection.CONNECTED || state == Connection.CONNECTING
    }

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
        val status = when (VpnConnectionStore.read(applicationContext).state) {
            Connection.CONNECTING -> Strings.get(lang, "status_connecting")
            Connection.DISCONNECTING -> Strings.get(lang, "status_disconnecting")
            Connection.ERROR -> Strings.get(lang, "status_error")
            Connection.CONNECTED -> Strings.get(lang, "status_connected")
            else -> if (active) Strings.get(lang, "status_connecting") else Strings.get(lang, "status_disconnected")
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
        // Update both the declared icon and runtime icon so cached tiles refresh.
        runCatching { tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ghajar) }
        tile.updateTile()
    }
}
