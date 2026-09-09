package net.gozar.app.configcenter

import net.gozar.app.configtoolkit.ConfigInput
import net.gozar.app.configtoolkit.DecoderRegistry
import java.security.MessageDigest
import java.util.Locale

/**
 * Bridges the Config Center onto the existing toolkit pipeline: Decoders +
 * FormatDetector + ProfileValidator. Links become NormalizedProfiles through
 * the same code path the toolkit uses, then deduplicate by content hash.
 */
object ConfigNormalizer {

    enum class Outcome { VALID, DUPLICATE, INVALID, UNSUPPORTED }

    data class Item(
        val link: String,
        val hash: String,
        val protocol: String,
        val outcome: Outcome,
        val profile: net.gozar.app.configtoolkit.NormalizedProfile? = null,
        val note: String = ""
    )

    data class Result(
        val items: List<Item>,
        val validCount: Int = items.count { it.outcome == Outcome.VALID },
        val duplicateCount: Int = items.count { it.outcome == Outcome.DUPLICATE },
        val invalidCount: Int = items.count { it.outcome == Outcome.INVALID },
        val unsupportedCount: Int = items.count { it.outcome == Outcome.UNSUPPORTED }
    )

    private val SUPPORTED = setOf("vless", "vmess", "trojan", "shadowsocks", "socks")

    /** Extracts and normalizes every config from [bytes] via the toolkit decoders. */
    fun normalizeFile(fileName: String?, bytes: ByteArray, knownHashes: MutableSet<String>): Result {
        val toolkitResult = runCatching {
            DecoderRegistry().decode(ConfigInput(bytes, displayName = fileName ?: "config"))
        }.getOrNull()
        val profiles = toolkitResult?.profiles.orEmpty()

        val items = mutableListOf<Item>()
        val linksSeen = mutableSetOf<String>()
        profiles.forEach { profile ->
            val link = runCatching {
                net.gozar.app.configtoolkit.V2RayLinkGenerator.generate(profile)
            }.getOrNull() ?: ""
            val hash = if (link.isNotBlank()) sha256(link) else sha256("${profile.protocol}|${profile.server}|${profile.port}|${profile.uuid}|${profile.password}")
            val outcome = when {
                hash in knownHashes -> Outcome.DUPLICATE
                else -> { knownHashes += hash; Outcome.VALID }
            }
            linksSeen += link
            items += Item(link, hash, profile.protocol.lowercase(Locale.US), outcome, profile = profile)
        }

        // Raw links from generic extraction that the toolkit containers missed.
        ConfigExtractor.extract(fileName, bytes).links.forEach { link ->
            if (link in linksSeen) return@forEach
            val protocol = link.substringBefore("://").lowercase(Locale.US)
            val hash = sha256(link)
            val outcome = when {
                protocol !in SUPPORTED -> Outcome.UNSUPPORTED
                hash in knownHashes -> Outcome.DUPLICATE
                else -> { knownHashes += hash; Outcome.VALID }
            }
            items += Item(link, hash, protocol, outcome, note = if (outcome == Outcome.UNSUPPORTED) "هسته فعلی این پروتکل را اجرا نمی‌کند" else "")
        }

        return Result(items)
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(24)
}
