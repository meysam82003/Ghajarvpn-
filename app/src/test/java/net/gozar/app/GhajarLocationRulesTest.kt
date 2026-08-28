package net.gozar.app

import org.junit.Assert.*
import org.junit.Test

class GhajarLocationRulesTest {
    private val valid = IpLocation("8.8.8.8", "Mountain View", "United States", "us", 37.4, -122.1)
    private val session = GhajarLocationSession(Connection.CONNECTED, "a", 10L, false, false)

    @Test fun countryAndCoordinatesComeFromValidatedResponse() {
        assertEquals("US", GhajarLocationRules.validate(valid)?.countryCode)
        assertEquals("🇺🇸", GhajarLocationRules.flag("US"))
        assertNull(GhajarLocationRules.validate(valid.copy(countryCode = "ZZ")))
        assertNull(GhajarLocationRules.validate(valid.copy(lat = Double.NaN)))
        assertNull(GhajarLocationRules.validate(valid.copy(lon = 181.0)))
        assertNull(GhajarLocationRules.validate(valid.copy(lat = -91.0)))
    }

    @Test fun providerCannotReturnLocationForAnotherIp() {
        assertNotNull(GhajarLocationRules.validate(valid, "8.8.8.8"))
        assertNull(GhajarLocationRules.validate(valid, "1.1.1.1"))
        assertTrue(GhajarLocationRules.sameIp("2606:4700:4700::1111", "2606:4700:4700:0:0:0:0:1111"))
    }

    @Test fun malformedAddressesNeverReachDnsOrUi() {
        listOf("example.com", "8.8.8.8/evil", "8.8.8.999", "8.8.8", "08.8.8.8",
            "127.0.0.1", "10.1.2.3", "::1", "fe80::1%wlan0", "8.8.8.8\nCookie: x").forEach {
            assertNull(it, GhajarLocationRules.numericIp(it))
        }
    }

    @Test fun reconnectAndDisconnectHideTheOldFlagImmediately() {
        val snapshot = GhajarLocationSnapshot(session, valid.ip, valid)
        assertEquals(valid, snapshot.forSession(session).location)
        listOf(session.copy(connectedAt = 11L), session.copy(activeId = "b"),
            session.copy(connection = Connection.CONNECTING),
            session.copy(connection = Connection.DISCONNECTED),
            session.copy(connection = Connection.ERROR)).forEach {
            val shown = snapshot.forSession(it)
            assertNull(shown.location)
            assertEquals("", shown.ip)
        }
    }

    @Test fun knownIpCanBeShownWithoutInventingCoordinates() {
        val snapshot = GhajarLocationSnapshot(session, "8.8.8.8", loading = false)
        assertEquals("8.8.8.8", snapshot.forSession(session).ip)
        assertNull(snapshot.location)
    }
}
