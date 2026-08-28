package net.gozar.app

import org.junit.Assert.*
import org.junit.Test

class GhajarStoryRulesTest {
    @Test fun relativeMediaUsesTheActualMiniappDirectory() {
        assertEquals(BrandConfig.STORE_URL + "assets/story-media/king.jpg?v=42",
            GhajarStoryRules.mediaUrl("assets/story-media/king.jpg?v=42"))
    }

    @Test fun mediaNeverEscapesToApiOrAnotherHost() {
        listOf("https://example.com/photo.jpg", "//example.com/evil.mp4", "../api/miniapp.php",
            "assets/story-media/../../secret.txt", "assets/story-media/%2e%2e/secret.txt",
            "assets/story-media/a%2Fb.jpg", "file:///sdcard/photo.jpg", "javascript:alert(1)",
            BrandConfig.STORE_URL.replace("https://", "http://") + "assets/story-media/a.jpg",
            "https://user:password@${BrandConfig.STORE_HOST}${BrandConfig.STORE_PATH}assets/story-media/a.jpg").forEach {
            assertNull(it, GhajarStoryRules.mediaUrl(it))
        }
    }

    @Test fun knownMiniappRoutesOpenTheEquivalentNativeSection() {
        assertEquals(3, GhajarStoryRules.route("#/recharge")?.section)
        assertEquals(0, GhajarStoryRules.route("#/buy")?.section)
        assertEquals(1, GhajarStoryRules.route("#/services")?.section)
        assertNull(GhajarStoryRules.route("#/admin"))
    }

    @Test fun externalActionsCannotLaunchScriptOrEmbeddedCredentials() {
        assertEquals("https://t.me/Ghajar_vpnbot", GhajarStoryRules.externalLink("https://t.me/Ghajar_vpnbot"))
        listOf("javascript:alert(1)", "intent://example", "file:///secret", "https://user:pass@example.com", "https://example.com:8080/")
            .forEach { assertNull(GhajarStoryRules.externalLink(it)) }
    }
}
