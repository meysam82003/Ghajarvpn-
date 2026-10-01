package net.gozar.app

/** Legacy port identifier retained for diagnostics. Phase 5 PhoneSharing owns the authenticated relay. */
object HttpSharePort {
    @Volatile
    var value: Int = 18686
}
