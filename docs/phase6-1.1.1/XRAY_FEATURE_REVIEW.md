# Official source review and selection

Reviewed official repository `https://github.com/XTLS/Xray-core`, tags:

| Tag | Commit | Release status inspected |
|---|---|---|
| v26.3.27 | d2758a023cd7f4174a5a5fa4ff66e487d4342ba0 | stable |
| v26.7.28 | 5ca6f4b7d4dc20a881d4330e498892697627ec0c | prerelease |
| v26.9.30 | b26a91de4f3294e26a0ad0a970b81a386a41f789 | prerelease |

Source diff 3.27→7.28: 541 files, 34,413 insertions, 15,628 deletions. 3.27→9.30: 705 files, 63,961 insertions, 20,454 deletions. Diff size alone is not a defect or a performance measurement.

| Area | Source examined / evidence | Ghajar decision |
|---|---|---|
| MASQUE | `proxy/masque/client.go`, `infra/conf/masque.go`, `infra/conf/transport_method.go::MasqueConfig`, `transport/internet/masque/dialer.go`, `masque/connectip` | Real client CONNECT-IP using WireGuard netstack. H3 default; H2 selected when ALPN has h2 without h3. No automatic fallback claim. TLS config required; certificate validation inherited from TLS; SNI/serverName distinct from HTTP host. Basic user/pass or explicit valid headers, rejects conflicting Authorization, validates path templates. Experimental candidate; not exposed on old bundled core. Existing Aether unaffected. |
| REALITY | `infra/conf/transport_security.go`, `transport/internet/reality`; current and candidate generated profiles | Preserve pbk/sid/SNI/fingerprint/flow; fix spiderX in actual model/parser/generator/toolkit/UI. No forced key conversion/reset. |
| Shadowsocks 2022 | `infra/conf/shadowsocks.go`, `proxy/shadowsocks_2022`; 3 ciphers in official validation | Existing client support retained. No new protocol tile. Real server/key interoperability remains device/server acceptance. |
| TUN | `proxy/tun`, `gozarcore.go::Start/Stop` and XRAY_TUN_FD | Android supplied FD contract exists. New options/stack/lifecycle require native Android testing. `run -test` behavior differs between tags; host failure without /dev/net/tun is not an Android regression. |
| DNS/FakeDNS | `app/dns`, `app/dns/fakedns`, `infra/conf/dns.go`; DNS/DoH/FakeDNS cases | Existing config passes host validation. No automatic adoption of new defaults/cache behavior without device DNS tests. |
| Routing/GeoData | `app/router`, `app/geodata`, `infra/conf/router.go`; bundled geoip.dat/geosite.dat | Validate routing with actual Ghajar assets. No unsolicited geodata auto-download or new telemetry. |
| FinalMask/mKCP | 3.27 `infra/conf/transport_internet.go::KCPConfig/Original/Aes128Gcm`; 7.28/9.30 `transport_method.go`, `transport_finalmask.go::MkcpLegacy`; `finalmask/finalmask.go` | Fix current generator. Candidate names and reverse application order explicitly adapted in qualification script. 42 cross-version loopback transfers passed. 9.30 accepting legacy header/seed while ignoring them is not semantic compatibility. |
| Noise | `transport/internet/finalmask/noise`, `infra/conf/transport_finalmask.go` | Existing noise cases validated. New expression options not shipped without selected runtime and validation/settings contract. |
| UDP Hop/Hysteria | `transport/internet/finalmask/udphop`, `transport/internet/hysteria`, `infra/conf/transport_method.go` | New UDP mask/lifecycle and QUIC params reviewed; existing Ghajar Hysteria2 settings/port hopping routing to bundled sing-box remains. No second UI method. |
| xDNS | 7.28 `Xdns` rejects old domain; 9.30 `XDNS` and `finalmask/xdns` refactored resolver/framing/cancellation | Method, not standalone protocol. CLI parse success on 9.30 is insufficient wire compatibility/safety proof; preserve phase4 unavailable decision. No unreviewed resolver substitution. |
| WireGuard | `infra/conf/wireguard.go::PreSharedKey`, `proxy/wireguard`; IPv4/IPv6 cases | Fix generated IPv6 endpoint and carry PSK. Preserve current engine and old profile fields. |
| HTTPUpgrade/gRPC/XHTTP | `transport/internet/httpupgrade`, `grpc`, `splithttp`, `infra/conf/transport_method.go` | Existing protocol/transport generation validated, including SplitHTTP alias. Runtime TLS/HTTP2/HTTP3 acceptance still required. |
| Legacy HTTP and QUIC transports | `infra/conf/transport_internet.go::TransportProtocol.Build` in current and candidate tags | Already removed from current official core. Negative compatibility cases retained. No silent protocol substitution; server-side migration required. QUIC underlying Hysteria/MASQUE is different. |

**Selected production dependency: unchanged v1.260327.0 / 26.3.27.**

The official host binaries are qualification tools, not the APK native library. `ca.psiphon.aar` combines Xray and a locally replaced Psiphon fork; its metadata records local replace paths without sufficient exact fork commit provenance. Changing go.mod would not rebuild the committed AAR in the present release pipeline. Replacing it without reproducibility would risk healthy Psiphon behavior. No binary replacement or false version declaration was made.

References: official source at the commits above, official releases `/releases/tag/v26.3.27`, `/v26.7.28`, `/v26.9.30`; local source-of-truth `docs/audit-1.1.1/BINARY_MODULE_EVIDENCE.json`, phase4 evidence and current `go.mod`. Candidate schema script is deliberately confined to validation and must not be described as an active app upgrade.
