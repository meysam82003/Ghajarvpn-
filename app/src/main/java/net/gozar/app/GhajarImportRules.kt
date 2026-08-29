package net.gozar.app

import android.net.Uri

internal object GhajarImportRules {
    fun isNpvt(name: String?): Boolean = name?.trim()?.endsWith(".npvt", ignoreCase = true) == true

    fun readableText(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024 || bytes.any { it == 0.toByte() }) return null
        return runCatching { bytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trim() }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** A text file containing exactly one HTTP(S) URL is a subscription import,
     * not a raw proxy configuration. Embedded credentials are rejected. */
    fun subscriptionUrl(text: String?): String? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() && !it.contains('\n') && !it.contains('\r') }
            ?: return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return value
    }
}
