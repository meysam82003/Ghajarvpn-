# VPN engine architecture

## Today (baseline, commit 59178909 → 5cf73b78)

```
                        GozarVpnService (android.net.VpnService)
                                       │  one tun per connection
   ┌──────────────┬────────────────────┼──────────────────┬────────────────┐
   │              │                    │                  │                │
 Xray          Psiphon           Aether / Tor        OpenVPN           IKEv2/IPsec
 gozarcore     ca.psiphon         subprocess,        module :openvpn   module :strongswan
 (gomobile)    (same AAR)         local SOCKS5       (own VpnService)  (own VpnService)
 owns the tun  local SOCKS5            │
                  │                     │
                  └──── zeptun tun2socks (JNI) ◄── routes proxy-only cores device-wide
                                       
 Separate, not VpnService:  GhajarDnsOnlyService (DNS changer), DnsTunnelController (dnstt, binary absent)
 Built in CI but unused:    libsingbox.so (sing-box executable)
```

- **Xray** runs its own network stack on the tun fd (`Gozarcore.start(config, fd)`).
- **Psiphon, Aether, Tor** publish a local SOCKS5; in VPN mode they are routed
  through the tun by zeptun, in proxy mode no tun is created.
- **OpenVPN and IKEv2** are complete VPN clients in their own modules with their
  own `VpnService`s. Android lets only one VPN be active at a time;
  `VpnCommandCoordinator` serialises the app's own connect/disconnect commands
  so the latest request wins.

## Layer added now: `net.gozar.app.engine`

```
 UI ──► CoreManager (read-only registry)
          ├─ VpnEngine: id, displayName, capabilities, availability(ctx), isRunning()
          ├─ EngineCapabilities: protocols, ownsTun, providesSocks, license, integration
          ├─ Availability: Available | Experimental(why) | Missing(why)
          └─ EngineFlags: off-by-default switches (singbox_engine, zeptun_for_xray, psiphon_fallback)
```

It answers "what is in this build, what is running, under which licence" and
feeds the diagnostics export. It **does not start or stop anything**: every
connect path still goes through the code that is known to work. This is
Phase 1 exactly as the plan required — no regression is possible from it
because it only reads.

## Import layer

The requested `ImportManager` / `GhajarProfile` already exist under other names
and were extended rather than duplicated:

| Requested | In the code |
|---|---|
| ImportManager | `configtoolkit/DecoderRegistry` (+ `FormatDetector`) |
| GhajarProfile | `configtoolkit/NormalizedProfile` → `ProxyConfig` |
| Xray URI / JSON / subscription / QR | `ConfigParser`, `GenericJsonDecoder`, `SubscriptionFetcher`, QR scanner |
| NPVS / NPVO1 | `NpvsDecoder` → **`NpvContainer`** (new) |
| NPVT / NPVTSUB1 | `NpvtDecoder` (readable copies only; vendor-locked files reported as protected) |
| OVPN | OpenVPN module import |

## Target (later phases, each behind a flag)

```
 CoreManager.start(profile)            ← Phase 1b, only after device tests
   ├─ XrayEngine        (existing gozarcore; AutoSelect when a public, reproducible build exists)
   ├─ SingBoxEngine     (libsingbox.so, SOCKS → zeptun)
   ├─ PsiphonEngine     (existing; opt-in fallback via EngineFlags.PSIPHON_FALLBACK)
   ├─ OpenVpnEngine     (existing module)
   ├─ IkeEngine         (existing module)
   ├─ OpenConnectEngine (libopenconnect JNI — Phase 4)
   └─ DnsTunnelEngine   (dnstt | MasterDNS | CottenDNS | VayDNS executables → SOCKS → zeptun — Phase 6)
```

Rules for every new engine:

1. One process-level contract: either it owns the tun fd, or it exposes a
   local SOCKS5 and zeptun owns the tun. Never both, never two VpnServices.
2. A second **gomobile** AAR cannot coexist with the existing one in one
   process; Go engines ship as executables (`lib<name>.so`, run from
   `nativeLibraryDir`) or are compiled into the one AAR.
3. Every engine reports `Missing` when its binary is not in the APK;
   nothing claims to connect without a working binary.
4. Sockets of subprocess engines are excluded from the tun with the
   per-app/`protect()` mechanism the existing Aether/Tor paths use.
5. New engines start disabled (`EngineFlags`) until the device test matrix in
   `MULTICORE_INTEGRATION_REPORT.md` has passed for them.
