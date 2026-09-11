package net.gozar.app

import android.net.VpnService
import android.util.Log
import ca.psiphon.PsiphonTunnel
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * NOTE ON PREREQUISITES - this file will not compile until two things are
 * added to the project (see CHANGELOG-PSIPHON.md for exact steps):
 *  1. The `ca.psiphon.PsiphonTunnel` / `psi.Psi` classes, from a
 *     `ca.psiphon.aar` built via the build-psiphon-aar.sh helper script
 *     (needs a checkout of a psiphon-tunnel-core fork + Go + gomobile +
 *     the Android NDK - this is a real native/Go build, not a file I can
 *     hand you as static output).
 *  2. Nothing else native - this build does NOT need its own
 *     own TUN bridge (no hev-socks5-tunnel required). It only opens a local
 *     SOCKS port; Xray (already in this app, via Gozarcore) dials that port
 *     as its outbound and does the actual TUN<->socks forwarding, exactly
 *     the way AetherController's SOCKS proxy is consumed today.
 */
data class PsiphonSpec(
    val mode: String = "auto",
    val country: String = ""
) {
    fun toJson(): String = JSONObject().put("mode", mode).put("country", country).toString()

    companion object {
        fun from(config: ProxyConfig): PsiphonSpec? =
            if (config.protocol != "psiphon") null
            else PsiphonSpec(
                mode = config.psiphonMode.ifBlank { "auto" },
                country = config.psiphonCountry
            )

        fun parse(raw: String?): PsiphonSpec? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                PsiphonSpec(
                    mode = o.optString("mode", "auto"),
                    country = o.optString("country", "")
                )
            }.getOrNull()
        }
    }
}

/**
 * In-process wrapper around ca.psiphon.PsiphonTunnel. Ported near-verbatim
 * using ca.psiphon.PsiphonTunnel's own HostService callbacks, same
 * protect()-on-bindToDevice contract, same start/stop shape. Renamed the
 * outer class to avoid confusion with anything already named "Aether" in
 * this codebase (Psiphon and this app's existing MASQUE-based Aether engine
 * are unrelated technologies that happen to share a naming lineage with
 * this app's own naming).
 */
private class PsiphonRuntime(
    private val service: VpnService,
    private val configJson: String,
    private val onLog: (String) -> Unit,
    private val onStopped: (String?) -> Unit,
    private val onSocksPort: (Int) -> Unit,
) {
    private val active = AtomicBoolean(false)
    private val socksPort = AtomicInteger(0)
    private var tunnel: PsiphonTunnel? = null

    val isRunning: Boolean get() = active.get()

    private val host = object : PsiphonTunnel.HostService {
        override fun getContext() = service
        override fun getPsiphonConfig(): String = configJson

        override fun bindToDevice(fileDescriptor: Long) {
            if (!service.protect(fileDescriptor.toInt())) {
                throw IllegalStateException("VpnService.protect failed for fd $fileDescriptor")
            }
        }

        override fun onDiagnosticMessage(message: String) = onLog("[psiphon] [*] $message")

        override fun onListeningSocksProxyPort(port: Int) {
            socksPort.set(port)
            onSocksPort(port)
            onLog("[psiphon] [+] socks proxy listening on $port")
        }

        override fun onSocksProxyPortInUse(port: Int) = fail("socks port $port already in use")
        override fun onHttpProxyPortInUse(port: Int) {}
        override fun onConnecting() = onLog("[psiphon] [*] establishing a tunnel")
        override fun onConnected() = onLog("[psiphon] [+] tunnel established")
        override fun onConnectedServerRegion(region: String) = onLog("[psiphon] [+] connected through $region")
        override fun onClientRegion(region: String) = onLog("[psiphon] [*] client region reported as $region")
        override fun onUntunneledAddress(address: String) {}
        override fun onUpstreamProxyError(message: String) = fail("upstream proxy error: $message")
        override fun onInproxyMustUpgrade() = fail("this build is too old for in-proxy mode")

        override fun onExiting() {
            if (active.compareAndSet(true, false)) {
                onLog("[psiphon] [-] psiphon core is exiting")
                onStopped(null)
            }
        }
    }

    @Synchronized
    fun start() {
        if (active.get()) stop()
        socksPort.set(0)
        val started = runCatching {
            val instance = PsiphonTunnel.newPsiphonTunnel(host)
            tunnel = instance
            instance.setVpnMode(true)
            active.set(true)
            instance.startTunneling("")
        }
        started.onFailure { error ->
            active.set(false); tunnel = null
            onLog("[psiphon] [-] failed to start: ${error.message}")
            onStopped(error.message ?: "psiphon core failed to start")
        }
    }

    @Synchronized
    fun stop() {
        val instance = tunnel ?: return
        active.set(false); tunnel = null; socksPort.set(0)
        runCatching { instance.stop() }
    }

    private fun fail(message: String) {
        if (active.compareAndSet(true, false)) {
            onLog("[psiphon] [-] $message")
            onStopped(message)
        }
    }
}

