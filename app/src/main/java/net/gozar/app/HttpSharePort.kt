package net.gozar.app

/** Port for VPN Share's unauthenticated HTTP inbound (see ConfigBuilder) -
 * separate from MixedPort's SOCKS5 inbound, which requires a credential. */
object HttpSharePort {
    @Volatile
    var value: Int = 18686
}
