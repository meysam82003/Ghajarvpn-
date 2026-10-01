# Deferred device acceptance — NOT EXECUTED

Only execute after the owner allows product build/device qualification. None of these are claimed passed.

1. Android phone A: connect an eligible Xray profile; compare its tunneled public IPv4/IPv6 with a controlled HTTPS endpoint.
2. Enable a WPA2/WPA3 hotspot; B joins. Choose the actual private interface. No bind to cellular, wildcard or TUN.
3. Unauthenticated CONNECT gets 407; SOCKS no-auth/wrong credentials cannot open upstream. Authenticated client uses remote hostname resolution (SOCKS5h/remote DNS).
4. B requests the controlled endpoint through the proxy: exit must match A's VPN, not A's ISP. Capture A/B/server DNS and IPv6; separate bootstrap DNS for the VPN server from destination DNS.
5. Disconnect/revoke A's VPN during a persistent transfer: listener and sessions close, B's proxy request fails. A direct request from B is outside this guarantee; disable client fallback.
6. Reconnect A with an eligible engine: new relay serves traffic; switch to OpenVPN/IKEv2/plugin/zeptun: no active relay. Kill A's process and reboot: no old listener/credential reuse.
7. Rotate password: existing sockets terminate; old password fails, new works. Stop sharing: both HTTP CONNECT and SOCKS ports close immediately.
8. Change Wi-Fi/LAN/hotspot address: no stale bind or silent selection of a different LAN. Test lock/unlock/background/lifecycle and OEM interface naming.
9. Verify client bytes/duration against controlled transfers and multiple sessions from one IP; no claim about unrelated hotspot devices.
10. iPhone/iPad/macOS: inspect/install IKEv2 EAP mobileconfig, enter password separately, verify valid and invalid server certificate/Remote ID. Test public CA and explicitly managed enterprise CA; no certificate bypass.
11. All target platforms: import supported URI/config in actual compatible client; round-trip WireGuard DNS/AllowedIPs, full Mihomo YAML/provider references, new settings, private credentials and large payload fallback.
12. OpenVPN original text and credential prompt on target; legacy profile missing raw text gives error, not Android runtime config. Test inline cert/key, TLS crypt, external file refusal, scripts refusal, backup/restore.
13. QR: consent before display, 60-second default expiry, secure window and background dismissal, image sharing with temporary read grant, too-large payload → file.
14. Plugin catalog metadata only at entry/startup. No absent plugin service, worker, native load or fabricated release size. Phase4 APK signing/device gates still apply.
