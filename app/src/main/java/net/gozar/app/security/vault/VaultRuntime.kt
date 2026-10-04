package net.gozar.app.security.vault

import net.gozar.app.ProxyConfig
import net.gozar.app.engine.EngineId
import net.gozar.app.engine.EngineRouting
import java.util.UUID

/** Process-local, single-use launch grants. Neither credentials nor grants survive process death.
 * UI locking clears unused grants; the running engine retains only its own session until stop.
 */
object VaultRuntime {
    const val PREFIX = "vault:"
    fun isReference(id: String?) = id?.startsWith(PREFIX) == true
    class Prepared(val json: String, val singbox: String?) {
        override fun toString() = "VaultPrepared(redacted)"
    }
    private class Grant(val entry: VaultEntry, val config: ProxyConfig, val ledger: VaultUsageLedger,
        val issuedAt: Long, var prepared: Prepared? = null, var session: VaultMeteredSession? = null)
    private val grants = mutableMapOf<String, Grant>()
    private const val LAUNCH_WINDOW = 120_000L

    @Synchronized fun issue(entry: VaultEntry, ledger: VaultUsageLedger, now: Long): ProxyConfig {
        val config = VaultRepository.config(entry)
        require(entry.policy.allowConnect) { "اتصال طبق سیاست فایل مجاز نیست." }
        require(!entry.policy.requireLocalAuthentication) { "این فایل به احراز هویت محلی نیاز دارد؛ اتصال آن تا تکمیل مسیر Biometric مجاز نیست." }
        require(config.chainId.isBlank() && config.torBaseId.isBlank()) { "وابستگی زنجیره هنوز داخل نشست صندوق بسته‌بندی نشده است." }
        require(config.protocol !in setOf("xray-full","singbox-full","tailscale")) { "قرارداد سهمیه و منع مسیر مستقیم برای Full Config یا Tailscale هنوز مستقل است؛ نشست صندوق مجاز نیست." }
        val engine = EngineRouting.engineFor(config)
        require(engine in setOf(EngineId.XRAY, EngineId.SINGBOX)) { "این موتور هنوز قرارداد نشست صندوق را ندارد." }
        val usage = ledger.record(entry.usageId, 0, 0, now, imported = true)
        require(entry.policy.allowReconnect || usage.firstConnectAt == null) { "اتصال دوباره طبق سیاست این دسترسی مجاز نیست." }
        requireAllowed(VaultQuotaPolicy.local(entry, usage, now))
        grants.entries.removeAll { it.value.session == null }
        val ref = PREFIX + UUID.randomUUID()
        val runtime = config.copy(id = ref, name = entry.displayName, locked = true)
        grants[ref] = Grant(entry, runtime, ledger, now)
        return runtime
    }

