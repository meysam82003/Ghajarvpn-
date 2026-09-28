#!/bin/sh
# Build native/ghajar-helper (GPL-3.0, this repository) for Android into
# app/src/main/jniLibs/<abi>/libghajarhelper.so. Dependencies are pinned by
# native/ghajar-helper/go.sum (amneziawg-go v3.1.20260828, mieru v3.38.0,
# brook 5ad0c40); -mod=readonly refuses anything not already pinned.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
cd "$root/native/ghajar-helper"
# The module proxy drops the odd download mid-stream (INTERNAL_ERROR); fetch
# the pinned modules up front with retries, falling back to direct from the
# origin. go.sum still pins every byte, so the fallback cannot change them.
export GOPROXY="${GOPROXY:-https://proxy.golang.org,direct}"
for attempt in 1 2 3 4; do
    go mod download && break
    [ "$attempt" = 4 ] && { echo "::error::go mod download failed 4 times" >&2; exit 1; }
    echo "go mod download failed (attempt $attempt); retrying" >&2
    sleep $((attempt * 10))
done
go mod verify
for spec in "arm64-v8a arm64 aarch64-linux-android26-clang" "armeabi-v7a arm armv7a-linux-androideabi26-clang"; do
    set -- $spec
    dest="$root/app/src/main/jniLibs/$1"; mkdir -p "$dest"
    CGO_ENABLED=1 GOOS=android GOARCH="$2" CC="$toolchain/$3" \
        go build -mod=readonly -trimpath -ldflags "-s -w -buildid= -linkmode=external -extldflags=-Wl,-z,max-page-size=16384" -o "$dest/libghajarhelper.so" .
    machine=$(readelf -h "$dest/libghajarhelper.so" | awk -F: '/Machine/ { gsub(/^ +/, "", $2); print $2 }')
    echo "$1 libghajarhelper.so: $machine $(stat -c %s "$dest/libghajarhelper.so") bytes"
done
