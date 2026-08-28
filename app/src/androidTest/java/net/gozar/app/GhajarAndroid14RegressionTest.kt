package net.gozar.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class GhajarAndroid14RegressionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun connectDisconnectReconnectAndRecreateOnAndroid14() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        // Grant VPN consent only on the disposable CI emulator, never a personal device.
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("cmd appops set ${context.packageName} ACTIVATE_VPN allow").close()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE).edit().putBoolean("soft_intro_seen", false).commit()
        val store = ConfigStore.get(context)
        runBlocking { store.awaitReady() }
        store.setAutoSelect(false); store.setKillSwitch(false); store.setFragment(false)
        store.setSplitRouting(false); store.setOnionRouting(false); store.setAdBlock(false)
        store.setEncryptedDns(false); store.setThemeMode(ThemeMode.DARK); store.setLang(Lang.FA)
        store.setGlobeStyle("filled")
        LocalSocksFixture().use { fixture ->
            val config = ProxyConfig("آزمون محلی اتصال", "socks", "127.0.0.1", fixture.port)
            store.add(config); store.setSelectedId(config.id)
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                compose.waitUntil(20000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("ghajar_welcome_poster")).fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                screenshot(context, "welcome-native-fa")
                compose.onNodeWithText("ورود به قاجار VPN").performClick()
                compose.waitUntil(20000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("ghajar_welcome_poster")).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodes(androidx.compose.ui.test.hasTestTag("ghajar_connect")).fetchSemanticsNodes().isNotEmpty() }
                repeat(2) { cycle ->
                    compose.onNodeWithTag("ghajar_connect").performClick()
                    compose.waitUntil(25000) { VpnState.state.value in listOf(Connection.CONNECTED, Connection.ERROR) }
                    assertEquals("VPN startup failed: ${VpnState.error.value}", Connection.CONNECTED, VpnState.state.value)
                    assertEquals(Lifecycle.State.RESUMED, scenario.state)
                    // Exercise the real bundled engine's SOCKS path, without a live service/account.
                    val response = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MixedPort.value))).use { socket ->
                        socket.soTimeout = 6000
                        socket.connect(InetSocketAddress.createUnresolved("qa.ghajar.invalid", 80), 6000)
                        socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: qa.ghajar.invalid\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.getInputStream().bufferedReader().readText()
                    }
                    assertTrue(response.contains("ghajar-ci-ping"))
                    compose.waitForIdle()
                    compose.mainClock.advanceTimeBy(1200)
                    compose.waitForIdle()
                    compose.onNodeWithTag("ghajar_connect").assertTextEquals(Strings.get(Lang.FA, "disconnect")).assertIsDisplayed()
                    screenshot(context, "connected-$cycle")
                    compose.onNodeWithTag("ghajar_connect").performClick()
                    compose.waitUntil(15000) { VpnState.state.value == Connection.DISCONNECTED }
                    assertEquals(Lifecycle.State.RESUMED, scenario.state)
                    scenario.onActivity { store.setGlobeStyle("royal") }
                }
                scenario.recreate()
                compose.waitUntil(20000) { scenario.state == Lifecycle.State.RESUMED &&
                    compose.onAllNodes(androidx.compose.ui.test.hasTestTag("ghajar_welcome_poster")).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodes(androidx.compose.ui.test.hasTestTag("ghajar_connect")).fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(1200)
                compose.waitForIdle()
                compose.onNodeWithTag("ghajar_connect").assertTextEquals(Strings.get(Lang.FA, "connect")).assertIsDisplayed()
                screenshot(context, "royal-recreated")
            } finally {
                context.startService(Intent(context, GozarVpnService::class.java).setAction(GozarVpnService.ACTION_STOP))
                scenario.close(); store.delete(config.id)
            }
        }
    }
}

internal fun screenshot(context: Context, name: String) {
    // Gradle uninstalls the test app, deleting its app-specific external files.
    // Keep non-sensitive fixture screenshots in the disposable emulator's Download directory.
    require(name.matches(Regex("[A-Za-z0-9_-]+")))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    val automation = instrumentation.uiAutomation
    fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
    shell("mkdir -p /sdcard/Download/ghajar-ci")
    shell("screencap -p /sdcard/Download/ghajar-ci/$name.png")
}

/** Local SOCKS5 fixture. Responds only with test content; never forwards any traffic. */
private class LocalSocksFixture : AutoCloseable {
    private val server = ServerSocket(0, 30, java.net.InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool()
    val port = server.localPort
    init {
        workers.submit {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                workers.submit { runCatching { answer(socket) }; runCatching { socket.close() } }
            }
        }
    }
    private fun answer(socket: Socket) {
        socket.use {
            socket.soTimeout = 4000
            val input = java.io.DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            if (input.readUnsignedByte() != 5) return
            repeat(input.readUnsignedByte()) { input.readByte() }
            output.write(byteArrayOf(5, 0)); output.flush()
            if (input.readUnsignedByte() != 5 || input.readUnsignedByte() != 1) return
            input.readByte()
            val count = when (input.readUnsignedByte()) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> return }
            input.readFully(ByteArray(count)); val port = input.readUnsignedShort()
            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80)); output.flush()
            if (port != 80) return
            val request = StringBuilder()
            while (request.length < 4096 && !request.endsWith("\r\n\r\n")) {
                val byte = input.read(); if (byte < 0) return; request.append(byte.toChar())
            }
            val body = "ghajar-ci-ping"
            output.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
            output.flush()
        }
    }
    override fun close() { server.close(); workers.shutdownNow() }
}
