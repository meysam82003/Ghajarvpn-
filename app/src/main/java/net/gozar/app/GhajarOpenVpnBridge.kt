package net.gozar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser as OvpnConfigParser
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.StatusListener
import de.blinkt.openvpn.core.VPNLaunchHelper
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

    fun initialize(context: Context) {
        if (statusListener == null) {
            statusListener = StatusListener().also { it.init(context.applicationContext) }
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
            VPNLaunchHelper.startOpenVpn(profile, context.applicationContext, "Ghajarvpn user request", true)
            _pending.value = null
        }
    }

    private fun parse(raw: String): VpnProfile {
        val parser = OvpnConfigParser()
        parser.parseConfig(StringReader(raw))
        return parser.convertProfile()
    }
}
