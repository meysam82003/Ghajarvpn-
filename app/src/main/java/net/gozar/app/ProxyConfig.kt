package net.gozar.app

import org.json.JSONObject
import java.util.UUID

enum class ConfigSource { PERSONAL, COMMUNITY, PREMIUM }

data class ProxyConfig(
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val uuid: String = "",
    val password: String = "",
    val method: String = "",
    val alterId: Int = 0,
    val encryption: String = "none",
    val flow: String = "",
    val network: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val fingerprint: String = "chrome",
    val path: String = "",
    val host: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val alpn: String = "",
    val headerType: String = "",
    val subId: String = "",
    val privateKey: String = "",
    val localAddress: String = "",
    val mtu: Int = 0,
    val reserved: String = "",
    val aetherMode: String = "masque",
    val aetherScan: String = "balanced",
    val aetherNoise: String = "",
    val aetherHttp2: Boolean = true,
    val aetherIpv6: Boolean = false,
    val hyObfs: String = "",
    val hyObfsPassword: String = "",
    val hyUpMbps: Int = 0,
    val hyDownMbps: Int = 0,
    val allowInsecure: Boolean = false,
    val pinnedCertSha256: String = "",
    /**
     * TLS cipher suites to offer, comma separated, or blank for the core's own.
     *
     * From PattNG: some networks fingerprint a client by the exact suite list
     * in its handshake, and being able to change it is the whole point.
     * Blank - the default on every existing config - emits no cipherSuites
     * field at all, so a config that never set it behaves exactly as before.
     */
    val cipherSuites: String = "",
    /**
     * Prefix a random label to the SNI and Host on every connect.
     *
     * MahsaNG calls this a random subdomain: a server behind a wildcard
     * certificate accepts any label, and varying it stops the exact same
     * hostname appearing in every handshake. Off by default, because a server
     * without a wildcard certificate will reject it.
     */
    val randomSubdomain: Boolean = false,
    /**
     * An extra disguise applied to the transport, below everything else.
     *
     * Xray calls this layer finalmask and it is the newest thing in the core
     * this app bundles. Blank - every existing config - emits no finalmask
     * field at all. The values this app offers:
     *
     * - `xdns`   the connection is carried inside DNS queries and responses
     *            for [maskDomain]. This is the "connect over DNS" method: to
     *            anything watching, the traffic is a device resolving names.
     *            UDP-based transports only, and the server must run the
     *            matching xdns mask for the same domain.
     * - `noise`  junk datagrams ahead of the real ones, on a UDP transport.
     *            Client-side only, so it needs nothing of the server beyond
     *            ignoring what it cannot parse.
     * - `sudoku` a table-driven byte permutation, keyed by [maskPassword].
     *            Both ends must share the password.
     * - `salamander` Hysteria 2's own obfuscation, keyed by [maskPassword].
     *
     * Every one of these except `noise` needs the server configured to match.
     * With a server that is not, the connection does not degrade - it fails,
     * which is the honest outcome for a disguise only one end is wearing.
     */
    val maskType: String = "",
    /** The domain queries are addressed to, for the `xdns` mask. */
    val maskDomain: String = "",
    /** The shared secret for the `sudoku` and `salamander` masks. */
    val maskPassword: String = "",
    /**
     * Encrypted Client Hello, as a base64 ECHConfigList or a DoH query.
     *
     * This is the one option here that hides the server name itself rather
     * than changing how it looks. Every other TLS setting still sends the SNI
     * in the clear; with ECH the real name is encrypted to the server's public
     * key and the handshake carries only a cover name.
     *
     * Two accepted forms, both from the core's own ech.go:
     *  - a base64 ECHConfigList, as published in the domain's HTTPS record
     *  - `example.com+https://1.1.1.1/dns-query`, or just the resolver URL
     *    when serverName already names the domain, to look the record up
     *
     * When the lookup fails the core deliberately fails the connection rather
     * than retrying without ECH. That is the point: a silent fallback would
     * put the real server name back on the wire at the worst moment.
     */
    val echConfigList: String = "",
    val torCountry: String = "",
    val torThroughVpn: Boolean = false,
    val torBaseId: String = "",
    val chainId: String = "",
    val psiphonMode: String = "auto",
    val psiphonCountry: String = "",
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val oblivionJson: String = "",
    val source: ConfigSource = ConfigSource.PERSONAL,
    val locked: Boolean = false,
    val favorite: Boolean = false,
    val id: String = UUID.randomUUID().toString()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("protocol", protocol)
        .put("address", address).put("port", port).put("uuid", uuid)
        .put("password", password).put("method", method).put("alterId", alterId)
        .put("encryption", encryption).put("flow", flow).put("network", network)
        .put("security", security).put("sni", sni).put("publicKey", publicKey)
        .put("shortId", shortId).put("fingerprint", fingerprint)
        .put("path", path).put("host", host)
        .put("serviceName", serviceName).put("mode", mode).put("alpn", alpn).put("source", source.name)
        .put("headerType", headerType)
        .put("subId", subId)
        .put("privateKey", privateKey).put("localAddress", localAddress)
        .put("mtu", mtu).put("reserved", reserved).put("locked", locked).put("favorite", favorite)
        .put("aetherMode", aetherMode).put("aetherScan", aetherScan)
        .put("aetherNoise", aetherNoise).put("aetherHttp2", aetherHttp2)
        .put("aetherIpv6", aetherIpv6).put("oblivionJson", oblivionJson)
        .put("hyObfs", hyObfs).put("hyObfsPassword", hyObfsPassword)
        .put("hyUpMbps", hyUpMbps).put("hyDownMbps", hyDownMbps)
        .put("allowInsecure", allowInsecure)
        .put("pinnedCertSha256", pinnedCertSha256)
        .put("cipherSuites", cipherSuites)
        .put("randomSubdomain", randomSubdomain)
        .put("maskType", maskType).put("maskDomain", maskDomain)
        .put("maskPassword", maskPassword).put("echConfigList", echConfigList)
        .put("torCountry", torCountry).put("torThroughVpn", torThroughVpn)
        .put("torBaseId", torBaseId).put("chainId", chainId)
        .put("psiphonMode", psiphonMode).put("psiphonCountry", psiphonCountry)
        .put("psiphonCdnIps", psiphonCdnIps).put("psiphonCdnSni", psiphonCdnSni)

    companion object {
        fun fromJson(o: JSONObject) = ProxyConfig(
            name = o.optString("name"),
            protocol = o.optString("protocol"),
            address = o.optString("address"),
            port = o.optInt("port"),
            uuid = o.optString("uuid", ""),
            password = o.optString("password", ""),
            method = o.optString("method", ""),
            alterId = o.optInt("alterId", 0),
            encryption = o.optString("encryption", "none"),
            flow = o.optString("flow", ""),
            network = o.optString("network", "tcp"),
            security = o.optString("security", "none"),
            sni = o.optString("sni", ""),
            publicKey = o.optString("publicKey", ""),
            shortId = o.optString("shortId", ""),
            fingerprint = o.optString("fingerprint", "chrome"),
            path = o.optString("path", ""),
            host = o.optString("host", ""),
            serviceName = o.optString("serviceName", ""),
            mode = o.optString("mode", ""),
            alpn = o.optString("alpn", ""),
            headerType = o.optString("headerType", ""),
            subId = o.optString("subId", ""),
            privateKey = o.optString("privateKey", ""),
            localAddress = o.optString("localAddress", ""),
            mtu = o.optInt("mtu", 0),
            reserved = o.optString("reserved", ""),
            aetherMode = o.optString("aetherMode", "masque"),
            aetherScan = o.optString("aetherScan", "balanced"),
            aetherNoise = o.optString("aetherNoise", ""),
            aetherHttp2 = o.optBoolean("aetherHttp2", false),
            aetherIpv6 = o.optBoolean("aetherIpv6", false),
            oblivionJson = o.optString("oblivionJson", ""),
            hyObfs = o.optString("hyObfs", ""),
            hyObfsPassword = o.optString("hyObfsPassword", ""),
            hyUpMbps = o.optInt("hyUpMbps", 0),
            hyDownMbps = o.optInt("hyDownMbps", 0),
            allowInsecure = o.optBoolean("allowInsecure", false),
            pinnedCertSha256 = o.optString("pinnedCertSha256", ""),
            cipherSuites = o.optString("cipherSuites", ""),
            randomSubdomain = o.optBoolean("randomSubdomain", false),
            maskType = o.optString("maskType", ""),
            maskDomain = o.optString("maskDomain", ""),
            maskPassword = o.optString("maskPassword", ""),
            echConfigList = o.optString("echConfigList", ""),
            torCountry = o.optString("torCountry", ""),
            torThroughVpn = o.optBoolean("torThroughVpn", false),
            torBaseId = o.optString("torBaseId", ""),
            chainId = o.optString("chainId", ""),
            psiphonMode = o.optString("psiphonMode", "auto"),
            psiphonCountry = o.optString("psiphonCountry", ""),
            psiphonCdnIps = o.optString("psiphonCdnIps", ""),
            psiphonCdnSni = o.optString("psiphonCdnSni", ""),
            source = runCatching { ConfigSource.valueOf(o.optString("source", "PERSONAL")) }.getOrDefault(ConfigSource.PERSONAL),
            locked = o.optBoolean("locked", false),
            favorite = o.optBoolean("favorite", false),
            id = o.optString("id", UUID.randomUUID().toString())
        )
    }
}