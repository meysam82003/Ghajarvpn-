package net.gozar.app.security.vault

/** Process-local unlock state. No password, master key or plaintext is persisted by this class. */
class VaultSession(private val clock: ()->Long = { System.nanoTime()/1_000_000 }) {
    enum class AutoLock(val timeoutMs: Long?) { IMMEDIATELY(0), SECONDS_30(30000), MINUTE_1(60000), MINUTES_5(300000), BACKGROUND(null), SCREEN_LOCK(null) }
    private var entries: List<VaultEntry>?=null
    private var lastActivity=0L
    var autoLock=AutoLock.BACKGROUND
    @Synchronized fun unlock(values: List<VaultEntry>) { entries=values.toList(); lastActivity=clock() }
    @Synchronized fun snapshot(): List<VaultEntry> {
        expire(); return entries?.toList() ?: throw VaultException(VaultException.Kind.LOCKED)
    }
    @Synchronized fun touch() { expire(); if(entries!=null)lastActivity=clock() }
    @Synchronized fun background() { if(autoLock!=AutoLock.SCREEN_LOCK)lock() }
    @Synchronized fun screenLocked()=lock()
    @Synchronized fun lock() { entries=null; lastActivity=0 }
    private fun expire() {
        val timeout=autoLock.timeoutMs ?: return
        // Immediately means lock on leaving the screen/background, not during the unlock callback itself.
        if(timeout>0 && clock()-lastActivity>=timeout)lock()
    }
}
