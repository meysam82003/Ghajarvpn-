package net.gozar.app

import android.net.VpnService
import dev.zeptun.Zeptun

/**
 * The zeptun userspace TUN engine, as an engine this app can use.
 *
 * What it is for here. The app's Xray path already owns a tun: the Go core is
 * handed the file descriptor and runs its own network stack inside. That path
 * is untouched by this class and does not use it.
 *
 * The gap it fills is the proxy-only modes. When Aether or Psiphon run with
 * routingMode = proxy, GozarVpnService deliberately establishes no tun at all
 * (see the `proxyOnly` branch): those engines publish a local SOCKS5 proxy and
 * nothing routes the device through it, so the user has to point individual
 * apps at the proxy by hand. zeptun is a tun engine that forwards to a SOCKS5
 * proxy, which is exactly the missing half - it turns a local proxy into a
 * device-wide tunnel without changing how the proxy itself works.
 *
 * Everything here is written so that the engine being absent is a normal
 * state, not an error. The libraries are built in CI from a pinned commit (see
 * third_party/zeptun/), and a build without that step simply has no zeptun in
 * it. [available] answers that honestly, every caller checks it, and nothing
 * falls back to pretending a tunnel exists.
 */
object ZeptunEngine {

    /** Result codes the bridge returns, named where the header names them. */
    const val OK = 0

