package net.gozar.app

import gozarcore.Gozarcore
import net.gozar.app.freecfg.FreeFeedRules
import net.gozar.app.freecfg.FreeFeedHttp
import net.gozar.app.freecfg.FreeSourceRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object FreeConfigs {
    const val CHANNEL = BrandConfig.FREE_CONFIG_CHANNEL
    const val SOURCE_URL = "https://t.me/s/$CHANNEL"
    const val CONFIG_NAME = "Ghajarvpn"
    const val BUSY = -1
    const val UNREACHABLE = -2
    const val NO_CONFIGS = -3
    const val MAX_LATENCY_MS = 2500
    data class Progress(val tested: Int, val total: Int, val alive: Int, val pages: Int = 0, val collecting: Boolean = true)
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _incomplete = MutableStateFlow(false)
    val incomplete: StateFlow<Boolean> = _incomplete.asStateFlow()
    private val refreshLock = Mutex()
    fun subscriptionOf(store: ConfigStore): Subscription? = store.subscriptions.value.firstOrNull { it.url == SOURCE_URL }
    fun isAdded(store: ConfigStore): Boolean = subscriptionOf(store) != null
    private fun route(): Proxy = if (VpnState.state.value == Connection.CONNECTED && !IkeController.active)
        Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value)) else Proxy.NO_PROXY
    suspend fun refreshMultiSource(store: ConfigStore, label: String): Int = refresh(store, label)

    /** Bounded workers consume every unique candidate from the last 72 hours.
     * Healthy results appear during scanning. Only a complete scan replaces the
     * whole subscription; an unreachable feed cannot erase its previous entries. */
    suspend fun refresh(store: ConfigStore, label: String): Int {
        if (!refreshLock.tryLock()) return BUSY
        _busy.value = true; _incomplete.value = false
        _progress.value = Progress(0, 0, 0)
        val started = System.currentTimeMillis()
        val existing = subscriptionOf(store)
        val sub = existing ?: Subscription(name = "@Ghajarvpn", url = SOURCE_URL)
        val previous = store.configs.value.filter { it.subId == sub.id }
        val candidates = ConcurrentHashMap.newKeySet<String>()
        val urls = ConcurrentHashMap.newKeySet<String>()
        val completed = ConcurrentHashMap.newKeySet<String>()
        val passed = ConcurrentHashMap<String, Pair<ProxyConfig, Int>>()
        val tested = AtomicInteger(); val pages = AtomicInteger(); val reachable = AtomicInteger(); val failures = AtomicInteger()
        val collecting = java.util.concurrent.atomic.AtomicBoolean(true)
        val publishLock = Mutex()
        var lastPublish = 0L
        fun progress(fetching: Boolean = collecting.get()) {
            _progress.value = Progress(tested.get(), candidates.size, passed.size, pages.get(), fetching)
        }
        suspend fun publish(final: Boolean) = withContext(Dispatchers.Main) {
            publishLock.lock()
            try {
                val now = System.currentTimeMillis()
                if (!final && (passed.isEmpty() || now - lastPublish < 1500)) return@withContext
                lastPublish = now
                val healthy = passed.values.sortedBy { it.second }.map { it.first }
                val combined = FreeFeedRules.reconcile(previous, healthy, completed, final && failures.get() == 0)
                // Keep refresh eligibility when some feeds could not be checked.
                val stamp = if (final && failures.get() == 0) now else existing?.lastUpdated ?: 0L
                store.upsertSubscription(sub.copy(name = "@Ghajarvpn", lastUpdated = stamp), combined)
            } finally { publishLock.unlock() }
        }
        try {
            coroutineScope {
                val configs = Channel<ProxyConfig>(64)
                val subscriptions = Channel<String>(32)
                suspend fun offer(cfg: ProxyConfig) {
                    if (cfg.address.isNotBlank() && cfg.port in 1..65535 && candidates.add(FreeFeedRules.signature(cfg))) {
                        configs.send(cfg); progress()
                    }
                }
                val testers = List(8) { launch(Dispatchers.IO) {
                    for (cfg in configs) {
                        ensureActive()
                        val ms = try { Gozarcore.measureDelayBounded(ConfigBuilder.buildForTest(cfg), 4000L) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { -1L }
                        if (ms in 0L..MAX_LATENCY_MS.toLong()) passed[FreeFeedRules.signature(cfg)] = cfg to ms.toInt()
                        completed.add(FreeFeedRules.signature(cfg))
                        tested.incrementAndGet(); progress()
                        publish(false)
                    }
                } }
                val expanders = List(4) { launch(Dispatchers.IO) {
                    for (url in subscriptions) {
                        try { SubscriptionFetcher.parseBody(FreeFeedHttp.read(url, route()), ConfigSource.COMMUNITY).forEach { offer(it) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: SubscriptionError) { /* A message's web link may be an ordinary page. */ }
                        catch (_: Exception) { failures.incrementAndGet() }
                    }
                } }
                val feedSlots = Semaphore(3)
                FreeSourceRegistry.DEFAULT_SOURCES.filter { it.enabled }.map { source -> launch(Dispatchers.IO) {
                    feedSlots.withPermit {
                        var before = 0L
                        try {
                            while (true) {
                                ensureActive()
                                val url = source.endpoint + if (before == 0L) "" else "?before=$before"
                                val html = FreeFeedHttp.read(url, route())
                                val posts = FreeFeedRules.posts(html)
                                if (posts.isEmpty()) { failures.incrementAndGet(); break }
                                reachable.incrementAndGet(); pages.incrementAndGet(); progress()
                                val fresh = FreeFeedRules.recentPosts(posts, started)
                                for (post in fresh) {
                                    val links = FreeFeedRules.extract(post.html)
                                    for (link in links.configs) runCatching { ConfigParser.parse(link, ConfigSource.COMMUNITY) }.getOrNull()?.let { offer(it) }
                                    for (link in links.subscriptions) if (urls.add(link)) subscriptions.send(link)
                                }
                                if (posts.any { it.publishedAt == null }) failures.incrementAndGet()
                                if (posts.all { (it.publishedAt ?: Long.MAX_VALUE) < started - 72 * 60 * 60 * 1000L }) break
                                val oldest = posts.minOf { it.id }
                                if (oldest <= 1L) break
                                if (before != 0L && oldest >= before) { failures.incrementAndGet(); break }
                                before = oldest
                            }
                        } catch (e: CancellationException) { throw e }
                          catch (_: Exception) { failures.incrementAndGet() }
                    }
                } }.joinAll()
                subscriptions.close(); expanders.joinAll()
                collecting.set(false); configs.close(); progress(); testers.joinAll()
            }
            _incomplete.value = failures.get() > 0
            if (reachable.get() == 0) return UNREACHABLE
            if (candidates.isEmpty() && failures.get() > 0) return NO_CONFIGS
            publish(true)
            return passed.size
        } finally { _busy.value = false; _progress.value = null; refreshLock.unlock() }
    }
}
