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
        "http" to socket,
        "openconnect" to socket.copy(supportsUdp = true, supportsDns = true, supportsAdvancedAuth = true),
        "masque" to socket.copy(supportsUdp = true, supportsMasque = true),
        "anytls" to socket.copy(supportsEch = true),
        "ssh" to socket,
        "tuic" to socket.copy(supportsUdp = true, supportsEch = true),
        "hysteria2" to socket.copy(supportsUdp = true, supportsPortHopping = true, supportsEch = true),
        "hysteria" to socket.copy(supportsUdp = true)
    )
    fun forConfig(config: ProxyConfig): CapabilityContract {
        if (FullSingBoxProfile.isFull(config)) return CapabilityContract() // Full text alone proves no runtime capability.
        if (net.gozar.app.AetherTorPolicy.active(config.oblivionJson)) return socket.copy(supportsDns = true)
        if (PluginProfiles.isPlugin(config)) return CapabilityContract() // UI may show only an approved installed release's claims.
        return protocolOverrides[config.protocol] ?: cores.getValue(EngineRouting.engineFor(config)).capabilities.let { c ->
            c.copy(supportsReality = c.supportsReality && config.protocol == "vless",
                supportsXhttp = c.supportsXhttp && config.protocol in setOf("vless", "vmess", "trojan"))
        }
    }
    /** Eligibility only. Session owner publishes a probed, generation-bound backend. */
    fun supportsPhoneSharing(config: ProxyConfig): Boolean = phoneSharingReason(config) == null
    fun phoneSharingReason(config: ProxyConfig): String? = when (EngineRouting.engineFor(config)) {
        EngineId.SINGBOX -> if (FullSingBoxProfile.isFull(config)) "Full Config ممکن است DIRECT داشته باشد؛ قرارداد اشتراک فقط از تونل ندارد." else if (config.protocol == "tailscale") "Tailscale ممکن است بدون exit node به شبکه مقصد route کند؛ Direct Share را به کلاینت همان شبکه بدهید." else null
        EngineId.PSIPHON -> null // no split-tunnel setting in PsiphonConfig
        EngineId.AETHER -> if (runCatching { net.gozar.app.OblivionOptions(config.oblivionJson).text("routeDirect").isBlank() }.getOrDefault(false)) null
            else "Aether دارای route-direct است؛ برای اشتراک عبوری از تونل آن را صریحاً حذف کنید یا Direct Share بدهید."
        EngineId.TOR -> null
        EngineId.XRAY -> if (config.protocol in setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2")) null
            else "این مسیر backend با DNS تضمین‌شده داخل تونل ندارد؛ WireGuard را با فایل conf و بقیه را با Direct Share منتقل کنید."
        EngineId.OPENVPN -> "OpenVPN مالک TUN مستقل است و SOCKS عبوری از همان نشست ندارد؛ فایل ovpn را با Direct Share منتقل کنید."
        EngineId.IKEV2 -> "strongSwan مالک TUN مستقل است؛ relay این UID از VPN مستثناست و dial مستقیم امن نیست. از IKEv2 Direct Share استفاده کنید."
        EngineId.PLUGIN -> "API 1 فقط SOCKS عمومی یا TUN را اعلام می‌کند؛ full-config ممکن است DIRECT داشته باشد. برای relay قرارداد tunnel-only لازم است؛ فعلاً Direct Share."
        else -> "برای این موتور از Direct Share استفاده کنید."
    }
    fun settingsFor(protocol: String) = net.gozar.app.EngineSettings.supported(protocol)
    fun candidate(id: String) = PluginCatalog.candidate(id)?.supported ?: CapabilityContract()
}
