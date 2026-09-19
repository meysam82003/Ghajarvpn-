package net.gozar.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Why a background relaunch did not happen. */
enum class LaunchOutcome {
    STARTED,

    /** Android has not been granted VPN permission, or it was revoked. */
    NO_PERMISSION,

    /**
     * The process was not allowed to start a foreground service.
     *
     * On Android 12+ a foreground service cannot be started from the
     * background, and a network change is not one of the exemptions. This is
     * reported rather than swallowed, because "it silently did not connect" is
     * the single worst thing an auto-connect feature can do.
     */
    REFUSED
}

/**
 * Starting the tunnel from outside the UI.
 *
 * This was AutoSelector's private reconnect, which is the only place in the app
 * that had ever built the service intent without an Activity. Both the
 * auto-selector and the per-network rules need exactly that, and two copies of
 * a connect path is how one of them quietly stops matching the other.
 */
object VpnLauncher {

    /**
     * Drops whatever tunnel is up and starts [config].
     *
     * The stop-and-wait is unconditional because GozarVpnService ignores a
     * start while it is already running (see its onStartCommand), so replacing
     * a tunnel means ending the old one first. When nothing is running the wait
     * returns immediately.
     */
    suspend fun relaunch(appContext: Context, store: ConfigStore, config: ProxyConfig): LaunchOutcome {
        if (VpnService.prepare(appContext) != null) return LaunchOutcome.NO_PERMISSION

        runCatching {
            appContext.startService(
                Intent(appContext, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP)
            )
        }
        withTimeoutOrNull(6000) {
            VpnState.state.first { it == Connection.DISCONNECTED || it == Connection.ERROR }
        }
        delay(400)

        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value,
            adBlock = store.adBlock.value,
            fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            customDns = store.customDns.value,
            youtubeDirect = store.youtubeDirect.value,
            noiseSpec = store.noiseSpec.value,
            fragmentPackets = store.fragmentPackets.value,
            fragmentLength = store.fragmentLength.value,
            fragmentInterval = store.fragmentInterval.value,
            onionRouting = store.onionRouting.value
        )
        VpnState.setConnecting(config.id)
        val intent = Intent(appContext, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_CONFIG, json)
            .putExtra(GozarVpnService.EXTRA_AETHER, AetherSpec.from(config)?.toJson())
            .putExtra(
                GozarVpnService.EXTRA_TOR,
                if (config.protocol == "tor")
                    config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") else null
            )
            .putExtra(GozarVpnService.EXTRA_NAME, config.name)
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
            .putExtra(GozarVpnService.EXTRA_ADDRESS, config.address)
            .putExtra(GozarVpnService.EXTRA_PORT, config.port)
        return runCatching {
            ContextCompat.startForegroundService(appContext, intent)
            LaunchOutcome.STARTED
        }.getOrElse { failure ->
            // The exception class is the whole diagnosis here, and it is not a
            // secret: ForegroundServiceStartNotAllowedException means the
            // background restriction, SecurityException means the process was
            // flagged bad.
            GhajarLog.e(TAG, "background start refused: ${failure.javaClass.simpleName}")
            VpnState.setDisconnected()
            LaunchOutcome.REFUSED
        }
    }

    private const val TAG = "GhajarLaunch"
}
