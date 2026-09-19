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
     * A TOML configuration that routes a tun through a local SOCKS5 proxy.
     *
     * Two details from zeptun's own Android notes are load-bearing and are why
     * this is built here rather than left to a caller:
     *
     * - The proxy runs in this same app, so its own upstream sockets must not
     *   be captured by the tunnel serving it. The engine calls VpnService
     *   .protect on every socket it opens through the callback the bridge
     *   installs, which covers the engine's own; the proxy's are covered
     *   because Android does not route a protected socket back into the tun.
     * - IPv6 is left off the interface unless asked for. Giving the tun an
     *   IPv6 address when the upstream has none makes every application spend
     *   its connect timeout on an address family that cannot leave the phone.
     */
    fun socksConfig(
        socksHost: String = "127.0.0.1",
        socksPort: Int,
        mtu: Int = 8500,
        ipv6: Boolean = false
    ): String = buildString {
        appendLine("preset = \"mobile\"")
        appendLine("mtu = $mtu")
        appendLine()
        appendLine("[handler]")
        appendLine("kind = \"socks5\"")
        appendLine("address = \"$socksHost:$socksPort\"")
        appendLine()
        appendLine("[dns]")
        // Queries are forwarded like any other flow rather than answered here:
        // the app's own DNS settings (encrypted DNS, the chosen resolver, fake
        // DNS) live in the proxy's configuration, and a second resolver in
        // front of it would silently override all three.
        appendLine("mode = \"forward\"")
        if (!ipv6) {
            appendLine()
            appendLine("[interface]")
            appendLine("ipv6 = false")
        }
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
