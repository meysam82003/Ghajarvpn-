package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exported diagnostics file is meant to be handed to support, so anything
 * in it that could be replayed against the account or a server has to be gone
 * before it leaves the device.
 *
 * These are the shapes this app actually carries. The export also includes
 * crash traces and the piped ics-openvpn engine log, which are arbitrary text
 * the app does not author - so redaction cannot rely on the app being careful
 * about what it logs, which it otherwise is.
 */
class GhajarLogRedactionTest {

    private fun redacted(input: String): String = GhajarLog.redact(input)

    private fun assertGone(secret: String, line: String) {
        val out = redacted(line)
        assertFalse("secret survived redaction in: $out", out.contains(secret))
    }

    @Test
    fun `bearer token is removed`() {
        assertGone("a1b2c3d4e5f6a7b8", "Authorization: Bearer a1b2c3d4e5f6a7b8")
    }

    @Test
    fun `keyed secrets are removed`() {
        assertGone("deadbeefcafe", "\"token\":\"deadbeefcafe\"")
        assertGone("deadbeefcafe", "access_token=deadbeefcafe")
        assertGone("deadbeefcafe", "api_key: deadbeefcafe")
        assertGone("deadbeefcafe", "session_token=deadbeefcafe")
        assertGone("hunter2hunter2", "password=hunter2hunter2")
        assertGone("hunter2hunter2", "pass=hunter2hunter2")
        assertGone("Zm9vYmFyYmF6", "psk: Zm9vYmFyYmF6")
        assertGone("xK3sh0rt1d", "short_id=xK3sh0rt1d")
    }

    /** The one-time web panel ticket, added with the browser hand-off. */
    @Test
    fun `web panel ticket is removed`() {
        val ticket = "0".repeat(64)
        assertGone(ticket, "opening panel with ticket=$ticket")
    }

    /**
     * A share link's credential lives in its userinfo, so the whole prefix is
     * the secret - but the host and port are the entire point of a diagnostic,
     * so they have to survive.
     */
    @Test
    fun `share link credential goes and the endpoint stays`() {
        val line = "failed vless://3f8a1c92-1111-2222-3333-444455556666@example.com:443?type=tcp"
        val out = redacted(line)
        assertFalse("uuid survived: $out", out.contains("3f8a1c92-1111-2222-3333-444455556666"))
        assertTrue("host was lost: $out", out.contains("example.com:443"))
    }

    @Test
    fun `trojan and shadowsocks links are covered`() {
        assertGone("s3cretpassword", "trojan://s3cretpassword@host.example:8443")
        assertGone("YWVzOnBhc3N3b3Jk", "ss://YWVzOnBhc3N3b3Jk@host.example:8388")
    }

    @Test
    fun `base64 vmess blob is removed`() {
        val blob = "eyJ2IjoiMiIsInBzIjoidGVzdCIsImFkZCI6ImV4YW1wbGUuY29tIn0="
        assertGone(blob, "importing vmess://$blob")
    }

    @Test
    fun `bare uuid is removed`() {
        assertGone(
            "3f8a1c92-aaaa-bbbb-cccc-ddddeeeeffff",
            "IllegalArgumentException: Invalid UUID: 3f8a1c92-aaaa-bbbb-cccc-ddddeeeeffff"
        )
    }

    /**
     * Every credential this app generates itself is a long hex run: the VPN
     * Share password, the link session token, the web ticket, a pinned
     * certificate fingerprint.
     */
    @Test
    fun `long hex runs are removed`() {
        val sharePass = "9f3c1ab27de45610"          // 16 hex - below the rule
        val sessionToken = "a".repeat(40)           // 40 hex - above it
        assertGone(sessionToken, "session established $sessionToken")
        // Documented boundary: a 16-char hex run is left alone unless it is
        // introduced by a key, which is how the share password is ever logged.
        assertGone(sharePass, "sharePass=$sharePass")
    }

    @Test
    fun `phone and card numbers are removed`() {
        assertGone("09123456789", "user phone 09123456789 verified")
        assertGone("1234567812345678", "card 1234567812345678 entered")
    }

    /** Redaction must not eat the parts a diagnostic exists to show. */
    @Test
    fun `ordinary diagnostic text survives`() {
        val line = "direct request to httpuser87890.ir failed (SocketTimeoutException: timeout)"
        assertEquals(line, redacted(line))
    }

    @Test
    fun `host only log lines survive`() {
        val line = "classify: rejected scheme/userinfo host=example.com"
        assertEquals(line, redacted(line))
    }

    @Test
    fun `empty input is safe`() {
        assertEquals("", redacted(""))
    }
}
