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

    @Test fun allUniqueCandidatesAreIncludedWithout350Cap() {
        val all = (1..500).map { ProxyConfig("old $it", "vless", "example.com", it, uuid = "id") }
        val result = FreeFeedRules.select(listOf(all, all.map { it.copy(name = "duplicate") }))
        assertEquals(500, result.size)
        assertEquals(500, result.map(FreeFeedRules::signature).distinct().size)
    }

    @Test fun onlyTheLast72HoursAreIncludedAcrossPageBoundaries() {
        val now = java.time.Instant.parse("2026-09-12T12:00:00Z").toEpochMilli()
        fun post(id: Int, stamp: String) = """<div data-post="channel/$id">
            <div class="tgme_widget_message_text">vless://id@server$id.example:443</div>
            <time datetime="$stamp"></time></div>"""
        val html = post(4, "2026-09-12T15:30:00+03:30") + post(3, "2026-09-09T12:00:00Z") +
            post(2, "2026-09-09T11:59:59Z") + post(1, "invalid")
        val selected = FreeFeedRules.recentPosts(FreeFeedRules.posts(html), now)
        assertEquals(listOf(4L, 3L), selected.map { it.id })
        assertEquals(2, selected.flatMap { FreeFeedRules.extract(it.html).configs }.size)
    }

    @Test fun completeRefreshDeletesMissingConfigsButPartialRefreshPreservesUntestedOnes() {
        val old = (1..3).map { ProxyConfig("old channel $it", "vless", "server$it.example", 443, uuid = "id") }
        val fresh = ProxyConfig("new channel", "vless", "new.example", 443, uuid = "id")
        val tested = setOf(FreeFeedRules.signature(old[0]), FreeFeedRules.signature(old[1]))
        val healthy = listOf(old[0].copy(name = "renamed"), fresh)
        val partial = FreeFeedRules.reconcile(old, healthy, tested, false)
        assertEquals(listOf("server1.example", "new.example", "server3.example"), partial.map { it.address })
        assertEquals(listOf("Ghajarvpn 1", "Ghajarvpn 2", "Ghajarvpn 3"), partial.map { it.name })
        assertEquals(2, FreeFeedRules.reconcile(old, healthy, tested, true).size)
        assertTrue(FreeFeedRules.reconcile(old, emptyList(), tested, true).isEmpty())
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
