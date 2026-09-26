package net.gozar.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSelectScoreTest {

    @Test
    fun steadyServerBeatsOneLuckySample() {
        val s = AutoSelectScore()
        listOf(120, 125, 118, 122).forEach { s.record("steady", it) }
        listOf(60, null, null, 400).forEach { s.record("flaky", it) }
        assertEquals("steady", s.best(listOf("steady", "flaky"))!!.first)
    }

    @Test
    fun serverThatNeverAnsweredHasNoScore() {
        val s = AutoSelectScore()
        s.record("dead", null); s.record("dead", null)
        assertNull(s.score("dead"))
        assertNull(s.best(listOf("dead")))
    }

    @Test
    fun smallDifferencesDoNotCauseASwitch() {
        val s = AutoSelectScore()
        repeat(3) { s.record("cur", 200); s.record("cand", 180) }
        assertFalse(s.shouldSwitch("cur", "cand"))
        repeat(3) { s.record("fast", 90) }
        assertTrue(s.shouldSwitch("cur", "fast"))
    }

    @Test
    fun currentServerThatStoppedAnsweringIsLeft() {
        val s = AutoSelectScore()
        s.record("cur", 100); s.record("cur", null); s.record("cur", null)
        s.record("cand", 300)
        assertTrue(s.failingNow("cur"))
        assertTrue(s.shouldSwitch("cur", "cand"))
    }

    @Test
    fun historyIsBoundedToTheWindow() {
        val s = AutoSelectScore()
        repeat(AutoSelectScore.WINDOW) { s.record("a", null) }
        repeat(AutoSelectScore.WINDOW) { s.record("a", 100) }
        assertEquals(100.0, s.score("a")!!, 0.001)
    }
}
