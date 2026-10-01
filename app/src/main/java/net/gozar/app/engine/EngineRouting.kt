package net.gozar.app.engine

enum class EngineId { XRAY, PSIPHON, OPENVPN, IKEV2, TOR, AETHER, ZEPTUN_TUN, DNS_TUNNEL, SINGBOX, PLUGIN }

/**
 * Which core carries a config when it is connected. This is the same
 * decision the launch sites make (MainActivity, QuickConnect, VpnLauncher
 * and GozarVpnService.switchTunnel), kept in one place for the UI filters
 * and the engine test. Free of Android and native references so it is
 * unit-testable.
 */
object EngineRouting {
    fun engineFor(config: net.gozar.app.ProxyConfig): EngineId = when {
        net.gozar.app.plugins.PluginProfiles.isPlugin(config) -> EngineId.PLUGIN
        config.protocol == "ikev2" -> EngineId.IKEV2
        config.protocol == "openvpn" -> EngineId.OPENVPN
        SingBoxConfig.handles(config) -> EngineId.SINGBOX
        config.protocol == "aether" -> EngineId.AETHER
        config.protocol == "psiphon" && runCatching { net.gozar.app.OblivionOptions(config.oblivionJson).aether }.getOrDefault(false) -> EngineId.AETHER
        config.protocol == "psiphon" -> EngineId.PSIPHON
        config.protocol == "tor" -> EngineId.TOR
        else -> EngineId.XRAY
    }
}
