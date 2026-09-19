# sing-box

Upstream: <https://github.com/SagerNet/sing-box>
Licence: GPL-3.0-or-later (see `LICENSE`), which this app is also under.
Pinned commit: `8330820fa62505f9574e4c35cd969d9af6eb7769`
Built with: Go 1.26.8 (see below — not the version in its go.mod)

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

## Why a subprocess and NOT a gomobile AAR

This is the important part of this file, and it was learned the hard way: the
first attempt built sing-box as a `gomobile bind` AAR, exactly the way its own
Android client does. That builds fine and then fails at
`:app:checkDebugDuplicateClasses`:

```
Duplicate class go.Seq found in modules
  ca.psiphon.aar -> ca.psiphon-runtime  and  libbox.aar -> libbox-runtime
```

**Two gomobile AARs cannot coexist in one Android app.** Every gomobile
binding ships the same `go.Seq`, `go.Universe` and `go.error` support classes,
and they are not interchangeable: `go.Seq`'s static initialiser loads its own
library by name (`gojni` for one, `box` for the other, from `-libname`), and
each `.so` registers JNI natives against that same class. Keeping one and
dropping the other does not work either — the surviving `go.Seq` would route
one core's calls into the other core's Go runtime.

This app already ships one gomobile AAR and cannot drop it: `ca.psiphon.aar`
is the Xray+Psiphon engine, committed as a prebuilt binary with no Go source
in this repository to rebuild from. So the second core cannot be a binding.

A plain executable has none of this. It is its own process with its own Go
runtime, no JNI, no shared classes — and it is the pattern this app already
uses for Aether (`AetherController` execs `libaether.so` from
`nativeLibraryDir` with `ProcessBuilder`). sing-box is built and run the same
way.

Two things follow from that choice, both good:

- **No `PlatformInterface`.** libbox's is a thirty-method interface, all of
  which would have had to be implemented and none of which can be verified
  without building the AAR first.
- **zeptun carries the tun.** sing-box runs with a `mixed` inbound — a local
  SOCKS5/HTTP proxy — and the zeptun engine already in this app forwards a
  VpnService tun into exactly that. The two engines added in this branch turn
  out to fit together.

## The `.so` name is load-bearing

The binary is installed as `app/src/main/jniLibs/<abi>/libsingbox.so` even
though it is an executable and not a library. Android only extracts files
matching `lib*.so` from an APK's ABI directories and only those come out with
the executable bit set, so a Go binary has to be named this way to be runnable
from `nativeLibraryDir` at all. `libaether.so` in this same app is the same
trick.

## `-checklinkname=0` is not optional

If the build ends, after several minutes of compiling, with:

```
link: experimental/libbox: invalid reference to os.checkPidfdOnce
```

the `-ldflags` are missing. sing-box pulls private runtime symbols in with
`//go:linkname`, and since Go 1.23 the linker refuses a pull-linkname to an
unmarked symbol unless `-checklinkname=0` is passed.

Two things this is easy to get wrong, and both cost a failed build here:

- **`badlinkname` is a build tag, `-checklinkname=0` is a linker flag.** They
  are different mechanisms and the tag does not substitute for the flag.
  Passing the full tag list with no ldflags fails in exactly the same way.
- **It is not a Go version problem.** The same failure happens on 1.26.8. The
  Go version is pinned to match upstream, not to fix this.

The flags come from their `cmd/internal/build_shared/flags.go`. `go.mod`'s
`go 1.25.5` is the minimum *language* version, not the toolchain their release
is built with.

## How it is built

Not vendored — built from source at the pinned commit:

- CI: the "Build the sing-box core" step in `.github/workflows/android.yml`,
  in **both** the build and release jobs
- locally: `sh scripts/build-singbox.sh`

Both run the same `go build` with the same tags and ldflags, then assert two
things about the result rather than trusting the invocation:

1. that the protocols this app needs are actually present in the binary,
   because a typo in a build tag is completely silent
2. that each ABI's file is an ELF for the machine it claims, because a binary
   built for the wrong `GOARCH` lands in the right directory and fails only at
   exec time on a real phone

## Build tags

Which protocols exist in the binary is decided by build tags, and a tag-gated
protocol that is left out is *absent* rather than disabled — sing-box ships an
`include/<name>_stub.go` for each one.

Included: `with_quic` (hysteria, hysteria2, tuic), `with_wireguard`,
`with_utls`, `with_openconnect`, `with_openvpn`, `with_clash_api`.

Always present with no tag: snell, anytls, ssh, tor, shadowtls, shadowsocksr,
vless, vmess, trojan, shadowsocks.

Deliberately left out: `with_tailscale`, `with_usbip`,
`with_naive_outbound`. None of them was asked for and each pulls in a large
dependency tree, against an APK that is already about 150 MB.

## What this does NOT replace

Nothing. The Xray path is untouched and is still what every existing config
uses. sing-box is reached only by a config whose protocol only it can speak.
The app keeps its own OpenVPN engine (ics-openvpn, a real native OpenVPN) and
its own IKEv2 engine (strongSwan); sing-box's `openvpn-client` is compiled in
as well, but it is a second way to do something the app could already do, so
nothing routes to it by default.
