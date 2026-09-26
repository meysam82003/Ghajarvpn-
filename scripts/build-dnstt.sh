#!/bin/sh
# Build the dnstt DNS tunnel client as a native executable, one per ABI, into
# app/src/main/jniLibs/<abi>/libdnstt.so
#
# Same pattern as scripts/build-singbox.sh: a plain Go binary run as a
# subprocess (a second gomobile AAR cannot share the process with the Xray
# one), named lib*.so because Android only extracts and marks executable the
# files under jniLibs that are named that way.
#
# Source: upstream dnstt by David Fifield (CC0-1.0), fetched through the Go
# module proxy at a pinned version. `go mod download` verifies the module
# against the public checksum database (sum.golang.org), so what is built is
# exactly the published v1.20260501.0 (git 0c5c52a57d899c05428c116898941761a2ed83c2)
# and not whatever a mirror serves. The upstream git host refuses this CI's
# network, which is why the module proxy is used.
#
# Needs: go 1.24+, the Android NDK (ANDROID_NDK_HOME or ANDROID_NDK_ROOT).
set -eu

DNSTT_MODULE=www.bamsoftware.com/git/dnstt.git
DNSTT_VERSION=v1.20260501.0
DNSTT_COMMIT=0c5c52a57d899c05428c116898941761a2ed83c2

root=$(cd "$(dirname "$0")/.." && pwd)
work=${DNSTT_WORKDIR:-/tmp/dnstt-build}

ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
if [ -z "$ndk" ] || [ ! -d "$ndk" ]; then
    echo "ERROR: set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) to an installed NDK" >&2
    exit 1
fi
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"

mkdir -p "$work"
cd "$work"
[ -f go.mod ] || go mod init ghajar/dnsttbuild
# Fetching the package (not just the module) records its dependencies in
# go.sum at the versions dnstt's own go.mod pins.
go get "$DNSTT_MODULE/dnstt-client@$DNSTT_VERSION"

# The module proxy's record of where this version came from must match the
# commit this script pins; a moved tag fails the build instead of shipping.
origin=$(go mod download -json "$DNSTT_MODULE@$DNSTT_VERSION" | grep -o '"Hash": *"[0-9a-f]*"' | grep -o '[0-9a-f]\{40\}' || true)
if [ -n "$origin" ] && [ "$origin" != "$DNSTT_COMMIT" ]; then
    echo "::error::dnstt $DNSTT_VERSION resolves to $origin, expected $DNSTT_COMMIT" >&2
    exit 1
fi

build_abi() {
    abi=$1; goarch=$2; cc=$3
    dest="$root/app/src/main/jniLibs/$abi"
    mkdir -p "$dest"
    echo "building dnstt-client for $abi ($goarch)"
    CGO_ENABLED=1 GOOS=android GOARCH="$goarch" CC="$toolchain/$cc" \
    go build -trimpath -ldflags "-s -w -buildid=" -o "$dest/libdnstt.so" "$DNSTT_MODULE/dnstt-client"
    ls -lh "$dest/libdnstt.so"
}

build_abi arm64-v8a arm64 aarch64-linux-android26-clang
build_abi armeabi-v7a arm armv7a-linux-androideabi26-clang

for abi in arm64-v8a armeabi-v7a; do
    so="$root/app/src/main/jniLibs/$abi/libdnstt.so"
    machine=$(readelf -h "$so" | awk -F: '/Machine/ { gsub(/^ +/, "", $2); print $2 }')
    echo "$abi: $machine"
    case "$abi:$machine" in
        arm64-v8a:*AArch64*) ;;
        armeabi-v7a:*ARM*) ;;
        *) echo "::error::$abi built for $machine"; exit 1 ;;
    esac
done

# Keep the licence next to what was built from it.
mkdir -p "$root/third_party/dnstt"
cp "$(go env GOMODCACHE)/$DNSTT_MODULE@$DNSTT_VERSION/COPYING" "$root/third_party/dnstt/COPYING"
echo "built libdnstt.so for both ABIs at dnstt $DNSTT_VERSION ($DNSTT_COMMIT)"
