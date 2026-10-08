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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import kotlin.coroutines.coroutineContext

/**
 * The one update state the whole app reads: the Home "بروزرسانی جدید" button,
 * the update dialog, About -> Versions and the reminder notification. The
 * download lives here, in a process-wide scope, so leaving the app, rotating
 * or recreating the activity never resets or loses it.
 */
object GhajarUpdateFlow {
    private val _available = MutableStateFlow<UpdateChecker.Result.Available?>(null)
    /** A release newer than the installed one; null when up to date. */
    val available = _available.asStateFlow()
    private val _dialog = MutableStateFlow(false)
    val dialogOpen = _dialog.asStateFlow()

    /** 0 offer, 1 downloading, 2 verifying, 3 ready, 4 error. */
    val stage = MutableStateFlow(0)
    val progress = MutableStateFlow(0f)
    val error = MutableStateFlow<String?>(null)
    val readyFile = MutableStateFlow<File?>(null)
    private var job: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

    fun offer(result: UpdateChecker.Result.Available, open: Boolean = true) {
        if (_available.value?.version != result.version) { stage.value = 0; progress.value = 0f; error.value = null; readyFile.value = null }
        _available.value = result
        if (open) _dialog.value = true
    }
    fun open() { if (_available.value != null) _dialog.value = true }
    /** Closing is refused while a download or check is running: only «لغو دانلود» stops it. */
    fun dismiss() { if (stage.value != 1 && stage.value != 2) _dialog.value = false }
    /** The installed app caught up (or passed) the offered version. */
    fun clearIfInstalled(installed: String) {
        val a = _available.value ?: return
        if (!UpdateChecker.isNewer(a.version, installed)) { _available.value = null; _dialog.value = false; stage.value = 0 }
    }

    fun cancel() {
        job?.cancel(); job = null
        stage.value = 0; progress.value = 0f
    }

    fun start(context: Context, onPage: (String) -> Unit) {
        val upd = _available.value ?: return
        val apk = upd.apk
        if (apk == null) { onPage(upd.url); return }
        if (job?.isActive == true) return
        val app = context.applicationContext
        stage.value = 1; progress.value = 0f; error.value = null
        job = scope.launch {
            when (val result = GhajarUpdateInstaller.download(app, apk) { read, total ->
                progress.value = if (total > 0) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            }) {
                is GhajarUpdateInstaller.DownloadResult.Cancelled -> stage.value = 0
                is GhajarUpdateInstaller.DownloadResult.Failed -> {
                    error.value = "دانلود ناموفق بود؛ اتصال را بررسی کن و دوباره تلاش کن."
                    stage.value = 4
                }
                is GhajarUpdateInstaller.DownloadResult.Success -> {
                    stage.value = 2
                    val checksum = withContext(Dispatchers.IO) { GhajarUpdateInstaller.verifySha256(result.file, upd.apkSha256) }
                    val signature = withContext(Dispatchers.IO) { GhajarUpdateInstaller.verifySignatureMatchesInstalled(app, result.file) }
                    val refused = when {
                        checksum is GhajarUpdateInstaller.VerifyResult.ChecksumMismatch ->
                            "فایل دانلودشده با نسخهٔ منتشرشده مطابقت ندارد؛ ممکن است دانلود خراب شده باشد. دوباره تلاش کن."
                        // 1.1.1: no hash published means no install. A file
                        // that cannot be checked is not handed to the installer.
                        checksum is GhajarUpdateInstaller.VerifyResult.Unavailable -> checksum.reason
                        signature is GhajarUpdateInstaller.VerifyResult.SignatureMismatch -> signature.reason
                        signature is GhajarUpdateInstaller.VerifyResult.Unavailable -> signature.reason
                        else -> null
                    }
                    if (refused != null) {
                        result.file.delete()
                        error.value = refused
                        stage.value = 4
                        return@launch
                    }
                    readyFile.value = result.file
                    stage.value = 3
                    if (GhajarUpdateInstaller.canInstallPackages(app)) runCatching { GhajarUpdateInstaller.install(app, result.file) }
                }
            }
        }
    }
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
