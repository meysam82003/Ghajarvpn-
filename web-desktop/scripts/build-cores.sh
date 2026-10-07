#!/usr/bin/env bash
# Builds every native core the desktop VPN engine (web-desktop/engine/) runs,
# into web-desktop/core/<target>/, the directory electron-builder packages as
# Resources/core (see build.extraResources in web-desktop/package.json).
#
#   bash web-desktop/scripts/build-cores.sh [win-x64] [mac-x64] [mac-arm64] [linux-x64]
#   (no arguments: all four)
#
# Per target it produces (".exe" on Windows):
#
#   xray            XTLS/Xray-core release, through scripts/fetch-core.mjs     REQUIRED
#   geoip.dat       app/src/main/assets (Chocolate4U Iran rules: they replace   REQUIRED
#   geosite.dat     the stock Xray files; geosite:ads and category-ir live only here)
#   geoip-ir.srs    Chocolate4U/Iran-sing-box-rules, sing-box rule-set           optional
#   sing-box        SagerNet/sing-box 132b38e (the same pin as scripts/build-singbox.sh)  REQUIRED
#   libcronet.so / libcronet.dll   NaiveProxy's Cronet, next to sing-box (linux / win)
#   psiphon         native/Psiphon ConsoleClient
#   ghajar-helper   native/ghajar-helper
#   juicity         juicity/juicity 88dbf4f cmd/client
#   dnstt           dnstt v1.20260501.0 (module proxy, checksum-verified)
#   vaydns          net2share/vaydns v0.2.8 (module proxy, checksum-verified)
#   noizdns         anonvector/noizdns f76c585 + anonvector/dnstt 51bdba3
#   masterdns       masterking32/MasterDnsVPN acbf1c6
#   stormdns        NullRoute1970/StormDNS ca2eb48
#   cottendns       WhiteDNS/CottenDns cdf084f
#   slipstream      Mygod/slipstream-rust 7de506b (Rust + C: built only where the
#                   host can build that target natively, see build_slipstream)
#   licenses/       licence texts of the third-party programs above
#
# The sources and commits are the ones the Android scripts in scripts/ pin.
# Everything written in Go is cross-compiled with CGO_ENABLED=0, except
# sing-box for macOS with NaiveProxy, which links Cronet's static library with
# cgo and so needs a macOS host (Xcode). On another host the macOS sing-box is
# built without NaiveProxy and the summary says so.
#
# A missing optional sidecar is a warning, not a failure: the app greys out the
# protocols whose program is absent. xray, sing-box and the two .dat files are
# required; the script exits non-zero when one of them is missing.
#
# Environment:
#   CORES_WORKDIR  where sources are cloned and built (default: $RUNNER_TEMP or /tmp, /ghajar-cores)
#   CORES_ONLY     comma list to build only some parts, e.g. "sing-box,psiphon" (testing)
#   CORES_NAIVE=0  leave NaiveProxy out of sing-box everywhere
#   XRAY_VERSION   an Xray-core tag for fetch-core.mjs (default: latest)
#
# Needs: bash 3.2+, git, go 1.26+, node (for fetch-core.mjs), curl, unzip;
# cargo + cmake for slipstream.
set -eu

SINGBOX_REPO=https://github.com/SagerNet/sing-box
SINGBOX_COMMIT=132b38e9caaba1a1959354d518e54d2d08419afe
# The tags of scripts/build-singbox.sh, minus with_naive_outbound, which is
# added per target below. badlinkname + -checklinkname=0 are both needed (see
# that script).
SINGBOX_TAGS=with_quic,with_wireguard,with_utls,with_openconnect,with_openvpn,with_tailscale,with_clash_api,badlinkname,tfogo_checklinkname0
SINGBOX_LDFLAGS="-checklinkname=0 -X runtime.godebugDefault=multipathtcp=0,tlssha1=1 -s -w -buildid="

