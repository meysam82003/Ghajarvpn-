#!/bin/sh
# Build the nDPI fingerprint check (native/ndpi-classify, GPL-3.0) against
# nDPI 6.0 (ntop/nDPI tag 6.0, commit 1a5293396337f9a72dfee1fa070b2c4b0a0a3aaf,
# LGPL-3.0) for Android into app/src/main/jniLibs/<abi>/libndpiclassify.so.
# Only the library is built (--with-only-libndpi); the classifier selects
# NDPI_LICENSE_FOR_PROFIT_LGPL at run time, so nDPI's dual-licensed dissectors
# stay off. LGPL relinking: the pinned source, this script and classify.c are
# everything needed to rebuild and relink it.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
tag=6.0
commit=1a5293396337f9a72dfee1fa070b2c4b0a0a3aaf
work=${NDPI_WORK:-$root/build/ndpi}
rm -rf "$work"; mkdir -p "$work"
git clone --quiet --depth 1 --branch "$tag" https://github.com/ntop/nDPI "$work/src"
got=$(git -C "$work/src" rev-parse HEAD)
[ "$got" = "$commit" ] || { echo "ERROR: nDPI $tag is $got, expected $commit" >&2; exit 1; }
(cd "$work/src" && ./autogen.sh --with-only-libndpi >/dev/null)
mkdir -p "$root/third_party/ndpi"
cp "$work/src/COPYING" "$root/third_party/ndpi/LICENSE-nDPI"
cp "$work/src/README.license.md" "$root/third_party/ndpi/README.license.md"
for spec in "arm64-v8a aarch64-linux-android aarch64-linux-android26-clang" "armeabi-v7a arm-linux-androideabi armv7a-linux-androideabi26-clang"; do
    set -- $spec
    abi=$1 host=$2 cc="$toolchain/$3"
    b="$work/$abi"; cp -r "$work/src" "$b"
    (cd "$b" && CC="$cc" AR="$toolchain/llvm-ar" RANLIB="$toolchain/llvm-ranlib" \
        CFLAGS="-O2 -fPIC -Wl,-z,max-page-size=16384" \
        ./configure --host="$host" --with-only-libndpi --disable-shared >/dev/null && make -C src/lib -j"$(nproc)" libndpi.a >/dev/null)
    dest="$root/app/src/main/jniLibs/$abi"; mkdir -p "$dest"
    "$cc" -O2 -fPIE -pie -Wl,-z,max-page-size=16384 -I"$b/src/include" \
        -o "$dest/libndpiclassify.so" "$root/native/ndpi-classify/classify.c" "$b/src/lib/libndpi.a" -lm
    "$toolchain/llvm-strip" "$dest/libndpiclassify.so"
    machine=$(readelf -h "$dest/libndpiclassify.so" | awk -F: '/Machine/ { gsub(/^ +/, "", $2); print $2 }')
    echo "$abi libndpiclassify.so: $machine $(stat -c %s "$dest/libndpiclassify.so") bytes"
done
