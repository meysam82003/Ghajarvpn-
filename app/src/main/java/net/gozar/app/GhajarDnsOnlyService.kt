package net.gozar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/** What DNS-only mode is doing. Deliberately not [VpnState]. */
enum class DnsOnlyPhase { OFF, STARTING, ACTIVE, FAILED }

/**
 * The state of DNS-only mode, kept apart from the tunnel's own state.
 *
 * Two separate facts, never merged: "the chosen resolver is being used for
 * lookups" and "a tunnel is up and carrying traffic". The home screen shows
 * the tunnel; this shows DNS. Folding one into the other would let the app
 * report a VPN that does not exist, which is the single most dangerous thing a
 * screen in this app can say.
 */
object DnsOnlyState {

    private val _phase = MutableStateFlow(DnsOnlyPhase.OFF)
    val phase: StateFlow<DnsOnlyPhase> = _phase.asStateFlow()

    private val _resolver = MutableStateFlow<String?>(null)
    val resolver: StateFlow<String?> = _resolver.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Queries forwarded and answered since this session started. */
    private val _served = MutableStateFlow(0L)
    val served: StateFlow<Long> = _served.asStateFlow()

    /**
     * Android's own Private DNS is on and will bypass this mode.
     *
     * Reported rather than worked around, because it cannot be worked around
     * from an app: when Private DNS is set to a hostname, the platform
     * resolver talks to that server over TLS and never consults the DNS server
     * a VpnService advertises. A user who has set it and is not told would see
     * this mode claim to be active while none of their lookups went through
     * the resolver they chose.
     */
    private val _privateDnsConflict = MutableStateFlow(false)
    val privateDnsConflict: StateFlow<Boolean> = _privateDnsConflict.asStateFlow()

    internal fun starting(address: String) {
        _resolver.value = address
        _error.value = null
        _served.value = 0L
        _phase.value = DnsOnlyPhase.STARTING
    }

    internal fun active(privateDnsOn: Boolean) {
        _privateDnsConflict.value = privateDnsOn
        _phase.value = DnsOnlyPhase.ACTIVE
    }

    internal fun served() {
        _served.value = _served.value + 1
    }

    internal fun failed(reason: String) {
        _error.value = reason
        _phase.value = DnsOnlyPhase.FAILED
        _resolver.value = null
    }

    internal fun off() {
        _phase.value = DnsOnlyPhase.OFF
        _resolver.value = null
        _privateDnsConflict.value = false
        _served.value = 0L
    }

    val active: Boolean get() = _phase.value == DnsOnlyPhase.ACTIVE
}

/**
 * Uses one chosen resolver for DNS, and nothing else.
 *
 * How it works, and why this shape rather than a full tunnel:
 *
 * A VpnService is the only way an app can change which resolver other apps
 * use. But a tun that captures everything would have to carry everything, and
 * this mode has no business moving anybody's traffic. So the tun advertises
 * the chosen resolver as its DNS server and adds a route for *that address
 * only*. DNS packets addressed to it enter the tun; everything else never
 * touches it and continues over the normal network untouched.
 *
 * The queries that arrive are forwarded from a socket this service has
 * `protect()`ed, which is what makes the forward go out over the real network
 * instead of back into the tun. Without that single call the first query would
 * loop until the phone gave up.
 *
 * Three things it refuses to do:
 *
 * - It will not start while the tunnel service is up. Android hands the tun to
 *   whoever established it last, so two of this app's own VpnServices racing
 *   means one silently wins and the app's state is a coin flip.
 * - It never reports itself through [VpnState]. That flow is the tunnel's, and
 *   a DNS forwarder writing into it would make the home screen say connected.
 * - It does not claim to work when Android's Private DNS is on. It says so
 *   instead - see [DnsOnlyState.privateDnsConflict].
 */
