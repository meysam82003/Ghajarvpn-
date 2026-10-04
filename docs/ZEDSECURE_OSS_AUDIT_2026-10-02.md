# ZedSecure OSS audit for Ghajar VPN

Date: 2026-10-02
Target branch: work/1.1.1-phase1
Reference repository: CluvexStudio/ZedSecure
Policy: reference/audit only for ZedSecure application code unless an explicit licensing decision is made.

## Executive result

ZedSecure becoming public is useful mainly as an architecture and capability reference. Ghajar already has a broader engine inventory than ZedSecure in several areas, so blindly porting ZedSecure would be a regression. The useful work is to close concrete runtime and UX gaps while preserving Ghajar's existing engines and GPL-3.0 licensing strategy.

Do not copy ZedSecure application code into Ghajar by default. ZedSecure is AGPL-3.0. Reimplement behavior against public upstream APIs/cores where possible.

## P0 — OpenConnect

Ghajar already builds sing-box with:
- with_openconnect
- with_openvpn
- with_quic
- with_wireguard
- with_utls
- with_tailscale
- with_naive_outbound

Pinned sing-box:
- commit 132b38e9caaba1a1959354d518e54d2d08419afe
- scripts/build-singbox.sh

Current Ghajar path:
- app/src/main/java/net/gozar/app/ConfigParser.kt
- app/src/main/java/net/gozar/app/ProtocolForms.kt
- app/src/main/java/net/gozar/app/ConfigShare.kt
- app/src/main/java/net/gozar/app/engine/SingBoxConfig.kt
- app/src/main/java/net/gozar/app/engine/SingBoxController.kt
- app/src/test/java/net/gozar/app/engine/SingBoxConfigTest.kt

Current support is real but incomplete compared with the public options exposed by the pinned sing-box OpenConnect endpoint.

Add support, using the pinned sing-box option/openconnect.go as the authority, for:
- cookie
- token modes: TOTP, HOTP, stoken, OIDC
- CA certificate
- client-key password
- DPD interval
- base MTU
- TCP keepalive
- compression controls
- HTTP keepalive disable
- XML POST disable
- external/password auth controls
- PFS / insecure legacy crypto switch
- version / local hostname
- form entries required by gateways with extra authentication fields

Keep client certificates, private keys, token secrets and cookies device-local by default. Do not put them into normal share links.

### Native OpenConnect finding

ZedSecure's native path uses:
- app/src/main/java/dev/cluvex/zedsecure/core/OpenConnectController.kt
- org.infradead.libopenconnect.LibOpenConnect
- libopenconnect.so

However, its build workflow pins CluvexStudio/Openconnect at be071398541d9b6bf5c29a50b41c489f2905db7c and explicitly reports that this patched fork is private when it cannot be cloned.

Therefore native OpenConnect from ZedSecure is NOT a reproducible public dependency today. Do not make it Ghajar's primary path. Keep Ghajar's public sing-box OpenConnect endpoint as the primary implementation. A native LGPL OpenConnect path can be added later from a fully public/reproducible source.

## P1 — DNS tunnel

Ghajar already has real sidecar runtime paths for:
- dnstt
- VayDNS
- NoizDNS
- MasterDNS
- StormDNS
- CottenDNS
- Slipstream

Relevant files:
- app/src/main/java/net/gozar/app/engine/SidecarRunner.kt
- app/src/main/java/net/gozar/app/engine/DnsTunnelPrefs.kt
- scripts/build-dnstt.sh
- scripts/build-dns-tunnels.sh
- scripts/build-slipstream.sh

ZedSecure 3.0.9+ uses its own zeddns engine. Useful behavior to reproduce independently in Ghajar:
- multiple resolvers per profile
- fan-out / all-resolvers mode
- round-robin mode
- per-query spread count
- resolver pool selection
- record type
- max QNAME length
- client ID size
- query-rate tuning
- idle timeout
- keepalive
- resolver timeout
- authoritative mode / payload size where the engine supports it
- explicit no-fallback policy

Do not copy zeddns source into Ghajar by default; it is part of the AGPL-3.0 ZedSecure codebase.

