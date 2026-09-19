package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The masks, the noises and the zeptun configuration.
 *
 * These are all emitted configurations for something that parses them
 * strictly, which is exactly the kind of code that is easy to get wrong and
 * cheap to pin down. Two of the assertions here would have caught bugs that
 * shipped: zeptun's parser rejects a key it does not know, and Xray's decoder
 * silently drops one.
 */
class MaskAndNoiseTest {

    // ---- freedom noises -------------------------------------------------

    @Test
    fun `off and blank emit nothing at all`() {
        assertNull(NoiseSpec.build(""))
        assertNull(NoiseSpec.build("   "))
        assertNull(NoiseSpec.build("off"))
    }

    @Test
    fun `a preset emits the shape the freedom outbound takes`() {
        val out = NoiseSpec.build("standard")
        assertNotNull(out)
        assertEquals(2, out!!.length())
        val first = out.getJSONObject(0)
        // rand puts the LENGTH range in packet - not a payload. Getting this
        // backwards is accepted by the JSON and rejected by the core.
        assertEquals("rand", first.getString("type"))
        assertEquals("50-100", first.getString("packet"))
        assertEquals("ip", first.getString("applyTo"))
    }

    @Test
    fun `every preset name produces something the core accepts`() {
        val types = setOf("rand", "str", "hex", "base64")
        NoiseSpec.PRESETS.filter { it != "off" && it != "custom" }.forEach { preset ->
            val out = NoiseSpec.build(preset)
            assertNotNull("preset $preset emitted nothing", out)
            for (i in 0 until out!!.length()) {
                val item = out.getJSONObject(i)
                assertTrue(
                    "preset $preset item $i has type ${item.optString("type")}",
                    item.getString("type") in types
                )
                assertTrue("preset $preset item $i has no packet", item.getString("packet").isNotEmpty())
            }
        }
    }

    @Test
    fun `a hand written spec is read line by line`() {
        val out = NoiseSpec.build("rand:20-40:1-2\nhex:c0ffee")
        assertNotNull(out)
        assertEquals(2, out!!.length())
        assertEquals("rand", out.getJSONObject(0).getString("type"))
        assertEquals("1-2", out.getJSONObject(0).getString("delay"))
        assertEquals("hex", out.getJSONObject(1).getString("type"))
        assertEquals("c0ffee", out.getJSONObject(1).getString("packet"))
        // No delay was given, so none is written rather than a zero.
        assertFalse(out.getJSONObject(1).has("delay"))
    }

    @Test
    fun `an unusable line is dropped and an unusable spec is null`() {
        // Unknown type, missing packet, and no colon at all.
        assertNull(NoiseSpec.build("bogus:10-20\nrand:\nnonsense"))
        val mixed = NoiseSpec.build("bogus:1-2\nrand:10-20")
        assertEquals(1, mixed!!.length())
        assertEquals("rand", mixed.getJSONObject(0).getString("type"))
    }

    // ---- the udp mask noise, which is a different shape -----------------

    @Test
    fun `the mask noise puts the length in rand and never sets both`() {
        val out = NoiseSpec.buildMaskNoise("standard")
        assertNotNull(out)
        for (i in 0 until out!!.length()) {
            val item = out.getJSONObject(i)
            assertTrue("item $i has no rand", item.has("rand"))
            // The core's NoiseMask.Build() errors on an item that sets a
            // literal packet and a random length together.
            assertFalse("item $i sets packet as well as rand", item.has("packet"))
            assertFalse("item $i names a type it should not", item.has("type"))
        }
    }

    @Test
    fun `the mask noise is off for off and blank`() {
        assertNull(NoiseSpec.buildMaskNoise(""))
        assertNull(NoiseSpec.buildMaskNoise("off"))
    }

    // ---- the zeptun configuration ---------------------------------------

