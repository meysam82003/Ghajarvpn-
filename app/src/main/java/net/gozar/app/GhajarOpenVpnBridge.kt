package net.gozar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser as OvpnConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.StatusListener
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.StringReader
import java.security.MessageDigest
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

data class GhajarOvpnProfile(
    val uuid: String,
    val name: String,
    val host: String,
    val port: Int,
    val needsCredentials: Boolean
)

enum class GhajarOvpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class GhajarOvpnTestResult(
    val running: Boolean = false,
    val ok: Boolean? = null,
    val connectMs: Long? = null,
    val message: String = ""
)

object GhajarOpenVpnBridge {
    private val _pending = MutableStateFlow<PendingOpenVpnImport?>(null)
    val pending = _pending.asStateFlow()

    private val _status = MutableStateFlow(GhajarOvpnState.DISCONNECTED)
    val status = _status.asStateFlow()

    private val _activeUuid = MutableStateFlow<String?>(null)
    val activeUuid = _activeUuid.asStateFlow()

    private val _lastMessage = MutableStateFlow("")
    val lastMessage = _lastMessage.asStateFlow()

    private val _tests = MutableStateFlow<Map<String, GhajarOvpnTestResult>>(emptyMap())
    val tests = _tests.asStateFlow()

    private var statusListener: StatusListener? = null
    private var stateListener: VpnStatus.StateListener? = null
    private var requestedUuid: String? = null
    private var connectedConfirmed = false
    private var connectedAtElapsed = 0L
    private var explicitDisconnect = false
    private var requestStartedAt = 0L

    /** The raw management-interface state string of the current engine session. */
    @Volatile private var engineState: String = ""

    private fun engineReallyConnected(): Boolean = engineState == GhajarUiRules.OVPN_MANAGEMENT_CONNECTED_STATE

    fun initialize(context: Context) {
        val app = context.applicationContext
        if (statusListener == null) {
            statusListener = StatusListener().also { it.init(app) }
        }
        ensureStateListener()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.getSystemService(NotificationManager::class.java).createNotificationChannels(
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
            override fun updateState(
                state: String,
                logmessage: String,
                localizedResId: Int,
                level: ConnectionStatus,
                Intent: android.content.Intent?
            ) {
                engineState = state.trim()
                val text = logmessage.trim().ifBlank { state.trim() }
                if (text.isNotBlank()) _lastMessage.value = GhajarUiRules.ovpnEngineMessage(text)
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - requestStartedAt
                when (level) {
                    ConnectionStatus.LEVEL_CONNECTED -> {
                        val uuid = _activeUuid.value ?: requestedUuid
                        connectedConfirmed = true
                        connectedAtElapsed = now
                        requestedUuid = null
                        explicitDisconnect = false
                        _status.value = GhajarOvpnState.CONNECTED
                        if (uuid != null) {
                            if (VpnState.activeId.value != "ovpn:$uuid") VpnState.setConnecting("ovpn:$uuid")
                            VpnState.setConnected()
                        }
                    }
                    ConnectionStatus.LEVEL_AUTH_FAILED -> {
                        requestedUuid = null
                        connectedConfirmed = false
                        _activeUuid.value = null
                        _status.value = GhajarOvpnState.ERROR
                        _lastMessage.value = "نام کاربری یا رمز OpenVPN رد شد (AUTH_FAILED)"
                        if (ownsGlobalTunnel()) VpnState.setError(_lastMessage.value)
                    }
                    ConnectionStatus.LEVEL_NONETWORK,
                    ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> {
                        _status.value = GhajarOvpnState.ERROR
                        requestedUuid = null
                        connectedConfirmed = false
                        _activeUuid.value = null
                        if (ownsGlobalTunnel()) VpnState.setError(_lastMessage.value.ifBlank { "OpenVPN متصل نشد" })
                    }
                    ConnectionStatus.LEVEL_NOTCONNECTED -> {
                        val keepConnectedGrace = connectedConfirmed && now - connectedAtElapsed < 1_200L && !explicitDisconnect
                        _status.value = when {
                            explicitDisconnect -> GhajarOvpnState.DISCONNECTED
                            keepConnectedGrace -> GhajarOvpnState.CONNECTED
                            requestedUuid != null && elapsed in 0..7_999 -> GhajarOvpnState.CONNECTING
                            else -> GhajarOvpnState.DISCONNECTED
                        }
                        if (_status.value == GhajarOvpnState.DISCONNECTED) {
                            connectedConfirmed = false
                            requestedUuid = null
                            if (ownsGlobalTunnel()) VpnState.setDisconnected()
                        }
                    }
                    ConnectionStatus.LEVEL_START,
                    ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
                    ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET -> {
                        if (!connectedConfirmed && (requestedUuid != null || _activeUuid.value != null)) {
                            _status.value = GhajarOvpnState.CONNECTING
                        }
                    }
                    ConnectionStatus.LEVEL_VPNPAUSED -> {
                        connectedConfirmed = false
                        _status.value = GhajarOvpnState.DISCONNECTED
                        if (ownsGlobalTunnel()) VpnState.setDisconnected()
                    }
                    ConnectionStatus.UNKNOWN_LEVEL -> {
                        if (!connectedConfirmed && requestedUuid != null) _status.value = GhajarOvpnState.CONNECTING
                    }
                }
            }

            override fun setConnectedVPN(uuid: String?) {
                val clean = uuid?.takeIf { it.isNotBlank() }
                if (clean != null) {
                    // Late callbacks from a previous session must not re-own the tunnel
                    // when the user has already moved on to a different profile.
                    if (requestedUuid != null && clean != requestedUuid) return
                    _activeUuid.value = clean
                } else {
                    _activeUuid.value = null
                    if (connectedConfirmed) {
                        connectedConfirmed = false
                        _status.value = GhajarOvpnState.DISCONNECTED
                        if (ownsGlobalTunnel()) VpnState.setDisconnected()
                    }
                }
            }
        }
        VpnStatus.addStateListener(stateListener)
    }