/**
 * Mirrors AetherController's contract (available/start/stop/SOCKS_PORT) so
 * GozarVpnService and ConfigBuilder can treat "psiphon" the same way they
 * already treat "aether": start this before Gozarcore.start(), point an
 * Xray SOCKS outbound at SOCKS_PORT, tear down on disconnect.
 *
 * Difference from Aether: Aether's port is a fixed constant known up front;
 * Psiphon's port is only known once the tunnel-core library reports it via
 * onListeningSocksProxyPort, so start() blocks (with a timeout) until that
 * callback fires or the attempt fails.
 */
object PsiphonController {
    private const val TAG = "Psiphon"
    private const val READY_TIMEOUT_MS = 60_000L

    @Volatile
    var SOCKS_PORT: Int = 0
        private set

    @Volatile
    private var runtime: PsiphonRuntime? = null

    fun available(): Boolean = runCatching { Class.forName("ca.psiphon.PsiphonTunnel") }.isSuccess

    fun isRunning(): Boolean = runtime?.isRunning == true

    fun start(service: VpnService, spec: PsiphonSpec): Boolean {
        stop()
        if (!available()) {
            Log.e(TAG, "ca.psiphon.aar is not bundled in this build")
            return false
        }

        val dataDir = File(service.filesDir, "psiphon").apply { mkdirs() }
        // socksPort=0 in the request lets tunnel-core pick a free ephemeral
        // port; we read the real one back from onListeningSocksProxyPort.
        val requestJson = PsiphonConfig.build(spec.mode, 0, spec.country, dataDir)

        val portLatch = java.util.concurrent.CountDownLatch(1)
        var failure: String? = null

        val instance = PsiphonRuntime(
            service = service,
            configJson = requestJson,
            onLog = { line -> Log.i(TAG, line); GhajarLog.i(TAG, line) },
            onStopped = { message ->
                failure = message
                SOCKS_PORT = 0
                if (portLatch.count > 0) portLatch.countDown()
            },
            onSocksPort = { port ->
                SOCKS_PORT = port
                if (portLatch.count > 0) portLatch.countDown()
            }
        )
        runtime = instance
        instance.start()

        val reachedInTime = runCatching {
            portLatch.await(READY_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.getOrDefault(false)

        if (!reachedInTime) {
            Log.e(TAG, "timed out after ${READY_TIMEOUT_MS}ms waiting for psiphon to come up")
            stop()
            return false
        }
        if (SOCKS_PORT == 0) {
            Log.e(TAG, "psiphon did not come up: ${failure ?: "unknown error"}")
            return false
        }
        Log.i(TAG, "socks ready on 127.0.0.1:$SOCKS_PORT")
        return true
    }

    fun stop() {
        runtime?.stop()
        runtime = null
        SOCKS_PORT = 0
    }
}
