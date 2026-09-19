package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import path, tested against the file it has to survive.
 *
 * `blueknight_net.txt` is a real 965-address list, committed as a fixture
 * because "it handles a big paste" is not a claim a hand-written five-line
 * sample can support: the things that break at a thousand entries are
 * duplicates, a trailing newline, and a parser that is quadratic in the number
 * of lines.
 */
class DnsResolverImportTest {

    private fun fixture(): String =
        javaClass.classLoader!!.getResourceAsStream("blueknight_net.txt")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `the blueknight list imports every address`() {
        val report = DnsResolverImport.parse(fixture())
        assertEquals(965, report.added)
        assertEquals(0, report.invalid)
        assertEquals(0, report.duplicates)
        // The file's own trailing newline is the only skippable line, and a
        // trimmed input has none - so nothing at all should be skipped.
        assertEquals(0, report.skipped)
        assertTrue(report.resolvers.all { it.transport == DnsTransport.UDP })
    }

    @Test
    fun `re-importing the same file adds nothing`() {
        val first = DnsResolverImport.parse(fixture())
        val second = DnsResolverImport.parse(fixture(), existing = first.resolvers)
        assertEquals(0, second.added)
        assertEquals(965, second.duplicates)
    }

    @Test
    fun `duplicates inside one paste are collapsed`() {
        val report = DnsResolverImport.parse("1.1.1.1\n1.1.1.1\n8.8.8.8\n1.1.1.1")
        assertEquals(2, report.added)
        assertEquals(2, report.duplicates)
    }

    @Test
    fun `blank lines and comments are skipped not counted as invalid`() {
        val report = DnsResolverImport.parse(
            """
            # my resolvers
            1.1.1.1

            // another
            8.8.8.8
            ; trailing
            """.trimIndent()
        )
        assertEquals(2, report.added)
        assertEquals(0, report.invalid)
        assertEquals(4, report.skipped)
    }

    @Test
    fun `a typo is invalid and a header row is skipped`() {
        // The distinction the report exists for: one means the file has a
        // mistake in it, the other means the file has a title.
        val report = DnsResolverImport.parse("address,country\n1.2.3.456\n9.9.9.9")
        assertEquals(1, report.added)
        assertEquals(1, report.invalid)
        assertEquals(1, report.skipped)
    }

    @Test
    fun `csv columns are read in either order`() {
        val a = DnsResolverImport.parse("1.1.1.1,Cloudflare,US")
        val b = DnsResolverImport.parse("US,Cloudflare,1.1.1.1")
        assertEquals(1, a.added)
        assertEquals(1, b.added)
        assertEquals(a.resolvers.single().address, b.resolvers.single().address)
    }

    @Test
    fun `a label on the line becomes the name`() {
        val report = DnsResolverImport.parse("1.1.1.1,Cloudflare")
        assertEquals("Cloudflare", report.resolvers.single().name)
    }

    @Test
    fun `json arrays of strings and of objects both parse`() {
        val strings = DnsResolverImport.parse("""["1.1.1.1","8.8.8.8"]""")
        assertEquals(2, strings.added)

        val objects = DnsResolverImport.parse(
            """[{"ip":"9.9.9.9","name":"Quad9"},{"address":"1.0.0.1"}]"""
        )
        assertEquals(2, objects.added)
        assertEquals("Quad9", objects.resolvers.first().name)
    }

    @Test
    fun `json object with the list under any key parses`() {
        val report = DnsResolverImport.parse("""{"whatever_they_called_it":["1.1.1.1"]}""")
        assertEquals(1, report.added)
    }

    @Test
    fun `json protocol field is honoured`() {
        val report = DnsResolverImport.parse("""[{"ip":"1.1.1.1","protocol":"tcp"}]""")
        assertEquals(DnsTransport.TCP, report.resolvers.single().transport)
    }

    @Test
    fun `a doh url survives a comma in its query string`() {
        val report = DnsResolverImport.parse("https://dns.example/dns-query?types=1,28")
        assertEquals(1, report.added)
        assertEquals(DnsTransport.DOH, report.resolvers.single().transport)
        assertEquals("https://dns.example/dns-query?types=1,28", report.resolvers.single().address)
    }

