package net.gozar.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** How healthy a resolver looked, at a glance. */
enum class DnsHealth {
    /** Not measured yet. */
    UNTESTED,

    /** Answered correctly, quickly, and without lying. */
    GOOD,

    /** Answered, but slowly, unreliably, or with something worth reading. */
    FAIR,

    /** No usable answer, or an answer that was manufactured. */
    BAD
}

/**
 * Everything one resolver was found to be.
 *
 * Nullable fields mean "not established", never "no". The difference matters
 * more here than almost anywhere else in this app: a resolver whose recursion
 * was never tested and one that refused to recurse deserve different words on
 * screen, and collapsing them into `false` is how a scan starts reporting
 * findings it did not make.
 */
data class DnsVerdict(
    val id: String,
    val health: DnsHealth = DnsHealth.UNTESTED,

    /** Median of the successful attempts, in ms. Null if none succeeded. */
    val latencyMs: Int? = null,

    /** Of the queries actually sent, how many came back usable. */
    val successPercent: Int = 0,

    /** Attempts that timed out or died, as a percentage of those sent. */
    val lossPercent: Int = 0,

    /** The RA flag, confirmed against a name the server cannot be authoritative for. */
    val recursive: Boolean? = null,

    /** Plain UDP on 53 worked. */
    val udpOk: Boolean? = null,

    /** Plain TCP on 53 worked - which survives some UDP-level interference. */
    val tcpOk: Boolean? = null,

    /**
     * It answered a blocked name with a sinkhole address.
     *
     * The finding that matters most, because a poisoned resolver is fast and
     * looks perfectly healthy: ranked by latency alone it wins, and picking it
     * is exactly how a "working" DNS setting breaks every blocked site.
     */
    val poisoned: Boolean = false,

    /** The reply's id or question did not match what was asked. */
    val mismatched: Boolean = false,

    /**
     * A TXT lookup under the user's own tunnel domain came back through it.
     *
     * Null unless a DNS Tunnel profile with a real domain exists to test
     * against. There is no project-owned domain to substitute, and inventing
     * one would make this field a decoration.
     */
    val tunnelReady: Boolean? = null,

    /** The response code, or the exception, in the resolver's own words. */
    val note: String = "",

    val lastTestedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("health", health.name)
        .put("latencyMs", latencyMs ?: -1)
        .put("successPercent", successPercent)
        .put("lossPercent", lossPercent)
        .put("recursive", recursive?.toString() ?: "")
        .put("udpOk", udpOk?.toString() ?: "")
        .put("tcpOk", tcpOk?.toString() ?: "")
        .put("poisoned", poisoned)
        .put("mismatched", mismatched)
        .put("tunnelReady", tunnelReady?.toString() ?: "")
        .put("note", note)
        .put("lastTestedAt", lastTestedAt)

    companion object {
        fun fromJson(o: JSONObject): DnsVerdict {
            fun tri(key: String): Boolean? =
                o.optString(key, "").takeIf { it.isNotEmpty() }?.toBooleanStrictOrNull()
            return DnsVerdict(
                id = o.optString("id"),
                health = runCatching { DnsHealth.valueOf(o.optString("health")) }
                    .getOrDefault(DnsHealth.UNTESTED),
                latencyMs = o.optInt("latencyMs", -1).takeIf { it >= 0 },
                successPercent = o.optInt("successPercent", 0),
                lossPercent = o.optInt("lossPercent", 0),
                recursive = tri("recursive"),
                udpOk = tri("udpOk"),
                tcpOk = tri("tcpOk"),
                poisoned = o.optBoolean("poisoned", false),
                mismatched = o.optBoolean("mismatched", false),
                tunnelReady = tri("tunnelReady"),
                note = o.optString("note", ""),
                lastTestedAt = o.optLong("lastTestedAt", 0L)
            )
        }
    }
}

/**
 * What the scan is allowed to do to the network and the battery.
 *
 * Defaults are conservative on purpose. Twenty at a time finishes a thousand
 * resolvers in a couple of minutes on a phone, and going wider mostly measures
 * the phone's own socket contention rather than the resolvers - which is worse
 * than slow, because it produces numbers that are wrong.
 */
