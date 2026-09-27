#!/bin/sh
# Build lyrebird (Tor pluggable transports: obfs4, meek_lite, webtunnel,
# snowflake; BSD-3-Clause) for Android into jniLibs/<abi>/liblyrebird.so.
# Pinned through the Go module proxy (checksum database):
#   gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird
#   v0.0.0-20260921142919-75ef9b2c1f18 (git 75ef9b2c1f18da5c5d1b9ba855f42c8e94882bcf)
set -eu
MOD=gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird
VER=v0.0.0-20260921142919-75ef9b2c1f18
root=$(cd "$(dirname "$0")/.." && pwd)
work=${PT_WORKDIR:-/tmp/pt-build}
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$work"; cd "$work"
[ -f go.mod ] || go mod init ghajar/ptbuild
go get "$MOD/cmd/lyrebird@$VER"
for spec in "arm64-v8a arm64 aarch64-linux-android26-clang" "armeabi-v7a arm armv7a-linux-androideabi26-clang"; do
    set -- $spec
    dest="$root/app/src/main/jniLibs/$1"; mkdir -p "$dest"
    CGO_ENABLED=1 GOOS=android GOARCH="$2" CC="$toolchain/$3" \
        go build -trimpath -ldflags "-checklinkname=0 -s -w -buildid=" -o "$dest/liblyrebird.so" "$MOD/cmd/lyrebird"
    echo "$1 liblyrebird.so $(stat -c %s "$dest/liblyrebird.so") bytes"
done
mkdir -p "$root/third_party/tor-pt"
cp "$(go env GOMODCACHE)/$MOD@$VER/LICENSE" "$root/third_party/tor-pt/LICENSE-lyrebird"
