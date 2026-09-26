# Multi-core integration report

Date: 2026-09-26 · Branch `claude/website-design-project-files-lahgbm` · Base commit `a5e4bc93`

Status words mean exactly this:

- **Shipped**: in the APK and used by a connect path that existed before this work.
- **Implemented**: new code in this work, compiled in CI and covered by unit tests.
- **Experimental**: present in the APK but not wired to a connect path, or not
  proven on a device. Off by default.
- **Planned**: feasible, with a concrete integration method; not built yet.
- **Blocked**: cannot be done reproducibly or legally as things stand; reason given.
- **Not verified**: no real device test was possible in this environment (a
  cloud container with no Android device or emulator). **This applies to every
  runtime claim below.** CI compiles and runs JVM unit tests; it does not
  connect to anything.

## Phase 0 — baseline

| Item | Value |
|---|---|
| Branch / commit | `claude/website-design-project-files-lahgbm` @ `59178909` (after the renewal fix) |
| Gradle | 9.4.1 (wrapper) |
| AGP | 9.2.1 |
| Kotlin | 2.2.10, Compose BOM 2026.02.01 |
| minSdk / targetSdk | 26 / 36 |
| ABIs shipped | `arm64-v8a`, `armeabi-v7a` (ABI splits); engine AAR also carries x86/x86_64 |
| NDK | from CI env `NDK_VERSION` |
| package / applicationId | namespace `net.gozar.app`, applicationId `com.ghajarvpn.app` (unchanged) |
| Main core | Xray-core v1.260327.0 via gomobile AAR `app/libs/ca.psiphon.aar` (package `gozarcore`), 68.3 MB; `libgojni.so` 46.8 MB per ABI |
| Other cores | Psiphon (same AAR), OpenVPN (`:openvpn`), IKEv2 (`:strongswan`), Tor and Aether (executables), zeptun (JNI, CI-built), sing-box (executable, CI-built, unused) |
| TUN | Xray owns the tun for Xray profiles; zeptun routes proxy-only engines |
| Parsers / models | `ConfigParser` → `ProxyConfig`; `configtoolkit` (`FormatDetector`, `DecoderRegistry`, `NormalizedProfile`) |
| Debug artifact | `Ghajarvpn-Android-debug` 187.4 MB (CI run 36259137065, success) |

## Integration matrix

