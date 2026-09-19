package net.gozar.app

/**
 * Best-effort discovery of this device's own hotspot/AP interface IPv4
 * address (typically 192.168.43.1 or 192.168.49.1 depending on OEM); null
 * if no such interface is currently up (hotspot off, or the user is trying
 * to share over a plain Wi-Fi client connection instead of a phone-hosted
 * hotspot - not supported, same as before).
 *
 * Deliberately excludes every other interface - Wi-Fi station, cellular
 * data, the VPN's own tun. VPN Share's inbounds must only ever be reachable
 * on this exact interface; ConfigBuilder binds them here instead of to the
 * wildcard 0.0.0.0, which would also accept connections arriving on the
 * cellular interface. Some carriers/networks hand a phone a real, routable
 * IPv4 (or IPv6) address there, which would turn an unauthenticated local
 * sharing proxy into one reachable from the public internet the whole time
 * VPN Share is toggled on and connected - regardless of whether a hotspot
 * was ever actually turned on.
 */
internal fun hotspotInterfaceAddress(): String? = runCatching {
    java.net.NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .filter { iface -> isHotspotInterfaceName(iface.name) }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<java.net.Inet4Address>()
        .firstOrNull()?.hostAddress
}.getOrNull()

/** Split out from [hotspotInterfaceAddress] so the name allow-list itself -
 * the part that actually decides which interface VPN Share's inbounds may
 * bind to - is testable without a real NetworkInterface (a JVM unit test
 * has no network stack to enumerate). Must never match a cellular data
 * interface (rmnet*, ccmni*, cellular titles) or the normal Wi-Fi station
 * interface (wlan0), only a phone-hosted hotspot's own interface. */
internal fun isHotspotInterfaceName(name: String): Boolean =
    name.startsWith("ap") || name.startsWith("wlan1") || name.contains("swlan")
