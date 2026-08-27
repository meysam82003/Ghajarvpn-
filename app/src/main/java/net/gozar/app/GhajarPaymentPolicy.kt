package net.gozar.app

import java.net.URI
import java.util.Locale

/** Pure URL policy, also used by tests; never accepts credentials in URLs. */
internal object GhajarPaymentPolicy {
    fun allows(value: String, issuedHost: String?, trusted: Set<String>): Boolean {
        val uri = try { URI(value) } catch (_: Exception) { return false }
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        val issued = issuedHost?.lowercase(Locale.ROOT)?.trimEnd('.')
        if (host == issued) return true
        return trusted.any { host == it || host.endsWith(".$it") }
    }
}