    /**
     * Whether the native libraries loaded.
     *
     * Resolved once, on first use, and never retried: a missing .so does not
     * appear later in the same process, and retrying System.loadLibrary on
     * every connect would only repeat the same failure in the log.
     */
    val available: Boolean by lazy {
        runCatching {
            System.loadLibrary("zeptun")
            System.loadLibrary("zeptun-jni")
            // A real call, not just the load: the bridge registers its methods
            // in JNI_OnLoad, and this is what proves the registration found
            // dev.zeptun.Zeptun. zeptun_version_string() needs no running
            // engine, so it is safe to ask at any time.
            Zeptun.nativeVersion().isNotBlank()
        }.onFailure {
            GhajarLog.i(TAG, "engine not in this build: ${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    /** The engine's own version string, or null when it is not present. */
    fun version(): String? =
        if (!available) null else runCatching { Zeptun.nativeVersion() }.getOrNull()

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    /**
     * What the engine does with DNS queries that enter the tun.
     *
     * [FORWARD] is the conservative default and the behaviour of every build
     * before this setting existed: a query is just another flow through the
     * proxy, so the app's own DNS configuration - encrypted DNS, the chosen
     * resolver, fake DNS - stays in charge of it.
     */
    enum class DnsMode { FORWARD, HIJACK, FAKE_IP }

    /**
     * How much of the phone the engine is allowed to spend on throughput.
     *
     * [BALANCED] emits nothing at all and leaves zeptun's own mobile preset
     * exactly as it is - which is what every build so far has used.
     */
    enum class Profile { BALANCED, THROUGHPUT, BATTERY }

    /**
     * The address the engine's own resolver answers on in FAKE_IP mode.
     *
     * It has to be an address the tun routes and nothing else claims. The tun
     * this app establishes carries 0.0.0.0/0 and its own address is
     * 10.10.0.2/32, so a second host in that same documentation range is
     * reachable and cannot collide with a real server.
     */
    const val FAKE_DNS_ADDRESS = "10.10.0.53"

    /**
     * A TOML configuration for a tun that forwards to a local SOCKS5 proxy.
     *
     * Every key here is a field of zeptun's own config document, checked
     * against src/config_json.zig at the pinned commit. That is not a style
     * preference: its TOML parser returns ConfigError for a key or a section
     * it does not know (see its "toml rejects unknown keys" test), so an
     * invented name does not degrade - it stops the engine from starting at
     * all.
     *
     * Two details from zeptun's Android notes are load-bearing:
     *
     * - The proxy runs in this same app, so its own upstream sockets must not
     *   be captured by the tunnel serving it. The engine calls VpnService
     *   .protect on every socket it opens through the callback the bridge
     *   installs, which covers the engine's own; the proxy's are covered
     *   because Android does not route a protected socket back into the tun.
     * - Nothing here configures the interface or the routing table. With a
     *   descriptor handed in rather than a device the engine opened, its route
     *   layer returns early (engine.zig checks device_kind == .tun), and the
     *   addresses, routes and MTU are the ones VpnService.Builder already set.
     *   [mtu] is passed only so the stack segments to the same number.
     */
    fun socksConfig(
        socksHost: String = "127.0.0.1",
        socksPort: Int,
        mtu: Int = 1500,
        ipv6: Boolean = false,
        dnsMode: DnsMode = DnsMode.FORWARD,
        dnsUpstream: String = "",
        profile: Profile = Profile.BALANCED
    ): String = buildString {
        appendLine("preset = \"mobile\"")
        appendLine()
        appendLine("[tun]")
        appendLine("mtu = $mtu")
        appendLine()
        appendLine("[handler]")
        appendLine("kind = \"socks5\"")
        appendLine()
        appendLine("[handler.socks5]")
        appendLine("server = \"$socksHost:$socksPort\"")
        // UDP over SOCKS5 rather than tunnelled over its TCP control
        // connection: without it QUIC, DNS and every game go nowhere, and
        // both Psiphon's and Aether's local proxies speak UDP ASSOCIATE.
        appendLine("udp = true")
        appendLine("udp_mode = \"udp\"")
        appendLine("pipeline = true")

        // offload and multi_queue are deliberately absent. They exist in the
        // document, but zeptun's Android device pins vnet_hdr and csum_offload
        // to false (src/device/android.zig), because a descriptor from
        // VpnService carries no virtio header and no second queue. Setting
        // them would read as a feature and do nothing.

        when (profile) {
            // Nothing: the mobile preset's own numbers, unchanged.
            Profile.BALANCED -> Unit
            Profile.THROUGHPUT -> {
                appendLine()
                appendLine("[stack]")
                appendLine("tcp_rx_window = 524288")
                appendLine("tcp_tx_buffer = 1048576")
                appendLine("max_tcp_sessions = 4096")
                appendLine("max_udp_sessions = 2048")
                appendLine()
                appendLine("[io]")
                appendLine("rx_parallel = 8")
                appendLine("tx_slots = 512")
                appendLine()
                appendLine("[memory]")
                appendLine("budget_bytes = 67108864")
            }
            Profile.BATTERY -> {
                appendLine()
                appendLine("[stack]")
                appendLine("tcp_rx_window = 32768")
                appendLine("tcp_tx_buffer = 65536")
                appendLine("max_tcp_sessions = 512")
                appendLine("max_udp_sessions = 256")
                appendLine()
                appendLine("[io]")
                appendLine("rx_parallel = 2")
                appendLine("tx_slots = 64")
            }
        }

        when (dnsMode) {
            // No [dns] section at all: fake_ip and hijack both default to
            // false, and the engine's resolver stays out of the way.
            DnsMode.FORWARD -> Unit
            DnsMode.HIJACK -> {
                // hijack does nothing without an upstream - zeptun's own
                // dnsActive() is `fake_ip or (hijack and upstream != null)` -
                // so a blank one is not written as a half-configured section.
                val upstream = withPort(dnsUpstream)
                if (upstream != null) {
                    appendLine()
                    appendLine("[dns]")
                    appendLine("hijack = true")
                    appendLine("upstream = \"$upstream\"")
                }
            }
            DnsMode.FAKE_IP -> {
                appendLine()
                appendLine("[dns]")
                appendLine("fake_ip = true")
                // Its own defaults are 198.18.0.0/15 and fc00::/18. The v4
                // range is stated rather than inherited so the value in the
                // log is the value in the code, and the v6 range is offered
                // only when the tun actually has v6 - a synthetic address in a
                // family that cannot leave the phone is a connect timeout.
                append("fake_ranges = [\"198.18.0.0/15\"")
                if (ipv6) append(", \"fc00::/18\"")
                appendLine("]")
                // The resolver needs an address of its own: with a /32 tun
                // address zeptun cannot derive one (its dnsAddress4 gives up
                // above /30), and the service adds this same address as the
                // tun's DNS server so queries actually arrive here.
                appendLine("address = [\"$FAKE_DNS_ADDRESS\"]")
            }
        }
    }

    /**
     * A resolver as host:port, or null when there is nothing usable.
     *
     * zeptun parses upstream as an endpoint, so a bare address is rejected -
     * and a bare address is exactly what a user types.
     */
    private fun withPort(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        // An IPv6 literal has colons of its own, so only a bracketed form
        // carries a port; "2606:4700::1111" is a host, not host:port.
        val hasPort = if (text.startsWith("[")) text.contains("]:")
            else text.count { it == ':' } == 1
        return if (hasPort) text else if (text.contains(':')) "[$text]:53" else "$text:53"
    }

    /**
     * Starts the engine on an established tun.
     *
     * [fd] must stay owned by the caller - the engine does not close it, and
     * closing it here would pull the tun out from under a running session.
     * Returns null on success, or a short reason for the log and the UI.
     */
    fun start(service: VpnService, fd: Int, config: String): String? {
        if (!available) return "engine not in this build"
        if (running) return "already running"
        return runCatching {
            val rc = Zeptun.nativeStart(service, fd, config)
            if (rc == OK) {
                running = true
                GhajarLog.i(TAG, "engine ${version()} up on fd $fd")
                null
            } else {
                "start returned $rc"
            }
        }.getOrElse { "start threw ${it.javaClass.simpleName}" }
    }

    fun stop() {
        if (!available || !running) return
        runCatching { Zeptun.nativeStop() }
            .onFailure { GhajarLog.e(TAG, "stop threw ${it.javaClass.simpleName}") }
        running = false
    }

    /**
     * Live counters, or null when nothing is running.
     *
     * The indices are the order of ZeptunStats' uint64 fields in the engine's
     * own header, which is what the bridge indexes into - they are not a
     * guess, but they do move if that struct ever gains a field, so a reading
     * that looks impossible should be checked against the header first.
     */
    fun counters(): Counters? {
        if (!available || !running) return null
        return runCatching {
            Counters(
                rxPackets = Zeptun.nativeCounter(0),
                rxBytes = Zeptun.nativeCounter(1),
                txPackets = Zeptun.nativeCounter(2),
                txBytes = Zeptun.nativeCounter(3),
                rxDropped = Zeptun.nativeCounter(4),
                txDropped = Zeptun.nativeCounter(5)
            )
        }.getOrNull()
    }

    data class Counters(
        val rxPackets: Long,
        val rxBytes: Long,
        val txPackets: Long,
        val txBytes: Long,
        val rxDropped: Long,
        val txDropped: Long
    )

    private const val TAG = "GhajarZeptun"
}
