package net.gozar.app.configtoolkit

import org.json.JSONObject

/** sing-box extended JSON accepts comments and trailing commas. Original text is never rewritten in storage. */
object BoundedJson {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun objectValue(raw: String, maxStringChars: Int = 1024 * 1024): JSONObject {
        require(maxStringChars in 1..3*1024*1024)
        require(raw.length <= MAX_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "JSON size limit" }
        val out = StringBuilder(raw.length)
        var i = 0; var quoted = false; var escaped = false; var depth = 0; var stringLength = 0
        while (i < raw.length) {
            val c = raw[i]
            if (quoted) {
                require(++stringLength <= maxStringChars) { "JSON string limit" }
                out.append(c)
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
                else require(c >= ' ') { "JSON control character" }
                i++; continue
            }
            if (c == '"') { quoted = true; stringLength = 0; out.append(c); i++; continue }
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                i += 2; while (i < raw.length && raw[i] != '\n') i++; out.append(' '); continue
            }
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '*') {
                val end = raw.indexOf("*/", i + 2); require(end >= 0) { "JSON unterminated comment" }
                i = end + 2; out.append(' '); continue
            }
            if (c == '{' || c == '[') { depth++; require(depth <= 64) { "JSON nesting limit" } }
            if (c == '}' || c == ']') {
                require(--depth >= 0) { "JSON delimiter" }
                var p = out.length - 1; while (p >= 0 && out[p].isWhitespace()) p--
                if (p >= 0 && out[p] == ',') {
                    var before = p - 1
                    while (before >= 0 && out[before].isWhitespace()) before--
                    require(before >= 0 && out[before] !in "{[,:" ) { "JSON missing value" }
                    out.setCharAt(p, ' ')
                }
            }
            out.append(c); i++
        }
        require(!quoted && depth == 0) { "JSON truncated" }
        val text = out.toString().trim()
        require(text.startsWith('{') && text.endsWith('}')) { "JSON object required" }
        Syntax(text).check()
        return JSONObject(text)
    }
    /** org.json is deliberately lenient; check RFC 8259 grammar after JSONC cleanup. */
    private class Syntax(private val text: String) {
        private var p = 0
        fun check() { value(); space(); require(p == text.length) { "JSON trailing data" } }
        private fun space() { while (p < text.length && text[p] in " \t\r\n") p++ }
        private fun take(c: Char): Boolean { space(); if (p < text.length && text[p] == c) { p++; return true }; return false }
        private fun need(c: Char) { require(take(c)) { "JSON syntax" } }
        private fun value() {
            space(); require(p < text.length) { "JSON truncated" }
            when (text[p]) {
                '{' -> {
                    p++; val keys=hashSetOf<String>()
                    if (!take('}')) {
                        do { require(keys.add(string())) { "Duplicate JSON field" }; need(':'); value() } while(take(','))
                        need('}')
                    }
                }
                '[' -> { p++; if (!take(']')) { do { value() } while(take(',')); need(']') } }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> number()
            }
        }
        private fun literal(v: String) { require(text.startsWith(v, p)) { "JSON literal" }; p += v.length }
        private fun string(): String {
            space();val start=p
            need('"')
            while (p < text.length) {
                val c = text[p++]
                if (c == '"') return org.json.JSONArray("["+text.substring(start,p)+"]").getString(0)
                require(c >= ' ') { "JSON control character" }
                if (c == '\\') {
                    require(p < text.length) { "JSON escape" }
                    val escaped = text[p++]
                    if (escaped == 'u') {
                        require(p + 4 <= text.length && text.substring(p, p + 4).all { it in "0123456789abcdefABCDEF" }) { "JSON unicode escape" }; p += 4
                    } else require(escaped in "\"\\/bfnrt") { "JSON escape" }
                }
            }
            error("JSON unterminated string")
        }
        private fun number() {
            if (p < text.length && text[p] == '-') p++
            require(p < text.length) { "JSON number" }
            if (text[p] == '0') p++ else { require(text[p] in '1'..'9') { "JSON number" }; digits() }
            if (p < text.length && text[p] == '.') { p++; digits() }
            if (p < text.length && text[p] in "eE") { p++; if (p < text.length && text[p] in "+-") p++; digits() }
        }
        private fun digits() { val start = p; while (p < text.length && text[p] in '0'..'9') p++; require(p > start) { "JSON digits" } }
    }
}
