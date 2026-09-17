package net.gozar.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the bug: ConfigFile.seal() used to bind the AES key to the
 * installing APK's signing-certificate hash on every backup, password or
 * not. A password-protected .grt exported on one phone/build could then
 * never be restored on a different one (different signing key = different
 * derived key = GCM auth failure), even with the correct password - exactly
 * the "backup must be restorable on another phone" requirement this broke.
 *
 * ConfigFile.seal/open are internal (not private) specifically so this test
 * can drive them with a fake certificate string instead of a real Context -
 * this module has no Robolectric/Mockito, so a real PackageManager signature
 * lookup isn't available in a JVM unit test.
 */
class ConfigFileTest {

    private fun payload(marker: String = "hello") = JSONObject().put("v", 1).put("marker", marker)

    @Test fun passwordBackupRestoresUnderADifferentSigningCertificate() {
        val sealed = ConfigFile.seal(cert = "phone-A-signature", root = payload(), password = "s3cret")
        val opened = ConfigFile.open(sealed, "s3cret") { "phone-B-signature" }
        assertEquals("hello", opened.getString("marker"))
    }

    @Test fun passwordBackupRestoresEvenWithNoCertificateAvailableOnTheOtherPhone() {
        val sealed = ConfigFile.seal(cert = "phone-A-signature", root = payload(), password = "s3cret")
        val opened = ConfigFile.open(sealed, "s3cret") { null }
        assertEquals("hello", opened.getString("marker"))
    }

    @Test fun passwordBackupStillRejectsTheWrongPassword() {
        val sealed = ConfigFile.seal(cert = "phone-A-signature", root = payload(), password = "s3cret")
        assertThrows(ConfigFile.WrongPassword::class.java) {
            ConfigFile.open(sealed, "wrong-password") { "phone-A-signature" }
        }
    }

    @Test fun passwordLessBackupStillRequiresTheOriginalSigningCertificate() {
        // Unchanged, intentional behavior: a password-less backup's only
        // protection against being opened elsewhere is the cert binding, so
        // this must still be enforced exactly like before the fix.
        val sealed = ConfigFile.seal(cert = "phone-A-signature", root = payload(), password = null)
        assertThrows(ConfigFile.BadFile::class.java) {
            ConfigFile.open(sealed, null) { "phone-B-signature" }
        }
        val opened = ConfigFile.open(sealed, null) { "phone-A-signature" }
        assertEquals("hello", opened.getString("marker"))
    }

    @Test fun roundTripPreservesArbitraryJsonContent() {
        val root = JSONObject().put("v", 4).put("kind", "backup").put("note", "پشتیبان‌گیری")
        val sealed = ConfigFile.seal(cert = "any-cert", root = root, password = "پسورد")
        val opened = ConfigFile.open(sealed, "پسورد") { "a-different-cert-entirely" }
        assertEquals("backup", opened.getString("kind"))
        assertEquals("پشتیبان‌گیری", opened.getString("note"))
    }
}
