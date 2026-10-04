package com.ghajarvpn.browser

import org.json.JSONObject

/** Site-scoped permission resources the browser can grant or deny. */
enum class SitePermission { CAMERA, MICROPHONE, GEOLOCATION }

/** Ask / Allow / Block per origin. */
enum class SitePermissionState { ASK, ALLOW, BLOCK }

/**
 * Per-origin permission decisions. Storage is behind a JSON document seam so the
 * policy is unit-testable; private tabs never consult stored decisions.
 */
class SitePermissionsStore(private val document: BrowserDocumentStore) {

    fun state(origin: String, resource: SitePermission): SitePermissionState = if (!supported(resource)) SitePermissionState.BLOCK else runCatching {
        val root = JSONObject(document.load(KEY) ?: "{}")
        val site = root.optJSONObject(origin) ?: return SitePermissionState.ASK
        SitePermissionState.valueOf(site.optString(resource.name.lowercase(), "ASK"))
    }.getOrDefault(SitePermissionState.ASK)

    fun remember(origin: String, resource: SitePermission, state: SitePermissionState) {
        if (origin.isBlank()) return
        val root = runCatching { JSONObject(document.load(KEY) ?: "{}") }.getOrDefault(JSONObject())
        val site = root.optJSONObject(origin) ?: JSONObject().also { root.put(origin, it) }
        site.put(resource.name.lowercase(), state.name)
        document.save(KEY, root.toString())
    }

    fun forget(origin: String) {
        val root = runCatching { JSONObject(document.load(KEY) ?: "{}") }.getOrDefault(JSONObject())
        root.remove(origin)
        document.save(KEY, root.toString())
    }

    fun clearAll() = document.remove(KEY)

    /** origin -> set of resources with an explicit Allow/Block decision. */
    fun decisions(): Map<String, Map<SitePermission, SitePermissionState>> = runCatching {
        val root = JSONObject(document.load(KEY) ?: "{}")
        root.keys().asSequence().mapNotNull { origin ->
            val site = root.optJSONObject(origin) ?: return@mapNotNull null
            val entries = site.keys().asSequence().mapNotNull { res ->
                val resource = runCatching { SitePermission.valueOf(res.uppercase()) }.getOrNull() ?: return@mapNotNull null
                val state = runCatching { SitePermissionState.valueOf(site.optString(res)) }.getOrNull() ?: return@mapNotNull null
                if (state == SitePermissionState.ASK) null else resource to if (supported(resource)) state else SitePermissionState.BLOCK
            }.toMap()
            if (entries.isEmpty()) null else origin to entries
        }.toMap()
    }.getOrDefault(emptyMap())

    fun originOf(url: String): String = runCatching {
        val uri = java.net.URI(url)
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host.orEmpty()
        if (scheme != "https" || host.isBlank()) "" else host
    }.getOrDefault("")

    companion object {
        private const val KEY = "site_permissions"

        fun supported(resource: SitePermission): Boolean = resource == SitePermission.CAMERA

        fun osPermissionFor(resource: SitePermission): String? = when (resource) {
            SitePermission.CAMERA -> ManifestCompat.CAMERA
            SitePermission.MICROPHONE -> null
            SitePermission.GEOLOCATION -> null
        }
    }

    private object ManifestCompat {
        const val CAMERA = android.Manifest.permission.CAMERA
    }
}
