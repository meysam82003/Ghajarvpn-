package net.gozar.app.engine

import android.content.Context
import gozarcore.Gozarcore
import net.gozar.app.AetherController
import net.gozar.app.GhajarOpenVpnBridge
import net.gozar.app.IkeController
import net.gozar.app.PsiphonController
import net.gozar.app.TorController
import net.gozar.app.ZeptunEngine
import org.strongswan.android.logic.VpnStateService
import java.io.File

/**
 * One view over every connection core in this build.
 *
 * ## What this is, and deliberately not yet
 *
 * Phase 1 of docs/VPN_ENGINE_ARCHITECTURE.md. The existing cores keep being
 * started and stopped exactly where they were (GozarVpnService, the OpenVPN
 * module, the strongSwan module): each has a different contract - a tun fd,
 * a VpnService, a subprocess - and moving those calls without device testing
 * would risk the connections that work today. So this layer only *reads*:
 * which cores are really in this build, which one is running, what each can
 * carry, and under which licence. Starting through [VpnEngine] comes after it
 * has been proven on devices, behind [EngineFlags].
 *
 * Nothing here pretends. A core is [Availability.Available] only when its
 * code or binary is actually present in this APK.
 */
enum class EngineId { XRAY, PSIPHON, OPENVPN, IKEV2, TOR, AETHER, ZEPTUN_TUN, DNS_TUNNEL, SINGBOX }

sealed class Availability {
    object Available : Availability()
    /** Present, but not wired to a connect path yet or not verified on devices. */
    data class Experimental(val why: String) : Availability()
    data class Missing(val why: String) : Availability()
}

data class EngineCapabilities(
    val protocols: List<String>,
    /** Whether the core runs its own network stack on the VpnService tun. */
    val ownsTun: Boolean,
    /** Whether the core exposes a local SOCKS5 endpoint another tun engine can use. */
    val providesSocks: Boolean,
    val license: String,
    val integration: String
)

interface VpnEngine {
    val id: EngineId
    val displayName: String
    val capabilities: EngineCapabilities
    fun availability(context: Context): Availability
    fun isRunning(): Boolean
}

object CoreManager {

