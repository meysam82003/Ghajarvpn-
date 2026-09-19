package net.gozar.app

/**
 * Which part of the app a log line came from, and the search over the buffer.
 *
 * This is separate from the log screen on purpose. Deciding what a line is
 * and whether it matches is the part that can be wrong in a way nobody
 * notices - a filter that quietly drops the one line you needed looks
 * identical to a filter that works - so it lives here where a test can hold
 * it to account, and the screen only draws the result.
 */
object GhajarLogFilter {

    /**
     * A source is a group of tags, not a single one.
     *
     * The tags are the real TAG constants in this app, gathered rather than
     * invented: a source that matches nothing would be a chip that always
     * shows an empty list, which is worse than not offering it.
     */
    enum class Source(val id: String) {
        ALL("all"),
        APP("app"),
        CORE("core"),
        TUNNEL("tunnel"),
        DNS("dns"),
        TOR("tor");
    }

    /**
     * Exact tags first, then a substring fallback.
     *
     * The fallback is what keeps this honest as the app grows: a tag added
     * next year that contains "Dns" lands under DNS without anyone
     * remembering to come back here, and anything unrecognised lands under
     * APP rather than disappearing.
     */
    private val EXACT: Map<String, Source> = buildMap {
        // The tunnel engines, each a separate process or library.
        listOf(
            "Psiphon", "Aether", "OpenVPN", "GhajarIke",
            "GhajarSsh", "GhajarSshShell", "GhajarSftp", "GhajarZeptun"
        ).forEach { put(it, Source.TUNNEL) }

        // The core and the tunnel it drives.
        listOf(
            "XrayCore", "GozarVpnService", "VpnState", "VpnCoordinator",
            "GhajarConnect", "GhajarConfigBuilder", "GhajarLaunch",
            "GhajarQuick", "GhajarRotate", "GhajarNetRules", "GhajarAuto"
        ).forEach { put(it, Source.CORE) }

        put("Tor", Source.TOR)

        // Everything the user sees rather than the network stack.
        listOf(
            "Startup", "Store", "Payment", "GhajarWidget", "GhajarGeo",
            "GhajarPin", "GhajarNet", "GozarBrowserRoute"
        ).forEach { put(it, Source.APP) }
    }

    fun sourceOf(tag: String): Source {
        EXACT[tag]?.let { return it }
        val lower = tag.lowercase()
        return when {
            lower.contains("dns") -> Source.DNS
            lower.contains("tor") -> Source.TOR
            lower.contains("vpn") || lower.contains("core") ||
                lower.contains("connect") -> Source.CORE
            lower.contains("ssh") || lower.contains("psiphon") ||
                lower.contains("aether") || lower.contains("ike") ||
                lower.contains("zeptun") -> Source.TUNNEL
            else -> Source.APP
        }
    }

    /**
     * The minimum level a chip stands for.
     *
     * "Warnings" means warnings and worse, not warnings only. A user looking
     * for what went wrong wants the errors too, and a filter that hid them
     * because they are a different level would be actively misleading.
     */
    enum class Floor(val id: String, val min: GhajarLogLevel) {
        ALL("all", GhajarLogLevel.DEBUG),
        INFO("info", GhajarLogLevel.INFO),
        WARN("warn", GhajarLogLevel.WARN),
        ERROR("error", GhajarLogLevel.ERROR);
    }

    private fun rank(level: GhajarLogLevel): Int = when (level) {
        GhajarLogLevel.DEBUG -> 0
        GhajarLogLevel.INFO -> 1
        GhajarLogLevel.WARN -> 2
        GhajarLogLevel.ERROR -> 3
        // A crash is the most severe thing here, so it survives every floor.
        GhajarLogLevel.CRASH -> 4
    }

    /**
     * The lines to show, in the order they were logged.
     *
     * [query] is matched against the tag and the message but not the
     * timestamp: searching for "09" should not return every line logged in
     * September.
     */
    fun apply(
        entries: List<GhajarLogEntry>,
        source: Source = Source.ALL,
        floor: Floor = Floor.ALL,
        query: String = ""
    ): List<GhajarLogEntry> {
        val needle = query.trim()
        val minRank = rank(floor.min)
        return entries.filter { entry ->
            (source == Source.ALL || sourceOf(entry.tag) == source) &&
                rank(entry.level) >= minRank &&
                (needle.isEmpty() ||
                    entry.message.contains(needle, ignoreCase = true) ||
                    entry.tag.contains(needle, ignoreCase = true))
        }
    }

    /**
     * How many lines each source has, for the counts beside the chips.
     *
     * Computed in one pass over the buffer rather than by running [apply]
     * once per source: the buffer holds thousands of lines and this runs on
     * every new one.
     */
    fun countsBySource(entries: List<GhajarLogEntry>): Map<Source, Int> {
        val counts = HashMap<Source, Int>(Source.entries.size)
        entries.forEach { entry ->
            val source = sourceOf(entry.tag)
            counts[source] = (counts[source] ?: 0) + 1
        }
        counts[Source.ALL] = entries.size
        return counts
    }

    /** The plain text of a selection, for the clipboard and for sharing. */
    fun asText(entries: List<GhajarLogEntry>): String =
        entries.joinToString("\n") { it.formatted() }
}
