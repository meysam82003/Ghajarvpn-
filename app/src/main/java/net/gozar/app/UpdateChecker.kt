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
                val assets = o.optJSONArray("assets").orEmptyArray().objects().mapNotNull { a ->
                    val name = a.optString("name")
                    val assetUrl = a.optString("browser_download_url")
                    if (name.isBlank() || assetUrl.isBlank()) null
                    else ReleaseAsset(name, assetUrl, a.optLong("size"))
                }
                val apk = abis.firstNotNullOfOrNull { abi -> assets.find { it.name.endsWith("-$abi.apk", true) } }
                    ?: assets.firstOrNull { it.name.endsWith(".apk", true) }
                val sumsUrl = assets.firstOrNull { it.name.equals("SHA256SUMS.txt", true) }?.url
                val sha256 = apk?.let { a -> sumsUrl?.let { parseSha256Sums(get(it), a.name) } }
                Result.Available(
                    version = tag,
                    url = url,
                    changelog = o.optString("body"),
                    apk = apk,
                    apkSha256 = sha256
                )
            } catch (e: Exception) {
                Result.Failed
            }
        }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Ghajar VPN")
            setRequestProperty("Accept", "application/vnd.github+json, text/plain")
        }
        return try {
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
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
            .firstOrNull()

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
