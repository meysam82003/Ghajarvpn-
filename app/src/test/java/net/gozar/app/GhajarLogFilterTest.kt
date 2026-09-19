package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The log filter.
 *
 * Worth testing because its failure mode is invisible: a filter that silently
 * drops the one line you were looking for looks exactly like one that found
 * nothing to show.
 */
class GhajarLogFilterTest {

    private fun entry(
        tag: String,
        level: GhajarLogLevel = GhajarLogLevel.INFO,
        message: String = "m",
        timeMs: Long = 0
    ) = GhajarLogEntry(timeMs, level, tag, message)

    // ---- which source a tag belongs to ----------------------------------

    @Test
    fun `the real tags in this app land where a user would look for them`() {
        // Every tag below is a TAG constant that exists in this codebase, not
        // an invented one - a source that matches nothing is a chip that is
        // always empty.
        assertEquals(GhajarLogFilter.Source.CORE, GhajarLogFilter.sourceOf("GozarVpnService"))
        assertEquals(GhajarLogFilter.Source.CORE, GhajarLogFilter.sourceOf("XrayCore"))
        assertEquals(GhajarLogFilter.Source.CORE, GhajarLogFilter.sourceOf("GhajarConfigBuilder"))
        assertEquals(GhajarLogFilter.Source.TUNNEL, GhajarLogFilter.sourceOf("Psiphon"))
        assertEquals(GhajarLogFilter.Source.TUNNEL, GhajarLogFilter.sourceOf("Aether"))
        assertEquals(GhajarLogFilter.Source.TUNNEL, GhajarLogFilter.sourceOf("GhajarZeptun"))
        assertEquals(GhajarLogFilter.Source.TUNNEL, GhajarLogFilter.sourceOf("GhajarIke"))
        assertEquals(GhajarLogFilter.Source.TOR, GhajarLogFilter.sourceOf("Tor"))
        assertEquals(GhajarLogFilter.Source.APP, GhajarLogFilter.sourceOf("Startup"))
        assertEquals(GhajarLogFilter.Source.APP, GhajarLogFilter.sourceOf("Payment"))
    }

    @Test
    fun `an unknown tag is shown rather than dropped`() {
        // Nothing may vanish just because nobody listed its tag.
        assertEquals(GhajarLogFilter.Source.APP, GhajarLogFilter.sourceOf("SomethingNew"))
        assertEquals(GhajarLogFilter.Source.APP, GhajarLogFilter.sourceOf(""))
    }

    @Test
    fun `a tag added later is classified by what it is called`() {
        // The fallback is what keeps this from going stale silently.
        assertEquals(GhajarLogFilter.Source.DNS, GhajarLogFilter.sourceOf("GhajarDnsLab"))
        assertEquals(GhajarLogFilter.Source.TUNNEL, GhajarLogFilter.sourceOf("MySshThing"))
        assertEquals(GhajarLogFilter.Source.CORE, GhajarLogFilter.sourceOf("SomeVpnHelper"))
    }

    // ---- the level floor -------------------------------------------------

    @Test
    fun `a level chip means that level and worse`() {
        val all = listOf(
            entry("A", GhajarLogLevel.DEBUG),
            entry("A", GhajarLogLevel.INFO),
            entry("A", GhajarLogLevel.WARN),
            entry("A", GhajarLogLevel.ERROR),
            entry("A", GhajarLogLevel.CRASH)
        )
        assertEquals(5, GhajarLogFilter.apply(all, floor = GhajarLogFilter.Floor.ALL).size)
        assertEquals(4, GhajarLogFilter.apply(all, floor = GhajarLogFilter.Floor.INFO).size)
        assertEquals(3, GhajarLogFilter.apply(all, floor = GhajarLogFilter.Floor.WARN).size)
        assertEquals(2, GhajarLogFilter.apply(all, floor = GhajarLogFilter.Floor.ERROR).size)
    }

    @Test
    fun `a crash survives every level filter`() {
        // The one line nobody can afford to have hidden by a filter.
        val crash = listOf(entry("A", GhajarLogLevel.CRASH, "boom"))
        GhajarLogFilter.Floor.entries.forEach { floor ->
            assertEquals(
                "crash hidden by floor $floor",
                1, GhajarLogFilter.apply(crash, floor = floor).size
            )
        }
    }

