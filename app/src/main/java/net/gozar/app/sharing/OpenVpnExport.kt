package net.gozar.app.sharing

/** No scripts, machine-local files, or stale embedded account passwords in a portable export. */
object OpenVpnExport {
    fun portable(raw: String): String {
        validate(raw)
        return raw.replace(Regex("(?ims)^\\s*<auth-user-pass>\\s*\\r?\\n.*?^\\s*</auth-user-pass>\\s*$"), "auth-user-pass")
            .replace(Regex("(?im)^\\s*auth-user-pass\\s+\\[inline]\\s*$"), "auth-user-pass")
    }
    fun validate(raw: String) {
        require(raw.toByteArray().size <= 2*1024*1024)
        val fileKeys=setOf("ca","cert","key","pkcs12","tls-auth","tls-crypt","tls-crypt-v2","crl-verify","auth-user-pass","extra-certs","secret","dh","askpass","http-proxy-user-pass")
        val forbidden=setOf("up","down","route-up","route-pre-down","ipchange","plugin","script-security","config","cd","chroot","management","log","log-append","writepid","status","tls-verify","iproute","client-connect","client-disconnect","learn-address","auth-user-pass-verify")
        var inline: String?=null; var connections=0
        raw.lineSequence().forEach { line ->
            val v=line.trim(); if(v.isEmpty() || v.startsWith('#') || v.startsWith(';')) return@forEach
            if(inline!=null) { if(v.equals("</$inline>",true)) inline=null; return@forEach }
            if(v.equals("<connection>",true)) { require(connections==0); connections++; return@forEach }
            if(v.equals("</connection>",true)) { require(connections==1); connections--; return@forEach }
            if(v.startsWith('<')) { val tag=v.removePrefix("<").removeSuffix(">").lowercase();require(tag in fileKeys);inline=tag; return@forEach }
            val parts=v.split(Regex("\\s+"),limit=2); val key=parts.first().removePrefix("--").lowercase()
            require(key !in forbidden) { "این فایل دستور یا مسیر مخصوص دستگاه دارد؛ فایل portable را از مدیر سرور بگیرید." }
            if(key in fileKeys && parts.size>1) require(parts[1].trim()=="[inline]") { "فایل جانبی $key باید داخل .ovpn قرار گیرد یا جدا منتقل شود." }
        }
        require(inline==null && connections==0) { "بخش inline فایل کامل نیست." }
    }
}
