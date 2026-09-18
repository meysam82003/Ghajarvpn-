package net.gozar.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/** What happened when something outside the UI asked for a connection. */
enum class QuickConnectResult {
    STARTED,

    /** No server exists yet; the user has to add one in the app. */
    NO_CONFIG,

    /** Android's VPN consent has never been given, which needs an Activity. */
    NEEDS_CONSENT,

    /** The service start itself was refused. The error is already in the log. */
    FAILED
}

/**
 * Connecting and disconnecting from a surface that has no UI - the quick
 * settings tile and the home-screen widget.
 *
 * This is the tile's own implementation, lifted rather than copied: it is the
 * one connect path that already handled every engine (IKEv2 through
 * IkeController, OpenVPN through its bridge on the way down, mux, a Tor or
 * chain base config, the connect coordinator's timeouts). A widget with its
 * own second version of it would have started drifting from the tile's on the
 * first change to either.
 *
 * Distinct from [VpnLauncher], which replaces a live tunnel with a specific
 * config for the auto-selector and the network rules. This one starts the
 * user's current selection from scratch.
 */
object QuickConnect {

    fun start(context: Context): QuickConnectResult {
        val store = ConfigStore.get(context.applicationContext)
        val selectedId = store.selectedId.value
        val config = store.configs.value.firstOrNull { it.id == selectedId }
            ?: store.configs.value.firstOrNull()
            ?: return QuickConnectResult.NO_CONFIG
        if (config.id != selectedId) store.setSelectedId(config.id)
        if (VpnService.prepare(context) != null) return QuickConnectResult.NEEDS_CONSENT

        if (config.protocol == "ikev2") {
            IkeController.claim(config)
            if (!IkeController.connect(context, config)) {
                VpnState.setError("اتصال IKEv2 انجام نشد")
                return QuickConnectResult.FAILED
            }
            return QuickConnectResult.STARTED
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
            customDns = store.customDns.value,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value
        )
        VpnCommandCoordinator.onConnectRequested(
            config.id,
            when (config.protocol) {
                "psiphon" -> 290_000L
                "aether" -> 200_000L
                else -> 45_000L
            }
        ) { VpnState.setConnecting(config.id) }
        val intent = Intent(context, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_CONFIG, json)
            .putExtra(GozarVpnService.EXTRA_AETHER, AetherController.spec(config))
            .putExtra(GozarVpnService.EXTRA_PSIPHON, PsiphonSpec.from(config)?.toJson())
            .putExtra(
                GozarVpnService.EXTRA_TOR,
                if (config.protocol == "tor")
                    config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0")
                else if (store.onionRouting.value) "|1" else null
            )
            .putExtra(GozarVpnService.EXTRA_NAME, config.name)
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
            .putExtra(GozarVpnService.EXTRA_ADDRESS, config.address)
            .putExtra(GozarVpnService.EXTRA_PORT, config.port)
        return runCatching {
            ContextCompat.startForegroundService(context, intent)
            QuickConnectResult.STARTED
        }.getOrElse {
            GhajarLog.e(TAG, "service start refused: ${it.javaClass.simpleName}")
            VpnState.setError("شروع سرویس VPN ناموفق بود")
            QuickConnectResult.FAILED
        }
    }

    /**
     * Ends whatever is up, through whichever engine owns it.
     *
     * [onOpenVpn] is called instead of a direct disconnect when OpenVPN owns
     * the session, because that call is suspending and each caller has its own
     * scope to run it in.
     */
    fun stop(context: Context, onOpenVpn: () -> Unit) {
        VpnCommandCoordinator.onDisconnectRequested {
            if (IkeController.active) {
                IkeController.disconnect(context)
                return@onDisconnectRequested
            }
            if (VpnState.activeId.value.orEmpty().startsWith("ovpn:") ||
                GhajarOpenVpnBridge.status.value != GhajarOvpnState.DISCONNECTED
            ) {
                onOpenVpn()
                return@onDisconnectRequested
            }
            runCatching {
                context.startService(
                    Intent(context, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP)
                )
            }
        }
    }

    private const val TAG = "GhajarQuick"
}
