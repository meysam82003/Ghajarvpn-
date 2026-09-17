package net.gozar.app.freecfg

import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches public Telegram channel/group previews through t.me/s/<name>. Group
 * or channel makes no difference — the public preview exposes admin posts in
 * both. This is the REAL integration point: no fake data. When a network is
 * unreachable the source simply records a failure and other sources continue.
 */
object TelegramWebFetcher : FreeConfigPipeline.Fetcher {

    private const val TIMEOUT_MS = 12_000

    override suspend fun fetchMessages(source: FreeSource): List<FreeConfigPipeline.TelegramMessage> {
        val url = source.endpoint.takeIf { it.startsWith("https://t.me/s/") }
            ?: throw IllegalArgumentException("unsupported endpoint: ${source.endpoint}")
        val html = withTimeout { httpGet(url) } ?: throw IllegalStateException("fetch failed")
        return parsePreview(html)
    }

    internal suspend fun withTimeout(block: () -> String?): String? =
        kotlinx.coroutines.withTimeoutOrNull(TIMEOUT_MS.toLong()) { block() }

    internal fun httpGet(url: String): String? = runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) GhajarVPN")
        val code = connection.responseCode
        if (code !in 200..299) { connection.disconnect(); return null }
        connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.also { connection.disconnect() }
    }.getOrNull()

    /** Parses the server-rendered preview into messages (id + text + captions). */
    internal fun parsePreview(html: String): List<FreeConfigPipeline.TelegramMessage> {
        val messages = mutableListOf<FreeConfigPipeline.TelegramMessage>()
        // Blocks look like: <div class="tgme_widget_message ..." data-post="name/12345" ...>
        val blockRegex = Regex(
            "<div[^>]*class=\"tgme_widget_message[^\"]*\"[^>]*data-post=\"[^\"]*?(\\d+)\"[^>]*>(.*?)</div>\\s*(?=<div[^>]*class=\"tgme_widget_message|$)",
            RegexOption.DOT_MATCHES_ALL
        )
        val textTag = Regex("<div[^>]*class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
        blockRegex.findAll(html).forEach { match ->
            val id = match.groupValues[1].toLongOrNull() ?: return@forEach
            val body = match.groupValues[2]
            val textNode = textTag.find(body)?.groupValues?.get(1)
            val text = stripTags(textNode ?: body)
            if (text.isNotBlank()) {
                messages += FreeConfigPipeline.TelegramMessage(id, text)
            }
        }
        return messages
    }

    private fun stripTags(html: String): String {
        val withBreaks = html.replace("<br/?>", "\n")
        return withBreaks
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .trim()
    }
}
