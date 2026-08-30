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

data class PendingOpenVpnImport(
    val raw: String,
    val name: String,
    val host: String,
    val port: Int,
    val needsCredentials: Boolean,
    val embeddedUsername: String,
    val embeddedPassword: String
)

object GhajarOpenVpnBridge {
    private val _pending = MutableStateFlow<PendingOpenVpnImport?>(null)
    val pending = _pending.asStateFlow()
    private var statusListener: StatusListener? = null
    private var stateListener: VpnStatus.StateListener? = null

    fun initialize(context: Context) {
        if (statusListener == null) {
            statusListener = StatusListener().also { it.init(context.applicationContext) }
        }
        if (stateListener == null) {
            val app = context.applicationContext
            stateListener = object : VpnStatus.StateListener {
                override fun updateState(state: String?, logmessage: String?, localizedResId: Int,
                    level: ConnectionStatus?, intent: Intent?) {
                    val id = activeId(app) ?: return
                    when (level) {
                        ConnectionStatus.LEVEL_CONNECTED -> {
                            if (VpnState.activeId.value != id) VpnState.setConnecting(id)
                            VpnState.setConnected()
                        }
                        ConnectionStatus.LEVEL_AUTH_FAILED ->
                            VpnState.setError("نام کاربری یا رمز عبور OVPN پذیرفته نشد.")
                        ConnectionStatus.LEVEL_NOTCONNECTED -> {
                            clearActive(app)
                            if (VpnState.activeId.value?.startsWith("ovpn:") == true) VpnState.setDisconnected()
                        }
                        ConnectionStatus.LEVEL_START,
                        ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
                        ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
                        ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT ->
                            if (VpnState.activeId.value != id) VpnState.setConnecting(id)
                        else -> Unit
                    }
                }

                override fun setConnectedVPN(uuid: String?) {
                    if (uuid.isNullOrBlank()) return
                    val expected = "ovpn:$uuid"
                    if (activeId(app) == expected && VpnState.activeId.value != expected) {
                        VpnState.setConnecting(expected)
                    }
                }
            }.also(VpnStatus::addStateListener)
        }
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
            syncSavedProfiles(context.applicationContext, ConfigStore.get(context.applicationContext))
            val active = "ovpn:${profile.getUUIDString()}"
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ACTIVE_ID, active).putString(KEY_ACTIVE_NAME, pending.name).commit()
            VpnState.setConnecting(active)
            VPNLaunchHelper.startOpenVpn(profile, context.applicationContext, "Ghajarvpn user request", true)
            _pending.value = null
        }
    }

    suspend fun connectSaved(context: Context, config: ProxyConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uuid = config.id.removePrefix("ovpn:")
            require(uuid.isNotBlank() && uuid != config.id) { "پروفایل OVPN معتبر نیست" }
            val app = context.applicationContext
            val profile = ProfileManager.get(app, uuid)
                ?: throw IllegalArgumentException("پروفایل OVPN پیدا نشد؛ فایل را دوباره وارد کن")
            require(profile.needUserPWInput(null, null) == 0) {
                "اطلاعات ورود OVPN ذخیره نشده؛ فایل را دوباره وارد کن"
            }
            val active = "ovpn:${profile.getUUIDString()}"
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ACTIVE_ID, active)
                .putString(KEY_ACTIVE_NAME, BrandConfig.sanitizePublicText(profile.name))
                .commit()
            VpnState.setConnecting(active)
            VPNLaunchHelper.startOpenVpn(profile, app, "Ghajarvpn saved profile", true)
        }
    }

    suspend fun syncSavedProfiles(context: Context, store: ConfigStore): Int = withContext(Dispatchers.IO) {
        store.awaitReady()
        val app = context.applicationContext
        val manager = ProfileManager.getInstance(app)
        manager.refreshVPNList(app)
        val existing = store.configs.value.mapTo(hashSetOf()) { it.id }
        val missing = manager.profiles.mapNotNull { profile ->
            val id = "ovpn:${profile.getUUIDString()}"
            if (id in existing) return@mapNotNull null
            val connection = profile.mConnections.firstOrNull()
            ProxyConfig(
                name = BrandConfig.sanitizePublicText(profile.name).ifBlank { "سرویس OVPN قاجار" },
                protocol = "openvpn",
                address = connection?.mServerName.orEmpty().ifBlank { "OVPN" },
                port = connection?.mServerPort?.toIntOrNull() ?: 1194,
                uuid = profile.getUUIDString(),
                network = "openvpn",
                source = ConfigSource.PERSONAL,
                id = id
            )
        }
        if (missing.isNotEmpty()) store.addImported(missing)
        missing.size
    }

    fun removeSaved(context: Context, config: ProxyConfig): Boolean = runCatching {
        val uuid = config.id.removePrefix("ovpn:")
        require(uuid.isNotBlank() && uuid != config.id)
        val app = context.applicationContext
        val profile = ProfileManager.get(app, uuid) ?: return@runCatching false
        if (activeId(app) == config.id) disconnect(app)
        ProfileManager.getInstance(app).removeProfile(app, profile)
        true
    }.getOrDefault(false)

    fun activeName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE_NAME, null)

    fun disconnect(context: Context): Boolean {
        val app = context.applicationContext
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                runCatching { IOpenVPNServiceInternal.Stub.asInterface(service).stopVPN(false) }
                runCatching { app.unbindService(this) }
                clearActive(app)
                VpnState.setDisconnected()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val bound = runCatching {
            app.bindService(Intent(app, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE),
                connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) {
            clearActive(app)
            VpnState.setDisconnected()
        }
        return bound
    }

    private fun activeId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE_ID, null)

    private fun clearActive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun parse(raw: String): VpnProfile {
        val parser = OvpnConfigParser()
        parser.parseConfig(StringReader(raw))
        return parser.convertProfile()
    }

    private const val PREFS = "ghajar_ovpn_state"
    private const val KEY_ACTIVE_ID = "active_id"
    private const val KEY_ACTIVE_NAME = "active_name"
}