JUICITY_REPO=https://github.com/juicity/juicity
JUICITY_COMMIT=88dbf4f8efc54d70caf319ec79d0b7f12d70faf1
DNSTT_MODULE=www.bamsoftware.com/git/dnstt.git
DNSTT_VERSION=v1.20260501.0
DNSTT_COMMIT=0c5c52a57d899c05428c116898941761a2ed83c2
VAYDNS_MODULE=github.com/net2share/vaydns
VAYDNS_VERSION=v0.2.8
VAYDNS_COMMIT=a0ff70110d5e96686ab8f4c8e1c44cd09be07a75
SLIP_REPO=https://github.com/Mygod/slipstream-rust
SLIP_COMMIT=7de506b222c83ae97a19a618e33fe26899af1bd5

GEOIP_IR_URLS="https://github.com/Chocolate4U/Iran-sing-box-rules/releases/latest/download/geoip-ir.srs
https://raw.githubusercontent.com/Chocolate4U/Iran-sing-box-rules/rule-set/geoip-ir.srs"

here=$(cd "$(dirname "$0")" && pwd)
desktop=$(cd "$here/.." && pwd)
root=$(cd "$desktop/.." && pwd)
work=${CORES_WORKDIR:-${RUNNER_TEMP:-/tmp}/ghajar-cores}
mkdir -p "$work"
work=$(cd "$work" && pwd)
results="$work/.results"
: > "$results"

host_os=$(uname -s)
host_arch=$(uname -m)
export GOPROXY="${GOPROXY:-https://proxy.golang.org,direct}"
export GOFLAGS="${GOFLAGS:-}"

targets="$*"
[ -n "$targets" ] || targets="win-x64 mac-x64 mac-arm64 linux-x64"
for t in $targets; do
    case "$t" in
        win-x64|mac-x64|mac-arm64|linux-x64) ;;
        *) echo "unknown target '$t' (use win-x64, mac-x64, mac-arm64, linux-x64)" >&2; exit 2 ;;
    esac
done

# ------------------------------------------------------------------ helpers

log() { printf '\n==> %s\n' "$*"; }
warn() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::warning::$*"; else echo "WARNING: $*" >&2; fi
}

wanted() { # is part $1 selected by CORES_ONLY?
    [ -z "${CORES_ONLY:-}" ] && return 0
    case ",$CORES_ONLY," in *",$1,"*) return 0 ;; esac
    return 1
}

goos() { case "$1" in win-*) echo windows ;; mac-*) echo darwin ;; linux-*) echo linux ;; esac; }
goarch() { case "$1" in *-x64) echo amd64 ;; *-arm64) echo arm64 ;; esac; }
exe() { case "$1" in win-*) echo .exe ;; *) echo "" ;; esac; }
outdir() { echo "$desktop/core/$1"; }
fsize() { wc -c < "$1" | tr -d ' '; }

# record target name status note: one line of the summary table
record() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "${4:-}" >> "$results"; }

# Did the file come out as an executable for the right OS and CPU? A binary
# built for the wrong GOARCH still lands in the right directory and only fails
# when a user runs it. Skipped quietly when `file` is missing.
check_format() { # file target
    command -v file >/dev/null 2>&1 || return 0
    desc=$(file -b "$1")
    case "$2:$desc" in
        win-x64:*PE32+*x86-64*) return 0 ;;
        mac-x64:*Mach-O*x86_64*) return 0 ;;
        mac-arm64:*Mach-O*arm64*) return 0 ;;
        linux-x64:*ELF*x86-64*) return 0 ;;
    esac
    echo "  $1 is not a $2 executable: $desc" >&2
    return 1
}

# finish target name file [note]: verify, chmod, record. Returns non-zero on a bad binary.
finish() {
    t=$1; name=$2; f=$3; note=${4:-}
    if [ ! -s "$f" ]; then record "$t" "$name" FAILED "not produced"; return 1; fi
    if ! check_format "$f" "$t"; then rm -f "$f"; record "$t" "$name" FAILED "wrong format"; return 1; fi
    chmod 755 "$f"
    record "$t" "$name" ok "$note"
    echo "  $t $(basename "$f"): $(fsize "$f") bytes${note:+ ($note)}"
}

# Clone (once) and check out a pinned commit, verifying it.
fetch_git() { # dir repo commit
    dir=$1; repo=$2; commit=$3
    [ -d "$dir/.git" ] || git clone -q "$repo" "$dir"
    if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null)" != "$commit" ]; then
        git -C "$dir" fetch -q origin "$commit" 2>/dev/null || git -C "$dir" fetch -q --tags origin
        git -C "$dir" checkout -q "$commit"
    fi
    [ "$(git -C "$dir" rev-parse HEAD)" = "$commit" ] || { echo "$repo is not at $commit" >&2; return 1; }
}

