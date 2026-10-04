package net.gozar.app.configtoolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64

/**
 * Regression tests for the false "this file has a password" prompt: only a
 * real passphrase requirement may produce [ImportRouter.Outcome.NeedsPasskey].
 */
class ImportRouterTest {

    /** Same synthetic passphrase-sealed NPVS v5 fixture as NpvContainerTest. */
    private val passSealed = Base64.getDecoder().decode("TlBWUwUAAADgATAxMjM0NTY3ODlhYmNkZWYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAPo/XmCpa4gQ9XuCcuR1BjxldQu7SmSw4aJoHBjDMD0zqj5QyOZSonUcLGPADp1ncF3X7KTiqFMk7sMvHodi1tTQ1vaawM0LkILJMPavQAAAFfVds3FK2Nf7OMCz7bBQ7MLfZoVvehjzLXPqXde9aXHVgqOfQ7t3DmnpdqOY2wXhgkzf2mXan3bo5FjyfJTfQidIJLqXuEud3H7QnaOKE7AS3rzHQaQgDePBGAJkFN6qhvbrEkAAALdTlBGASLMWVJju47LEkmY4ZJP3IjDUbG5jc9GnDWjuIKlPkxwAA4ADQAAABZhTtiMB1AmghErEJHNrtj0yikjOLtmAAEAAAA25qTl68w9LKjcRABEaWuOzuVsxpMQ+YMxMabhtPySqj/IUWjPasP0ixNQcT4rHN83F2Q7xW6jAAIAAAATYes8ZhAZiu/0aTNU6faSmHjzeAAGAAAAFY+3EBpHiEI9KERSfF2I7MG9zNlEUAAHAAAAFca+S+ftJ6H7xVBPF21tAviNpkhRUwAIAAAAHfIPQAROGOh+6xNIvEzL0XEh91o8PzLmKwkNxeHyAAwAAAAoDwBD9Yo2Mtl1gH5ePqbXorYho2R0U5oLcC5O7xWNhQnzZFPlKQl9Kv//AAABEtQF1h/TSy0b2b7cDL7/DlntaamvhWkqilieyn3V+XQ9GfqJVkY63jhtg8gqHUKETDmwPol76U+vc+uQYPjm6Hxhs8DttvhoDHXvTviI5KZUwGxe7t0BHHQqowCmPC+Zh2r9J3H7s7Z1LH8okx5zTEMEwGzWGOvwE1UUup4B5hWAb0z64yke/iqXyf+lDjhmU8VW2eM7DUX7M18dXhBo+5cO/rx5QMLwCErsLGI+Qw+/nKB2wGV81qdJ7+aweTRnL2TQa97ZknOg5JVtxP2zoFKq1GkqoU+LiF0AD01Nfn6jE/+eL8DVwODqzMLtYia7UIaoF5rsptOOl+F/UdPgHm5QXaHApQiXODycAT3fVOmDFfoAAwAAABVJn4U2EgG7lEDvXlE3Xb1KNq4MLVkABAAAABh2/Rrgq348OZzdbpa/RpCSQdeGI2wyoYAABQAAABT/SlbLA9QCr0GJzOa5OYBL1GQ7KAAJAAAAGTo47nYuOfyfBocj4eQZLv0pGITgWba8JLgACgAAABPP6lH6cMDBxUhs2coHzpYcvE82AAsAAAAWMCmMEHtADt2uGqs5zjGV3qCsN2ylhQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")

    /** NPVS v5 compact header with unlock method 2 (app key), recipients 0. */
    private fun appKeySealed(): ByteArray {
        val h = ByteArray(135).also { it[0] = 1; it[50] = 2 }
        return "NPVS".toByteArray() + byteArrayOf(5) + ByteBuffer.allocate(4).putInt(h.size).array() + h + ByteArray(200)
    }

    @Test fun appKeyNpvsNeverAsksForAPassword() {
        val outcome = ImportRouter.decode(appKeySealed(), "user.npvs")
        assertTrue("got $outcome", outcome is ImportRouter.Outcome.Invalid)
    }

    @Test fun passphraseNpvsWithoutPassphraseAsksForIt() {
        assertEquals(ImportRouter.Outcome.NeedsPasskey, ImportRouter.decode(passSealed, "x.npvs"))
    }

    @Test fun wrongPassphraseIsWrongPasskey() {
        assertEquals(ImportRouter.Outcome.WrongPasskey, ImportRouter.decode(passSealed, "x.npvs", "nope".toCharArray()))
    }

    @Test fun vendorLockedLegacyFormatsAreNotPasswordPrompts() {
        for ((bytes, name) in listOf(
            "NPVT1abcdefghijklmnop".toByteArray() to "a.npvt",
            "happ://crypt4/AAAAAAAAAAAAAAAA".toByteArray() to "a.happ",
            ByteArray(64) { (it * 37).toByte() } to "a.nm"
        )) {
            val outcome = ImportRouter.decode(bytes, name)
            assertFalse("$name gave a password prompt", outcome is ImportRouter.Outcome.NeedsPasskey)
        }
    }

    @Test fun unrecognisedFileIsNotAPasswordPrompt() {
        val outcome = ImportRouter.decode(ByteArray(40) { 7 }, "mystery.bin")
        assertFalse(outcome is ImportRouter.Outcome.NeedsPasskey)
    }

    @Test fun ghajarOwnContainerIsRecognisedByMagic() {
        assertTrue(ImportRouter.isGhajarConfigFile("GRT1\u0001rest".toByteArray()))
        assertFalse(ImportRouter.isGhajarConfigFile("NPVS\u0005".toByteArray()))
    }
}
