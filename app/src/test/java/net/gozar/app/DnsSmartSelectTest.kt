package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The selection rules, which are the part of automatic DNS most likely to be
 * quietly wrong: every failure mode here looks like "DNS is flaky" rather than
 * like a bug in a selector.
 */
class DnsSmartSelectTest {

    private fun resolver(ip: String) = DnsResolver(ip, DnsTransport.UDP, ip)

    private fun good(ms: Int, success: Int = 100) = DnsVerdict(
        id = "",
        health = DnsHealth.GOOD,
        latencyMs = ms,
        successPercent = success,
        recursive = true
    )

    private fun dead() = DnsVerdict(id = "", health = DnsHealth.BAD, successPercent = 0)

    private val fast = resolver("1.1.1.1")
    private val slow = resolver("8.8.8.8")
    private val third = resolver("9.9.9.9")
    private val all = listOf(fast, slow, third)

    @Before
    fun reset() {
        DnsSmartSelect.reset()
    }

    @Test
    fun `with nothing chosen it takes the best`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(20), slow.id to good(90)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertEquals(fast.id, picked?.id)
    }

    @Test
    fun `a manual pin is never overridden`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(20), slow.id to good(900)),
            currentId = slow.id,
            manuallyPinned = true,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertNull(picked)
        assertEquals("manual-pin", DnsSmartSelect.reason.value)
    }

    @Test
    fun `an unmeasured resolver is never selected`() {
        // Only the scan's own findings count. Being in the list is not health.
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = emptyMap(),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertNull(picked)
        assertEquals("none-healthy", DnsSmartSelect.reason.value)
    }

    @Test
    fun `a poisoned resolver is never selected however fast`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(
                fast.id to good(5).copy(poisoned = true),
                slow.id to good(300)
            ),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertEquals(slow.id, picked?.id)
    }

    @Test
    fun `a resolver whose recursion was never confirmed is not selected`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(10).copy(recursive = null)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertNull(picked)
    }

    @Test
    fun `stay means lookups fail rather than moving`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to dead(), slow.id to good(50)),
            currentId = fast.id,
            manuallyPinned = false,
            policy = DnsFailPolicy.STAY
        )
        assertNull(picked)
        assertEquals("failed-but-staying", DnsSmartSelect.reason.value)
    }

    @Test
    fun `next healthy moves off a dead resolver`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to dead(), slow.id to good(50)),
            currentId = fast.id,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertEquals(slow.id, picked?.id)
        assertEquals("failed-over", DnsSmartSelect.reason.value)
    }

    @Test
    fun `failing over with no alternative changes nothing`() {
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to dead()),
            currentId = fast.id,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertNull(picked)
    }

    @Test
    fun `a working resolver is not swapped for a marginally faster one`() {
        // 30ms apart, below the margin. Without this rule two similar
        // resolvers make the app flap, which the user experiences as DNS
        // breaking every minute.
        val start = 10_000_000L
        DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(slow.id to good(80)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start
        )
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(50), slow.id to good(80)),
            currentId = slow.id,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start + 120_000L
        )
        assertNull(picked)
        assertEquals("margin-too-small", DnsSmartSelect.reason.value)
    }

    @Test
    fun `a clearly better resolver is taken once the gap has passed`() {
        val start = 20_000_000L
        DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(slow.id to good(300)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start
        )
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(30), slow.id to good(300)),
            currentId = slow.id,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start + 120_000L
        )
        assertEquals(fast.id, picked?.id)
    }

    @Test
    fun `a better resolver is not taken before the gap has passed`() {
        val start = 30_000_000L
        DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(slow.id to good(300)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start
        )
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(30), slow.id to good(300)),
            currentId = slow.id,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start + 5_000L
        )
        assertNull(picked)
        assertEquals("too-soon", DnsSmartSelect.reason.value)
    }

    @Test
    fun `success rate outranks latency`() {
        // Nine-in-ten at 60ms beats six-in-ten at 20ms for holding a
        // connection open, and sorting on latency alone gets it backwards.
        val picked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(
                fast.id to good(20, success = 60),
                slow.id to good(60, success = 90)
            ),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY
        )
        assertEquals(slow.id, picked?.id)
    }

    @Test
    fun `a failed switch backs off and then recovers`() {
        val start = 40_000_000L
        DnsSmartSelect.switchFailed(now = start)
        val blocked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(20)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start + 1_000L
        )
        assertNull(blocked)
        assertEquals("backoff", DnsSmartSelect.reason.value)

        DnsSmartSelect.switchWorked()
        val allowed = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(20)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start + 2_000L
        )
        assertEquals(fast.id, allowed?.id)
    }

    @Test
    fun `backoff doubles`() {
        val start = 50_000_000L
        DnsSmartSelect.switchFailed(now = start)
        // 15s then 30s: the second failure must block past the first window.
        DnsSmartSelect.switchFailed(now = start)
        val stillBlocked = DnsSmartSelect.decide(
            resolvers = all,
            verdicts = mapOf(fast.id to good(20)),
            currentId = null,
            manuallyPinned = false,
            policy = DnsFailPolicy.NEXT_HEALTHY,
            now = start + 20_000L
        )
        assertNull(stillBlocked)
    }
}
