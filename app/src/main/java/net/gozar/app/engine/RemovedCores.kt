package net.gozar.app.engine

import net.gozar.app.ProxyConfig

/**
 * Protocols whose engines were taken out of the base app in 1.1.1 (the
 * standalone DNS tunnels and Juicity). Their profiles are kept exactly as they
 * were - they still import, back up, restore, share and can be edited - but
 * they cannot be connected or tested here, and every place that would try says
 * so with [MESSAGE] instead of failing in some other core.
 */
object RemovedCores {
    val PROTOCOLS = setOf("dnstt", "vaydns", "noizdns", "slipstream", "masterdns", "stormdns", "cottendns", "juicity")

    const val MESSAGE = "این روش در نسخه سبک 1.1.1 داخل برنامه اصلی موجود نیست."
    const val MESSAGE_EN = "This method is not included in the lightweight 1.1.1 app."

    fun isRemoved(config: ProxyConfig): Boolean = config.protocol in PROTOCOLS
    fun isRemoved(protocol: String): Boolean = protocol in PROTOCOLS
}