| Core | Repository | Status | Integration method | Licence | APK impact | Implemented? | Tested? | Known issues |
|---|---|---|---|---|---|---|---|---|
| Xray (current) | XTLS/Xray-core v1.260327.0 | Shipped | gomobile AAR | MPL-2.0 | 46.8 MB/ABI (`libgojni.so`, shared with Psiphon) | yes (existing) | CI build ✅, device: Not verified here | — |
| ZedSecure Xray: AutoSelect | CluvexStudio/Xray-core `zedsecure` `proxy/autoselect` + AndroidLibXrayLite `AutoSelectStatus/NetworkChanged/Pin` | **Blocked** | would replace the Xray in the AAR | MPL-2.0 / LGPL-3.0 | ≈ +0 (same binary) | no | no | AndroidLibXrayLite `go.mod` replaces 8 modules with local paths that are not published: `../xray-core`, `../../reference/psiphon-tunnel-core`, `.../dtls`, `../qpack-patched`, `.../slipnet-src/noizdns`, `.../slipnet-src/vaydns-mobile`, `.../MasterDnsVPN`, `.../slipnet-src/dnstt`, `.../vaydns`, `.../amneziawg-go`. No reproducible build is possible from public sources. Ghajar's own `AutoSelector.kt` remains in use. |
| ZedSecure Xray: sing-box outbound | same (`proxy/singbox`) | **Blocked** | same | MPL-2.0 + sing-box GPL-3 | large (+sing-box in-process) | no | no | same dependency problem; sing-box is already available as a separate executable |
| ZedSecure Xray: AmneziaWG | same (`amneziawg-go/v3` replaced by local path) | **Blocked** | same | MIT (amneziawg-go) | small | no | no | the pinned `amneziawg-go` is a local, unpublished checkout |
| sing-box | SagerNet/sing-box @ 8330820 | Experimental | executable `libsingbox.so` (CI) | GPL-3.0-or-later | tens of MB (built tags: quic, wireguard, utls, openconnect, openvpn, clash_api) | binary only; listed by CoreManager, flag `singbox_engine` off | CI build only | no connect path yet |
| Psiphon | Psiphon-Labs upstream (in AAR) | Shipped | gomobile AAR | GPL-3.0 | shared | existing | Not verified here | Cluvex `shirokhorshid` fork not adopted |
| Psiphon fallback | — | Planned | CoreManager fallback, opt-in `psiphon_fallback` | — | 0 | flag only | no | never automatic without the user's setting |
| OpenVPN | ics-openvpn (`:openvpn`) | Shipped | Gradle module | **GPL-2.0-only** | existing | existing | Not verified here | licence incompatibility with GPL-3 (pre-existing) — see licence report |
| sing-openvpn | CluvexStudio/sing-openvpn | Not adopted | Go executable | GPL-3.0 | — | no | no | young project; the shipped OpenVPN plus sing-box `with_openvpn` cover `.ovpn` more safely |
| IKEv2 / IPsec | strongSwan (`:strongswan`) | Shipped | Gradle module | GPL-2.0-or-later | existing | existing | Not verified here | — |
| zeptun | Noisemux/zeptun @ 2798fc0 | Shipped (optional) | JNI, CI-built | MIT | ~1–2 MB/ABI | existing | Not verified here | used only for proxy-only engines; Xray keeps its own tun (flag `zeptun_for_xray` off) |
| Aether (MASQUE/WARP) | CluvexStudio/Aether (`native/Aether`) | Experimental | separate executable, SOCKS5 | **AGPL-3.0** | executable | existing | Not verified here | keep unmodified, separate, with source offer (feasibility in the licence report) |
| Tor | tor | Shipped | executable | BSD-3 | existing | existing | Not verified here | — |
| DNSTT | upstream dnstt / dnstt-xyz | Planned | executable `libdnstt.so` (controller exists, binary not built) | CC0 | small | controller only | no | CoreManager reports "missing" honestly |
| MasterDnsVPN | Hidden-Node/MasterDnsVPN-AndroidClient @ 17459d9 | Planned | Go **executable** (`cmd/client`), SOCKS → zeptun | MIT | est. 10–15 MB/ABI | no | no | its gomobile AAR cannot be a second AAR in the same process |
| CottenDNS | WhiteDNS/CottenDns @ cdf084f | Planned | `scripts/build-android-client.sh` → `libcottendns_client.so` (16 KB aligned) | MIT | est. 10 MB/ABI | no | no | — |
| VayDNS | net2share/vaydns @ a0ff701 | Planned | Go executable from `vaydns-client`; UDP/DoH/DoT transports in `client/` | CC0 | est. 8 MB/ABI | no | no | — |
| OpenConnect | gitlab openconnect (LGPL-2.1); OConnect GPL-2+ as reference only | Planned | `libopenconnect` + GnuTLS/libxml2/stoken as `.so`, own JNI wrapper, `OpenConnectEngine` owning the tun | LGPL-2.1 | est. 5–8 MB/ABI | no | no | sing-box `with_openconnect` is an alternative once sing-box has a connect path |
| SSTP | ZedPass → `sstp_flutter` 1.3.0 (BSD-3), pure-Kotlin SSTP/PPP | Planned | new `:sstp` module (Kotlin, no native code) | BSD-3 | < 1 MB | no | no | written for Flutter; its Android part is plain Kotlin and portable |
| nDPI | CluvexStudio/nDPI | Later | debug-only `.so` | LGPL-3.0 | several MB | no | no | must not run in the release data path |
| NPVS / NPVO1 import | FrontierTM/Pantegnos (MIT) | **Implemented** | Kotlin port `configtoolkit/NpvContainer.kt` | MIT | ~30 KB | yes | JVM unit tests; fixture verified against Pantegnos' own Go decoder | vendor app-key, recipient-key and NPVT1/NPVTSUB1 locked files are refused on purpose |

## What was built in this work

