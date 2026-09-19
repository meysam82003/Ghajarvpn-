package net.gozar.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Junk packets sent ahead of the real traffic, as Xray's freedom outbound
 * takes them.
 *
 * What this is for. A censor that fingerprints a connection by the first bytes
 * it sees on the wire has to decide what those bytes are. Sending one or more
 * packets of nothing in front of the handshake changes that first impression
 * without changing the handshake itself. MahsaNG exposes this as a list of
 * presets; this is the same idea with the presets named for what they do.
 *
 * It is not encryption and not a tunnel. The real traffic follows unchanged.
 * A server that does not tolerate an unexpected leading packet will simply
 * fail, which is why this is off by default.
 *
 * The emitted shape is checked against Xray-core's own infra/conf/freedom.go
 * at the version this app bundles (v1.260327.0):
 *
 *     "noises": [ { "type": "rand", "packet": "50-100",
 *                   "delay": "10-20", "applyTo": "ip" } ]
 *
 * with type one of rand, str, hex or base64. For rand, packet is a length
 * range and not a payload; for the other three it is the payload itself.
 * ParseNoise rejects anything else outright, so nothing here invents a name.
 */
object NoiseSpec {

    /** The preset ids, in the order the settings screen offers them. */
    val PRESETS = listOf("off", "light", "standard", "aggressive", "quic", "custom")

    /**
     * The JSON array for a preset, or null when there is nothing to emit.
     *
     * Null - not an empty array - is the "off" answer, because an empty
     * noises list is still a noises field and this app's promise is that a
     * config which never set this is byte-for-byte what it was before.
     */
    fun build(spec: String): JSONArray? {
        val text = spec.trim()
        if (text.isEmpty() || text == "off") return null
        return when (text) {
            // One short packet. The cheapest thing that changes the opening
            // bytes, and the one least likely to upset a strict server.
            "light" -> array(rand("10-30", "0-3"))
            // Two packets of ordinary size with a pause between them, so the
            // shape on the wire is not just "one odd datagram".
            "standard" -> array(rand("50-100", "5-15"), rand("50-100", "5-15"))
            // Three larger packets. Costs latency on every new connection and
            // is meant for a network that blocks the quieter settings.
            "aggressive" -> array(
                rand("200-400", "10-25"),
                rand("200-400", "10-25"),
                rand("200-400", "10-25")
            )
            // A QUIC initial's first byte has the long-header form bits set;
            // leading with one makes the opening look like a QUIC attempt on
            // a network that treats QUIC as ordinary traffic.
            "quic" -> array(hex("c00000000108", "0-5"))
            else -> parse(text)
        }
    }

    /**
     * A hand-written spec: one noise per line, `type:packet[:delay]`.
     *
     * Kept deliberately small and forgiving. A line that does not parse is
     * dropped rather than failing the whole connect, and a spec that produces
     * nothing at all comes back as null - the same answer as off.
     */
    private fun parse(text: String): JSONArray? {
        val out = JSONArray()
        text.split('\n', ';').forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(':')
            if (parts.size < 2) return@forEach
            val type = parts[0].trim().lowercase()
            if (type !in TYPES) return@forEach
            val packet = parts[1].trim()
            if (packet.isEmpty()) return@forEach
            val delay = parts.getOrNull(2)?.trim().orEmpty()
            out.put(item(type, packet, delay))
        }
        return if (out.length() == 0) null else out
    }

    private val TYPES = setOf("rand", "str", "hex", "base64")

    /**
     * The same idea in the shape the udp *finalmask* noise takes.
     *
     * It is deliberately a separate function because the two shapes are not
     * the same, however alike they read. The freedom outbound's noise names a
     * type and puts the length range in `packet`; the mask's NoiseItem puts
     * the length range in `rand` and reserves `packet` for a literal payload -
     * and its Build() rejects an item that sets both. Reusing one for the
     * other produces a config the core refuses.
     *
     * Only random lengths are offered here. A fixed payload repeated in front
     * of every datagram is itself a signature, which is the opposite of what
     * this is for.
     */
    fun buildMaskNoise(spec: String): JSONArray? {
        val text = spec.trim()
        if (text.isEmpty() || text == "off") return null
        val lengths = when (text) {
            "light" -> listOf("10-30" to "0-3")
            "aggressive" -> listOf("200-400" to "10-25", "200-400" to "10-25", "200-400" to "10-25")
            // "standard" and anything hand-written: a hand-written spec is in
            // the freedom format and does not transfer, so it gets the middle
            // setting rather than being silently dropped.
            else -> listOf("50-100" to "5-15", "50-100" to "5-15")
        }
        val out = JSONArray()
        lengths.forEach { (rand, delay) ->
            out.put(JSONObject().put("rand", rand).put("delay", delay))
        }
        return out
    }

    private fun rand(length: String, delay: String) = item("rand", length, delay)

    private fun hex(payload: String, delay: String) = item("hex", payload, delay)

    private fun item(type: String, packet: String, delay: String): JSONObject {
        val o = JSONObject().put("type", type).put("packet", packet)
        // Both fields are optional in the core. delay is written only when
        // asked for, and applyTo is always "ip" - this app has no reason to
        // noise one address family and not the other, and naming it keeps the
        // emitted config readable in a diagnostics export.
        if (delay.isNotEmpty()) o.put("delay", delay)
        o.put("applyTo", "ip")
        return o
    }

    private fun array(vararg items: JSONObject): JSONArray =
        JSONArray().apply { items.forEach { put(it) } }
}
