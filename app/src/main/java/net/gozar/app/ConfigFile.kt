package net.gozar.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ConfigFile {

    const val EXTENSION = "grt"
    const val MIME = "application/octet-stream"

    private const val MAGIC = "GRT1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val PBKDF2_ITERS = 210_000

    private const val FLAG_PW = 0x01
    private const val FLAG_CERT = 0x02

    private val P1 = intArrayOf(0x2A, 0x3F, 0x02, 0x18, 0x19, 0x08, 0x40, 0x0A)
    private val P2 = intArrayOf(0x1F, 0x19, 0x40, 0x1B, 0x5C, 0x40, 0x06, 0x08)
    private val P3 = intArrayOf(0x14, 0x40, 0x09, 0x02, 0x00, 0x0C, 0x04, 0x03)
    private val P4 = intArrayOf(0x40, 0x1E, 0x08, 0x15, 0x0C, 0x1F, 0x0C, 0x19)
    private const val MASK = 0x6D

    private val staticSecret: String by lazy {
        val sb = StringBuilder(32)
        listOf(P1, P2, P3, P4).forEach { part ->
            part.forEach { sb.append((it xor MASK).toChar()) }
        }
        sb.toString()
    }

    @Volatile private var certHash: String? = null

    class WrongPassword : Exception()
    class NeedsPassword : Exception()
    class BadFile : Exception()
    class ForeignApp : Exception()
    class NotABackup : Exception()

    /**
     * An old, certificate-bound backup (FLAG_CERT) that this install cannot
     * decrypt because it was written by a differently-signed build. Debug
     * builds are signed with an auto-generated debug keystore that differs
     * per machine/CI run, so every such build produced backups only it could
     * ever read. New backups are never certificate-bound (see [seal]), so
     * this can only surface for files written before that change.
     */
    class ForeignBuild : Exception()

    class Backup(
        val configs: List<ProxyConfig>,
        val subs: List<Subscription>,
        val settings: JSONObject?,
        /** OpenVPN engine preferences (ConfigFile v>=3 only); null on older
         * backups. */
        val openVpnSettings: JSONObject?,
        /** Each saved OpenVPN profile, serialized (ConfigFile v>=4 only);
         * empty on older backups. See [GhajarOpenVpnBridge.exportProfiles]. */
        val openVpnProfiles: List<ByteArray> = emptyList(),
        /** Per-network auto-connect rules (ConfigFile v>=5 only); null on
         * older backups, which leaves the installed rules untouched. */
        val networkRules: JSONObject? = null
    )

    fun isPasswordProtected(bytes: ByteArray): Boolean {
        if (bytes.size < 5) throw BadFile()
        if (String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) throw BadFile()
        return (bytes[4].toInt() and FLAG_PW) != 0
    }

    fun encode(
        context: Context,
        configs: List<ProxyConfig>,
        password: String?,
        locked: Boolean = true
    ): ByteArray {
        val arr = JSONArray()
        configs.forEach { arr.put(it.toJson()) }
        val root = JSONObject().put("v", 1).put("locked", locked).put("configs", arr)
        return seal(root, password)
    }

    fun encodeBackup(
        context: Context,
        configs: List<ProxyConfig>,
        subs: List<Subscription>,
        settings: JSONObject,
        password: String?
    ): ByteArray {
        val cfgArr = JSONArray()
        configs.forEach { cfgArr.put(it.toJson()) }
        val subArr = JSONArray()
        subs.forEach { subArr.put(it.toJson()) }
        val ovpn = GhajarOpenVpnSettings.read(context)
        val ovpnObj = JSONObject()
            .put("reconnectOnNetworkChange", ovpn.reconnectOnNetworkChange)
            .put("useSystemProxy", ovpn.useSystemProxy)
            .put("pauseOnScreenOff", ovpn.pauseOnScreenOff)
            .put("encryptProfiles", ovpn.encryptProfiles)
        val profilesArr = JSONArray()
        GhajarOpenVpnBridge.exportProfiles(context).forEach { bytes ->
            profilesArr.put(Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        val root = JSONObject()
            .put("v", 5)
            .put("kind", "backup")
            .put("configs", cfgArr)
            .put("subs", subArr)
            .put("settings", settings)
            .put("openVpnSettings", ovpnObj)
            .put("openVpnProfiles", profilesArr)
            .put("networkRules", NetworkRules.toJson(context))
        return seal(root, password)
    }

    /**
     * A backup is never bound to the installing APK's signing certificate.
     *
     * It used to be: every backup mixed [signingHash] into the key, so the
     * derived key only existed on that exact signed build. Debug builds are
     * signed with an auto-generated debug keystore (a fresh one per machine,
     * and per CI run on an ephemeral runner), and this screen exports without
     * a password, so in practice *every* exported backup was readable only by
     * the single build that wrote it - installing the next build silently
     * turned every existing backup into an unrecoverable file, reported by
     * the import picker as "this is a shared config, not a backup".
     *
     * The binding also bought nothing: the static secret mixed into the key
     * lives in the APK, so anyone holding the APK can derive the key with or
     * without the certificate. All it did was break the one thing a backup
     * exists for - restoring onto another build or another phone.
     *
     * Confidentiality now comes from [password] when the caller supplies one.
     * Old certificate-bound files are still read (see [open]).
     */
    internal fun seal(root: JSONObject, password: String?): ByteArray {
        val plain = root.toString().toByteArray(Charsets.UTF_8)

        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val hasPw = !password.isNullOrEmpty()

        val key = deriveKey(password, salt, null)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)

        var flags = 0
        if (hasPw) flags = flags or FLAG_PW

        val header = ByteArray(5)
        MAGIC.toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        header[4] = flags.toByte()

        return header + salt + iv + ct
    }

    fun decode(context: Context, bytes: ByteArray, password: String?): List<ProxyConfig> {
        val root = open(bytes, password) { signingHash(context) }
        val arr = root.optJSONArray("configs") ?: throw BadFile()
        val locked = root.optBoolean("locked", true)
        return (0 until arr.length()).map { i ->
            ProxyConfig.fromJson(arr.getJSONObject(i)).copy(
                id = java.util.UUID.randomUUID().toString(),
                subId = "",
                locked = locked,
                source = ConfigSource.COMMUNITY
            )
        }
    }

    fun isBackup(context: Context, bytes: ByteArray, password: String?): Boolean =
        open(bytes, password) { signingHash(context) }.optString("kind") == "backup"

    fun decodeBackup(context: Context, bytes: ByteArray, password: String?): Backup {
        val root = open(bytes, password) { signingHash(context) }
        if (root.optString("kind") != "backup") throw NotABackup()
        val cfgArr = root.optJSONArray("configs") ?: throw BadFile()
        val configs = (0 until cfgArr.length()).map {
            ProxyConfig.fromJson(cfgArr.getJSONObject(it))
        }
        val subArr = root.optJSONArray("subs") ?: JSONArray()
        val subs = (0 until subArr.length()).map {
            Subscription.fromJson(subArr.getJSONObject(it))
        }
        val profilesArr = root.optJSONArray("openVpnProfiles") ?: JSONArray()
        val profiles = (0 until profilesArr.length()).mapNotNull { i ->
            runCatching { Base64.decode(profilesArr.getString(i), Base64.NO_WRAP) }.getOrNull()
        }
        return Backup(
            configs, subs, root.optJSONObject("settings"), root.optJSONObject("openVpnSettings"), profiles,
            root.optJSONObject("networkRules")
        )
    }

    internal fun open(bytes: ByteArray, password: String?, certProvider: () -> String?): JSONObject {
        if (bytes.size < 5 + SALT_LEN + IV_LEN + 16) throw BadFile()
        if (String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) throw BadFile()

        val flags = bytes[4].toInt()
        val hasPw = (flags and FLAG_PW) != 0
        val hasCert = (flags and FLAG_CERT) != 0
        if (hasPw && password.isNullOrEmpty()) throw NeedsPassword()

        // seal() never sets FLAG_CERT any more, so this only runs for files
        // written before that change: they stay readable on the build that
        // wrote them, and fail as ForeignBuild anywhere else.
        val cert = if (hasCert) (certProvider() ?: throw ForeignApp()) else null

        var off = 5
        val salt = bytes.copyOfRange(off, off + SALT_LEN); off += SALT_LEN
        val iv = bytes.copyOfRange(off, off + IV_LEN); off += IV_LEN
        val ct = bytes.copyOfRange(off, bytes.size)

        val key = deriveKey(if (hasPw) password else null, salt, cert)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val plain = try {
            cipher.doFinal(ct)
        } catch (e: Exception) {
            // GCM rejected the key. Which input was wrong is knowable from the
            // flags, and saying so matters: a certificate-bound file that fails
            // here is not corrupt and not "not a backup" - it was written by a
            // differently-signed build and no password can open it.
            when {
                hasCert && !hasPw -> throw ForeignBuild()
                hasPw -> throw WrongPassword()
                else -> throw BadFile()
            }
        }

        return try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BadFile()
        }
    }

    private fun deriveKey(password: String?, salt: ByteArray, cert: String?): SecretKeySpec {
        val material = (password ?: "") + "\u0000" + staticSecret + "\u0000" + (cert ?: "")
        val spec = PBEKeySpec(material.toCharArray(), salt, PBKDF2_ITERS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun signingHash(context: Context): String? {
        certHash?.let { return it }
        val sigs: Array<Signature>? = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
        }.getOrNull()

        if (sigs.isNullOrEmpty()) return null
        val md = MessageDigest.getInstance("SHA-256")
        sigs.map { it.toByteArray() }
            .sortedWith(compareBy({ it.size }, { it.joinToString("") { b -> b.toString() } }))
            .forEach { md.update(it) }
        val h = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
        certHash = h
        return h
    }

    fun writeToCache(context: Context, fileName: String, data: ByteArray): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safe = sanitize(fileName)
        val name = if (safe.endsWith(".$EXTENSION")) safe else "$safe.$EXTENSION"
        val out = File(dir, name)
        out.writeBytes(data)
        return out
    }

    private fun sanitize(name: String): String {
        val cleaned = name.trim().replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")
        return cleaned.ifBlank { "configs" }.take(60)
    }
}