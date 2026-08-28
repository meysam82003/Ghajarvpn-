package net.gozar.app

import java.net.InetAddress
import java.util.Locale

data class IpLocation(
    val ip: String,
    val city: String,
    val country: String,
    val countryCode: String,
    val lat: Double,
    val lon: Double
)

internal data class GhajarLocationSession(
    val connection: Connection,
    val activeId: String?,
    val connectedAt: Long,
    val locked: Boolean,
    val nativeTunnel: Boolean
)

internal data class GhajarLocationSnapshot(
    val session: GhajarLocationSession? = null,
    val ip: String = "",
    val location: IpLocation? = null,
    val loading: Boolean = false
) {
    fun forSession(current: GhajarLocationSession): GhajarLocationSnapshot =
        if (session == current) this else GhajarLocationSnapshot(session = current)
}

internal object GhajarLocationRules {
    // Only numeric addresses reach InetAddress; never perform DNS for provider input.
    fun numericIp(value: String): String? {
        val ip = value.trim()
        if (ip.isEmpty() || ip.length > 45) return null
        if (':' in ip) {
            if (!ip.matches(Regex("[0-9a-fA-F:.]+"))) return null
        } else {
            val parts = ip.split('.')
            if (parts.size != 4 || parts.any {
                    it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } ||
                        it.toInt() !in 0..255 || (it.length > 1 && it.startsWith('0'))
                }) return null
        }
        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return null
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return null
        return ip
    }

    fun sameIp(first: String, second: String): Boolean {
        if (numericIp(first) == null || numericIp(second) == null) return false
        return InetAddress.getByName(first) == InetAddress.getByName(second)
    }

    fun validate(location: IpLocation, expectedIp: String? = null): IpLocation? {
        val ip = numericIp(location.ip) ?: return null
        if (expectedIp != null && !sameIp(ip, expectedIp)) return null
        if (!location.lat.isFinite() || !location.lon.isFinite() ||
            location.lat !in -90.0..90.0 || location.lon !in -180.0..180.0) return null
        val code = location.countryCode.uppercase(Locale.ROOT)
        if (code !in Locale.getISOCountries() && code != "XK") return null
        return location.copy(ip = ip, countryCode = code,
            city = location.city.filterNot(Char::isISOControl).take(100),
            country = location.country.filterNot(Char::isISOControl).take(100))
    }

    fun flag(code: String): String = if (code.length == 2 && code.all { it in 'A'..'Z' })
        code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("") else ""
}
