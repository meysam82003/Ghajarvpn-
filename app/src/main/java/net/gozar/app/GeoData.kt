package net.gozar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest

/**
 * The routing data files Xray reads (geoip.dat, geosite.dat): what is
 * installed, and updating them from a published release. A download only
 * replaces the installed file after its SHA-256 matches the checksum file the
 * same release publishes; otherwise the old file stays.
 */
object GeoData {

    val FILES = listOf("geoip.dat", "geosite.dat")

    enum class Source(val key: String, val base: String) {
        IRAN("iran", "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/"),
        LOYALSOLDIER("loyalsoldier", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/")
    }

    data class Info(val name: String, val size: Long, val modifiedMs: Long, val bundled: Boolean)

    private fun dir(ctx: Context) = ctx.filesDir
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ghajar_geodata", Context.MODE_PRIVATE)

    fun info(ctx: Context): List<Info> = FILES.map { name ->
        val f = File(dir(ctx), name)
        Info(name, if (f.exists()) f.length() else 0L, if (f.exists()) f.lastModified() else 0L,
            bundled = prefs(ctx).getString("src_$name", null) == null)
    }

    fun sourceOf(ctx: Context, name: String): String? = prefs(ctx).getString("src_$name", null)

    private fun proxy(): Proxy =
        if (VpnState.state.value == Connection.CONNECTED && !IkeController.active)
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value))
        else Proxy.NO_PROXY

    private fun fetch(url: String, out: File?, limit: Long): ByteArray? {
        var target = url
        repeat(5) {
            val c = (URL(target).openConnection(proxy()) as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "GhajarVPN")
            }
            try {
                val code = c.responseCode
                if (code in 300..399) { target = c.getHeaderField("Location") ?: return null; return@repeat }
                if (code != 200) return null
                c.inputStream.use { input ->
                    if (out == null) return input.readBytes()
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024); var total = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            total += n; if (total > limit) return null
                            o.write(buf, 0, n)
                        }
                    }
                    return ByteArray(0)
                }
            } finally { c.disconnect() }
        }
        return null
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Updates every file from [source]; returns one line per file. Blocking IO off the main thread. */
    suspend fun update(ctx: Context, source: Source): List<Pair<String, String?>> = withContext(Dispatchers.IO) {
        FILES.map { name ->
            val tmp = File(ctx.cacheDir, "$name.part")
            val err = runCatching {
                val sumText = fetch(source.base + name + ".sha256sum", null, 4096)?.toString(Charsets.UTF_8)
                    ?: return@runCatching "checksum file not reachable"
                val want = sumText.trim().substringBefore(' ').lowercase()
                if (!Regex("^[0-9a-f]{64}$").matches(want)) return@runCatching "checksum file is not a SHA-256"
                fetch(source.base + name, tmp, 64L * 1024 * 1024) ?: return@runCatching "download failed"
                val got = sha256(tmp)
                if (got != want) return@runCatching "SHA-256 mismatch"
                val dest = File(dir(ctx), name)
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true) }
                prefs(ctx).edit().putString("src_$name", source.key).putString("sha_$name", got).apply()
                null
            }.getOrElse { it.javaClass.simpleName }
            tmp.delete()
            name to err
        }
    }

    /** Puts the copies shipped in the APK back. */
    suspend fun restoreBundled(ctx: Context) = withContext(Dispatchers.IO) {
        FILES.forEach { name ->
            runCatching {
                ctx.assets.open(name).use { i -> File(dir(ctx), name).outputStream().use { o -> i.copyTo(o) } }
                prefs(ctx).edit().remove("src_$name").remove("sha_$name").apply()
            }
        }
    }
}
