package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarWelcomeOverlayRulesTest {
    @Test fun autocloseWindowIsPositiveAndStable() {
        val ms = GhajarWelcomeOverlayRules.autocloseMs()
        assertTrue(ms in 1..10_000)
        assertEquals(GhajarWelcomeOverlayRules.AUTOCLOSE_MS, ms)
    }

    @Test fun frameIsHairlineAndNeverCropsThePoster() {
        assertTrue(GhajarWelcomeOverlayRules.FRAME_STROKE_DP <= 1f)
        assertTrue(GhajarWelcomeOverlayRules.FRAME_GAP_DP <= 2f)
    }

    @Test fun unknownPosterNameFallsBackToFirstPoster() {
        val fallback = GhajarWelcomeOverlayRules.resolvePoster("نه_این_نه_آن")
        assertEquals(GhajarWelcomeAssets.posters.first(), fallback)
    }

    @Test fun everyPosterNameResolvesToItself() {
        GhajarWelcomeAssets.posters.forEach { poster ->
            assertEquals(poster, GhajarWelcomeOverlayRules.resolvePoster(poster.name))
        }
    }

    @Test fun assetListStaysAtThirtyThreePosters() {
        assertEquals(33, GhajarWelcomeAssets.posters.size)
        assertTrue(GhajarWelcomeAssets.posters.all { it.resourceId != 0 })
    }

    @Test fun reserveRotationNeverReturnsSamePosterTwiceInARow() {
        var last: String? = null
        repeat(20) { seed ->
            val pick = GhajarWelcomeRotation.next(
                GhajarWelcomeAssets.posters.map { it.name },
                emptySet(), last, kotlin.random.Random(seed)
            )!!
            assertNotEquals(last, pick.name)
            last = pick.name
        }
    }
}