data class DnsScanBudget(
    /** Resolvers probed at the same time. */
    val concurrency: Int = 20,

    /** Per query. */
    val timeoutMs: Int = 3_000,

    /**
     * Queries per second across the whole scan.
     *
     * A ceiling on the app, not on any one server. Without it, a thousand
     * resolvers at twenty concurrent is a burst that a mobile network's own
     * NAT will start dropping - and dropped packets get recorded as resolvers
     * that failed, which is a scan lying about its subjects.
     */
    val queriesPerSecond: Int = 60,

    /**
     * The smallest gap between two queries to the same resolver.
     *
     * The deep stage asks one resolver several questions. Spacing them is what
     * keeps this a measurement rather than a small flood, and it is also how
     * the latency samples end up independent instead of queueing behind one
     * another.
     */
    val perResolverGapMs: Long = 220,

    /** Latency samples taken from a resolver that passed the light stage. */
    val deepRepeats: Int = 3
)

/** Where a scan has got to. */
data class DnsScanProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Resolvers that answered at all, so far. */
    val answered: Int = 0,
    /** True while the pass is stopping at the user's request. */
    val stopping: Boolean = false
)

/**
 * Measuring a thousand resolvers without melting the phone or lying about the
 * results.
 *
 * Two stages, and the split is the whole performance story. The light stage
 * sends one small standard A query to every resolver; most of a scraped list
 * does not answer at all, and there is no sense spending five queries each on
 * finding that out. Only responders reach the deep stage, which is where the
 * repeats, the TCP check, the recursion check and the poisoning check happen.
 *
 * On not being a weapon: every query is a single standard question with one
 * name in it, sent at a bounded rate, with a floor on the gap between two
 * queries to the same server. Nothing here sends ANY, nothing sets a large
 * EDNS buffer, and nothing asks a resolver for a response larger than the
 * question - the three things that turn a DNS client into an amplifier. A
 * server that refuses to answer a stranger is recorded as refusing and is not
 * asked again.
 */
object DnsScanEngine {

    private const val TAG = "GhajarDnsScan"

    /**
     * A name no resolver can be authoritative for, used to test recursion.
     *
     * The RA flag is a claim; this is the check. A scan of arbitrary IPs turns
     * up authoritative nameservers that answer their own zone perfectly and
     * are useless as resolvers, and they are only distinguishable by asking
     * for something outside it.
     */
    private const val RECURSION_PROBE = "www.iana.org"

    private val _progress = MutableStateFlow(DnsScanProgress())
    val progress: StateFlow<DnsScanProgress> = _progress.asStateFlow()

    private val _verdicts = MutableStateFlow<Map<String, DnsVerdict>>(emptyMap())
    val verdicts: StateFlow<Map<String, DnsVerdict>> = _verdicts.asStateFlow()

    private val pending = LinkedHashMap<String, DnsVerdict>()
    private val pendingLock = Mutex()
    private val rateLock = Mutex()
    private var nextSlotAt = 0L

    @Volatile
    private var cancelled = false

    /** Seeds the table from storage, so a reopened screen is not blank. */
    fun seed(saved: Map<String, DnsVerdict>) {
        if (_verdicts.value.isEmpty()) _verdicts.value = saved
    }

    /** Asks the running pass to stop. It stops between queries, not mid-socket. */
    fun stop() {
        cancelled = true
        _progress.value = _progress.value.copy(stopping = true)
    }

    /**
     * Runs a pass over [resolvers].
     *
     * [baseline] is a set of addresses known to be the real answer for the
     * blocked probe host, obtained once before the pass. Poisoning is only
     * reported when there is something truthful to compare against: with no
     * baseline the verdict is left unset rather than guessed, because calling
     * a resolver a liar on no evidence is worse than saying nothing.
     */
    suspend fun run(
        resolvers: List<DnsResolver>,
        budget: DnsScanBudget = DnsScanBudget(),
        baseline: Set<String> = emptySet(),
        tunnelDomain: String? = null,
        onBatch: (Map<String, DnsVerdict>) -> Unit = {}
    ) {
        if (resolvers.isEmpty()) return
        cancelled = false
        nextSlotAt = 0L
        _progress.value = DnsScanProgress(running = true, done = 0, total = resolvers.size)

        val gate = Semaphore(budget.concurrency.coerceIn(1, 64))
        var done = 0
        var answered = 0
        val counterLock = Mutex()

        try {
            coroutineScope {
                val publisher = startPublisher(this, onBatch)
                val probes = resolvers.map { resolver ->
                    launch(Dispatchers.IO) {
                        if (cancelled) return@launch
                        gate.withPermit {
                            if (cancelled) return@withPermit
                            val verdict = runCatching {
                                measure(resolver, budget, baseline, tunnelDomain)
                            }.getOrElse { error ->
                                if (error is CancellationException) throw error
                                DnsVerdict(
                                    id = resolver.id,
                                    health = DnsHealth.BAD,
                                    note = error.javaClass.simpleName,
                                    lastTestedAt = System.currentTimeMillis()
                                )
                            }
                            pendingLock.withLock { pending[resolver.id] = verdict }
                            counterLock.withLock {
                                done++
                                if (verdict.health != DnsHealth.BAD) answered++
                                _progress.value = _progress.value.copy(
                                    done = done,
                                    answered = answered
                                )
                            }
                        }
                    }
                }
                // The probes are joined explicitly and only then is the
                // publisher cancelled. It loops forever by design, so leaving
                // it to coroutineScope would mean this block never returns -
                // and cancelling it before the join would drop the last batch,
                // which the flush in `finally` then has to rescue anyway.
                probes.joinAll()
                publisher.cancel()
            }
        } finally {
            flush(onBatch)
            _progress.value = _progress.value.copy(running = false, stopping = false)
        }
    }