# go module download with retries: the proxy drops the odd download mid-stream.
go_download() { # run in a module directory
    for attempt in 1 2 3 4; do
        go mod download "$@" && return 0
        echo "go mod download failed (attempt $attempt); retrying" >&2
        sleep $((attempt * 5))
    done
    return 1
}

# Plain CGO_ENABLED=0 cross build of a Go main package.
gobuild() { # srcdir target pkg output [extra go build args...]
    src=$1; t=$2; pkg=$3; out=$4; shift 4
    ( cd "$src" && CGO_ENABLED=0 GOOS=$(goos "$t") GOARCH=$(goarch "$t") \
        go build -trimpath -ldflags "-s -w -buildid=" "$@" -o "$out" "$pkg" )
}

licence() { # target name file
    [ -f "$3" ] || return 0
    mkdir -p "$(outdir "$1")/licenses"
    cp "$3" "$(outdir "$1")/licenses/$2.txt"
}

# Runs a sidecar build step; a failure is a warning and the binary is dropped.
optional() { # target name output-file -- command...
    t=$1; name=$2; f=$3; shift 4
    rm -f "$f"
    if "$@"; then
        finish "$t" "$name" "$f" || warn "$name for $t: bad binary, left out"
    else
        rm -f "$f"
        record "$t" "$name" FAILED "build error"
        warn "$name for $t did not build; the app greys out its protocols"
    fi
}

# ------------------------------------------------------------------ sources (once)

go version >/dev/null 2>&1 || { echo "go is not installed" >&2; exit 1; }
echo "host: $host_os/$host_arch, $(go version), work: $work"

singbox_src="$work/sing-box"
singbox_ready=0
prepare_singbox() {
    [ "$singbox_ready" = 1 ] && return 0
    log "sing-box sources at $SINGBOX_COMMIT"
    fetch_git "$singbox_src" "$SINGBOX_REPO" "$SINGBOX_COMMIT" &&
        ( cd "$singbox_src" && go_download ) || return 1
    singbox_ready=1
}

# The directory of a module from sing-box's go.sum-verified module cache.
singbox_module_dir() {
    ( cd "$singbox_src" && go mod download -json "$1" ) | sed -n 's/^[[:space:]]*"Dir": *"\(.*\)",*$/\1/p'
}

psiphon_src="$work/psiphon"
psiphon_ready=0
prepare_psiphon() {
    [ "$psiphon_ready" = 1 ] && return 0
    log "psiphon sources (scratch copy of native/Psiphon)"
    # native/Psiphon/go.mod requires github.com/quic-go/qpack/v4legacy v0.4.0
    # while vendor/modules.txt vendors github.com/quic-go/qpack v0.4.0, so
    # -mod=vendor refuses the tree. The fix is made in a scratch copy only;
    # native/Psiphon itself is left as it is.
    rm -rf "$psiphon_src" && mkdir -p "$psiphon_src" &&
        ( cd "$root/native/Psiphon" && tar cf - --exclude=.git . ) | ( cd "$psiphon_src" && tar xf - ) &&
        sed 's#github.com/quic-go/qpack/v4legacy v0.4.0#github.com/quic-go/qpack v0.4.0#' "$psiphon_src/go.mod" > "$psiphon_src/go.mod.new" &&
        mv "$psiphon_src/go.mod.new" "$psiphon_src/go.mod" &&
        [ -f "$psiphon_src/ConsoleClient/main.go" ] || return 1
    psiphon_ready=1
}

helper_ready=0
prepare_helper() {
    [ "$helper_ready" = 1 ] && return 0
    log "ghajar-helper modules"
    ( cd "$root/native/ghajar-helper" && go_download && go mod verify )
    helper_ready=1
}

juicity_ready=0
prepare_juicity() {
    [ "$juicity_ready" = 1 ] && return 0
    log "juicity sources at $JUICITY_COMMIT"
    fetch_git "$work/juicity" "$JUICITY_REPO" "$JUICITY_COMMIT" && ( cd "$work/juicity" && go_download ) || return 1
    juicity_ready=1
}

