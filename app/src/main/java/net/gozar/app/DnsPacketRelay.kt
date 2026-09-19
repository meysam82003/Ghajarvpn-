package net.gozar.app

/**
 * The IP-level half of DNS-only mode: reading a DNS query out of a tun packet
 * and building the reply packet that goes back in.
 *
 * All of it is pure functions over byte arrays, with no sockets and no Android
 * types, for one reason: this is the code where an off-by-one produces a
 * malformed packet that Android drops silently, and silent packet loss is the
 * hardest kind of bug to find from a phone. Pure means it can be tested, and
 * the checksums in particular are tested against hand-computed values.
 *
 * Only IPv4 + UDP is handled here, and the service is explicit about that
 * rather than quietly dropping the rest - see [GhajarDnsOnlyService], which
 * routes only the chosen resolver's own address into the tun, so nothing else
 * should arrive in the first place.
 */
object DnsPacketRelay {

    const val PROTO_UDP = 17
    const val PROTO_TCP = 6

    /** The smallest IPv4 header, in bytes. */
    private const val IP4_MIN_HEADER = 20
    private const val UDP_HEADER = 8

    /**
     * One DNS query lifted out of a tun packet.
     *
     * The addresses and ports are kept so the reply can be built as the exact
     * mirror of the request. A client will ignore a reply whose source does
     * not match the address it sent to, so "send it back to the app" means
     * reproducing all four of these swapped, not just the payload.
     */
    data class Query(
        val sourceIp: ByteArray,
        val destinationIp: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
        /** The request's IPv4 identification field, echoed for traceability. */
        val ipId: Int
    ) {
        // Generated equals on a data class with ByteArray members compares by
        // reference, which for a packet is never what is meant.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Query) return false
            return sourcePort == other.sourcePort &&
                destinationPort == other.destinationPort &&
                ipId == other.ipId &&
                sourceIp.contentEquals(other.sourceIp) &&
                destinationIp.contentEquals(other.destinationIp) &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = sourceIp.contentHashCode()
            result = 31 * result + destinationIp.contentHashCode()
            result = 31 * result + sourcePort
            result = 31 * result + destinationPort
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + ipId
            return result
        }
    }

    /**
     * Pulls a UDP DNS query out of an IPv4 packet, or returns null.
     *
     * Null covers every packet this mode is not for: IPv6, a protocol other
     * than UDP, a truncated header, a fragment, or a port that is not 53.
     * Returning null rather than throwing is deliberate - the read loop sees
     * whatever the OS hands it, and one odd packet must not end the session.
     *
     * Fragments are refused rather than reassembled. A DNS query that needs
     * fragmenting is already outside what this mode handles, and a
     * half-implemented reassembler is a memory-exhaustion bug waiting for a
     * hostile network.
     */
    fun parseUdpQuery(packet: ByteArray, length: Int = packet.size): Query? {
        if (length < IP4_MIN_HEADER) return null
        val version = (packet[0].toInt() and 0xF0) shr 4
        if (version != 4) return null

        val headerWords = packet[0].toInt() and 0x0F
        val headerLength = headerWords * 4
        if (headerWords < 5 || headerLength > length) return null

        val totalLength = u16(packet, 2)
        if (totalLength < headerLength || totalLength > length) return null

        // Fragment offset and the MF flag both live in bytes 6-7. Anything but
        // a lone, unfragmented packet is out of scope.
        val flagsAndOffset = u16(packet, 6)
        val moreFragments = flagsAndOffset and 0x2000 != 0
        val fragmentOffset = flagsAndOffset and 0x1FFF
        if (moreFragments || fragmentOffset != 0) return null

        if ((packet[9].toInt() and 0xFF) != PROTO_UDP) return null

        val udpStart = headerLength
        if (udpStart + UDP_HEADER > totalLength) return null
        val sourcePort = u16(packet, udpStart)
        val destinationPort = u16(packet, udpStart + 2)
        if (destinationPort != 53) return null

        val udpLength = u16(packet, udpStart + 4)
        if (udpLength < UDP_HEADER) return null
        val payloadLength = minOf(udpLength - UDP_HEADER, totalLength - udpStart - UDP_HEADER)
        if (payloadLength <= 0) return null

        val payloadStart = udpStart + UDP_HEADER
        return Query(
            sourceIp = packet.copyOfRange(12, 16),
            destinationIp = packet.copyOfRange(16, 20),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = packet.copyOfRange(payloadStart, payloadStart + payloadLength),
            ipId = u16(packet, 4)
        )
    }

    /**
     * Builds the IPv4+UDP packet carrying [answer] back to the asking app.
     *
     * The addresses are mirrored: the reply's source is the address the query
     * was sent to, and its destination is where the query came from. A reply
     * whose source is anything else is discarded by the client's socket, which
     * looks exactly like the resolver never answering.
     *
     * Both checksums are computed. The UDP checksum is optional in IPv4 and
     * could legally be zero, but Android's own stack and some apps' sockets
     * are stricter than the RFC in practice, and a correct checksum costs one
     * pass over a packet that is at most a few hundred bytes.
     */
    fun buildUdpReply(query: Query, answer: ByteArray): ByteArray {
        val udpLength = UDP_HEADER + answer.size
        val totalLength = IP4_MIN_HEADER + udpLength
        val out = ByteArray(totalLength)

        out[0] = 0x45                      // IPv4, 5-word header
        out[1] = 0                         // no DSCP, no ECN
        put16(out, 2, totalLength)
        put16(out, 4, query.ipId)
        put16(out, 6, 0x4000)              // don't fragment
        out[8] = 64                        // TTL: this packet never leaves the phone
        out[9] = PROTO_UDP.toByte()
        put16(out, 10, 0)                  // checksum, filled in below
        // Mirrored, not copied.
        query.destinationIp.copyInto(out, 12)
        query.sourceIp.copyInto(out, 16)
        put16(out, 10, checksum(out, 0, IP4_MIN_HEADER))

        val udp = IP4_MIN_HEADER
        put16(out, udp, query.destinationPort)
        put16(out, udp + 2, query.sourcePort)
        put16(out, udp + 4, udpLength)
        put16(out, udp + 6, 0)
        answer.copyInto(out, udp + UDP_HEADER)
        put16(out, udp + 6, udpChecksum(out))

        return out
    }

    /**
     * The one's-complement sum RFC 1071 defines, over [length] bytes from [from].
     */
    internal fun checksum(data: ByteArray, from: Int, length: Int): Int {
        var sum = 0L
        var at = from
        val end = from + length
        while (at + 1 < end) {
            sum += u16(data, at).toLong()
            at += 2
        }
        // An odd trailing byte is the high half of a padded word.
        if (at < end) sum += ((data[at].toInt() and 0xFF) shl 8).toLong()
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /**
     * The UDP checksum, which covers a pseudo-header as well as the datagram.
     *
     * The pseudo-header - both addresses, the protocol and the UDP length - is
     * why this cannot reuse [checksum] directly: a UDP checksum computed over
     * the datagram alone is wrong, and wrong in the way that gets the packet
     * dropped without a word.
     */
    internal fun udpChecksum(packet: ByteArray): Int {
        val udp = IP4_MIN_HEADER
        val udpLength = u16(packet, udp + 4)
        var sum = 0L

        // Pseudo-header: source and destination addresses, zero, protocol,
        // and the UDP length repeated.
        for (at in 12 until 20 step 2) sum += u16(packet, at).toLong()
        sum += PROTO_UDP.toLong()
        sum += udpLength.toLong()

        var at = udp
        val end = udp + udpLength
        while (at + 1 < end) {
            sum += u16(packet, at).toLong()
            at += 2
        }
        if (at < end) sum += ((packet[at].toInt() and 0xFF) shl 8).toLong()

        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val result = (sum.inv() and 0xFFFF).toInt()
        // Zero means "no checksum" on the wire, so the RFC says send all-ones
        // instead. Without this line one datagram in 65536 arrives unchecked.
        return if (result == 0) 0xFFFF else result
    }

    private fun u16(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun put16(data: ByteArray, at: Int, value: Int) {
        data[at] = ((value shr 8) and 0xFF).toByte()
        data[at + 1] = (value and 0xFF).toByte()
    }
}