### Plain DNS-over-TCP

ZedSecure exposes plain TCP DNS transport through zeddns. Ghajar's currently pinned public dnstt/VayDNS sidecars expose UDP/DoT/DoH, not the same plain-TCP transport contract.

Treat plain TCP as a separate missing capability. Do not fake it in the UI. Add it only after an independent/public implementation exists and is covered by runtime tests.

## P2 — Engine chaining

ZedSecure models chaining explicitly:
- ProfileSource.ProxyChain(memberIds)
- ProfileSource.CrossChain(innerId, outerId)

Ghajar already has pieces:
- ProxyConfig.chainId
- Xray dialerProxy in ConfigBuilder
- Aether upstream support
- chain-aware engine testing

The gap is the normalized cross-engine model and validation layer.

Add a Ghajar chain model that:
1. stores carrier and exit profile IDs explicitly;
2. validates whether the carrier can relay the exit's required transport, especially UDP;
3. rejects recursive/self chains and missing members;
4. serializes/restores through backup;
5. exposes one start/stop/status contract through CoreManager;
6. keeps existing Xray chain behavior compatible.

Do not replace working Xray chaining; wrap it in the normalized model.

## P3 — Desktop architecture

ZedSecure demonstrates a good structural pattern:
- :shared common model/UI/domain module
- :desktop platform/runtime module
- Compose Desktop
- native distributions: DEB, RPM, AppImage, MSI, DMG
- System Proxy as default
- SOCKS-only mode
- TUN mode where supported
- platform-specific bundled binaries

For Ghajar, do NOT fork/copy the Android app into a separate desktop codebase.

Recommended migration:
1. extract pure models/parsers/config builders/settings contracts into a common module;
2. reuse protocol forms and config normalization;
3. create a desktop runtime adapter for Xray/sing-box and local SOCKS;
4. implement System Proxy first on Windows/macOS/Linux;
5. expose SOCKS-only mode;
6. add TUN per platform only after runtime verification;
7. package independently for MSI/DMG/DEB/RPM/AppImage.

Android-only features such as VpnService, per-app routing and Android notification/service code must stay behind the platform boundary.

## Licensing

ZedSecure application code: AGPL-3.0.
Ghajar application: GPL-3.0.

Default rule for this phase:
- use ZedSecure as a reference implementation;
- prefer public upstream projects and their own licenses;
- independently implement behavior in Ghajar;
- do not copy AGPL ZedSecure application source unless Ghajar intentionally accepts the resulting AGPL obligations.

Existing Ghajar licensing issue remains separate:
- the vendored ics-openvpn module is documented as GPL-2.0-only while Ghajar contains GPL-3.0 code.
- long-term options remain replacing it with the already-built sing-box OpenVPN path, isolating it as a separate plugin/distribution, or obtaining compatible licensing terms.

## Tests required before claiming completion

OpenConnect:
- link -> config round-trip
- endpoint vs outbound
- each flavor
- token object generation
- CA/client certificate/client key/key-password mapping
- form-entry mapping
- invalid/unsupported values rejected
- sing-box check
- real gateway test when credentials are available
- disconnect/reconnect/network change

DNS:
- each sidecar command contract
- multi-resolver strategy
- failure when binary missing
- listener readiness
- HTTPS-through-tunnel probe
- resolver failure/fallback policy
- reconnect and network change

Chaining:
- TCP carrier + TCP exit
- UDP-capable carrier + UDP exit
- invalid UDP carrier rejected before start
- missing member
- recursive chain
- backup/restore
- auto-select/test semantics

Desktop:
- bundled binary architecture validation
- process-exit propagation
- system proxy set/restore
- app shutdown restores proxy
- SOCKS-only leaves system proxy untouched
- TUN fallback behavior
- package smoke tests per operating system

## What NOT to do

- Do not replace Ghajar's broader engine inventory with ZedSecure's.
- Do not mark native OpenConnect complete from ZedSecure's private fork.
- Do not expose DNS-over-TCP until there is a real engine.
- Do not report Experimental features as device-verified.
- Do not trigger final Build/Release from this audit.