# A throwaway module that requires a published module at a pinned version, so
# the checksum database verifies what is built; the proxy's record of the
# commit must match the pin, so a moved tag fails instead of shipping.
prepare_proxy_module() { # dir module version commit package
    d=$1; m=$2; v=$3; c=$4; p=$5
    mkdir -p "$d"
    ( cd "$d"
      [ -f go.mod ] || go mod init "ghajar/$(basename "$d")build" >/dev/null 2>&1
      go get "$p@$v"
      origin=$(go mod download -json "$m@$v" | grep -o '"Hash": *"[0-9a-f]*"' | grep -o '[0-9a-f]\{40\}' || true)
      if [ -n "$origin" ] && [ "$origin" != "$c" ]; then
          echo "$m $v resolves to $origin, expected $c" >&2; exit 1
      fi )
}

dns_ready=""
prepare_dns() { # name: prepares one DNS tunnel source tree
    case " $dns_ready " in *" $1 "*) return 0 ;; esac
    log "$1 sources"
    case "$1" in
        dnstt) prepare_proxy_module "$work/dnstt-mod" "$DNSTT_MODULE" "$DNSTT_VERSION" "$DNSTT_COMMIT" "$DNSTT_MODULE/dnstt-client" ;;
        vaydns) prepare_proxy_module "$work/vaydns-mod" "$VAYDNS_MODULE" "$VAYDNS_VERSION" "$VAYDNS_COMMIT" "$VAYDNS_MODULE/vaydns-client" ;;
        # noizdns' go.mod replaces dnstt with ../dnstt: the fork must sit next to it.
        noizdns) fetch_git "$work/noizdns" https://github.com/anonvector/noizdns f76c585093c7c31e9be6c10a1b425be033eb4938 &&
                 fetch_git "$work/dnstt" https://github.com/anonvector/dnstt 51bdba3948d44b3175bc88ea40b2116bb9daff07 &&
                 ( cd "$work/noizdns" && go_download ) ;;
        masterdns) fetch_git "$work/masterdns" https://github.com/masterking32/MasterDnsVPN acbf1c61f90786f41b975d2e2f616afbce292b29 && ( cd "$work/masterdns" && go_download ) ;;
        stormdns) fetch_git "$work/stormdns" https://github.com/NullRoute1970/StormDNS ca2eb481fddd4a80d26b3a1a7c714b5aecacecfc && ( cd "$work/stormdns" && go_download ) ;;
        cottendns) fetch_git "$work/cottendns" https://github.com/WhiteDNS/CottenDns cdf084f528c5833eeb77f1b9a29ecf0ceae89f86 && ( cd "$work/cottendns" && go_download ) ;;
    esac || return 1
    dns_ready="$dns_ready $1"
}

# ------------------------------------------------------------------ per binary

build_xray() { # target
    t=$1; out=$(outdir "$t")
    log "xray for $t"
    # fetch-core.mjs starts the target directory afresh, so it runs first.
    if node "$here/fetch-core.mjs" "$t"; then
        finish "$t" xray "$out/xray$(exe "$t")" "release ${XRAY_VERSION:-latest}" || true
        # Its README.md is Xray's own; the licence is kept for the package.
        rm -f "$out/README.md"
        [ -f "$out/LICENSE" ] && mkdir -p "$out/licenses" && mv "$out/LICENSE" "$out/licenses/xray.txt"
    else
        record "$t" xray FAILED "download failed"
    fi
}

build_geo() { # target
    t=$1; out=$(outdir "$t"); mkdir -p "$out"
    log "geo files for $t"
    for f in geoip.dat geosite.dat; do
        if cp "$root/app/src/main/assets/$f" "$out/$f"; then record "$t" "$f" ok "Chocolate4U (app assets)"
        else record "$t" "$f" FAILED "missing in app/src/main/assets"; fi
    done
    rm -f "$out/geoip-ir.srs"
    for url in $GEOIP_IR_URLS; do
        if curl -fsSL --retry 3 -o "$out/geoip-ir.srs.part" "$url" &&
           [ "$(head -c 3 "$out/geoip-ir.srs.part")" = SRS ]; then
            mv "$out/geoip-ir.srs.part" "$out/geoip-ir.srs"
            record "$t" geoip-ir.srs ok "$(echo "$url" | sed 's#https://[^/]*/##')"
            return 0
        fi
        rm -f "$out/geoip-ir.srs.part"
    done
    record "$t" geoip-ir.srs MISSING "download failed (optional)"
    warn "geoip-ir.srs could not be downloaded for $t; the TUN mode routes Iran by geosite only"
}

