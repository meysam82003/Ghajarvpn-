# Aether + Tor — current review supersedes the original design status

The phase-4 text below records the original design. The current implementation and limits are in [PHASE1_6_REMAINING_FA.md](../PHASE1_6_REMAINING_FA.md): Tor-over-Aether now has shared-session orchestration, while Aether-H2-over-Tor remains blocked by the pinned native API-front system-DNS path. Neither direction has Android device qualification in this work. Historical statements below about unwired controllers are not the current status.

## Original phase-4 design (historical)

No new Tor library or Aether build is required to express the TCP-compatible paths in the existing CLIs. This is a source-backed design; it has not been wired into the production controller. User-visible enabled choices must wait for lifecycle tests.

| Packet path (unambiguous order) | Existing source hook | Constraint |
|---|---|---|
| App → Tor SOCKS → Tor network over Aether SOCKS → Internet | Tor `Socks5Proxy`; Aether existing local SOCKS | Start Aether, verify real SOCKS, then bootstrap Tor. Tor transport must be compatible with an upstream proxy; do not combine blindly with a bridge that ignores that proxy. |
| App → Aether H2 SOCKS → Tor SOCKS → Tor network → Aether endpoint | Aether CLI `--upstream socks5://127.0.0.1:<TorPort>` | Tor is TCP-only. Restrict Aether to H2; do not advertise H3/WireGuard/UDP over this path. Bootstrap Tor before starting Aether. |

Use a single host VPN/TUN lease, with the final application-facing SOCKS as the only TUN target. Upstream controller lifetimes are session-owned, not globals inferred from booleans. Reserve distinct ports and private directories; validate a DAG with exactly one TUN owner. Stop in reverse start order; cancellation at any await tears down all acquired resources. Upstream death invalidates the downstream lease and blocks traffic under kill-switch policy. Never fall back to direct network on a chain error. Reconnect replaces one generation only after old resources are closed. Missing/failed prerequisite keeps the entire method unavailable.

Current `Torcontroller.kt` uses the fixed `BRIDGE_PORT` when `throughVpn` is true. Aether's `--upstream` exists, but the current controllers/service teardown paths were not built for two simultaneous standalone controller owners. Wiring just a proxy URL would therefore be incomplete. A future typed `ChainSession` must own both controllers and expose bootstrap/proxy/exit health separately. No menu item claims this design is connected today.
