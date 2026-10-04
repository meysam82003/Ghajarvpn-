package net.gozar.app.configtoolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The fixture is a synthetic NPVS v5 file sealed with the passphrase
 * "ghajar-test-pass" (test servers 9.9.9.9, no real credentials). It was built
 * with a Go test against Pantegnos' own decryptNPVSGen2, which opened it to
 * exactly the two links asserted below.
 */
class NpvContainerTest {
    private val fixture = Base64.getDecoder().decode("TlBWUwUAAADgATAxMjM0NTY3ODlhYmNkZWYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAPo/XmCpa4gQ9XuCcuR1BjxldQu7SmSw4aJoHBjDMD0zqj5QyOZSonUcLGPADp1ncF3X7KTiqFMk7sMvHodi1tTQ1vaawM0LkILJMPavQAAAFfVds3FK2Nf7OMCz7bBQ7MLfZoVvehjzLXPqXde9aXHVgqOfQ7t3DmnpdqOY2wXhgkzf2mXan3bo5FjyfJTfQidIJLqXuEud3H7QnaOKE7AS3rzHQaQgDePBGAJkFN6qhvbrEkAAALdTlBGASLMWVJju47LEkmY4ZJP3IjDUbG5jc9GnDWjuIKlPkxwAA4ADQAAABZhTtiMB1AmghErEJHNrtj0yikjOLtmAAEAAAA25qTl68w9LKjcRABEaWuOzuVsxpMQ+YMxMabhtPySqj/IUWjPasP0ixNQcT4rHN83F2Q7xW6jAAIAAAATYes8ZhAZiu/0aTNU6faSmHjzeAAGAAAAFY+3EBpHiEI9KERSfF2I7MG9zNlEUAAHAAAAFca+S+ftJ6H7xVBPF21tAviNpkhRUwAIAAAAHfIPQAROGOh+6xNIvEzL0XEh91o8PzLmKwkNxeHyAAwAAAAoDwBD9Yo2Mtl1gH5ePqbXorYho2R0U5oLcC5O7xWNhQnzZFPlKQl9Kv//AAABEtQF1h/TSy0b2b7cDL7/DlntaamvhWkqilieyn3V+XQ9GfqJVkY63jhtg8gqHUKETDmwPol76U+vc+uQYPjm6Hxhs8DttvhoDHXvTviI5KZUwGxe7t0BHHQqowCmPC+Zh2r9J3H7s7Z1LH8okx5zTEMEwGzWGOvwE1UUup4B5hWAb0z64yke/iqXyf+lDjhmU8VW2eM7DUX7M18dXhBo+5cO/rx5QMLwCErsLGI+Qw+/nKB2wGV81qdJ7+aweTRnL2TQa97ZknOg5JVtxP2zoFKq1GkqoU+LiF0AD01Nfn6jE/+eL8DVwODqzMLtYia7UIaoF5rsptOOl+F/UdPgHm5QXaHApQiXODycAT3fVOmDFfoAAwAAABVJn4U2EgG7lEDvXlE3Xb1KNq4MLVkABAAAABh2/Rrgq348OZzdbpa/RpCSQdeGI2wyoYAABQAAABT/SlbLA9QCr0GJzOa5OYBL1GQ7KAAJAAAAGTo47nYuOfyfBocj4eQZLv0pGITgWba8JLgACgAAABPP6lH6cMDBxUhs2coHzpYcvE82AAsAAAAWMCmMEHtADt2uGqs5zjGV3qCsN2ylhQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")

    @Test fun opensPassphraseSealedGen2LikePantegnos() {
        val r = NpvContainer.open(fixture, "ghajar-test-pass") as NpvContainer.Result.Opened
        assertEquals(listOf(
            "vless://11111111-2222-3333-4444-555555555555@9.9.9.9:443?encryption=none&path=%2Fws&security=tls&sni=ex.com&type=ws#Ghajar%20Test",
            "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpzc3B3@9.9.9.9:8388#Ghajar%20Test"
        ), r.lines)
        assertEquals("Hello from test", r.creatorMessage)
    }

    @Test fun wrongOrMissingPassphrase() {
        assertTrue(NpvContainer.open(fixture, "wrong") is NpvContainer.Result.WrongPassphrase)
        assertTrue(NpvContainer.open(fixture, null) is NpvContainer.Result.NeedsPassphrase)
    }

    @Test fun opensNpvo1OpenExport() {
        val open = "NPVO1\n{\"configs\":[{\"name\":\"Open\",\"address\":\"1.1.1.1\",\"v2rayProfile\":{\"configType\":6," +
            "\"server\":\"1.1.1.1\",\"serverPort\":443,\"password\":\"pw\",\"network\":\"tcp\",\"security\":\"reality\"," +
            "\"sni\":\"a.com\",\"publicKey\":\"PBK\",\"shortId\":\"ab\"}}]}"
        val r = NpvContainer.open(open.toByteArray(), null) as NpvContainer.Result.Opened
        assertEquals(listOf("trojan://pw@1.1.1.1:443?pbk=PBK&security=reality&sid=ab&sni=a.com&type=tcp#Open"), r.lines)
    }

    @Test fun vendorLockedLegacyExportStaysClosed() {
        assertTrue(NpvContainer.open("NPVT1abcdefgh".toByteArray(), null) is NpvContainer.Result.Protected)
        assertTrue(NpvContainer.open("NPVTSUB1abcdefgh".toByteArray(), null) is NpvContainer.Result.Protected)
    }

    @Test fun chachaMatchesJdk() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { (it * 3).toByte() }
        val pt = ByteArray(200) { (it * 7).toByte() }
        val c = Cipher.getInstance("ChaCha20-Poly1305")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        c.updateAAD("aad".toByteArray())
        val ct = c.doFinal(pt)
        assertTrue(ChaCha20Poly1305.open(key, nonce, ct, "aad".toByteArray())!!.contentEquals(pt))
        assertTrue(ChaCha20Poly1305.seal(key, nonce, pt, "aad".toByteArray()).contentEquals(ct))
        ct[0] = (ct[0].toInt() xor 1).toByte()
        assertEquals(null, ChaCha20Poly1305.open(key, nonce, ct, "aad".toByteArray()))
    }
}
