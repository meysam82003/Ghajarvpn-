package net.gozar.app.plugins

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.gozar.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** One manager, one serialized mutation queue; startup reads only a small metadata journal. */
class PluginManager private constructor(private val context: Context) {
    private val trust = PluginTrust(ProductionPublishers.all, BuildConfig.VERSION_CODE.toLong(), Build.SUPPORTED_ABIS.toSet())
    private val root = File(context.filesDir, "plugins").apply { mkdirs() }
    private val journal = AtomicFile(File(root, "registry.json"))
    private val lock = Any()
    private val mutation = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var data: JSONObject = runCatching { journal.openRead().use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) } }.getOrDefault(JSONObject())
    private val changes = MutableStateFlow(0)
    val revision = changes.asStateFlow()
    // Failed/in-progress journals never imply activation. The active envelope is independently retained.
    init {
        synchronized(lock) {
            PluginCatalog.candidates.forEach { c ->
                val r = record(c.id)
                if (r.optString("state") in setOf("DOWNLOADING", "VERIFYING")) {
                    r.put("state", "FAILED").put("error", "عملیات قبلی ناتمام ماند؛ دوباره تلاش کنید.")
                }
            }
        }
    }
    private fun record(id: String): JSONObject = data.optJSONObject(id) ?: JSONObject().also { data.put(id, it) }
    /** Copy-on-write means a failed fsync/write cannot change active in memory either. */
    private fun update(id: String, change: (JSONObject) -> Unit) = synchronized(lock) {
        val next = JSONObject(data.toString()); val row = next.optJSONObject(id) ?: JSONObject().also { next.put(id, it) }
        change(row)
        val stream = journal.startWrite()
        try {
            val bytes = next.toString().toByteArray()
            stream.write(bytes); stream.fd.sync(); journal.finishWrite(stream)
            require(journal.openRead().use { it.readBytes() }.contentEquals(bytes)) { "Plugin journal was not committed" }
        }
        catch (e: Exception) { journal.failWrite(stream); throw e }
        data = next; changes.value += 1
    }
    private fun row(id: String) = synchronized(lock) { JSONObject(record(id).toString()) }
    private fun state(id: String, value: PluginState, error: String = "") = update(id) { it.put("state", value.name).put("error", error) }
    data class View(val candidate: PluginCandidate, val state: PluginState, val activeVersion: String?, val release: PluginRelease?, val error: String)
    fun view(id: String): View {
        val c = requireNotNull(PluginCatalog.candidate(id))
        val r = row(id)
        val a = r.optJSONObject("active")
        val release = latest(id)
        val saved = runCatching { PluginState.valueOf(r.optString("state")) }.getOrNull()
        val value = if (a == null && id in incompatible) PluginState.INCOMPATIBLE else if (a != null && active(id) == null) PluginState.BROKEN else saved ?: if (a != null) PluginState.INSTALLED else if (release != null) PluginState.AVAILABLE else PluginState.NOT_INSTALLED
        return View(c, if (value == PluginState.INSTALLED && a != null && release != null && release.versionCode > a.getLong("versionCode")) PluginState.UPDATE_AVAILABLE else value,
            a?.optString("version"), release, r.optString("error"))
    }
    /** Only host-bundled, publisher-signed catalog entries. A backup cannot add a trusted release. */
    private val incompatible = mutableSetOf<String>()
    private val releases: List<PluginRelease> by lazy {
        val list = context.assets.open("plugins/catalog.json").use { JSONObject(String(it.readBytes())) }.getJSONArray("releases")
        (0 until list.length()).mapNotNull { index ->
            val envelope = list.getJSONObject(index)
            try { trust.verify(envelope.toString()) }
            catch (e: PluginIncompatible) {
                // An ID here is a display hint only; this never authorizes installation.
                runCatching { JSONObject(String(java.util.Base64.getDecoder().decode(envelope.getString("payload"))))
                    .getString("id") }.getOrNull()?.let { incompatible.add(it) }; null
            } catch (e: Exception) { null }
        }
    }
    fun latest(id: String) = releases.filter { it.id == id }.maxByOrNull { it.versionCode }
    fun active(id: String): PluginRelease? = row(id).optJSONObject("active")?.optString("envelope")?.let { runCatching { trust.verify(it) }.getOrNull() }
    private fun saved(r: PluginRelease) = JSONObject().put("envelope", r.signedEnvelope).put("version", r.version).put("versionCode", r.versionCode).put("package", r.packageName)
    private fun dependencies(r: PluginRelease, seen: Set<String> = emptySet()) {
        require(r.id !in seen) { "Cyclic plugin dependency" }
        r.dependencies.forEach { d ->
            val installed = active(d.id) ?: error("افزونهٔ وابسته نصب نیست: ${d.id}")
            require(installed.versionCode >= d.minVersionCode)
            verifyInstalled(installed, seen + r.id)
        }
    }
    @Suppress("DEPRECATION")
    private fun flags() = PackageManager.GET_SERVICES or PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or
        PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun verifyPackage(info: PackageInfo, release: PluginRelease) {
        require(info.packageName == release.packageName && info.sharedUserId == null) { "APK identity mismatch" }
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(code == release.versionCode && info.versionName == release.version) { "APK version mismatch" }
        require(info.splitNames.isNullOrEmpty()) { "Split APKs are not supported by API 1" }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        require(signatures?.size == 1 && PluginTrust.sha256(signatures.single().toByteArray()) == release.certificateSha256) { "APK signer mismatch" }
        val app = requireNotNull(info.applicationInfo)
        require(app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) { "Debug plugins cannot be activated" }
        val permissions = setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.WAKE_LOCK")
        require(info.requestedPermissions.orEmpty().all { it in permissions }) { "Plugin requests unapproved permissions" }
        require(info.activities.orEmpty().none { it.exported } && info.receivers.orEmpty().none { it.exported } &&
            info.providers.orEmpty().none { it.exported } && info.services.orEmpty().none { it.exported && it.name != release.serviceClass }) {
            "Unexpected exported plugin component"
        }
        val service = info.services?.singleOrNull { it.name == release.serviceClass } ?: error("Plugin service absent")
        require(service.exported && service.enabled && service.permission == null)
        val meta = requireNotNull(service.metaData)
        require(meta.getString("net.gozar.plugin.ID") == release.id && meta.getInt("net.gozar.plugin.API") == release.apiVersion) { "Service metadata mismatch" }
    }
    private fun hash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun verifyFile(file: File, r: PluginRelease) {
        trust.verify(r.signedEnvelope)
        require(file.length() == r.size && hash(file) == r.sha256) { "APK hash/size mismatch" }
        ZipFile(file).use { z ->
            val abis = z.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") && it.endsWith(".so") }.map { it.split('/')[1] }.toSet()
            require(abis.containsAll(r.abis) && abis.any { it in Build.SUPPORTED_ABIS }) { "APK native ABI mismatch" }
        }
        verifyPackage(context.packageManager.getPackageArchiveInfo(file.path, flags()) ?: error("Unreadable APK"), r)
    }
    fun verifyInstalled(r: PluginRelease, seen: Set<String> = emptySet()) {
        trust.verify(r.signedEnvelope); dependencies(r, seen)
        val info = context.packageManager.getPackageInfo(r.packageName, flags())
        verifyPackage(info, r)
        val apk = File(requireNotNull(info.applicationInfo).sourceDir)
        require(apk.length() == r.size && hash(apk) == r.sha256) { "Installed APK differs from approved release" }
    }
    private val confirmations = java.util.concurrent.ConcurrentHashMap<String, Intent>()
    fun continueInstall(activity: Activity, id: String) {
        val pending = row(id).optJSONObject("pending") ?: return
        val intent = confirmations[id] ?: context.packageManager.packageInstaller.getSessionInfo(pending.optInt("session", -1))?.createDetailsIntent()
        if (intent != null) runCatching { activity.startActivity(intent) }
    }
    fun enqueueResult(intent: Intent) = scope.launch { installResult(intent) }
    fun requestInstall(activity: Activity, id: String) {
        if (latest(id) == null) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        scope.launch { mutation.withLock {
            val release = latest(id)
            if (release == null) { state(id, PluginState.NOT_INSTALLED, "هنوز APK سازگار و امضاشدهٔ تأییدشده منتشر نشده است."); return@withLock }
            if (row(id).has("pending")) { state(id, PluginState.INSTALLING, "نصب قبلی را در Android تکمیل یا لغو کنید."); return@withLock }
            val temp = File(root, "${UUID.randomUUID()}.apk.part")
            try {
                dependencies(release)
                val staged = row(id).optJSONObject("staged")
                if (staged?.optString("package") == release.packageName) {
                    if (runCatching { verifyInstalled(release) }.isSuccess) { activate(release); return@withLock }
                    update(id) { it.remove("staged") } // Missing/corrupt inactive APK can be downloaded again.
                }
                require(active(id)?.packageName != release.packageName) { "نسخهٔ فعال جایگزین نمی‌شود؛ Repair را اجرا کنید." }
                state(id, PluginState.DOWNLOADING)
                val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false // signed URL must be the final approved HTTPS origin
                    connection.connectTimeout = 20000; connection.readTimeout = 20000
                    require(connection.responseCode == 200) { "Download failed or redirected" }
                    connection.inputStream.use { input -> temp.outputStream().use { out ->
                        val buf = ByteArray(65536); var total = 0L
                        while (true) { ensureActive(); val n = input.read(buf); if (n < 0) break; total += n
                            require(total <= release.size) { "APK exceeds signed size" }; out.write(buf, 0, n) }
                        out.fd.sync()
                    } }
                } finally { connection.disconnect() }
                state(id, PluginState.VERIFYING); verifyFile(temp, release)
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(release.packageName); setSize(release.size)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val sessionId = installer.createSession(params); val nonce = UUID.randomUUID().toString()
                try {
                    installer.openSession(sessionId).use { session ->
                        session.openWrite("base.apk", 0, release.size).use { out -> temp.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                        update(id) { it.put("pending", saved(release).put("session", sessionId).put("nonce", nonce)).put("state", "INSTALLING").put("error", "") }
                        val callback = Intent(context, PluginInstallReceiver::class.java).setAction("net.gozar.plugin.INSTALL")
                            .setData(Uri.parse("ghajar-plugin://install/$nonce")).putExtra("pluginId", id).putExtra("nonce", nonce)
                        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                        session.commit(PendingIntent.getBroadcast(context, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT or mutable).intentSender)
                    }
                } catch (e: Exception) {
                    runCatching { installer.abandonSession(sessionId) }; update(id) { it.remove("pending") }; throw e
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state(id, PluginState.FAILED, e.message ?: e.javaClass.simpleName) }
            finally { temp.delete() }
        } }
    }
    suspend fun installResult(intent: Intent) = mutation.withLock {
        val id = intent.getStringExtra("pluginId") ?: return@withLock
        if (PluginCatalog.candidate(id) == null) return@withLock
        val pending = row(id).optJSONObject("pending") ?: return@withLock
        if (intent.getStringExtra("nonce") != pending.optString("nonce") || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != pending.optInt("session")) return@withLock
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return@withLock
            // OS consent is shown from a notification if background activity launch is disallowed.
            confirmations[id] = confirm
            changes.value += 1
            PluginNotifications.confirm(context, confirm, pending.getInt("session"))
            return@withLock
        }
        try {
            require(status == PackageInstaller.STATUS_SUCCESS) { "نصب در Android کامل نشد؛ نسخهٔ قبلی حفظ شد." }
            update(id) { it.put("staged", pending) }
            activate(trust.verify(pending.getString("envelope")))
        } catch (e: Exception) { state(id, PluginState.FAILED, e.message ?: "Plugin verification failed") }
        finally { confirmations.remove(id); update(id) { it.remove("pending") } }
    }
    private suspend fun activate(release: PluginRelease) = PluginRuntime.whileInactive(release.id) {
        verifyInstalled(release)
        // No activation while a live tunnel holds this plugin slot.
        PluginRpc(context, release).use { rpc -> rpc.open(); rpc.health() }
        update(release.id) { r ->
            val old = r.optJSONObject("active")
            val slots = PluginSlots(old?.optString("package"), r.optJSONObject("previous")?.optString("package")).activate(release.packageName, healthy = true)
            if (slots.previous == old?.optString("package") && old != null) r.put("previous", old)
            r.put("active", saved(release)).put("state", "INSTALLED").put("error", "")
            r.remove("staged")
        }
    }
    fun repair(id: String) = scope.launch { mutation.withLock {
        try {
            val installed = row(id).optJSONObject("staged")?.getString("envelope")?.let { trust.verify(it) } ?: active(id) ?: error("افزونه نصب نیست؛ نصب مجدد را انتخاب کنید.")
            activate(installed)
        } catch (e: Exception) { state(id, PluginState.BROKEN, e.message ?: "Health check failed") }
    } }
    fun rollback(id: String) = scope.launch { mutation.withLock {
        try { val previous = row(id).optJSONObject("previous") ?: error("نسخهٔ قبلی موجود نیست")
            activate(trust.verify(previous.getString("envelope")))
        } catch (e: Exception) { state(id, PluginState.FAILED, e.message ?: "Rollback failed") }
    } }
    fun remove(activity: Activity, id: String) {
        require(!PluginRuntime.isUsing(id)) { "ابتدا اتصال را قطع کنید." }
        require(PluginCatalog.candidates.none { it.id != id && active(it.id)?.dependencies?.any { d -> d.id == id } == true }) { "افزونهٔ دیگری به این نسخه وابسته است." }
        val r = active(id) ?: return
        activity.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${r.packageName}")))
        // Never erase config or mark removed before OS confirms it. reconcile() runs on UI resume.
    }
    fun reconcile() = scope.launch { mutation.withLock {
        root.listFiles()?.filter { it.name.endsWith(".apk.part") }?.forEach { it.delete() }
        val pendingIds = PluginCatalog.candidates.mapNotNull { row(it.id).optJSONObject("pending")?.optInt("session") }.toSet()
        context.packageManager.packageInstaller.mySessions.filter { session ->
            session.sessionId !in pendingIds && PluginCatalog.candidates.any { session.appPackageName?.startsWith("net.ghajar.plugin.${it.id}.v") == true }
        }.forEach { runCatching { context.packageManager.packageInstaller.abandonSession(it.sessionId) } }
        PluginCatalog.candidates.forEach { c ->
            val a = active(c.id)
            if (a != null && runCatching { context.packageManager.getPackageInfo(a.packageName, 0) }.isFailure) {
                update(c.id) { it.remove("active"); it.put("state", "NOT_INSTALLED").put("error", "افزونه حذف شده؛ کانفیگ‌ها نگه‌داری شده‌اند.") }
            }
            val pending = row(c.id).optJSONObject("pending")
            if (pending != null && context.packageManager.packageInstaller.getSessionInfo(pending.optInt("session", -1)) == null) {
                try { activate(trust.verify(pending.getString("envelope"))) }
                catch (e: Exception) { state(c.id, PluginState.FAILED, "نصب ناتمام یا نامعتبر؛ دوباره تلاش کنید.") }
                finally { update(c.id) { it.remove("pending") } }
            }
        }
    } }
    fun backup(): JSONArray = JSONArray().apply {
        PluginCatalog.candidates.forEach { c -> val r = row(c.id); val a = r.optJSONObject("active")
            put(JSONObject().put("id", c.id).put("version", a?.optString("version") ?: r.optString("requestedVersion"))
                .put("settings", r.optJSONObject("settings") ?: JSONObject())) }
    }
    fun restore(a: JSONArray) {
        require(a.length() <= 64)
        for (i in 0 until a.length()) { val r = a.getJSONObject(i); val id = r.getString("id")
            if (PluginCatalog.candidate(id) == null) continue
            require((r.optJSONObject("settings")?.toString()?.toByteArray()?.size ?: 0) <= 65536)
            update(id) { it.put("requestedVersion", r.optString("version").take(128))
                .put("settings", r.optJSONObject("settings") ?: JSONObject()) }
        } // No trust anchors, active slots, URLs or binaries ever restored.
    }
    companion object {
        @Volatile private var instance: PluginManager? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: PluginManager(context.applicationContext).also { instance = it } }
    }
}

class PluginInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { val job = PluginManager.get(context).enqueueResult(intent)
                withTimeoutOrNull(7000) { job.join() } // Process death is recovered by the persisted pending slot.
            } finally { pending.finish() }
        }
    }
}
