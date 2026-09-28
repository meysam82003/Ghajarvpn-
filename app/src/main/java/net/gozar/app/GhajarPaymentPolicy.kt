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

    /**
     * A page the checkout itself navigates to after it opened on a trusted
     * host. Gateways redirect through bank and PSP domains nobody can list in
     * advance (the fixed list kept failing real payments with "not in the
     * payment domain list"), so inside the flow any plain HTTPS page is
     * allowed, as in a browser. Credentials in the URL, other ports and every
     * non-HTTPS scheme stay refused. The first URL still has to pass [allows].
     */
    fun allowsInFlow(value: String): Boolean {
        val uri = try { URI(value) } catch (_: Exception) { return false }
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return host.contains('.') && host != "localhost"
    }
}
