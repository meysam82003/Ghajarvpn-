package net.gozar.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the data-loss bug proven from a real user backup file: its flags byte
 * was 0x02, meaning no password but FLAG_CERT - the AES key was bound to the
 * installing APK's signing certificate. Debug builds are signed with an
 * auto-generated debug keystore (a fresh one per CI runner), and the backup
 * screen exports without a password, so every exported backup was readable
 * only by the exact build that wrote it. Installing the next build turned it
 * into an unrecoverable file, which the import picker then mislabelled as
 * "this is a shared config, not a backup".
 *
 * seal()/open() are internal (not private) so these tests can drive them with
 * a fake certificate string instead of a real Context - this module has no
 * Robolectric, so a real PackageManager signature lookup isn't available in a
 * JVM unit test.
 */
class ConfigFileTest {

    private fun payload(marker: String = "hello") = JSONObject().put("v", 1).put("marker", marker)

    private fun flagsOf(sealed: ByteArray) = sealed[4].toInt()

    @Test fun newBackupsAreNeverBoundToTheSigningCertificate() {
        val withPassword = ConfigFile.seal(payload(), "s3cret")
        val withoutPassword = ConfigFile.seal(payload(), null)
        // 0x02 is FLAG_CERT: it must never be written again.
        assertEquals("password backup must not set FLAG_CERT", 0, flagsOf(withPassword) and 0x02)
        assertEquals("password-less backup must not set FLAG_CERT", 0, flagsOf(withoutPassword) and 0x02)
        assertEquals("password backup must set FLAG_PW", 0x01, flagsOf(withPassword) and 0x01)
        assertEquals("password-less backup must not set FLAG_PW", 0, flagsOf(withoutPassword) and 0x01)
    }

    @Test fun passwordLessBackupRestoresUnderAnySigningCertificate() {
        // The exact case that used to lose data: no password, moved to a build
        // signed with a different key.
        val sealed = ConfigFile.seal(payload(), null)
        val opened = ConfigFile.open(sealed, null) { "some-other-builds-signature" }
        assertEquals("hello", opened.getString("marker"))
    }

    @Test fun passwordLessBackupRestoresWhenNoCertificateIsAvailableAtAll() {
        val sealed = ConfigFile.seal(payload(), null)
        val opened = ConfigFile.open(sealed, null) { null }
        assertEquals("hello", opened.getString("marker"))
    }

    @Test fun passwordBackupRestoresUnderADifferentSigningCertificate() {
        val sealed = ConfigFile.seal(payload(), "s3cret")
        val opened = ConfigFile.open(sealed, "s3cret") { "phone-B-signature" }
        assertEquals("hello", opened.getString("marker"))
    }

    @Test fun passwordBackupStillRejectsTheWrongPassword() {
        val sealed = ConfigFile.seal(payload(), "s3cret")
        assertThrows(ConfigFile.WrongPassword::class.java) {
            ConfigFile.open(sealed, "wrong-password") { null }
        }
    }

    @Test fun aPasswordProtectedFileStillReportsThatItNeedsOne() {
        val sealed = ConfigFile.seal(payload(), "s3cret")
        assertTrue(ConfigFile.isPasswordProtected(sealed))
        assertFalse(ConfigFile.isPasswordProtected(ConfigFile.seal(payload(), null)))
        assertThrows(ConfigFile.NeedsPassword::class.java) {
            ConfigFile.open(sealed, null) { null }
        }
    }

    @Test fun anOldCertificateBoundBackupIsReportedAsSuchInsteadOfCorrupt() {
        // Reproduces the legacy on-disk shape (FLAG_CERT, no password) that the
        // user's real file has, and asserts open() now names the actual cause
        // so the UI can stop calling it "not a backup".
        val legacy = legacySealCertBound(payload("legacy"), cert = "build-A-signature")
        assertEquals(0x02, flagsOf(legacy) and 0x02)

        // Same certificate: still readable, exactly as before.
        val opened = ConfigFile.open(legacy, null) { "build-A-signature" }
        assertEquals("legacy", opened.getString("marker"))

        // Different build's certificate: the honest, specific failure.
        assertThrows(ConfigFile.ForeignBuild::class.java) {
            ConfigFile.open(legacy, null) { "build-B-signature" }
        }
    }

    @Test fun roundTripPreservesArbitraryJsonContent() {
        val root = JSONObject().put("v", 4).put("kind", "backup").put("note", "پشتیبان‌گیری")
        val sealed = ConfigFile.seal(root, "پسورد")
        val opened = ConfigFile.open(sealed, "پسورد") { "irrelevant" }
        assertEquals("backup", opened.getString("kind"))
        assertEquals("پشتیبان‌گیری", opened.getString("note"))
    }

    /**
     * Writes a file the way the old, certificate-binding seal() did, so the
     * backward-compatible read path has something real to read. Mirrors the
     * removed production code: PBKDF2(password + NUL + staticSecret + NUL +
     * cert) with FLAG_CERT set.
     */
    private fun legacySealCertBound(root: JSONObject, cert: String): ByteArray {
        val plain = root.toString().toByteArray(Charsets.UTF_8)
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val secret = String(
            intArrayOf(
                0x2A, 0x3F, 0x02, 0x18, 0x19, 0x08, 0x40, 0x0A,
                0x1F, 0x19, 0x40, 0x1B, 0x5C, 0x40, 0x06, 0x08,
                0x14, 0x40, 0x09, 0x02, 0x00, 0x0C, 0x04, 0x03,
                0x40, 0x1E, 0x08, 0x15, 0x0C, 0x1F, 0x0C, 0x19
            ).map { (it xor 0x6D).toChar() }.toCharArray()
        )
        val material = "" + 0.toChar() + secret + 0.toChar() + cert
        val spec = javax.crypto.spec.PBEKeySpec(material.toCharArray(), salt, 210_000, 256)
        val keyBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        val ct = cipher.doFinal(plain)
        val header = ByteArray(5)
        "GRT1".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        header[4] = 0x02.toByte()
        return header + salt + iv + ct
    }
}
