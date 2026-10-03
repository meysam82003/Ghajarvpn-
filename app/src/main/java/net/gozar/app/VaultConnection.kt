package net.gozar.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import net.gozar.app.security.vault.VaultRuntime

/** Same host builder/engine preparation, with only an opaque reference crossing the Intent. */
object VaultConnection {
    fun start(context: Context, store: ConfigStore, config: ProxyConfig) {
        VaultRuntime.check(config)
        require(!store.onionRouting.value && !store.splitRouting.value && !store.youtubeDirect.value) {
            "اتصال صندوق با مسیر مستقیم/تفکیکی یا Tor سراسری ترکیب نشده است؛ این تنظیمات را صریحاً خاموش کنید."
        }
        require(!store.vpnShareEnabled.value) { "اشتراک گوشی برای نشست صندوق هنوز حسابداری مستقل ندارد؛ ابتدا آن را خاموش کنید." }
        net.gozar.app.engine.CoreManager.prepare(context, config)?.let { error(it) }
        val json = ConfigBuilder.build(config, store.fragment.value, store.splitRouting.value, store.sniffing.value, store.sniffTypes.value, mux = store.mux.value, muxConcurrency = store.muxConcurrency.value, adBlock = store.adBlock.value, fakeDns = store.fakeDns.value,
            encryptedDns = store.encryptedDns.value,
            customDns = store.customDns.value,
            youtubeDirect = store.youtubeDirect.value,
            noiseSpec = store.noiseSpec.value,
            fragmentPackets = store.fragmentPackets.value,
            fragmentLength = store.fragmentLength.value,
            fragmentInterval = store.fragmentInterval.value,
            torBase = if (config.protocol == "tor" && config.torBaseId.isNotEmpty())
                store.configs.value.find { it.id == config.torBaseId } else null,
            chainBase = if (config.chainId.isNotEmpty())
                store.configs.value.find { it.id == config.chainId } else null,
            onionRouting = store.onionRouting.value,
            coreLogLevel = store.coreLogLevel.value,
            shareOnLan = store.vpnShareEnabled.value,
            shareUser = if (store.vpnShareEnabled.value) store.ensureVpnShareCredential().first else "",
            sharePass = store.vpnSharePassword.value,
            shareListenAddress = "127.0.0.1")
        VaultRuntime.prepare(config, VaultRuntime.Prepared(json, net.gozar.app.engine.SingBoxConfig.spec(config)))
        VpnState.setConnecting(config.id)
        ContextCompat.startForegroundService(context, Intent(context, GozarVpnService::class.java)
            .putExtra(GozarVpnService.EXTRA_VAULT_REF, config.id)
            .putExtra(GozarVpnService.EXTRA_NAME, "صندوق امن")
            .putExtra(GozarVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect")))
    }
}