    /**
     * Publishes accumulated results a few times a second rather than per result.
     *
     * A thousand resolvers arriving individually is a thousand state
     * emissions, each one recomposing a list - which is the difference between
     * a scan you can scroll during and a frozen screen. The batch window is
     * short enough to still feel live.
     */
    private fun startPublisher(
        scope: kotlinx.coroutines.CoroutineScope,
        onBatch: (Map<String, DnsVerdict>) -> Unit
    ): Job = scope.launch {
        while (true) {
            delay(BATCH_MS)
            flush(onBatch)
        }
    }

    private suspend fun flush(onBatch: (Map<String, DnsVerdict>) -> Unit) {
        val batch = pendingLock.withLock {
            if (pending.isEmpty()) return
            val copy = HashMap(pending)
            pending.clear()
            copy
        }
        _verdicts.value = _verdicts.value + batch
        onBatch(batch)
    }

    /**
     * Holds the whole scan to its queries-per-second ceiling.
     *
     * A slot-stamp rather than a token bucket: each caller claims the next
     * instant it is allowed to send and sleeps until then, so the rate is
     * smooth instead of arriving in bursts at the top of each second.
     */
    private suspend fun awaitSlot(budget: DnsScanBudget) {
        val gapMs = (1000L / budget.queriesPerSecond.coerceAtLeast(1)).coerceAtLeast(1L)
        val waitFor = rateLock.withLock {
            val now = System.currentTimeMillis()
            val slot = maxOf(now, nextSlotAt)
            nextSlotAt = slot + gapMs
            slot - now
        }
        if (waitFor > 0) delay(waitFor)
    }

