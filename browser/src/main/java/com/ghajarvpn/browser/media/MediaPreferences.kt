package com.ghajarvpn.browser.media

import android.content.Context

class MediaPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ghajar_media_v1", Context.MODE_PRIVATE)
    var speed: Float
        get() = prefs.getFloat("speed", 1f).coerceIn(.5f, 2f)
        set(value) { prefs.edit().putFloat("speed", value.coerceIn(.5f, 2f)).apply() }
    var seekSeconds: Int
        get() = prefs.getInt("seek", 10).takeIf { it in setOf(5, 10, 15, 30) } ?: 10
        set(value) { prefs.edit().putInt("seek", value.takeIf { it in setOf(5, 10, 15, 30) } ?: 10).apply() }
    var gestures: Boolean
        get() = prefs.getBoolean("gestures", true)
        set(value) { prefs.edit().putBoolean("gestures", value).apply() }
    var backgroundPlayback: Boolean
        get() = prefs.getBoolean("background", false)
        set(value) { prefs.edit().putBoolean("background", value).apply() }
    var autoPictureInPicture: Boolean
        get() = prefs.getBoolean("auto_pip", false)
        set(value) { prefs.edit().putBoolean("auto_pip", value).apply() }
    var continueOnNavigate: Boolean
        get() = prefs.getBoolean("continue", true)
        set(value) { prefs.edit().putBoolean("continue", value).apply() }

    fun resumePosition(url: String): Long = prefs.getLong(MediaPrivacyPolicy.resumeStorageKey(url), 0L)
    fun saveResume(url: String, position: Long) {
        val key = MediaPrivacyPolicy.resumeStorageKey(url)
        if (position < 15_000) prefs.edit().remove(key).apply() else prefs.edit().putLong(key, position).apply()
    }

    fun clearResume(url: String) {
        prefs.edit().remove(MediaPrivacyPolicy.resumeStorageKey(url)).apply()
    }

    fun clearResumeHistory() {
        val keys = prefs.all.keys.filter(MediaPrivacyPolicy::isResumeStorageKey)
        if (keys.isEmpty()) return
        prefs.edit().also { editor -> keys.forEach { editor.remove(it) } }.apply()
    }
}

/** Resume storage contains a hash, never a raw media URL or its credentials. */
object MediaPrivacyPolicy {
    private const val RESUME_PREFIX = "resume_"

    fun resumeStorageKey(url: String): String = RESUME_PREFIX + MediaSourceResolver.resumeKey(url)
    fun isResumeStorageKey(key: String): Boolean = key.startsWith(RESUME_PREFIX)
}
