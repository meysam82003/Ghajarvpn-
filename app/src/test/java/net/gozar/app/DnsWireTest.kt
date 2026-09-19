package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The codec reads packets from resolvers this app has no reason to trust -
 * that is the whole point of scanning them - so a malformed or hostile reply
 * has to come back as an exception, never as a wrong answer or a hang.
 */
class DnsWireTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** A well-formed reply for one A record, with a compressed answer name. */
    private fun replyFor(
        id: Int = 0x1234,
        flags: Int = 0x8180,
        ip: List<Int> = listOf(93, 184, 216, 34),
        qname: List<String> = listOf("example", "com")
    ): ByteArray {
        val out = mutableListOf<Int>()
        fun short(v: Int) { out.add((v shr 8) and 0xFF); out.add(v and 0xFF) }
        short(id); short(flags); short(1); short(1); short(0); short(0)
        qname.forEach { label -> out.add(label.length); label.forEach { out.add(it.code) } }
        out.add(0); short(DnsWire.TYPE_A); short(1)
        // Answer: a pointer back to the question's name at offset 12.
        out.add(0xC0); out.add(12)
        short(DnsWire.TYPE_A); short(1)
        short(0); short(300)                    // ttl
        short(ip.size)
        ip.forEach { out.add(it) }
        return ByteArray(out.size) { out[it].toByte() }
    }

    @Test
    fun `a query carries the id the caller chose`() {
        val packet = DnsWire.query("example.com", DnsWire.TYPE_A, 0xBEEF)
        assertEquals(0xBE, packet[0].toInt() and 0xFF)
        assertEquals(0xEF, packet[1].toInt() and 0xFF)
        // One question, no answers.
        assertEquals(1, ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF))
        assertEquals(0, ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF))
    }

    @Test
    fun `a query encodes the name as length-prefixed labels`() {
        val packet = DnsWire.query("a.bc", DnsWire.TYPE_A, 1)
        // header is 12 bytes, then 1'a' 2'bc' 0
        assertEquals(1, packet[12].toInt())
        assertEquals('a'.code, packet[13].toInt())
        assertEquals(2, packet[14].toInt())
        assertEquals('b'.code, packet[15].toInt())
        assertEquals('c'.code, packet[16].toInt())
        assertEquals(0, packet[17].toInt())
    }

    @Test
    fun `a trailing dot is not an empty label`() {
        DnsWire.query("example.com.", DnsWire.TYPE_A, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty label is rejected`() {
        DnsWire.query("example..com", DnsWire.TYPE_A, 1)
    }

    @Test
    fun `an A record is read back, following the compression pointer`() {
        val answer = DnsWire.parse(replyFor(), 0x1234, "example.com")
        assertEquals(DnsWire.RCODE_NOERROR, answer.rcode)
        assertEquals(listOf("93.184.216.34"), answer.addresses)
        assertFalse(answer.mismatched)
    }

    @Test
    fun `a reply for a different question is flagged`() {
        val answer = DnsWire.parse(
            replyFor(qname = listOf("other", "com")), 0x1234, "example.com"
        )
        assertTrue(answer.mismatched)
    }

    @Test(expected = DnsWire.Malformed::class)
    fun `a reply with the wrong id is refused`() {
        DnsWire.parse(replyFor(id = 0x4321), 0x1234, "example.com")
    }

    @Test(expected = DnsWire.Malformed::class)
    fun `a query packet is not accepted as a response`() {
        DnsWire.parse(replyFor(flags = 0x0100), 0x1234, "example.com")
    }

    @Test
    fun `a response code is reported rather than thrown`() {
        val answer = DnsWire.parse(
            replyFor(flags = 0x8183, ip = listOf(0, 0, 0, 0)), 0x1234, "example.com"
        )
        assertEquals(DnsWire.RCODE_NXDOMAIN, answer.rcode)
    }

    @Test(expected = DnsWire.Malformed::class)
    fun `a truncated packet is refused`() {
        DnsWire.parse(bytes(0x12, 0x34, 0x81), 0x1234, "example.com")
    }

    /** A packet whose name points at itself must not hang the parser. */
    @Test(expected = DnsWire.Malformed::class)
    fun `a compression pointer loop is refused`() {
        val out = mutableListOf<Int>()
        fun short(v: Int) { out.add((v shr 8) and 0xFF); out.add(v and 0xFF) }
        short(0x1234); short(0x8180); short(1); short(0); short(0); short(0)
        // A question whose name is a pointer to itself at offset 12.
        out.add(0xC0); out.add(12)
        short(DnsWire.TYPE_A); short(1)
        DnsWire.parse(ByteArray(out.size) { out[it].toByte() }, 0x1234, "example.com")
    }

    @Test(expected = DnsWire.Malformed::class)
    fun `a pointer past the end of the packet is refused`() {
        val out = mutableListOf<Int>()
        fun short(v: Int) { out.add((v shr 8) and 0xFF); out.add(v and 0xFF) }
        short(0x1234); short(0x8180); short(1); short(0); short(0); short(0)
        out.add(0xC0); out.add(200)
        short(DnsWire.TYPE_A); short(1)
        DnsWire.parse(ByteArray(out.size) { out[it].toByte() }, 0x1234, "example.com")
    }

    @Test
    fun `an AAAA record is read as an IPv6 address`() {
        val out = mutableListOf<Int>()
        fun short(v: Int) { out.add((v shr 8) and 0xFF); out.add(v and 0xFF) }
        short(0x1234); short(0x8180); short(1); short(1); short(0); short(0)
        listOf("example", "com").forEach { l -> out.add(l.length); l.forEach { out.add(it.code) } }
        out.add(0); short(DnsWire.TYPE_AAAA); short(1)
        out.add(0xC0); out.add(12)
        short(DnsWire.TYPE_AAAA); short(1); short(0); short(300); short(16)
        // 2606:2800:220:1:248:1893:25c8:1946
        listOf(0x26, 0x06, 0x28, 0x00, 0x02, 0x20, 0x00, 0x01,
               0x02, 0x48, 0x18, 0x93, 0x25, 0xc8, 0x19, 0x46).forEach { out.add(it) }
        val answer = DnsWire.parse(ByteArray(out.size) { out[it].toByte() }, 0x1234, "example.com")
        assertEquals(listOf("2606:2800:220:1:248:1893:25c8:1946"), answer.addresses)
    }

    /** The finding the scan exists for: a fast answer that is a lie. */
    @Test
    fun `sinkhole answers are recognised`() {
        assertTrue(GhajarDnsLab.looksManufactured("10.10.34.36"))
        assertTrue(GhajarDnsLab.looksManufactured("10.10.34.1"))
        assertTrue(GhajarDnsLab.looksManufactured("0.0.0.0"))
        assertTrue(GhajarDnsLab.looksManufactured("127.0.0.1"))
        assertTrue(GhajarDnsLab.looksManufactured("203.0.113.9"))
        assertFalse(GhajarDnsLab.looksManufactured("93.184.216.34"))
        assertFalse(GhajarDnsLab.looksManufactured("142.250.185.110"))
        // 10.10.44.x is an ordinary private address, not the sinkhole block.
        assertFalse(GhajarDnsLab.looksManufactured("10.10.44.5"))
    }

    @Test
    fun `every catalogue entry is addressed for its transport`() {
        GhajarDnsLab.Catalogue.forEach { r ->
            if (r.transport == DnsTransport.DOH) {
                assertTrue("${r.name} DoH needs a URL: ${r.address}", r.address.startsWith("https://"))
            } else {
                assertFalse("${r.name} must not be a URL: ${r.address}", r.address.contains("://"))
            }
        }
        // Ids have to be unique or the scan's result map collapses entries.
        val ids = GhajarDnsLab.Catalogue.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
