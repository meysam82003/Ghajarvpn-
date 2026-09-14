package net.gozar.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarSharePolicyTest {
    @Test fun quotaExpiresAtConfiguredLimit() {
        val c = GhajarShareClient(ip = "192.168.1.2", uploaded = 100, downloaded = 200, quotaBytes = 300)
        assertTrue(c.expired)
    }

    @Test fun unlimitedClientDoesNotExpire() {
        val c = GhajarShareClient(ip = "192.168.1.3", uploaded = 999999, downloaded = 999999)
        assertFalse(c.expired)
    }

    @Test fun blockedClientExpiresImmediately() {
        assertTrue(GhajarShareClient(ip = "192.168.1.4", blocked = true).expired)
    }
}
