package net.gozar.app

/**
 * Policy ONLY for traffic that a client deliberately sends to a controlled,
 * authenticated relay. Android VpnService does not capture stock hotspot
 * clients' arbitrary traffic. No system-wide tethering guarantee is implied.
 *
 * The transport MUST authenticate its client before calling open(), call
 * forward() before each bounded network write, and tear down live sockets on
 * any VPN/relay/network transition. A policy object is not itself a relay.
 */
internal enum class GhajarShareMode { VPN_ONLY, HYBRID, CLIENTS_ONLY }
internal enum class GhajarShareRoute { VPN, DIRECT, DENY }

internal data class GhajarShareNetwork(
    val vpnConnected: Boolean,
    val relayReady: Boolean,
    val vpnRouteBound: Boolean,
    /** Explicit user consent to possible direct connection / public IP leak. */
    val directApproved: Boolean = false,
    /** Proof from the transport of separate host/direct and client/VPN routes. */
    val hostIsolationVerified: Boolean = false
)

internal data class GhajarShareClientLimit(
    val maxBytes: Long = Long.MAX_VALUE,
    val maxDurationMs: Long = Long.MAX_VALUE,
    val paused: Boolean = false,
    val blocked: Boolean = false
) {
    init {
        require(maxBytes >= 0L && maxDurationMs >= 0L)
    }
}

/** Stateful, synchronized admission and byte reservations across reconnects. */
internal class GhajarSharePolicy(
    initialMode: GhajarShareMode,
    private val startedAtMs: Long,
    private val maxClients: Int = 5,
    private val globalByteLimit: Long = Long.MAX_VALUE,
    private val globalTimeLimitMs: Long = Long.MAX_VALUE
) {
    init {
        require(maxClients > 0 && globalByteLimit >= 0L && globalTimeLimitMs >= 0L)
    }

    private data class Client(var firstSeenMs: Long, var usedBytes: Long = 0L)
    private val registered = mutableMapOf<String, Client>()
    private val active = mutableSetOf<String>()
    private val limits = mutableMapOf<String, GhajarShareClientLimit>()
    private var totalBytes = 0L
    private var stopped = false
    private var allowNewClients = true
    private var mode = initialMode

    @Synchronized fun changeMode(next: GhajarShareMode) { mode = next }
    @Synchronized fun setAllowNewClients(allowed: Boolean) { allowNewClients = allowed }
    @Synchronized fun setClientLimit(clientId: String, limit: GhajarShareClientLimit) {
        require(clientId.isNotBlank())
        limits[clientId] = limit
        if (limit.blocked) active.remove(clientId)
    }

    /** Caller MUST verify token/credentials, not infer identity from LAN IP. */
    @Synchronized fun open(
        clientId: String, authenticated: Boolean, network: GhajarShareNetwork,
        nowMs: Long
    ): GhajarShareRoute {
        if (!authenticated || clientId.isBlank() || stopped || expired(nowMs)) return GhajarShareRoute.DENY
        val limit = limits[clientId] ?: GhajarShareClientLimit()
        val previous = registered[clientId]
        if (limit.blocked || limit.paused || (previous?.usedBytes ?: 0L) >= limit.maxBytes) return GhajarShareRoute.DENY
        if (previous != null && elapsed(nowMs, previous.firstSeenMs) >= limit.maxDurationMs) return GhajarShareRoute.DENY
        if (clientId !in active && (!allowNewClients || active.size >= maxClients)) return GhajarShareRoute.DENY
        val route = chooseRoute(network)
        if (route == GhajarShareRoute.DENY) return route
        if (previous == null) registered[clientId] = Client(nowMs)
        active.add(clientId)
        return route
    }

    /** Reserve bytes BEFORE forwarding; no quota bypass via reconnect/races. */
    @Synchronized fun forward(clientId: String, bytes: Long, network: GhajarShareNetwork, nowMs: Long): GhajarShareRoute {
        if (bytes <= 0L || stopped || clientId !in active || expired(nowMs)) return GhajarShareRoute.DENY
        val client = registered[clientId] ?: return GhajarShareRoute.DENY
        val limit = limits[clientId] ?: GhajarShareClientLimit()
        if (limit.blocked || limit.paused || elapsed(nowMs, client.firstSeenMs) >= limit.maxDurationMs ||
            !fits(client.usedBytes, bytes, limit.maxBytes) || !fits(totalBytes, bytes, globalByteLimit)) {
            return GhajarShareRoute.DENY
        }
        val route = chooseRoute(network)
        if (route == GhajarShareRoute.DENY) return route
        client.usedBytes += bytes
        totalBytes += bytes
        return route
    }

    @Synchronized fun disconnect(clientId: String) { active.remove(clientId) }
    /** Terminal and idempotent. Restart requires a new policy/session object. */
    @Synchronized fun stop() { stopped = true; active.clear() }
    @Synchronized fun usedBytes(clientId: String): Long = registered[clientId]?.usedBytes ?: 0L
    @Synchronized fun globalUsedBytes(): Long = totalBytes
    @Synchronized fun activeClientCount(): Int = active.size

    private fun chooseRoute(network: GhajarShareNetwork): GhajarShareRoute {
        val vpnUsable = network.vpnConnected && network.relayReady && network.vpnRouteBound
        return when (mode) {
            GhajarShareMode.VPN_ONLY -> if (vpnUsable) GhajarShareRoute.VPN else GhajarShareRoute.DENY
            GhajarShareMode.CLIENTS_ONLY -> if (vpnUsable && network.hostIsolationVerified) GhajarShareRoute.VPN else GhajarShareRoute.DENY
            GhajarShareMode.HYBRID -> when {
                vpnUsable -> GhajarShareRoute.VPN
                network.relayReady && network.directApproved -> GhajarShareRoute.DIRECT
                else -> GhajarShareRoute.DENY
            }
        }
    }

    private fun expired(nowMs: Long): Boolean =
        elapsed(nowMs, startedAtMs) >= globalTimeLimitMs || totalBytes >= globalByteLimit

    private fun elapsed(now: Long, start: Long): Long = if (now < start) Long.MAX_VALUE else now - start
    private fun fits(used: Long, increment: Long, cap: Long): Boolean = used <= cap && increment <= cap - used
}
