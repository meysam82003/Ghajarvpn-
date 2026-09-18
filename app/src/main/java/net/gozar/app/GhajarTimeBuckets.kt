package net.gozar.app

/**
 * The store's time-range filter has to be applied on this side, because the
 * two server endpoints disagree about what a "time range" is.
 *
 * `time_ranges` (TimeRangesHandler) offers *buckets*, and a bucket absorbs
 * neighbouring day counts: the 30-day entry is emitted when any product is
 * stored as 30 **or 31** days, 60 absorbs 61, 90 absorbs 91, 120 absorbs 121,
 * 180 absorbs 181. It reports only the canonical day of the bucket.
 *
 * `services` (ServicesHandler) filters with `AND Service_time = :time_range_day`
 * - an exact match on the stored day count.
 *
 * So a panel whose monthly plans are stored as 31 days offers a "1 month"
 * range in the picker and then returns zero plans for it, because 31 != 30.
 * Same for 61, 91, 121 and 181. The picker looked fine and the plan list came
 * back empty, which is exactly the reported symptom.
 *
 * `services` also ignores the filter entirely when the requested day is 0, so
 * picking "unlimited" used to return every plan instead of the unlimited ones.
 *
 * Keeping these buckets in one place lets the app ask for the panel/category
 * catalogue and apply the range itself, matching the server's own bucketing
 * instead of a filter the server cannot satisfy.
 */
internal object GhajarTimeBuckets {

    private val ALIASES: Map<Int, List<Int>> = mapOf(
        1 to listOf(1),
        7 to listOf(7),
        30 to listOf(30, 31),
        60 to listOf(60, 61),
        90 to listOf(90, 91),
        120 to listOf(120, 121),
        180 to listOf(180, 181),
        365 to listOf(365),
        0 to listOf(0)
    )

    /** Day counts a product may carry to belong to [bucketDay]'s range. */
    fun aliasesOf(bucketDay: Int): List<Int> = ALIASES[bucketDay] ?: listOf(bucketDay)

    fun matches(bucketDay: Int, productDays: Int?): Boolean =
        productDays != null && productDays in aliasesOf(bucketDay)
}