    /**
     * The light stage and, for anything that answered, the deep one.
     */
    private suspend fun measure(
        resolver: DnsResolver,
        budget: DnsScanBudget,
        baseline: Set<String>,
        tunnelDomain: String?
    ): DnsVerdict = withContext(Dispatchers.IO) {
        val now = { System.currentTimeMillis() }

        // Stage one: a single ordinary A query for a name nothing blocks. This
        // separates "dead or not a resolver" from everything else, and it is
        // the only query most of a scraped list will ever receive.
        awaitSlot(budget)
        val first = probeOnce(resolver, GhajarDnsLab.NEUTRAL_PROBE_HOST, budget.timeoutMs)
        if (first !is Attempt.Answered) {
            return@withContext DnsVerdict(
                id = resolver.id,
                health = DnsHealth.BAD,
                successPercent = 0,
                lossPercent = 100,
                note = (first as Attempt.Dead).reason,
                lastTestedAt = now()
            )
        }

        val samples = mutableListOf<Int>()
        var sent = 1
        var ok = 1
        samples += first.ms
        var mismatched = first.answer.mismatched
        var recursive: Boolean? = null
        var poisoned = false
        var note = DnsWire.rcodeName(first.answer.rcode)

        // Recursion, checked rather than taken on the server's word.
        if (!cancelled) {
            delay(budget.perResolverGapMs)
            awaitSlot(budget)
            sent++
            val rec = probeOnce(resolver, RECURSION_PROBE, budget.timeoutMs)
            recursive = when (rec) {
                is Attempt.Answered -> {
                    ok++
                    // Both halves: it says it recurses, and it produced an
                    // address for a zone it cannot own.
                    rec.answer.recursionAvailable &&
                        rec.answer.rcode == DnsWire.RCODE_NOERROR &&
                        rec.answer.addresses.isNotEmpty()
                }
                is Attempt.Dead -> false
            }
            if (rec is Attempt.Answered) {
                samples += rec.ms
                if (rec.answer.mismatched) mismatched = true
            }
        }

        // Poisoning: only judged against a baseline obtained independently.
        if (!cancelled && baseline.isNotEmpty()) {
            delay(budget.perResolverGapMs)
            awaitSlot(budget)
            sent++
            when (val blocked = probeOnce(resolver, GhajarDnsLab.BLOCKED_PROBE_HOST, budget.timeoutMs)) {
                is Attempt.Answered -> {
                    ok++
                    samples += blocked.ms
                    val addresses = blocked.answer.addresses
                    poisoned = addresses.any { GhajarDnsLab.looksManufactured(it) } ||
                        // A NOERROR answer whose addresses share nothing with
                        // the truth is a manufactured one even when the address
                        // is not a known sinkhole. NXDOMAIN is not counted:
                        // refusing to answer is not the same as lying, and
                        // conflating them marks honest filtered resolvers as
                        // poisoners.
                        (blocked.answer.rcode == DnsWire.RCODE_NOERROR &&
                            addresses.isNotEmpty() &&
                            addresses.none { it in baseline } &&
                            addresses.none { looksPlausiblyGlobal(it) })
                    if (blocked.answer.rcode != DnsWire.RCODE_NOERROR) {
                        note = DnsWire.rcodeName(blocked.answer.rcode)
                    }
                }
                is Attempt.Dead -> Unit
            }
        }

        // Extra latency samples, up to the budget, for a median worth reading.
        var round = samples.size
        while (!cancelled && round < budget.deepRepeats) {
            delay(budget.perResolverGapMs)
            awaitSlot(budget)
            sent++
            when (val extra = probeOnce(resolver, GhajarDnsLab.NEUTRAL_PROBE_HOST, budget.timeoutMs)) {
                is Attempt.Answered -> { ok++; samples += extra.ms }
                is Attempt.Dead -> Unit
            }
            round++
        }

        // Transport support. For a UDP or TCP resolver both are worth knowing:
        // TCP survives interference that kills UDP on 53, and a resolver that
        // only does TCP is still usable.
        var udpOk: Boolean? = null
        var tcpOk: Boolean? = null
        if (resolver.transport == DnsTransport.UDP || resolver.transport == DnsTransport.TCP) {
            udpOk = resolver.transport == DnsTransport.UDP
            if (!cancelled) {
                delay(budget.perResolverGapMs)
                awaitSlot(budget)
                sent++
                val viaTcp = probeOnce(
                    resolver, GhajarDnsLab.NEUTRAL_PROBE_HOST, budget.timeoutMs, forceTcp = true
                )
                tcpOk = viaTcp is Attempt.Answered
                if (viaTcp is Attempt.Answered) { ok++; samples += viaTcp.ms }
            }
        }

        // The tunnel-path check, and only when there is real infrastructure to
        // aim it at. A resolver answering an ordinary A query says nothing
        // about whether it will carry a tunnel's TXT traffic.
        var tunnelReady: Boolean? = null
        if (!cancelled && !tunnelDomain.isNullOrBlank()) {
            delay(budget.perResolverGapMs)
            awaitSlot(budget)
            sent++
            tunnelReady = probeTunnelPath(resolver, tunnelDomain, budget.timeoutMs)
            if (tunnelReady) ok++
        }

        val median = samples.sorted().let { s ->
            if (s.isEmpty()) null else s[s.size / 2]
        }
        val successPercent = if (sent == 0) 0 else ok * 100 / sent
        val health = when {
            poisoned || mismatched -> DnsHealth.BAD
            median == null -> DnsHealth.BAD
            successPercent >= 80 && median <= 250 && recursive == true -> DnsHealth.GOOD
            successPercent >= 50 -> DnsHealth.FAIR
            else -> DnsHealth.BAD
        }

        DnsVerdict(
            id = resolver.id,
            health = health,
            latencyMs = median,
            successPercent = successPercent,
            lossPercent = if (sent == 0) 0 else (sent - ok) * 100 / sent,
            recursive = recursive,
            udpOk = udpOk,
            tcpOk = tcpOk,
            poisoned = poisoned,
            mismatched = mismatched,
            tunnelReady = tunnelReady,
            note = note,
            lastTestedAt = now()
        )
    }

    private sealed interface Attempt {
        data class Answered(val ms: Int, val answer: DnsWire.Answer) : Attempt
        data class Dead(val reason: String) : Attempt
    }

