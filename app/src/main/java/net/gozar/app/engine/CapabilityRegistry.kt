package net.gozar.app.engine

/**
 * What each layer of the connection can really do, in one place, so the UI
 * shows only what exists. Four layers are kept apart:
 *
 * - a **core** is a program that speaks protocols (Xray, sing-box, Psiphon, ...);
 * - a **protocol** is what a profile is (vless, hysteria2, ssh, ...);
 * - a **transport / method** is how a protocol travels (ws, xhttp, REALITY, meek, ...);
 * - **TUN** is what carries the device into a core (Xray's own tun, zeptun).
 *
 * Every entry here was checked against the code that builds the config; a
 * capability that the builder cannot produce is not listed, even if the
 * upstream core has it (Hysteria 2 port hopping is the example: Xray supports
 * it, this app's builder does not write it, so it is not claimed).
 */
enum class Capability {
    TCP, UDP, IPV6, TUN, SOCKS, DNS, PORT_HOPPING, ECH, REALITY, XHTTP,
    FULL_CONFIG, SUBSCRIPTION, ADVANCED_AUTH, SHARE_THROUGH_PHONE
}

enum class LayerKind { CORE, TUN }

data class CoreDescriptor(
    val id: EngineId,
    val kind: LayerKind,
    /** Protected cores are part of the base app and are never pruned or made on-demand. */
    val protected: Boolean,
    val capabilities: Set<Capability>
)

object CapabilityRegistry {
    val CORES: List<CoreDescriptor> = listOf(
        CoreDescriptor(EngineId.XRAY, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.UDP, Capability.IPV6, Capability.TUN, Capability.SOCKS, Capability.DNS,
            Capability.ECH, Capability.REALITY, Capability.XHTTP, Capability.FULL_CONFIG, Capability.SUBSCRIPTION,
            Capability.SHARE_THROUGH_PHONE)),
        CoreDescriptor(EngineId.SINGBOX, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.UDP, Capability.IPV6, Capability.SOCKS, Capability.DNS,
            Capability.FULL_CONFIG, Capability.SUBSCRIPTION, Capability.ADVANCED_AUTH, Capability.SHARE_THROUGH_PHONE)),
        CoreDescriptor(EngineId.PSIPHON, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.SOCKS, Capability.SHARE_THROUGH_PHONE)),
        CoreDescriptor(EngineId.TOR, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.SOCKS, Capability.SHARE_THROUGH_PHONE)),
        CoreDescriptor(EngineId.AETHER, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.UDP, Capability.SOCKS, Capability.SHARE_THROUGH_PHONE)),
        CoreDescriptor(EngineId.IKEV2, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.UDP, Capability.TUN, Capability.ADVANCED_AUTH)),
        CoreDescriptor(EngineId.OPENVPN, LayerKind.CORE, true, setOf(
            Capability.TCP, Capability.UDP, Capability.TUN, Capability.FULL_CONFIG, Capability.ADVANCED_AUTH)),
        CoreDescriptor(EngineId.ZEPTUN_TUN, LayerKind.TUN, true, setOf(
            Capability.TCP, Capability.UDP, Capability.IPV6, Capability.TUN))
    )

    fun core(id: EngineId): CoreDescriptor = CORES.first { it.id == id }

    /** What a profile of [protocol] can do here; empty for a protocol whose engine is not in this build. */
    fun forProtocol(protocol: String): Set<Capability> {
        val p = protocol.trim().lowercase()
        if (RemovedCores.isRemoved(p)) return emptySet()
        val base = mutableSetOf(Capability.TCP, Capability.SUBSCRIPTION)
        when (p) {
            "vless" -> base += setOf(Capability.UDP, Capability.IPV6, Capability.REALITY, Capability.XHTTP, Capability.ECH, Capability.SHARE_THROUGH_PHONE)
            "vmess", "trojan" -> base += setOf(Capability.UDP, Capability.IPV6, Capability.XHTTP, Capability.ECH, Capability.SHARE_THROUGH_PHONE)
            "ss", "shadowsocks", "socks", "http" -> base += setOf(Capability.UDP, Capability.IPV6, Capability.SHARE_THROUGH_PHONE)
            "hysteria2", "tuic", "hysteria" -> base += setOf(Capability.UDP, Capability.IPV6, Capability.SHARE_THROUGH_PHONE)
            "wireguard", "amneziawg" -> base += setOf(Capability.UDP, Capability.IPV6, Capability.FULL_CONFIG, Capability.SHARE_THROUGH_PHONE)
            "ssh", "naive", "anytls", "shadowtls", "snell", "mieru", "brook", "masque" ->
                base += setOf(Capability.UDP, Capability.SHARE_THROUGH_PHONE)
            "openconnect", "sstp", "softether" -> base += setOf(Capability.UDP, Capability.ADVANCED_AUTH, Capability.SHARE_THROUGH_PHONE)
            "tailscale", "tailcat" -> base += setOf(Capability.UDP, Capability.ADVANCED_AUTH)
            SingBoxFull.PROTOCOL -> base += setOf(Capability.UDP, Capability.DNS, Capability.FULL_CONFIG, Capability.SHARE_THROUGH_PHONE)
            "openvpn" -> { base -= Capability.SUBSCRIPTION; base += setOf(Capability.UDP, Capability.TUN, Capability.FULL_CONFIG, Capability.ADVANCED_AUTH) }
            "ikev2" -> { base -= Capability.SUBSCRIPTION; base += setOf(Capability.UDP, Capability.TUN, Capability.ADVANCED_AUTH) }
            "psiphon", "tor", "aether" -> { base -= Capability.SUBSCRIPTION; base += Capability.SHARE_THROUGH_PHONE }
        }
        return base
    }

    fun has(protocol: String, capability: Capability): Boolean = capability in forProtocol(protocol)
}
