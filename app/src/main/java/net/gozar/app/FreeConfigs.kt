package net.gozar.app

import gozarcore.Gozarcore
import net.gozar.app.freecfg.FreeFeedRules
import net.gozar.app.freecfg.FreeSourceRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object FreeConfigs {

    const val CHANNEL = BrandConfig.FREE_CONFIG_CHANNEL
    const val SOURCE_URL = "https://t.me/s/$CHANNEL"

    private const val TAG = "GhajarFree"
    private const val CONCURRENCY = 16
    private const val MESSAGES = 50
    private const val MAX_TEST = FreeFeedRules.LIMIT
    private const val MAX_SUBSCRIPTIONS = 12
    private const val MAX_PAGES = 4
    private const val HTTP_TIMEOUT = 12_000
    private const val MEASURE_TIMEOUT_MS = 20_000L

    const val CONFIG_NAME = "Ghajarvpn"


    private val LINK = Regex(
        "(?:vless|vmess|trojan|ss|hysteria2|hy2)://[^\\s\"'<>\\\\]+",
        RegexOption.IGNORE_CASE
    )
    private val POST_ID = Regex("data-post=\"[^/\"]+/(\\d+)\"")

    data class Progress(val tested: Int, val total: Int, val alive: Int)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val refreshLock = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun subscriptionOf(store: ConfigStore): Subscription? =
        store.subscriptions.value.firstOrNull { it.url == SOURCE_URL }

    fun isAdded(store: ConfigStore): Boolean = subscriptionOf(store) != null

    private fun route(): Proxy =
        if (VpnState.state.value == Connection.CONNECTED && !IkeController.active)
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value))
        else Proxy.NO_PROXY

    private fun unescape(raw: String): String = raw
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    private fun page(before: Long, sourceUrl: String = SOURCE_URL): String = runCatching {
        val url = if (before <= 0) sourceUrl else "$sourceUrl?before=$before"
        val conn = (URL(url).openConnection(route()) as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT
            readTimeout = HTTP_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            setRequestProperty("Accept-Language", "en")
        }
        val code = conn.responseCode
        val body = if (code in 200..299)
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    require(out.size() + n <= 4 * 1024 * 1024); out.write(buffer, 0, n) }
                val bytes = out.toByteArray()
                require(bytes.size <= 4 * 1024 * 1024)
                bytes.toString(Charsets.UTF_8)
            } else ""
        runCatching { conn.disconnect() }
        body
    }.getOrDefault("")

    data class Scrape(val reachable: Boolean, val messages: Int, val configs: List<ProxyConfig>)

    /** All configured feeds share one branded, deduplicated subscription. */
    suspend fun refreshMultiSource(store: ConfigStore, label: String): Int = refresh(store, label)

    private suspend fun scrapeSource(sourceUrl: String): Scrape = withContext(Dispatchers.IO) {
        val seen = LinkedHashMap<String, ProxyConfig>()
        var before = 0L
        var pages = 0
        var messages = 0
        var reachable = false
        var links = 0
        val subscriptions = linkedSetOf<String>()

        while (pages < MAX_PAGES && messages < MESSAGES) {
            currentCoroutineContext().ensureActive()
            val html = page(before, sourceUrl)
            if (html.isBlank()) break
            reachable = true

            val ids = POST_ID.findAll(html).mapNotNull { it.groupValues[1].toLongOrNull() }.toList()
            if (ids.isEmpty()) break
            val room = MESSAGES - messages
            val take = ids.sortedDescending().take(room).toSet()
            messages += take.size

            val extracted = FreeFeedRules.extract(html)
            extracted.subscriptions.forEach { subscriptions += it }
            extracted.configs.forEach { uri ->
                links++
                val cfg = runCatching { ConfigParser.parse(uri, ConfigSource.COMMUNITY) }.getOrNull()
                if (cfg != null && cfg.address.isNotBlank() && cfg.port in 1..65535) {
                    seen.putIfAbsent(sig(cfg), rename(cfg))
                }
            }

            val oldest = ids.minOrNull() ?: break
            if (before != 0L && oldest >= before) break
            before = oldest
            pages++
        }

        for (url in subscriptions.sortedByDescending { Regex("(?i)/(sub|subscription|api|s)/|[?&](token|sub)=").containsMatchIn(it) }.take(MAX_SUBSCRIPTIONS)) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                SubscriptionFetcher.fetchFull(url, ConfigSource.COMMUNITY, route(), strictTls = true)
                    .configs.forEach { cfg -> seen.putIfAbsent(sig(cfg), rename(cfg)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
              catch (_: Exception) { /* A bad subscription must not discard other posts. */ }
        }
        android.util.Log.i(
            TAG,
            "pages=$pages messages=$messages links=$links parsed=${seen.size} reachable=$reachable"
        )
        Scrape(reachable, messages, seen.values.toList())
    }

    private fun rename(cfg: ProxyConfig): ProxyConfig = cfg.copy(name = CONFIG_NAME)

    private fun sig(c: ProxyConfig): String = FreeFeedRules.signature(c)

    const val BUSY = -1
    const val UNREACHABLE = -2
    const val NO_CONFIGS = -3

    suspend fun refresh(store: ConfigStore, label: String): Int {
        if (!refreshLock.tryLock()) return BUSY
        _busy.value = true
        try {
            val feeds = coroutineScope {
                val sem = Semaphore(3)
                FreeSourceRegistry.DEFAULT_SOURCES.filter { it.enabled }.map { source ->
                    async { sem.withPermit { scrapeSource(source.endpoint) } }
                }.awaitAll()
            }
            val found = Scrape(feeds.any { it.reachable }, feeds.sumOf { it.messages },
                FreeFeedRules.select(feeds.map { it.configs }, MAX_TEST))
            if (!found.reachable) {
                android.util.Log.w(TAG, "channel unreachable")
                return UNREACHABLE
            }
            if (found.configs.isEmpty()) {
                android.util.Log.w(TAG, "no parsable configs in ${found.messages} messages")
                return NO_CONFIGS
            }
            val existing = subscriptionOf(store)
            val current = if (existing == null) emptyList()
            else store.configs.value.filter { it.subId == existing.id }

            val merged = found.configs.distinctBy { sig(it) }.take(MAX_TEST)
            android.util.Log.i(
                TAG,
                "kept=${current.size} scraped=${found.configs.size} merged=${merged.size}"
            )

            val alive = measureAll(merged)
            val sub = (existing ?: Subscription(name = label, url = SOURCE_URL))
                .copy(lastUpdated = System.currentTimeMillis())
            if (alive.isNotEmpty() || existing != null) store.upsertSubscription(sub.copy(name = "@Ghajarvpn"), alive.mapIndexed { index, cfg -> cfg.copy(name = "$CONFIG_NAME ${index + 1}") })
            android.util.Log.i(TAG, "kept ${alive.size} of ${found.configs.size}")
            return alive.size
        } finally {
            _busy.value = false
            _progress.value = null
            refreshLock.unlock()
        }
    }

    private suspend fun measureAll(configs: List<ProxyConfig>): List<ProxyConfig> = coroutineScope {
        val total = configs.size
        val alive = java.util.Collections.synchronizedList(mutableListOf<Pair<ProxyConfig, Int>>())
        var done = 0
        val lock = Any()
        _progress.value = Progress(0, total, 0)

        val sem = Semaphore(CONCURRENCY)
        configs.map { cfg ->
            launch {
                sem.withPermit {
                    val ms = withContext(Dispatchers.IO) {
                        runCatching {
                            kotlinx.coroutines.withTimeoutOrNull(MEASURE_TIMEOUT_MS) {
                                Gozarcore.measureDelay(ConfigBuilder.buildForTest(cfg))
                            } ?: -1L
                        }.getOrDefault(-1L)
                    }
                    if (ms >= 0) alive += cfg to ms.toInt()
                    else android.util.Log.d(TAG, "dead ${cfg.protocol} ${cfg.address}:${cfg.port}")
                    synchronized(lock) {
                        done++
                        _progress.value = Progress(done, total, alive.size)
                    }
                }
            }
        }.joinAll()

        alive.sortedBy { it.second }.map { it.first }
    }

}
