package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarChannelRulesTest {
    @Test fun extractsEverySupportedSchemeFromMixedChannelText() {
        val text = """
            کانال قاجار — کانفیگ امروز:
            vless://uuid123@example.com:443?security=tls#قاجار۱
            vmess://eyJhZGQiOiJleGFtcGxlLmNvbSJ9
            trojan://pass@host.example:8443?sni=x#t2
            hysteria2://pass@h.example:443#hy2
            ss://YWVzLTEyOC1nY206dGVzdA@1.2.3.4:8388#ss1
        """.trimIndent()
        val links = GhajarChannelRules.extractProxyLinks(text)
        assertEquals(5, links.size)
        assertTrue(links[0].startsWith("vless://"))
        assertTrue(links[1].startsWith("vmess://"))
        assertTrue(links[2].startsWith("trojan://"))
        assertTrue(links[3].startsWith("hysteria2://"))
        assertTrue(links[4].startsWith("ss://"))
    }

    @Test fun deduplicatesAndKeepsOrder() {
        val text = "vless://a@x:1#one\nمتن فارسی\nvless://a@x:1#one-dup\nss://b@y:2#two"
        assertEquals(
            listOf("vless://a@x:1#one", "ss://b@y:2#two"),
            GhajarChannelRules.extractProxyLinks(text)
        )
    }

    @Test fun telegramAndNoiseLinksAreNeverTreatedAsProxies() {
        val text = """
            https://t.me/Ghajarvpn
            https://t.me/+inviteOnly
            https://github.com/meysam82003
            vless://real@host:443#ok
        """.trimIndent()
        assertEquals(listOf("vless://real@host:443#ok"), GhajarChannelRules.extractProxyLinks(text))
    }

    @Test fun linksInsideHtmlTagsAndPersianPunctuationAreTrimmed() {
        val text = "<a href=\"ss://k@1.2.3.4:8388#x\">لینک</a>، vless://p@h:1#دو؛"
        assertEquals(
            listOf("ss://k@1.2.3.4:8388#x", "vless://p@h:1#دو"),
            GhajarChannelRules.extractProxyLinks(text)
        )
    }

    @Test fun zwnjInsidePersianTextNeverBreaksTheLink() {
        val text = "کانفیگ بعدی\u200c: trojan://t@h:443#قاجار"
        assertEquals(
            listOf("trojan://t@h:443#قاجار"),
            GhajarChannelRules.extractProxyLinks(text)
        )
    }

    @Test fun emptyAndBlankInputsYieldNothing() {
        assertTrue(GhajarChannelRules.extractProxyLinks(null).isEmpty())
        assertTrue(GhajarChannelRules.extractProxyLinks("   ").isEmpty())
        assertTrue(GhajarChannelRules.extractProxyLinks("بدون هیچ لینکی").isEmpty())
        assertFalse(GhajarChannelRules.isAcceptableChannelText("سلام"))
        assertTrue(GhajarChannelRules.isAcceptableChannelText("hy2://p@h:1#x"))
    }

    @Test fun publicBrandIsAlwaysGhajarvpnWhateverTheSourceIs() {
        listOf(null, "", "t.me/someChannel", "@OtherChannel", "https://t.me/Ghajarvpn").forEach { source ->
            assertEquals("@Ghajarvpn", GhajarChannelRules.publicBrand(source))
        }
    }

    @Test fun originTagStaysInternalAndNormalized() {
        assertEquals("src:internal", GhajarChannelRules.internalOriginTag(null))
        assertEquals("src:t.me/somechannel", GhajarChannelRules.internalOriginTag("https://t.me/SomeChannel"))
        assertFalse(GhajarChannelRules.internalOriginTag("https://t.me/Secret").contains("Secret"))
    }

    @Test fun openNpvtPrefersProxyLinkThenSubscriptionUrl() {
        assertEquals(
            "vless://v@h:443#a",
            GhajarChannelRules.npvtPayload("نوت قاجار\nvless://v@h:443#a")
        )
        assertEquals(
            "https://sub.example.com/s/abc",
            GhajarChannelRules.npvtPayload("https://sub.example.com/s/abc")
        )
    }

    @Test fun lockedOrEmptyNpvtIsRejectedWithoutBreakingIt() {
        assertNull(GhajarChannelRules.npvtPayload(null))
        assertNull(GhajarChannelRules.npvtPayload(""))
        assertNull(GhajarChannelRules.npvtPayload("فقط متن قفل‌شده بدون لینک"))
        assertTrue(GhajarChannelRules.npvtIsLocked("فقط متن قفل‌شده بدون لینک"))
        assertFalse(GhajarChannelRules.npvtIsLocked(null))
        assertFalse(GhajarChannelRules.npvtIsLocked("ss://k@1.2.3.4:8388#open"))
    }
}
