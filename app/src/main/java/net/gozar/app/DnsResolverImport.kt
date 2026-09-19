package net.gozar.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * What an import produced, including what it threw away and why.
 *
 * The counts are not decoration. A file of a thousand lines that yields six
 * hundred resolvers has had four hundred lines rejected, and "imported 600"
 * on its own leaves the user unable to tell a deduplicated list from a
 * half-parsed one.
 */
data class DnsImportReport(
    val resolvers: List<DnsResolver>,
    /** Lines that held no address at all - blank, comments, prose. */
    val skipped: Int,
    /** Lines with something address-shaped that was not a valid address. */
    val invalid: Int,
    /** Addresses that were already present, in the file or in the list. */
    val duplicates: Int
) {
    val added: Int get() = resolvers.size
    val total: Int get() = added + skipped + invalid + duplicates
}

/**
 * Turning a pile of text into resolvers.
 *
 * All of this is pure: no coroutines, no context, no IO. That is what lets the
 * caller run it on a worker thread and lets the tests feed it the real file.
 *
 * The parsing is deliberately forgiving about shape and strict about
 * addresses. A list of resolvers arrives as a paste from a chat, a scraper's
 * CSV, an exported JSON, or a text file with a title line at the top, and
 * being fussy about the container is how an import fails on a file the user
 * can plainly see the addresses in. Being loose about the addresses, on the
 * other hand, means a scan wasting minutes on typos.
 */
object DnsResolverImport {

    /** Above this, an import is refused rather than silently truncated. */
    const val MAX_ENTRIES = 20_000

