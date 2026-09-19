package net.gozar.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What happens when the resolver in use stops working.
 *
 * The choice is the user's because the right answer depends on what they are
 * doing, and an app that picks silently gets it wrong for somebody.
 */
enum class DnsFailPolicy {
    /**
     * Stay on the chosen resolver and let lookups fail.
     *
     * The safe option and the reason it is the default: moving to another
     * resolver behind the user's back can mean their lookups start going
     * somewhere they did not choose, which for DNS is exactly the exposure
     * they picked a resolver to avoid.
     */
    STAY,

    /**
     * Move to the next resolver that was measured healthy.
     *
     * Only ever within the list the user imported and only among resolvers
     * this app has itself measured - never to a system default, and never to
     * one that failed or was found poisoned.
     */
    NEXT_HEALTHY
}

/**
 * Picking a resolver, and moving off one that has stopped working.
 *
 * Four rules, and each exists because its opposite is a bug somebody has
 * shipped:
 *
 * 1. **A manual choice is never overridden.** If the user picked a resolver by
 *    hand, automatic selection is off until they clear it. A setting that
 *    quietly reverts is worse than no setting.
 * 2. **Only measured resolvers are selected.** Health comes from this app's
 *    own scan - never from "it is in the list" or "it is popular".
 * 3. **A move needs a real margin and a real gap.** Without both, two
 *    resolvers of similar quality make the app flap between them, which is
 *    visible as DNS that intermittently breaks.
 * 4. **A failed move is reported, not retried forever.** Backoff doubles up to
 *    a ceiling, and the reason is kept so the screen can say why.
 */
object DnsSmartSelect {

    private const val TAG = "GhajarDnsSelect"

    /** How much better a candidate must be before a switch is worth it. */
    private const val SWITCH_MARGIN_MS = 45

    /** The shortest time between two automatic switches. */
    private const val MIN_SWITCH_GAP_MS = 60_000L

    /** Backoff after a failed switch, doubling to this ceiling. */
    private const val BACKOFF_START_MS = 15_000L
    private const val BACKOFF_CEILING_MS = 10 * 60_000L

    private val _lastSwitchAt = MutableStateFlow(0L)
    private val _backoffMs = MutableStateFlow(BACKOFF_START_MS)
    private val _blockedUntil = MutableStateFlow(0L)

    private val _reason = MutableStateFlow("")

    /** Why the last decision went the way it did, for the screen to show. */
    val reason: StateFlow<String> = _reason.asStateFlow()

    /**
     * The resolver to use now, or null to leave things alone.
     *
     * A null result is a decision, not a failure: it means nothing better is
     * available, or the gap since the last switch has not passed, or the user
     * has pinned a choice. The caller changes nothing when it gets null.
     */
    fun decide(
        resolvers: List<DnsResolver>,
        verdicts: Map<String, DnsVerdict>,
        currentId: String?,
        manuallyPinned: Boolean,
        policy: DnsFailPolicy,
        now: Long = System.currentTimeMillis()
    ): DnsResolver? {
        if (manuallyPinned) {
            _reason.value = "manual-pin"
            return null
        }
        if (now < _blockedUntil.value) {
            _reason.value = "backoff"
            return null
        }

        val healthy = resolvers
            .mapNotNull { r -> verdicts[r.id]?.let { r to it } }
            .filter { (_, v) ->
                v.health == DnsHealth.GOOD && !v.poisoned && !v.mismatched &&
                    v.recursive == true && v.latencyMs != null
            }
            .sortedWith(
                compareByDescending<Pair<DnsResolver, DnsVerdict>> { it.second.successPercent }
                    .thenBy { it.second.latencyMs ?: Int.MAX_VALUE }
            )
        if (healthy.isEmpty()) {
            _reason.value = "none-healthy"
            return null
        }

        val current = currentId?.let { id -> verdicts[id] }
        val currentStillGood = current != null &&
            current.health == DnsHealth.GOOD && !current.poisoned && !current.mismatched

        // Nothing is chosen yet: take the best, with no gap to respect.
        if (currentId == null) {
            _reason.value = "first-pick"
            _lastSwitchAt.value = now
            return healthy.first().first
        }

        // The one in use has failed outright. STAY means exactly that.
        if (!currentStillGood) {
            if (policy == DnsFailPolicy.STAY) {
                _reason.value = "failed-but-staying"
                return null
            }
            val next = healthy.firstOrNull { it.first.id != currentId }
            if (next == null) {
                _reason.value = "no-alternative"
                return null
            }
            _reason.value = "failed-over"
            _lastSwitchAt.value = now
            return next.first
        }

        // The one in use still works. Only a clearly better candidate, and
        // only after the gap, justifies moving - anything looser is flapping.
        if (now - _lastSwitchAt.value < MIN_SWITCH_GAP_MS) {
            _reason.value = "too-soon"
            return null
        }
        val best = healthy.first()
        if (best.first.id == currentId) {
            _reason.value = "already-best"
            return null
        }
        val currentMs = current?.latencyMs ?: Int.MAX_VALUE
        val bestMs = best.second.latencyMs ?: return null
        if (currentMs - bestMs < SWITCH_MARGIN_MS) {
            _reason.value = "margin-too-small"
            return null
        }
        _reason.value = "better-available"
        _lastSwitchAt.value = now
        return best.first
    }

    /** Records that a switch did not help, so the next attempt waits longer. */
    fun switchFailed(now: Long = System.currentTimeMillis()) {
        val wait = _backoffMs.value
        _blockedUntil.value = now + wait
        _backoffMs.value = (wait * 2).coerceAtMost(BACKOFF_CEILING_MS)
        _reason.value = "backing-off"
        GhajarLog.w(TAG, "switch failed; not trying again for ${wait / 1000}s")
    }

    /** Records that a switch worked, clearing the backoff. */
    fun switchWorked() {
        _backoffMs.value = BACKOFF_START_MS
        _blockedUntil.value = 0L
    }

    /** Forgets all pacing state. Used when the user changes the policy by hand. */
    fun reset() {
        _lastSwitchAt.value = 0L
        _backoffMs.value = BACKOFF_START_MS
        _blockedUntil.value = 0L
        _reason.value = ""
    }
}
