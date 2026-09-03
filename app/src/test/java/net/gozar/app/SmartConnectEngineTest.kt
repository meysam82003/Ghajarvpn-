package net.gozar.app

import org.junit.Assert.*
import org.junit.Test

class SmartConnectEngineTest {
    @Test fun gamingPrefersLowerLatencyAndJitter() {
        val a = ServerCandidate("a"); val b = ServerCandidate("b")
        val health = mapOf(
            "a" to ServerHealth(latencyMs = 55.0, jitterMs = 8.0, packetLossPercent = 1.0, successCount = 8, failureCount = 2),
            "b" to ServerHealth(latencyMs = 130.0, jitterMs = 70.0, packetLossPercent = 0.0, successCount = 10)
        )
        assertEquals("a", SmartConnectScorer.rank(listOf(a, b), health, SmartMode.GAMING).first().candidate.id)
    }

    @Test fun downloadUsesMeasuredThroughputWithoutInventingIt() {
        val slow = ServerCandidate("slow"); val fast = ServerCandidate("fast")
        val base = ServerHealth(latencyMs = 80.0, jitterMs = 10.0, packetLossPercent = 0.0, successCount = 10)
        val ranked = SmartConnectScorer.rank(listOf(slow, fast), mapOf("slow" to base.copy(throughputBytesPerSecond = 1_000_000), "fast" to base.copy(throughputBytesPerSecond = 20_000_000)), SmartMode.DOWNLOAD)
        assertEquals("fast", ranked.first().candidate.id)
    }

    @Test fun unhealthyServersAreTemporarilyExcludedButEmergencyCanUseThem() {
        val now = 1_000_000L
        val bad = ServerCandidate("bad"); val weak = ServerCandidate("weak")
        val health = mapOf(
            "bad" to ServerHealth(latencyMs = 10.0, jitterMs = 1.0, packetLossPercent = 0.0, successCount = 10, unhealthyUntil = now + 1000),
            "weak" to ServerHealth(latencyMs = 400.0, jitterMs = 50.0, packetLossPercent = 5.0, successCount = 5, failureCount = 1)
        )
        assertEquals("weak", SmartConnectScorer.rank(listOf(bad, weak), health, SmartMode.AUTO, now).first().candidate.id)
        assertTrue(SmartConnectScorer.rank(listOf(bad, weak), health, SmartMode.EMERGENCY, now).any { it.candidate.id == "bad" })
    }

    @Test fun unavailableProtocolNeverWins() {
        val ranked = SmartConnectScorer.rank(listOf(ServerCandidate("off", false), ServerCandidate("on")), emptyMap(), SmartMode.AUTO)
        assertEquals("on", ranked.first().candidate.id)
    }
}
