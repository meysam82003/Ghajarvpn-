package net.gozar.app

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object TorLog {
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun emit(line: String) {
        runCatching { sink?.invoke("[Tor] " + line) }
    }
}

object TorController {

    const val SOCKS_PORT = 9150
    const val CONTROL_PORT = 9151
    const val BRIDGE_PORT = 10627

    private const val TAG = "Tor"
    private const val READY_TIMEOUT_MS = 120_000L

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopping = false

    @Volatile
    private var bootstrapped = false

    @Volatile
    var bootstrapPercent = 0
        private set

    val Countries = listOf(
        "" to "Automatic",
        "us" to "United States",
        "de" to "Germany",
        "nl" to "Netherlands",
        "fr" to "France",
        "gb" to "United Kingdom",
        "se" to "Sweden",
        "ch" to "Switzerland",
        "fi" to "Finland",
        "ro" to "Romania",
        "at" to "Austria",
        "ca" to "Canada",
        "jp" to "Japan",
        "sg" to "Singapore",
        "au" to "Australia",
        "in" to "India",
        "br" to "Brazil",
        "id" to "Indonesia",
        "th" to "Thailand",
        "ua" to "Ukraine",
        "es" to "Spain",
        "it" to "Italy",
        "pl" to "Poland",
        "cz" to "Czechia",
        "no" to "Norway",
        "dk" to "Denmark",
        "be" to "Belgium",
        "ie" to "Ireland",
        "tr" to "Turkey",
        "za" to "South Africa",
        "ru" to "Russia",
        "kr" to "South Korea",
        "az" to "Azerbaijan",
        "mx" to "Mexico",
        "cn" to "China",
        "eg" to "Egypt",
        "il" to "Israel"
    )

    fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libtor.so")

    /** lyrebird (obfs4, meek_lite, webtunnel, snowflake), built in CI by scripts/build-tor-pt.sh. */
    fun ptBinary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "liblyrebird.so")

    /**
     * The service extra for a Tor profile: "country|viaVpn|transport|base64(bridge lines)".
     * The last two fields are empty for a direct Tor connection.
     */
    fun spec(config: ProxyConfig): String {
        val b = TorBridges.from(config)
        val lines = java.util.Base64.getEncoder().encodeToString(b.lines.joinToString("\n").toByteArray())
        return config.torCountry + "|" + (if (config.torThroughVpn) "1" else "0") + "|" + b.transport + "|" + lines
    }

    fun available(context: Context): Boolean = binary(context).exists()

    fun dataDir(context: Context): File = File(context.filesDir, "tor").apply { mkdirs() }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun geoFiles(context: Context): Pair<File, File> {
        val dir = dataDir(context)
        val geo = File(dir, "geoip")
        val geo6 = File(dir, "geoip6")
        listOf("geoip" to geo, "geoip6" to geo6).forEach { (name, out) ->
            if (!out.exists() || out.length() == 0L) {
                runCatching {
                    context.assets.open(name).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        return geo to geo6
    }

    private fun writeTorrc(context: Context, exitCountry: String, throughVpn: Boolean, bridges: TorBridges = TorBridges.NONE, socksPort: Int = SOCKS_PORT, upstreamPort: Int = BRIDGE_PORT): File {
        val dir = dataDir(context)
        val (geo, geo6) = geoFiles(context)
        val sb = StringBuilder()
        sb.appendLine("SocksPort 127.0.0.1:" + socksPort)
        sb.appendLine("ControlPort 127.0.0.1:" + if (socksPort == SOCKS_PORT) CONTROL_PORT else AetherTorPolicy.CONTROL_PORT)
        sb.appendLine("CookieAuthentication 1")
        sb.appendLine("DataDirectory " + dir.absolutePath)
        sb.appendLine("CacheDirectory " + File(dir, "cache").absolutePath)
        sb.appendLine("AvoidDiskWrites 1")
        sb.appendLine("Log notice stdout")
        sb.appendLine("ClientOnly 1")
        val cc = exitCountry.trim().lowercase()
        if (cc.length == 2 && geo.exists() && geo6.exists()) {
            sb.appendLine("GeoIPFile " + geo.absolutePath)
            sb.appendLine("GeoIPv6File " + geo6.absolutePath)
            sb.appendLine("ExitNodes {" + cc + "}")
            sb.appendLine("StrictNodes 0")
        } else if (cc.length == 2) {
            GhajarLog.w(TAG, "geoip assets missing, exit country ignored")
        }
        if (throughVpn) {
            sb.appendLine("Socks5Proxy 127.0.0.1:" + upstreamPort)
        }
        sb.append(bridges.torrc(ptBinary(context).absolutePath))
        val torrc = File(dir, "torrc")
        torrc.writeText(sb.toString())
        return torrc
    }

    fun start(context: Context, exitCountry: String, throughVpn: Boolean, bridges: TorBridges = TorBridges.NONE, socksPort: Int = SOCKS_PORT, upstreamPort: Int = BRIDGE_PORT): Boolean {
        stop()
        stopping = false
        bootstrapped = false
        bootstrapPercent = 0

        val bin = binary(context)
        if (!bin.exists()) {
            GhajarLog.e(TAG, "binary missing at " + bin.absolutePath)
            return false
        }

        val dir = dataDir(context)
        if (bridges.transport.isNotEmpty() && bridges.transport != "vanilla" && !ptBinary(context).exists()) {
            GhajarLog.e(TAG, "bridges need lyrebird, which is not in this build")
            return false
        }
        val torrc = writeTorrc(context, exitCountry, throughVpn, bridges, socksPort, upstreamPort)

        val p = try {
            ProcessBuilder(listOf(bin.absolutePath, "-f", torrc.absolutePath))
                .directory(dir)
                .redirectErrorStream(true)
                .apply { environment()["HOME"] = dir.absolutePath }
                .start()
        } catch (e: Exception) {
            GhajarLog.e(TAG, "spawn failed", e)
            return false
        }
        process = p

        thread(isDaemon = true, name = "tor-log") {
            runCatching {
                BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        if (stopping || process !== p) return@forEach
                        Log.i(TAG, line)
                        TorLog.emit(line)
                        val idx = line.indexOf("Bootstrapped ")
                        if (idx >= 0) {
                            val pct = line.substring(idx + 13)
                                .takeWhile { c -> c.isDigit() }
                                .toIntOrNull()
                            if (pct != null) {
                                bootstrapPercent = pct
                                if (pct >= 100) bootstrapped = true
                            }
                        }
                    }
                }
            }
        }

        return waitForPort(socksPort)
    }

    private fun waitForPort(socksPort: Int): Boolean {
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        var portOpen = false
        while (System.currentTimeMillis() < deadline) {
            if (stopping) return false
            val p = process
            if (p == null || !p.isAlive) {
                GhajarLog.e(TAG, "process exited before bootstrap completed")
                return false
            }
            if (!portOpen) {
                portOpen = runCatching {
                    Socket().use {
                        it.connect(InetSocketAddress("127.0.0.1", socksPort), 400)
                        true
                    }
                }.getOrDefault(false)
            }
            if (portOpen && bootstrapped) {
                Log.i(TAG, "bootstrapped, socks ready on 127.0.0.1:" + SOCKS_PORT)
                return true
            }
            Thread.sleep(500)
        }
        GhajarLog.e(TAG, "timed out at bootstrap " + bootstrapPercent + "%")
        return false
    }

    fun stop() {
        stopping = true
        val p = process ?: return
        terminateProcess(p, 3000)
        process = null
    }
}

