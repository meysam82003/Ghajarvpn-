package net.gozar.app.plugins

import java.security.MessageDigest

/**
 * The rules an optional core plugin has to pass before it can ever run.
 *
 * A plugin is a separate, signed APK that carries one core. It is never an
 * arbitrary `.so`, never a URL the user pastes and never trusted on first use:
 * its release record must name a publisher whose signing-certificate SHA-256
 * is pinned in this app ([TRUSTED_PUBLISHERS]), the downloaded file must match
 * the record's size and SHA-256, and the installed package must be signed by
 * that same certificate. Updates are atomic (the active release only changes
 * after the new one verified), and a rollback may only return to a release
 * that was itself active before.
 *
 * 1.1.1 publishes no plugins: [TRUSTED_PUBLISHERS] is empty, so [check]
 * refuses everything and the UI lists none. Backups carry plugin metadata
 * (which release was active), never plugin binaries.
 */
object PluginPolicy {
    const val API_VERSION = 1

    /** Publisher id -> pinned signing-certificate SHA-256 (lowercase hex). Empty in 1.1.1. */
    val TRUSTED_PUBLISHERS: Map<String, String> = emptyMap()

    enum class State { NOT_INSTALLED, DOWNLOADING, VERIFYING, INSTALLED, UPDATE_AVAILABLE, INCOMPATIBLE, BROKEN }

    data class Release(
        val id: String,
        val versionCode: Long,
        val apiVersion: Int,
        val minHostVersionCode: Long,
        val abis: Set<String>,
        val size: Long,
        val sha256: String,
        val publisher: String,
        val certificateSha256: String,
        val packageName: String
    )

    sealed class Verdict {
        object Ok : Verdict()
        data class Refused(val reason: String) : Verdict()
    }

    /** Release-record checks: trusted publisher, pinned certificate, API, host version, ABI. */
    fun check(
        r: Release,
        hostVersionCode: Long,
        deviceAbis: Set<String>,
        trusted: Map<String, String> = TRUSTED_PUBLISHERS
    ): Verdict {
        val pin = trusted[r.publisher] ?: return Verdict.Refused("ناشر این افزونه در برنامه ثبت نشده است.")
        if (!pin.equals(r.certificateSha256, ignoreCase = true)) return Verdict.Refused("گواهی امضای افزونه با ناشر ثبت‌شده یکی نیست.")
        if (r.apiVersion != API_VERSION) return Verdict.Refused("نسخهٔ رابط افزونه با این برنامه سازگار نیست.")
        if (hostVersionCode < r.minHostVersionCode) return Verdict.Refused("این افزونه نسخهٔ جدیدتری از قاجار VPN می‌خواهد.")
        if (r.abis.none { it in deviceAbis }) return Verdict.Refused("این افزونه برای معماری این گوشی ساخته نشده است.")
        if (!HEX64.matches(r.sha256)) return Verdict.Refused("هش فایل افزونه معتبر نیست.")
        return Verdict.Ok
    }

    /** The downloaded bytes are exactly the release. */
    fun fileMatches(r: Release, bytes: ByteArray): Boolean =
        bytes.size.toLong() == r.size && sha256(bytes).equals(r.sha256, ignoreCase = true)

    /** The installed package is signed by the pinned certificate and nothing else. */
    fun signerMatches(r: Release, signerCertificates: List<ByteArray>): Boolean =
        signerCertificates.size == 1 && sha256(signerCertificates.single()).equals(r.certificateSha256, ignoreCase = true)

    /**
     * Whether [next] may replace [active]. An update must raise the version;
     * going down is allowed only to a release that was active before.
     */
    fun mayActivate(active: Release?, next: Release, previouslyActive: Set<Long>): Boolean = when {
        active == null -> true
        active.id != next.id || active.packageName != next.packageName -> false
        next.versionCode > active.versionCode -> true
        else -> next.versionCode in previouslyActive
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")
}
