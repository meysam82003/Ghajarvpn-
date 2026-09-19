# sing-box

Upstream: <https://github.com/SagerNet/sing-box>
Licence: GPL-3.0-or-later (see `LICENSE`), which this app is also under.
Pinned commit: `8330820fa62505f9574e4c35cd969d9af6eb7769`
Toolchain: Go 1.26.8, gomobile v0.1.13 (see below - not the versions in go.mod)

## Why a second core

The app's first core is Xray, as a prebuilt gomobile AAR
(`app/libs/ca.psiphon.aar`, Xray-core v1.260327.0). It covers VLESS, VMess,
Trojan, Shadowsocks, Hysteria 2, WireGuard and the whole finalmask layer.

It does not cover, and cannot be made to cover, these:

| Protocol | Why Xray cannot do it |
|---|---|
| **OpenConnect** | Cisco AnyConnect's protocol. A different wire protocol with its own authentication, not an Xray transport. |
| **Snell** | Surge's own protocol. Never implemented in Xray. |
| **AnyTLS** | A separate protocol, not a TLS setting. |
| **ShadowTLS** | Implemented in sing-box, not in Xray. |
| **ShadowsocksR** | Dropped from Xray long ago. |
| **SSH as a tunnel** | The app already has an SSH client for a terminal and SFTP; carrying traffic over SSH is a different job. |

sing-box implements all of them in one core, so it is added as one core
rather than six separate integrations.

## What this does NOT replace

Nothing. The Xray path is untouched and is still what every existing config
uses. sing-box is reached only by a config whose protocol only it can speak.
The app also keeps its own OpenVPN engine (ics-openvpn, a real native
OpenVPN) and its own IKEv2 engine (strongSwan); sing-box's `openvpn-client`
is built in as well, but it is the second way to do something the app could
already do, so nothing routes to it by default.

## How it is built

It is **not** vendored. `libbox.aar` is roughly a hundred megabytes and a
committed binary nobody can reproduce is worse than a build step, so it is
built from source at the pinned commit above:

- CI: the "Build the sing-box core" step in `.github/workflows/android.yml`
- locally: `sh scripts/build-singbox-aar.sh`

Both run the same `gomobile bind` with the same build tags and then assert
that the protocols this app needs are actually present in the artifact,
because a typo in a build tag is otherwise completely silent.

## `-checklinkname=0` is not optional

If the build ends, after about ten minutes of compiling, with:

```
link: experimental/libbox: invalid reference to os.checkPidfdOnce
```

the `-ldflags` are missing. `libbox/pidfd_android.go` pulls the private
`os.checkPidfdOnce` in with `//go:linkname` to switch pidfd off on Android
(their issue 3233), and since Go 1.23 the linker refuses a pull-linkname to
an unmarked symbol unless `-checklinkname=0` is passed.

Two things this is easy to get wrong, and both cost a failed build here:

- **`badlinkname` is a build tag, `-checklinkname=0` is a linker flag.** They
  are different mechanisms and the tag does not substitute for the flag.
  Passing the full tag list with no ldflags fails in exactly the same way.
- **It is not a Go version problem.** The same failure happens on 1.26.8. The
  Go version below is pinned to match upstream, not to fix this.

The flags come from their `cmd/internal/build_shared/flags.go`.

## The toolchain versions are not the ones in go.mod

Read from sing-box's own CI, because `go.mod` describes what the module needs
rather than what its release is built with:

| | go.mod says | upstream CI uses |
|---|---|---|
| Go | `go 1.25.5` | **1.26.8** |
| gomobile | `v0.1.12` | **v0.1.13** |

`go 1.25.5` is the minimum *language* version. The gomobile difference is the
same shape: go.mod pins v0.1.12 as a *library*, while their `Makefile`'s
`lib_install` installs the v0.1.13 *tool*, and the tool is the one that
generates the bindings.

## The two things that will confuse the next person

**1. This is a compile-time dependency, unlike zeptun.** zeptun's absence is a
normal runtime state - `ZeptunEngine.available` answers for it and the app
builds and runs without it. `libbox.aar` is different: the Kotlin wrapper
imports `io.nekohasekai.libbox`, so a checkout without the AAR does not
compile at all. Run the script once before building locally.

**2. `io.nekohasekai.libbox` is not a package name we chose.** It comes from
`-javapkg=io.nekohasekai` in the build, which is what sing-box's own client
uses. Changing it means changing the build and every import together.

## Build tags

Which protocols exist in the binary is decided by build tags, and a
tag-gated protocol that is left out is *absent* rather than disabled -
sing-box ships an `include/<name>_stub.go` for each one.

Included: `with_quic` (hysteria, hysteria2, tuic), `with_wireguard`,
`with_utls`, `with_openconnect`, `with_openvpn`, `with_clash_api`.

Always present with no tag: snell, anytls, ssh, tor, shadowtls,
shadowsocksr, vless, vmess, trojan, shadowsocks.

Deliberately left out: `with_tailscale`, `with_usbip`,
`with_naive_outbound`. None of them was asked for and each pulls in a large
dependency tree, against an APK that is already about 150 MB.

`badlinkname` and `tfogo_checklinkname0` are not features. They are the
linkname workarounds sing-box's own release build passes, and the build fails
on a current Go toolchain without them.
