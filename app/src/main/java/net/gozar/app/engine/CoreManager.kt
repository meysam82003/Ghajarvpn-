package net.gozar.app.engine

import android.content.Context
import gozarcore.Gozarcore
import net.gozar.app.AetherController
import net.gozar.app.DnsTunnelController
import net.gozar.app.DnsTunnelPhase
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
            EngineCapabilities(listOf("Tor"), ownsTun = false, providesSocks = true,
                license = "BSD-3-Clause (tor)", integration = "bundled executable"),
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
            availability = { if (ZeptunEngine.available) Availability.Available else Availability.Missing("libzeptun not in this build") },
            running = { ZeptunEngine.isRunning }),
        engine(EngineId.DNS_TUNNEL, "DNS tunnel (dnstt)",
            EngineCapabilities(listOf("DNSTT over UDP DNS / DoH / DoT"), ownsTun = false, providesSocks = true,
                license = "CC0 (dnstt)", integration = "subprocess libdnstt.so"),
            availability = { ctx -> if (DnsTunnelController.available(ctx)) Availability.Available else Availability.Missing("libdnstt.so not in this build") },
            running = { DnsTunnelController.phase.value.let { it == DnsTunnelPhase.LISTENING || it == DnsTunnelPhase.CARRYING || it == DnsTunnelPhase.STARTING } }),
        engine(EngineId.SINGBOX, "sing-box",
            EngineCapabilities(listOf("Hysteria2", "TUIC", "AnyTLS", "ShadowTLS", "Snell", "ShadowsocksR", "WireGuard", "OpenConnect (with_openconnect)", "OpenVPN (with_openvpn)"),
                ownsTun = false, providesSocks = true, license = "GPL-3.0-or-later",
                integration = "executable libsingbox.so built in CI (scripts/build-singbox.sh)"),
            availability = { ctx ->
                if (nativeFile(ctx, "libsingbox.so")) Availability.Experimental("built into the APK; no connect path uses it yet (EngineFlags.SINGBOX)")
                else Availability.Missing("libsingbox.so not in this build")
            },
            running = { false })
    )

    fun engine(id: EngineId): VpnEngine = engines.first { it.id == id }

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
