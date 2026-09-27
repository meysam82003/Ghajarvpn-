package net.gozar.app.engine

import android.content.Context
import net.gozar.app.GhajarLog
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * How to run one helper process ("sidecar") in front of sing-box.
 *
 * Every non-sing-box engine in this app has the same shape: an executable
 * built in CI from pinned source (`lib<name>.so` in nativeLibraryDir) that
 * listens on one local TCP port. Either that port is a SOCKS5 server
 * ([socks] = true: MasterDNS family, Mieru, Juicity, AmneziaWG, SSTP), or a
 * raw forward to whatever the remote server forwards to ([socks] = false:
 * the dnstt family, Slipstream, the SSH transports), which sing-box then
 * speaks SOCKS5 or SSH to. sing-box always sits on top, so every engine ends
 * in the same local SOCKS5 inbound that zeptun carries the device into.
 *
 * Arguments and file contents may contain [PORT] and [DIR]; the runner
 * replaces them with the chosen local port and the private working directory.
 */
data class SidecarLaunch(
    val binary: String,
    val args: List<String>,
    val socks: Boolean,
    /** Files written into the working directory before start, name to content. */
    val files: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val readyTimeoutMs: Long = 15_000,
    /** Files that hold credentials; deleted once the process is listening. */
    val secretFiles: Set<String> = files.keys
) {
    companion object {
        const val PORT = "{port}"
        /** A second free local port, for engines that need one (Mieru's RPC). */
        const val PORT2 = "{port2}"
        const val DIR = "{dir}"
    }
}

/**
 * Runs one [SidecarLaunch]. Returns from [start] only when the process is
 * alive and its local port accepts a connection; whether traffic actually
 * flows is for the engine test to decide.
 */
class SidecarRunner(private val tag: String, private val subdir: String) {

    @Volatile private var process: Process? = null
    @Volatile private var stopping = false
    private val tail = ArrayDeque<String>()

    @Volatile var onUnexpectedExit: ((Int?) -> Unit)? = null

    fun isRunning(): Boolean = process?.isAlive == true

    fun lastOutput(): List<String> = synchronized(tail) { tail.toList() }

    /** Returns null on success or a reason that is safe to show. */
    fun start(context: Context, launch: SidecarLaunch, port: Int): String? {
        stop()
        stopping = false
        val bin = File(context.applicationInfo.nativeLibraryDir, launch.binary)
        if (!bin.exists()) return "${launch.binary.removePrefix("lib").removeSuffix(".so")} is not in this build"
        val dir = File(context.noBackupFilesDir, subdir).apply { mkdirs() }
        val port2 = SingBoxRunner.freePort() ?: 0
        fun fill(s: String) = s.replace(SidecarLaunch.PORT2, port2.toString()).replace(SidecarLaunch.PORT, port.toString()).replace(SidecarLaunch.DIR, dir.absolutePath)
        runCatching { launch.files.forEach { (name, content) -> File(dir, name).writeText(fill(content)) } }
            .onFailure { return "could not write the engine configuration" }
        val p = runCatching {
            ProcessBuilder(listOf(bin.absolutePath) + launch.args.map(::fill))
                .directory(dir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = dir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                    launch.env.forEach { (k, v) -> environment()[k] = fill(v) }
                }
                .start()
        }.getOrElse { return "${launch.binary} could not be started: ${it.javaClass.simpleName}" }
        process = p
        synchronized(tail) { tail.clear() }
        thread(isDaemon = true, name = "$tag-log") {
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = GhajarLog.redact(raw)
                        synchronized(tail) { tail.addLast(line); while (tail.size > 60) tail.removeFirst() }
                        if (!stopping) GhajarLog.i(tag, line)
                    }
                }
            }
            val code = runCatching { p.waitFor() }.getOrNull()
            if (!stopping && process === p) {
                GhajarLog.e(tag, "exited unexpectedly, code=$code")
                process = null
                onUnexpectedExit?.invoke(code)
            }
        }
        val deadline = System.currentTimeMillis() + launch.readyTimeoutMs
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            if (stopping) break
            if (!p.isAlive) break
            if (runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true } }.getOrDefault(false)) { ready = true; break }
            Thread.sleep(150)
        }
        launch.secretFiles.forEach { runCatching { File(dir, it).delete() } }
        if (ready) return null
        val last = lastOutput().lastOrNull { it.isNotBlank() }.orEmpty().take(200)
        val alive = p.isAlive
        stop()
        return if (!alive) "${launch.binary.removePrefix("lib").removeSuffix(".so")} exited: $last"
            else "${launch.binary.removePrefix("lib").removeSuffix(".so")} did not open its local port"
    }

    fun stop() {
        stopping = true
        val p = process ?: return
        process = null
        runCatching { p.destroy(); if (!p.waitFor(2000, TimeUnit.MILLISECONDS)) p.destroyForcibly() }
    }
}

