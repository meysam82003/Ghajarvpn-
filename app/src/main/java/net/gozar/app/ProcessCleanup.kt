package net.gozar.app

import java.util.concurrent.TimeUnit

/** Cancellation must not skip forced termination before the next session. */
internal fun terminateProcess(process: Process, graceMillis: Long) {
    val interrupted = Thread.interrupted()
    try {
        process.destroy()
        try {
            if (!process.waitFor(graceMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                check(process.waitFor(2000, TimeUnit.MILLISECONDS)) { "Engine did not terminate" }
            }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.interrupted()
            check(process.waitFor(2000, TimeUnit.MILLISECONDS)) { "Engine did not terminate after cancellation" }
            Thread.currentThread().interrupt()
        }
    } finally { if (interrupted) Thread.currentThread().interrupt() }
}
