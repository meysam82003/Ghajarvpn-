package net.gozar.app.sharing

import net.gozar.app.OblivionOptions
import net.gozar.app.ProxyConfig
import net.gozar.app.engine.EngineId
import net.gozar.app.engine.EngineRouting
import net.gozar.app.engine.RemovedCores

/**
 * Per-core decision for "share through this phone". The shared listeners are
 * Xray inbounds (ConfigBuilder: authenticated socks-in / http-share-in), so
 * sharing works exactly when Xray carries the session: Xray profiles, and
 * Psiphon / Tor / Aether, which Xray reaches through their local SOCKS port.
 * sing-box sessions are carried into Xray's tun through sing-box's local
 * SOCKS too, so they share as well. OpenVPN and IKEv2 run without Xray, and a proxy-only
 * Aether/Psiphon session carried by zeptun has no Xray either - none of those
 * is offered, rather than showing an address that never answers.
 */
object PhoneShare {
    fun supports(config: ProxyConfig): Boolean {
        if (RemovedCores.isRemoved(config)) return false
        val engine = EngineRouting.engineFor(config)
        if (engine !in setOf(EngineId.XRAY, EngineId.PSIPHON, EngineId.TOR, EngineId.AETHER, EngineId.SINGBOX)) return false
        return runCatching { !OblivionOptions(config.oblivionJson).proxyOnly }.getOrDefault(true)
    }

    fun unsupportedReason(config: ProxyConfig?): String = when {
        config == null -> "اتصالی فعال نیست"
        RemovedCores.isRemoved(config) -> RemovedCores.MESSAGE
        else -> when (EngineRouting.engineFor(config)) {
            EngineId.OPENVPN, EngineId.IKEV2 -> "اتصال فعلی OpenVPN یا IKEv2 است و درگاه اشتراک ندارد"
            else -> "این اتصال در حالت فقط-پراکسی است"
        }
    }
}
