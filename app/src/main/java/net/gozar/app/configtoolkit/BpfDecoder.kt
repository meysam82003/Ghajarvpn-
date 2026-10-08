package net.gozar.app.configtoolkit

import net.gozar.app.engine.SingBoxFull
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

/**
 * sing-box profile files (`.bpf`), as the official clients export them.
 *
 * Read from the implementation, not guessed (sing-box
 * experimental/libbox/profile_import.go, `ProfileContent.Encode` /
 * `DecodeProfileContent`): one byte message type (3 = ProfileContent), one
 * byte version (0 or 1), then a gzip stream holding uvarint-length strings and
 * big-endian integers: name, int32 type (0 local, 1 iCloud, 2 remote), config,
 * then for non-local types the remote path, and for remote ones a bool
 * auto-update, an int32 interval (version >= 1 only) and an int64 last-updated.
 * No generation of this format is encrypted.
 */
object SingBoxProfileFile {
    const val MESSAGE_TYPE_PROFILE_CONTENT = 3
    const val MAX_VERSION = 1
    const val MAX_DECOMPRESSED = 4L * 1024 * 1024

    data class Content(
        val name: String,
        val type: Int,
        val config: String,
        val remotePath: String,
        val autoUpdate: Boolean,
        val autoUpdateInterval: Int,
        val lastUpdated: Long
    )

    private fun BadHeader() = ConfigToolkitException.InvalidConfig("سرآیند فایل BPF معتبر نیست.")
    private fun UnsupportedVersion(v: Int) = ConfigToolkitException.InvalidConfig("نسخهٔ $v فایل BPF پشتیبانی نمی‌شود.")
    private fun Corrupt(why: String) = ConfigToolkitException.InvalidConfig("محتوای فایل BPF خراب است ($why).")

    fun looksLike(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0].toInt() == MESSAGE_TYPE_PROFILE_CONTENT && bytes[1].toInt() in 0..MAX_VERSION &&
            (bytes[2].toInt() and 0xff) == 0x1f && (bytes[3].toInt() and 0xff) == 0x8b

    fun decode(bytes: ByteArray): Content {
        if (bytes.size < 4 || bytes[0].toInt() != MESSAGE_TYPE_PROFILE_CONTENT) throw BadHeader()
        val version = bytes[1].toInt() and 0xff
        if (version > MAX_VERSION) throw UnsupportedVersion(version)
        val input = try {
            Bounded(GZIPInputStream(ByteArrayInputStream(bytes, 2, bytes.size - 2)), MAX_DECOMPRESSED)
        } catch (e: ZipException) { throw Corrupt("gzip") } catch (e: EOFException) { throw Corrupt("gzip") }
        try {
            val name = string(input)
            val type = int32(input)
            if (type !in 0..2) throw Corrupt("type $type")
            val config = string(input)
            val remotePath = if (type != 0) string(input) else ""
            var autoUpdate = false; var interval = 0; var last = 0L
            if (type == 2 || (version == 0 && type != 0)) {
                autoUpdate = byte(input) != 0
                if (version >= 1) interval = int32(input)
                last = int64(input)
            }
            return Content(name, type, config, remotePath, autoUpdate, interval, last)
        } catch (e: EOFException) { throw Corrupt("truncated") } catch (e: ZipException) { throw Corrupt("gzip") }
    }

    private fun byte(i: InputStream): Int = i.read().also { if (it < 0) throw EOFException() }

    private fun uvarint(i: InputStream): Long {
        var x = 0L; var s = 0
        repeat(10) {
            val b = byte(i)
            if (b < 0x80) return x or (b.toLong() shl s)
            x = x or ((b and 0x7f).toLong() shl s); s += 7
        }
        throw Corrupt("varint")
    }

    private fun string(i: InputStream): String {
        val n = uvarint(i)
        if (n < 0 || n > MAX_DECOMPRESSED) throw Corrupt("length")
        val out = ByteArray(n.toInt())
        var off = 0
        while (off < out.size) { val r = i.read(out, off, out.size - off); if (r < 0) throw EOFException(); off += r }
        return String(out, Charsets.UTF_8)
    }

    private fun int32(i: InputStream): Int = (byte(i) shl 24) or (byte(i) shl 16) or (byte(i) shl 8) or byte(i)
    private fun int64(i: InputStream): Long = (int32(i).toLong() shl 32) or (int32(i).toLong() and 0xffffffffL)

    /** Decompression-bomb guard: refuses to read past [limit] bytes of output. */
    private class Bounded(private val inner: InputStream, private val limit: Long) : InputStream() {
        private var count = 0L
        override fun read(): Int { val b = inner.read(); if (b >= 0 && ++count > limit) throw Corrupt("too large"); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = inner.read(b, off, len); if (n > 0) { count += n; if (count > limit) throw Corrupt("too large") }; return n
        }
    }
}

class BpfDecoder : ConfigDecoder {
    override val format = ConfigFormat.BPF

    override fun decode(input: ConfigInput): ParsedConfig {
        val content = SingBoxProfileFile.decode(input.bytes)
        val profile = try {
            SingBoxFull.profile(content.name, content.config)
        } catch (e: SingBoxFull.Invalid) {
            throw ConfigToolkitException.InvalidConfig(e.message.orEmpty())
        }
        val warnings = buildList {
            if (content.type == 2 && content.remotePath.isNotBlank()) add("این پروفایل از یک آدرس راه دور به‌روز می‌شد؛ نسخهٔ ذخیره‌شده در فایل وارد شد.")
        }
        return ParsedConfig(ConfigFormat.BPF, listOf(NormalizedProfile.from(profile, ConfigFormat.BPF)), null, null, warnings)
    }

    override fun validate(parsed: ParsedConfig): ValidationResult = ValidationResult(emptyList())
}
