package net.gozar.app

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

enum class PerAppMode { OFF, ALLOWLIST, BLOCKLIST }
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
class ConfigStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("gozarnet", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val writeDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "gozar-config-io") }.asCoroutineDispatcher()

    private val loadedSignal = CompletableDeferred<Unit>()

    suspend fun awaitReady() = loadedSignal.await()

    private val _configs = MutableStateFlow<List<ProxyConfig>>(emptyList())
    val configs: StateFlow<List<ProxyConfig>> = _configs.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    init {
        scope.launch {
            val cfgs = loadConfigs()
            val subs = loadSubscriptions()
            _configs.value = cfgs
            _subscriptions.value = subs
            loadedSignal.complete(Unit)
        }
    }

    private val _fragment = MutableStateFlow(prefs.getBoolean(KEY_FRAGMENT, false))
    val fragment: StateFlow<Boolean> = _fragment.asStateFlow()

    private val _fragmentPackets = MutableStateFlow(prefs.getString(KEY_FRAG_PACKETS, "tlshello") ?: "tlshello")
    val fragmentPackets: StateFlow<String> = _fragmentPackets.asStateFlow()
    fun setFragmentPackets(v: String) {
        _fragmentPackets.value = v
        prefs.edit().putString(KEY_FRAG_PACKETS, v).apply()
    }

    private val _fragmentLength = MutableStateFlow(prefs.getString(KEY_FRAG_LENGTH, "10-20") ?: "10-20")
    val fragmentLength: StateFlow<String> = _fragmentLength.asStateFlow()
    fun setFragmentLength(v: String) {
        _fragmentLength.value = v
        prefs.edit().putString(KEY_FRAG_LENGTH, v).apply()
    }

    private val _fragmentInterval = MutableStateFlow(prefs.getString(KEY_FRAG_INTERVAL, "10-20") ?: "10-20")
    val fragmentInterval: StateFlow<String> = _fragmentInterval.asStateFlow()
    fun setFragmentInterval(v: String) {
        _fragmentInterval.value = v
        prefs.edit().putString(KEY_FRAG_INTERVAL, v).apply()
    }

    /**
     * Switch to another server every N minutes, or 0 to never.
     *
     * Rotation is off by default and changes nothing until it is turned on. It
     * exists because a single endpoint used for hours is the easiest thing on a
     * network to notice and to throttle; moving between the servers that
     * already answered spreads that out. It never adds or removes a server -
     * it only changes which of yours is in use.
     */
    private val _rotateMinutes = MutableStateFlow(prefs.getInt(KEY_ROTATE_MINUTES, 0))
    val rotateMinutes: StateFlow<Int> = _rotateMinutes.asStateFlow()

    fun setRotateMinutes(value: Int) {
        val clean = value.coerceIn(0, 720)
        _rotateMinutes.value = clean
        prefs.edit().putInt(KEY_ROTATE_MINUTES, clean).apply()
    }

    /**
     * Route the whole device through a proxy-only engine, using zeptun.
     *
     * Only has any effect when the active engine is running in proxy mode
     * (Aether or Psiphon with routingMode = proxy), which today establishes no
     * tunnel at all. Off by default, and ignored entirely when the zeptun
     * libraries are not in the build - see ZeptunEngine.available.
     */
    private val _zeptunTunnel = MutableStateFlow(prefs.getBoolean(KEY_ZEPTUN, false))
    val zeptunTunnel: StateFlow<Boolean> = _zeptunTunnel.asStateFlow()

    fun setZeptunTunnel(enabled: Boolean) {
        _zeptunTunnel.value = enabled
        prefs.edit().putBoolean(KEY_ZEPTUN, enabled).apply()
    }

    /**
     * Stop the animations that never stop on their own.
     *
     * Nineteen infinite transitions redraw forever while their screen is up.
     * That is liveliness for most people and a problem for two groups: anyone
     * with vestibular sensitivity, for whom it is unusable, and anyone on a
     * weak phone, for whom it is heat and battery spent on decoration.
     */
    private val _reduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE_MOTION, false))
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun setReduceMotion(enabled: Boolean) {
        _reduceMotion.value = enabled
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()
    }

    /**
     * Take the accent colour from the system wallpaper (Material You).
     *
     * Only the accent, never the whole scheme: this app's palette carries
     * meaning - warning amber, error red, connected green - and handing all
     * of it to the wallpaper would turn an error message green on somebody's
     * phone. Off by default, and inert below Android 12.
     */
    private val _dynamicAccent = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_ACCENT, false))
    val dynamicAccent: StateFlow<Boolean> = _dynamicAccent.asStateFlow()

    fun setDynamicAccent(enabled: Boolean) {
        _dynamicAccent.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_ACCENT, enabled).apply()
    }

    /** One or two columns in the server list. */
    private val _listDensity = MutableStateFlow(readListDensity())
    val listDensity: StateFlow<ListDensity> = _listDensity.asStateFlow()

    private fun readListDensity(): ListDensity {
        val name = prefs.getString(KEY_LIST_DENSITY, null) ?: return ListDensity.ONE
        return runCatching { ListDensity.valueOf(name) }.getOrDefault(ListDensity.ONE)
    }

    fun setListDensity(density: ListDensity) {
        _listDensity.value = density
        prefs.edit().putString(KEY_LIST_DENSITY, density.name).apply()
    }

    /**
     * What the zeptun engine does with DNS queries entering its tun.
     *
     * Stored as the enum's own name so an unreadable or future value falls
     * back to FORWARD, which is what every build before this setting did.
     */
    private val _zeptunDns = MutableStateFlow(readZeptunDns())
    val zeptunDns: StateFlow<ZeptunEngine.DnsMode> = _zeptunDns.asStateFlow()

    private fun readZeptunDns(): ZeptunEngine.DnsMode {
        val name = prefs.getString(KEY_ZEPTUN_DNS, null) ?: return ZeptunEngine.DnsMode.FORWARD
        return runCatching { ZeptunEngine.DnsMode.valueOf(name) }
            .getOrDefault(ZeptunEngine.DnsMode.FORWARD)
    }

    fun setZeptunDns(mode: ZeptunEngine.DnsMode) {
        _zeptunDns.value = mode
        prefs.edit().putString(KEY_ZEPTUN_DNS, mode.name).apply()
    }

    /** The resolver hijacked queries are sent to. Only read in HIJACK mode. */
    private val _zeptunDnsUpstream =
        MutableStateFlow(prefs.getString(KEY_ZEPTUN_DNS_UPSTREAM, "").orEmpty())
    val zeptunDnsUpstream: StateFlow<String> = _zeptunDnsUpstream.asStateFlow()

    fun setZeptunDnsUpstream(value: String) {
        val clean = value.trim()
        _zeptunDnsUpstream.value = clean
        prefs.edit().putString(KEY_ZEPTUN_DNS_UPSTREAM, clean).apply()
    }

    /** How much of the phone the zeptun stack may spend on throughput. */
    private val _zeptunProfile = MutableStateFlow(readZeptunProfile())
    val zeptunProfile: StateFlow<ZeptunEngine.Profile> = _zeptunProfile.asStateFlow()

    private fun readZeptunProfile(): ZeptunEngine.Profile {
        val name = prefs.getString(KEY_ZEPTUN_PROFILE, null) ?: return ZeptunEngine.Profile.BALANCED
        return runCatching { ZeptunEngine.Profile.valueOf(name) }
            .getOrDefault(ZeptunEngine.Profile.BALANCED)
    }

    fun setZeptunProfile(profile: ZeptunEngine.Profile) {
        _zeptunProfile.value = profile
        prefs.edit().putString(KEY_ZEPTUN_PROFILE, profile.name).apply()
    }

    /**
     * Send YouTube straight out instead of through the tunnel.
     *
     * MahsaNG calls this Youtube Direct. It is a bandwidth decision rather
     * than a censorship one: video is the heaviest thing most people do, and a
     * server paying per gigabyte would rather not carry it. Off by default,
     * because where YouTube is blocked this makes it stop working.
     */
    private val _youtubeDirect = MutableStateFlow(prefs.getBoolean(KEY_YOUTUBE_DIRECT, false))
    val youtubeDirect: StateFlow<Boolean> = _youtubeDirect.asStateFlow()

    fun setYoutubeDirect(enabled: Boolean) {
        _youtubeDirect.value = enabled
        prefs.edit().putBoolean(KEY_YOUTUBE_DIRECT, enabled).apply()
    }

    /**
     * Junk packets sent ahead of the real traffic on the direct outbound.
     *
     * Xray's freedom outbound calls these noises, and MahsaNG exposes them as
     * a preset list. Blank - every existing config - emits no noises field.
     * See NoiseSpec for the format and what each preset is for.
     */
    private val _noiseSpec = MutableStateFlow(prefs.getString(KEY_NOISE_SPEC, "").orEmpty())
    val noiseSpec: StateFlow<String> = _noiseSpec.asStateFlow()

    fun setNoiseSpec(value: String) {
        val clean = value.trim()
        _noiseSpec.value = clean
        prefs.edit().putString(KEY_NOISE_SPEC, clean).apply()
    }

    private val _splitRouting = MutableStateFlow(prefs.getBoolean(KEY_SPLIT, false))
    val splitRouting: StateFlow<Boolean> = _splitRouting.asStateFlow()

    private val _sniffing = MutableStateFlow(prefs.getBoolean(KEY_SNIFFING, false))
    val sniffing: StateFlow<Boolean> = _sniffing.asStateFlow()
    fun setSniffing(enabled: Boolean) {
        _sniffing.value = enabled
        prefs.edit().putBoolean(KEY_SNIFFING, enabled).apply()
    }

    private val _coreLogLevel = MutableStateFlow(prefs.getString(KEY_CORE_LOG, "warning") ?: "warning")
    val coreLogLevel: StateFlow<String> = _coreLogLevel.asStateFlow()
    fun setCoreLogLevel(level: String) {
        _coreLogLevel.value = level
        prefs.edit().putString(KEY_CORE_LOG, level).apply()
    }

    private val _killSwitch = MutableStateFlow(prefs.getBoolean(KEY_KILL_SWITCH, false))
    val killSwitch: StateFlow<Boolean> = _killSwitch.asStateFlow()
    fun setKillSwitch(enabled: Boolean) {
        _killSwitch.value = enabled
        prefs.edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    private val _mux = MutableStateFlow(prefs.getBoolean(KEY_MUX, false))
    val mux: StateFlow<Boolean> = _mux.asStateFlow()
    fun setMux(enabled: Boolean) {
        _mux.value = enabled
        prefs.edit().putBoolean(KEY_MUX, enabled).apply()
    }

    private val _muxConcurrency = MutableStateFlow(prefs.getInt(KEY_MUX_CONCURRENCY, 8))
    val muxConcurrency: StateFlow<Int> = _muxConcurrency.asStateFlow()
    fun setMuxConcurrency(value: Int) {
        val v = value.coerceIn(1, 128)
        _muxConcurrency.value = v
        prefs.edit().putInt(KEY_MUX_CONCURRENCY, v).apply()
    }

    private val _sniffTypes = MutableStateFlow(loadSniffTypes())
    val sniffTypes: StateFlow<Set<String>> = _sniffTypes.asStateFlow()

    private fun loadSniffTypes(): Set<String> =
        prefs.getStringSet(KEY_SNIFF_TYPES, null)?.toSet() ?: setOf("http", "tls", "quic")

    fun toggleSniffType(type: String) {
        val cur = _sniffTypes.value.toMutableSet()
        if (!cur.add(type)) cur.remove(type)
        _sniffTypes.value = cur
        prefs.edit().putStringSet(KEY_SNIFF_TYPES, cur).apply()
    }

    private val _blockWhenOff = MutableStateFlow(prefs.getBoolean(KEY_BLOCK_WHEN_OFF, false))
    val blockWhenOff: StateFlow<Boolean> = _blockWhenOff.asStateFlow()

    fun setBlockWhenOff(enabled: Boolean) {
        _blockWhenOff.value = enabled
        prefs.edit().putBoolean(KEY_BLOCK_WHEN_OFF, enabled).apply()
    }

    /** VPN Share: exposes the same local SOCKS5 inbound the engine already
     * binds to 127.0.0.1 (see ConfigBuilder's socksIn) on 0.0.0.0 instead, so
     * devices on this phone's own hotspot can point their proxy settings at
     * it. Tearing down the tunnel tears down this listener with it - there is
     * no fallback path, so a dropped VPN fails shared clients closed rather
     * than leaking their traffic direct. */
    private val _vpnShareEnabled = MutableStateFlow(prefs.getBoolean(KEY_VPN_SHARE, false))
    val vpnShareEnabled: StateFlow<Boolean> = _vpnShareEnabled.asStateFlow()

    fun setVpnShareEnabled(enabled: Boolean) {
        _vpnShareEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VPN_SHARE, enabled).apply()
    }

    /** SOCKS5 credential Xray requires from every VPN-Share client. Without
     * this the shared inbound was a plain unauthenticated open proxy on the
     * hotspot subnet - anyone on the same Wi-Fi AP could use or sniff through
     * it. Generated once on first use and stored the same way other secrets
     * (KEY_CONFIGS/KEY_SUBS) already are; changing it forces every guest
     * device to re-enter the new value, which is the intended effect of the
     * "تولید مجدد" action in the share dialog. */
    private val _vpnShareUsername = MutableStateFlow(readSecret(KEY_VPN_SHARE_USER).orEmpty())
    val vpnShareUsername: StateFlow<String> = _vpnShareUsername.asStateFlow()

    private val _vpnSharePassword = MutableStateFlow(readSecret(KEY_VPN_SHARE_PASS).orEmpty())
    val vpnSharePassword: StateFlow<String> = _vpnSharePassword.asStateFlow()

    /** Returns the current credential, generating and persisting one first if
     * this is the first time VPN Share is used. */
    fun ensureVpnShareCredential(): Pair<String, String> {
        if (_vpnShareUsername.value.isNotBlank() && _vpnSharePassword.value.isNotBlank()) {
            return _vpnShareUsername.value to _vpnSharePassword.value
        }
        return regenerateVpnShareCredential()
    }

    fun regenerateVpnShareCredential(): Pair<String, String> {
        val user = "ghajar" + secureRandomToken(4)
        val pass = secureRandomToken(12)
        _vpnShareUsername.value = user
        _vpnSharePassword.value = pass
        scope.launch(writeDispatcher) {
            putSecretBlocking(KEY_VPN_SHARE_USER, user)
            putSecretBlocking(KEY_VPN_SHARE_PASS, pass)
        }
        return user to pass
    }

    private fun secureRandomToken(bytes: Int): String {
        val raw = ByteArray(bytes).also { java.security.SecureRandom().nextBytes(it) }
        return raw.joinToString("") { "%02x".format(it) }
    }

    private val _onionRouting = MutableStateFlow(prefs.getBoolean(KEY_ONION, false))
    val onionRouting: StateFlow<Boolean> = _onionRouting.asStateFlow()

    fun setOnionRouting(enabled: Boolean) {
        _onionRouting.value = enabled
        prefs.edit().putBoolean(KEY_ONION, enabled).apply()
    }

    private val _encryptedDns = MutableStateFlow(prefs.getBoolean(KEY_ENC_DNS, false))
    val encryptedDns: StateFlow<Boolean> = _encryptedDns.asStateFlow()

    fun setEncryptedDns(enabled: Boolean) {
        _encryptedDns.value = enabled
        prefs.edit().putBoolean(KEY_ENC_DNS, enabled).apply()
    }

    /**
     * A resolver chosen in the DNS lab, or blank for the built-in default.
     *
     * Additive on purpose: blank reproduces exactly what the tunnel published
     * before this existed (1.1.1.1 and 8.8.8.8, plus their DoH endpoints when
     * encrypted DNS is on). Setting it prepends the chosen resolver; the
     * defaults stay behind it as a fallback, so a resolver that stops
     * answering degrades instead of taking DNS down with it.
     */
    private val _customDns = MutableStateFlow(prefs.getString(KEY_CUSTOM_DNS, "").orEmpty())
    val customDns: StateFlow<String> = _customDns.asStateFlow()

    fun setCustomDns(value: String) {
        val clean = value.trim()
        _customDns.value = clean
        prefs.edit().putString(KEY_CUSTOM_DNS, clean).apply()
    }

    private val _fakeDns = MutableStateFlow(prefs.getBoolean(KEY_FAKE_DNS, false))
    val fakeDns: StateFlow<Boolean> = _fakeDns.asStateFlow()

    fun setFakeDns(enabled: Boolean) {
        _fakeDns.value = enabled
        prefs.edit().putBoolean(KEY_FAKE_DNS, enabled).apply()
    }

    private val _adBlock = MutableStateFlow(prefs.getBoolean(KEY_AD_BLOCK, false))
    val adBlock: StateFlow<Boolean> = _adBlock.asStateFlow()

    fun setAdBlock(enabled: Boolean) {
        _adBlock.value = enabled
        prefs.edit().putBoolean(KEY_AD_BLOCK, enabled).apply()
    }

    private val _mixedPort = MutableStateFlow(prefs.getInt(KEY_MIXED_PORT, 10626))
    val mixedPort: StateFlow<Int> = _mixedPort.asStateFlow()

    fun setMixedPort(port: Int) {
        val v = port.coerceIn(1024, 65535)
        _mixedPort.value = v
        prefs.edit().putInt(KEY_MIXED_PORT, v).apply()
        MixedPort.value = v
    }

    private val _sortMode = MutableStateFlow(
        // Default to fastest-first so a server's ping rank decides its position
        // within a subscription without the user having to find the sort toggle.
        prefs.getString(KEY_SORT_MODE, null)
            ?: if (prefs.getBoolean(KEY_SORT_SPEED, true)) SORT_FASTEST else SORT_ADDED
    )
    val sortMode: StateFlow<String> = _sortMode.asStateFlow()

    fun setSortMode(mode: String) {
        _sortMode.value = mode
        prefs.edit().putString(KEY_SORT_MODE, mode).apply()
    }

    private val _autoSelect = MutableStateFlow(prefs.getBoolean(KEY_AUTOSELECT, false))
    val autoSelect: StateFlow<Boolean> = _autoSelect.asStateFlow()

    fun setAutoSelect(enabled: Boolean) {
        _autoSelect.value = enabled
        prefs.edit().putBoolean(KEY_AUTOSELECT, enabled).apply()
    }

    /**
     * The autopilot card is flying the tunnel.
     *
     * Kept apart from [autoSelect] although engaging sets both. autoSelect is
     * the mechanism - a failover loop in the service - and it has always been
     * settable on its own from Settings. This is the user having handed the
     * choice of server over from the list, which is what the card reflects and
     * what a manual tap on a server has to be able to take back.
     */
    private val _autoPilot = MutableStateFlow(prefs.getBoolean(KEY_AUTOPILOT, false))
    val autoPilot: StateFlow<Boolean> = _autoPilot.asStateFlow()

    fun setAutoPilot(enabled: Boolean) {
        _autoPilot.value = enabled
        prefs.edit().putBoolean(KEY_AUTOPILOT, enabled).apply()
    }

    /**
     * Newest server at the top of the list, under the thumb.
     *
     * A config is added because it is about to be used, and the list is
     * ordered oldest-first, so the one just pasted in landed at the bottom of
     * thirty rows - the furthest point on the screen from where it was pasted.
     * Off by default: the existing order is what people have learnt.
     */
    private val _newestFirst = MutableStateFlow(prefs.getBoolean(KEY_NEWEST_FIRST, false))
    val newestFirst: StateFlow<Boolean> = _newestFirst.asStateFlow()

    fun setNewestFirst(enabled: Boolean) {
        _newestFirst.value = enabled
        prefs.edit().putBoolean(KEY_NEWEST_FIRST, enabled).apply()
    }

    /**
     * The DNS Tunnel profile.
     *
     * Stored as separate values rather than one blob because each is
     * independently missing in the common case: someone has a resolver and a
     * domain but no key yet, and a screen that can only save a complete
     * profile makes them retype the parts they already had.
     *
     * None of this is guessable. A resolver's IP alone cannot build a tunnel -
     * the domain names a zone whose nameserver is the tunnel server, and the
     * public key is what the client verifies the far end with. So the tunnel
     * is offered only when all three exist, and never reported as connected
     * on the strength of a resolver that merely answers.
     */
    private val _dnsTunnelDomain = MutableStateFlow(prefs.getString(KEY_DNSTT_DOMAIN, "") ?: "")
    val dnsTunnelDomain: StateFlow<String> = _dnsTunnelDomain.asStateFlow()

    fun setDnsTunnelDomain(value: String) {
        val v = value.trim().trim('.')
        _dnsTunnelDomain.value = v
        prefs.edit().putString(KEY_DNSTT_DOMAIN, v).apply()
    }

    /**
     * The tunnel server's public key, as the tunnel implementation prints it.
     *
     * Never defaulted and never generated here. A client that accepts any key
     * has no way to tell the tunnel server from whoever is between them, which
     * on the networks this app is used on is the whole threat.
     */
    private val _dnsTunnelKey = MutableStateFlow(prefs.getString(KEY_DNSTT_KEY, "") ?: "")
    val dnsTunnelKey: StateFlow<String> = _dnsTunnelKey.asStateFlow()

    fun setDnsTunnelKey(value: String) {
        val v = value.trim()
        _dnsTunnelKey.value = v
        prefs.edit().putString(KEY_DNSTT_KEY, v).apply()
    }

    /** Which imported resolver the tunnel sends its queries through. */
    private val _dnsTunnelResolver = MutableStateFlow(prefs.getString(KEY_DNSTT_RESOLVER, "") ?: "")
    val dnsTunnelResolver: StateFlow<String> = _dnsTunnelResolver.asStateFlow()

    fun setDnsTunnelResolver(id: String) {
        _dnsTunnelResolver.value = id
        prefs.edit().putString(KEY_DNSTT_RESOLVER, id).apply()
    }

    /** A name for the profile, so more than one can be told apart later. */
    private val _dnsTunnelName = MutableStateFlow(prefs.getString(KEY_DNSTT_NAME, "") ?: "")
    val dnsTunnelName: StateFlow<String> = _dnsTunnelName.asStateFlow()

    fun setDnsTunnelName(value: String) {
        _dnsTunnelName.value = value.trim()
        prefs.edit().putString(KEY_DNSTT_NAME, value.trim()).apply()
    }

    /**
     * Reconnect the tunnel by itself when the path drops.
     *
     * Off by default. An automatic reconnect that silently falls back to no
     * tunnel is worse than a visible failure, so this only ever retries the
     * tunnel - never a plain connection in its place.
     */
    private val _dnsTunnelAutoReconnect =
        MutableStateFlow(prefs.getBoolean(KEY_DNSTT_RECONNECT, false))
    val dnsTunnelAutoReconnect: StateFlow<Boolean> = _dnsTunnelAutoReconnect.asStateFlow()

    fun setDnsTunnelAutoReconnect(enabled: Boolean) {
        _dnsTunnelAutoReconnect.value = enabled
        prefs.edit().putBoolean(KEY_DNSTT_RECONNECT, enabled).apply()
    }

    /** True only when every part a tunnel cannot work without is present. */
    val dnsTunnelConfigured: Boolean
        get() = _dnsTunnelDomain.value.isNotBlank() &&
            _dnsTunnelKey.value.isNotBlank() &&
            _dnsTunnelResolver.value.isNotBlank()

    private val _autoRefreshHours = MutableStateFlow(prefs.getInt(KEY_AUTOREFRESH, DEFAULT_AUTOREFRESH))
    val autoRefreshHours: StateFlow<Int> = _autoRefreshHours.asStateFlow()

    fun setAutoRefreshHours(hours: Int) {
        _autoRefreshHours.value = hours
        prefs.edit().putInt(KEY_AUTOREFRESH, hours).apply()
    }

    private val _lang = MutableStateFlow(loadLang())
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private fun loadThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "DARK") }
            .getOrDefault(ThemeMode.DARK)

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private val _uiTheme = MutableStateFlow(loadUiTheme())
    val uiTheme: StateFlow<GhajarThemeId> = _uiTheme.asStateFlow()

    /**
     * Stored under its own key, so the older light/dark/amoled preference (and
     * everything else already saved) is left untouched. A fresh install has no
     * value and lands on Premium Green Dark; an existing install is carried
     * over from whatever it had chosen rather than being reset.
     */
    private fun loadUiTheme(): GhajarThemeId {
        prefs.getString(KEY_UI_THEME, null)?.let { return GhajarThemeId.parse(it) }
        return when (_themeMode.value) {
            ThemeMode.LIGHT -> GhajarThemeId.PREMIUM_GREEN_LIGHT
            ThemeMode.SYSTEM -> GhajarThemeId.SYSTEM
            else -> GhajarThemeId.PREMIUM_GREEN_DARK
        }
    }

    fun setUiTheme(theme: GhajarThemeId) {
        _uiTheme.value = theme
        prefs.edit().putString(KEY_UI_THEME, theme.name).apply()
        // Keep the legacy flag coherent for anything still reading it (and for
        // backups written by older builds).
        setThemeMode(
            when (theme) {
                GhajarThemeId.PREMIUM_GREEN_LIGHT -> ThemeMode.LIGHT
                GhajarThemeId.SYSTEM -> ThemeMode.SYSTEM
                else -> ThemeMode.DARK
            }
        )
    }

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED, null))
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun setSelectedId(id: String?) {
        _selectedId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun setLang(lang: Lang) {
        _lang.value = lang
        prefs.edit().putString(KEY_LANG, lang.name).apply()
    }

    private fun loadLang(): Lang {
        val saved = prefs.getString(KEY_LANG, null)
        return if (saved != null) {
            runCatching { Lang.valueOf(saved) }.getOrDefault(defaultLang())
        } else defaultLang()
    }

    private fun defaultLang(): Lang =
        if (java.util.Locale.getDefault().language == "fa") Lang.FA else Lang.EN

    fun setSplitRouting(enabled: Boolean) {
        _splitRouting.value = enabled
        prefs.edit().putBoolean(KEY_SPLIT, enabled).apply()
    }

    fun setFragment(enabled: Boolean) {
        _fragment.value = enabled
        prefs.edit().putBoolean(KEY_FRAGMENT, enabled).apply()
    }

    fun add(config: ProxyConfig) {
        _configs.value = _configs.value + config
        persistConfigs()
    }

    fun addToLocalSub(name: String, configs: List<ProxyConfig>) {
        if (configs.isEmpty()) return
        val existing = _subscriptions.value.firstOrNull { it.name == name && it.url.isBlank() }
        val sub = existing ?: Subscription(
            name = name,
            url = "",
            lastUpdated = System.currentTimeMillis()
        )
        if (existing == null) _subscriptions.value = listOf(sub) + _subscriptions.value
        _configs.value = _configs.value + configs.map { it.copy(subId = sub.id) }
        persistConfigs()
        persistSubscriptions()
    }

    fun update(config: ProxyConfig) {
        _configs.value = _configs.value.map { existing ->
            when {
                existing.id != config.id -> existing
                existing.locked -> existing.copy(name = config.name)
                else -> config
            }
        }
        persistConfigs()
    }

    fun addImported(imported: List<ProxyConfig>): Int {
        if (imported.isEmpty()) return 0
        _configs.value = _configs.value + imported
        persistConfigs()
        return imported.size
    }

    fun delete(id: String) {
        _configs.value = _configs.value.filterNot { it.id == id }
        if (_selectedId.value == id) setSelectedId(null)
        persistConfigs()
    }

    fun setFavorite(id: String, favorite: Boolean) {
        _configs.value = _configs.value.map { if (it.id == id) it.copy(favorite = favorite) else it }
        persistConfigs()
    }

    /**
     * One-time cleanup for installs from before the auto-seeded default
     * config was removed: deletes only the exact fingerprint that seeding
     * ever created (name, protocol, address and port all match, and it was
     * never converted to a real subscription/manual entry, i.e. subId is
     * still empty) so a user's own manually-added Aether config — even one
     * that happens to share the name — is never touched. The Aether engine,
     * manual "add config" flow, and any other Aether config remain fully
     * intact; only this exact seeded artifact is removed, and only once.
     */
    fun removeLegacyDefaultAetherSeed() {
        if (prefs.getBoolean(KEY_AETHER_SEED_CLEANED, false)) return
        prefs.edit().putBoolean(KEY_AETHER_SEED_CLEANED, true).apply()
        val before = _configs.value
        val after = before.filterNot {
            it.protocol == "aether" && it.name == "Aether (MASQUE)" &&
                it.address == "127.0.0.1" && it.port == 1819 && it.subId.isEmpty()
        }
        if (after.size == before.size) return
        if (_selectedId.value != null && after.none { it.id == _selectedId.value }) {
            setSelectedId(after.firstOrNull()?.id)
        }
        _configs.value = after
        persistConfigs()
    }

    /** Finds the singleton Psiphon config, creating it on first use. This is
     * lazy - created the first time the user opens the Psiphon hub - and
     * never auto-seeded into every install by default (Aether's own default
     * config used to be; that auto-seeding was removed, see
     * removeLegacyDefaultAetherSeed()). */
    fun ensurePsiphonConfig(): ProxyConfig {
        _configs.value.firstOrNull { it.protocol == "psiphon" }?.let { return it }
        val cfg = ProxyConfig(
            name = "Psiphon",
            protocol = "psiphon",
            address = "127.0.0.1",
            port = 0,
            psiphonMode = "auto",
            psiphonCountry = "",
            source = ConfigSource.COMMUNITY
        )
        _configs.value = _configs.value + cfg
        persistConfigs()
        return cfg
    }

    fun updatePsiphonSettings(id: String, mode: String, country: String) {
        _configs.value = _configs.value.map {
            if (it.id == id) it.copy(psiphonMode = mode, psiphonCountry = country) else it
        }
        persistConfigs()
    }

    fun upsertSubscription(sub: Subscription, fetched: List<ProxyConfig>) {
        val identity: (ProxyConfig) -> String = if (sub.url == FreeConfigs.SOURCE_URL) net.gozar.app.freecfg.FreeFeedRules::signature else ::sigOf
        val oldBySig = _configs.value.filter { it.subId == sub.id }
            .associateBy { identity(it) }.toMutableMap()
        val tagged = fetched.map { f ->
            val kept = oldBySig.remove(identity(f))
            f.copy(subId = sub.id, id = kept?.id ?: f.id)
        }
        _configs.value = _configs.value.filterNot { it.subId == sub.id } + tagged
        // A brand-new subscription goes to the very top of the list. An existing
        // subscription being refreshed (auto-refresh, manual update, quota sync)
        // keeps its current position instead of jumping around every refresh.
        val existingIndex = _subscriptions.value.indexOfFirst { it.id == sub.id }
        val withoutSub = _subscriptions.value.filterNot { it.id == sub.id }
        _subscriptions.value = if (existingIndex < 0) {
            listOf(sub) + withoutSub
        } else {
            withoutSub.toMutableList().apply { add(existingIndex.coerceAtMost(size), sub) }
        }
        persistConfigs()
        persistSubscriptions()
    }

    private fun sigOf(c: ProxyConfig): String =
        "${c.protocol}|${c.address}|${c.port}|${c.uuid}|${c.password}"

    fun renameSubscription(id: String, newName: String) {
        _subscriptions.value = _subscriptions.value.map { if (it.id == id) it.copy(name = newName) else it }
        persistSubscriptions()
    }

    fun deleteConfigsByIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        _configs.value = _configs.value.filterNot { it.id in ids }
        if (_selectedId.value in ids) setSelectedId(null)
        persistConfigs()
    }

    fun duplicateIds(): Set<String> {
        val seen = HashSet<String>()
        val dupes = LinkedHashSet<String>()
        _configs.value.forEach { c ->
            val key = listOf(
                c.protocol, c.address.trim().lowercase(), c.port.toString(),
                c.uuid, c.password, c.method, c.encryption, c.flow,
                c.alterId.toString(), c.network, c.security, c.sni,
                c.path, c.host, c.publicKey, c.shortId, c.serviceName,
                c.privateKey, c.localAddress, c.torCountry, c.aetherMode
            ).joinToString("\u0000")
            if (!seen.add(key)) dupes.add(c.id)
        }
        return dupes
    }

    fun deleteAllConfigs() {
        _configs.value = emptyList()
        _subscriptions.value = emptyList()
        setSelectedId(null)
        persistConfigs()
        persistSubscriptions()
    }

    fun settingsSnapshot(): JSONObject = JSONObject().apply {
        put("fragment", _fragment.value)
        put("rotateMinutes", _rotateMinutes.value)
        put("zeptunTunnel", _zeptunTunnel.value)
        // The tunnel's domain, resolver and name travel in a backup. Its
        // public key deliberately does not: a backup is a file that gets
        // shared, and the key is the one part of this profile that is a
        // credential. It is retyped on the new device.
        put("dnsTunnelDomain", _dnsTunnelDomain.value)
        put("dnsTunnelResolver", _dnsTunnelResolver.value)
        put("dnsTunnelName", _dnsTunnelName.value)
        put("dnsTunnelAutoReconnect", _dnsTunnelAutoReconnect.value)
        put("autoPilot", _autoPilot.value)
        put("newestFirst", _newestFirst.value)
        put("reduceMotion", _reduceMotion.value)
        put("dynamicAccent", _dynamicAccent.value)
        put("listDensity", _listDensity.value.name)
        put("zeptunDns", _zeptunDns.value.name)
        put("zeptunDnsUpstream", _zeptunDnsUpstream.value)
        put("zeptunProfile", _zeptunProfile.value.name)
        put("youtubeDirect", _youtubeDirect.value)
        put("noiseSpec", _noiseSpec.value)
        put("fragmentPackets", _fragmentPackets.value)
        put("fragmentLength", _fragmentLength.value)
        put("fragmentInterval", _fragmentInterval.value)
        put("splitRouting", _splitRouting.value)
        put("sniffing", _sniffing.value)
        put("sniffTypes", JSONArray(_sniffTypes.value.toList()))
        put("killSwitch", _killSwitch.value)
        put("mux", _mux.value)
        put("muxConcurrency", _muxConcurrency.value)
        put("blockWhenOff", _blockWhenOff.value)
        put("vpnShareEnabled", _vpnShareEnabled.value)
        put("onionRouting", _onionRouting.value)
        put("encryptedDns", _encryptedDns.value)
        put("fakeDns", _fakeDns.value)
        put("customDns", _customDns.value)
        put("adBlock", _adBlock.value)
        put("mixedPort", _mixedPort.value)
        put("sortMode", _sortMode.value)
        put("autoSelect", _autoSelect.value)
        put("autoRefreshHours", _autoRefreshHours.value)
        put("themeMode", _themeMode.value.name)
        put("uiTheme", _uiTheme.value.name)
        put("lang", _lang.value.name)
        put("perAppMode", _perAppMode.value.name)
        put("perAppList", JSONArray(_perAppList.value.toList()))
        put("selectedId", _selectedId.value ?: "")
    }

    fun restoreSettings(o: JSONObject) {
        if (o.has("fragment")) setFragment(o.getBoolean("fragment"))
        if (o.has("fragmentPackets")) setFragmentPackets(o.getString("fragmentPackets"))
        if (o.has("fragmentLength")) setFragmentLength(o.getString("fragmentLength"))
        if (o.has("fragmentInterval")) setFragmentInterval(o.getString("fragmentInterval"))
        if (o.has("splitRouting")) setSplitRouting(o.getBoolean("splitRouting"))
        if (o.has("sniffing")) setSniffing(o.getBoolean("sniffing"))
        o.optJSONArray("sniffTypes")?.let { arr ->
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            _sniffTypes.value = set
            prefs.edit().putStringSet(KEY_SNIFF_TYPES, set).apply()
        }
        if (o.has("killSwitch")) setKillSwitch(o.getBoolean("killSwitch"))
        if (o.has("mux")) setMux(o.getBoolean("mux"))
        if (o.has("muxConcurrency")) setMuxConcurrency(o.getInt("muxConcurrency"))
        if (o.has("blockWhenOff")) setBlockWhenOff(o.getBoolean("blockWhenOff"))
        if (o.has("vpnShareEnabled")) setVpnShareEnabled(o.getBoolean("vpnShareEnabled"))
        if (o.has("onionRouting")) setOnionRouting(o.getBoolean("onionRouting"))
        if (o.has("encryptedDns")) setEncryptedDns(o.getBoolean("encryptedDns"))
        if (o.has("fakeDns")) setFakeDns(o.getBoolean("fakeDns"))
        if (o.has("customDns")) setCustomDns(o.optString("customDns"))
        if (o.has("rotateMinutes")) setRotateMinutes(o.optInt("rotateMinutes", 0))
        if (o.has("zeptunTunnel")) setZeptunTunnel(o.getBoolean("zeptunTunnel"))
        if (o.has("dnsTunnelDomain")) setDnsTunnelDomain(o.optString("dnsTunnelDomain"))
        if (o.has("dnsTunnelResolver")) setDnsTunnelResolver(o.optString("dnsTunnelResolver"))
        if (o.has("dnsTunnelName")) setDnsTunnelName(o.optString("dnsTunnelName"))
        if (o.has("dnsTunnelAutoReconnect")) {
            setDnsTunnelAutoReconnect(o.getBoolean("dnsTunnelAutoReconnect"))
        }
        if (o.has("autoPilot")) setAutoPilot(o.getBoolean("autoPilot"))
        if (o.has("newestFirst")) setNewestFirst(o.getBoolean("newestFirst"))
        if (o.has("reduceMotion")) setReduceMotion(o.getBoolean("reduceMotion"))
        if (o.has("dynamicAccent")) setDynamicAccent(o.getBoolean("dynamicAccent"))
        if (o.has("listDensity")) runCatching {
            setListDensity(ListDensity.valueOf(o.optString("listDensity")))
        }
        // Each of these is read only when present, and an unreadable enum name
        // falls back to the setting's own default rather than failing the
        // restore: a backup written before they existed has to come back
        // exactly as it went in.
        if (o.has("zeptunDns")) runCatching {
            setZeptunDns(ZeptunEngine.DnsMode.valueOf(o.optString("zeptunDns")))
        }
        if (o.has("zeptunDnsUpstream")) setZeptunDnsUpstream(o.optString("zeptunDnsUpstream"))
        if (o.has("zeptunProfile")) runCatching {
            setZeptunProfile(ZeptunEngine.Profile.valueOf(o.optString("zeptunProfile")))
        }
        if (o.has("youtubeDirect")) setYoutubeDirect(o.getBoolean("youtubeDirect"))
        if (o.has("noiseSpec")) setNoiseSpec(o.optString("noiseSpec"))
        if (o.has("adBlock")) setAdBlock(o.getBoolean("adBlock"))
        if (o.has("mixedPort")) setMixedPort(o.getInt("mixedPort"))
        if (o.has("sortMode")) setSortMode(o.getString("sortMode"))
        if (o.has("autoSelect")) setAutoSelect(o.getBoolean("autoSelect"))
        if (o.has("autoRefreshHours")) setAutoRefreshHours(o.getInt("autoRefreshHours"))
        o.optString("themeMode").takeIf { it.isNotEmpty() }?.let { v ->
            runCatching { setThemeMode(ThemeMode.valueOf(v)) }
        }
        // Restored after themeMode so the newer setting wins; a backup from an
        // older build carries no uiTheme and keeps the migrated value.
        o.optString("uiTheme").takeIf { it.isNotEmpty() }?.let { v ->
            runCatching { setUiTheme(GhajarThemeId.parse(v)) }
        }
        o.optString("lang").takeIf { it.isNotEmpty() }?.let { v ->
            runCatching { setLang(Lang.valueOf(v)) }
        }
        o.optString("perAppMode").takeIf { it.isNotEmpty() }?.let { v ->
            runCatching { setPerAppMode(PerAppMode.valueOf(v)) }
        }
        o.optJSONArray("perAppList")?.let { arr ->
            setPerAppList((0 until arr.length()).map { arr.getString(it) }.toSet())
        }
    }

    fun restoreBackup(configs: List<ProxyConfig>, subs: List<Subscription>, settings: JSONObject?) {
        _configs.value = configs
        _subscriptions.value = subs
        persistConfigs()
        persistSubscriptions()
        settings?.let { restoreSettings(it) }
        val wanted = settings?.optString("selectedId").orEmpty()
        setSelectedId(if (configs.any { it.id == wanted }) wanted else configs.firstOrNull()?.id)
    }

    data class MergeReport(
        val addedConfigs: Int,
        val duplicateConfigs: Int,
        val addedSubscriptions: Int,
        val duplicateSubscriptions: Int
    )

    /**
     * Adds a backup's configs/subscriptions to whatever already exists,
     * instead of [restoreBackup]'s full replace: nothing already on the
     * device is deleted or overwritten, duplicates (by the same identity
     * [upsertSubscription] already uses for configs, and by URL for
     * subscriptions) are skipped rather than doubled, and settings/wallet
     * data are left untouched entirely (there is no server-authoritative
     * data in this local format to begin with — balance lives on the panel).
     */
    fun mergeBackup(configs: List<ProxyConfig>, subs: List<Subscription>): MergeReport {
        val existingConfigSigs = _configs.value.mapTo(HashSet(), ::sigOf)
        val newConfigs = configs.filter { sigOf(it) !in existingConfigSigs }
            .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
        val duplicateConfigs = configs.size - newConfigs.size

        val existingSubUrls = _subscriptions.value.mapTo(HashSet()) { it.url }
        val newSubs = subs.filter { it.url !in existingSubUrls }
            .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
        val duplicateSubs = subs.size - newSubs.size

        if (newConfigs.isNotEmpty()) _configs.value = _configs.value + newConfigs
        if (newSubs.isNotEmpty()) _subscriptions.value = _subscriptions.value + newSubs
        if (newConfigs.isNotEmpty()) persistConfigs()
        if (newSubs.isNotEmpty()) persistSubscriptions()

        return MergeReport(newConfigs.size, duplicateConfigs, newSubs.size, duplicateSubs)
    }

    fun deleteSubscription(id: String) {
        _configs.value = _configs.value.filterNot { it.subId == id }
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        persistConfigs()
        persistSubscriptions()
    }

    // persistConfigs()/persistSubscriptions() run after nearly every mutation,
    // many of them triggered directly from UI callbacks on the main thread
    // (add/delete/select, a subscription refresh replacing dozens of items).
    // JSON-serializing the whole list used to happen synchronously on
    // whichever thread called persist*() before handing only the final
    // string off to a background dispatcher for the actual disk write - for
    // a large config/subscription list that serialization itself was real,
    // measurable main-thread work. Both the serialization and the write now
    // happen off-thread; only capturing the current snapshot (a cheap
    // reference copy of an immutable list) stays on the caller's thread.
    private fun persistConfigs() {
        val snapshot = _configs.value
        scope.launch(writeDispatcher) {
            val arr = JSONArray()
            snapshot.forEach { arr.put(it.toJson()) }
            putSecretBlocking(KEY_CONFIGS, arr.toString())
        }
    }

    private fun persistSubscriptions() {
        val snapshot = _subscriptions.value
        scope.launch(writeDispatcher) {
            val arr = JSONArray()
            snapshot.forEach { arr.put(it.toJson()) }
            putSecretBlocking(KEY_SUBS, arr.toString())
        }
    }

    private fun putSecretBlocking(key: String, json: String) {
        prefs.edit().putString(key, Crypto.encrypt(json) ?: json).apply()
    }

    private fun readSecret(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        Crypto.decrypt(raw)?.let { return it }
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            Crypto.encrypt(raw)?.let { prefs.edit().putString(key, it).apply() }
            return raw
        }
        return null
    }

    private fun loadConfigs(): List<ProxyConfig> {
        val raw = readSecret(KEY_CONFIGS) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ProxyConfig.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private fun loadSubscriptions(): List<Subscription> {
        val raw = readSecret(KEY_SUBS) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Subscription.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private val _perAppMode = MutableStateFlow(loadPerAppMode())
    val perAppMode: StateFlow<PerAppMode> = _perAppMode

    private val _perAppList = MutableStateFlow(loadPerAppList())
    val perAppList: StateFlow<Set<String>> = _perAppList

    private fun loadPerAppMode(): PerAppMode =
        runCatching { PerAppMode.valueOf(prefs.getString(KEY_PERAPP_MODE, null) ?: "OFF") }
            .getOrDefault(PerAppMode.OFF)

    private fun loadPerAppList(): Set<String> =
        prefs.getStringSet(KEY_PERAPP_LIST, emptySet())?.toSet() ?: emptySet()

    fun setPerAppMode(mode: PerAppMode) {
        _perAppMode.value = mode
        prefs.edit().putString(KEY_PERAPP_MODE, mode.name).apply()
    }

    fun setPerAppList(pkgs: Set<String>) {
        _perAppList.value = pkgs
        prefs.edit().putStringSet(KEY_PERAPP_LIST, pkgs).apply()
    }

    fun togglePerApp(pkg: String) {
        val cur = _perAppList.value.toMutableSet()
        if (!cur.add(pkg)) cur.remove(pkg)
        setPerAppList(cur)
    }

    private val _expandedSubs = MutableStateFlow(loadExpandedSubs())
    val expandedSubs: StateFlow<Set<String>> = _expandedSubs

    private fun loadExpandedSubs(): Set<String> =
        prefs.getStringSet(KEY_EXPANDED_SUBS, emptySet())?.toSet() ?: emptySet()

    fun toggleSubExpanded(id: String) {
        val cur = _expandedSubs.value.toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        _expandedSubs.value = cur
        prefs.edit().putStringSet(KEY_EXPANDED_SUBS, cur).apply()
    }

    fun lastUpdateCheck(): Long = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)

    fun markUpdateChecked() {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    fun saveLastTest(json: String, timeMillis: Long) {
        prefs.edit().putString(KEY_LAST_TEST, json).putLong(KEY_LAST_TEST_TIME, timeMillis).apply()
    }

    fun lastTestJson(): String? = prefs.getString(KEY_LAST_TEST, null)

    fun lastTestTime(): Long = prefs.getLong(KEY_LAST_TEST_TIME, 0L)

    companion object {
        @Volatile private var instance: ConfigStore? = null

        fun get(context: Context): ConfigStore =
            instance ?: synchronized(this) {
                instance ?: ConfigStore(context.applicationContext).also { instance = it }
            }

        private const val KEY_CONFIGS = "configs"
        private const val KEY_SUBS = "subscriptions"
        private const val KEY_ZEPTUN = "zeptun_tunnel"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_DYNAMIC_ACCENT = "dynamic_accent"
        private const val KEY_LIST_DENSITY = "list_density"
        private const val KEY_ZEPTUN_DNS = "zeptun_dns_mode"
        private const val KEY_ZEPTUN_DNS_UPSTREAM = "zeptun_dns_upstream"
        private const val KEY_ZEPTUN_PROFILE = "zeptun_profile"
        private const val KEY_YOUTUBE_DIRECT = "youtube_direct"
        private const val KEY_NOISE_SPEC = "noise_spec"
        private const val KEY_ROTATE_MINUTES = "rotate_minutes"
        private const val KEY_FRAGMENT = "fragment_enabled"
        private const val KEY_FRAG_PACKETS = "fragment_packets"
        private const val KEY_FRAG_LENGTH = "fragment_length"
        private const val KEY_FRAG_INTERVAL = "fragment_interval"
        private const val KEY_SPLIT = "split_routing_enabled"
        private const val KEY_SNIFFING = "sniffing_enabled"
        private const val KEY_CORE_LOG = "core_log_level"
        private const val KEY_KILL_SWITCH = "kill_switch_enabled"
        private const val KEY_MUX = "mux_enabled"
        private const val KEY_MUX_CONCURRENCY = "mux_concurrency"
        private const val KEY_SNIFF_TYPES = "sniffing_types"
        private const val KEY_AUTOSELECT = "auto_select_fastest"
        private const val KEY_SORT_SPEED = "sort_by_speed"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_MIXED_PORT = "mixed_port"
        private const val KEY_AD_BLOCK = "ad_block"
        private const val KEY_CUSTOM_DNS = "custom_dns"
        private const val KEY_FAKE_DNS = "fake_dns"
        private const val KEY_ENC_DNS = "encrypted_dns"
        private const val KEY_ONION = "onion_routing"
        private const val KEY_BLOCK_WHEN_OFF = "block_when_off"
        private const val KEY_VPN_SHARE = "vpn_share_enabled"
        private const val KEY_VPN_SHARE_USER = "vpn_share_user"
        private const val KEY_VPN_SHARE_PASS = "vpn_share_pass"
        const val SORT_ADDED = "added"
        const val SORT_ALPHA = "alpha"
        const val SORT_FASTEST = "fastest"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_UI_THEME = "ui_theme"
        private const val KEY_DNSTT_DOMAIN = "dnstt_domain"
        private const val KEY_DNSTT_KEY = "dnstt_key"
        private const val KEY_DNSTT_RESOLVER = "dnstt_resolver"
        private const val KEY_DNSTT_NAME = "dnstt_name"
        private const val KEY_DNSTT_RECONNECT = "dnstt_reconnect"
        private const val KEY_AUTOPILOT = "auto_pilot"
        private const val KEY_NEWEST_FIRST = "newest_first"
        private const val KEY_AETHER_SEED_CLEANED = "aether_seed_cleaned_v1"
        private const val KEY_AUTOREFRESH = "auto_refresh_hours"
        private const val DEFAULT_AUTOREFRESH = 1
        private const val KEY_LANG = "app_lang"
        private const val KEY_SELECTED = "selected_config_id"
        private const val KEY_PERAPP_MODE = "perapp_mode"
        private const val KEY_PERAPP_LIST = "perapp_list"
        private const val KEY_EXPANDED_SUBS = "expanded_subs"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_LAST_TEST = "last_test_json"
        private const val KEY_LAST_TEST_TIME = "last_test_time"
    }
}