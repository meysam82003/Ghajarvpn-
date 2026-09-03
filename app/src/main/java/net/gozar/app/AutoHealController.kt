package net.gozar.app

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class AutoHealAction { RETRY_CURRENT, CHANGE_SERVER, CHANGE_PROTOCOL, FALLBACK, GIVE_UP }

data class AutoHealStep(val action: AutoHealAction, val delayMs: Long)

object AutoHealPolicy {
    const val MAX_ATTEMPTS = 4
    const val WINDOW_MS = 5 * 60 * 1000L

    fun step(attempt: Int): AutoHealStep = when (attempt) {
        0 -> AutoHealStep(AutoHealAction.RETRY_CURRENT, 1_500)
        1 -> AutoHealStep(AutoHealAction.CHANGE_SERVER, 3_000)
        2 -> AutoHealStep(AutoHealAction.CHANGE_PROTOCOL, 6_000)
        3 -> AutoHealStep(AutoHealAction.FALLBACK, 10_000)
        else -> AutoHealStep(AutoHealAction.GIVE_UP, 0)
    }
}

class AutoHealPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ghajar_auto_heal", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
}

data class AutoHealResult(val connected: Boolean, val attempts: Int, val lastAction: AutoHealAction)

/** Finite recovery coordinator; it never owns or changes the VPN implementation itself. */
class AutoHealController(
    context: Context,
    private val store: ConfigStore,
    private val connect: (ProxyConfig) -> Unit,
    private val onStep: (AutoHealAction, Int) -> Unit = { _, _ -> }
) {
    private val health = ServerHealthRepository(context.applicationContext)
    private val smartPreferences = SmartConnectPreferences(context.applicationContext)
    private var windowStartedAt = 0L
    private var attemptsInWindow = 0

    suspend fun recover(lastConfigId: String?): AutoHealResult {
        val now = System.currentTimeMillis()
        if (now - windowStartedAt > AutoHealPolicy.WINDOW_MS) { windowStartedAt = now; attemptsInWindow = 0 }
        store.awaitReady()
        var current = store.configs.value.firstOrNull { it.id == lastConfigId }
        var lastAction = AutoHealAction.GIVE_UP
        while (attemptsInWindow < AutoHealPolicy.MAX_ATTEMPTS) {
            val step = AutoHealPolicy.step(attemptsInWindow)
            if (step.action == AutoHealAction.GIVE_UP) break
            delay(step.delayMs)
            if (VpnState.state.value == Connection.CONNECTED) return AutoHealResult(true, attemptsInWindow, step.action)
            val selected = select(step.action, current)
            if (selected == null) { attemptsInWindow++; continue }
            lastAction = step.action; attemptsInWindow++
            onStep(step.action, attemptsInWindow)
            connect(selected)
            val state = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                VpnState.state.first { it == Connection.CONNECTED || it == Connection.ERROR || it == Connection.DISCONNECTED }
            }
            if (state == Connection.CONNECTED) {
                val usedAttempts = attemptsInWindow
                attemptsInWindow = 0; windowStartedAt = System.currentTimeMillis(); return AutoHealResult(true, usedAttempts, lastAction)
            }
            health.recordConnectionFailure(selected.id)
            current = selected
        }
        return AutoHealResult(false, attemptsInWindow, lastAction)
    }

    private fun select(action: AutoHealAction, current: ProxyConfig?): ProxyConfig? {
        val supported = store.configs.value.filter { it.protocol.trim().lowercase() !in setOf("tor", "aether") }
        if (supported.isEmpty()) return null
        if (action == AutoHealAction.RETRY_CURRENT) return current ?: supported.firstOrNull()
        val alternatives = when (action) {
            AutoHealAction.CHANGE_SERVER -> supported.filterNot { it.id == current?.id }
            AutoHealAction.CHANGE_PROTOCOL -> supported.filter { !it.protocol.equals(current?.protocol, true) }
            AutoHealAction.FALLBACK -> supported.filterNot { it.id == current?.id }
            else -> supported
        }
        if (alternatives.isEmpty()) return null
        val states = health.snapshot(alternatives.map { it.id })
        val mode = if (action == AutoHealAction.FALLBACK) SmartMode.EMERGENCY else smartPreferences.mode
        val ranked = SmartConnectScorer.rank(alternatives.map { ServerCandidate(it.id) }, states, mode)
        return ranked.firstOrNull()?.let { rankedItem -> alternatives.firstOrNull { it.id == rankedItem.candidate.id } }
    }

    private companion object { const val CONNECT_TIMEOUT_MS = 20_000L }
}