    val engines: List<VpnEngine> = listOf(
        engine(EngineId.XRAY, "Xray",
            EngineCapabilities(listOf("VLESS", "VMess", "Trojan", "Shadowsocks", "SOCKS", "HTTP", "Hysteria2", "WireGuard",
                "REALITY", "XHTTP", "gRPC", "WebSocket", "HTTPUpgrade", "KCP"),
                ownsTun = true, providesSocks = true, license = "MPL-2.0 (Xray-core)",
                integration = "gomobile AAR (app/libs/ca.psiphon.aar, package gozarcore)"),
            availability = { xrayAvailability() },
            running = { runCatching { Gozarcore.isRunning() }.getOrDefault(false) }),
        engine(EngineId.PSIPHON, "Psiphon",
            EngineCapabilities(listOf("Psiphon"), ownsTun = false, providesSocks = true,
                license = "GPL-3.0 (psiphon-tunnel-core)", integration = "same gomobile AAR (ca.psiphon.PsiphonTunnel)"),
            availability = { if (PsiphonController.available()) Availability.Available else Availability.Missing("ca.psiphon.PsiphonTunnel not in this build") },
            running = { PsiphonController.isRunning() }),
        engine(EngineId.OPENVPN, "OpenVPN",
            EngineCapabilities(listOf("OpenVPN (.ovpn)"), ownsTun = true, providesSocks = false,
                license = "GPL-2.0 (ics-openvpn module)", integration = "Gradle module :openvpn"),
            availability = { classPresent("de.blinkt.openvpn.core.OpenVPNService", "OpenVPN module") },
            running = { GhajarOpenVpnBridge.activeUuid.value != null }),
        engine(EngineId.IKEV2, "IKEv2 / IPsec",
            EngineCapabilities(listOf("IKEv2", "IPsec"), ownsTun = true, providesSocks = false,
                license = "GPL-2.0+ (strongSwan)", integration = "Gradle module :strongswan"),
            availability = { classPresent("org.strongswan.android.logic.CharonVpnService", "strongSwan module") },
            running = { IkeController.state.value.let { it == VpnStateService.State.CONNECTED || it == VpnStateService.State.CONNECTING } }),
        engine(EngineId.TOR, "Tor",
            EngineCapabilities(listOf("Tor", "Bridges: obfs4, meek_lite, webtunnel, snowflake, vanilla"), ownsTun = false, providesSocks = true,
                license = "BSD-3-Clause (tor, lyrebird)", integration = "bundled executable; lyrebird (liblyrebird.so) as ClientTransportPlugin"),
            availability = { ctx -> if (TorController.available(ctx)) Availability.Available else Availability.Missing("tor binary not in this build") },
            running = { TorController.isRunning() }),
        engine(EngineId.AETHER, "Aether (MASQUE / WARP)",
            EngineCapabilities(listOf("MASQUE HTTP/3", "MASQUE HTTP/2", "WARP"), ownsTun = false, providesSocks = true,
                license = "AGPL-3.0 (separate executable)", integration = "subprocess, local SOCKS5"),
            availability = { ctx ->
                if (AetherController.available(ctx)) Availability.Experimental("AGPL-3.0: runs as a separate program; see THIRD_PARTY_CORE_LICENSES.md")
                else Availability.Missing("aether binary not in this build")
            },
            running = { AetherController.isRunning() }),
        engine(EngineId.ZEPTUN_TUN, "zeptun tun2socks",
            EngineCapabilities(listOf("TUN → SOCKS5 (TCP/UDP/ICMP)"), ownsTun = true, providesSocks = false,
                license = "MIT", integration = "JNI (libzeptun.so, libzeptun-jni.so), used for proxy-only cores"),
            availability = { if (ZeptunEngine.available) Availability.Available else Availability.Missing(ZeptunEngine.loadError ?: "libzeptun not in this build") },
            running = { ZeptunEngine.isRunning }),
        engine(EngineId.DNS_TUNNEL, "DNS tunnels",
            EngineCapabilities(listOf("DNSTT (UDP/DoT/DoH)", "VayDNS", "NoizDNS", "Slipstream (QUIC over DNS)",
                "MasterDnsVPN", "StormDNS", "CottenDNS"), ownsTun = false, providesSocks = true,
                license = "CC0 (dnstt, VayDNS) · AGPL-3.0 (NoizDNS) · Apache-2.0 (Slipstream) · MIT (MasterDNS family)",
                integration = "separate executables (scripts/build-dnstt.sh, build-dns-tunnels.sh, build-slipstream.sh) -> sing-box -> zeptun"),
            availability = { ctx ->
                val present = listOf("libdnstt.so", "libvaydns.so", "libnoizdns.so", "libslipstream.so",
                    "libmasterdns.so", "libstormdns.so", "libcottendns.so").filter { nativeFile(ctx, it) }
                when {
                    present.isEmpty() -> Availability.Missing("no DNS tunnel client in this build")
                    !nativeFile(ctx, "libsingbox.so") -> Availability.Missing("sing-box (carries the tunnels) not in this build")
                    else -> Availability.Experimental("${present.size}/7 clients present; not device verified")
                }
            },
            running = { SingBoxController.isRunning() && SingBoxController.sidecarKind() in setOf("dnstt", "vaydns", "noizdns",
                "slipstream", "masterdns", "stormdns", "cottendns") }),
        engine(EngineId.SINGBOX, "sing-box",
            // Connect path: GozarVpnService EXTRA_SINGBOX -> SingBoxController (local SOCKS5) -> zeptun tun.
            // Protocol list = SingBoxConfig.PROTOCOLS, audited against the pinned source (v1.15.0-alpha.6);
            // ShadowsocksR is only a removed stub there and is not offered.
            EngineCapabilities(listOf("TUIC v5", "Hysteria (v1)", "AnyTLS", "SSH (direct, payload, HTTP/HTTPS proxy, TLS-SNI, payload+TLS, WS, WSS)",
                "Snell v4/v6", "OpenConnect (AnyConnect, GlobalProtect, Fortinet, F5, Pulse, NC)", "NaiveProxy (Cronet)", "ShadowTLS v1-3",
                "AmneziaWG 1.x/2.0", "Mieru", "Brook", "Juicity", "SSTP (PAP / MS-CHAPv2, crypto binding)", "SoftEther VPN protocol (Virtual Hub, DHCP / static)"),
                ownsTun = false, providesSocks = true, license = "GPL-3.0-or-later",
                integration = "executable libsingbox.so built in CI (scripts/build-singbox.sh), SOCKS5 -> zeptun"),
            availability = { ctx ->
                when {
                    !nativeFile(ctx, "libsingbox.so") -> Availability.Missing("libsingbox.so not in this build")
                    !ZeptunEngine.available -> Availability.Missing("zeptun (needed to carry sing-box) not in this build")
                    else -> Availability.Experimental("wired; not device verified")
                }
            },
            running = { SingBoxController.isRunning() })
    )

