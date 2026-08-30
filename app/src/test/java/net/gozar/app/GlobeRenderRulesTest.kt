package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Raster buffer rules of the home globe: sizes are capped independently of
 * screen density and step in fixed blocks so state-driven layout changes never
 * rebuild the whole texture pipeline.
 */
class GlobeRenderRulesTest {

    @Test fun phoneAndTabletSidesHitTheDensityCap() {
        assertEquals(384, globeBufferSize(1080))
        assertEquals(384, globeBufferSize(1440))
        assertEquals(384, globeBufferSize(4096))
    }

    @Test fun sizesMoveInFixed32PixelSteps() {
        assertEquals(288, globeBufferSize(400))
        assertEquals(288, globeBufferSize(410))
        assertEquals(128, globeBufferSize(200))
        assertEquals(0, globeBufferSize(400) % 32)
        assertEquals(0, globeBufferSize(200) % 32)
    }

    @Test fun tinyWindowsKeepAMinimumUsableGlobe() {
        assertEquals(96, globeBufferSize(0))
        assertEquals(96, globeBufferSize(1))
        assertEquals(96, globeBufferSize(100))
    }

    @Test fun sizeNeverShrinksWhenTheSideGrows() {
        var previous = globeBufferSize(0)
        for (side in 1..4096 step 7) {
            val current = globeBufferSize(side)
            assertTrue("size regressed at side=$side", current >= previous)
            previous = current
        }
    }
}
