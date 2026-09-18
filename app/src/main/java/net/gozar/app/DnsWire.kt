package net.gozar.app

/**
 * A minimal DNS wire codec: enough to ask for A/AAAA records and read the
 * answer back, with no dependency and no platform resolver in the way.
 *
 * InetAddress.getAllByName cannot do this job. It asks whatever resolver the
 * system is configured with, so it can neither measure a specific resolver nor
 * tell a real answer from a poisoned one - and both are the entire point of
 * scanning resolvers. The same packet is used over UDP, over TCP with its
 * two-byte length prefix, over TLS, and as the body of a DoH request, because
 * RFC 8484 carries exactly this format.
 */
internal object DnsWire {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    /** Response codes worth naming when a resolver answers but refuses. */
    const val RCODE_NOERROR = 0
    const val RCODE_FORMERR = 1
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_NOTIMP = 4
    const val RCODE_REFUSED = 5

    data class Answer(
        val rcode: Int,
        val addresses: List<String>,
        /** True when the reply's question did not match the one we asked. */
        val mismatched: Boolean = false
    )

    class Malformed(message: String) : IllegalArgumentException(message)

    /**
     * A query packet for [host].
     *
     * [id] is the caller's, so a UDP reply can be matched to its request - an
     * unsolicited or late packet with a different id is not this answer.
     * Recursion is requested, and no EDNS record is added: a bare query is
     * what the widest range of resolvers, including the deliberately broken
     * ones this scan exists to find, will answer.
     */
    fun query(host: String, type: Int, id: Int): ByteArray {
        val labels = host.trim().trim('.').split('.')
        require(labels.isNotEmpty() && labels.all { it.isNotEmpty() }) { "empty label in $host" }
        labels.forEach { require(it.length <= 63) { "label too long in $host" } }

        val out = ArrayList<Byte>(32 + host.length)
        fun short(value: Int) {
            out.add(((value shr 8) and 0xFF).toByte())
            out.add((value and 0xFF).toByte())
        }
        short(id)
        short(0x0100)   // standard query, recursion desired
        short(1)        // one question
        short(0)        // no answers
        short(0)        // no authority
        short(0)        // no additional
        labels.forEach { label ->
            out.add(label.length.toByte())
            label.forEach { ch -> out.add(ch.code.toByte()) }
        }
        out.add(0)      // root label
        short(type)
        short(1)        // IN
        return out.toByteArray()
    }

    /**
     * Reads the addresses out of a reply.
     *
     * Names are decompressed, because a resolver is free to point the answer's
     * name at the question's, and a CNAME chain is followed by simply taking
     * every A/AAAA record in the section - which is what a client needs and
     * what every stub resolver does.
     */
    fun parse(packet: ByteArray, expectId: Int, expectHost: String): Answer {
        if (packet.size < 12) throw Malformed("short packet (${packet.size} bytes)")
        val reader = Reader(packet)
        val id = reader.short()
        val flags = reader.short()
        val questions = reader.short()
        val answers = reader.short()
        reader.short()  // authority count
        reader.short()  // additional count

        if (id != expectId) throw Malformed("id $id is not the $expectId we asked")
        if (flags and 0x8000 == 0) throw Malformed("not a response")
        val rcode = flags and 0x000F

        var mismatched = false
        repeat(questions) {
            val name = reader.name()
            reader.short()  // qtype
            reader.short()  // qclass
            if (!name.equals(expectHost.trim().trim('.'), ignoreCase = true)) mismatched = true
        }

        val found = mutableListOf<String>()
        repeat(answers) {
            reader.name()
            val type = reader.short()
            reader.short()          // class
            reader.int()            // ttl
            val length = reader.short()
            val start = reader.position
            when {
                type == TYPE_A && length == 4 ->
                    found += (0 until 4).joinToString(".") { (packet[start + it].toInt() and 0xFF).toString() }
                type == TYPE_AAAA && length == 16 ->
                    found += (0 until 8).joinToString(":") { i ->
                        val hi = packet[start + i * 2].toInt() and 0xFF
                        val lo = packet[start + i * 2 + 1].toInt() and 0xFF
                        "%x".format((hi shl 8) or lo)
                    }
            }
            reader.position = start + length
        }
        return Answer(rcode, found, mismatched)
    }

    fun rcodeName(rcode: Int): String = when (rcode) {
        RCODE_NOERROR -> "NOERROR"
        RCODE_FORMERR -> "FORMERR"
        RCODE_SERVFAIL -> "SERVFAIL"
        RCODE_NXDOMAIN -> "NXDOMAIN"
        RCODE_NOTIMP -> "NOTIMP"
        RCODE_REFUSED -> "REFUSED"
        else -> "RCODE$rcode"
    }

    private class Reader(private val data: ByteArray) {
        var position = 0

        fun byte(): Int {
            if (position >= data.size) throw Malformed("read past the end of the packet")
            return data[position++].toInt() and 0xFF
        }

        fun short(): Int = (byte() shl 8) or byte()

        fun int(): Long {
            var value = 0L
            repeat(4) { value = (value shl 8) or byte().toLong() }
            return value
        }

        /**
         * A possibly compressed name.
         *
         * Pointer following is bounded by a hop budget: a packet that points a
         * name at itself is a loop, and this parser reads packets from
         * resolvers that are actively hostile.
         */
        fun name(): String {
            val parts = mutableListOf<String>()
            var hops = 0
            var cursor = position
            var jumped = false
            while (true) {
                if (cursor >= data.size) throw Malformed("name runs past the packet")
                val length = data[cursor].toInt() and 0xFF
                when {
                    length == 0 -> {
                        cursor++
                        if (!jumped) position = cursor
                        return parts.joinToString(".")
                    }
                    length and 0xC0 == 0xC0 -> {
                        if (cursor + 1 >= data.size) throw Malformed("truncated compression pointer")
                        if (++hops > 16) throw Malformed("compression pointer loop")
                        val target = ((length and 0x3F) shl 8) or (data[cursor + 1].toInt() and 0xFF)
                        if (!jumped) {
                            position = cursor + 2
                            jumped = true
                        }
                        if (target >= data.size) throw Malformed("compression pointer out of range")
                        cursor = target
                    }
                    else -> {
                        val start = cursor + 1
                        val end = start + length
                        if (end > data.size) throw Malformed("label runs past the packet")
                        parts += String(data, start, length, Charsets.ISO_8859_1)
                        cursor = end
                        if (!jumped) position = cursor
                    }
                }
            }
        }
    }
}
