package net.gozar.app

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GhajarNativeUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exactCardAmountCopyAndReceiptControlsWorkWithLargePersianText() {
        var receipt by mutableStateOf<Uri?>(null)
        var sent by mutableStateOf(false)
        val photo = File(context.cacheDir, "test-receipt.png")
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val invoice = GhajarPaymentInit("carttocart", "ci-fixture", null, "0000111122223333", "حساب آزمایشی", 123456, 1234560, "")
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalLang provides Lang.FA, LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides Density(density.density, 1.3f)) {
                    Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                        CardToCardCard(invoice, receipt, false, sent, onPickReceipt = { receipt = Uri.fromFile(photo) }, onUpload = { sent = true })
                    } }
                }
            }
        }
        compose.onNodeWithContentDescription("کپی شماره کارت").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(invoice.cardNumber, (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString()) }
        screenshot(context, "payment-card-large-fa")
        compose.onNodeWithTag("ghajar_upload_receipt").assertIsNotEnabled()
        compose.onNodeWithTag("ghajar_pick_receipt").performScrollTo().performClick()
        compose.onNodeWithTag("ghajar_upload_receipt").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("رسید ارسال شد؛ منتظر تأیید").assertExists()
        compose.onNodeWithTag("ghajar_upload_receipt").assertIsNotEnabled()
        compose.onNodeWithTag("ghajar_pick_receipt").assertIsNotEnabled()
        screenshot(context, "receipt-pending-fa")
    }

    @Test fun deliveredQrContainsTheActualSelectedSubscription() {
        val url = "https://example.invalid/sub/ci-public-fixture"
        val service = GhajarServiceDetails("qa", "سرویس آزمایشی", "active", 0.0, 5.0, 5.0, "", url, emptyList())
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalLang provides Lang.FA, LocalLayoutDirection provides LayoutDirection.Rtl) {
                GhajarDeliveryDialog(GhajarDelivery(service, 2, true), {}, {}, false)
            }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("QR اتصال همین سرویس").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("سرویس به قاجار VPN اضافه شد").assertExists()
        val bitmap = compose.onNodeWithContentDescription("QR اتصال همین سرویس").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(url, decoded.text)
        screenshot(context, "delivered-qr-fa")
    }

    @Test fun invoiceUsesServerFinalAmountAndNeverTreatsNullAsACard() {
        val api = GhajarStoreApi(context)
        val payload = JSONObject("""{"kind":"url","order_id":"fixture","amount":123468,"card_number":null,"name_card":null}""")
        val invoice = api.paymentFrom(payload, 123450)
        assertEquals(123468L, invoice.amount)
        assertEquals(1234680L, invoice.amountRial)
        assertNull(invoice.cardNumber)
        assertNull(invoice.cardHolder)
        assertFalse(GhajarCommerceRules.cardPayment(invoice.kind, invoice.cardNumber))
        assertTrue(runCatching { api.paymentFrom(payload.put("amount", "invalid"), 123450) }.isFailure)
    }

    @Test fun presetsAndExtremeCustomColorsRemainReadableInBothModes() {
        val palettes = listOf(darkColorScheme(background = Color(0xFF071B2E)), lightColorScheme(background = Color(0xFFEEF3FA)), darkColorScheme(background = Color.Black))
        for (base in palettes) for (hex in listOf("#C79E48", "#398BCB", "#D95864", "#FFFFFF", "#000000", "#F2CA4C", "#359F78")) {
            val colors = ghajarColorScheme(base, hex)
            assertTrue("$hex background", ghajarContrast(colors.primary, colors.background) >= 4.5f)
            assertTrue("$hex button", ghajarContrast(colors.primary, colors.onPrimary) >= 4.5f)
            assertTrue("$hex container", ghajarContrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5f)
        }
    }

    @Test fun customColorCanBeAppliedAndResetFromNativeControls() {
        val settings = GhajarAppearance.get(context)
        settings.setAccent(null)
        try {
            compose.setContent {
                val accent by settings.accent.collectAsState()
                MaterialTheme(colorScheme = ghajarColorScheme(darkColorScheme(), accent)) {
                    CompositionLocalProvider(LocalLang provides Lang.FA, LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                            GhajarAppearanceSettings()
                        } }
                    }
                }
            }
            compose.onNodeWithTag("ghajar_accent_hex").performScrollTo().performTextReplacement("#359f78")
            compose.onNodeWithTag("ghajar_accent_apply").performScrollTo().performClick()
            compose.runOnIdle { assertEquals("#359F78", settings.accent.value) }
            screenshot(context, "custom-accent-fa")
            compose.onNodeWithText("رنگ اصلی").performScrollTo().performClick()
            compose.runOnIdle { assertNull(settings.accent.value) }
        } finally { settings.setAccent(null) }
    }

    @Test fun firstWelcomeShowsOnlyOnePosterWithoutASlider() {
        context.getSharedPreferences("ghajar_welcome", Context.MODE_PRIVATE).edit().putBoolean("soft_intro_seen", false).commit()
        var closed = false
        compose.setContent { MaterialTheme { GhajarWelcomeScreen { closed = true } } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("ghajar_welcome_poster").fetchSemanticsNodes().size == 1 }
        compose.onAllNodesWithTag("ghajar_welcome_poster").assertCountEquals(1)
        compose.onNodeWithText("بعدی").assertDoesNotExist()
        screenshot(context, "single-welcome-fa")
        compose.onNodeWithText("ورود به قاجار VPN").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun storyReactionAndGiftUseTheNativeRouteWithoutSilentRedemption() {
        val story = GhajarStoryRules.parse(JSONObject("""{"items":[{"id":"qa-viewer","label":"داستان قاجار","sublabel":"هدیهٔ آزمایشی",
            "slides":[{"title":"خوش آمدی","body":"اعتبار هدیه فقط پس از تأیید شما ثبت می‌شود.","bg":"#082F2B",
            "code":"QA-GIFT","code_kind":"gift","cta_label":"کیف پول","cta_link":"#/wallet","cta_color":"#C79E48"}]}]}""")).single()
        var closed by mutableStateOf(false)
        var reaction: String? = null
        GhajarStoryNavigation.pending.value?.let(GhajarStoryNavigation::consumed)
        try {
            compose.setContent { MaterialTheme {
                CompositionLocalProvider(LocalLang provides Lang.FA, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (!closed) GhajarStoryViewer(story, { closed = true }, { closed = true }) { value, done -> reaction = value; done(true) }
                }
            } }
            compose.onNodeWithContentDescription("توقف استوری").performClick()
            compose.onNodeWithText("❤️").performClick()
            compose.runOnIdle { assertEquals("heart", reaction) }
            compose.onNodeWithText("واکنش ثبت شد").assertExists()
            screenshot(context, "story-gift-fa")
            compose.onNodeWithText("استفاده از هدیه").performScrollTo().performClick()
            compose.runOnIdle {
                assertTrue(closed)
                assertEquals(GhajarStoryRoute(3, giftCode = "QA-GIFT"), GhajarStoryNavigation.pending.value)
            }
        } finally { GhajarStoryNavigation.pending.value?.let(GhajarStoryNavigation::consumed) }
    }

    @Test fun storyContractHandlesMediaTransformsGiftAndMultipleAttachments() {
        val story = GhajarStoryRules.parse(JSONObject("""{"items":[{"id":"qa","label":"داستان قاجار","slides":[
            {"title":"هدیه","body":"<b>برای شما</b>","media":"assets/story-media/qa.mp4?v=7","media_type":"video",
             "media_x":10,"media_y":-12,"media_scale":2,"media_rotate":45,"code":"QA-GIFT","code_kind":"gift",
             "attaches":[{"url":"assets/story-media/qa.pdf","name":"راهنما","size":1024}]}]}]}""")).single()
        assertTrue(story.slides.single().video)
        assertEquals(10f, story.slides.single().x, .001f)
        assertEquals(2f, story.slides.single().scale, .001f)
        assertEquals("gift", story.slides.single().codeKind)
        assertEquals("برای شما", story.slides.single().body)
        assertEquals(1, story.slides.single().attachments.size)
    }

    @Test fun geoProviderMismatchNeverCreatesAFakeCountry() {
        val body = """{"success":true,"ip":"8.8.8.8","country":"United States","country_code":"US","latitude":37.4,"longitude":-122.1}"""
        assertNotNull(LocationFetcher.parse(body, false, "8.8.8.8"))
        assertNull(LocationFetcher.parse(body, false, "1.1.1.1"))
    }

    @Test fun globeCentersOnExitCoordinatesAndNeverKeepsThePreviousFlag() {
        val store = ConfigStore.get(context)
        runBlocking { store.awaitReady() }
        store.setKillSwitch(false)
        VpnState.setConnecting("ui-geo-fixture"); VpnState.setConnected()
        val session = GhajarLocationSession(Connection.CONNECTED, VpnState.activeId.value, VpnState.connectedAt.value, false, false)
        val first = IpLocation("8.8.8.8", "Fixture West", "United States", "US", 37.4, -122.1)
        val next = IpLocation("1.1.1.1", "Fixture East", "Germany", "DE", 52.52, 13.40)
        try {
            GhajarLocationMonitor.publish(GhajarLocationSnapshot(session, first.ip, first))
            compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalLang provides Lang.FA, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface { EarthSection(Modifier.fillMaxWidth().height(440.dp)) }
                }
            } }
            compose.onNodeWithText("United States", substring = true).assertExists()
            compose.runOnIdle { GhajarLocationMonitor.publish(GhajarLocationSnapshot(session, next.ip, next)) }
            compose.onNodeWithText("🇩🇪", substring = true).assertExists()
            compose.onNodeWithText("United States", substring = true).assertDoesNotExist()
            for (location in listOf(first, next)) {
                val spin = nearestAngle(-Math.toRadians(location.lon).toFloat(), 0f)
                val point = project(location.lat, location.lon, spin, Math.toRadians(location.lat).toFloat(), 200f, 200f, 150f)
                assertEquals(200f, point[0], .01f)
                assertEquals(200f, point[1], .01f)
                assertTrue(point[2] > .99f)
            }
            screenshot(context, "globe-country-fixture-fa")
            compose.runOnIdle { VpnState.setConnecting("ui-geo-next") }
            compose.onNodeWithText("Germany", substring = true).assertDoesNotExist()
        } finally {
            VpnState.setDisconnected()
            GhajarLocationMonitor.publish(GhajarLocationSnapshot())
        }
    }
}
