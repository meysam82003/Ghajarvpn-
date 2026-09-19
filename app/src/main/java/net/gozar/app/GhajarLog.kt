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
 * - Android Log calls are best-effort so plain JVM unit tests can exercise
 *   state-machine code without Robolectric just because it emits diagnostics.
 */
object GhajarLog {
    private const val MAX_MEMORY_ENTRIES = 4000
    private const val MAX_FILE_BYTES = 1_500_000L
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
    private var extLogDir: File? = null
    private fun currentFile() = File(logDir, "ghajar.log")
    private fun rotatedFile(n: Int) = File(logDir, "ghajar.log.$n")

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        logDir = File(context.filesDir, "ghajar_logs").apply { mkdirs() }
        // Mirror to external app storage so the log is reachable without adb
        // (readable at /storage/emulated/0/Android/data/<pkg>/files/).
        extLogDir = context.getExternalFilesDir(null)?.let { File(it, "ghajar_logs_ext").apply { mkdirs() } }
        i("Logger", "=== Ghajar log session started (v${runCatching { BuildConfig.VERSION_NAME }.getOrDefault("?")}) ===")
    }

    fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = runCatching { Log.getStackTraceString(throwable) }
                    .getOrElse { throwable.stackTraceToString() }
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
                if (started.get()) {
                    FileOutputStream(currentFile(), true).use { it.write(block.toByteArray()) }
                    extLogDir?.let { dir ->
                        runCatching {
                            FileOutputStream(File(dir, "ghajar-crash.log"), true).use {
                                it.write(block.toByteArray())
                            }
                        }
                    }
                }
            }
            previous?.uncaughtException(thread, throwable)
                ?: run { android.os.Process.killProcess(android.os.Process.myPid()) }
        }
    }

    fun d(tag: String, msg: String) = log(GhajarLogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(GhajarLogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(GhajarLogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) =
        log(
            GhajarLogLevel.ERROR,
            tag,
            if (throwable != null) {
                val trace = runCatching { Log.getStackTraceString(throwable) }
                    .getOrElse { throwable.stackTraceToString() }
                "$msg :: $trace"
            } else msg
        )

    private fun log(level: GhajarLogLevel, tag: String, msg: String) {
        val entry = GhajarLogEntry(System.currentTimeMillis(), level, tag, msg)
        synchronized(ringLock) {
            if (ring.size >= MAX_MEMORY_ENTRIES) ring.removeFirst()
            ring.addLast(entry)
            _entries.value = ring.toList()
        }

        // android.util.Log methods throw "not mocked" from local JVM tests.
        // Diagnostics must never alter VPN state-machine behaviour.
        runCatching {
            when (level) {
                GhajarLogLevel.DEBUG -> Log.d(tag, msg)
                GhajarLogLevel.INFO -> Log.i(tag, msg)
                GhajarLogLevel.WARN -> Log.w(tag, msg)
                GhajarLogLevel.ERROR, GhajarLogLevel.CRASH -> Log.e(tag, msg)
            }
        }

        if (started.get()) {
            if (tag == "Startup") {
                // Startup phase markers must survive a native crash that never
                // reaches the uncaught-exception handler: write synchronously.
                runCatching {
                    writeEntrySync(entry)
                }
                return
            }
            scope.launch {
                writeMutex.withLock {
                    runCatching {
                        rotateIfNeeded()
                        writeEntryLocked(entry)
                    }
                }
            }
        }
    }

    private fun writeEntrySync(entry: GhajarLogEntry) {
        val line = (entry.formatted() + "\n").toByteArray()
        FileOutputStream(currentFile(), true).use { it.write(line) }
        extLogDir?.let { dir ->
            FileOutputStream(File(dir, "ghajar.log"), true).use { it.write(line) }
        }
    }

    private fun writeEntryLocked(entry: GhajarLogEntry) {
        rotateIfNeeded()
        FileOutputStream(currentFile(), true).use {
            it.write((entry.formatted() + "\n").toByteArray())
        }
        extLogDir?.let { dir ->
            FileOutputStream(File(dir, "ghajar.log"), true).use {
                it.write((entry.formatted() + "\n").toByteArray())
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

    suspend fun exportFile(context: Context): File = writeMutex.withLock {
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(sharedDir, "ghajar-log-export.txt")
        runCatching {
            val raw = buildString {
                for (n in ROTATED_FILES downTo 1) {
                    val f = rotatedFile(n)
                    if (f.exists()) append(f.readText(Charsets.UTF_8))
                }
                if (currentFile().exists()) append(currentFile().readText(Charsets.UTF_8))
            }
            out.writeText(redact(raw), Charsets.UTF_8)
        }
        out
    }

    /**
     * The exported file is meant to be shared with support/developers, so it
     * must never carry anything that could be replayed against the account:
     * bearer/session tokens, API keys, card numbers or phone numbers that may
     * have ended up in a logged exception message or URL. This never touches
     * what's kept in the in-app log (GhajarLogActivity), only the copy that
     * leaves the device.
     */
    internal val redactionPatterns: List<Pair<Regex, String>> = listOf(
        Regex("(?i)bearer\\s+[A-Za-z0-9\\-_.]{8,}") to "Bearer [REDACTED]",
        // The key list covers what this app actually carries, not just the
        // generic three: "pass" as well as "password", the private/public keys
        // and short id a Reality config is useless without, the pre-shared key
        // and obfuscation secret, and the one-time web panel ticket.
        Regex("(?i)(\"?(?:token|access_token|api[_-]?key|session|ticket|secret|psk|pass|passwd|private[_-]?key|public[_-]?key|short[_-]?id|auth)\"?\\s*[:=]\\s*\"?)[A-Za-z0-9+/\\-_.=]{6,}") to "$1[REDACTED]",
        Regex("(?i)(\"?password\"?\\s*[:=]\\s*\"?)[^\"\\s,}]{1,}") to "$1[REDACTED]",
        // A share link carries the credential in its userinfo, so the whole
        // link is the secret. Everything up to the @ goes; the host and port
        // stay, because which server failed is the point of a diagnostic.
        Regex("(?i)\\b(vless|vmess|trojan|ss|ssr|hysteria2?|hy2|tuic|socks5?|http)://[^@\\s/]+@") to "$1://[REDACTED]@",
        // A bare vmess:// link is a base64 blob with no @ at all.
        Regex("(?i)\\bvmess://[A-Za-z0-9+/=]{16,}") to "vmess://[REDACTED]",
        // A VLESS/VMess uuid is that server's whole authentication.
        Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b") to "[UUID]",
        // Long hex runs are how every credential this app generates looks: the
        // VPN Share password, the link session token, the web panel ticket, a
        // pinned certificate fingerprint. 32 is above anything meaningful a
        // diagnostic would print in hex and below every one of those.
        Regex("(?i)\\b[0-9a-f]{32,}\\b") to "[REDACTED]",
        Regex("(?:\\+98|0)9\\d{9}\\b") to "[PHONE]",
        Regex("\\b\\d{16}\\b") to "[CARD]"
    )

    /**
     * Internal rather than private so the pattern set is unit-tested. It is
     * applied only to the copy that leaves the device; the in-app log view is
     * untouched, so a user debugging their own connection still sees
     * everything.
     */
    internal fun redact(text: String): String =
        redactionPatterns.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
}
