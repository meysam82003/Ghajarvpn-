package net.gozar.app.engine

import net.gozar.app.ProxyConfig
import net.gozar.app.plugins.CapabilityContract
import net.gozar.app.plugins.ComponentKind
import net.gozar.app.plugins.PluginCatalog
import net.gozar.app.plugins.PluginProfiles

/** Source-level support, not runtime readiness. Unknown/unwired options stay false. */
data class CoreContract(val kind: ComponentKind, val capabilities: CapabilityContract, val evidence: String)
object CapabilityRegistry {
    private val socket = CapabilityContract(supportsTcp = true, supportsSocks = true)
    val cores: Map<EngineId, CoreContract> = mapOf(
        EngineId.XRAY to CoreContract(ComponentKind.CORE, socket.copy(supportsUdp = true, supportsIPv6 = true,
            supportsTun = true, supportsDns = true, supportsEch = true, supportsReality = true, supportsXhttp = true), "ConfigBuilder; Xray v26.3.27"),
        EngineId.PSIPHON to CoreContract(ComponentKind.CORE, socket, "PsiphonConfig; current non-INPROXY modes"),
        EngineId.OPENVPN to CoreContract(ComponentKind.CORE, CapabilityContract(supportsTcp = true, supportsUdp = true,
            supportsIPv6 = true, supportsTun = true, supportsDns = true, supportsAdvancedAuth = true), "Dedicated openvpn module"),
        EngineId.IKEV2 to CoreContract(ComponentKind.CORE, CapabilityContract(supportsTcp = true, supportsUdp = true,
            supportsIPv6 = true, supportsTun = true, supportsDns = true, supportsAdvancedAuth = true), "strongSwan/IkeController"),
        EngineId.TOR to CoreContract(ComponentKind.CORE, socket, "Torcontroller/lyrebird; no general UDP"),
        EngineId.AETHER to CoreContract(ComponentKind.CORE, socket.copy(supportsUdp = true, supportsIPv6 = true,
            supportsMasque = true), "Aether 21e7150a; Tor feature not compiled"),
        EngineId.SINGBOX to CoreContract(ComponentKind.CORE, socket.copy(supportsUdp = true, supportsDns = true), "SingBoxConfig alpha.9; per-protocol options below"),
        EngineId.DNS_TUNNEL to CoreContract(ComponentKind.METHOD, socket, "DNS sidecars through SOCKS"),
        EngineId.ZEPTUN_TUN to CoreContract(ComponentKind.TUN_ENGINE, CapabilityContract(supportsTcp = true,
            supportsUdp = true, supportsIPv6 = true, supportsTun = true, supportsDns = true), "zeptun 1.1.1; ICMP is not a proxy protocol"),
        EngineId.PLUGIN to CoreContract(ComponentKind.PLUGIN, CapabilityContract(), "Approved signed release capabilities required")
    )
    private val protocolOverrides = mapOf(
        "openconnect" to socket.copy(supportsUdp = true, supportsDns = true, supportsAdvancedAuth = true),
        "masque" to socket.copy(supportsUdp = true, supportsMasque = true),
        "anytls" to socket.copy(supportsEch = true),
        "ssh" to socket,
        "tuic" to socket.copy(supportsUdp = true, supportsEch = true),
        "hysteria2" to socket.copy(supportsUdp = true, supportsPortHopping = true, supportsEch = true),
        "hysteria" to socket.copy(supportsUdp = true)
    )
    fun forConfig(config: ProxyConfig): CapabilityContract {
        if (PluginProfiles.isPlugin(config)) return CapabilityContract() // UI may show only an approved installed release's claims.
        return protocolOverrides[config.protocol] ?: cores.getValue(EngineRouting.engineFor(config)).capabilities.let { c ->
            c.copy(supportsReality = c.supportsReality && config.protocol == "vless",
                supportsXhttp = c.supportsXhttp && config.protocol in setOf("vless", "vmess", "trojan"))
        }
    }
    fun settingsFor(protocol: String) = net.gozar.app.EngineSettings.supported(protocol)
    fun candidate(id: String) = PluginCatalog.candidate(id)?.supported ?: CapabilityContract()
}
