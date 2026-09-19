#!/bin/sh
# Build the sing-box core as a native executable, one per ABI, into
# app/src/main/jniLibs/<abi>/libsingbox.so
#
# NOT a gomobile AAR, and that is the whole point of this file. See
# third_party/sing-box/README.md for why: two gomobile AARs cannot coexist in
# one Android process, and this app already ships one (ca.psiphon.aar, the
# Xray+Psiphon core). A plain binary run as a subprocess has no such problem,
# and it is the pattern AetherController already uses in this app.
#
# The .so name is not cosmetic. Android only extracts and marks executable the
# files under jniLibs that are named lib*.so, so a Go binary has to be called
# that to be runnable from nativeLibraryDir at all.
#
# Needs: go 1.26+, git, the Android NDK (ANDROID_NDK_HOME or ANDROID_NDK_ROOT).
# Takes several minutes and downloads a large module cache.
#
# Usage:  sh scripts/build-singbox-aar.sh
set -eu

SINGBOX_COMMIT=8330820fa62505f9574e4c35cd969d9af6eb7769
SINGBOX_REPO=https://github.com/SagerNet/sing-box

# From sing-box's own CI rather than its go.mod: go.mod's "go 1.25.5" is the
# minimum LANGUAGE version, while their release builds with 1.26.8.
GO_VERSION=1.26.8

root=$(cd "$(dirname "$0")/.." && pwd)
work=${SINGBOX_WORKDIR:-/tmp/sing-box}

# Which protocols exist in the binary is decided by build tags: a protocol
# whose registration sits behind a tag is absent, not disabled - sing-box ships
# an include/<name>_stub.go for each one. These are the tag-gated protocols
# this app wants.
#
#   with_quic        hysteria, hysteria2, tuic
#   with_wireguard   wireguard endpoints
#   with_utls        client hello fingerprinting
#   with_openconnect Cisco AnyConnect
#   with_openvpn     sing-box's own OpenVPN client
#   with_clash_api   the stats and selector API
#
# snell, anytls, ssh, tor, shadowtls, shadowsocksr, vless, vmess, trojan and
# shadowsocks need no tag and are always present.
#
# Left out on purpose: with_tailscale, with_usbip, with_naive_outbound. None
# was asked for and each pulls in a large dependency tree, against an APK that
# is already about 150 MB.
TAGS=with_quic,with_wireguard,with_utls,with_openconnect,with_openvpn,with_clash_api,badlinkname,tfogo_checklinkname0

# -checklinkname=0 is a LINKER flag and it is not optional. libbox and the
# main package pull private runtime symbols in with //go:linkname, and since Go
# 1.23 the linker refuses a pull-linkname to an unmarked symbol without it -
# failing at the very last step with, for example:
#
#   link: experimental/libbox: invalid reference to os.checkPidfdOnce
#
# The badlinkname build tag above is a separate mechanism and does NOT
# substitute for it. Both come from their cmd/internal/build_shared/flags.go.
#
# -s -w -buildid= strip the binary, which matters when it is shipped inside an
# APK that is already large.
LDFLAGS="-checklinkname=0 -X runtime.godebugDefault=multipathtcp=0,tlssha1=1 -s -w -buildid="

ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
if [ -z "$ndk" ] || [ ! -d "$ndk" ]; then
    echo "ERROR: set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) to an installed NDK" >&2
    exit 1
fi
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ ! -d "$toolchain" ]; then
    echo "ERROR: no linux-x86_64 toolchain under $ndk" >&2
    exit 1
fi

have=$(go env GOVERSION)
echo "go toolchain: $have (upstream builds with go$GO_VERSION)"

if [ ! -d "$work/.git" ]; then
    git clone "$SINGBOX_REPO" "$work"
fi
git -C "$work" fetch --all --tags
git -C "$work" checkout "$SINGBOX_COMMIT"

cd "$work"

# Only the two ABIs this app ships. The API level matches the app's minSdk
# path for the Psiphon AAR (26); a lower one buys nothing here.
build_abi() {
    abi=$1
    goarch=$2
    cc=$3
    dest="$root/app/src/main/jniLibs/$abi"
    mkdir -p "$dest"
    echo "building $abi ($goarch) with $(basename "$cc")"
    CGO_ENABLED=1 \
    GOOS=android \
    GOARCH="$goarch" \
    CC="$toolchain/$cc" \
    go build -v \
        -tags "$TAGS" \
        -ldflags "$LDFLAGS" \
        -trimpath \
        -o "$dest/libsingbox.so" \
        ./cmd/sing-box
    ls -lh "$dest/libsingbox.so"
}

build_abi arm64-v8a arm64 aarch64-linux-android26-clang
build_abi armeabi-v7a arm armv7a-linux-androideabi26-clang
if [ "${GHAJAR_EMULATOR_TEST:-false}" = "true" ]; then
    build_abi x86_64 amd64 x86_64-linux-android26-clang
fi

# Assert the protocols are in the artifact rather than trusting the tag list.
# A typo in a tag is silent, and a core without OpenConnect is
# indistinguishable from one with it until the first connect fails.
missing=0
for proto in openconnect snell anytls shadowtls ssh tor hysteria2 tuic; do
    if strings -n 4 "$root/app/src/main/jniLibs/arm64-v8a/libsingbox.so" \
         | grep -q "protocol/$proto"; then
        echo "ok: $proto"
    else
        echo "MISSING: $proto - check the build tags" >&2
        missing=1
    fi
done
[ "$missing" -eq 0 ] || exit 1

# And that it is actually an executable for the right machine, because a Go
# binary that built for the wrong GOARCH still lands in the right directory
# and fails only at exec time on a real phone.
for abi in arm64-v8a armeabi-v7a; do
    so="$root/app/src/main/jniLibs/$abi/libsingbox.so"
    machine=$(readelf -h "$so" | awk -F: '/Machine/ { gsub(/^ +/, "", $2); print $2 }')
    echo "$abi: $machine"
    case "$abi:$machine" in
        arm64-v8a:*AArch64*) ;;
        armeabi-v7a:*ARM*) ;;
        *) echo "::error::$abi built for $machine"; exit 1 ;;
    esac
done

echo "built libsingbox.so for both ABIs at sing-box $SINGBOX_COMMIT"
