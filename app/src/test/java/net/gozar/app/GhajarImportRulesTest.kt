package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarImportRulesTest {
    @Test fun recognizesNpvtWithoutCaseSensitivity() {
        assertTrue(GhajarImportRules.isNpvt("Royal.NPVT"))
    }

    @Test fun acceptsSubscriptionFileAndRejectsBundlesOrCredentials() {
        assertEquals("https://example.com/sub?id=1", GhajarImportRules.subscriptionUrl(" https://example.com/sub?id=1 "))
        assertNull(GhajarImportRules.subscriptionUrl("https://one.example/sub\nhttps://two.example/sub"))
        assertNull(GhajarImportRules.subscriptionUrl("https://user:pass@example.com/sub"))
    }
}
