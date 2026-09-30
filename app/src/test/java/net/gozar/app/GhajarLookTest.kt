package net.gozar.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhajarLookTest {

    private val custom = GhajarLook.Default.copy(
        preset = "amoled", accent = 0xFF3D7BFFL, amoled = true,
        colors = mapOf("background" to 0xFF000000L, "tile_down_icon" to 0xFF22D3EEL),
        orbStyle = "shield", navIndicator = "outline", navSpacing = 6,
        homeOrder = listOf("traffic", "orb", "route", "session", "quota", "facts"), homeHidden = setOf("facts"),
        homeSizes = mapOf("orb" to "large"),
        settingsOrder = listOf("backup", "cores"), settingsHidden = setOf("ssh"), tileSize = "large", gridGap = 14,
        storeButtonStyle = "tonal", storeTabStyle = "underline", storeShow = setOf("logo", "title", "prices"),
        serverView = "grid", serverFields = setOf("name", "flag", "core", "traffic"),
        addServerStyle = "tabs", customPresets = mapOf("mine" to "{\"preset\":\"ocean\"}")
    )

    @Test
    fun versionedExportRoundTrips() {
        val export = custom.toExport("1.0.10")
        assertEquals(GhajarLook.SCHEMA_VERSION, export.getInt("schemaVersion"))
        assertEquals("1.0.10", export.getString("appVersion"))
        listOf("theme", "colors", "typography", "animations", "homeLayout", "storeLayout", "settingsLayout",
            "serverSelectorLayout", "addServerLayout", "navigationStyle", "connectionButtonStyle", "componentPreferences")
            .forEach { assertTrue(it, export.has(it)) }
        assertEquals(custom, GhajarLook.fromAny(JSONObject(export.toString())))
    }

    @Test
    fun flatV1ExportStillImports() {
        val back = GhajarLook.fromAny(JSONObject(custom.toJson().toString()))
        assertEquals(custom, back)
    }

    @Test
    fun unknownFieldsAndInvalidValuesFallBackWithoutThrowing() {
        val o = JSONObject(custom.toExport("1.0.10").toString())
            .put("someFutureField", JSONObject().put("x", 1))
            .put("connectionButtonStyle", "not-a-style")
        o.getJSONObject("navigationStyle").put("spacing", 999)
        o.getJSONObject("serverSelectorLayout").put("view", 42)
        o.getJSONObject("colors").put("not_an_element", 1L)
        val look = GhajarLook.fromAny(o)
        assertEquals(GhajarLook.Default.orbStyle, look.orbStyle)
        assertEquals(16, look.navSpacing)
        assertEquals(GhajarLook.Default.serverView, look.serverView)
        assertFalse(look.colors.containsKey("not_an_element"))
        assertEquals(GhajarLook.Default, GhajarLook.fromAny(JSONObject()))
    }

    @Test
    fun criticalHomeSectionsCannotBeHidden() {
        val o = JSONObject().put("homeHidden", org.json.JSONArray(listOf("orb", "route", "facts")))
        assertEquals(setOf("facts"), GhajarLook.fromAny(o).homeHidden)
    }

    @Test
    fun serverNameIsAlwaysShown() {
        val o = JSONObject().put("serverFields", org.json.JSONArray(listOf("ping")))
        assertTrue("name" in GhajarLook.fromAny(o).serverFields)
    }

    @Test
    fun looksLikeExportRejectsOtherJson() {
        assertTrue(looksLikeExport(custom.toExport("1.0.10")))
        assertFalse(looksLikeExport(JSONObject().put("configs", 1)))
    }
}
