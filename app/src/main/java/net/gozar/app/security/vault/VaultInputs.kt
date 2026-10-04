package net.gozar.app.security.vault

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Explicit binary units. Month means 30 days, independent of DST and calendar changes. */
object VaultInputs {
    val volumeUnits = listOf("MB", "GB", "TB", "Unlimited")
    val timeUnits = listOf("Hours", "Days", "Months", "Custom date/time", "Unlimited")
    private fun positive(value: String, multiplier: Long): Long = Math.multiplyExact(value.trim().toLong().also { require(it > 0) }, multiplier)
    fun bytes(value: String, unit: String): Long? = when (unit) {
        "MB" -> positive(value, 1L shl 20)
        "GB" -> positive(value, 1L shl 30)
        "TB" -> positive(value, 1L shl 40)
        "Unlimited" -> null
        else -> error("Unknown volume unit")
    }
    fun duration(value: String, unit: String): Long? = when (unit) {
        "Hours" -> positive(value, 3_600_000L)
        "Days" -> positive(value, 86_400_000L)
        "Months" -> positive(value, 2_592_000_000L)
        "Unlimited", "Custom date/time" -> null
        else -> error("Unknown time unit")
    }
    fun expiry(value: String, unit: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (unit != "Custom date/time") return null
        val local = LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT))
        require(zone.rules.getValidOffsets(local).size == 1) { "Ambiguous or nonexistent local time" }
        return local.atZone(zone).toInstant().toEpochMilli().also { require(it > now) }
    }
    fun size(value: Long): String = when {
        value >= 1L shl 40 -> String.format(Locale.ROOT, "%.2f TB", value.toDouble() / (1L shl 40))
        value >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", value.toDouble() / (1L shl 30))
        value >= 1L shl 20 -> String.format(Locale.ROOT, "%.2f MB", value.toDouble() / (1L shl 20))
        else -> "$value B"
    }
}
