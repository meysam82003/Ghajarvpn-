package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GhajarWidgetRulesTest {
    @Test fun widgetExposesAllFourConnectionPhases() {
        assertEquals(GhajarWidgetPhase.DISCONNECTED, GhajarWidgetRules.phase(Connection.DISCONNECTED, null))
        assertEquals(GhajarWidgetPhase.CONNECTING, GhajarWidgetRules.phase(Connection.CONNECTING, null))
        assertEquals(GhajarWidgetPhase.CONNECTED, GhajarWidgetRules.phase(Connection.CONNECTED, null))
        assertEquals(GhajarWidgetPhase.DISCONNECTING, GhajarWidgetRules.phase(Connection.CONNECTED, "disconnecting"))
        assertEquals("در حال قطع…", GhajarWidgetRules.status(GhajarWidgetPhase.DISCONNECTING))
    }

    @Test fun nextLocationCyclesWithoutSkippingOrGettingStuck() {
        val ids = listOf("tehran", "shiraz", "tabriz")
        assertEquals("tehran", GhajarWidgetRules.nextId(ids, null))
        assertEquals("shiraz", GhajarWidgetRules.nextId(ids, "tehran"))
        assertEquals("tehran", GhajarWidgetRules.nextId(ids, "tabriz"))
        assertEquals(null, GhajarWidgetRules.nextId(emptyList(), "tehran"))
    }
}
