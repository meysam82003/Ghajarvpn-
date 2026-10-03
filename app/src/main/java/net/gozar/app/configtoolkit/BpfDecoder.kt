package net.gozar.app.configtoolkit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import org.json.JSONObject

/** Independent bounded reader of libbox/profile_import.go at sing-box 132b38e. */
object BpfParser {
    const val MAX_INPUT = 8 * 1024 * 1024
    const val MAX_PAYLOAD = 8 * 1024 * 1024
    enum class Error { INVALID_HEADER, UNSUPPORTED_VERSION, CORRUPT_GZIP, INVALID_METADATA, INVALID_JSON, SIZE_LIMIT }
    class Failure(val code: Error) : IllegalArgumentException("BPF: ${code.name}")
    data class Profile(val version: Int, val name: String, val type: Int, val config: String,
                       val remotePath: String, val autoUpdate: Boolean, val interval: Int, val lastUpdated: Long) {
        override fun toString() = "BpfProfile(redacted)"
        fun metadata() = JSONObject().put("version", version).put("profileType", type).put("remotePath", remotePath)
            .put("autoUpdate", autoUpdate).put("updateInterval", interval).put("lastUpdated", lastUpdated)
    }
    fun looksLike(bytes: ByteArray) = bytes.size >= 1 && bytes[0] == 3.toByte()
    fun parse(bytes: ByteArray): Profile {
        if (bytes.size > MAX_INPUT) throw Failure(Error.SIZE_LIMIT)
        if (bytes.size < 2 || bytes[0] != 3.toByte()) throw Failure(Error.INVALID_HEADER)
        val version = bytes[1].toInt() and 255
        if (version !in 0..1) throw Failure(Error.UNSUPPORTED_VERSION)
        val payload = try { inflate(bytes.copyOfRange(2,bytes.size)) }
            catch (e: Failure) { throw e } catch (_: Exception) { throw Failure(Error.CORRUPT_GZIP) }

        try {
            val reader = Reader(payload)
            val name = reader.string(16 * 1024)
            val type = reader.int(); require(type in 0..2)
            val config = reader.string(MAX_PAYLOAD)
            val remote = if (type != 0) reader.string(64 * 1024) else ""
            var update = false; var interval = 0; var last = 0L
            if (type == 2 || version == 0 && type != 0) {
                val flag = reader.byte(); require(flag in 0..1); update = flag == 1
                if (version >= 1) interval = reader.int()
                last = reader.long(); require(interval >= 0 && last >= 0)
            }
            require(reader.left() == 0)
            try { BoundedJson.objectValue(config) } catch (_: Exception) { throw Failure(Error.INVALID_JSON) }
            return Profile(version, name, type, config, remote, update, interval, last)
        } catch (e: Failure) { throw e } catch (_: Exception) { throw Failure(Error.INVALID_METADATA) }
    }
    /** One gzip member with bounded output and verified CRC/ISIZE; trailing junk is never ignored. */
    private fun inflate(b: ByteArray): ByteArray {
        require(b.size >= 18 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte() && b[2] == 8.toByte())
        val flags = b[3].toInt() and 255; require(flags and 0xe0 == 0)
        var p=10
        fun u16(i: Int): Int { require(i+2<=b.size);return (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8) }
        if(flags and 4 != 0){val n=u16(p);p+=2;require(n<=b.size-p);p+=n}
        for(flag in intArrayOf(8,16))if(flags and flag != 0){while(p<b.size&&b[p]!=0.toByte())p++;require(p<b.size);p++}
        if(flags and 2 != 0){val crc=java.util.zip.CRC32().apply {update(b,0,p)};require(u16(p)==(crc.value.toInt() and 65535));p+=2}
        require(p<=b.size-8)
        val inflater=java.util.zip.Inflater(true)
        val out=ByteArrayOutputStream()
        try {
            inflater.setInput(b,p,b.size-p)
            val buffer=ByteArray(8192)
            while(!inflater.finished()){
                val n=inflater.inflate(buffer)
                if(out.size()+n>MAX_PAYLOAD)throw Failure(Error.SIZE_LIMIT)
                require(n>0 || inflater.finished())
                out.write(buffer,0,n)
            }
            val end=b.size-inflater.remaining
            require(end==b.size-8)
            fun u32(i:Int):Long = (0..3).fold(0L){v,j->v or ((b[i+j].toLong() and 255) shl (8*j))}
            val data=out.toByteArray();val crc=java.util.zip.CRC32().apply{update(data)}
            require(u32(end)==crc.value && u32(end+4)==data.size.toLong())
            return data
        }finally{inflater.end()}
    }
    private class Reader(val data: ByteArray) {
        var p = 0
        fun left() = data.size - p
        fun byte(): Int { require(left() > 0); return data[p++].toInt() and 255 }
        fun int(): Int { require(left() >= 4); return ByteBuffer.wrap(data, p, 4).int.also { p += 4 } }
        fun long(): Long { require(left() >= 8); return ByteBuffer.wrap(data, p, 8).long.also { p += 8 } }
        fun string(limit: Int): String {
            var length = 0L; var shift = 0
            while (true) { val v = byte(); require(shift < 63); length = length or ((v and 127).toLong() shl shift)
                if (v < 128) break; shift += 7 }
            require(length <= limit && length <= left())
            val n = length.toInt()
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, p, n)).toString().also { p += n }
        }
    }
}

class BpfDecoder : ConfigDecoder {
    override val format = ConfigFormat.BPF
    override fun decode(input: ConfigInput): ParsedConfig {
        val p = try { BpfParser.parse(input.bytes) } catch (e: BpfParser.Failure) {
            val reason = when(e.code) {
                BpfParser.Error.INVALID_HEADER -> "سرآیند BPF معتبر نیست."
                BpfParser.Error.UNSUPPORTED_VERSION -> "نسخهٔ BPF پشتیبانی نمی‌شود."
                BpfParser.Error.CORRUPT_GZIP -> "دادهٔ gzip در BPF خراب یا ناقص است."
                BpfParser.Error.INVALID_METADATA -> "متادیتای BPF نامعتبر یا ناقص است."
                BpfParser.Error.INVALID_JSON -> "JSON داخل BPF نامعتبر است یا از محدودیت امن عبور می‌کند."
                BpfParser.Error.SIZE_LIMIT -> "حجم BPF یا دادهٔ بازشده از سقف امن بیشتر است."
            }; throw ConfigToolkitException.InvalidConfig(reason)
        }
        val c = net.gozar.app.engine.FullSingBoxProfile.create(p.config, p.name, p.metadata())
        return ParsedConfig(format, listOf(NormalizedProfile.from(c, format, p.config)), p.config,
            warnings = listOfNotNull(net.gozar.app.engine.FullSingBoxProfile.blockReason(c)))
    }
}