    /**
     * Every section and key zeptun's config document declares. Its TOML
     * parser returns ConfigError for anything else - so this list is the
     * contract, and the test below is what makes it one.
     */
    private val zeptunKeys = setOf(
        "preset", "log_level", "stats_interval_s", "log_file", "pid_file",
        "post_up_script", "pre_down_script",
        "tun.name", "tun.fd", "tun.mtu", "tun.queues", "tun.offload",
        "tun.multi_queue", "tun.persist", "tun.napi", "tun.jumbo",
        "tun.txqueuelen", "tun.configure", "tun.netns", "tun.guid", "tun.address",
        "stack.mode", "stack.tcp_rx_window", "stack.tcp_rx_budget",
        "stack.tcp_tx_buffer", "stack.tcp_mss_clamp", "stack.tcp_initial_cwnd",
        "stack.congestion", "stack.sack", "stack.timestamps",
        "stack.window_scaling", "stack.tcp_connect_timeout_ms",
        "stack.tcp_idle_timeout_ms", "stack.tcp_delayed_ack_ms",
        "stack.tcp_early_accept", "stack.udp_idle_timeout_ms", "stack.udp",
        "stack.udp_nat", "stack.icmp", "stack.max_tcp_sessions",
        "stack.max_udp_sessions", "stack.listen_port_base",
        "stack.nat_port_base", "stack.nat_port_limit",
        "handler.kind", "handler.tcp_fastopen", "handler.preserve_dscp",
        "handler.socks5.server", "handler.socks5.username",
        "handler.socks5.password", "handler.socks5.udp",
        "handler.socks5.udp_mode", "handler.socks5.udp_address",
        "handler.socks5.pipeline", "handler.socks5.optimistic_data",
        "handler.socks5.pool_size", "handler.socks5.pool_idle_ms",
        "handler.direct.fwmark", "handler.direct.bind_interface",
        "route.auto_route", "route.table", "route.rule_priority",
        "route.fwmark", "route.include", "route.exclude", "route.include_file",
        "route.exclude_file", "route.strict", "route.auto_redirect",
        "route.redirect_port", "route.include_uid", "route.exclude_uid",
        "route.include_interface", "route.exclude_interface",
        "route.include_package", "route.exclude_package", "route.android_user",
        "route.dns_servers",
        "io.backend", "io.ring_entries", "io.sqpoll", "io.workers",
        "io.pin_cpus", "io.rx_parallel", "io.tx_slots", "io.busy_poll_us",
        "io.multishot_rx", "io.monitor_network", "io.elastic",
        "memory.budget_bytes", "memory.buffers_per_worker",
        "dns.fake_ip", "dns.fake_ranges", "dns.cache_size", "dns.ttl",
        "dns.address", "dns.hijack", "dns.upstream", "dns.systemd_resolved"
    )

