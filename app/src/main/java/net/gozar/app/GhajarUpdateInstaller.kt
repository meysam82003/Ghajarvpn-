package net.gozar.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import kotlin.coroutines.coroutineContext

/** Single shared bus so both the periodic background check and the manual
 * "check for updates" button in Settings/About surface the exact same
 * download+verify+install dialog instead of two divergent update flows. */
object GhajarUpdateFlow {
    private val _available = MutableStateFlow<UpdateChecker.Result.Available?>(null)
    val available = _available.asStateFlow()
    fun offer(result: UpdateChecker.Result.Available) { _available.value = result }
    fun clear() { _available.value = null }
}

/**
 * Downloads, verifies, and installs a GitHub release APK found by
 * [UpdateChecker]. Every step here is real: no simulated progress, no
 * skipped verification. When a check genuinely cannot be performed (no
 * SHA-256 published, signature unreadable), callers get an explicit
 * failure reason instead of a silent pass.
 */
object GhajarUpdateInstaller {

    sealed interface DownloadResult {
        data class Success(val file: File) : DownloadResult
        data object Cancelled : DownloadResult
        data class Failed(val reason: String) : DownloadResult
    }

    sealed interface VerifyResult {
        data object Ok : VerifyResult
        data class ChecksumMismatch(val expected: String, val actual: String) : VerifyResult
        data class SignatureMismatch(val reason: String) : VerifyResult
        data class Unavailable(val reason: String) : VerifyResult
    }

    private fun downloadsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }

    suspend fun download(
        context: Context,
        asset: UpdateChecker.ReleaseAsset,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dest = File(downloadsDir(context), asset.name)
        val tmp = File(dest.parentFile, dest.name + ".part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Ghajar VPN")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.sizeBytes
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Long = 0
                    while (true) {
                        if (!coroutineContext.isActive) {
                            tmp.delete()
                            return@withContext DownloadResult.Cancelled
                        }
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            if (!tmp.renameTo(dest)) { dest.delete(); tmp.copyTo(dest, overwrite = true); tmp.delete() }
            DownloadResult.Success(dest)
        } catch (e: kotlinx.coroutines.CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            DownloadResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    fun verifySha256(file: File, expectedHex: String?): VerifyResult {
        if (expectedHex.isNullOrBlank()) {
            return VerifyResult.Unavailable("این نسخه فایل SHA256SUMS.txt منتشر نکرده؛ صحت فایل قابل تأیید نیست.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual.equals(expectedHex, ignoreCase = true)) VerifyResult.Ok
        else VerifyResult.ChecksumMismatch(expectedHex, actual)
    }

    /** The new APK's signing certificate(s) must match the currently-installed
     * app's exactly (Android would refuse a mismatched-signature install
     * anyway, but checking here lets the app explain why *before* handing the
     * file to the system installer, and up front reject an accidental debug
     * build — its certificate never matches the release signing key). */
    fun verifySignatureMatchesInstalled(context: Context, apkFile: File): VerifyResult {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val installed = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()
            ?: return VerifyResult.Unavailable("اطلاعات امضای نسخهٔ نصب‌شده در دسترس نیست.")
        val downloaded = runCatching { pm.getPackageArchiveInfo(apkFile.absolutePath, flags) }.getOrNull()
            ?: return VerifyResult.Unavailable("فایل دانلودشده یک بستهٔ اندروید معتبر نیست یا امضا ندارد.")
        val installedCerts = certFingerprints(installed) ?: return VerifyResult.Unavailable("امضای نسخهٔ نصب‌شده قابل خواندن نیست.")
        val downloadedCerts = certFingerprints(downloaded) ?: return VerifyResult.Unavailable("امضای فایل دانلودشده قابل خواندن نیست.")
        return if (installedCerts == downloadedCerts) VerifyResult.Ok
        else VerifyResult.SignatureMismatch(
            "امضای نسخهٔ جدید با نسخهٔ نصب‌شده یکی نیست؛ ممکن است این فایل نسخهٔ دیباگ یا یک بستهٔ دستکاری‌شده باشد. نصب متوقف شد."
        )
    }

    private fun certFingerprints(info: PackageInfo): Set<String>? {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val encoded: List<ByteArray> = when {
            Build.VERSION.SDK_INT >= 28 -> {
                val signingInfo = info.signingInfo ?: return null
                (if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory)
                    ?.map { it.toByteArray() } ?: return null
            }
            else -> @Suppress("DEPRECATION") (info.signatures?.map { it.toByteArray() } ?: return null)
        }
        if (encoded.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        return encoded.mapTo(mutableSetOf()) { bytes ->
            val cert = certificateFactory.generateCertificate(bytes.inputStream())
            digest.digest(cert.encoded).joinToString("") { "%02x".format(it) }
        }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Hands the verified APK to Android's own installer; the OS still shows
     * its own confirmation UI (and, for a signature mismatch it would refuse
     * outright — we just explain that case earlier and more clearly). */
    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
