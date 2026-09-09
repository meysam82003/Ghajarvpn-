package net.gozar.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class GhajarLogLevel(val short: String) { DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), CRASH("F") }

data class GhajarLogEntry(
    val timeMs: Long,
    val level: GhajarLogLevel,
    val tag: String,
    val message: String
) {
    fun formatted(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timeMs))
        return "$time  ${level.short}/${tag}: $message"
    }
}

/**
 * App-wide log + crash recorder.
 *
 * - Every entry lands in a bounded in-memory ring buffer (for the live in-app
 *   log screen) AND is appended to a rotating file on disk, so nothing is
 *   lost if the process dies right after logging.
 * - [installCrashHandler] wraps the default uncaught-exception handler: it
 *   writes the full stack trace plus the last [CRASH_CONTEXT_LINES] log
 *   lines to disk *synchronously* before the process actually dies (a crash
 *   is exactly the moment a background coroutine write would be lost).
 * - [exportFile] concatenates the current + rotated files into one .txt the
 *   user can share/download, so "چیزی که هرجای برنامه هست رو سیو کنه" holds
 *   even across app restarts.
 */
object GhajarLog {
    private const val MAX_MEMORY_ENTRIES = 4000
    private const val MAX_FILE_BYTES = 1_500_000L // ~1.5MB per file
    private const val ROTATED_FILES = 3
    private const val CRASH_CONTEXT_LINES = 200

    private val ring = ArrayDeque<GhajarLogEntry>(MAX_MEMORY_ENTRIES)
    private val ringLock = Any()
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _entries = MutableStateFlow<List<GhajarLogEntry>>(emptyList())
    val entries: StateFlow<List<GhajarLogEntry>> = _entries.asStateFlow()

    private lateinit var logDir: File
    private fun currentFile() = File(logDir, "ghajar.log")
    private fun rotatedFile(n: Int) = File(logDir, "ghajar.log.$n")

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        logDir = File(context.filesDir, "ghajar_logs").apply { mkdirs() }
        i("Logger", "=== Ghajar log session started (v${runCatching { BuildConfig.VERSION_NAME }.getOrDefault("?")}) ===")
    }

    fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = Log.getStackTraceString(throwable)
                val recent = synchronized(ringLock) { ring.toList() }
                    .takeLast(CRASH_CONTEXT_LINES)
                    .joinToString("\n") { it.formatted() }
                val block = buildString {
                    appendLine("========== CRASH ==========")
                    appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
                    appendLine("Thread: ${thread.name}")
                    appendLine(trace)
                    appendLine("---- recent log context ----")
                    appendLine(recent)
                    appendLine("============================")
                }
                // Synchronous, best-effort direct write: the process is about to
                // die, so no coroutine dispatch — append straight to the file.
                if (started.get()) {
                    FileOutputStream(currentFile(), true).use { it.write(block.toByteArray()) }
                }
            }
            previous?.onUncaughtException(thread, throwable)
                ?: run { android.os.Process.killProcess(android.os.Process.myPid()) }
        }
    }

    fun d(tag: String, msg: String) = log(GhajarLogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(GhajarLogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(GhajarLogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) =
        log(GhajarLogLevel.ERROR, tag, if (throwable != null) "$msg :: ${Log.getStackTraceString(throwable)}" else msg)

    private fun log(level: GhajarLogLevel, tag: String, msg: String) {
        val entry = GhajarLogEntry(System.currentTimeMillis(), level, tag, msg)
        synchronized(ringLock) {
            if (ring.size >= MAX_MEMORY_ENTRIES) ring.removeFirst()
            ring.addLast(entry)
            _entries.value = ring.toList()
        }
        when (level) {
            GhajarLogLevel.DEBUG -> Log.d(tag, msg)
            GhajarLogLevel.INFO -> Log.i(tag, msg)
            GhajarLogLevel.WARN -> Log.w(tag, msg)
            GhajarLogLevel.ERROR, GhajarLogLevel.CRASH -> Log.e(tag, msg)
        }
        if (started.get()) {
            scope.launch {
                writeMutex.withLock {
                    runCatching {
                        rotateIfNeeded()
                        FileOutputStream(currentFile(), true).use {
                            it.write((entry.formatted() + "\n").toByteArray())
                        }
                    }
                }
            }
        }
    }

    private fun rotateIfNeeded() {
        val file = currentFile()
        if (!file.exists() || file.length() < MAX_FILE_BYTES) return
        for (n in ROTATED_FILES downTo 1) {
            val src = if (n == 1) file else rotatedFile(n - 1)
            val dst = rotatedFile(n)
            if (n == ROTATED_FILES && dst.exists()) dst.delete()
            if (src.exists()) src.renameTo(dst)
        }
    }

    fun clear() {
        synchronized(ringLock) { ring.clear(); _entries.value = emptyList() }
        scope.launch {
            writeMutex.withLock {
                runCatching {
                    currentFile().delete()
                    for (n in 1..ROTATED_FILES) rotatedFile(n).delete()
                }
            }
        }
        i("Logger", "=== log cleared by user ===")
    }

    /** Builds one combined .txt (oldest rotated file first) ready to share. */
    suspend fun exportFile(context: Context): File = writeMutex.withLock {
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(sharedDir, "ghajar-log-export.txt")
        runCatching {
            FileOutputStream(out).use { stream ->
                for (n in ROTATED_FILES downTo 1) {
                    val f = rotatedFile(n)
                    if (f.exists()) stream.write(f.readBytes())
                }
                if (currentFile().exists()) stream.write(currentFile().readBytes())
            }
        }
        out
    }
}
