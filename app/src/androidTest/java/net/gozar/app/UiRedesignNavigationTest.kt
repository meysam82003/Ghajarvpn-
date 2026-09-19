package net.gozar.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures real empty/unauthenticated states; never injects display data. */
@RunWith(AndroidJUnit4::class)
class UiRedesignNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun nav(index: Int) {
        ui.onNodeWithTag("root-nav-$index").performClick()
        ui.waitForIdle()
    }
    private fun open(label: String) {
        ui.onAllNodesWithText(label, useUnmergedTree = true).onLast()
            .performScrollTo().performTouchInput { click() }
        ui.waitForIdle()
    }
    private fun shot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-redesign").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun realDestinationsAndBackNavigation() {
        val store = ConfigStore.get(ui.activity)
        runBlocking { store.awaitReady() }
        ui.runOnUiThread { store.setLang(Lang.FA) }
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("root-nav-0").fetchSemanticsNodes().isNotEmpty() }
        nav(0)
        shot("01-home")
        open("سرورها")
        shot("03-servers")
        nav(0)
        open("رایگان")
        shot("02-free-configs")
        ui.onAllNodesWithText("به‌روزرسانی").onLast().performTouchInput { click() }
        nav(0) // A refresh must allow immediate navigation away.
        ui.onNodeWithTag("root-nav-0").assertIsDisplayed()
        nav(1)
        shot("05-store")
        nav(2)
        shot("06-settings")
        open("بکاپ‌گیری و بازگردانی")
        shot("04-backup")
        androidx.test.espresso.Espresso.pressBack()
        ui.onNodeWithTag("root-nav-2").assertIsDisplayed()
        nav(2)
        open("مصرف داده")
        shot("07-data-usage")
        nav(2)
        open("اشتراک‌گذاری VPN")
        shot("08-vpn-sharing")
        nav(2)
        open("OpenVPN")
        shot("10-openvpn")
        nav(2)
        open("اعلان‌ها")
        shot("11-notifications")
        nav(2)
        open("تنظیمات ظاهری")
        shot("12-appearance")
        nav(2)
        open("SSH")
        shot("13-ssh")
        nav(2)
        open("دیباگر")
        shot("14-debugger")
    }
}
