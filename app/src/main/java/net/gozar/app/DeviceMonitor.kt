package net.gozar.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * What the app costs the phone right now, for Settings -> Live monitor:
 * battery level and current draw, battery temperature, the system's thermal
 * status and headroom, and the CPU and memory of this app together with the
 * engine processes it started (sing-box, the helper engines).
 *
 * Every reader is best-effort: a vendor that hides a value gives null, never
 * an exception. Call [Sampler.sample] off the main thread, at most once a
 * second (getThermalHeadroom returns NaN when called faster).
 */
object DeviceMonitor {

    data class Sample(
        val batteryPct: Int?,
        val currentMa: Int?,
        val charging: Boolean,
        val batteryTempC: Float?,
        val voltageMv: Int?,
        /** PowerManager.THERMAL_STATUS_* (0 none .. 6 shutdown), null before Android 10. */
        val thermalStatus: Int?,
        /** How close the phone is to throttling, 0..100+ (100 = throttling), null when unknown. */
        val headroomPct: Float?,
        /** CPU of this app and its engine processes, as a share of every core. */
        val cpuPct: Float?,
        val memoryMb: Int?,
        val processes: Int,
        val threads: Int?
    )

    /** Whether the CPU has AES instructions, so TLS/AEAD runs in hardware. */
    val hardwareAes: Boolean by lazy {
        runCatching {
            File("/proc/cpuinfo").readLines().any { line ->
                val l = line.lowercase()
                (l.startsWith("features") || l.startsWith("flags")) &&
                    l.substringAfter(':').split(' ', '\t').any { it == "aes" }
            }
        }.getOrDefault(false)
    }

    class Sampler(context: Context) {
        private val app = context.applicationContext
        private val battery = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        private val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private val ticksPerSecond = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1L)
        private var lastTicks = -1L
        private var lastWallMs = 0L

        fun sample(): Sample {
            val sticky = runCatching {
                app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = runCatching { battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrNull()
                ?.takeIf { it in 0..100 }
                ?: if (level >= 0 && scale > 0) level * 100 / scale else null
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val temp = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE && it > -400 }?.let { it / 10f }
            val volt = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
            // CURRENT_NOW is microamps on most phones and milliamps on some;
            // the sign (charging or not) also differs between vendors.
            val current = runCatching { battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
                ?.takeIf { it != Int.MIN_VALUE && it != 0 }
                ?.let { kotlin.math.abs(it) }
                ?.let { if (it > 20_000) it / 1000 else it }

            val thermal = if (Build.VERSION.SDK_INT >= 29) runCatching { power?.currentThermalStatus }.getOrNull() else null
            val headroom = if (Build.VERSION.SDK_INT >= 30)
                runCatching { power?.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() && it >= 0f }?.let { it * 100f }
            else null

            val procs = ownProcesses()
            var ticks = 0L
            var rssKb = 0L
            var threads: Int? = null
            for (pid in procs) {
                ticks += cpuTicks(pid)
                val st = runCatching { File("/proc/$pid/status").readLines() }.getOrDefault(emptyList())
                st.firstOrNull { it.startsWith("VmRSS:") }?.let { rssKb += kb(it) }
                if (pid == android.os.Process.myPid()) {
                    threads = st.firstOrNull { it.startsWith("Threads:") }?.substringAfter(':')?.trim()?.toIntOrNull()
                }
            }
            val nowMs = android.os.SystemClock.elapsedRealtime()
            val cpu = if (lastTicks >= 0 && nowMs > lastWallMs && ticks >= lastTicks) {
                val seconds = (nowMs - lastWallMs) / 1000f
                ((ticks - lastTicks).toFloat() / ticksPerSecond / seconds / cores * 100f).coerceIn(0f, 100f)
            } else null
            lastTicks = ticks
            lastWallMs = nowMs

            return Sample(
                batteryPct = pct, currentMa = current, charging = charging, batteryTempC = temp,
                voltageMv = volt, thermalStatus = thermal, headroomPct = headroom,
                cpuPct = cpu, memoryMb = if (rssKb > 0) (rssKb / 1024).toInt() else null,
                processes = procs.size, threads = threads
            )
        }

        /** This process and every process it started (engines, and theirs). */
        private fun ownProcesses(): List<Int> {
            val me = android.os.Process.myPid()
            val parents = HashMap<Int, Int>()
            // /proc only lists this app's own processes on Android 7+.
            File("/proc").listFiles()?.forEach { f ->
                val pid = f.name.toIntOrNull() ?: return@forEach
                val stat = runCatching { File(f, "stat").readText() }.getOrNull() ?: return@forEach
                val ppid = stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toIntOrNull() ?: return@forEach
                parents[pid] = ppid
            }
            val mine = linkedSetOf(me)
            var grew = true
            while (grew) {
                grew = false
                for ((pid, ppid) in parents) if (ppid in mine && mine.add(pid)) grew = true
            }
            return mine.toList()
        }

        private fun cpuTicks(pid: Int): Long = runCatching {
            // Fields after "(comm)": state ppid ... utime is the 12th, stime the 13th.
            val f = File("/proc/$pid/stat").readText().substringAfterLast(')').trim().split(' ')
            (f.getOrNull(11)?.toLongOrNull() ?: 0L) + (f.getOrNull(12)?.toLongOrNull() ?: 0L)
        }.getOrDefault(0L)

        private fun kb(line: String): Long =
            line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
    }
}