/** Builds the [SidecarLaunch] for a spec's "sidecar" object. Pure, so it is unit-tested. */
object Sidecars {

    private val HEX_KEY = Regex("^[0-9a-fA-F]{64}$")

    /** Every binary a sidecar kind needs, for availability checks. */
    fun binaryFor(kind: String): String = when (kind) {
        "dnstt" -> "libdnstt.so"
        "vaydns" -> "libvaydns.so"
        "noizdns" -> "libnoizdns.so"
        "masterdns" -> "libmasterdns.so"
        "stormdns" -> "libstormdns.so"
        "cottendns" -> "libcottendns.so"
        "slipstream" -> "libslipstream.so"
        "sshtransport", "awg", "mieru", "brook" -> HELPER
        else -> throw IllegalArgumentException("unknown engine: $kind")
    }

    fun launch(spec: JSONObject): SidecarLaunch = when (val kind = spec.optString("kind")) {
        "dnstt" -> SidecarLaunch("libdnstt.so", dnsttArgs(spec, SidecarLaunch.PORT), socks = false)
        "vaydns" -> SidecarLaunch("libvaydns.so", vaydnsArgs(spec, SidecarLaunch.PORT), socks = false)
        "noizdns" -> SidecarLaunch("libnoizdns.so", noizdnsArgs(spec, SidecarLaunch.PORT), socks = false)
        "masterdns", "stormdns", "cottendns" -> masterFamily(kind, spec)
        "slipstream" -> slipstream(spec)
        "sshtransport" -> sshTransport(spec)
        "awg" -> SidecarLaunch(HELPER, listOf("awg", "-listen", SidecarLaunch.PORT, "-config", "${SidecarLaunch.DIR}/awg.conf"),
            socks = true, files = mapOf("awg.conf" to spec.optString("conf").also {
                require(it.contains("[Interface]", true) && it.contains("[Peer]", true)) { "AmneziaWG: the profile needs an [Interface] and a [Peer]" }
            }))
        "mieru" -> SidecarLaunch(HELPER, listOf("mieru", "-listen", SidecarLaunch.PORT, "-rpc", SidecarLaunch.PORT2,
            "-url", spec.optString("url").also { require(it.startsWith("mieru://") || it.startsWith("mierus://")) { "Mieru: a mieru:// or mierus:// link is needed" } },
            "-dir", SidecarLaunch.DIR), socks = true, readyTimeoutMs = 20_000)
        "brook" -> SidecarLaunch(HELPER, listOf("brook", "-listen", SidecarLaunch.PORT,
            "-url", spec.optString("url").also { require(it.startsWith("brook://")) { "Brook: a brook:// link is needed" } }), socks = true)
        else -> throw IllegalArgumentException("unknown engine: $kind")
    }

    /**
     * vaydns-client (net2share/vaydns v0.2.8, vaydns-client/main.go):
     * `(-udp|-dot|-doh) R -pubkey HEX -domain D -listen 127.0.0.1:PORT
     *  [-record-type T] [-dnstt-compat] [-max-qname-len N] [-clientid-size N]`.
     */
    fun vaydnsArgs(spec: JSONObject, port: String): List<String> {
        val (flag, resolver) = resolverFlag(spec)
        val out = mutableListOf(flag, resolver, "-pubkey", pubkey(spec), "-domain", domain(spec), "-listen", "127.0.0.1:$port")
        spec.optString("recordType").takeIf { it in setOf("txt", "null", "cname", "a", "aaaa", "mx", "ns", "srv", "caa") }
            ?.let { out += listOf("-record-type", it) }
        if (spec.optBoolean("dnsttCompat")) out += "-dnstt-compat"
        spec.optInt("maxQnameLen", 0).takeIf { it > 0 }?.let { out += listOf("-max-qname-len", it.toString()) }
        spec.optInt("clientIdSize", 0).takeIf { it > 0 && !spec.optBoolean("dnsttCompat") }?.let { out += listOf("-clientid-size", it.toString()) }
        return out
    }

