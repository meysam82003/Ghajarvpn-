package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTestRulesTest {
    @Test fun rateUsesDecimalMegabits() {
        assertEquals(8.0, SpeedTest.rateMbps(1_000_000, 1_000_000_000L)!!, 0.0001)
    }

    @Test fun invalidMeasurementsAreRejected() {
        assertNull(SpeedTest.rateMbps(0, 1_000_000L))
        assertNull(SpeedTest.rateMbps(100, 0))
    }
}
