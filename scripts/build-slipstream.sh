#!/bin/sh
# Build slipstream-client (QUIC over DNS) for Android into
# app/src/main/jniLibs/<abi>/libslipstream.so.
#
#   Mygod/slipstream-rust 7de506b222c83ae97a19a618e33fe26899af1bd5 (Apache-2.0)
#   picoquic: the submodule commit pinned by that tree (vendor/picoquic)
#   OpenSSL:  vendored through openssl-src, version pinned by Cargo.lock (--locked)
#
# Upstream supports Android builds (scripts/build_picoquic.sh uses the NDK's
# CMake toolchain when TARGET contains "android").
set -eu

SLIP_REPO=https://github.com/Mygod/slipstream-rust
SLIP_COMMIT=7de506b222c83ae97a19a618e33fe26899af1bd5

root=$(cd "$(dirname "$0")/.." && pwd)
work=${SLIP_WORKDIR:-/tmp/slipstream-build}
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
export ANDROID_NDK_HOME="$ndk"

# The vendored OpenSSL build calls the old GNU-prefixed binutils
# (aarch64-linux-android-ranlib, arm-linux-androideabi-ar, ...), which modern
# NDKs no longer ship; point those names at the NDK's llvm tools.
shim="${SLIP_WORKDIR:-/tmp/slipstream-build}.ndk-shim"
mkdir -p "$shim"
for p in aarch64-linux-android arm-linux-androideabi armv7a-linux-androideabi; do
    for t in ar ranlib nm strip; do ln -sf "$toolchain/llvm-$t" "$shim/$p-$t"; done
done
export PATH="$shim:$PATH"

[ -d "$work/.git" ] || git clone -q "$SLIP_REPO" "$work"
git -C "$work" fetch -q origin "$SLIP_COMMIT" 2>/dev/null || true
git -C "$work" checkout -q "$SLIP_COMMIT"
[ "$(git -C "$work" rev-parse HEAD)" = "$SLIP_COMMIT" ] || { echo "::error::slipstream not at $SLIP_COMMIT" >&2; exit 1; }
git -C "$work" submodule update --init --recursive

# The NDK toolchain file limits find_library/find_path to the sysroot
# (CMAKE_FIND_ROOT_PATH_MODE_*=ONLY), so picoquic's find_package(OpenSSL)
# cannot see the vendored openssl-src build that build.rs passes in
# OPENSSL_ROOT_DIR. Add that directory to the find root path.
git -C "$work" checkout -q -- scripts/build_picoquic.sh
sed -i 's|^  CMAKE_ARGS+=("-DCMAKE_TOOLCHAIN_FILE=${TOOLCHAIN_FILE}")|&\n  if [[ -n "${OPENSSL_ROOT_DIR:-}" ]]; then CMAKE_ARGS+=("-DCMAKE_FIND_ROOT_PATH=${OPENSSL_ROOT_DIR}"); fi|' \
    "$work/scripts/build_picoquic.sh"
grep -q 'CMAKE_FIND_ROOT_PATH=' "$work/scripts/build_picoquic.sh" || { echo "::error::build_picoquic.sh patch did not apply" >&2; exit 1; }

rustup target add aarch64-linux-android armv7-linux-androideabi

build() { # abi rust-target clang-prefix env-suffix
    abi=$1; target=$2; cc="$toolchain/${3}26-clang"; up=$4
    export ANDROID_ABI="$abi" ANDROID_PLATFORM=android-26
    export PICOQUIC_BUILD_DIR="$work/.picoquic-build-$abi"
    eval "export CC_$(echo "$target" | tr '-' '_')=\"$cc\""
    # slipstream-ffi compiles its own C shims for Android with this (or $CC,
    # which would also hit host builds), falling back to the host "cc".
    export RUST_ANDROID_GRADLE_CC="$cc"
    eval "export AR_$(echo "$target" | tr '-' '_')=\"$toolchain/llvm-ar\""
    eval "export CARGO_TARGET_${up}_LINKER=\"$cc\""
    # 16 KB page alignment for Android 15+ devices.
    eval "export CARGO_TARGET_${up}_RUSTFLAGS=\"-C link-arg=-Wl,-z,max-page-size=16384\""
    ( cd "$work" && cargo build --release --locked -p slipstream-client --target "$target" \
        --features openssl-vendored,picoquic-minimal-build )
    dest="$root/app/src/main/jniLibs/$abi"; mkdir -p "$dest"
    cp "$work/target/$target/release/slipstream-client" "$dest/libslipstream.so"
    "$toolchain/llvm-strip" "$dest/libslipstream.so"
    machine=$(readelf -h "$dest/libslipstream.so" | awk -F: '/Machine/ { gsub(/^ +/, "", $2); print $2 }')
    echo "$abi libslipstream.so: $machine $(stat -c %s "$dest/libslipstream.so") bytes"
}

build arm64-v8a aarch64-linux-android aarch64-linux-android AARCH64_LINUX_ANDROID
build armeabi-v7a armv7-linux-androideabi armv7a-linux-androideabi ARMV7_LINUX_ANDROIDEABI

mkdir -p "$root/third_party/dns-tunnels/slipstream"
cp "$work/LICENSE" "$root/third_party/dns-tunnels/slipstream/LICENSE"
echo "built libslipstream.so at $SLIP_COMMIT"
