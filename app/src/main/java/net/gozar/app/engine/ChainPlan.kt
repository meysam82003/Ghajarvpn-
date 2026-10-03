package net.gozar.app.engine

import net.gozar.app.ProxyConfig
import org.json.JSONObject

/** Network-facing carrier first, application-facing exit second. No engine startup is implied by metadata. */
data class ChainPlan(val carrierId: String, val exitId: String, val carrierEngine: EngineId,
    val exitEngine: EngineId, val carrierRelaysUdp: Boolean, val exitRequiresUdp: Boolean) {
    fun toJson() = JSONObject().put("version", 1).put("carrierId", carrierId).put("exitId", exitId)
    companion object {
        private val xrayDialers = setOf("vless", "vmess", "trojan", "shadowsocks", "http", "socks")
        fun validate(exit: ProxyConfig, carrier: ProxyConfig?): ChainPlan? {
            if (carrier == null) {
                require(exit.chainId.isBlank()) { "پروفایل Carrier زنجیره پیدا نشد؛ اتصال مستقیم جایگزین نمی‌شود" }
                return null
            }
            require(exit.id != carrier.id) { "زنجیره به خودش متصل نمی‌شود" }
            require(exit.chainId.isBlank() || exit.chainId == carrier.id) { "Carrier زنجیره با پروفایل ذخیره‌شده مطابقت ندارد" }
            require(carrier.chainId.isBlank()) { "زنجیرهٔ تو در تو یا حلقوی در این مسیر پشتیبانی نمی‌شود" }
            val ce = EngineRouting.engineFor(carrier); val ee = EngineRouting.engineFor(exit)
            val udp = CapabilityRegistry.forConfig(carrier).supportsUdp && carrier.protocol != "http"
            val needsUdp = exit.protocol in setOf("wireguard", "amneziawg", "hysteria", "hysteria2", "tuic") || exit.network.lowercase() in setOf("kcp", "mkcp", "quic")
            require(!needsUdp || udp) { "Exit به UDP نیاز دارد ولی Carrier فقط TCP عبور می‌دهد" }
            require(ce == EngineId.XRAY && ee == EngineId.XRAY && carrier.protocol in (xrayDialers + setOf("wireguard", "hysteria2")) && exit.protocol in xrayDialers) {
                "این ترکیب موتور مسیر dialerProxy تأییدشده ندارد؛ گزینهٔ زنجیرهٔ Aether/Tor مسیر مستقل دارد"
            }
            return ChainPlan(carrier.id, exit.id, ce, ee, udp, needsUdp)
        }
        fun resolve(exit: ProxyConfig, profiles: List<ProxyConfig>): ChainPlan? =
            validate(exit, profiles.firstOrNull { it.id == exit.chainId })
    }
}