    fun inspect(bytes: ByteArray): Result<PendingOpenVpnImport> = runCatching {
        val raw = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF')
        require(raw.contains("remote ", ignoreCase = true) || raw.contains("<connection>", ignoreCase = true)) {
            "فایل OVPN معتبر نیست"
        }
        val profile = parse(raw)
        val connection = profile.mConnections.firstOrNull()
        val host = connection?.mServerName.orEmpty()
        PendingOpenVpnImport(
            raw = raw,
            name = GhajarUiRules.ovpnDisplayName(host),
            host = host,
            port = connection?.mServerPort?.toIntOrNull() ?: 1194,
            needsCredentials = profile.isUserPWAuth() && (profile.mUsername.isNullOrBlank() || profile.mPassword.isNullOrBlank()),
            embeddedUsername = profile.mUsername.orEmpty(),
            embeddedPassword = profile.mPassword.orEmpty()
        )
    }

    fun offer(bytes: ByteArray): Result<PendingOpenVpnImport> = inspect(bytes).onSuccess { _pending.value = it }

    fun dismiss() { _pending.value = null }

    fun saveImported(
        context: Context,
        pending: PendingOpenVpnImport,
        username: String = "",
        password: String = ""
    ): Result<GhajarOvpnProfile> = runCatching {
        val app = context.applicationContext
        val profile = parse(pending.raw)
        profile.mName = GhajarUiRules.ovpnDisplayName(pending.host)
        if (username.isNotBlank()) profile.mUsername = username
        if (password.isNotBlank()) profile.mPassword = password
        profile.importedProfileHash = sha256(pending.raw)

        val manager = ProfileManager.getInstance(app)
        val existing = manager.getProfiles().firstOrNull {
            !it.importedProfileHash.isNullOrBlank() && it.importedProfileHash == profile.importedProfileHash
        }
        val target = existing ?: profile.also { manager.addProfile(it) }
        if (existing != null) {
            target.mName = profile.mName
            target.mConnections = profile.mConnections
            target.mAuthenticationType = profile.mAuthenticationType
            target.mCaFilename = profile.mCaFilename
            target.mClientCertFilename = profile.mClientCertFilename
            target.mClientKeyFilename = profile.mClientKeyFilename
            target.mTLSAuthFilename = profile.mTLSAuthFilename
            target.mUseTLSAuth = profile.mUseTLSAuth
            target.mDataCiphers = profile.mDataCiphers
            target.importedProfileHash = profile.importedProfileHash
            if (username.isNotBlank()) target.mUsername = username
            if (password.isNotBlank()) target.mPassword = password
        }
        ProfileManager.saveProfile(app, target)
        manager.saveProfileList(app)
        toUi(target)
    }