    /**
     * noizdns-client (anonvector/noizdns 7289a56, cmd/noizdns-client):
     * `(-udp|-dot|-doh) R -pubkey HEX [-noiz] [-stealth] DOMAIN 127.0.0.1:PORT`.
     * Without -noiz it speaks plain dnstt, so it also reaches dnstt servers.
     */
    fun noizdnsArgs(spec: JSONObject, port: String): List<String> {
        val (flag, resolver) = resolverFlag(spec)
        val out = mutableListOf(flag, resolver, "-pubkey", pubkey(spec))
        val noiz = spec.optBoolean("noiz", true)
        if (noiz) out += "-noiz"
        if (noiz && spec.optBoolean("stealth")) out += "-stealth"
        out += listOf(domain(spec), "127.0.0.1:$port")
        return out
    }

    /**
     * MasterDnsVPN (masterking32/MasterDnsVPN acbf1c6) and its forks
     * StormDNS (NullRoute1970/StormDNS ca2eb48) and CottenDNS
     * (WhiteDNS/CottenDns cdf084f): `client -config FILE -resolvers FILE`,
     * TOML configuration, the client itself serves SOCKS5 on LISTEN_PORT.
     * Unset keys keep each client's own defaults (defaultClientConfig()).
     */
    fun masterFamily(kind: String, spec: JSONObject): SidecarLaunch {
        val domains = spec.optString("domain").split(',', ' ', '\n').map { it.trim().trim('.') }.filter { it.isNotEmpty() }
        require(domains.isNotEmpty()) { "DNS tunnel: no domain" }
        val key = spec.optString("key")
        require(key.isNotBlank()) { "DNS tunnel: no encryption key" }
        val resolvers = spec.optString("resolvers").split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        require(resolvers.isNotEmpty()) { "DNS tunnel: no resolver" }
        val enc = spec.optInt("enc", 1).coerceIn(0, 5)
        val toml = buildString {
            appendLine("DOMAINS = [" + domains.joinToString(", ") { tomlString(it) } + "]")
            appendLine("DATA_ENCRYPTION_METHOD = $enc")
            appendLine("ENCRYPTION_KEY = " + tomlString(key))
            appendLine("PROTOCOL_TYPE = \"SOCKS5\"")
            appendLine("LISTEN_IP = \"127.0.0.1\"")
            appendLine("LISTEN_PORT = ${SidecarLaunch.PORT}")
            appendLine("LOCAL_DNS_ENABLED = false")
            if (kind == "cottendns") {
                when (spec.optString("transport")) {
                    "dot" -> appendLine("RESOLVER_TRANSPORT = \"dot\"")
                    "doh" -> appendLine("RESOLVER_TRANSPORT = \"doh\"")
                }
            }
        }
        return SidecarLaunch(
            binary = binaryFor(kind),
            args = listOf("-config", "${SidecarLaunch.DIR}/client.toml", "-resolvers", "${SidecarLaunch.DIR}/resolvers.txt"),
            socks = true,
            files = mapOf("client.toml" to toml, "resolvers.txt" to resolvers.joinToString("\n") + "\n"),
            // These clients measure resolver MTUs before they listen.
            readyTimeoutMs = 90_000,
            secretFiles = setOf("client.toml")
        )
    }

    /**
     * slipstream-client (Mygod/slipstream-rust 7de506b, QUIC over DNS):
     * `--tcp-listen-host 127.0.0.1 --tcp-listen-port PORT --resolver R --domain D
     *  [--authoritative R2] [--cert FILE] [-c bbr|dcubic]`. A raw forward to the
     * server's target, like dnstt. The pinned certificate is optional.
     */
    fun slipstream(spec: JSONObject): SidecarLaunch {
        val (flag, resolver) = resolverFlag(spec)
        require(flag == "-udp") { "Slipstream carries plain UDP DNS only" }
        val args = mutableListOf("--tcp-listen-host", "127.0.0.1", "--tcp-listen-port", SidecarLaunch.PORT,
            "--resolver", resolver, "--domain", domain(spec))
        spec.optString("authoritative").takeIf { it.isNotBlank() }?.let { args += listOf("--authoritative", it) }
        spec.optString("cc").takeIf { it == "bbr" || it == "dcubic" }?.let { args += listOf("-c", it) }
        val files = mutableMapOf<String, String>()
        spec.optString("cert").takeIf { it.contains("BEGIN CERTIFICATE") }?.let {
            files["server.pem"] = it
            args += listOf("--cert", "${SidecarLaunch.DIR}/server.pem")
        }
        return SidecarLaunch("libslipstream.so", args, socks = false, files = files, secretFiles = emptySet())
    }

