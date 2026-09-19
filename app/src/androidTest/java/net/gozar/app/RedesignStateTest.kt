package net.gozar.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android storage/crypto integration. Fixtures never ship in production. */
@RunWith(AndroidJUnit4::class)
class RedesignStateTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun manualSelectionDisablesAutomationAndPersistsFilters() = runBlocking {
        val store = ConfigStore.get(context)
        store.awaitReady()
        val original = store.settingsSnapshot()
        val query = store.pickerQuery.value
        val favorites = store.pickerFavorites.value
        val protocol = store.pickerProtocol.value
        val selected = store.selectedId.value
        try {
            store.setAutoSelect(true)
            store.setAutoPilot(true)
            store.selectExplicitly("instrumented-explicit-selection")
            store.setPickerQuery("پروتکل test")
            store.setPickerFavorites(true)
            store.setPickerProtocol("socks")
            assertEquals("instrumented-explicit-selection", store.selectedId.value)
            assertFalse(store.autoSelect.value)
            assertFalse(store.autoPilot.value)
            val prefs = context.getSharedPreferences("gozarnet", Context.MODE_PRIVATE)
            assertEquals("پروتکل test", prefs.getString("picker_query", null))
            assertTrue(prefs.getBoolean("picker_favorites", false))
            assertEquals("socks", prefs.getString("picker_protocol", null))
            assertEquals("instrumented-explicit-selection", store.settingsSnapshot().getString("selectedId"))
        } finally {
            store.restoreSettings(original)
            store.setSelectedId(selected)
            store.setPickerQuery(query)
            store.setPickerFavorites(favorites)
            store.setPickerProtocol(protocol)
        }
    }

    @Test fun authenticatedBackupRoundTripsAndRejectsWrongPassword() = runBlocking {
        val store = ConfigStore.get(context)
        store.awaitReady()
        val config = ProxyConfig(name = "instrumented fixture", protocol = "socks", address = "127.0.0.1", port = 1080, password = "test-secret")
        val data = ConfigFile.encodeBackup(context, listOf(config), emptyList(), store.settingsSnapshot(), "instrumented-password")
        assertFalse(data.toString(Charsets.UTF_8).contains("test-secret"))
        assertTrue(ConfigFile.isPasswordProtected(data))
        val decoded = ConfigFile.decodeBackup(context, data, "instrumented-password")
        assertEquals(config, decoded.configs.single())
        assertTrue(runCatching { ConfigFile.decodeBackup(context, data, "wrong-password") }.isFailure)
    }
}