    @Test
    fun `an import is capped rather than truncated silently`() {
        val huge = (1..DnsResolverImport.MAX_ENTRIES + 500)
            .joinToString("\n") { "10.${it / 65536 % 256}.${it / 256 % 256}.${it % 256}" }
        val report = DnsResolverImport.parse(huge)
        assertTrue(report.added <= DnsResolverImport.MAX_ENTRIES)
    }

    // --- transport rules -----------------------------------------------

    @Test
    fun `DoT is refused for a bare IP`() {
        // There is nothing to bind a certificate to, and the alternative -
        // skipping the check - is not on offer.
        assertNull(DnsResolverImport.manual("1.1.1.1", transport = DnsTransport.DOT))
        assertNotNull(DnsResolverImport.manual("one.one.one.one", transport = DnsTransport.DOT))
    }

    @Test
    fun `DoH needs an https url`() {
        assertNull(DnsResolverImport.manual("1.1.1.1", transport = DnsTransport.DOH))
        assertNull(DnsResolverImport.manual("http://dns.example/q", transport = DnsTransport.DOH))
        assertNull(DnsResolverImport.manual("https://", transport = DnsTransport.DOH))
        assertNotNull(DnsResolverImport.manual("https://dns.example/dns-query", transport = DnsTransport.DOH))
    }

    @Test
    fun `a bare IP never becomes an encrypted transport on its own`() {
        val report = DnsResolverImport.parse("1.1.1.1")
        assertEquals(DnsTransport.UDP, report.resolvers.single().transport)
    }

    @Test
    fun `an out of range port is rejected`() {
        assertNull(DnsResolverImport.manual("1.1.1.1", port = "70000"))
        assertNull(DnsResolverImport.manual("1.1.1.1", port = "0"))
        assertNotNull(DnsResolverImport.manual("1.1.1.1", port = "5353"))
    }

    // --- address validation --------------------------------------------

    @Test
    fun `ipv4 rules`() {
        assertTrue(DnsResolverImport.isIpv4("0.0.0.0"))
        assertTrue(DnsResolverImport.isIpv4("255.255.255.255"))
        assertFalse(DnsResolverImport.isIpv4("256.1.1.1"))
        assertFalse(DnsResolverImport.isIpv4("1.1.1"))
        assertFalse(DnsResolverImport.isIpv4("1.1.1.1.1"))
        // Leading zeros rejected so one resolver cannot appear twice under two
        // spellings of the same address.
        assertFalse(DnsResolverImport.isIpv4("1.1.1.01"))
        assertFalse(DnsResolverImport.isIpv4(""))
    }

    @Test
    fun `ipv6 rules`() {
        assertTrue(DnsResolverImport.isIpv6("2001:4860:4860::8888"))
        assertTrue(DnsResolverImport.isIpv6("::1"))
        assertTrue(DnsResolverImport.isIpv6("2606:4700:4700::1111"))
        assertTrue(DnsResolverImport.isIpv6("[2620:fe::fe]"))
        assertTrue(DnsResolverImport.isIpv6("::ffff:1.2.3.4"))
        assertFalse(DnsResolverImport.isIpv6("1::2::3"))
        assertFalse(DnsResolverImport.isIpv6("gggg::1"))
        assertFalse(DnsResolverImport.isIpv6("1.1.1.1"))
    }

    @Test
    fun `ipv6 addresses import`() {
        val report = DnsResolverImport.parse("2606:4700:4700::1111\n2001:4860:4860::8888")
        assertEquals(2, report.added)
    }

    @Test
    fun `hostname rules`() {
        assertTrue(DnsResolverImport.looksHostname("dns.google"))
        assertTrue(DnsResolverImport.looksHostname("one.one.one.one"))
        assertFalse(DnsResolverImport.looksHostname("localhost"))
        assertFalse(DnsResolverImport.looksHostname("-bad.example"))
        assertFalse(DnsResolverImport.looksHostname(""))
    }
}