    @Synchronized fun check(config: ProxyConfig, now: Long = System.currentTimeMillis()) {
        if (!isReference(config.id)) return
        val g = grant(config.id, now)
        require(VaultFingerprint.of(config) == VaultFingerprint.of(g.config)) { "محتوای نشست صندوق تغییر کرده است." }
        requireAllowed(VaultQuotaPolicy.local(g.entry, g.ledger.read(g.entry.usageId), now))
    }
    @Synchronized fun prepare(config: ProxyConfig, prepared: Prepared, now: Long = System.currentTimeMillis()) {
        check(config, now)
        val g = grant(config.id, now)
        require(g.session == null) { "نشست صندوق قبلاً شروع شده است." }
        g.prepared = prepared
    }
    @Synchronized fun claim(ref: String, now: Long): Pair<Prepared, VaultMeteredSession> {
        val g = grant(ref, now)
        require(g.session == null) { "شناسهٔ نشست صندوق قبلاً مصرف شده است." }
        val prepared = g.prepared ?: throw VaultException(VaultException.Kind.LOCKED)
        val session = VaultMeteredSession(g.entry, g.ledger, now)
        g.session = session
        g.prepared = null
        return prepared to session
    }
    @Synchronized fun activeReference(entryId: String): String? = grants.entries.firstOrNull { it.value.entry.id == entryId && it.value.session?.active == true }?.key
    @Synchronized fun forget(ref: String?) { grants.remove(ref)?.session?.close() }
    @Synchronized fun lock() { grants.entries.removeAll { it.value.session == null } }
    @Synchronized fun testAllowed(ref: String): Boolean = grants[ref]?.let {
        it.entry.policy.allowTest && it.session?.active == true
    } == true
    @Synchronized fun reconnectAllowed(ref: String, now: Long): Boolean = grants[ref]?.let {
        it.entry.policy.allowReconnect && it.session?.check(now)?.connectable == true
    } == true
    private fun grant(ref: String, now: Long): Grant {
        val g = grants[ref] ?: throw VaultException(VaultException.Kind.LOCKED)
        if (g.session == null && (now < g.issuedAt || now - g.issuedAt > LAUNCH_WINDOW)) {
            grants.remove(ref); throw VaultException(VaultException.Kind.LOCKED)
        }
        return g
    }
    fun requireAllowed(decision: VaultQuotaPolicy.Decision) {
        require(decision.connectable) { when (decision.status) {
            EntitlementStatus.QUOTA_EXHAUSTED -> "حجم این دسترسی به پایان رسیده است."
            EntitlementStatus.EXPIRED -> "زمان این دسترسی به پایان رسیده است."
            else -> "وضعیت دسترسی یا آخرین ثبت مصرف قابل تأیید نیست؛ اتصال قفل است."
        } }
    }
}

/** Consumes cumulative counters for exactly one engine generation. Counter resets are charged
 * as a new generation, not negative usage. A stale/closed session can never activate or charge.
 * Local polling is not a server-side hard cap; the interval and OS scheduling allow overshoot.
 */
class VaultMeteredSession(private val entry: VaultEntry, private val ledger: VaultUsageLedger, now: Long, private val monotonicMillis: () -> Long = { System.nanoTime()/1_000_000 }) {
    private val startWall = now
    private val startMonotonic = monotonicMillis()
    private var lastGeneration: String? = null
    private var lastUpload = 0L
    private var lastDownload = 0L
    private var lastClock = now
    @Volatile var active = true
        private set
    private var accountingVerified = true
    init { VaultRuntime.requireAllowed(check(now));ledger.begin(entry.usageId) }
    @Synchronized fun invalidate() { accountingVerified=false }
    @Synchronized fun check(now: Long): VaultQuotaPolicy.Decision {
        check(active) { "نشست صندوق بسته شده است." }
        // A backwards wall-clock change in this session must not extend its lifetime.
        lastClock = maxOf(now, lastClock, VaultQuotaPolicy.saturatedAdd(startWall,(monotonicMillis()-startMonotonic).coerceAtLeast(0)))
        return VaultQuotaPolicy.local(entry, ledger.read(entry.usageId), lastClock)
    }
    @Synchronized fun sample(upload: Long, download: Long, now: Long, successful: Boolean = false, generation: String? = null): VaultQuotaPolicy.Decision {
        check(active) { "نشست صندوق بسته شده است." }
        require(upload >= 0 && download >= 0)
        lastClock = maxOf(now, lastClock, VaultQuotaPolicy.saturatedAdd(startWall,(monotonicMillis()-startMonotonic).coerceAtLeast(0)))
        if (generation != lastGeneration) { lastUpload = 0; lastDownload = 0 }
        val up = if (upload >= lastUpload) upload - lastUpload else upload
        val down = if (download >= lastDownload) download - lastDownload else download
        // Persist first. On I/O failure the caller must stop the engine, never advance counters.
        // A verified probe OR actual routed payload starts validity. Otherwise a
        // blocked probe endpoint could leave a working data path unmetered in time.
        val usage = ledger.record(entry.usageId, up, down, lastClock, connected = successful || up > 0 || down > 0)
        lastUpload = upload; lastDownload = download; lastGeneration = generation
        return VaultQuotaPolicy.local(entry, usage, lastClock)
    }
    @Synchronized fun close() { if(!active)return;active=false;ledger.finish(entry.usageId,accountingVerified) }
}
