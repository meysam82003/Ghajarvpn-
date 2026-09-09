package net.gozar.app.freecfg

import net.gozar.app.configcenter.ConfigNormalizer

/**
 * Free-config pipeline: source fetch (real integration point) -> message
 * extraction -> decode via the universal engine -> normalize -> dedupe ->
 * rank. Telegram message texts arrive here; fetching itself is delegated to
 * [TelegramWebFetcher] which requires no credentials for public web previews.
 */
object FreeConfigPipeline {

    /** password/رمز patterns for locked files, per the source-message contract. */
    private val PASSWORD_PATTERNS = listOf(
        Regex("(?i)password\\s*[:=]\\s*(\\S+)"),
        Regex("(?i)\\bpass\\s*[:=]\\s*(\\S+)"),
        Regex("(?i)رمز\\s*(?:عبور)?\\s*[:=]\\s*(\\S+)"),
        Regex("(?i)پسورد\\s*(?:فایل)?\\s*[:=]\\s*(\\S+)")
    )

    fun passwordFromContext(caption: String?, message: String?, grouped: List<String>? = null): String? {
        for (scope in listOf(caption, message)) {
            scope?.let { text ->
                PASSWORD_PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
                    ?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        grouped?.forEach { text ->
            PASSWORD_PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** One processed item after dedupe; metadata kept for debug/health only. */
    data class Item(
        val link: String,
        val hash: String,
        val outcome: ConfigNormalizer.Outcome,
        val sourceId: String,
        val messageId: Long,
        val fetchedAt: Long,
        val contentHash: String,
        val country: String = "",
        val protocol: String = "",
        val name: String = ""
    )

    data class Report(
        val items: List<Item>,
        val found: Int,
        val valid: Int,
        val duplicates: Int,
        val invalid: Int,
        val locked: Int,
        val skippedSources: List<String>
    )

    interface Fetcher {
        /** Returns raw message texts (and captions) of the source, newest first. */
        suspend fun fetchMessages(source: FreeSource): List<TelegramMessage>
    }

    data class TelegramMessage(
        val id: Long,
        val text: String,
        val caption: String? = null,
        val attachments: List<ByteArray> = emptyList(),
        val attachmentNames: List<String> = emptyList()
    )

    fun extractFromMessages(source: FreeSource, messages: List<TelegramMessage>, knownHashes: MutableSet<String>): Report {
        val items = mutableListOf<Item>()
        var found = 0; var valid = 0; var dup = 0; var invalid = 0; var locked = 0
        val now = System.currentTimeMillis()
        messages.forEach { message ->
            val bodies = buildList {
                add(message.text)
                message.caption?.let { add(it) }
            }
            val password = passwordFromContext(message.caption, message.text)
            message.attachments.forEachIndexed { index, bytes ->
                val extracted = net.gozar.app.configcenter.ConfigExtractor.extract(message.attachmentNames.getOrNull(index), bytes)
                val resolved = when {
                    !extracted.locked -> null
                    password != null -> runCatching {
                        val decrypted = PasswordedContainer.tryDecrypt(bytes, password)
                        decrypted?.let { net.gozar.app.configcenter.ConfigExtractor.extract(message.attachmentNames.getOrNull(index), it) }
                    }.getOrNull() ?: extracted
                    else -> extracted
                }
                if (resolved.locked) { locked++; return@forEachIndexed }
                val result = ConfigNormalizer.normalizeFile(message.attachmentNames.getOrNull(index), bytesOf(resolved, bytes), knownHashes)
                result.items.forEach { item ->
                    found++
                    when (item.outcome) {
                        ConfigNormalizer.Outcome.VALID -> valid++
                        ConfigNormalizer.Outcome.DUPLICATE -> dup++
                        ConfigNormalizer.Outcome.INVALID -> invalid++
                        ConfigNormalizer.Outcome.UNSUPPORTED -> invalid++
                    }
                    if (item.outcome == ConfigNormalizer.Outcome.VALID) {
                        items += Item(item.link, item.hash, item.outcome, source.id, message.id, now,
                            item.hash, countryOf(item.profile?.name ?: item.link), item.protocol, item.profile?.name ?: "")
                    }
                }
            }
            bodies.forEach { body ->
                val result = net.gozar.app.configcenter.ConfigExtractor.extractText(body)
                if (result.locked) { locked++; return@forEach }
                if (result.links.isEmpty()) return@forEach
                val normalized = ConfigNormalizer.normalize(result.links, knownHashes)
                normalized.items.forEach { item ->
                    found++
                    when (item.outcome) {
                        ConfigNormalizer.Outcome.VALID -> valid++
                        ConfigNormalizer.Outcome.DUPLICATE -> dup++
                        ConfigNormalizer.Outcome.INVALID -> invalid++
                        ConfigNormalizer.Outcome.UNSUPPORTED -> invalid++
                    }
                    if (item.outcome == ConfigNormalizer.Outcome.VALID) {
                        items += Item(item.link, item.hash, item.outcome, source.id, message.id, now,
                            item.hash, countryOf(item.profile?.name ?: item.link), item.protocol, item.profile?.name ?: "")
                    }
                }
            }
        }
        return Report(dedupe(items), found, valid, dup, invalid, locked, emptyList())
    }

    private fun bytesOf(extraction: net.gozar.app.configcenter.ConfigExtractor.Extraction, original: ByteArray): ByteArray =
        if (extraction.rawText.isNotBlank()) extraction.rawText.toByteArray() else original

    private fun countryOf(text: String): String {
        val flags = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")
        return flags.find(text)?.value ?: ""
    }

    /** Content-level dedupe across sources: same hash collapses to one item. */
    fun dedupe(items: List<Item>): List<Item> = items.distinctBy { it.hash }

    /** Rank: recent success first, protocol diversity after. Health tests join in later. */
    fun rank(items: List<Item>): List<Item> =
        items.sortedWith(compareByDescending<Item> { it.fetchedAt }.thenBy { it.sourceId })
}
