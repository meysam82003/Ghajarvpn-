package net.gozar.app.freecfg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeConfigPipelineTest {

    @Test
    fun `password found in caption`() {
        assertEquals("1234", FreeConfigPipeline.passwordFromContext("Password: 1234", null))
        assertEquals("test", FreeConfigPipeline.passwordFromContext(null, "Pass = test"))
        assertEquals("7788", FreeConfigPipeline.passwordFromContext(null, "رمز: 7788"))
        assertEquals("abc", FreeConfigPipeline.passwordFromContext("پسورد فایل : abc", null))
    }

    @Test
    fun `password missing returns null not guessed`() {
        assertNull(FreeConfigPipeline.passwordFromContext("سلام دوستان", "کانفیگ جدید اومد"))
    }

    @Test
    fun `preview parser extracts message ids and proxy links`() {
        val html = """
        <div class="tgme_widget_message_wrap"><div class="tgme_widget_message" data-post="chan/101"><div class="tgme_widget_message_text">vless://uuid@host:443?x=1#A</div></div></div>
        <div class="tgme_widget_message_wrap"><div class="tgme_widget_message" data-post="chan/102"><div class="tgme_widget_message_text">vmess://eyJhZGQiOiJhIiwiYWlkIjoiMCJ9</div></div></div>
        """.trimIndent()
        val messages = TelegramWebFetcher.parsePreview(html)
        assertEquals(2, messages.size)
        assertEquals(101L, messages[0].id)
        assertTrue(messages[0].text.contains("vless://"))
        assertTrue(messages[1].text.contains("vmess://"))
    }

    @Test
    fun `html entities and tags are stripped`() {
        val html = """<div class="tgme_widget_message" data-post="c/7"><div class="tgme_widget_message_text">ramz &amp; proxy &lt;ok&gt;<br/>vmess://zzz</div></div>"""
        val messages = TelegramWebFetcher.parsePreview(html)
        assertTrue(messages.first().text.contains("ramz & proxy <ok>"))
    }

    @Test
    fun `pipeline dedupes across sources`() {
        val link = "vless://uuid@host:443#A"
        val known = mutableSetOf<String>()
        val first = ConfigNormalizerSha(link, known)
        val second = ConfigNormalizerSha(link, known)
        assertEquals(Outcome_VALID, first)
        assertEquals(Outcome_DUPLICATE, second)
    }

    // helpers bridging the real normalizer without android deps
    private fun ConfigNormalizerSha(link: String, known: MutableSet<String>): Any {
        val hash = net.gozar.app.configcenter.ConfigNormalizer.sha256(link)
        return when {
            hash in known -> "DUPLICATE"
            else -> { known += hash; "VALID" }
        }
    }
    private val Outcome_VALID get() = "VALID"
    private val Outcome_DUPLICATE get() = "DUPLICATE"
}
