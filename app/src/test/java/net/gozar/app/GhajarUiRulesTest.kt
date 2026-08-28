package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarUiRulesTest {
    private val linkToken = "a".repeat(48)

    @Test fun welcomeShowsEveryImageExactlyOnceBeforeRepeating() {
        val images = (1..33).map { "poster_$it" }
        var seen = emptySet<String>()
        var last: String? = null
        val random = kotlin.random.Random(42)
        repeat(4) {
            val cycle = mutableListOf<String>()
            repeat(images.size) {
                val pick = GhajarWelcomeRotation.next(images, seen, last, random)!!
                cycle += pick.name; seen = pick.seen; last = pick.name
            }
            assertEquals(images.toSet(), cycle.toSet())
            assertEquals(images.size, cycle.size)
        }
    }
    @Test fun welcomeDoesNotRepeatAtTheBoundaryBetweenCycles() {
        val images = listOf("king", "queen", "minister")
        repeat(50) { seed ->
            val pick = GhajarWelcomeRotation.next(images, images.toSet(), "queen", kotlin.random.Random(seed))!!
            assertFalse(pick.name == "queen")
            assertEquals(setOf(pick.name), pick.seen)
        }
    }
    @Test fun newWelcomePicturesAreShownBeforeTheNextCycle() {
        val pick = GhajarWelcomeRotation.next(listOf("king", "queen", "new"), setOf("king", "queen"), "queen")!!
        assertEquals("new", pick.name)
    }
    @Test fun removedWelcomePicturesCannotReappearFromSavedHistory() {
        val pick = GhajarWelcomeRotation.next(listOf("king", "queen"), setOf("deleted", "king"), "king")!!
        assertEquals("queen", pick.name)
        assertEquals(setOf("king", "queen"), pick.seen)
    }
    @Test fun welcomeHistoryCanBeRestoredBetweenLaunches() {
        val images = listOf("king", "queen", "minister")
        val first = GhajarWelcomeRotation.next(images, emptySet(), null)!!
        val saved = first.seen.joinToString(",").split(",").toSet()
        val second = GhajarWelcomeRotation.next(images, saved, first.name)!!
        assertFalse(first.name == second.name)
        assertEquals(2, second.seen.size)
    }
    @Test fun welcomeHandlesOnePictureDuplicateNamesAndEmptySets() {
        assertEquals(null, GhajarWelcomeRotation.next(emptyList(), setOf("old"), "old"))
        val pick = GhajarWelcomeRotation.next(listOf("king", "king", ""), setOf("king"), "king")!!
        assertEquals("king", pick.name)
        assertEquals(setOf("king"), pick.seen)
    }

    @Test fun nativeTelegramIntentCarriesTheSameCommandAsTheWebFallback() {
        assertEquals(listOf("tg://resolve?domain=Ghajar_vpnbot&start=link_012345",
            "https://t.me/Ghajar_vpnbot?start=link_012345"),
            GhajarUiRules.botLoginUrls("@Ghajar_vpnbot", "012345"))
    }
    @Test fun nativeTelegramIsOpenedWithoutLaunchingTheBrowserAgain() {
        val opened = mutableListOf<String>()
        assertTrue(GhajarUiRules.launchBotLogin("Ghajar_vpnbot", "123456") { opened += it; true })
        assertEquals(listOf("tg://resolve?domain=Ghajar_vpnbot&start=link_123456"), opened)
    }
    @Test fun unavailableTelegramFallsBackWithoutLosingTheCode() {
        val opened = mutableListOf<String>()
        assertTrue(GhajarUiRules.launchBotLogin("Ghajar_vpnbot", "123456") {
            opened += it; it.startsWith("https:")
        })
        assertEquals(2, opened.size)
        assertEquals("https://t.me/Ghajar_vpnbot?start=link_123456", opened.last())
    }
    @Test fun invalidLoginCodeNeverOpensABareBotChat() {
        var launched = false
        assertFalse(GhajarUiRules.launchBotLogin("Ghajar_vpnbot", null) { launched = true; true })
        assertFalse(GhajarUiRules.launchBotLogin("Ghajar_vpnbot", "123&start=x") { launched = true; true })
        assertFalse(launched)
    }
    @Test fun unavailableHandlersAreReportedAsFailure() {
        assertFalse(GhajarUiRules.launchBotLogin("Ghajar_vpnbot", "123456") { false })
    }

    @Test fun restoringLinkKeepsItsOriginalExpiry() {
        val expires = GhajarUiRules.linkExpiresAt(1_000, 300)
        assertEquals(301_000L, expires)
        assertTrue(GhajarUiRules.validPendingLink("123456", linkToken, expires, 200_000))
        assertFalse(GhajarUiRules.validPendingLink("123456", linkToken, expires, 301_000))
    }
    @Test fun expiredOrMalformedLinksCannotBeRestored() {
        assertFalse(GhajarUiRules.validPendingLink("123456", linkToken, 1_000, 2_000))
        assertFalse(GhajarUiRules.validPendingLink("12345x", linkToken, 3_000, 2_000))
        assertFalse(GhajarUiRules.validPendingLink("123456", "not-a-session", 3_000, 2_000))
    }
    @Test fun clockRollbackCannotGiveLinkAnUnboundedLifetime() {
        assertFalse(GhajarUiRules.validPendingLink("123456", linkToken, 2_000_000, 1_000))
    }
    @Test fun invalidServerTtlCannotCreateAnUnboundedSession() {
        assertEquals(1_000L, GhajarUiRules.linkExpiresAt(1_000, -30))
        assertEquals(901_000L, GhajarUiRules.linkExpiresAt(1_000, Int.MAX_VALUE))
    }

    @Test fun onlyTheOldAutomaticFeedIsArchived() {
        org.junit.Assert.assertTrue(GhajarUiRules.isLegacyAutomaticFreeFeed("Free Configs", "https://t.me/s/ConfigsHUB"))
        org.junit.Assert.assertTrue(GhajarUiRules.isLegacyAutomaticFreeFeed("کانفیگ‌های رایگان", "https://t.me/s/configshub/"))
        org.junit.Assert.assertFalse(GhajarUiRules.isLegacyAutomaticFreeFeed("My manual feed", "https://t.me/s/ConfigsHUB"))
        org.junit.Assert.assertFalse(GhajarUiRules.isLegacyAutomaticFreeFeed("Free Configs", "https://t.me/s/Ghajarvpn"))
    }
    @Test fun botReceivesTheExactLinkPayload() {
        assertEquals("https://t.me/Ghajar_vpnbot?start=link_012345", GhajarUiRules.botLink("@Ghajar_vpnbot", "012345"))
    }
    @Test fun malformedBotCannotRedirectOutsideTelegram() {
        assertEquals("https://t.me/Ghajar_vpnbot?start=link_123456", GhajarUiRules.botLink("evil.example/?x=", "123456"))
    }
    @Test fun malformedCodeIsNeverInjectedIntoDeepLink() {
        assertEquals("https://t.me/Ghajar_vpnbot", GhajarUiRules.botLink(null, "123&x=1"))
    }
    @Test fun persianAndArabicDigitsWorkInCustomPurchase() {
        assertEquals("1234567890", GhajarUiRules.asciiDigits("۱۲۳۴۵٦٧٨٩٠"))
        assertEquals("30", GhajarUiRules.asciiDigits("۳۰ روز"))
    }
}
