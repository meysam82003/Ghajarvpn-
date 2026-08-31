package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarShopRulesTest {
    @Test fun customStepsStayInsideServerLimitsAndIncludeBoundaries() {
        val values = GhajarShopRules.optionValues(20, 80, listOf(10, 30, 50, 100))
        assertEquals(listOf(20, 30, 50, 80), values)
        assertTrue(values.all { it in 20..80 })
    }

    @Test fun customStepsRemainUsefulBeforeTheFirstLiveQuote() {
        assertEquals(listOf(7, 30, 60, 90, 180),
            GhajarShopRules.optionValues(0, 0, listOf(7, 30, 60, 90, 180)))
    }
}