    fun engine(id: EngineId): VpnEngine = engines.first { it.id == id }

    fun engineFor(config: net.gozar.app.ProxyConfig): EngineId = EngineRouting.engineFor(config)

    // ---- Orchestration ----
    //
    // The existing, working connect paths (Xray in GozarVpnService, the
    // OpenVPN and strongSwan modules) are not rewritten: start/stop/reconnect
    // go through VpnLauncher, which is what the UI and AutoSelect already
    // use, and the service decides per engine. What is new is that every
    // engine answers the same questions before and while it runs.

    data class Status(
        val running: List<EngineId>,
        /** Helper engine in front of sing-box, if one is running. */
        val sidecar: String?,
        /** Local SOCKS5 port zeptun is carrying, when a proxy engine owns the session. */
        val socksPort: Int?
    )

    data class Stats(val uploadBytes: Long, val downloadBytes: Long, val source: String)

    /**
     * Everything that can be checked without connecting: the engine is in
     * this build, its helper binary is too, and the profile turns into a
     * configuration. Returns null when ready, or the reason it is not.
     */
    fun prepare(context: Context, config: net.gozar.app.ProxyConfig): String? {
        val id = engineFor(config)
        when (val a = runCatching { engine(id).availability(context) }.getOrElse { Availability.Missing(it.javaClass.simpleName) }) {
            is Availability.Missing -> return "${engine(id).displayName}: ${a.why}"
            else -> {}
        }
        if (id == EngineId.SINGBOX) {
            val spec = runCatching { SingBoxConfig.spec(config) }.getOrElse { return it.message ?: "incomplete profile" }
                ?: return "not a sing-box profile"
            val side = org.json.JSONObject(spec).optJSONObject("sidecar")
            if (side != null) {
                val launch = runCatching { Sidecars.launch(side) }.getOrElse { return it.message ?: "incomplete profile" }
                if (!nativeFile(context, launch.binary)) return "${launch.binary} is not in this build"
            }
        }
        return null
    }

    suspend fun start(context: Context, store: net.gozar.app.ConfigStore, config: net.gozar.app.ProxyConfig): net.gozar.app.LaunchOutcome =
        net.gozar.app.VpnLauncher.relaunch(context.applicationContext, store, config)

    fun stop(context: Context) {
        runCatching {
            context.startService(android.content.Intent(context, net.gozar.app.GozarVpnService::class.java)
                .setAction(net.gozar.app.GozarVpnService.ACTION_STOP))
        }
    }

    suspend fun reconnect(context: Context, store: net.gozar.app.ConfigStore, config: net.gozar.app.ProxyConfig) =
        start(context, store, config)

    /** A real test on the engine that carries [config]. Blocking. */
    fun test(config: net.gozar.app.ProxyConfig): EngineTestResult = EngineTester.test(config)

    fun status(): Status = Status(
        running = running(),
        sidecar = SingBoxController.sidecarKind(),
        socksPort = SingBoxController.socksPort.takeIf { SingBoxController.isRunning() && it > 0 }
    )

