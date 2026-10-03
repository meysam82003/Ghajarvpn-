package net.gozar.app.security.vault

/** Contract only: capabilities must be supplied by a deployed, authenticated adapter. None is enabled by default. */
interface QuotaProvider {
    data class Capabilities(val canCreateChildQuota: Boolean, val canRegisterDevices: Boolean,
        val canRenew: Boolean, val canEnforceDuringSession: Boolean, val allocation: Allocation)
    enum class Allocation { HARD_RESERVATION, SHARED_POOL, SHARED_PARENT_ONLY }
    val capabilities: Capabilities
    suspend fun createEntitlement(parentId: String, bytes: Long, deviceLimit: Int?): SignedEntitlement
    suspend fun getEntitlement(entitlementId: String): SignedEntitlement
    suspend fun getUsage(entitlementId: String): SignedEntitlement
    suspend fun registerDevice(entitlementId: String, publicKey: ByteArray, proof: ByteArray): SignedEntitlement
    suspend fun removeDevice(entitlementId: String, fingerprint: String): SignedEntitlement
    suspend fun revoke(entitlementId: String): SignedEntitlement
    suspend fun renew(entitlementId: String): SignedEntitlement
    suspend fun increaseQuota(entitlementId: String, bytes: Long): SignedEntitlement
    suspend fun extendExpiry(entitlementId: String, expiresAt: Long): SignedEntitlement
}

class SignedEntitlement(val keyId: String, val payload: ByteArray, val signature: ByteArray) {
    override fun toString() = "SignedEntitlement(redacted)"
}

data class ParentAllowance(val totalBytes: Long, val reservedBytes: Long, val consumedBytes: Long) {
    init { require(totalBytes>=0 && reservedBytes>=0 && consumedBytes>=0) }
    // Server must define whether consumed is already included in reserved; this contract treats them as disjoint.
    val availableToAllocate: Long get() = (totalBytes - minOf(totalBytes,VaultQuotaPolicy.saturatedAdd(reservedBytes,consumedBytes))).coerceAtLeast(0)
    fun reserve(bytes: Long): ParentAllowance {
        require(bytes>0 && bytes<=availableToAllocate) { "Parent allowance insufficient" }
        return copy(reservedBytes=Math.addExact(reservedBytes,bytes))
    }
}