/**
 * Tor bridges for a profile. Transports are served by lyrebird
 * (gitlab.torproject.org/.../lyrebird, BSD-3-Clause), which carries obfs4,
 * meek_lite, webtunnel and snowflake in one executable.
 */
data class TorBridges(val transport: String, val lines: List<String>) {

    /** torrc lines; empty when no bridge is used. */
    fun torrc(lyrebird: String): String {
        if (transport.isEmpty() || lines.isEmpty()) return ""
        val sb = StringBuilder("UseBridges 1\n")
        if (transport != "vanilla") {
            sb.append("ClientTransportPlugin obfs4,meek_lite,webtunnel,snowflake exec ").append(lyrebird).append("\n")
        }
        lines.forEach { sb.append("Bridge ").append(it.removePrefix("Bridge ").trim()).append("\n") }
        return sb.toString()
    }

    companion object {
        val NONE = TorBridges("", emptyList())
        val TRANSPORTS = listOf("obfs4", "meek_lite", "webtunnel", "snowflake", "vanilla")

        /**
         * Snowflake's own default bridges, verbatim from the pinned snowflake
         * v2.14.1 client/torrc (public, published by the Tor Project).
         */
        val SNOWFLAKE_DEFAULT = listOf(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=www.cdn77.com,www.phpmyadmin.net ice=stun:stun.antisip.com:3478,stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.nextcloud.com:3478,stun:stun.bethesda.net:3478,stun:stun.nextcloud.com:443 utls-imitate=hellorandomizedalpn",
            "snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=https://1098762253.rsc.cdn77.org/ fronts=www.cdn77.com,www.phpmyadmin.net ice=stun:stun.antisip.com:3478,stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.nextcloud.com:3478,stun:stun.bethesda.net:3478,stun:stun.nextcloud.com:443 utls-imitate=hellorandomizedalpn"
        )

        fun from(config: ProxyConfig): TorBridges {
            val x = config.extraJson()
            val lines = x.optString("bridges").lines().map { it.trim().removePrefix("Bridge ").trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val declared = x.optString("pt").lowercase()
            val transport = declared.ifEmpty { lines.firstOrNull()?.substringBefore(' ')?.lowercase()?.takeIf { it in TRANSPORTS } ?: if (lines.isNotEmpty()) "vanilla" else "" }
            if (transport == "snowflake" && lines.isEmpty()) return TorBridges("snowflake", SNOWFLAKE_DEFAULT)
            return if (lines.isEmpty()) NONE else TorBridges(transport, lines)
        }

        /** Parses the service extra written by [TorController.spec]. */
        fun fromSpec(parts: List<String>): TorBridges {
            val transport = parts.getOrElse(2) { "" }
            val lines = runCatching { String(java.util.Base64.getDecoder().decode(parts.getOrElse(3) { "" })) }.getOrDefault("")
                .lines().filter { it.isNotBlank() }
            return if (transport.isEmpty() || lines.isEmpty()) NONE else TorBridges(transport, lines)
        }
    }
}
