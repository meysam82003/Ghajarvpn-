package net.gozar.app.configtoolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigToolkitTest {
    @Test fun detectorUsesMagicBeforeExtension() {
        val detection = FormatDetector.detect(ConfigInput("NPVT1\n{}".toByteArray(), "wrong.txt"))
        assertEquals(ConfigFormat.NPVT, detection.format)
        assertTrue(detection.confidence >= 90)
    }

    @Test fun detectorRecognizesHappCryptWithoutTryingToDecryptIt() {
        val detection = FormatDetector.detect(ConfigInput("happ://crypt/not-readable".toByteArray(), "config.bin"))
        assertEquals(ConfigFormat.HAPP, detection.format)
        assertEquals(95, detection.confidence)
    }

    @Test fun validatorAcceptsBracketlessIpv6AndRejectsBadPort() {
        val good = sample(server = "2606:4700:20::ac43:4728", port = 443)
        assertTrue(ProfileValidator.validate(good).valid)
        assertFalse(ProfileValidator.validate(good.copy(port = 70000)).valid)
    }

    @Test fun validatorRequiresRealityKeyAndSni() {
        val result = ProfileValidator.validate(sample(security = "reality", publicKey = "", sni = ""))
        assertFalse(result.valid)
        assertTrue(result.issues.any { it.field == "publicKey" })
        assertTrue(result.issues.any { it.field == "sni" })
    }

    @Test fun unsupportedProtocolIsNotImported() {
        assertFalse(ProfileValidator.validate(sample(protocol = "unknown")).valid)
    }

    private fun sample(
        protocol: String = "vless",
        server: String = "example.com",
        port: Int = 443,
        security: String = "tls",
        publicKey: String = "",
        sni: String = "example.com"
    ) = NormalizedProfile(
        name = "test", protocol = protocol, server = server, port = port,
        uuid = "123e4567-e89b-12d3-a456-426614174000", security = security,
        publicKey = publicKey, sni = sni, sourceFormat = ConfigFormat.TEXT
    )
}