# NaiveProxy (with_naive_outbound) links Chromium's network stack through
# github.com/sagernet/cronet-go, pinned (checksums in sing-box's go.sum) at the
# version sing-box's go.mod names. Per desktop target:
#   win-x64    only purego exists upstream: CGO_ENABLED=0 -tags with_purego and
#              libcronet.dll (from the checksum-verified module
#              cronet-go/lib/windows_amd64) beside sing-box.exe.
#   linux-x64  the same purego build with libcronet.so beside sing-box (what
#              upstream ships as its "purego" Linux variant). The cgo variant
#              would need Chromium's clang toolchain and sysroot.
#   mac-*      no dylib is published: cgo against the static libcronet.a of
#              cronet-go/lib/darwin_<arch>, linking Apple frameworks, so it
#              needs a macOS host with Xcode. Elsewhere: built without naive.
# cronet-go looks for the library in the executable's directory first, which
# is Resources/core in the packaged app.
build_singbox() { # target
    t=$1; out=$(outdir "$t"); mkdir -p "$out"; f="$out/sing-box$(exe "$t")"
    log "sing-box for $t"
    prepare_singbox || { record "$t" sing-box FAILED "source fetch failed"; return 0; }
    rm -f "$f" "$out/libcronet.so" "$out/libcronet.dll"
    tags=$SINGBOX_TAGS; cgo=0; lib=""; note="no naive"
    if [ "${CORES_NAIVE:-1}" != 0 ]; then
        case "$t" in
            win-x64) tags="$tags,with_naive_outbound,with_purego"; lib=windows_amd64/libcronet.dll ;;
            linux-x64) tags="$tags,with_naive_outbound,with_purego"; lib=linux_amd64/libcronet.so ;;
            mac-*)
                if [ "$host_os" = Darwin ]; then tags="$tags,with_naive_outbound"; cgo=1; note="naive (cgo, static Cronet)"
                else note="no naive: needs a macOS host"; warn "sing-box for $t built WITHOUT NaiveProxy (static Cronet needs a macOS host)"; fi ;;
        esac
    fi
    if [ -n "$lib" ]; then
        mod="github.com/sagernet/cronet-go/lib/${lib%%/*}"
        dir=$(singbox_module_dir "$mod")
        if [ -n "$dir" ] && [ -f "$dir/${lib#*/}" ]; then
            cp "$dir/${lib#*/}" "$out/${lib#*/}"; chmod 644 "$out/${lib#*/}"
            note="naive (purego + ${lib#*/} $(basename "$dir" | sed 's/.*@//'))"
        else
            warn "no ${lib#*/} in $mod; sing-box for $t is built without NaiveProxy"
            tags=$SINGBOX_TAGS; note="no naive: library missing"
        fi
    fi
    echo "  tags: $tags (CGO_ENABLED=$cgo)"
    if ( cd "$singbox_src" && CGO_ENABLED=$cgo GOOS=$(goos "$t") GOARCH=$(goarch "$t") \
            MACOSX_DEPLOYMENT_TARGET=${MACOSX_DEPLOYMENT_TARGET:-12.0} \
            go build -trimpath -tags "$tags" -ldflags "$SINGBOX_LDFLAGS" -o "$f" ./cmd/sing-box ); then
        finish "$t" sing-box "$f" "$note" || true
        [ -f "$out/libcronet.so" ] && record "$t" libcronet.so ok "for naive"
        [ -f "$out/libcronet.dll" ] && record "$t" libcronet.dll ok "for naive"
        licence "$t" sing-box "$singbox_src/LICENSE"
    else
        rm -f "$out/libcronet.so" "$out/libcronet.dll"
        record "$t" sing-box FAILED "build error"
    fi
}

