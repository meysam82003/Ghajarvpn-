package net.gozar.app.security.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 9106 Argon2id v1.3. No implicit fallback; parameters are authenticated by master-key wrapping. */
object VaultKdf {
    data class Parameters(val memoryKiB: Int = 65536, val iterations: Int = 3, val parallelism: Int = 1) {
        fun validate() {
            require(memoryKiB in 32768..131072 && iterations in 2..6 && parallelism in 1..4)
            require(memoryKiB % (4 * parallelism) == 0)
        }
    }
    @Synchronized fun passwordKey(password: CharArray, salt: ByteArray, p: Parameters): ByteArray {
        p.validate(); require(salt.size == 16 && password.isNotEmpty() && password.size <= 1024)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt).withMemoryAsKB(p.memoryKiB).withIterations(p.iterations).withParallelism(p.parallelism).build()
        return try { ByteArray(32).also { Argon2BytesGenerator().apply { init(params) }.generateBytes(password,it) } }
        finally { params.clear() }
    }
    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key,"HmacSHA256")); doFinal(data)
    }
    /** RFC 5869 extract+expand, one 32-byte block with domain-separated info. */
    fun derive(master: ByteArray, salt: ByteArray, info: String): ByteArray {
        val prk=hmac(salt,master)
        return try { hmac(prk,info.toByteArray(Charsets.UTF_8)+byteArrayOf(1)) } finally { prk.fill(0) }
    }
}
