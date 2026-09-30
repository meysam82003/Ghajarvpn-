#!/bin/sh
# Build Aether (CluvexStudio/Aether 2.1.0, AGPL-3.0, shipped unmodified as a separate program) for Android into
# jniLibs/<abi>/libaether.so, from source at a pinned commit.
#
# Replaces the 1.9.0 executables that were committed as binaries: the
# release is now reproducible from this script and the commit below, the same
# way zeptun, sing-box and the DNS tunnels are.
#
# Built without the `tor` feature: that feature bundles arti (a Tor client in
# Rust) and the app already ships Tor with its own pluggable transports. The
# --psiphon options need an external psiphon executable and are not used.
#
# Needs: rustup, the Android NDK (ANDROID_NDK_HOME), cmake and ninja (quiche
# builds BoringSSL). ABIS defaults to the two ARM ABIs; CI adds x86_64 for the
# emulator split.
set -eu
REPO=https://github.com/CluvexStudio/Aether
COMMIT=21e7150ac2225caa01cbb96ac572b5c0cc1e1dc2
root=$(cd "$(dirname "$0")/.." && pwd)
work=${AETHER_WORKDIR:-/tmp/aether-build}
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
ABIS=${ABIS:-"arm64-v8a armeabi-v7a"}

# init + fetch rather than clone: CI restores a cached target/ into $work
# first, and git clone refuses a directory that is not empty.
if [ ! -d "$work/.git" ]; then
    mkdir -p "$work"
    git -C "$work" init -q
    git -C "$work" remote add origin "$REPO"
fi
git -C "$work" fetch -q --depth 1 origin "$COMMIT" 2>/dev/null || git -C "$work" fetch -q origin
git -C "$work" checkout -q -f "$COMMIT"
[ "$(git -C "$work" rev-parse HEAD)" = "$COMMIT" ] || { echo "::error::Aether not at $COMMIT" >&2; exit 1; }

# The crate asks for rust 1.98; take the current stable rather than whatever
# the runner image happens to carry.
rustup toolchain install stable --profile minimal >/dev/null
rustup default stable >/dev/null
command -v cargo-ndk >/dev/null || cargo install cargo-ndk --locked

triple() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        armeabi-v7a) echo armv7-linux-androideabi ;;
        x86_64) echo x86_64-linux-android ;;
    esac
}

for abi in $ABIS; do
    t=$(triple "$abi")
    rustup target add "$t" >/dev/null
    # 16 KB pages (Android 15+): the same load alignment every other native
    # file in the APK is checked for.
    ( cd "$work/aether" && RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384" \
        cargo ndk -t "$abi" --platform 26 build --release --locked )
    dest="$root/app/src/main/jniLibs/$abi"; mkdir -p "$dest"
    cp "$work/aether/target/$t/release/aether" "$dest/libaether.so"
    "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$dest/libaether.so" 2>/dev/null || true
    echo "$abi libaether.so $(stat -c %s "$dest/libaether.so") bytes, sha256 $(sha256sum "$dest/libaether.so" | cut -c1-16)"
done
mkdir -p "$root/third_party/aether"
cp "$work/LICENSE" "$root/third_party/aether/LICENSE"
printf 'CluvexStudio/Aether\ncommit: %s (release 2.1.0)\nbuilt by scripts/build-aether.sh without the tor feature\n' "$COMMIT" > "$root/third_party/aether/README.md"