build_psiphon() {
    t=$1; f="$(outdir "$t")/psiphon$(exe "$t")"; mkdir -p "$(outdir "$t")"
    log "psiphon for $t"
    prepare_psiphon || { record "$t" psiphon FAILED "source copy failed"; return 0; }
    rev=$(git -C "$root" rev-parse --short HEAD 2>/dev/null || echo unknown)
    bi=github.com/Psiphon-Labs/psiphon-tunnel-core/psiphon/common/buildinfo
    optional "$t" psiphon "$f" -- gobuild "$psiphon_src/ConsoleClient" "$t" . "$f" -mod=vendor \
        -ldflags "-s -w -buildid= -X $bi.buildRepo=ghajarvpn/native/Psiphon -X $bi.buildRev=$rev"
    licence "$t" psiphon "$psiphon_src/LICENSE"
}

build_helper() {
    t=$1; f="$(outdir "$t")/ghajar-helper$(exe "$t")"; mkdir -p "$(outdir "$t")"
    log "ghajar-helper for $t"
    prepare_helper || { record "$t" ghajar-helper FAILED "module download failed"; return 0; }
    optional "$t" ghajar-helper "$f" -- gobuild "$root/native/ghajar-helper" "$t" . "$f" -mod=readonly
}

build_juicity() {
    t=$1; f="$(outdir "$t")/juicity$(exe "$t")"; mkdir -p "$(outdir "$t")"
    log "juicity for $t"
    prepare_juicity || { record "$t" juicity FAILED "source fetch failed"; warn "juicity sources unavailable"; return 0; }
    optional "$t" juicity "$f" -- gobuild "$work/juicity" "$t" ./cmd/client "$f"
    licence "$t" juicity "$work/juicity/LICENSE"
}

build_dns() { # target name
    t=$1; n=$2; f="$(outdir "$t")/$n$(exe "$t")"; mkdir -p "$(outdir "$t")"
    log "$n for $t"
    prepare_dns "$n" || { record "$t" "$n" FAILED "source fetch failed"; warn "$n sources unavailable"; return 0; }
    case "$n" in
        dnstt) src="$work/dnstt-mod"; pkg="$DNSTT_MODULE/dnstt-client"
               lic="$(go env GOMODCACHE)/$DNSTT_MODULE@$DNSTT_VERSION/COPYING" ;;
        vaydns) src="$work/vaydns-mod"; pkg="$VAYDNS_MODULE/vaydns-client"
                lic="$(go env GOMODCACHE)/$VAYDNS_MODULE@$VAYDNS_VERSION/COPYING" ;;
        noizdns) src="$work/noizdns"; pkg=./cmd/noizdns-client; lic="$work/noizdns/LICENSE"
                 licence "$t" noizdns-dnstt "$work/dnstt/COPYING" ;;
        *) src="$work/$n"; pkg=./cmd/client; lic="$work/$n/LICENSE" ;;
    esac
    optional "$t" "$n" "$f" -- gobuild "$src" "$t" "$pkg" "$f"
    licence "$t" "$n" "$lic"
}

