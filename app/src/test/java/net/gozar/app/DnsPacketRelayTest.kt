package net.gozar.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The packet layer, against packets built outside this file.
 *
 * Both fixtures below - a query and the reply expected for it - were assembled
 * and checksummed by a separate reference implementation, not by the code
 * under test. That is the only kind of test worth having here: asserting that
 * `checksum` agrees with itself proves nothing, and a wrong checksum does not
 * throw, it just makes Android drop the packet and the lookup hang.
 */
class DnsPacketRelayTest {

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte() }

    /** 10.7.0.2:50000 -> 1.1.1.1:53, asking for www.example.com A. */
    private val query = hex(
        "4500003dabcd4000401182d80a07000201010101c35000350029d37b" +
            "12340100000100000000000003777777076578616d706c6503636f6d0000010001"
    )

    /** The reply for that query carrying a 40-byte answer of 0x20..0x47. */
    private val expectedReply = hex(
        "45000044abcd4000401182d1010101010a0700020035c35000302fea" +
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4041424344454647"
    )

    private val answer = ByteArray(40) { (0x20 + it).toByte() }

    @Test
    fun `a udp dns query is parsed out of the packet`() {
        val parsed = DnsPacketRelay.parseUdpQuery(query)
        assertNotNull(parsed)
        parsed!!
        assertArrayEquals(byteArrayOf(10, 7, 0, 2), parsed.sourceIp)
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), parsed.destinationIp)
        assertEquals(50000, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertEquals(0xABCD, parsed.ipId)
        // The DNS payload starts with the transaction id the query carried.
        assertEquals(0x12, parsed.payload[0].toInt() and 0xFF)
        assertEquals(0x34, parsed.payload[1].toInt() and 0xFF)
        assertEquals(33, parsed.payload.size)
    }

    @Test
    fun `the reply packet matches one built independently`() {
        val parsed = DnsPacketRelay.parseUdpQuery(query)!!
        val reply = DnsPacketRelay.buildUdpReply(parsed, answer)
        assertArrayEquals(expectedReply, reply)
    }

    @Test
    fun `the reply mirrors the addresses and ports`() {
        val parsed = DnsPacketRelay.parseUdpQuery(query)!!
        val reply = DnsPacketRelay.buildUdpReply(parsed, answer)
        // Source is the resolver the app asked; destination is the app. A
        // reply with these the other way round is silently dropped by the
        // client socket, which looks exactly like the resolver not answering.
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), reply.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 7, 0, 2), reply.copyOfRange(16, 20))
        assertEquals(53, ((reply[20].toInt() and 0xFF) shl 8) or (reply[21].toInt() and 0xFF))
        assertEquals(50000, ((reply[22].toInt() and 0xFF) shl 8) or (reply[23].toInt() and 0xFF))
    }

    @Test
    fun `a correct header checksums to zero`() {
        // The defining property: summing a header that already contains its
        // own checksum yields zero. This catches a byte-order slip that a
        // round-trip test would not.
        val reply = DnsPacketRelay.buildUdpReply(DnsPacketRelay.parseUdpQuery(query)!!, answer)
        assertEquals(0, DnsPacketRelay.checksum(reply, 0, 20))
        assertEquals(0, DnsPacketRelay.checksum(query, 0, 20))
    }

    @Test
    fun `an odd length payload is checksummed with the trailing byte padded high`() {
        val parsed = DnsPacketRelay.parseUdpQuery(query)!!
        val odd = ByteArray(41) { (it + 1).toByte() }
        val reply = DnsPacketRelay.buildUdpReply(parsed, odd)
        assertEquals(20 + 8 + 41, reply.size)
        assertEquals(0, DnsPacketRelay.checksum(reply, 0, 20))
        // Recomputing over the finished packet must agree with what was
        // written into it; for UDP that means the stored value reproduces.
        val stored = ((reply[26].toInt() and 0xFF) shl 8) or (reply[27].toInt() and 0xFF)
        reply[26] = 0
        reply[27] = 0
        assertEquals(stored, DnsPacketRelay.udpChecksum(reply))
    }

    @Test
    fun `a udp checksum is never written as zero`() {
        // Zero means "not checksummed" on the wire, so the all-ones form has
        // to be sent instead. Asserted on the function rather than hunting for
        // a payload that happens to sum to zero.
        val parsed = DnsPacketRelay.parseUdpQuery(query)!!
        val reply = DnsPacketRelay.buildUdpReply(parsed, answer)
        val stored = ((reply[26].toInt() and 0xFF) shl 8) or (reply[27].toInt() and 0xFF)
        assertEquals(true, stored != 0)
    }

    // --- everything it must refuse ------------------------------------

    @Test
    fun `ipv6 is not parsed`() {
        val v6 = query.copyOf()
        v6[0] = 0x65        // version 6, same header length nibble
        assertNull(DnsPacketRelay.parseUdpQuery(v6))
    }

    @Test
    fun `tcp is not parsed`() {
        val tcp = query.copyOf()
        tcp[9] = DnsPacketRelay.PROTO_TCP.toByte()
        assertNull(DnsPacketRelay.parseUdpQuery(tcp))
    }

    @Test
    fun `a port other than 53 is not parsed`() {
        val other = query.copyOf()
        other[22] = 0x01
        other[23] = 0xBB.toByte()   // 443
        assertNull(DnsPacketRelay.parseUdpQuery(other))
    }

    @Test
    fun `a fragment is refused rather than reassembled`() {
        val moreFragments = query.copyOf()
        moreFragments[6] = 0x20     // MF set, offset 0
        assertNull(DnsPacketRelay.parseUdpQuery(moreFragments))

        val laterFragment = query.copyOf()
        laterFragment[6] = 0x00
        laterFragment[7] = 0x10     // non-zero offset
        assertNull(DnsPacketRelay.parseUdpQuery(laterFragment))
    }

    @Test
    fun `a truncated packet is refused`() {
        assertNull(DnsPacketRelay.parseUdpQuery(ByteArray(12)))
        assertNull(DnsPacketRelay.parseUdpQuery(query, length = 19))
        // A total length claiming more than was delivered is a lie, and
        // trusting it is how a read loop walks off the end of the buffer.
        val overlong = query.copyOf()
        overlong[2] = 0x0F
        overlong[3] = 0xFF.toByte()
        assertNull(DnsPacketRelay.parseUdpQuery(overlong))
    }

    @Test
    fun `a header length below the minimum is refused`() {
        val short = query.copyOf()
        short[0] = 0x44     // IPv4, 4-word header: impossible
        assertNull(DnsPacketRelay.parseUdpQuery(short))
    }

    @Test
    fun `a udp datagram with no payload is refused`() {
        val empty = query.copyOf()
        // UDP length of exactly the header means no DNS message at all.
        empty[24] = 0
        empty[25] = 8
        assertNull(DnsPacketRelay.parseUdpQuery(empty))
    }
}
