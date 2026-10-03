package net.gozar.app.security.vault

import net.gozar.app.configtoolkit.BoundedJson
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

object VaultFormat {
    val magic = "GSB2".toByteArray(Charsets.US_ASCII)
    const val MAX_FILE = 16 * 1024 * 1024
    const val MAX_ENTRIES = 2048
    const val MAX_HEADER = 4096
    internal data class Envelope(val header: JSONObject, val prefix: ByteArray, val wrapped: ByteArray, val body: ByteArray, val mac: ByteArray)
    fun isV2(b: ByteArray) = b.size >= 4 && b.copyOfRange(0,4).contentEquals(magic)
    internal fun utf8(b: ByteArray): String = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
    internal fun encode(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    internal fun decode(s: String, max: Int): ByteArray {
        require(s.length <= ((max.toLong()+2)/3*4))
        return Base64.getDecoder().decode(s).also { require(it.size <= max) }
    }
    internal fun parse(b: ByteArray): Envelope {
        if (b.size !in 9..MAX_FILE || !isV2(b)) throw VaultException(VaultException.Kind.CORRUPT)
        if (b[4].toInt() != 1) throw VaultException(VaultException.Kind.UNSUPPORTED_VERSION)
        try {
            val n=ByteBuffer.wrap(b,5,4).int
            require(n in 1..MAX_HEADER && b.size >= 9+n+48+2+32)
            val prefix=b.copyOfRange(0,9+n)
            val header=BoundedJson.objectValue(utf8(b.copyOfRange(9,9+n)))
            require(header.getString("cipher")=="AES-256-GCM" && header.getString("kdf")=="Argon2id-19")
            return Envelope(header,prefix,b.copyOfRange(9+n,9+n+48),b.copyOfRange(9+n+48,b.size-32),b.takeLast(32).toByteArray())
        } catch (e: VaultException) { throw e } catch (_: Exception) { throw VaultException(VaultException.Kind.CORRUPT) }
    }
    // Entry ciphertext is base64 and larger than the 1 MiB plaintext payload limit.
    internal fun json(b: ByteArray) = BoundedJson.objectValue(utf8(b),3*1024*1024)
    /** Stable object ordering; arrays retain their semantic order. */
    fun canonical(v: Any?): String = when(v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(",","{","}") { JSONObject.quote(it)+":"+canonical(v.get(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",","[","]") { canonical(v.get(it)) }
        is String -> JSONObject.quote(v)
        is Boolean, is Number -> v.toString()
        else -> error("Invalid JSON value")
    }
}
