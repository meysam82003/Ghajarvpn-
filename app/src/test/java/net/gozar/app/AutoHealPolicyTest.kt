package net.gozar.app

import org.junit.Assert.*
import org.junit.Test

class AutoHealPolicyTest {
    @Test fun recoverySequenceIsFiniteAndEscalates() {
        assertEquals(AutoHealAction.RETRY_CURRENT, AutoHealPolicy.step(0).action)
        assertEquals(AutoHealAction.CHANGE_SERVER, AutoHealPolicy.step(1).action)
        assertEquals(AutoHealAction.CHANGE_PROTOCOL, AutoHealPolicy.step(2).action)
        assertEquals(AutoHealAction.FALLBACK, AutoHealPolicy.step(3).action)
        assertEquals(AutoHealAction.GIVE_UP, AutoHealPolicy.step(4).action)
    }

    @Test fun retryDelaysAreBoundedAndIncreasing() {
        val delays = (0 until AutoHealPolicy.MAX_ATTEMPTS).map { AutoHealPolicy.step(it).delayMs }
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.last() <= 10_000)
    }
}
