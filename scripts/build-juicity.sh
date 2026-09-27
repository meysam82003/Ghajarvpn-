#!/bin/sh
# Build juicity-client (juicity/juicity v0.5.0 = 88dbf4f, AGPL-3.0) for
# Android into jniLibs/<abi>/libjuicity.so. Built inside its own module
# because its go.mod carries replace directives. Shipped unmodified as a
# separate program; its source is offered as the AGPL requires
# (third_party/juicity/README.md).
set -eu
REPO=https://github.com/juicity/juicity
COMMIT=88dbf4f8efc54d70caf319ec79d0b7f12d70faf1
root=$(cd "$(dirname "$0")/.." && pwd)
work=${JUICITY_WORKDIR:-/tmp/juicity-build}
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -d "$work/.git" ] || git clone -q "$REPO" "$work"
git -C "$work" fetch -q origin "$COMMIT" 2>/dev/null || git -C "$work" fetch -q --tags origin
git -C "$work" checkout -q "$COMMIT"
[ "$(git -C "$work" rev-parse HEAD)" = "$COMMIT" ] || { echo "::error::juicity not at $COMMIT" >&2; exit 1; }
for spec in "arm64-v8a arm64 aarch64-linux-android26-clang" "armeabi-v7a arm armv7a-linux-androideabi26-clang"; do
    set -- $spec
    dest="$root/app/src/main/jniLibs/$1"; mkdir -p "$dest"
    ( cd "$work" && CGO_ENABLED=1 GOOS=android GOARCH="$2" CC="$toolchain/$3" \
        go build -trimpath -ldflags "-s -w -buildid=" -o "$dest/libjuicity.so" ./cmd/client )
    echo "$1 libjuicity.so $(stat -c %s "$dest/libjuicity.so") bytes"
done
mkdir -p "$root/third_party/juicity"; cp "$work/LICENSE" "$root/third_party/juicity/LICENSE"
