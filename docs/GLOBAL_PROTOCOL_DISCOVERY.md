# Global protocol discovery

Goal: the largest protocol coverage with the fewest, maintainable, reproducible
cores. CluvexStudio's repositories were one reference among many.

## Method

1. List what users in censored networks actually import (share links,
   subscriptions, app-specific config files) and what those resolve to on the
   wire.
2. For each wire protocol, find cores that implement it; prefer a core the app
   already ships (Tier A: Xray, sing-box, strongSwan, OpenVPN, Psiphon, Tor,
   Aether, zeptun).
3. A new core is added only if it is pinned (commit/tag/checksum), built from
   source in CI, licence-compatible, and covers something no Tier A core does.

## Finding: two cores cover almost everything

| Family | Covered by |
|---|---|
| V2Ray family (VLESS/VMess/Trojan/SS, REALITY, XHTTP, gRPC, WS, KCP), Hysteria 2, WireGuard | Xray (shipped) |
| QUIC family beyond Xray (TUIC, Hysteria v1), AnyTLS, Snell, SSH, OpenConnect (6 flavours) | sing-box (already built in CI; now wired) |
| DNS tunnels | dnstt (new, CC0, tiny) in front of sing-box |
| Enterprise VPN | strongSwan (IKEv2), ics-openvpn (OpenVPN), sing-box OpenConnect |
| Circumvention networks | Psiphon, Tor, Aether |

So the only new binary in this round is dnstt (a few MB); everything else was
unlocked by wiring the sing-box executable the CI already produced.

## Config ecosystems (apps whose files people share)

These are containers, not protocols. They are decoded by the import layer and
normalised to a `ProxyConfig`, which then goes to whichever engine speaks the
wire protocol inside.

| Ecosystem | What is inside | Handling |
|---|---|---|
| NapsternetV (.npvs/.npvt) | V2Ray links / JSON | open + passphrase decoded; app-key/recipient/vendor-locked → Locked |
| Happ | V2Ray links | plain decoded; `happ://crypt*` → Locked |
| HTTP Injector (.ehi), HA Tunnel (.hat), HTTP Custom, NetMod (.nm), Dark, SlipNet (.slip) | mostly SSH + payload/SNI/WS, sometimes V2Ray or DNS tunnels | readable copies imported; vendor-encrypted files → Locked (no vendor keys, no lock bypass) |

### SSH transport modes (what those apps carry)

| Mode | Status |
|---|---|
| SSH_DIRECT | Experimental — sing-box `ssh` outbound (`ssh://`) |
| SSH_DNS_TUNNEL_CHAIN | Experimental — `dnstt://…&upstream=ssh` |
| SSH_SOCKS_CHAIN | Planned — sing-box `detour` from ssh to a socks outbound |
| SSH_TLS_SNI, SSH_WSS, SSH_WEBSOCKET | Planned — ssh over a TLS / WS transport needs a detour outbound; sing-box has no raw-TLS or WS client outbound, so a small local shim is required |
| SSH_HTTP_PROXY_CONNECT | Planned — detour to an `http` outbound (CONNECT) |
| SSH_HTTP_PAYLOAD, SSH_PAYLOAD_PLUS_TLS | Planned — custom payload injection is not in any Tier A core; needs a dedicated shim |

## Candidates reviewed and not taken now

See ENGINE_MATRIX.md "Not integrated, with the reason". Short version:
unreproducible builds (ZedSecure Xray fork) are Blocked; everything else is
Planned behind the same contract (executable + local SOCKS5 + zeptun).