class GhajarDnsOnlyService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pump: Job? = null
    private var watchdog: Job? = null

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                return START_NOT_STICKY
            }
        }
        val address = intent?.getStringExtra(EXTRA_RESOLVER)?.trim().orEmpty()
        if (address.isEmpty()) {
            DnsOnlyState.failed("no resolver given")
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) {
            // Already up on possibly a different resolver: tear the old tun
            // down first rather than establishing a second one.
            teardown()
        }
        start(address)
        return START_NOT_STICKY
    }

    private fun start(address: String) {
        DnsOnlyState.starting(address)
        startForegroundNotice(address)

        // A tunnel already holds the tun. Refusing is the honest outcome: the
        // tunnel is the bigger promise and this mode is not worth breaking it.
        if (VpnState.state.value != Connection.DISCONNECTED &&
            VpnState.state.value != Connection.ERROR
        ) {
            GhajarLog.w(TAG, "refusing to start: the tunnel service holds the tun")
            DnsOnlyState.failed("tunnel-active")
            stopSelf()
            return
        }

        val resolverAddress = runCatching { InetAddress.getByName(address) }.getOrNull()
        if (resolverAddress == null || resolverAddress.address.size != 4) {
            // IPv4 only, said out loud. The packet relay handles IPv4+UDP, and
            // advertising an IPv6 resolver it cannot forward would be a mode
            // that looks active and answers nothing.
            GhajarLog.e(TAG, "not an IPv4 resolver: $address")
            DnsOnlyState.failed("not-ipv4")
            stopSelf()
            return
        }

        val fd = runCatching {
            Builder()
                .setSession("GhajarVPN DNS")
                .setMtu(MTU)
                // A link-local-ish private address inside the tun. Nothing
                // else ever sees it; it only has to not collide with a real
                // network the phone is on.
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(address)
                // The one route. This is the whole design: only packets for
                // the resolver's own address enter the tun, so non-DNS traffic
                // cannot be captured, cannot loop, and cannot be dropped by
                // this service having nowhere to send it.
                .addRoute(address, 32)
                .also { builder ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        builder.setMetered(false)
                    }
                    // This app's own traffic must not go through its own tun:
                    // the forwarding socket is protected, but excluding the
                    // app entirely removes a whole class of loop.
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
                .establish()
        }.getOrNull()

        if (fd == null) {
            GhajarLog.e(TAG, "could not establish the DNS tun")
            DnsOnlyState.failed("establish-failed")
            stopSelf()
            return
        }

        tun = fd
        running = true
        DnsOnlyState.active(privateDnsActive())
        GhajarLog.i(TAG, "DNS-only mode active on $address")
        pump = scope.launch { forward(fd, resolverAddress) }
        watchdog = scope.launch { watch(address) }
    }

    /**
     * Notices when the resolver in use has stopped answering, and applies the
     * user's policy.
     *
     * A watchdog rather than reacting to forwarding errors: one query timing
     * out is an ordinary event on these networks, and treating it as failure
     * would move the resolver constantly. Two consecutive probe failures is
     * the threshold, which on a 45-second period means roughly a minute and a
     * half of a resolver being genuinely unreachable.
     *
     * Everything about whether to move, and where to, is decided by
     * [DnsSmartSelect] - which is pure and tested. This function only observes
     * and acts; it holds none of the policy itself.
     */
    private suspend fun watch(address: String) {
        val store = ConfigStore.get(applicationContext)
        val labStore = DnsResolverStore.get(applicationContext)
        labStore.load()
        var consecutiveFailures = 0

        while (running) {
            delay(WATCH_PERIOD_MS)
            if (!running) return

            val resolver = labStore.resolvers.value.firstOrNull { it.address == address }
                ?: return
            val alive = withContext(Dispatchers.IO) {
                runCatching {
                    val id = (1..0xFFFE).random()
                    val request = DnsWire.query(
                        GhajarDnsLab.NEUTRAL_PROBE_HOST, DnsWire.TYPE_A, id
                    )
                    // Through the same protected path the forwarder uses, so a
                    // healthy probe means the forwarder's own route is healthy
                    // - not merely that the resolver answers somebody else.
                    val reply = probeProtected(resolver, request)
                    DnsWire.parse(reply, id, GhajarDnsLab.NEUTRAL_PROBE_HOST)
                    true
                }.getOrDefault(false)
            }

            if (alive) {
                consecutiveFailures = 0
                continue
            }
            consecutiveFailures++
            GhajarLog.w(TAG, "$address did not answer ($consecutiveFailures in a row)")
            if (consecutiveFailures < FAILURES_BEFORE_ACTING) continue

            // Mark it failed in the store so the decision is made on a verdict
            // rather than on this function's private opinion.
            labStore.putVerdicts(
                mapOf(
                    resolver.id to (labStore.verdicts.value[resolver.id] ?: DnsVerdict(resolver.id))
                        .copy(
                            health = DnsHealth.BAD,
                            note = "unreachable",
                            lastTestedAt = System.currentTimeMillis()
                        )
                )
            )

            val next = DnsSmartSelect.decide(
                resolvers = labStore.resolvers.value,
                verdicts = labStore.verdicts.value,
                currentId = resolver.id,
                manuallyPinned = labStore.chosenManually.value,
                policy = store.dnsFailPolicy.value
            )
            if (next == null) {
                // Staying and failing visibly is a valid outcome, and the
                // reason is published so the screen can say which it was.
                GhajarLog.i(TAG, "not switching: ${DnsSmartSelect.reason.value}")
                DnsOnlyState.failed(DnsSmartSelect.reason.value)
                return
            }

            GhajarLog.i(TAG, "failing over to ${next.address}")
            labStore.choose(next.id, manual = false)
            store.setCustomDns(next.address)
            DnsSmartSelect.switchWorked()
            // Restarting rather than mutating: a VpnService's DNS server is
            // fixed at establish() time, so a new resolver means a new tun.
            start(next.address)
            return
        }
    }

    /**
     * One probe over a protected socket.
     *
     * Deliberately not GhajarDnsLab.exchange: that opens an ordinary socket,
     * which from inside this service would be routed by this service's own
     * tun. The probe has to take the same protected path the forwarder does or
     * it is measuring the wrong thing.
     */
    private fun probeProtected(resolver: DnsResolver, request: ByteArray): ByteArray =
        DatagramSocket().use { socket ->
            if (!protect(socket)) throw IllegalStateException("protect refused")
            socket.soTimeout = QUERY_TIMEOUT_MS
            socket.send(
                DatagramPacket(
                    request, request.size,
                    InetSocketAddress(InetAddress.getByName(resolver.address), 53)
                )
            )
            val reply = ByteArray(MAX_ANSWER)
            val packet = DatagramPacket(reply, reply.size)
            socket.receive(packet)
            reply.copyOf(packet.length)
        }

    /**
     * Reads queries out of the tun, forwards each one, writes the answer back.
     *
     * One protected socket per query rather than a shared one. It costs a file
     * descriptor for a few milliseconds and buys correctness: a single socket
     * would need its own request/response matching to avoid handing app A's
     * answer to app B, and getting that wrong is a DNS cache poisoning bug in
     * the app's own code.
     *
     * Failures are counted, not fatal. One query timing out is an ordinary
     * event on these networks; ending the session for it would turn a slow
     * lookup into DNS being off.
     */
    private fun forward(fd: ParcelFileDescriptor, resolver: InetAddress) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(MTU)
        val inFlight = AtomicLong(0)

        while (running) {
            val read = runCatching { input.read(buffer) }.getOrNull() ?: break
            if (read <= 0) continue
            val query = DnsPacketRelay.parseUdpQuery(buffer, read) ?: continue
            if (inFlight.get() > MAX_IN_FLIGHT) {
                // Shedding rather than queueing without bound. A burst that
                // outruns the network is better answered late by the app's own
                // retry than by this service holding a thousand sockets open.
                continue
            }
            val payload = query.payload
            inFlight.incrementAndGet()
            scope.launch {
                try {
                    val answer = exchange(resolver, payload)
                    if (answer != null && running) {
                        val reply = DnsPacketRelay.buildUdpReply(query, answer)
                        synchronized(output) { output.write(reply) }
                        DnsOnlyState.served()
                    }
                } catch (e: Exception) {
                    GhajarLog.w(TAG, "forward failed: ${e.javaClass.simpleName}")
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }
    }

    /**
     * Sends one query to the resolver over the real network.
     *
     * `protect()` is the load-bearing line. Without it this socket's packets
     * are routed by the tun that this very service established, and the query
     * comes straight back to be forwarded again.
     */
    private fun exchange(resolver: InetAddress, payload: ByteArray): ByteArray? =
        DatagramSocket().use { socket ->
            if (!protect(socket)) {
                GhajarLog.e(TAG, "protect() refused; not sending a query into our own tun")
                return null
            }
            socket.soTimeout = QUERY_TIMEOUT_MS
            socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(resolver, 53)))
            val reply = ByteArray(MAX_ANSWER)
            val packet = DatagramPacket(reply, reply.size)
            socket.receive(packet)
            reply.copyOf(packet.length)
        }

    /**
     * Whether the platform's own Private DNS will bypass this mode.
     *
     * Read from the active network's link properties rather than the setting,
     * because the setting says what the user asked for and this says what the
     * platform is doing - and on "opportunistic" they differ.
     */
    private fun privateDnsActive(): Boolean = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        cm.getLinkProperties(network)?.isPrivateDnsActive == true
    }.getOrDefault(false)

    private fun teardown() {
        if (!running && tun == null) {
            stopSelf()
            return
        }
        running = false
        pump?.cancel()
        pump = null
        watchdog?.cancel()
        watchdog = null
        runCatching { tun?.close() }
        tun = null
        DnsOnlyState.off()
        GhajarLog.i(TAG, "DNS-only mode stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        GhajarLog.i(TAG, "another VPN took the tun")
        teardown()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        pump?.cancel()
        watchdog?.cancel()
        runCatching { tun?.close() }
        tun = null
        if (DnsOnlyState.phase.value != DnsOnlyPhase.FAILED) DnsOnlyState.off()
        super.onDestroy()
    }

    /**
     * The notification, which is not optional.
     *
     * Android requires a foreground notification for this, and it is the right
     * thing anyway: a persistent entry that says which resolver is in use is
     * how a user finds out that DNS-only mode is still on tomorrow.
     */
    private fun startForegroundNotice(address: String) {
        val nm = getSystemService(NotificationManager::class.java)
        // minSdk is 26, so the channel always exists to be created.
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "GhajarVPN DNS", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ghajar)
            .setContentTitle(getString(R.string.app_name))
            // Says DNS, never "connected". The wording is the guard against
            // somebody reading this notification as a VPN being up.
            .setContentText("DNS · $address")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "GhajarDnsOnly"
        private const val CHANNEL_ID = "ghajar_dns_only"
        private const val NOTIFICATION_ID = 4711
        private const val MTU = 1500
        private const val QUERY_TIMEOUT_MS = 5_000
        private const val MAX_ANSWER = 4096
        private const val MAX_IN_FLIGHT = 64L

        /** How often the resolver in use is checked. */
        private const val WATCH_PERIOD_MS = 45_000L

        /** Consecutive probe failures before the policy is applied. */
        private const val FAILURES_BEFORE_ACTING = 2

        /** The address inside the tun. Nothing outside this service sees it. */
        private const val TUN_ADDRESS = "10.7.63.1"

        const val ACTION_STOP = "net.gozar.app.DNS_ONLY_STOP"
        private const val EXTRA_RESOLVER = "resolver"

        /**
         * Asks Android for VPN permission if needed, then starts the mode.
         *
         * Returns the consent intent when the user has to approve it. The
         * caller launches that and calls again - the same contract the tunnel
         * uses, so there is one way permission is obtained in this app.
         */
        fun prepareOrNull(context: Context): Intent? = VpnService.prepare(context)

        fun start(context: Context, resolverAddress: String) {
            val intent = Intent(context, GhajarDnsOnlyService::class.java)
                .putExtra(EXTRA_RESOLVER, resolverAddress)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, GhajarDnsOnlyService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