1. `fix:` a service bought at a marketplace shop now renews at that shop
   (the subscription's `market_sub.php?s=<id>` link identifies it).
2. `feat:` **NPVS/NPVO1 importer**. Opens NPVO1 open exports and NPVS v1/v5
   sealed with a passphrase the user was given (PBKDF2-HMAC-SHA256, HKDF-SHA256,
   ChaCha20-Poly1305 — the AEAD is implemented in Kotlin because the platform
   cipher needs API 28). The UI already prompts for the passphrase, keeps it in
   memory only and wipes it. Verified: the Kotlin output for a synthetic
   passphrase-sealed v5 file equals, character for character, what Pantegnos'
   `decryptNPVSGen2` produces; ChaCha20-Poly1305 matches the JDK both ways.
   **Security decision:** files sealed with NPV's embedded app key (whitebox),
   sealed to a recipient, or legacy NPVT1/NPVTSUB1 are **not** opened. That
   would use a key extracted from someone else's app to remove a lock the
   config's author chose. The user sees why.
3. `refactor:` **CoreManager** read-only registry (`engine/CoreManager.kt`) and
   `EngineFlags`; the diagnostics export now begins with the core list.
4. Log redaction now also removes `ssh://` credentials, PEM blocks, `.ovpn`
   inline `<key>/<cert>/<ca>/<tls-*>`, OpenConnect cookies and passphrases.

## Additional cores and protocols worth adding (search result)

Not in the request, found while surveying; none added yet:

| Protocol / core | Where it comes from | Note |
|---|---|---|
| TUIC v5, Hysteria2, AnyTLS, ShadowTLS v3, Snell, ShadowsocksR | sing-box (already built) | needs the sing-box connect path |
| NaïveProxy | sing-box `with_naive_outbound` (Chromium net stack) | large; left out of the build on purpose |
| Mieru | enfein/mieru (GPL-3), supported by mihomo | censorship-resistant, TCP/UDP |
| Juicity | juicity/juicity (AGPL-3) | QUIC-based; AGPL |
| WireGuard / AmneziaWG | wireguard-go (MIT), amneziawg-go (MIT) | AmneziaWG useful where plain WG is fingerprinted |
| obfs4 / Snowflake / WebTunnel (Tor PTs) | Tor Project (BSD) | would harden the existing Tor path |
| Cloak | cbeuw/Cloak (GPL-3) | plugin for Shadowsocks/OpenVPN |
| XHTTP / REALITY | Xray (shipped) | already supported |
| Outline (Shadowsocks + prefix) | Jigsaw outline-sdk (Apache-2.0) | small Go library, strong in Iran |

## Performance

Measured: debug artifact 187.4 MB (zip of per-ABI APKs); engine AAR 68.3 MB,
of which `libgojni.so` is 46.8 MB per ABI. RAM, CPU, start time, connect time
and battery could not be measured without a device: **Not verified**.

Recommendation: before adding the planned Go executables (each 8–15 MB per
ABI), move optional engines (sing-box, DNS tunnels, OpenConnect, Aether, Tor)
into **on-demand downloadable engine packs** verified by SHA-256 and signature,
the same checks the app update installer already uses. A Play-style dynamic
feature is not available for sideloaded GitHub releases; a separate native
package per engine is.

## Device test matrix (required before any engine is enabled)

For each engine: import, validation, connect, internet, DNS, disconnect,
reconnect, network change (Wi-Fi↔mobile), airplane mode, app restart, process
killed, screen off/on, IPv4, IPv6, leak check, 20 connect/disconnect cycles,
notification, Quick Settings tile, last server, foreground service, battery
optimisation, crash recovery.

**Status: not run in this work (no device available).** Every engine that is
new in this report stays off until it passes.

## Regression report

What changed in app behaviour: renewal routing (marketplace services only),
the NPVS import path, the diagnostics export header and extra redaction.
Nothing touched connect, disconnect, the tun, Xray configs, the store, settings
storage or the API.

| Area | Code changed? | CI compile | Unit tests | Device |
|---|---|---|---|---|
| Xray | no | ✅ | existing tests | Not verified here |
| Server selection | no | ✅ | — | Not verified here |
| VPN connect/disconnect | no | ✅ | VpnCommandCoordinatorTest | Not verified here |
| Reconnect | no | ✅ | — | Not verified here |
| Store | renewal routing only | ✅ | GhajarCommerceRulesTest | Not verified here |
| Settings | no | ✅ | — | Not verified here |
| Notifications | no | ✅ | GhajarNoticeBusTest | Not verified here |
| Quick Settings | no | ✅ | — | Not verified here |
| Free configs | no | ✅ | freecfg tests | Not verified here |
| Subscriptions | no | ✅ | — | Not verified here |
| API | no | ✅ | — | — |
| Existing user data | no migration, no schema change | ✅ | — | — |

## Commit strategy used

```
fix: renew a marketplace service at its own shop, not the Ghajar shop
feat: npvs/npvo1 importer (passphrase and open exports, Pantegnos MIT port)
refactor: introduce read-only vpn engine registry (CoreManager) and harden log redaction
docs: multi-core integration report, engine architecture, core licence audit
```

Each is independently revertible (`git revert <sha>`); none migrates data.

## Next steps, in order

1. Resolve the ics-openvpn licence question (owner decision).
2. Device-test the existing cores through the matrix above and record results.
3. sing-box connect path (SOCKS → zeptun) behind `singbox_engine`, test, then enable.
4. DNS engines as executables: MasterDnsVPN → CottenDNS → VayDNS → dnstt.
5. OpenConnect via `libopenconnect` JNI.
6. ZedSecure AutoSelect/AmneziaWG once its dependencies are pinned to public commits.
7. `:sstp` module from the BSD-3 Kotlin SSTP client.
