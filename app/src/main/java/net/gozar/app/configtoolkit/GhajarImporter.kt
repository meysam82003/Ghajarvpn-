package net.gozar.app.configtoolkit

import net.gozar.app.ConfigStore
import org.json.JSONObject

data class ImportResult(val requested: Int, val imported: Int, val rejected: Int)

class GhajarImporter(private val store: ConfigStore) {
    suspend fun import(parsed: ParsedConfig, selectedIds: Set<String>? = null): ImportResult {
        store.awaitReady()
        val selected = if (selectedIds == null) parsed.profiles else parsed.profiles.filter { it.id in selectedIds }
        val valid = selected.filter { ProfileValidator.validate(it).valid }
        val verified = valid.filter { profile ->
            // Round-trip is mandatory only for protocols with a share-link representation.
            RoundTripVerifier.verify(profile).isSuccess
        }
        val imported = store.addImported(verified.map(NormalizedProfile::toProxyConfig))
        return ImportResult(selected.size, imported, selected.size - imported)
    }
}

object EditableNpvtCopy {
    fun create(original: ByteArray, clearPassword: Boolean): ByteArray {
        val text = ReadablePayload.extract(ConfigInput(original, "owned.npvt"), "NPVT1")
            ?: throw ConfigToolkitException.UnsupportedFormat(ConfigFormat.NPVT)
        val root = runCatching { JSONObject(text) }.getOrElse {
            throw ConfigToolkitException.InvalidConfig("نسخهٔ قابل ویرایش فقط برای NPVT JSON متعلق به کاربر ساخته می‌شود.")
        }
        val lock = root.optJSONObject("lockConfig") ?: JSONObject().also { root.put("lockConfig", it) }
        lock.put("isLocked", false)
            .put("onlyMobileNetwork", false)
            .put("blockRootedAndJailbroken", false)
            .put("onlyOfficialStores", false)
            .put("deviceIds", "")
            .put("expiryDate", "")
        if (clearPassword) lock.put("password", "")
        val output = root.toString(2).toByteArray(Charsets.UTF_8)
        val reparsed = NpvtDecoder().decode(ConfigInput(output, "Ghajar_unlocked_owned.npvt"))
        check(reparsed.profiles.isNotEmpty()) { "نسخهٔ خروجی قابل Parse نیست." }
        return output
    }
}
