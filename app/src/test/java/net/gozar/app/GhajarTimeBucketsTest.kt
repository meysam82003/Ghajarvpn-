package net.gozar.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the bucket table against the real bot source (api/handlers/
 * TimeRangesHandler.php): its RANGES list is the authority for which stored
 * day counts a single offered range covers, and ServicesHandler filters with
 * an exact `Service_time =` match that cannot honour those buckets.
 */
class GhajarTimeBucketsTest {

    @Test fun monthlyBucketsAbsorbTheirOffByOneDayCounts() {
        // The reported bug: a 31-day plan under the "1 month" range.
        assertTrue(GhajarTimeBuckets.matches(30, 31))
        assertTrue(GhajarTimeBuckets.matches(30, 30))
        assertTrue(GhajarTimeBuckets.matches(60, 61))
        assertTrue(GhajarTimeBuckets.matches(90, 91))
        assertTrue(GhajarTimeBuckets.matches(120, 121))
        assertTrue(GhajarTimeBuckets.matches(180, 181))
    }

    @Test fun bucketsDoNotBleedIntoEachOther() {
        assertFalse(GhajarTimeBuckets.matches(30, 60))
        assertFalse(GhajarTimeBuckets.matches(30, 29))
        assertFalse(GhajarTimeBuckets.matches(30, 32))
        assertFalse(GhajarTimeBuckets.matches(60, 90))
        assertFalse(GhajarTimeBuckets.matches(180, 365))
        assertFalse(GhajarTimeBuckets.matches(365, 366))
    }

    @Test fun exactRangesHaveNoAliases() {
        assertTrue(GhajarTimeBuckets.matches(1, 1))
        assertFalse(GhajarTimeBuckets.matches(1, 2))
        assertTrue(GhajarTimeBuckets.matches(7, 7))
        assertFalse(GhajarTimeBuckets.matches(7, 8))
        assertTrue(GhajarTimeBuckets.matches(365, 365))
    }

    @Test fun unlimitedRangeKeepsOnlyUnlimitedPlans() {
        // The server skipped its filter entirely for day 0, so "unlimited"
        // used to list every plan.
        assertTrue(GhajarTimeBuckets.matches(0, 0))
        assertFalse(GhajarTimeBuckets.matches(0, 30))
        assertFalse(GhajarTimeBuckets.matches(0, 365))
    }

    @Test fun aPlanWithNoDayCountNeverMatchesARange() {
        assertFalse(GhajarTimeBuckets.matches(30, null))
        assertFalse(GhajarTimeBuckets.matches(0, null))
    }

    @Test fun anUnknownRangeFallsBackToAnExactMatch() {
        // A day count the server's RANGES table doesn't list must not silently
        // widen into a bucket.
        assertTrue(GhajarTimeBuckets.matches(45, 45))
        assertFalse(GhajarTimeBuckets.matches(45, 46))
    }
}