    /**
     * Reads resolvers out of arbitrary text.
     *
     * [existing] is the list already held, so a re-import of the same file
     * reports duplicates rather than doubling the list.
     *
     * Format is sniffed rather than declared: a caller that had to say
     * "this is CSV" would be guessing from a file extension, and the extension
     * on a file shared through three chat apps means nothing.
     */
    fun parse(
        text: String,
        existing: Collection<DnsResolver> = emptyList(),
        defaultTransport: DnsTransport = DnsTransport.UDP
    ): DnsImportReport {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") ->
                fromJson(trimmed, existing, defaultTransport)
            else -> fromLines(trimmed.lineSequence(), existing, defaultTransport)
        }
    }

    /**
     * The line-based path: plain text and CSV both.
     *
     * CSV is not parsed as CSV. A resolver list's columns are an address and
     * some labels, in an order nobody agrees on, so every field on the line is
     * tested and the first one that is an address wins. That reads
     * `1.1.1.1,Cloudflare,US` and `US,Cloudflare,1.1.1.1` identically, which
     * a column-index parser cannot.
     */
    private fun fromLines(
        lines: Sequence<String>,
        existing: Collection<DnsResolver>,
        defaultTransport: DnsTransport
    ): DnsImportReport {
        val seen = existing.mapTo(HashSet()) { it.id }
        val out = ArrayList<DnsResolver>()
        var skipped = 0
        var invalid = 0
        var duplicates = 0

        for (raw in lines) {
            if (out.size >= MAX_ENTRIES) break
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) {
                skipped++
                continue
            }
            // A URL is its own kind of entry and is not split on commas at
            // all: https://dns.example/dns-query?types=1,28 is one address,
            // and cutting it at the comma leaves a URL that resolves nothing.
            // Whitespace is the only delimiter a URL cannot contain, so it is
            // the one used - and the name comes from the host rather than from
            // the line's other fields, which for a URL are its own tail.
            if (line.startsWith("https://", ignoreCase = true)) {
                val url = line.split(' ', '\t').first()
                val resolver = manual(url, "", DnsTransport.DOH)
                if (resolver == null) invalid++
                else if (!seen.add(resolver.id)) duplicates++
                else out += resolver
                continue
            }

            val fields = line.split(',', '\t', ';', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val address = fields.firstOrNull { looksAddressish(it) }
            if (address == null) {
                skipped++
                continue
            }
            val resolver = manual(address, "", defaultTransport, labelFrom(line, address))
            if (resolver == null) {
                invalid++
                continue
            }
            if (!seen.add(resolver.id)) duplicates++ else out += resolver
        }
        return DnsImportReport(out, skipped, invalid, duplicates)
    }

    /**
     * JSON, in the three shapes these lists actually arrive in.
     *
     * An array of strings, an array of objects, or an object with a list under
     * some key. The key is not guessed from a fixed name: the first array-valued
     * member is taken, because every exporter names it differently
     * ("servers", "dns", "resolvers", "data") and matching a list of names is
     * a list that is always missing one.
     */
    private fun fromJson(
        text: String,
        existing: Collection<DnsResolver>,
        defaultTransport: DnsTransport
    ): DnsImportReport {
        val array: JSONArray = runCatching {
            if (text.startsWith("[")) JSONArray(text) else {
                val o = JSONObject(text)
                o.keys().asSequence()
                    .mapNotNull { k -> o.optJSONArray(k) }
                    .firstOrNull() ?: JSONArray().put(o)
            }
        }.getOrNull() ?: return DnsImportReport(emptyList(), 0, 1, 0)

        val seen = existing.mapTo(HashSet()) { it.id }
        val out = ArrayList<DnsResolver>()
        var skipped = 0
        var invalid = 0
        var duplicates = 0

        for (i in 0 until minOf(array.length(), MAX_ENTRIES)) {
            val item = array.opt(i)
            val (address, name, transportHint) = when (item) {
                is String -> Triple(item.trim(), "", null)
                is JSONObject -> Triple(
                    firstNonBlank(item, "address", "ip", "server", "host", "url", "addr"),
                    firstNonBlank(item, "name", "label", "title", "provider"),
                    item.optString("protocol", item.optString("transport", "")).ifBlank { null }
                )
                else -> Triple("", "", null)
            }
            if (address.isBlank()) {
                skipped++
                continue
            }
            val transport = transportHint?.let { transportOf(it) } ?: inferTransport(address, defaultTransport)
            val resolver = manual(address, "", transport, name)
            if (resolver == null) {
                invalid++
                continue
            }
            if (!seen.add(resolver.id)) duplicates++ else out += resolver
        }
        return DnsImportReport(out, skipped, invalid, duplicates)
    }

    private fun firstNonBlank(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    /**
     * Builds one resolver from typed-in parts, or null if the address is not one.
     *
     * This is the single place an address becomes a resolver - the manual form,
     * the paste and the file all come through here, so a rule about what is
     * acceptable cannot hold in one path and not another.
     *
     * The transport is never upgraded on its own. A bare IP becomes UDP or TCP
     * on 53 and nothing else: DoT needs a name to verify a certificate against
     * and DoH needs a URL, and guessing either from an IP would mean either a
     * certificate check that cannot succeed or one quietly skipped. Neither is
     * offered.
     */
    fun manual(
        address: String,
        port: String = "",
        transport: DnsTransport = DnsTransport.UDP,
        name: String = ""
    ): DnsResolver? {
        val addr = address.trim()
        if (addr.isEmpty()) return null

        if (transport == DnsTransport.DOH) {
            if (!addr.startsWith("https://", ignoreCase = true)) return null
            // Must have a host, and must not be a bare scheme.
            val host = addr.removePrefix("https://").removePrefix("HTTPS://")
                .substringBefore('/').substringBefore('?')
            if (host.isBlank()) return null
            return DnsResolver(
                name = name.ifBlank { host },
                transport = DnsTransport.DOH,
                address = addr,
                note = "DoH"
            )
        }

        if (transport == DnsTransport.DOT) {
            // A hostname, not an IP: the whole value of DoT is a certificate
            // bound to a name, and there is nothing to bind an IP to.
            if (isIpv4(addr) || isIpv6(addr)) return null
            if (!looksHostname(addr)) return null
            return DnsResolver(
                name = name.ifBlank { addr },
                transport = DnsTransport.DOT,
                address = addr,
                note = if (port.isBlank() || port == "853") "DoT 853" else "DoT $port"
            )
        }

        val ok = isIpv4(addr) || isIpv6(addr) || looksHostname(addr)
        if (!ok) return null
        val portNote = port.trim()
        if (portNote.isNotEmpty() && portNote.toIntOrNull() !in 1..65535) return null
        return DnsResolver(
            name = name.ifBlank { addr },
            transport = transport,
            address = addr,
            note = when {
                portNote.isNotEmpty() && portNote != "53" -> "${transport.name} $portNote"
                transport == DnsTransport.TCP -> "TCP 53"
                else -> addr
            }
        )
    }

    fun transportOf(raw: String): DnsTransport = when (raw.trim().lowercase()) {
        "tcp", "tcp53", "tcp-53" -> DnsTransport.TCP
        "dot", "tls", "dns-over-tls" -> DnsTransport.DOT
        "doh", "https", "dns-over-https" -> DnsTransport.DOH
        else -> DnsTransport.UDP
    }

    private fun inferTransport(address: String, fallback: DnsTransport): DnsTransport =
        if (address.startsWith("https://", ignoreCase = true)) DnsTransport.DOH else fallback

    /**
     * Worth trying to parse as an address.
     *
     * Kept loose on purpose: this only decides whether a field is a candidate,
     * so that "1.2.3.456" is reported as invalid rather than skipped as prose.
     * The difference matters to the user reading the report - one means their
     * file has a typo, the other means it has a header row.
     */
    internal fun looksAddressish(field: String): Boolean {
        if (field.count { it == '.' } >= 3 && field.first().isDigit()) return true
        if (field.count { it == ':' } >= 2) return true
        return false
    }

    /** A label for the entry, taken from the line's other fields if there is one. */
    private fun labelFrom(line: String, address: String): String =
        line.split(',', '\t', ';')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != address && !looksAddressish(it) }
            ?.take(40)
            ?: ""

    fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            // No leading zeros: "1.1.1.01" is not how anyone writes an address,
            // and accepting it means two spellings of one resolver in the list.
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
                (part.length == 1 || part[0] != '0') && part.toInt() in 0..255
        }
    }

    /**
     * IPv6, including the one compressed form.
     *
     * Hand-written rather than handed to InetAddress: InetAddress.getByName on
     * a hostname performs a DNS lookup, on the system resolver, from whatever
     * thread called it. Validating a thousand lines that way would be a
     * thousand lookups through the resolver this screen exists to replace.
     */
    fun isIpv6(value: String): Boolean {
        val v = value.trim().removePrefix("[").removeSuffix("]")
        if (!v.contains(':')) return false
        if (v.count { it == ':' } > 8) return false
        val doubleColons = Regex("::").findAll(v).count()
        if (doubleColons > 1) return false
        val groups = v.split(':')
        if (doubleColons == 0 && groups.size != 8) return false
        var sawEmpty = 0
        for (g in groups) {
            if (g.isEmpty()) {
                sawEmpty++
                continue
            }
            // An embedded IPv4 tail is legal and appears in real lists.
            if (g.contains('.')) {
                if (g != groups.last() || !isIpv4(g)) return false
                continue
            }
            if (g.length > 4 || !g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return false
        }
        return sawEmpty <= 2
    }

    /**
     * A name that could be looked up.
     *
     * The all-numeric last label is the rule that matters. Without it
     * `1.2.3.456` - a mistyped address - passes as a hostname and gets
     * imported, so the scan spends its time on a name that cannot exist while
     * the report fails to say the file has a typo in it. No top-level domain
     * is all digits, which is what makes the test safe as well as useful.
     */
    fun looksHostname(value: String): Boolean {
        val v = value.trim().trim('.')
        if (v.isEmpty() || v.length > 253 || !v.contains('.')) return false
        if (v.substringAfterLast('.').all { it.isDigit() }) return false
        return v.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.all { it.isLetterOrDigit() || it == '-' || it == '_' } &&
                !label.startsWith('-') && !label.endsWith('-')
        }
    }
}
