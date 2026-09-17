package net.gozar.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the bug: the store's offline cache used to be keyed by panel id
 * alone, so switching category or time range (or account, or panel) while
 * offline showed a *different* filter's cached plans under the current
 * selection. These tests pin down the fix at the pure-logic level (no
 * Android Context available in a JVM unit test, so the actual
 * SharedPreferences-backed GhajarStoreCache itself isn't exercised here -
 * that would need Robolectric, which this module doesn't have).
 */
class GhajarStoreCacheKeyTest {
    @Test fun differentCategoryProducesADifferentKey_sameOtherwise() {
        val a = GhajarStoreCacheKey.of("acc1", "panel1", "cat1", 30)
        val b = GhajarStoreCacheKey.of("acc1", "panel1", "cat2", 30)
        assertNotEquals("changing category must change the key", a, b)
    }

    @Test fun differentTimeRangeProducesADifferentKey() {
        val a = GhajarStoreCacheKey.of("acc1", "panel1", "cat1", 30)
        val b = GhajarStoreCacheKey.of("acc1", "panel1", "cat1", 90)
        assertNotEquals("changing days must change the key", a, b)
    }

    @Test fun differentPanelProducesADifferentKey() {
        val a = GhajarStoreCacheKey.of("acc1", "panelA", "cat1", 30)
        val b = GhajarStoreCacheKey.of("acc1", "panelB", "cat1", 30)
        assertNotEquals("changing panel must change the key", a, b)
    }

    @Test fun differentAccountProducesADifferentKey() {
        val a = GhajarStoreCacheKey.of("acc1", "panel1", "cat1", 30)
        val b = GhajarStoreCacheKey.of("acc2", "panel1", "cat1", 30)
        assertNotEquals("relinking to a different account must change the key", a, b)
    }

    @Test fun sameParametersAlwaysProduceTheSameKey() {
        val a = GhajarStoreCacheKey.of("acc1", "panel1", "cat1", 30)
        val b = GhajarStoreCacheKey.of("acc1", "panel1", "cat1", 30)
        assertEquals(a, b)
    }

    @Test fun nullCategoryAndNullDaysAreDistinctFromRealValues() {
        val withNulls = GhajarStoreCacheKey.of("acc1", "panel1", null, null)
        val withValues = GhajarStoreCacheKey.of("acc1", "panel1", "", 0)
        // "no category selected" collapses to the same bucket as an empty
        // string / zero days by design (both mean "no filter"), but it must
        // never collide with an actual category id or a real days value.
        assertEquals(withNulls, withValues)
        assertNotEquals(withNulls, GhajarStoreCacheKey.of("acc1", "panel1", "0", null))
    }

    @Test fun freshCacheWithinMaxAgeIsUsable() {
        val now = 1_000_000L
        assertTrue(GhajarStoreCacheKey.isFresh(savedAtMs = now - 1000, nowMs = now))
    }

    @Test fun cacheOlderThanMaxAgeIsNeverUsable() {
        val now = GhajarStoreCacheKey.MAX_AGE_MS + 10_000
        val savedTooLongAgo = now - GhajarStoreCacheKey.MAX_AGE_MS - 1
        assertFalse(GhajarStoreCacheKey.isFresh(savedAtMs = savedTooLongAgo, nowMs = now))
    }

    @Test fun cacheExactlyAtMaxAgeIsStillUsable() {
        val now = GhajarStoreCacheKey.MAX_AGE_MS + 10_000
        val savedAtBoundary = now - GhajarStoreCacheKey.MAX_AGE_MS
        assertTrue(GhajarStoreCacheKey.isFresh(savedAtMs = savedAtBoundary, nowMs = now))
    }

    @Test fun neverSavedOrClockSkewedEntriesAreNotUsable() {
        assertFalse("a zero/unset timestamp must never be treated as fresh",
            GhajarStoreCacheKey.isFresh(savedAtMs = 0L, nowMs = 1_000_000L))
        assertFalse("a timestamp from the future (clock skew) must never be treated as fresh",
            GhajarStoreCacheKey.isFresh(savedAtMs = 2_000_000L, nowMs = 1_000_000L))
    }
}
