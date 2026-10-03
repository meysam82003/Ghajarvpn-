package net.gozar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers

/** Called only under GozarVpnService.engineLock; no VpnService or TUN is created here. */
object AetherTorRuntime {
    suspend fun start(context: Context, spec: AetherSpec, options: OblivionOptions): ChainSession {
        options.validate()
        check(AetherController.available(context) && TorController.available(context)) { "Aether/Tor binary missing" }
        val ports = listOf(options.aetherPort, options.aetherPort + 1, AetherTorPolicy.TOR_PORT, AetherTorPolicy.CONTROL_PORT)
        val reservations = mutableListOf<java.net.ServerSocket>()
        try { ports.forEach { port -> reservations.add(java.net.ServerSocket().apply { bind(java.net.InetSocketAddress("127.0.0.1", port)) }) } }
        finally { reservations.forEach { it.close() } }
        fun step(port: Int, launch: () -> Boolean, alive: () -> Boolean, stop: () -> Unit) = object : ChainSession.Step {
            override fun start() { check(launch()) { "Chain engine failed to start" } }
            override fun ready(): Boolean { net.gozar.plugin.api.SocksProbe.test(port); return alive() }
            override fun alive() = alive.invoke()
            override fun stop() = stop.invoke()
        }
        val aether = step(options.aetherPort, {
            AetherController.start(context, spec, if (options.core == "aether-over-tor") AetherTorPolicy.TOR_PORT else null)
        }, AetherController::isRunning, AetherController::stop)
        val tor = step(AetherTorPolicy.TOR_PORT, {
            TorController.start(context, options.text("torCountry"), options.core == "tor-over-aether",
                bridges = AetherTorPolicy.bridgeLines(options).let { if (it.isEmpty()) TorBridges.NONE else TorBridges("vanilla", it) },
                socksPort = AetherTorPolicy.TOR_PORT, upstreamPort = options.aetherPort)
        }, TorController::isRunning, TorController::stop)
        val session = ChainSession(if (options.core == "tor-over-aether") listOf(aether, tor) else listOf(tor, aether))
        try {
            kotlinx.coroutines.withTimeout(360_000) { kotlinx.coroutines.runInterruptible(Dispatchers.IO) { session.start() } }
        } catch (e: Throwable) { session.close(); throw e }
        return session
    }
}
