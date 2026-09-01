package net.gozar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser as OvpnConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.StatusListener
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class PendingOpenVpnImport(
    val raw: String,
    val name: String,
    val host: String,
    val port: Int,
    val needsCredentials: Boolean,
    val embeddedUsername: String,
    val embeddedPassword: String
)

/** A saved OVPN profile as shown in the Ghajar server list. */
data class GhajarOvpnProfile(
    val uuid: String,
    val name: String,
    val host: String,
    val port: Int,
    val needsCredentials: Boolean
)

enum class GhajarOvpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

object GhajarOpenVpnBridge {
    private val _pending = MutableStateFlow<PendingOpenVpnImport?>(null)
    val pending = _pending.asStateFlow()
    private val _status = MutableStateFlow(GhajarOvpnState.DISCONNECTED)
    val status = _status.asStateFlow()
    private val _activeUuid = MutableStateFlow<String?>(null)
    val activeUuid = _activeUuid.asStateFlow()
    private var statusListener: StatusListener? = null
    private var stateListener: VpnStatus.StateListener? = null

    fun initialize(context: Context) {
        if (statusListener == null) {
            statusListener = StatusListener().also { it.init(context.applicationContext) }
        }
        ensureStateListener()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel(OpenVPNService.NOTIFICATION_CHANNEL_BG_ID, "اتصال OVPN قاجار", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(OpenVPNService.NOTIFICATION_CHANNEL_NEWSTATUS_ID, "وضعیت OVPN قاجار", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(OpenVPNService.NOTIFICATION_CHANNEL_USERREQ_ID, "درخواست امنیتی OVPN", NotificationManager.IMPORTANCE_HIGH)
                )
            )
        }
    }

    private fun ensureStateListener() {
        if (stateListener != null) return
        stateListener = object : VpnStatus.StateListener {
            override fun updateState(state: String, logmessage: String, localizedResId: Int, level: ConnectionStatus, Intent: android.content.Intent?) {
                _status.value = when (level) {
                    ConnectionStatus.LEVEL_CONNECTED -> GhajarOvpnState.CONNECTED
                    ConnectionStatus.LEVEL_AUTH_FAILED -> GhajarOvpnState.ERROR
                    ConnectionStatus.LEVEL_NOTCONNECTED -> GhajarOvpnState.DISCONNECTED
                    else -> GhajarOvpnState.CONNECTING
                }
            }

            override fun setConnectedVPN(uuid: String?) {
                _activeUuid.value = uuid?.takeIf { it.isNotBlank() }
            }
        }
        VpnStatus.addStateListener(stateListener)
    }

    fun offer(bytes: ByteArray): Result<PendingOpenVpnImport> = runCatching {
        val raw = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF')
        require(raw.contains("remote ", ignoreCase = true) || raw.contains("<connection>", ignoreCase = true)) {
            "فایل OVPN معتبر نیست"
        }
        val profile = parse(raw)
        val connection = profile.mConnections.firstOrNull()
        val result = PendingOpenVpnImport(
            raw = raw,
            name = BrandConfig.sanitizePublicText(profile.name)
                .takeUnless { it.equals(OvpnConfigParser.CONVERTED_PROFILE, true) }
                ?: "سرویس OVPN قاجار",
            host = connection?.mServerName.orEmpty(),
            port = connection?.mServerPort?.toIntOrNull() ?: 1194,
            needsCredentials = profile.isUserPWAuth() && (profile.mUsername.isNullOrBlank() || profile.mPassword.isNullOrBlank()),
            embeddedUsername = profile.mUsername.orEmpty(),
            embeddedPassword = profile.mPassword.orEmpty()
        )
        _pending.value = result
        result
    }

    fun dismiss() { _pending.value = null }

    suspend fun connect(
        context: Context,
        pending: PendingOpenVpnImport,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = parse(pending.raw)
            profile.mName = pending.name
            if (username.isNotBlank()) profile.mUsername = username
            if (password.isNotBlank()) profile.mPassword = password
            require(profile.needUserPWInput(null, null) == 0) { "نام کاربری یا رمز عبور OVPN لازم است" }

            val manager = ProfileManager.getInstance(context.applicationContext)
            manager.addProfile(profile)
            ProfileManager.saveProfile(context.applicationContext, profile)
            manager.saveProfileList(context.applicationContext)
            VPNLaunchHelper.startOpenVpn(profile, context.applicationContext, "Ghajarvpn user request", true)
            _pending.value = null
        }
    }

    /** Saved OVPN profiles for the server list section. */
    fun profiles(context: Context): List<GhajarOvpnProfile> = runCatching {
        ProfileManager.getInstance(context.applicationContext).getProfiles().map { profile ->
            GhajarOvpnProfile(
                uuid = profile.getUUIDString(),
                name = BrandConfig.sanitizePublicText(profile.mName.ifBlank { "سرویس OVPN قاجار" }),
                host = profile.mConnections.firstOrNull()?.mServerName.orEmpty(),
                port = profile.mConnections.firstOrNull()?.mServerPort?.toIntOrNull() ?: 1194,
                needsCredentials = profile.isUserPWAuth() && (profile.mUsername.isNullOrBlank() || profile.mPassword.isNullOrBlank())
            )
        }.sortedBy { it.name }
    }.getOrDefault(emptyList())

    fun findProfile(context: Context, uuid: String): VpnProfile? =
        ProfileManager.getInstance(context.applicationContext).getProfiles()
            .firstOrNull { it.getUUIDString() == uuid }

    /** Connects a saved profile; the caller must have prepared the VPN permission. */
    fun connectSaved(context: Context, uuid: String): Result<Unit> = runCatching {
        val profile = findProfile(context, uuid)
            ?: throw IllegalStateException("پروفایل OVPN پیدا نشد؛ دوباره ایمپورتش کن.")
        require(profile.needUserPWInput(null, null) == 0) { "نام کاربری یا رمز عبور این پروفایل لازم است" }
        VPNLaunchHelper.startOpenVpn(profile, context.applicationContext, "Ghajarvpn user request", true)
    }

    fun updateCredentials(context: Context, uuid: String, username: String, password: String): Result<Unit> = runCatching {
        val profile = findProfile(context, uuid)
            ?: throw IllegalStateException("پروفایل OVPN پیدا نشد.")
        profile.mUsername = username
        profile.mPassword = password
        val manager = ProfileManager.getInstance(context.applicationContext)
        ProfileManager.saveProfile(context.applicationContext, profile)
        manager.saveProfileList(context.applicationContext)
    }

    fun delete(context: Context, uuid: String): Result<Unit> = runCatching {
        val profile = findProfile(context, uuid)
            ?: throw IllegalStateException("پروفایل OVPN پیدا نشد.")
        ProfileManager.getInstance(context.applicationContext).removeProfile(context.applicationContext, profile)
    }

    /** Gracefully stops the bundled OpenVPN engine from inside the app. */
    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext
            val latch = CountDownLatch(1)
            var service: IOpenVPNServiceInternal? = null
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    service = IOpenVPNServiceInternal.Stub.asInterface(binder)
                    try { service?.stopVPN(false) } catch (_: Exception) {}
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    latch.countDown()
                }
            }
            val intent = Intent(app, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE)
            val bound = runCatching { app.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            if (bound) {
                latch.await(5, TimeUnit.SECONDS)
                runCatching { app.unbindService(connection) }
            } else {
                VpnStatus.updateStateString("DISCONNECTED", "", 0, ConnectionStatus.LEVEL_NOTCONNECTED)
            }
        }
    }

    private fun parse(raw: String): VpnProfile {
        val parser = OvpnConfigParser()
        parser.parseConfig(StringReader(raw))
        return parser.convertProfile()
    }
}