    /** Section-qualified keys, the way the parser sees them. */
    private fun keysOf(toml: String): List<String> {
        var table = ""
        val out = ArrayList<String>()
        toml.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("[")) {
                table = line.trim('[', ']')
                return@forEach
            }
            val key = line.substringBefore('=').trim()
            out.add(if (table.isEmpty()) key else "$table.$key")
        }
        return out
    }

    @Test
    fun `every key zeptun is given is one it knows`() {
        val configs = buildList {
            add(ZeptunEngine.socksConfig(socksPort = 1080))
            ZeptunEngine.Profile.entries.forEach {
                add(ZeptunEngine.socksConfig(socksPort = 1080, profile = it))
            }
            ZeptunEngine.DnsMode.entries.forEach {
                add(ZeptunEngine.socksConfig(socksPort = 1080, dnsMode = it, dnsUpstream = "1.1.1.1"))
            }
            add(ZeptunEngine.socksConfig(socksPort = 1080, ipv6 = true, dnsMode = ZeptunEngine.DnsMode.FAKE_IP))
        }
        configs.forEach { toml ->
            keysOf(toml).forEach { key ->
                assertTrue("zeptun has no key $key\n$toml", key in zeptunKeys)
            }
        }
    }

    @Test
    fun `the socks server is emitted under its own section`() {
        val toml = ZeptunEngine.socksConfig(socksPort = 1080)
        assertTrue(toml, toml.contains("[handler.socks5]"))
        assertTrue(toml, toml.contains("server = \"127.0.0.1:1080\""))
        // The old shape - an "address" key directly under [handler] - is not a
        // field of the document, and zeptun refuses the whole config for it.
        assertFalse(toml, toml.contains("address ="))
    }

    @Test
    fun `offload and multi queue are never claimed`() {
        // zeptun's Android device pins both off, so emitting them would read
        // as a feature and change nothing.
        ZeptunEngine.Profile.entries.forEach {
            val toml = ZeptunEngine.socksConfig(socksPort = 1080, profile = it)
            assertFalse(toml, toml.contains("offload"))
            assertFalse(toml, toml.contains("multi_queue"))
        }
    }

    @Test
    fun `forward mode writes no dns section`() {
        val toml = ZeptunEngine.socksConfig(socksPort = 1080)
        assertFalse(toml, toml.contains("[dns]"))
    }

    @Test
    fun `hijack without an upstream is not written half configured`() {
        // The engine's own dnsActive() ignores hijack unless upstream is set,
        // so a blank one would be a switch that silently does nothing.
        val toml = ZeptunEngine.socksConfig(
            socksPort = 1080, dnsMode = ZeptunEngine.DnsMode.HIJACK, dnsUpstream = " "
        )
        assertFalse(toml, toml.contains("[dns]"))
    }

    @Test
    fun `a hijack upstream gets a port when the user did not give one`() {
        val plain = ZeptunEngine.socksConfig(
            socksPort = 1080, dnsMode = ZeptunEngine.DnsMode.HIJACK, dnsUpstream = "9.9.9.9"
        )
        assertTrue(plain, plain.contains("upstream = \"9.9.9.9:53\""))

        val withPort = ZeptunEngine.socksConfig(
            socksPort = 1080, dnsMode = ZeptunEngine.DnsMode.HIJACK, dnsUpstream = "9.9.9.9:5353"
        )
        assertTrue(withPort, withPort.contains("upstream = \"9.9.9.9:5353\""))

        // A bare IPv6 literal is a host, not host:port, however many colons it
        // has - so it gets brackets and a port rather than being mistaken for
        // one that already has them.
        val v6 = ZeptunEngine.socksConfig(
            socksPort = 1080, dnsMode = ZeptunEngine.DnsMode.HIJACK, dnsUpstream = "2620:fe::fe"
        )
        assertTrue(v6, v6.contains("upstream = \"[2620:fe::fe]:53\""))

        val v6WithPort = ZeptunEngine.socksConfig(
            socksPort = 1080, dnsMode = ZeptunEngine.DnsMode.HIJACK, dnsUpstream = "[2620:fe::fe]:53"
        )
        assertTrue(v6WithPort, v6WithPort.contains("upstream = \"[2620:fe::fe]:53\""))
    }

    @Test
    fun `fake ip names a range and an address`() {
        val v4 = ZeptunEngine.socksConfig(socksPort = 1080, dnsMode = ZeptunEngine.DnsMode.FAKE_IP)
        assertTrue(v4, v4.contains("fake_ip = true"))
        assertTrue(v4, v4.contains("fake_ranges = [\"198.18.0.0/15\"]"))
        assertTrue(v4, v4.contains("address = [\"${ZeptunEngine.FAKE_DNS_ADDRESS}\"]"))

        // A v6 fake range only when the tun actually carries v6.
        val v6 = ZeptunEngine.socksConfig(
            socksPort = 1080, ipv6 = true, dnsMode = ZeptunEngine.DnsMode.FAKE_IP
        )
        assertTrue(v6, v6.contains("fc00::/18"))
    }

    @Test
    fun `the balanced profile leaves the preset alone`() {
        val toml = ZeptunEngine.socksConfig(socksPort = 1080, profile = ZeptunEngine.Profile.BALANCED)
        assertFalse(toml, toml.contains("[stack]"))
        assertFalse(toml, toml.contains("[io]"))
    }

    // ---- the finalmask entries ------------------------------------------

    private fun config(
        network: String = "tcp",
        maskType: String = "",
        maskDomain: String = "",
        maskPassword: String = ""
    ) = ProxyConfig(
        name = "s", protocol = "vless", address = "example.com", port = 443,
        network = network, maskType = maskType,
        maskDomain = maskDomain, maskPassword = maskPassword
    )

    @Test
    fun `no mask named means no entry`() {
        assertNull(ConfigBuilder.maskEntry(config()))
    }

    @Test
    fun `an unknown mask name is not passed through`() {
        assertNull(ConfigBuilder.maskEntry(config(maskType = "wireguard-noise")))
    }

    @Test
    fun `xdns carries its domain`() {
        val entry = ConfigBuilder.maskEntry(config(maskType = "xdns", maskDomain = "t.example.com"))
        assertNotNull(entry)
        assertEquals("xdns", entry!!.getString("type"))
        assertEquals("t.example.com", entry.getJSONObject("settings").getString("domain"))
    }

    @Test
    fun `an xdns without a domain is refused rather than emitted empty`() {
        // The core errors on an empty domain, which would take the whole
        // connect down instead of just the disguise.
        assertNull(ConfigBuilder.maskEntry(config(maskType = "xdns")))
    }

    @Test
    fun `a keyed mask without its password is refused`() {
        assertNull(ConfigBuilder.maskEntry(config(maskType = "sudoku")))
        assertNull(ConfigBuilder.maskEntry(config(maskType = "salamander")))
        assertNotNull(ConfigBuilder.maskEntry(config(maskType = "sudoku", maskPassword = "x")))
    }

    @Test
    fun `masks land in the list their transport belongs to`() {
        // mKCP and Hysteria are UDP, so everything is available there.
        assertEquals("udp", ConfigBuilder.maskSide("kcp", "xdns"))
        assertEquals("udp", ConfigBuilder.maskSide("mkcp", "noise"))
        assertEquals("udp", ConfigBuilder.maskSide("hysteria", "salamander"))
        // Only sudoku is registered for TCP.
        assertEquals("tcp", ConfigBuilder.maskSide("tcp", "sudoku"))
        assertEquals("tcp", ConfigBuilder.maskSide("ws", "sudoku"))
        // A udp-only mask on a TCP transport belongs in neither list, and
        // saying so is what stops it being written somewhere it is ignored.
        assertNull(ConfigBuilder.maskSide("tcp", "xdns"))
        assertNull(ConfigBuilder.maskSide("ws", "noise"))
        assertNull(ConfigBuilder.maskSide("grpc", "salamander"))
    }

    // ---- the per-config fields are additive -----------------------------

    @Test
    fun `the new per config fields default to blank`() {
        val c = config()
        assertEquals("", c.maskType)
        assertEquals("", c.maskDomain)
        assertEquals("", c.maskPassword)
        assertEquals("", c.echConfigList)
    }

    @Test
    fun `a config json without the new keys decodes to blank`() {
        val json = config().toJson()
        listOf("maskType", "maskDomain", "maskPassword", "echConfigList").forEach { json.remove(it) }
        val back = ProxyConfig.fromJson(json)
        assertEquals("", back.maskType)
        assertEquals("", back.maskDomain)
        assertEquals("", back.maskPassword)
        assertEquals("", back.echConfigList)
    }

    @Test
    fun `the new per config fields survive a round trip`() {
        val c = config(network = "kcp", maskType = "xdns", maskDomain = "t.example.com")
            .copy(maskPassword = "p", echConfigList = "AEX+DQBB...")
        val back = ProxyConfig.fromJson(c.toJson())
        assertEquals("xdns", back.maskType)
        assertEquals("t.example.com", back.maskDomain)
        assertEquals("p", back.maskPassword)
        assertEquals("AEX+DQBB...", back.echConfigList)
    }
}