    fun stats(): Stats? {
        ZeptunEngine.counters()?.let { return Stats(it.rxBytes, it.txBytes, "zeptun") }
        return runCatching {
            if (!Gozarcore.isRunning()) null else Stats(Gozarcore.queryUplink(), Gozarcore.queryDownlink(), "xray")
        }.getOrNull()
    }

    /**
     * The device moved to another network. Subprocess engines are restarted
     * on the same local port (GozarVpnService also does this from its own
     * network callback); Xray, OpenVPN and IKEv2 handle it themselves.
     */
    fun networkChanged(context: Context): String? =
        if (SingBoxController.isRunning()) SingBoxController.reconnect(context) else null

    /** Which core is carrying traffic now, if any. */
    fun running(): List<EngineId> = engines.filter { runCatching { it.isRunning() }.getOrDefault(false) }.map { it.id }

    /** One line per core, for diagnostics and the integration report. No secrets. */
    fun report(context: Context): String = engines.joinToString("\n") { e ->
        val a = runCatching { e.availability(context) }.getOrElse { Availability.Missing(it.javaClass.simpleName) }
        val state = when (a) {
            Availability.Available -> "available"
            is Availability.Experimental -> "experimental: ${a.why}"
            is Availability.Missing -> "missing: ${a.why}"
        }
        "${e.displayName} [${e.capabilities.license}] — $state" + if (runCatching { e.isRunning() }.getOrDefault(false)) " — RUNNING" else ""
    }

    private fun xrayAvailability(): Availability = runCatching {
        val v = Gozarcore.xrayVersion()
        if (v.isNotBlank()) Availability.Available else Availability.Missing("empty version")
    }.getOrElse { Availability.Missing("gozarcore not loadable: ${it.javaClass.simpleName}") }

    private fun classPresent(name: String, what: String): Availability =
        if (runCatching { Class.forName(name) }.isSuccess) Availability.Available else Availability.Missing("$what not in this build")

    private fun nativeFile(context: Context, name: String): Boolean =
        runCatching { File(context.applicationInfo.nativeLibraryDir, name).exists() }.getOrDefault(false)

    private fun engine(
        id: EngineId, name: String, caps: EngineCapabilities,
        availability: (Context) -> Availability, running: () -> Boolean
    ): VpnEngine = object : VpnEngine {
        override val id = id
        override val displayName = name
        override val capabilities = caps
        override fun availability(context: Context) = availability(context)
        override fun isRunning() = running()
    }
}

/**
 * Switches for cores that are present but not proven on devices. All off by
 * default: turning one on is a decision, not an update side effect.
 */
object EngineFlags {
    private const val PREFS = "ghajar_engine_flags"
    const val SINGBOX = "singbox_engine"
    const val ZEPTUN_FOR_XRAY = "zeptun_for_xray"
    const val PSIPHON_FALLBACK = "psiphon_fallback"

    fun enabled(context: Context, flag: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(flag, false)

    fun set(context: Context, flag: String, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(flag, on).apply()
    }
}

/**
 * Which core carries a config when it is connected. This is the same
 * decision the launch sites make (MainActivity, QuickConnect, VpnLauncher
 * and GozarVpnService.switchTunnel), kept in one place for the UI filters
 * and the engine test. Free of Android and native references so it is
 * unit-testable.
 */
object EngineRouting {
    fun engineFor(config: net.gozar.app.ProxyConfig): EngineId = when {
        config.protocol == "ikev2" -> EngineId.IKEV2
        config.protocol == "openvpn" -> EngineId.OPENVPN
        SingBoxConfig.handles(config) -> EngineId.SINGBOX
        config.protocol == "aether" -> EngineId.AETHER
        config.protocol == "psiphon" && runCatching { net.gozar.app.OblivionOptions(config.oblivionJson).aether }.getOrDefault(false) -> EngineId.AETHER
        config.protocol == "psiphon" -> EngineId.PSIPHON
        config.protocol == "tor" -> EngineId.TOR
        else -> EngineId.XRAY
    }
}
