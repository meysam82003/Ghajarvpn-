package net.gozar.app.configtoolkit

import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/** Public format tables pinned to MIT Pantegnos v9.4.3; see docs/licenses/PANTEGNOS_NPV_MIT.txt.
 * Independent shared nibble-network implementation, no key guessing or device key extraction.
 */
internal object NpvWhitebox {
    private val shift = intArrayOf(0,5,10,15,4,9,14,3,8,13,2,7,12,1,6,11)
    private fun resource(name: String, size: Int): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/npv/$name")).use { it.readBytes() }.also { require(it.size == size) }
    private val ty by lazy { resource("npvt-tyBoxes.bin", 16384) }
    private val mb by lazy { resource("npvt-mbl.bin", 16384) }
    private val xor by lazy { resource("npvt-xorTable.bin", 24576) }
    private val last by lazy { resource("npvt-tboxesLast.bin", 4096) }
    private val last2 by lazy { resource("tboxes_last_v2.bin", 4096) }
    private val npvsLast1 by lazy { resource("tboxes_last.bin", 4096) }
    private val gen2 by lazy {
        InflaterInputStream(requireNotNull(javaClass.getResourceAsStream("/npv/gen2_tables.bin.z"))).use { input ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
            while (true) { val n = input.read(buf); if(n < 0) break; require(out.size() + n <= 749568); out.write(buf,0,n) }
            out.toByteArray().also { require(it.size == 749568) }
        }
    }
    private fun byte(b: ByteArray, p: Int) = b[p].toInt() and 255
    private fun word(b: ByteArray, p: Int): Int = (byte(b,p) shl 24) or (byte(b,p+1) shl 16) or (byte(b,p+2) shl 8) or byte(b,p+3)
    private fun mix(tables: ByteArray, base: Int, words: IntArray, index: Int): Int {
        val t = base + index * 6 * 256
        fun at(page: Int, a: Int, b: Int) = byte(tables,t + page*256 + a*16 + b)
        fun n(w: Int, s: Int) = (words[w] ushr s) and 15
        val hi = 28 - 8*index; val lo = hi - 4
        return (at(4,at(0,n(0,hi),n(1,hi)),at(1,n(2,hi),n(3,hi))) shl 4) or
            at(5,at(2,n(0,lo),n(1,lo)),at(3,n(2,lo),n(3,lo)))
    }
    private fun block(input: ByteArray, tail: ByteArray): ByteArray {
        require(input.size == 16); val state = IntArray(16) { byte(input,shift[it]) }
        for (g in 0..3) for (tab in arrayOf(ty,mb)) {
            val words = IntArray(4) { word(tab, ((4*g+it)*256 + state[4*g+it])*4) }
            for (j in 0..3) state[4*g+j] = mix(xor,g*24*256,words,j)
        }
        return ByteArray(16) { tail[it*256 + state[shift[it]]] }
    }
    fun legacyBlock(input: ByteArray): ByteArray = block(input,last)
    /** Two published legacy table variants; AEAD determines the variant, never key guessing. */
    fun appV1Kdks(salt: ByteArray): List<ByteArray> {
        require(salt.size==16)
        return listOf(npvsLast1,last2).map { tail ->
            MessageDigest.getInstance("SHA-256").digest("npvtunnel/appkey/v1 ".toByteArray()+block(salt,tail))
        }
    }
    fun legacyCtr(data: ByteArray): ByteArray {
        require(data.size >= 16); val counter = data.copyOfRange(0,16); val out = ByteArray(data.size-16)
        for (offset in out.indices step 16) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val key = legacyBlock(counter)
            for (j in 0 until minOf(16,out.size-offset)) out[offset+j] = (data[16+offset+j].toInt() xor key[j].toInt()).toByte()
            for (j in 15 downTo 0) { counter[j] = (byte(counter,j)+1).toByte(); if (counter[j] != 0.toByte()) break }
        }; return out
    }
    fun gen2Kdk(salt: ByteArray, configId: ByteArray): ByteArray {
        require(salt.size == 16 && configId.size == 16)
        var state = IntArray(16) { byte(salt,it) }; val table = gen2
        for (round in 0 until 13) {
            state = IntArray(16) { state[shift[it]] }
            val base = round*57344
            for (g in 0..3) for (half in intArrayOf(0x6000,0xa000)) {
                val w = IntArray(4) { word(table,base+half+g*4096+it*1024+state[4*g+it]*4) }
                for (j in 0..3) state[4*g+j] = mix(table,base+g*24*256,w,j)
            }
        }
        val a16 = ByteArray(16) { table[13*57344+it*256+state[shift[it]]] }
        return MessageDigest.getInstance("SHA-256").digest("npvtunnel/appkey/v2 ".toByteArray() + a16 + configId)
    }
}
