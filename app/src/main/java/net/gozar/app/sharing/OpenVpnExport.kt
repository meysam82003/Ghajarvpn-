package net.gozar.app.sharing

/** Portable client export gate; does not modify the installed OpenVPN profile or disable verification. */
object OpenVpnExport {
    private val fileKeys = setOf("ca", "cert", "key", "pkcs12", "tls-auth", "tls-crypt", "tls-crypt-v2",
        "crl-verify", "auth-user-pass", "extra-certs", "secret", "dh", "askpass", "http-proxy-user-pass")
    private val forbidden = setOf("up", "down", "route-up", "route-pre-down", "ipchange", "plugin",
        "script-security", "config", "cd", "chroot", "management", "log", "log-append", "writepid", "status",
        "tls-verify", "tls-crypt-v2-verify", "iproute", "client-connect", "client-disconnect", "learn-address",
        "auth-user-pass-verify", "tls-export-cert", "tmp-dir", "replay-persist", "ifconfig-pool-persist",
        "auth-gen-token-secret", "client-config-dir", "capath", "providers", "pkcs11-providers", "engine")

    fun validate(raw: String) { portable(raw) }

    fun portable(raw: String): String {
        require(raw.length <= 2*1024*1024 && raw.toByteArray().size <= 2*1024*1024)
        require('\u0000' !in raw) { "فایل OpenVPN کاراکتر نامعتبر دارد." }
        var inline: String? = null
        var connections = 0
        val output = ArrayList<String>()
        raw.lineSequence().forEachIndexed { index, original ->
            val line = if(index==0) original.removePrefix("\uFEFF") else original
            val block = inline
            if(block!=null) {
                val end = line.trim().equals("</$block>", true)
                if(block!="auth-user-pass") output += line
                if(end) inline=null
                return@forEachIndexed
            }
            var parts = tokens(line)
            if(parts.isEmpty()) { output += line; return@forEachIndexed }
            parts = listOf(parts.first().removePrefix("--").lowercase()) + parts.drop(1)
            // OpenVPN's add_option interprets this prefix as an optional directive, not an inert environment variable.
            if(parts.take(2)==listOf("setenv", "opt")) {
                require(parts.size>=3)
                parts=parts.drop(2)
            }
            val key=parts.first().removePrefix("--").lowercase()
            if(key=="<connection>" || key=="</connection>") {
                require(parts.size==1)
                if(key=="<connection>") { require(connections==0); connections++ }
                else { require(connections==1); connections-- }
            } else if(key.startsWith('<')) {
                val tag=key.removePrefix("<").removeSuffix(">")
                require(key.endsWith('>') && parts.size==1 && tag in fileKeys) { "بخش inline فایل شناخته‌شده نیست." }
                inline=tag
                if(tag=="auth-user-pass") { output += "auth-user-pass"; return@forEachIndexed }
            } else {
                require(key !in forbidden && !key.startsWith("management-")) {
                    "این فایل دستور یا مسیر مخصوص دستگاه دارد؛ فایل portable را از مدیر سرور بگیرید."
                }
                if(key in fileKeys) {
                    val args=parts.drop(1)
                    val promptOnly=key in setOf("auth-user-pass", "askpass") && args.isEmpty()
                    val inlined=args.firstOrNull()=="[inline]" && (args.size==1 ||
                        key in setOf("tls-auth", "secret") && args.size==2 && args[1] in setOf("0", "1"))
                    require(promptOnly || inlined) { "فایل جانبی $key باید داخل .ovpn قرار گیرد یا جدا منتقل شود." }
                    if(key=="auth-user-pass") { output += "auth-user-pass"; return@forEachIndexed }
                }
                // Proxy auth-file arguments occur on the proxy directive itself, not only on fileKeys above.
                if(key=="http-proxy" && parts.size>=4) require(parts[3] in setOf("auto", "auto-nct")) {
                    "فایل احراز هویت پراکسی باید جدا و به‌صورت امن روی مقصد فراهم شود."
                }
                if(key=="socks-proxy") require(parts.size<=3) { "فایل احراز هویت پراکسی قابل انتقال خودکار نیست." }
            }
            output += line
        }
        require(inline==null && connections==0) { "بخش inline فایل کامل نیست." }
        return output.joinToString("\n")
    }

    /** Quotes/escapes/comments are syntax, including around the option name. Ambiguous input fails closed. */
    private fun tokens(line: String): List<String> {
        val result=ArrayList<String>(); var i=0
        while(i<line.length) {
            while(i<line.length && line[i].isWhitespace()) i++
            if(i==line.length || line[i] in "#;") break
            val quote=line[i].takeIf { it=='\'' || it=='"' }
            if(quote!=null) i++
            val token=StringBuilder(); var ended=quote==null
            while(i<line.length) {
                val ch=line[i++]
                if(quote!=null && ch==quote) { ended=true; break }
                if(quote==null && ch.isWhitespace()) break
                if(ch=='\\' && quote!='\'') {
                    require(i<line.length) { "escape ناقص در فایل OpenVPN." }
                    val next=line[i++]
                    require(next=='\\' || next=='"' || next.isWhitespace()) { "escape نامعتبر در فایل OpenVPN." }
                    token.append(next)
                } else token.append(ch)
            }
            require(ended) { "نقل‌قول فایل OpenVPN کامل نیست." }
            result += token.toString()
        }
        return result
    }
}
