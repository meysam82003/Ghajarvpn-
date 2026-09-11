package net.gozar.app

import android.util.Base64
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the JSON config psiphon-tunnel-core expects (ca.psiphon.PsiphonTunnel).
 * Builds the same field names/shape that ca.psiphon.PsiphonTunnel expects.
 * These are Psiphon Inc's own public
 * server-list URL/signature - those are Psiphon Inc's public values, not
 * secrets). Custom CDN IP/SNI override support was left out for simplicity;
 * add it back the same way if you need fronting overrides.
 */
object PsiphonConfig {

    const val MODE_AUTO = "auto"
    const val MODE_CDN = "cdn"
    const val MODE_DIRECT = "direct"

    private val CDN_PROTOCOLS = listOf(
        "FRONTED-MEEK-CDN-OSSH",
        "FRONTED-MEEK-CDN-HTTP-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH",
    )

    private val NON_INPROXY_PROTOCOLS = listOf(
        "SSH", "OSSH", "TLS-OSSH", "UNFRONTED-MEEK-OSSH",
        "UNFRONTED-MEEK-HTTPS-OSSH", "UNFRONTED-MEEK-SESSION-TICKET-OSSH",
        "QUIC-OSSH", "SHADOWSOCKS-OSSH", "FRONTED-MEEK-OSSH",
        "FRONTED-MEEK-CDN-OSSH", "FRONTED-MEEK-HTTP-OSSH",
        "FRONTED-MEEK-CDN-HTTP-OSSH", "FRONTED-MEEK-QUIC-OSSH",
        "FRONTED-MEEK-CDN-QUIC-OSSH",
    )

    // Psiphon Inc's own public propagation/sponsor IDs and remote server list
    // location/signature - these are meant to be embedded in client builds,
    // not per-app secrets.
    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"
    private const val SERVER_LIST_URL =
        "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"
    private const val SERVER_LIST_SIGNATURE_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH" +
            "5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5" +
            "OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot" +
            "LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6" +
            "/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G" +
            "stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O" +
            "geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8" +
            "u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz" +
            "31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal" +
            "KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    fun mode(raw: String): String = when (raw.trim()) {
        MODE_CDN -> MODE_CDN
        MODE_DIRECT -> MODE_DIRECT
        else -> MODE_AUTO
    }

    /**
     * @param socksPort local SOCKS port for Xray's "psiphon" outbound to dial.
     * @param country ISO-3166 region code to prefer for egress, blank = any.
     * @param dataDirectory writable dir for Psiphon's own state/resumability.
     */
    fun build(mode: String, socksPort: Int, country: String, dataDirectory: File): String {
        val config = JSONObject()
        config.put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
        config.put("SponsorId", SPONSOR_ID)
        config.put("ClientVersion", "1")
        config.put("DataRootDirectory", dataDirectory.absolutePath)
        config.put("LocalSocksProxyPort", socksPort)
        config.put("EmitDiagnosticNotices", true)
        config.put("EmitDiagnosticNetworkParameters", true)
        config.put("EmitServerAlerts", true)

        val region = country.trim().uppercase()
        if (region.isNotEmpty()) config.put("EgressRegion", region)

        config.put(
            "RemoteServerListURLs",
            JSONArray().put(
                JSONObject().put(
                    "URL",
                    Base64.encodeToString(SERVER_LIST_URL.toByteArray(), Base64.NO_WRAP),
                ),
            ),
        )
        config.put("RemoteServerListSignaturePublicKey", SERVER_LIST_SIGNATURE_KEY)
        // This build has no ServerEntrySignaturePublicKey, so in-proxy/conduit
        // mode is not offered - keep those probabilities at zero.
        config.put("InproxyTunnelProtocolPreferProbability", 0.0)
        config.put("InproxyTunnelProtocolSelectionProbability", 0.0)

        when (mode(mode)) {
            MODE_CDN -> {
                config.put("LimitTunnelProtocols", JSONArray(CDN_PROTOCOLS))
                config.put("FrontedMeekCDNScanUseBuiltInSpec", true)
                config.put("DisableTactics", true)
            }
            MODE_DIRECT -> {
                config.put("LimitTunnelProtocols", JSONArray(NON_INPROXY_PROTOCOLS))
                config.put("DisableTactics", true)
            }
            else -> config.put("LimitTunnelProtocols", JSONArray(NON_INPROXY_PROTOCOLS))
        }

        return config.toString()
    }
}
