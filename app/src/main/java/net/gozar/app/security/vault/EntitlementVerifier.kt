package net.gozar.app.security.vault

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** Public keys come from owner-approved app configuration, never from the imported vault itself. */
class EntitlementVerifier(private val trustedKeys: Map<String,ByteArray>) {
    class Verified internal constructor(val id: String,val fingerprint: String,val quotaBytes: Long,val usedBytes: Long,
        val expiresAt: Long?,val status: EntitlementStatus,val issuedAt: Long,val validUntil: Long,val sequence: Long) {
        val remainingBytes get() = (quotaBytes-usedBytes).coerceAtLeast(0)
    }
    fun verify(snapshot: SignedEntitlement, expectedId: String, configFingerprint: String, deviceFingerprint: String,
        now: Long, minimumSequence: Long = 0): Verified {
        require(snapshot.payload.size in 1..16384 && snapshot.signature.size==64)
        val key=trustedKeys[snapshot.keyId] ?: error("Untrusted entitlement issuer")
        require(key.size==32)
        val verifier=Ed25519Signer().apply { init(false,Ed25519PublicKeyParameters(key,0)) }
        val data="GhajarEntitlement/v1\u0000".toByteArray()+snapshot.payload
        verifier.update(data,0,data.size); require(verifier.verifySignature(snapshot.signature)) { "Invalid entitlement signature" }
        val o=VaultFormat.json(snapshot.payload)
        require(VaultFormat.canonical(o).toByteArray().contentEquals(snapshot.payload)) { "Noncanonical entitlement" }
        fun nonnegative(k: String): Long { val v=o.get(k); require(v is Long || v is Int); return (v as Number).toLong().also { require(it>=0) } }
        require(nonnegative("version")==1L && o.getString("entitlementId")==expectedId && o.getString("configFingerprint")==configFingerprint)
        require(o.getString("deviceFingerprint")==deviceFingerprint)
        val issued=nonnegative("issuedAt"); val valid=nonnegative("validUntil"); val seq=nonnegative("sequence")
        require(issued<=now && valid>now && valid>=issued && valid-issued<=3600000 && seq>=minimumSequence) { "Stale entitlement" }
        val quota=nonnegative("quotaBytes"); val used=nonnegative("usedBytes")
        val expires=if(o.has("expiresAt")&&!o.isNull("expiresAt"))nonnegative("expiresAt") else null
        var status=EntitlementStatus.valueOf(o.getString("status"))
        if(status==EntitlementStatus.ACTIVE && expires!=null && now>=expires) status=EntitlementStatus.EXPIRED
        if(status==EntitlementStatus.ACTIVE && used>=quota) status=EntitlementStatus.QUOTA_EXHAUSTED
        return Verified(expectedId,configFingerprint,quota,used,expires,status,issued,valid,seq)
    }
}
