package net.gozar.app

/** A session owns acquisitions before start, including partially started engines.
 * The service engineLock serializes generations. No start of a successor before close returns. */
class ChainSession(private val steps: List<Step>, private val deadlineNanos: Long = System.nanoTime() + 360_000_000_000L) : AutoCloseable {
    interface Step {
        fun start()
        fun ready(): Boolean
        fun alive(): Boolean
        fun stop()
    }
    private val acquired = java.util.concurrent.CopyOnWriteArrayList<Step>()
    @Volatile private var closed = false
    fun start() {
        check(!closed && acquired.isEmpty()) { "Session cannot be restarted or reused" }
        val owner = Thread.currentThread()
        val completed = java.util.concurrent.CopyOnWriteArrayList<Step>()
        val starting = java.util.concurrent.atomic.AtomicBoolean(true)
        val startupFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val watcher = kotlin.concurrent.thread(isDaemon = true, name = "chain-start-watch") {
            try {
                while (starting.get()) {
                    val failure = when {
                        System.nanoTime() >= deadlineNanos -> "Chain startup timed out"
                        completed.any { !it.alive() } -> "Chain prerequisite died during startup"
                        else -> null
                    }
                    if (failure != null) { startupFailure.set(failure); owner.interrupt(); break }
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) { }
        }
        try {
            for (step in steps) {
                checkActive()
                acquired.add(step)
                step.start()
                checkActive()
                check(step.ready()) { "Chain prerequisite did not pass its data probe" }
                checkActive()
                check(acquired.all { it.alive() }) { "Chain engine stopped during startup" }
                completed.add(step)
            }
        } catch (e: Throwable) {
            // An internal watch failure is an engine error, not user coroutine cancellation.
            val failure = startupFailure.get()?.let { Thread.interrupted(); IllegalStateException(it, e) } ?: e
            try { close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
        finally { starting.set(false); watcher.interrupt(); val interrupted=Thread.interrupted(); try { watcher.join(500) } finally { if(interrupted) Thread.currentThread().interrupt() } }
    }
    private fun checkActive() {
        if (closed || Thread.currentThread().isInterrupted) throw InterruptedException("Chain cancelled")
        check(System.nanoTime() < deadlineNanos) { "Chain startup timed out" }
    }
    fun healthy(): Boolean = !closed && acquired.size == steps.size && acquired.all { it.alive() }
    override fun close() {
        closed = true
        // The owner calls close under engineLock, after interruptible start has completed.
        var failure: Throwable? = null
        acquired.toList().asReversed().forEach { step ->
            try { step.stop(); acquired.remove(step) }
            catch (e: Throwable) { if (failure == null) failure=e else failure!!.addSuppressed(e) }
        }
        // Failed releases remain owned; callers must not start the next generation.
        failure?.let { throw it }
    }
}
