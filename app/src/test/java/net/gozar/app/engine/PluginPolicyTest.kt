package net.gozar.app.engine

import net.gozar.app.plugins.PluginPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPolicyTest {
    private val cert = "cert".toByteArray()
    private val apk = "apk-bytes".toByteArray()
    private val release = PluginPolicy.Release(
        id = "x", versionCode = 2, apiVersion = PluginPolicy.API_VERSION, minHostVersionCode = 30026,
        abis = setOf("arm64-v8a"), size = apk.size.toLong(), sha256 = PluginPolicy.sha256(apk),
        publisher = "ghajar", certificateSha256 = PluginPolicy.sha256(cert), packageName = "net.gozar.plugin.x"
    )
    private val trusted = mapOf("ghajar" to PluginPolicy.sha256(cert))

    @Test fun nothingIsTrustedIn111() {
        assertTrue(PluginPolicy.TRUSTED_PUBLISHERS.isEmpty())
        assertTrue(PluginPolicy.check(release, 30026, setOf("arm64-v8a")) is PluginPolicy.Verdict.Refused)
    }

    @Test fun pinnedPublisherPassesAndEveryMismatchIsRefused() {
        assertEquals(PluginPolicy.Verdict.Ok, PluginPolicy.check(release, 30026, setOf("arm64-v8a"), trusted))
        assertTrue(PluginPolicy.check(release.copy(certificateSha256 = "0".repeat(64)), 30026, setOf("arm64-v8a"), trusted) is PluginPolicy.Verdict.Refused)
        assertTrue(PluginPolicy.check(release, 30025, setOf("arm64-v8a"), trusted) is PluginPolicy.Verdict.Refused)
        assertTrue(PluginPolicy.check(release, 30026, setOf("armeabi-v7a"), trusted) is PluginPolicy.Verdict.Refused)
        assertTrue(PluginPolicy.check(release.copy(apiVersion = 2), 30026, setOf("arm64-v8a"), trusted) is PluginPolicy.Verdict.Refused)
    }

    @Test fun fileAndSignerMustMatch() {
        assertTrue(PluginPolicy.fileMatches(release, apk))
        assertFalse(PluginPolicy.fileMatches(release, "other".toByteArray()))
        assertTrue(PluginPolicy.signerMatches(release, listOf(cert)))
        assertFalse(PluginPolicy.signerMatches(release, listOf(cert, cert)))
        assertFalse(PluginPolicy.signerMatches(release, listOf("evil".toByteArray())))
    }

    @Test fun updatesGoUpAndRollbackOnlyToAPreviouslyActiveRelease() {
        val v1 = release.copy(versionCode = 1)
        assertTrue(PluginPolicy.mayActivate(v1, release, emptySet()))
        assertFalse(PluginPolicy.mayActivate(release, v1, emptySet()))
        assertTrue(PluginPolicy.mayActivate(release, v1, setOf(1L)))
        assertFalse(PluginPolicy.mayActivate(release, release.copy(packageName = "evil"), emptySet()))
    }
}
