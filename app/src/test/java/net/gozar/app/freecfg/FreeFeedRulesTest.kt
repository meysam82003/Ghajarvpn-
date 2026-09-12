package net.gozar.app.freecfg

import net.gozar.app.ConfigParser
import net.gozar.app.ConfigSource
import net.gozar.app.ProxyConfig
import org.junit.Assert.*
import org.junit.Test

class FreeFeedRulesTest {
    @Test fun opaqueSubscriptionsSurviveTelegramFormattingAndArePrioritized() {
        val html = """<a href="https://navigation.example/">outside message</a>
          <div class="tgme_widget_message_text js-message_text" dir="auto">
          <a href="https://ads.example/">ad</a>
          <a href="https://panel.example/sub/opaque&#65;?x=1&amp;y=2">subscription</a>
          https://second.example/sub/long<wbr>token
          vless://id@example.com:443?security=tls#old​
          </div>"""
        val links = FreeFeedRules.extract(html)
        assertEquals("https://panel.example/sub/opaqueA?x=1&y=2", links.subscriptions.first())
        assertTrue(links.subscriptions.contains("https://second.example/sub/longtoken"))
        assertFalse(links.subscriptions.any { it.contains("navigation") })
        assertEquals(1, links.configs.size)
        assertFalse(links.configs[0].contains('\u200b'))
    }

    @Test fun duplicateNamesCollapseButDistinctTransportsRemain() {
        val base = ProxyConfig("old channel", "vless", "example.com", 443, uuid = "id")
        val selected = FreeFeedRules.select(listOf(listOf(base, base.copy(name = "another", subId = "old")),
            listOf(base.copy(network = "ws", path = "/one"), base.copy(network = "ws", path = "/two"))))
        assertEquals(3, selected.size)
    }

    @Test fun atMost350UniqueCandidatesAreTestedAcrossFeeds() {
        val all = (1..500).map { ProxyConfig("old $it", "vless", "example.com", it, uuid = "id") }
        val result = FreeFeedRules.select(listOf(all, all.map { it.copy(name = "duplicate") }))
        assertEquals(350, result.size)
        assertEquals(350, result.map(FreeFeedRules::signature).distinct().size)
    }

    @Test fun jsonSubscriptionArrayReadsAllNineProfilesAndSkipsDirectOutbounds() {
        val json = (1..9).joinToString(",", "[", "]") { i -> """
          {"remarks":"source $i","log":{},"inbounds":[],"outbounds":[
            {"protocol":"vless","settings":{"vnext":[{"address":"server$i.example","port":443,
              "users":[{"id":"00000000-0000-0000-0000-000000000001","encryption":"none"}]}]},
              "streamSettings":{"network":"ws","security":"tls","tlsSettings":{"serverName":"sni.example"},
                "wsSettings":{"path":"/subpath","headers":{"Host":"host.example"}}}},
            {"protocol":"freedom"},{"protocol":"blackhole"}]}""" }
        val configs = ConfigParser.parseBundle(json, ConfigSource.COMMUNITY)
        assertEquals(9, configs.size)
        assertTrue(configs.all { it.protocol == "vless" && it.network == "ws" && it.sni == "sni.example" && it.path == "/subpath" })
    }
}
