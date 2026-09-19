package net.gozar.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The resolvers the user has imported, and what the last scan found out about
 * them.
 *
 * A file rather than SharedPreferences. A thousand resolvers with a verdict
 * each is a few hundred kilobytes, and SharedPreferences holds its entire
 * contents in memory, parses it on the main thread on first touch, and rewrites
 * the whole XML document on every commit. The imported list is also the one
 * thing here that a user would be annoyed to lose, so writes go to a temporary
 * file and are renamed over the real one - a rename is atomic, a half-written
 * JSON array is not.
 *
 * Nothing in here leaves the phone. The imported file is not uploaded, the
 * addresses are not reported anywhere, and the export is a local share the
 * user starts themselves.
 */
class DnsResolverStore private constructor(context: Context) {

    private val app = context.applicationContext
    private val file = File(app.filesDir, "dns_resolvers.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _resolvers = MutableStateFlow<List<DnsResolver>>(emptyList())
    val resolvers: StateFlow<List<DnsResolver>> = _resolvers.asStateFlow()

    private val _verdicts = MutableStateFlow<Map<String, DnsVerdict>>(emptyMap())
    val verdicts: StateFlow<Map<String, DnsVerdict>> = _verdicts.asStateFlow()

    /**
     * The resolver the user chose to actually use, if any.
     *
     * Held here rather than in ConfigStore because it is only meaningful
     * alongside this list - and because a manual choice surviving a reconnect
     * means it has to be stored next to the thing it names, not re-derived
     * from a scan that may since have found something faster.
     */
    private val _chosenId = MutableStateFlow<String?>(null)
    val chosenId: StateFlow<String?> = _chosenId.asStateFlow()

    /**
     * The choice was the user's own, not the app's.
     *
     * The distinction is load-bearing: automatic failover must never move off
     * a resolver somebody picked by hand, and without this flag "chosen"
     * cannot tell a deliberate pick from one the app made two minutes ago.
     */
    private val _chosenManually = MutableStateFlow(false)
    val chosenManually: StateFlow<Boolean> = _chosenManually.asStateFlow()

    /**
     * Whether the stored choice was manual, read during [load].
     *
     * A field rather than a fourth element of the Triple: the Triple is
     * already at the edge of readable, and this value is only ever written by
     * the read and consumed immediately after it.
     */
    private var manualOnDisk = true

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * Reads the file, or falls back to the built-in catalogue on first run.
     *
     * The catalogue is seeded rather than shown from a separate list, so a
     * user who imports nothing still has something to scan, and the known
     * resolvers sort and rank beside the imported ones instead of living in
     * their own section with their own rules.
     */
    suspend fun load() {
        if (_loaded.value) return
        val (list, verdicts, chosen) = withContext(Dispatchers.IO) { readFile() }
        _resolvers.value = list.ifEmpty { GhajarDnsLab.Catalogue }
        _verdicts.value = verdicts
        _chosenId.value = chosen
        _chosenManually.value = chosen != null && manualOnDisk
        _loaded.value = true
        DnsScanEngine.seed(verdicts)
    }

    private fun readFile(): Triple<List<DnsResolver>, Map<String, DnsVerdict>, String?> {
        if (!file.exists()) return Triple(emptyList(), emptyMap(), null)
        val text = runCatching { file.readText() }.getOrNull()
            ?: return Triple(emptyList(), emptyMap(), null)
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return Triple(emptyList(), emptyMap(), null)

        val list = ArrayList<DnsResolver>()
        val array = root.optJSONArray("resolvers") ?: JSONArray()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val transport = runCatching { DnsTransport.valueOf(o.optString("transport")) }
                .getOrNull() ?: continue
            val address = o.optString("address")
            if (address.isBlank()) continue
            list += DnsResolver(
                name = o.optString("name", address),
                transport = transport,
                address = address,
                note = o.optString("note", ""),
                local = o.optBoolean("local", false)
            )
        }

        val verdicts = HashMap<String, DnsVerdict>()
        val vArray = root.optJSONArray("verdicts") ?: JSONArray()
        for (i in 0 until vArray.length()) {
            val o = vArray.optJSONObject(i) ?: continue
            val v = DnsVerdict.fromJson(o)
            if (v.id.isNotBlank()) verdicts[v.id] = v
        }

        manualOnDisk = root.optBoolean("chosenManually", true)
        return Triple(
            list,
            verdicts,
            root.optString("chosen", "").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Adds an import's results, keeping what is already there.
     *
     * The report has already deduplicated against [resolvers]; this re-checks
     * rather than trusting it, because an import running while a second one
     * finishes would otherwise be able to insert a duplicate.
     */
    fun add(incoming: List<DnsResolver>) {
        if (incoming.isEmpty()) return
        val have = _resolvers.value.mapTo(HashSet()) { it.id }
        val merged = _resolvers.value + incoming.filter { have.add(it.id) }
        _resolvers.value = merged
        persist()
    }

    fun addManual(resolver: DnsResolver): Boolean {
        if (_resolvers.value.any { it.id == resolver.id }) return false
        _resolvers.value = _resolvers.value + resolver
        persist()
        return true
    }

    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        _resolvers.value = _resolvers.value.filterNot { it.id in ids }
        _verdicts.value = _verdicts.value - ids
        if (_chosenId.value in ids) {
            _chosenId.value = null
            _chosenManually.value = false
        }
        persist()
    }

    /**
     * Throws away everything that is not healthy.
     *
     * "Save the good ones" as a verb, which after a thousand-resolver scan is
     * the only way the list becomes something a person can read. Untested
     * entries are kept: discarding them would delete the ones the scan was
     * stopped before reaching.
     */
    fun keepHealthy() {
        val verdicts = _verdicts.value
        val keep = _resolvers.value.filter { r ->
            val v = verdicts[r.id]
            v == null || v.health == DnsHealth.GOOD || v.health == DnsHealth.UNTESTED
        }
        if (keep.size == _resolvers.value.size) return
        val kept = keep.mapTo(HashSet()) { it.id }
        _resolvers.value = keep
        _verdicts.value = verdicts.filterKeys { it in kept }
        if (_chosenId.value !in kept) {
            _chosenId.value = null
            _chosenManually.value = false
        }
        persist()
    }

    fun clearAll() {
        _resolvers.value = emptyList()
        _verdicts.value = emptyMap()
        _chosenId.value = null
        _chosenManually.value = false
        persist()
    }

    /** Restores the built-in catalogue without touching imported entries. */
    fun restoreCatalogue() {
        val have = _resolvers.value.mapTo(HashSet()) { it.id }
        val missing = GhajarDnsLab.Catalogue.filter { it.id !in have }
        if (missing.isEmpty()) return
        _resolvers.value = _resolvers.value + missing
        persist()
    }

    fun putVerdicts(batch: Map<String, DnsVerdict>) {
        if (batch.isEmpty()) return
        _verdicts.value = _verdicts.value + batch
        persist()
    }

    /**
     * Records which resolver is in use.
     *
     * [manual] says whether a person picked it. Automatic selection passes
     * false and is then free to move on; a manual pick is left alone until the
     * user clears it.
     */
    fun choose(id: String?, manual: Boolean = true) {
        _chosenId.value = id
        _chosenManually.value = id != null && manual
        persist()
    }

    fun resolverOf(id: String?): DnsResolver? =
        if (id == null) null else _resolvers.value.firstOrNull { it.id == id }

    /**
     * The best candidate for automatic selection, or null.
     *
     * Deliberately strict, and the strictness is the feature. Only GOOD, never
     * poisoned, never mismatched, and recursion actually confirmed - a
     * resolver that answers quickly and lies is the worst thing this could
     * pick, and latency alone would pick it first.
     *
     * [tunnelOnly] narrows it to resolvers whose tunnel path was tested and
     * worked. Answering an ordinary query says nothing about carrying a
     * tunnel, so the two selections cannot share one predicate.
     */
    fun bestCandidate(tunnelOnly: Boolean = false): DnsResolver? {
        val verdicts = _verdicts.value
        return _resolvers.value
            .mapNotNull { r -> verdicts[r.id]?.let { r to it } }
            .filter { (_, v) ->
                v.health == DnsHealth.GOOD && !v.poisoned && !v.mismatched &&
                    v.recursive == true && v.latencyMs != null &&
                    (!tunnelOnly || v.tunnelReady == true)
            }
            // Success rate first, latency second. A resolver that answers
            // nine times in ten at 60ms is better to hold a connection open
            // through than one that answers six times in ten at 20ms, and
            // sorting by latency alone gets that backwards.
            .sortedWith(
                compareByDescending<Pair<DnsResolver, DnsVerdict>> { it.second.successPercent }
                    .thenBy { it.second.latencyMs ?: Int.MAX_VALUE }
            )
            .firstOrNull()?.first
    }

    /**
     * The list as text the user can keep.
     *
     * One resolver per line with its numbers as a trailing comment, so the
     * file both re-imports through this app's own parser (which skips the
     * comment) and reads as a report.
     */
    fun exportText(): String {
        val verdicts = _verdicts.value
        val sb = StringBuilder()
        sb.append("# GhajarVPN DNS laboratory export\n")
        sb.append("# ").append(_resolvers.value.size).append(" resolvers\n")
        _resolvers.value.forEach { r ->
            val v = verdicts[r.id]
            sb.append(r.address)
            if (v != null && v.health != DnsHealth.UNTESTED) {
                sb.append("    # ").append(r.transport.name)
                sb.append(" ").append(v.health.name)
                v.latencyMs?.let { sb.append(" ").append(it).append("ms") }
                sb.append(" ").append(v.successPercent).append("%ok")
                if (v.poisoned) sb.append(" POISONED")
                if (v.recursive == false) sb.append(" no-recursion")
                if (v.tunnelReady == true) sb.append(" tunnel-ok")
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun persist() {
        val resolvers = _resolvers.value
        val verdicts = _verdicts.value
        val chosen = _chosenId.value
        val manual = _chosenManually.value
        scope.launch {
            writeLock.withLock {
                runCatching {
                    val root = JSONObject()
                    val array = JSONArray()
                    resolvers.forEach { r ->
                        array.put(
                            JSONObject()
                                .put("name", r.name)
                                .put("transport", r.transport.name)
                                .put("address", r.address)
                                .put("note", r.note)
                                .put("local", r.local)
                        )
                    }
                    root.put("resolvers", array)
                    val vArray = JSONArray()
                    verdicts.values.forEach { vArray.put(it.toJson()) }
                    root.put("verdicts", vArray)
                    if (chosen != null) {
                        root.put("chosen", chosen)
                        root.put("chosenManually", manual)
                    }

                    // Rename over, never write in place: a process killed
                    // mid-write would otherwise leave a truncated array and
                    // the whole list would be unreadable on next open.
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(root.toString())
                    if (!tmp.renameTo(file)) {
                        file.writeText(root.toString())
                        tmp.delete()
                    }
                }.onFailure {
                    GhajarLog.e("GhajarDnsStore", "could not save: ${it.javaClass.simpleName}")
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: DnsResolverStore? = null

        fun get(context: Context): DnsResolverStore =
            instance ?: synchronized(this) {
                instance ?: DnsResolverStore(context).also { instance = it }
            }
    }
}
