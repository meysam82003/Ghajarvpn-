# `app/libs/ca.psiphon.aar` — provenance (1.1.1)

This file records what the bundled AAR is, as read from the artifact itself. Nothing here is inferred from its name.

| Item | Value |
|---|---|
| Path | `app/libs/ca.psiphon.aar` |
| SHA-256 | `213ce36cf9faa2f9149c568541ed178b987f83b5576719ff61b49fc8ad8805a1` |
| Size | 68,297,940 bytes |
| Entered the repo in | commit `c65a15c2` ("Consolidate canonical source from verified Release 1.0.0") |
| Java package | `gozarcore` (gomobile bind: `Gozarcore`, `Logger`, `PsiphonProvider`); `ca.psiphon.PsiphonTunnel` comes from the same native library |
| Native library | `jni/<abi>/libgojni.so` for arm64-v8a (46.8 MB), armeabi-v7a (44.7 MB), x86, x86_64. The APK ships only arm64-v8a and armeabi-v7a |
| Go toolchain (embedded build info) | go1.26.3 |
| psiphon-tunnel-core | `github.com/Psiphon-Labs/psiphon-tunnel-core v0.0.0 => /home/runner/work/_temp/psiphon-tunnel-core (devel)`, with its `third_party/quic-go-fork`. It was built on a GitHub Actions runner from a local checkout, and no upstream commit hash is embedded. The source tree in `native/Psiphon/` is the reference copy |
| Xray-core (same library) | `github.com/xtls/xray-core v1.260327.0` (Xray 26.3.27, the 1.1.1 baseline) |
| Licences | psiphon-tunnel-core GPL-3.0; Xray-core MPL-2.0 |

## Tunnel protocols the app offers

These are the protocols in `PsiphonConfig.NON_INPROXY_PROTOCOLS`. Each name was checked against the protocol strings inside `libgojni.so`, and all of them are present:

`SSH`, `OSSH`, `TLS-OSSH`, `UNFRONTED-MEEK-OSSH`, `UNFRONTED-MEEK-HTTPS-OSSH`, `UNFRONTED-MEEK-SESSION-TICKET-OSSH`, `QUIC-OSSH`, `SHADOWSOCKS-OSSH`, `FRONTED-MEEK-OSSH`, `FRONTED-MEEK-CDN-OSSH`, `FRONTED-MEEK-HTTP-OSSH`, `FRONTED-MEEK-CDN-HTTP-OSSH`, `FRONTED-MEEK-QUIC-OSSH`, `FRONTED-MEEK-CDN-QUIC-OSSH`.

You can pick the protocol in the Psiphon screen: automatic, CDN only, direct only, or one pinned protocol. Country selection comes from the engine's own `AvailableEgressRegions`.

Not offered:

- **INPROXY-WEBRTC-\*** (Conduit). This needs a server-entry signature key held by Ghajar, and this build does not have one. The UI says it is unavailable, and the in-proxy probabilities are fixed at 0.
- **TAPDANCE-OSSH / CONJURE-OSSH**. Their names appear in the library, but they depend on refraction-networking stations that this app neither configures nor has tested.

## Chains

In the Aether → Psiphon chain, Psiphon dials through Aether's SOCKS5 port, which carries no UDP. Any QUIC protocol pinned there is rejected before connecting (`PsiphonConfig.incompatibility`). In automatic mode the chain simply excludes QUIC.
