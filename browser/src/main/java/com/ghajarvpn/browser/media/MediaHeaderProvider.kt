package com.ghajarvpn.browser.media

/** Keeps only the small allowlist of headers required to replay the browser request. */
object MediaHeaderProvider {
    private val allowed = setOf("cookie", "referer", "user-agent", "authorization", "origin", "accept-language")

    fun sanitize(input: Map<String, String>): Map<String, String> = input.mapNotNull { (name, value) ->
        val normalized = name.trim().lowercase()
        if (normalized !in allowed || value.isBlank() || value.length > 16_384 || value.contains('\n') || value.contains('\r')) null
        else name.trim() to value
    }.toMap()
}
