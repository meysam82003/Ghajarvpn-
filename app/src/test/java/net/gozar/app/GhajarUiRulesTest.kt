package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarUiRulesTest {
    private val linkToken = "a".repeat(48)

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
    @Test fun configNamesAlwaysLeadWithGhajarvpnWithoutDoublePrefixing() {
        assertEquals("Ghajarvpn • Germany 01", GhajarUiRules.brandedConfigName("Germany 01"))
        assertEquals("Ghajarvpn France", GhajarUiRules.brandedConfigName("Ghajarvpn France"))
    }
    @Test fun subscriptionTitleUsesServerQuotaInGb() {
        assertEquals("Ghajarvpn 10 GB", GhajarUiRules.brandedSubscriptionTitle(10L * 1024 * 1024 * 1024, "anything"))
        assertEquals("Ghajarvpn 1.5 GB", GhajarUiRules.brandedSubscriptionTitle(1536L * 1024 * 1024, "anything"))
        assertEquals("Ghajarvpn • Royal", GhajarUiRules.brandedSubscriptionTitle(0, "Royal"))
    }
    @Test fun ovpnNamesUseCountryFlagFromHostname() {
        assertEquals("Ghajarvpn 🇧🇴", GhajarUiRules.ovpnDisplayName("97-1-bo.cg-dialup.net"))
        assertEquals("Ghajarvpn 🇮🇳", GhajarUiRules.ovpnDisplayName("97-1-in.cg-dialup.net"))
        assertEquals("Ghajarvpn 🇵🇪", GhajarUiRules.ovpnDisplayName("97-1-pe.cg-dialup.net"))
        assertEquals("Ghajarvpn 🌐", GhajarUiRules.ovpnDisplayName("vpn.example.invalid"))
    }

    @Test fun persianAndArabicDigitsWorkInCustomPurchase() {
        assertEquals("1234567890", GhajarUiRules.asciiDigits("۱۲۳۴۵٦٧٨٩٠"))
        assertEquals("30", GhajarUiRules.asciiDigits("۳۰ روز"))
    }

    @Test fun ovpnEngineAuthFailureIsReportedAsRejectedCredentials() {
        assertEquals(
            "نام کاربری یا رمز OpenVPN رد شد",
            GhajarUiRules.ovpnEngineMessage("AUTH_FAILED: wrong username/password")
        )
    }

    @Test fun ovpnEngineProcessExitReportsTheExitCode() {
        val message = GhajarUiRules.ovpnEngineMessage(
            "NOPROCESS: No process running. exit code 1"
        )
        assertTrue(message.contains("کد 1"))
        assertTrue(message.contains("پیکربندی") || message.contains("پردازش"))
        assertTrue(message.contains("OpenVPN"))
    }

    @Test fun ovpnEngineServiceIntentFailureIsTranslatedForTheUser() {
        val message = GhajarUiRules.ovpnEngineMessage(
            "java.lang.SecurityException: Unable to start service Intent { cmp=com.ghajarvpn.app/de.blinkt.openvpn.core.OpenVPNService }"
        )
        assertEquals("موتور OpenVPN اندروید اجرا نشد؛ سرویس داخلی قاجار در دسترس نیست", message)
    }

    @Test fun ovpnEngineFatalTlsErrorPointsAtTheServerHandshake() {
        val message = GhajarUiRules.ovpnEngineMessage(
            "OpenVPN: TLS Error: TLS key negotiation failed to occur within 60 seconds"
        )
        assertTrue(message.contains("دست‌دهی"))
    }

    @Test fun ovpnEngineLongMessagesAreTruncatedButKeepTheirMeaning() {
        val raw = "A".repeat(500)
        val message = GhajarUiRules.ovpnEngineMessage(raw)
        assertTrue(message.length <= 181)
        assertTrue(message.endsWith("…"))
    }

    @Test fun ovpnSavedCredentialRequirementMatchesTheBridgeRule() {
        assertTrue(GhajarUiRules.ovpnNeedsSavedCredentials(null, "secret"))
        assertTrue(GhajarUiRules.ovpnNeedsSavedCredentials("user", "  "))
        assertFalse(GhajarUiRules.ovpnNeedsSavedCredentials("user", "secret"))
    }
}