    /** The in-repo helper (native/ghajar-helper, GPL-3.0), built in CI. */
    const val HELPER = "libghajarhelper.so"

    /** SSH transport modes the helper implements; "direct" needs no helper at all. */
    val SSH_MODES = setOf("payload", "http-proxy", "https-proxy", "tls", "payload-tls", "ws", "wss")

    /**
     * ghajar-helper sshtransport (native/ghajar-helper/sshtransport.go): a raw
     * forward to the SSH server through the chosen disguise; sing-box's SSH
     * client (with its host-key check) runs on top.
     */
    fun sshTransport(spec: JSONObject): SidecarLaunch {
        val mode = spec.optString("mode")
        require(mode in SSH_MODES) { "SSH: unknown transport mode $mode" }
        val host = spec.optString("host").also { require(it.isNotBlank()) { "SSH: no server" } }
        val args = mutableListOf("sshtransport", "-listen", SidecarLaunch.PORT, "-mode", mode,
            "-host", host, "-port", spec.optInt("port", 22).toString())
        val proxyHost = spec.optString("proxyHost")
        if (proxyHost.isNotBlank()) args += listOf("-proxy", proxyHost + ":" + spec.optInt("proxyPort", if (mode == "https-proxy" || mode.contains("tls") || mode == "wss") 443 else 80))
        else require(mode != "http-proxy" && mode != "https-proxy") { "SSH: this mode needs a proxy address" }
        spec.optString("sni").takeIf { it.isNotBlank() }?.let { args += listOf("-sni", it) }
        spec.optString("payload").takeIf { it.isNotBlank() }?.let {
            args += listOf("-payload", java.util.Base64.getEncoder().encodeToString(it.toByteArray()))
        }
        spec.optString("wsPath").takeIf { it.isNotBlank() }?.let { args += listOf("-ws-path", it) }
        spec.optString("wsHost").takeIf { it.isNotBlank() }?.let { args += listOf("-ws-host", it) }
        spec.optString("ua").takeIf { it.isNotBlank() }?.let { args += listOf("-ua", it) }
        if (spec.optBoolean("wsFraming")) args += "-ws-framing"
        if (spec.optBoolean("verify")) args += "-verify"
        require(mode !in setOf("payload", "payload-tls") || spec.optString("payload").isNotBlank()) { "SSH: this mode needs a payload" }
        return SidecarLaunch(HELPER, args, socks = false)
    }

    private fun tomlString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * dnstt-client (v1.20260501.0, dnstt-client/main.go):
     * `(-udp ADDR | -dot ADDR | -doh URL) -pubkey HEX DOMAIN 127.0.0.1:PORT`.
     */
    fun dnsttArgs(spec: JSONObject, port: String): List<String> {
        val (flag, resolver) = resolverFlag(spec)
        val domain = domain(spec)
        return listOf(flag, resolver, "-pubkey", pubkey(spec), domain, "127.0.0.1:$port")
    }

    internal fun resolverFlag(spec: JSONObject): Pair<String, String> {
        val resolver = spec.optString("resolver").trim()
        require(resolver.isNotEmpty()) { "DNS tunnel: no resolver" }
        val flag = when (spec.optString("transport", "udp")) { "doh" -> "-doh"; "dot" -> "-dot"; else -> "-udp" }
        return flag to resolver
    }

    internal fun domain(spec: JSONObject): String =
        spec.optString("domain").trim().trim('.').also { require(it.isNotEmpty()) { "DNS tunnel: no domain" } }

    internal fun pubkey(spec: JSONObject): String =
        spec.optString("pubkey").trim().also { require(HEX_KEY.matches(it)) { "DNS tunnel: the server key must be 64 hex digits" } }.lowercase()
}