    // ---- search ----------------------------------------------------------

    @Test
    fun `search matches the message and the tag but not the timestamp`() {
        val list = listOf(
            entry("Psiphon", message = "connected"),
            entry("Tor", message = "bootstrap 10%", timeMs = 1_600_000_000_000)
        )
        assertEquals(1, GhajarLogFilter.apply(list, query = "connect").size)
        assertEquals(1, GhajarLogFilter.apply(list, query = "psiphon").size)
        assertEquals(1, GhajarLogFilter.apply(list, query = "BOOTSTRAP").size)
        // The timestamp is formatting, not content: searching a year should
        // not return every line logged in it.
        assertEquals(0, GhajarLogFilter.apply(list, query = "2020").size)
    }

    @Test
    fun `a blank or whitespace query filters nothing`() {
        val list = listOf(entry("A"), entry("B"))
        assertEquals(2, GhajarLogFilter.apply(list, query = "").size)
        assertEquals(2, GhajarLogFilter.apply(list, query = "   ").size)
    }

    // ---- combining -------------------------------------------------------

    @Test
    fun `source level and search all apply together`() {
        val list = listOf(
            entry("Tor", GhajarLogLevel.ERROR, "circuit failed"),
            entry("Tor", GhajarLogLevel.INFO, "circuit built"),
            entry("Psiphon", GhajarLogLevel.ERROR, "circuit failed")
        )
        val out = GhajarLogFilter.apply(
            list,
            source = GhajarLogFilter.Source.TOR,
            floor = GhajarLogFilter.Floor.ERROR,
            query = "circuit"
        )
        assertEquals(1, out.size)
        assertEquals("circuit failed", out[0].message)
    }

    @Test
    fun `filtering preserves the order lines were logged in`() {
        val list = (1..5).map { entry("A", message = "line $it", timeMs = it.toLong()) }
        val out = GhajarLogFilter.apply(list, query = "line")
        assertEquals(list.map { it.message }, out.map { it.message })
    }

    // ---- the counts beside the chips -------------------------------------

    @Test
    fun `the counts add up and all is the whole buffer`() {
        val list = listOf(
            entry("Tor"), entry("Psiphon"), entry("Psiphon"),
            entry("Startup"), entry("GozarVpnService")
        )
        val counts = GhajarLogFilter.countsBySource(list)
        assertEquals(5, counts[GhajarLogFilter.Source.ALL])
        assertEquals(1, counts[GhajarLogFilter.Source.TOR])
        assertEquals(2, counts[GhajarLogFilter.Source.TUNNEL])
        assertEquals(1, counts[GhajarLogFilter.Source.APP])
        assertEquals(1, counts[GhajarLogFilter.Source.CORE])
        // Every source except ALL partitions the buffer, so they sum to it.
        val sum = GhajarLogFilter.Source.entries
            .filter { it != GhajarLogFilter.Source.ALL }
            .sumOf { counts[it] ?: 0 }
        assertEquals(5, sum)
    }

    @Test
    fun `a count matches what selecting that chip actually shows`() {
        // The number on a chip has to be the number of lines behind it, or it
        // is worse than no number.
        val list = listOf(
            entry("Tor"), entry("Psiphon"), entry("GhajarDnsLab"),
            entry("Startup"), entry("XrayCore"), entry("Aether")
        )
        val counts = GhajarLogFilter.countsBySource(list)
        GhajarLogFilter.Source.entries.forEach { source ->
            assertEquals(
                "chip $source lies about its count",
                GhajarLogFilter.apply(list, source = source).size,
                counts[source] ?: 0
            )
        }
    }

    @Test
    fun `an empty buffer counts zero everywhere and shows nothing`() {
        val counts = GhajarLogFilter.countsBySource(emptyList())
        assertEquals(0, counts[GhajarLogFilter.Source.ALL])
        assertTrue(GhajarLogFilter.apply(emptyList()).isEmpty())
    }

    @Test
    fun `the text export is one line per entry in order`() {
        val list = listOf(entry("A", message = "one"), entry("B", message = "two"))
        val text = GhajarLogFilter.asText(list)
        assertEquals(2, text.lines().size)
        assertTrue(text.lines()[0].contains("one"))
        assertTrue(text.lines()[1].contains("two"))
    }
}