# slipstream-client is Rust over picoquic (C, CMake) and OpenSSL. It is built
# with the host's own toolchain only:
#   linux-x64  on a Linux x86_64 host: vendored (static) OpenSSL, as on Android.
#   mac-*      on a macOS host: both arches (Xcode clang cross-builds x86_64 on
#              Apple silicon; CMAKE_OSX_ARCHITECTURES points picoquic's CMake
#              build at the target arch).
#   win-x64    upstream builds picoquic for Windows only with MSVC
#              (scripts/build_picoquic_windows.ps1, OpenSSL from vcpkg), which
#              build.rs insists on; that is done by the workflow's Windows job,
#              not by this script.
slip_ready=0
build_slipstream() {
    t=$1; out=$(outdir "$t"); f="$out/slipstream$(exe "$t")"; mkdir -p "$out"
    log "slipstream for $t"
    case "$t:$host_os:$host_arch" in
        linux-x64:Linux:x86_64) rt=x86_64-unknown-linux-gnu ;;
        mac-x64:Darwin:*) rt=x86_64-apple-darwin; osx=x86_64 ;;
        mac-arm64:Darwin:*) rt=aarch64-apple-darwin; osx=arm64 ;;
        *)
            record "$t" slipstream SKIPPED "Rust/C build needs a $t host (see the workflow)"
            echo "  skipped: slipstream for $t is built on a $t runner"
            return 0 ;;
    esac
    command -v cargo >/dev/null 2>&1 && command -v cmake >/dev/null 2>&1 || {
        record "$t" slipstream FAILED "cargo or cmake missing"; warn "slipstream: cargo/cmake not installed"; return 0; }
    if [ "$slip_ready" = 0 ]; then
        fetch_git "$work/slipstream" "$SLIP_REPO" "$SLIP_COMMIT" &&
            git -C "$work/slipstream" submodule update --init --recursive ||
            { record "$t" slipstream FAILED "source fetch failed"; warn "slipstream sources unavailable"; return 0; }
        slip_ready=1
    fi
    # scripts/build_picoquic.sh uses bash 4 syntax (${var,,}); macOS ships 3.2.
    path=$PATH
    if [ "$(bash -c 'echo ${BASH_VERSINFO[0]}')" -lt 4 ]; then
        for b in /opt/homebrew/bin /usr/local/bin; do
            [ -x "$b/bash" ] && [ "$("$b/bash" -c 'echo ${BASH_VERSINFO[0]}')" -ge 4 ] && { path="$b:$PATH"; break; }
        done
    fi
    rustup target add "$rt" >/dev/null 2>&1 || true
    optional "$t" slipstream "$f" -- slip_cargo "$rt" "${osx:-}" "$path" "$f"
    licence "$t" slipstream "$work/slipstream/LICENSE"
}
slip_cargo() { # rust-target osx-arch PATH output
    ( cd "$work/slipstream"
      export PATH="$3"
      export PICOQUIC_BUILD_DIR="$work/slipstream/.picoquic-build-$1"
      if [ -n "$2" ]; then
          export CMAKE_OSX_ARCHITECTURES="$2"
          export MACOSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-12.0}"
      fi
      cargo build --release --locked -p slipstream-client --target "$1" \
          --features openssl-vendored,picoquic-minimal-build ) || return 1
    cp "$work/slipstream/target/$1/release/slipstream-client" "$4"
    strip "$4" 2>/dev/null || true
}

# ------------------------------------------------------------------ main

for t in $targets; do
    log "################ $t ################"
    if wanted xray; then build_xray "$t"; fi
    mkdir -p "$(outdir "$t")"
    if wanted geo; then build_geo "$t"; fi
    if wanted sing-box; then build_singbox "$t"; fi
    if wanted psiphon; then build_psiphon "$t"; fi
    if wanted ghajar-helper; then build_helper "$t"; fi
    if wanted juicity; then build_juicity "$t"; fi
    for n in dnstt vaydns noizdns masterdns stormdns cottendns; do
        if wanted "$n"; then build_dns "$t" "$n"; fi
    done
    if wanted slipstream; then build_slipstream "$t"; fi
done

# ------------------------------------------------------------------ summary

echo
echo "================ desktop cores ================"
printf '%-10s %-15s %-8s %12s  %s\n' TARGET FILE STATUS BYTES NOTE
failed=0
for t in $targets; do
    out=$(outdir "$t")
    while IFS="$(printf '\t')" read -r rt name status note; do
        [ "$rt" = "$t" ] || continue
        size=-
        for cand in "$out/$name" "$out/$name.exe"; do [ -f "$cand" ] && { size=$(fsize "$cand"); break; }; done
        printf '%-10s %-15s %-8s %12s  %s\n' "$t" "$name" "$status" "$size" "$note"
    done < "$results"
    # The engine cannot run at all without these.
    for req in "xray$(exe "$t")" "sing-box$(exe "$t")" geoip.dat geosite.dat; do
        if wanted "$(echo "$req" | sed 's/\.exe$//; s/^geo.*\.dat$/geo/')" && [ ! -s "$out/$req" ]; then
            echo "ERROR: required $req missing for $t" >&2
            failed=1
        fi
    done
done
[ "$failed" = 0 ] || exit 1
echo "done: $targets"
