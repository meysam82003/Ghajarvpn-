#!/bin/sh
# Deferred native preparation only. NOT invoked by base APK tasks or CI.
# Requires Rust targets and NDK. Preserve Cargo.lock, no floating dependencies.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
work=${SHADOWQUIC_WORKDIR:?Set SHADOWQUIC_WORKDIR to a dedicated checkout directory}
ndk=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
pin=5540e3a32ca73c85af125723e4262e02cf28ebcd
if [ ! -d "$work/.git" ]; then git clone https://github.com/spongebob888/shadowquic "$work"; fi
git -C "$work" fetch origin "$pin"
[ -z "$(git -C "$work" status --porcelain)" ] || { echo 'Refusing dirty upstream checkout' >&2; exit 1; }
git -C "$work" checkout --detach "$pin"
[ "$(git -C "$work" rev-parse HEAD)" = "$pin" ]
for pair in 'aarch64-linux-android arm64-v8a aarch64-linux-android' 'armv7-linux-androideabi armeabi-v7a armv7a-linux-androideabi'; do
    set -- $pair
    target=$1; abi=$2; clang_target=$3
    linker="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/${clang_target}26-clang"
    [ -x "$linker" ]
    key=$(printf '%s' "$target" | tr '[:lower:]-' '[:upper:]_')
    (cd "$work" && env "CARGO_TARGET_${key}_LINKER=$linker" "CC=$linker" RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384' cargo build --locked --release --target "$target" -p shadowquic --bin shadowquic)
    mkdir -p "$root/plugins/shadowquic/src/main/jniLibs/$abi"
    cp "$work/target/$target/release/shadowquic" "$root/plugins/shadowquic/src/main/jniLibs/$abi/libshadowquic.so"
done
mkdir -p "$root/plugins/shadowquic/src/main/assets/licenses"
cp "$work/LICENSE" "$root/plugins/shadowquic/src/main/assets/licenses/shadowquic-MIT.txt"
cp "$work/Cargo.lock" "$root/plugins/shadowquic/src/main/assets/Cargo.lock"