    private fun probeOnce(
        resolver: DnsResolver,
        host: String,
        timeoutMs: Int,
        forceTcp: Boolean = false
    ): Attempt {
        val id = (1..0xFFFE).random()
        val request = runCatching { DnsWire.query(host, DnsWire.TYPE_A, id) }.getOrNull()
            ?: return Attempt.Dead("bad host")
        val started = System.currentTimeMillis()
        return try {
            val reply = GhajarDnsLab.exchange(resolver, request, timeoutMs, forceTcp)
            val elapsed = (System.currentTimeMillis() - started).toInt()
            Attempt.Answered(elapsed, DnsWire.parse(reply, id, host))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Attempt.Dead(e.javaClass.simpleName)
        }
    }

    /**
     * Whether this resolver can carry tunnel traffic for [domain].
     *
     * A DNS tunnel moves its payload in TXT records under a domain whose
     * nameserver is the tunnel server. So the test is the real thing in
     * miniature: ask this resolver for a TXT record under a random label of
     * that domain, and see whether an answer comes back from the far side.
     *
     * A random label on every call, because a cached answer would prove only
     * that the resolver has a cache. NXDOMAIN counts as a pass: it means the
     * query reached a nameserver that owns the zone and came back, which is
     * the path the tunnel needs. A timeout or a refusal does not.
     */
    private fun probeTunnelPath(resolver: DnsResolver, domain: String, timeoutMs: Int): Boolean {
        val label = (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val name = "$label.${domain.trim().trim('.')}"
        val id = (1..0xFFFE).random()
        val request = runCatching { DnsWire.query(name, DnsWire.TYPE_TXT, id) }.getOrNull()
            ?: return false
        return try {
            val reply = GhajarDnsLab.exchange(resolver, request, timeoutMs)
            val answer = DnsWire.parse(reply, id, name)
            !answer.mismatched && answer.rcode in setOf(
                DnsWire.RCODE_NOERROR, DnsWire.RCODE_NXDOMAIN
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtains the truth to compare poisoned answers against.
     *
     * Over DoH, because a plain query on 53 is the thing being interfered
     * with: taking the baseline the same way would mean comparing a lie to a
     * lie. If it cannot be had, the scan reports no poisoning verdicts at all
     * rather than inventing them.
     */
    suspend fun baseline(timeoutMs: Int = 6_000): Set<String> = withContext(Dispatchers.IO) {
        for (source in BASELINE_SOURCES) {
            val id = (1..0xFFFE).random()
            val request = runCatching {
                DnsWire.query(GhajarDnsLab.BLOCKED_PROBE_HOST, DnsWire.TYPE_A, id)
            }.getOrNull() ?: continue
            val answer = runCatching {
                DnsWire.parse(
                    GhajarDnsLab.exchange(source, request, timeoutMs),
                    id,
                    GhajarDnsLab.BLOCKED_PROBE_HOST
                )
            }.getOrNull() ?: continue
            val usable = answer.addresses.filterNot { GhajarDnsLab.looksManufactured(it) }
            if (usable.isNotEmpty()) {
                GhajarLog.i(TAG, "baseline from ${source.name}: ${usable.size} addresses")
                return@withContext usable.toSet()
            }
        }
        GhajarLog.w(TAG, "no baseline available; poisoning will not be judged this pass")
        emptySet()
    }

    /**
     * An address that could plausibly be a real global one.
     *
     * Used only to avoid calling an answer manufactured when it is merely a
     * CDN node this app's baseline did not happen to see - a big site answers
     * differently from every vantage point, and comparing address sets
     * strictly would mark honest resolvers as liars. Private and reserved
     * ranges are the ones that cannot be a real answer for a public name.
     */
    private fun looksPlausiblyGlobal(address: String): Boolean {
        if (address.contains(':')) return true
        val parts = address.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b) = parts[0] to parts[1]
        return when {
            a == 10 -> false
            a == 127 -> false
            a == 0 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 168 -> false
            a == 169 && b == 254 -> false
            a == 100 && b in 64..127 -> false
            a >= 224 -> false
            else -> true
        }
    }

    private const val BATCH_MS = 140L

    /** Encrypted sources for the baseline, tried in order. */
    private val BASELINE_SOURCES = listOf(
        DnsResolver("Cloudflare", DnsTransport.DOH, "https://1.1.1.1/dns-query", "baseline"),
        DnsResolver("Google", DnsTransport.DOH, "https://8.8.8.8/dns-query", "baseline"),
        DnsResolver("Quad9", DnsTransport.DOH, "https://9.9.9.9/dns-query", "baseline")
    )
}
