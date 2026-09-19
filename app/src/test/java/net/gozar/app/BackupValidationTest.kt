package net.gozar.app

import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    @Test fun futureAndInvalidVersionsAreRejectedBeforeRestore() {
        listOf(-1, 0, 6, Int.MAX_VALUE).forEach { version ->
            assertThrows(ConfigFile.BadFile::class.java) { ConfigFile.validateBackupVersion(version) }
        }
        (1..5).forEach(ConfigFile::validateBackupVersion)
    }

    @Test fun changingCiphertextFailsAuthentication() {
        val encrypted = ConfigFile.seal(JSONObject().put("v", 5).put("kind", "backup"), "a test password")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(ConfigFile.WrongPassword::class.java) {
            ConfigFile.open(encrypted, "a test password") { null }
        }
    }

    @Test fun importedBytesAreNotTruncated() {
        val input = ByteArray(100_003) { (it % 251).toByte() }
        assertArrayEquals(input, GhajarBackupRestore.readBounded(ByteArrayInputStream(input)))
    }

    @Test fun oversizedImportFailsRatherThanAllocatingWithoutBound() {
        val stream = object : java.io.InputStream() {
            override fun read(): Int = 1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = length
        }
        assertThrows(IllegalArgumentException::class.java) { GhajarBackupRestore.readBounded(stream) }
    }
}
