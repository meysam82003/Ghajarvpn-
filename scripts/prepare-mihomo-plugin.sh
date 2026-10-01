#!/bin/sh
# Deferred preparation. Never part of base APK tasks or CI. No Xray/zeptun changes.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
work=${MIHOMO_WORKDIR:?Set MIHOMO_WORKDIR to a dedicated checkout}
ndk=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
pin=3189346611caeba73aa87feaf708e4fd65115d16
if [ ! -d "$work/.git" ]; then git clone https://github.com/appshubcc/Bettbox "$work"; fi
git -C "$work" fetch origin "$pin"
[ -z "$(git -C "$work" status --porcelain)" ] || { echo 'Refusing dirty upstream checkout' >&2; exit 1; }
git -C "$work" checkout --detach "$pin"
[ "$(git -C "$work" rev-parse HEAD)" = "$pin" ]
core="$work/core/Clash.Meta"
mkdir -p "$core/cmd/ghajar-plugin"
trap 'rm -f "$core/cmd/ghajar-plugin/main.go" "$core/cmd/ghajar-plugin/path_policy.go"; rmdir "$core/cmd/ghajar-plugin" 2>/dev/null || true' EXIT
cp "$root/native/plugin-mihomo/main.go" "$root/native/plugin-mihomo/path_policy.go" "$core/cmd/ghajar-plugin/"
for pair in 'arm64 arm64-v8a aarch64-linux-android' 'arm armeabi-v7a armv7a-linux-androideabi'; do
    set -- $pair
    arch=$1; abi=$2; clang_target=$3
    mkdir -p "$root/plugins/mihomo/src/main/jniLibs/$abi"
    (cd "$core" && GOOS=android GOARCH="$arch" GOARM=7 CGO_ENABLED=1 CC="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/${clang_target}26-clang" go build -tags=with_gvisor -mod=readonly -trimpath -ldflags='-checklinkname=0 -s -w -extldflags=-Wl,-z,max-page-size=16384' -o "$root/plugins/mihomo/src/main/jniLibs/$abi/libmihomoghajar.so" ./cmd/ghajar-plugin)
done
mkdir -p "$root/plugins/mihomo/src/main/assets/licenses"
cp "$core/LICENSE" "$root/plugins/mihomo/src/main/assets/licenses/mihomo-GPL-3.0.txt"
cp "$core/go.mod" "$root/plugins/mihomo/src/main/assets/go.mod"
cp "$core/go.sum" "$root/plugins/mihomo/src/main/assets/go.sum"
