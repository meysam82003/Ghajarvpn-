package net.gozar.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val API = "https://api.github.com/repos/meysam82003/Ghajarvpn-/releases/latest"
    private const val RELEASES = "https://github.com/meysam82003/Ghajarvpn-/releases/latest"

    data class ReleaseAsset(val name: String, val url: String, val sizeBytes: Long)

    sealed interface Result {
        data class Available(
            val version: String,
            val url: String,
            val changelog: String,
            /** The APK asset matching this device's ABI, if the release has one. */
            val apk: ReleaseAsset?,
            /** Expected SHA-256 of [apk], parsed from the release's own SHA256SUMS.txt asset. */
            val apkSha256: String?
        ) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    suspend fun check(currentVersion: String, abis: List<String> = android.os.Build.SUPPORTED_ABIS.toList()): Result =
        withContext(Dispatchers.IO) {
            try {
                val o = JSONObject(get(API))
                val tag = o.optString("tag_name").removePrefix("v").removePrefix("V").trim()
                val url = o.optString("html_url").ifEmpty { RELEASES }
                if (tag.isEmpty() || !isNewer(tag, currentVersion)) {
                    return@withContext if (tag.isEmpty()) Result.Failed else Result.UpToDate
                }
                release(o, abis)

            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                Result.Failed
            }
        }

    data class ReleasePage(val releases: List<Result.Available>, val nextPage: Int?)

    /** Published releases only, paginated; no synthetic version or download URL. */
    suspend fun history(page: Int = 1, abis: List<String> = android.os.Build.SUPPORTED_ABIS.toList()): ReleasePage = withContext(Dispatchers.IO) {
        require(page in 1..1000)
        val rows = JSONArray(get("https://api.github.com/repos/meysam82003/Ghajarvpn-/releases?per_page=30&page=$page"))
        val releases = rows.objects().filterNot { it.optBoolean("draft") || it.optBoolean("prerelease") }
            .mapNotNull { runCatching { release(it, abis) }.getOrNull() }
        ReleasePage(releases, if (rows.length() == 30) page + 1 else null)
    }

    private fun release(o: JSONObject, abis: List<String>): Result.Available {
        val tag = o.optString("tag_name").removePrefix("v").removePrefix("V").trim()
        require(tag.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}")))
        val assets = o.optJSONArray("assets").orEmptyArray().objects().mapNotNull { a ->
            val name = a.optString("name"); val url = a.optString("browser_download_url"); val size = a.optLong("size")
            if (name.isBlank() || name.contains('/') || name.contains('\\') || !releaseUrl(url)) null else ReleaseAsset(name,url,size)
        }
        val apk = selectApk(assets, abis)
        val digest = o.optJSONArray("assets").orEmptyArray().objects().firstOrNull { it.optString("name") == apk?.name }
            ?.optString("digest")?.removePrefix("sha256:")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
        val sums = assets.firstOrNull { it.name.equals("SHA256SUMS.txt",true) }
        val hash = digest ?: if(apk != null && sums != null) runCatching { parseSha256Sums(get(sums.url),apk.name) }.getOrNull() else null
        val html = o.optString("html_url").takeIf(::releaseUrl) ?: RELEASES
        return Result.Available(tag, html, o.optString("body"), apk, hash)
    }

    internal fun selectApk(assets: List<ReleaseAsset>, abis: List<String>): ReleaseAsset? =
        abis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.name.endsWith("-$abi.apk",true) } }
            ?: assets.firstOrNull { it.name.endsWith("-universal.apk",true) }
            ?: assets.singleOrNull { it.name.endsWith(".apk",true) && !Regex("(arm64-v8a|armeabi-v7a|x86_64|x86)",RegexOption.IGNORE_CASE).containsMatchIn(it.name) }

    internal fun releaseUrl(value: String): Boolean = runCatching {
        val u=java.net.URI(value)
        u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 &&
            u.path.startsWith("/meysam82003/Ghajarvpn-/releases/")
    }.getOrDefault(false)

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Ghajar VPN")
            setRequestProperty("Accept", "application/vnd.github+json, text/plain")
        }
        return try {
            conn.inputStream.use { net.gozar.app.configtoolkit.BoundedInput.read(it, 4L * 1024 * 1024).toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    /** `SHA256SUMS.txt` lines look like "<hex>  <filename>" (sha256sum's own format). */
    internal fun parseSha256Sums(text: String, fileName: String): String? =
        text.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2 && parts[1].trimStart('*') == fileName) parts[0].lowercase() else null
            }
            .firstOrNull { it.matches(Regex("[a-f0-9]{64}")) }

    private fun JSONArray?.orEmptyArray(): JSONArray = this ?: JSONArray()
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)

    /** Numeric dot-segment comparison (1.9.0 < 1.10.0), matching this repo's plain "MAJOR.MINOR.PATCH" tags. */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.substringBefore('-').substringBefore('+')
            .split('.').map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
}
