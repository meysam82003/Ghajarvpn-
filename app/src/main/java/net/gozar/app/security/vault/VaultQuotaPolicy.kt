package net.gozar.app.security.vault

/** Pure decision layer; runtime must persist activation/usage before reporting success. */
object VaultQuotaPolicy {
    data class Usage(val upload: Long = 0, val download: Long = 0, val firstImportAt: Long? = null, val firstConnectAt: Long? = null) {
        init { require(upload >= 0 && download >= 0) }
        fun bytes(mode: Accounting): Long = when(mode) {
            Accounting.UPLOAD -> upload
            Accounting.DOWNLOAD -> download
            Accounting.TOTAL -> saturatedAdd(upload,download)
        }
    }
    data class Decision(val status: EntitlementStatus, val usedBytes: Long, val remainingBytes: Long?, val expiresAt: Long?) {
        val connectable get() = status == EntitlementStatus.ACTIVE
    }
    fun local(entry: VaultEntry, usage: Usage, now: Long): Decision {
        require(now >= 0)
        val quota=entry.quota
        val used=usage.bytes(quota.accounting)
        val activation=quota.activatedAt ?: when(quota.activationMode) {
            ActivationMode.CREATED -> entry.createdAt
            ActivationMode.IMPORTED -> usage.firstImportAt
            ActivationMode.FIRST_CONNECT -> usage.firstConnectAt
        }
        val relative=if(activation!=null && quota.validityMillis!=null) saturatedAdd(activation,quota.validityMillis) else null
        val expiry=listOfNotNull(entry.expiresAt,relative).minOrNull()
        val remaining=quota.quotaBytes?.let { (it-used).coerceAtLeast(0) }
        val status=when {
            quota.mode==QuotaMode.SERVER -> EntitlementStatus.UNKNOWN // never authorize server mode using local counters
            expiry!=null && now>=expiry -> EntitlementStatus.EXPIRED
            quota.mode==QuotaMode.LOCAL && remaining==0L -> EntitlementStatus.QUOTA_EXHAUSTED
            else -> EntitlementStatus.ACTIVE
        }
        return Decision(status,used,remaining,expiry)
    }
    internal fun saturatedAdd(a: Long,b: Long): Long { require(a>=0 && b>=0); return if(a>Long.MAX_VALUE-b) Long.MAX_VALUE else a+b }
}