    suspend fun connect(
        context: Context,
        pending: PendingOpenVpnImport,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        saveImported(context, pending, username, password).mapCatching { saved ->
            _pending.value = null
            connectSaved(context, saved.uuid).getOrThrow()
        }
    }

    fun profiles(context: Context): List<GhajarOvpnProfile> = runCatching {
        ProfileManager.getInstance(context.applicationContext).getProfiles().map(::toUi).sortedBy { it.name }
    }.getOrDefault(emptyList())

    fun findProfile(context: Context, uuid: String): VpnProfile? =
        ProfileManager.getInstance(context.applicationContext).getProfiles().firstOrNull { it.getUUIDString() == uuid }

    fun connectSaved(context: Context, uuid: String): Result<Unit> = runCatching {
        initialize(context)
        val app = context.applicationContext
        ensureOpenVpnServiceInstalled(app)
        val profile = findProfile(app, uuid) ?: throw IllegalStateException("پروفایل OVPN پیدا نشد؛ دوباره ایمپورتش کن.")
        require(profile.needUserPWInput(null, null) == 0) { "نام کاربری یا رمز عبور این پروفایل لازم است" }

        // A session that is already coming up for this profile must not be restarted;
        // two rapid start requests race the engine's management handshake otherwise.
        if (requestedUuid == uuid && _status.value == GhajarOvpnState.CONNECTING &&
            SystemClock.elapsedRealtime() - requestStartedAt < 6_000L
        ) return@runCatching

        // A different profile owning the tunnel must be stopped before a new engine
        // process starts, otherwise the old process fights the new one for the tun fd.
        if (requestedUuid != null && requestedUuid != uuid || engineReallyConnected() && _activeUuid.value != uuid) {
            runCatching { stopEngineNow(app) }
        }

        requestedUuid = uuid
        connectedConfirmed = false
        connectedAtElapsed = 0L
        explicitDisconnect = false
        engineState = ""
        requestStartedAt = SystemClock.elapsedRealtime()
        _activeUuid.value = uuid
        _status.value = GhajarOvpnState.CONNECTING
        _lastMessage.value = "در حال راه‌اندازی موتور OpenVPN…"
        VpnState.setConnecting("ovpn:$uuid")

        val intent = profile.getStartServiceIntent(app, "Ghajarvpn user request", true)
            ?: throw IllegalStateException("OpenVPN نتوانست Intent اتصال را بسازد")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
            else app.startService(intent)
        } catch (t: Throwable) {
            requestedUuid = null
            connectedConfirmed = false
            _activeUuid.value = null
            _status.value = GhajarOvpnState.ERROR
            _lastMessage.value = GhajarUiRules.ovpnEngineMessage(t.message ?: t.javaClass.simpleName)
            VpnState.setError(_lastMessage.value)
            throw IllegalStateException(_lastMessage.value, t)
        }
    }

    suspend fun testSaved(context: Context, uuid: String, timeoutMs: Long = 18_000L): GhajarOvpnTestResult = withContext(Dispatchers.IO) {
        if (_tests.value[uuid]?.running == true) return@withContext _tests.value[uuid]!!
        _tests.value = _tests.value + (uuid to GhajarOvpnTestResult(running = true, message = "در حال تست اتصال واقعی…"))
        val started = SystemClock.elapsedRealtime()
        val launch = connectSaved(context, uuid)
        if (launch.isFailure) {
            val result = GhajarOvpnTestResult(ok = false, message = launch.exceptionOrNull()?.message ?: "اتصال آغاز نشد")
            _tests.value = _tests.value + (uuid to result)
            return@withContext result
        }

        val final = withTimeoutOrNull(timeoutMs) {
            while (true) {
                when (_status.value) {
                    // Only the engine's own management handshake counts as CONNECTED;
                    // a TCP ping or a stale callback from a previous session never does.
                    GhajarOvpnState.CONNECTED -> if (engineReallyConnected()) return@withTimeoutOrNull GhajarOvpnState.CONNECTED
                    GhajarOvpnState.ERROR -> return@withTimeoutOrNull GhajarOvpnState.ERROR
                    GhajarOvpnState.DISCONNECTED -> if (SystemClock.elapsedRealtime() - started > 1800) {
                        return@withTimeoutOrNull GhajarOvpnState.DISCONNECTED
                    }
                    GhajarOvpnState.CONNECTING -> Unit
                }
                delay(120)
            }
            @Suppress("UNREACHABLE_CODE") GhajarOvpnState.ERROR
        }

        val result = if (final == GhajarOvpnState.CONNECTED) {
            val ms = SystemClock.elapsedRealtime() - started
            delay(350)
            explicitDisconnect = true
            disconnect(context)
            GhajarOvpnTestResult(ok = true, connectMs = ms, message = "اتصال واقعی موفق بود")
        } else {
            explicitDisconnect = true
            disconnect(context)
            GhajarOvpnTestResult(
                ok = false,
                message = if (final == null) "مهلت تست تمام شد" else _lastMessage.value.ifBlank { "OpenVPN متصل نشد" }
            )
        }
        _tests.value = _tests.value + (uuid to result)
        result
    }

    fun updateCredentials(context: Context, uuid: String, username: String, password: String): Result<Unit> = runCatching {
        val profile = findProfile(context, uuid) ?: throw IllegalStateException("پروفایل OVPN پیدا نشد.")
        if (username.isNotBlank()) profile.mUsername = username
        if (password.isNotBlank()) profile.mPassword = password
        val manager = ProfileManager.getInstance(context.applicationContext)
        ProfileManager.saveProfile(context.applicationContext, profile)
        manager.saveProfileList(context.applicationContext)
    }

    fun delete(context: Context, uuid: String): Result<Unit> = runCatching {
        // The engine serialises the profile on connect; deleting the active profile
        // while a session is coming up leaves the service holding a dangling UUID.
        if (requestedUuid == uuid || _activeUuid.value == uuid) {
            runCatching { stopEngineNow(context.applicationContext) }
            engineState = ""
            requestedUuid = null
            connectedConfirmed = false
            _activeUuid.value = null
            _status.value = GhajarOvpnState.DISCONNECTED
        }
        val profile = findProfile(context, uuid) ?: throw IllegalStateException("پروفایل OVPN پیدا نشد.")
        ProfileManager.getInstance(context.applicationContext).removeProfile(context.applicationContext, profile)
        _tests.value = _tests.value - uuid
    }

    /** Bind to the engine once, call stopVPN and release. Safe to call repeatedly. */
    private fun stopEngineNow(app: android.content.Context) {
        val latch = CountDownLatch(1)
        var service: IOpenVPNServiceInternal? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IOpenVPNServiceInternal.Stub.asInterface(binder)
                try { service?.stopVPN(false) } catch (_: Exception) {}
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { latch.countDown() }
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

    suspend fun disconnect(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext
            ensureOpenVpnServiceInstalled(app)
            explicitDisconnect = true
            stopEngineNow(app)
            engineState = ""
            requestedUuid = null
            connectedConfirmed = false
            _activeUuid.value = null
            _status.value = GhajarOvpnState.DISCONNECTED
            if (VpnState.activeId.value.orEmpty().startsWith("ovpn:")) VpnState.setDisconnected()
            explicitDisconnect = false
        }
    }

    private fun ensureOpenVpnServiceInstalled(context: Context) {
        val component = ComponentName(context, OpenVPNService::class.java)
        val found = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        }.isSuccess
        require(found) {
            "موتور OpenVPN داخل APK ثبت نشده است (${component.className})"
        }
    }

    private fun ownsGlobalTunnel(): Boolean = VpnState.activeId.value.orEmpty().startsWith("ovpn:")

    private fun toUi(profile: VpnProfile): GhajarOvpnProfile {
        val host = profile.mConnections.firstOrNull()?.mServerName.orEmpty()
        return GhajarOvpnProfile(
            uuid = profile.getUUIDString(),
            name = GhajarUiRules.ovpnDisplayName(host),
            host = host,
            port = profile.mConnections.firstOrNull()?.mServerPort?.toIntOrNull() ?: 1194,
            needsCredentials = profile.isUserPWAuth() && (profile.mUsername.isNullOrBlank() || profile.mPassword.isNullOrBlank())
        )
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun parse(raw: String): VpnProfile {
        val parser = OvpnConfigParser()
        parser.parseConfig(StringReader(raw))
        return parser.convertProfile()
    }
}
