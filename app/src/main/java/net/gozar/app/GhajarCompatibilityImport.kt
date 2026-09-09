package net.gozar.app

import android.net.Uri
import android.util.Base64

/** Imports public Happ/Xray-compatible links without depending on proprietary encryption keys. */
object GhajarCompatibilityImport {
    fun parseDeepLink(raw: String): List<ProxyConfig> {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return emptyList()
        val candidates = buildList {
            listOf("url", "config", "subscription", "data", "link").forEach { key ->
                uri.getQueryParameter(key)?.takeIf { it.isNotBlank() }?.let(::add)
            }
            uri.fragment?.takeIf { it.isNotBlank() }?.let(::add)
            uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() && it != "add" }?.let(::add)
        }
        return candidates.asSequence()
            .flatMap { candidate ->
                sequenceOf(candidate, decodeBase64(candidate)).filterNotNull()
            }
            .map { Uri.decode(it).trim() }
            .map { ConfigParser.parseBundle(it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .map { it.copy(name = BrandConfig.sanitizePublicText(it.name)) }
    }

    private fun decodeBase64(value: String): String? {
        val packed = value.filterNot(Char::isWhitespace)
        val padded = packed + "=".repeat((4 - packed.length % 4) % 4)
        return listOf(Base64.URL_SAFE, Base64.DEFAULT).firstNotNullOfOrNull { mode ->
            runCatching { String(Base64.decode(padded, mode), Charsets.UTF_8) }.getOrNull()
        }
    }
}
