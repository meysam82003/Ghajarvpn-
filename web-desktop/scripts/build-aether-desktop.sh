#!/usr/bin/env bash
# Builds Aether (CluvexStudio/Aether 2.1.0, AGPL-3.0, run unmodified as a
# separate program) for the desktop VPN engine (web-desktop/engine/aether.js)
# into web-desktop/core/<target>/aether[.exe].
#
#   bash web-desktop/scripts/build-aether-desktop.sh [win-x64] [mac-x64] [mac-arm64] [linux-x64]
#   (no arguments: the targets this host can build, see below)
#
# Same source and pin as the Android build (scripts/build-aether.sh):
# commit 21e7150 of github.com/CluvexStudio/Aether, without the `tor` feature
# (the app runs the official tor itself, see fetch-tor.sh). AETHER_SRC=<dir>
# builds a local tree instead (e.g. native/Aether, which holds aether/ and
# quiche/): it is copied to the work folder first, so the tree is never written.
#
# Aether links BoringSSL through quiche / boring (built from source with
# CMake, bindgen needs libclang), so every target is built natively on its own
# OS - cross-compiling BoringSSL is not attempted:
#
#   linux-x64   on Linux x86_64. The binary needs the build host's glibc or
#               newer: build on the oldest Ubuntu you support (ubuntu-22.04 →
#               glibc 2.35). Needs gcc/clang, cmake, libclang.
#   mac-x64     on macOS (either CPU; rustup adds x86_64-apple-darwin).
#   mac-arm64   on macOS (either CPU; rustup adds aarch64-apple-darwin).
#               Needs the Xcode command line tools and cmake.
#   win-x64     on Windows (Git Bash / MSYS) with the MSVC toolchain
#               (x86_64-pc-windows-msvc), cmake, NASM and LLVM (libclang).
#
# Environment:
#   AETHER_WORKDIR  where the source is fetched and built (default: $RUNNER_TEMP or /tmp, /aether-desktop)
#   AETHER_SRC      a local Aether tree to build instead of the pinned commit
#
# Needs: git, rustup (the crate asks for rust 1.98; current stable is installed).
set -eu
REPO=https://github.com/CluvexStudio/Aether
COMMIT=21e7150ac2225caa01cbb96ac572b5c0cc1e1dc2

here=$(cd "$(dirname "$0")" && pwd)
desktop=$(cd "$here/.." && pwd)
work=${AETHER_WORKDIR:-${RUNNER_TEMP:-/tmp}/aether-desktop}
mkdir -p "$work"
work=$(cd "$work" && pwd)

log() { printf '\n==> %s\n' "$*"; }
die() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error::$*"; else echo "ERROR: $*" >&2; fi
    exit 1
}

host=$(uname -s)
case "$host" in
    Linux) native="linux-x64" ;;
    Darwin) native="mac-x64 mac-arm64" ;;
    MINGW*|MSYS*|CYGWIN*) native="win-x64" ;;
    *) die "unsupported build host $host" ;;
esac
targets="$*"
[ -n "$targets" ] || targets=$native
for t in $targets; do
    case " $native " in *" $t "*) ;; *) die "$t cannot be built on $host (BoringSSL is built natively; use a $t runner)" ;; esac
done

triple() {
    case "$1" in
        linux-x64) echo x86_64-unknown-linux-gnu ;;
        mac-x64) echo x86_64-apple-darwin ;;
        mac-arm64) echo aarch64-apple-darwin ;;
        win-x64) echo x86_64-pc-windows-msvc ;;
    esac
}

# ------------------------------------------------------------------ source

src="$work/src"
if [ -n "${AETHER_SRC:-}" ]; then
    [ -d "$AETHER_SRC/aether" ] && [ -d "$AETHER_SRC/quiche" ] || die "AETHER_SRC must hold aether/ and quiche/"
    log "copying $AETHER_SRC"
    rm -rf "$src"; mkdir -p "$src"
    cp -R "$AETHER_SRC/aether" "$AETHER_SRC/quiche" "$src/"
    [ -f "$AETHER_SRC/LICENSE" ] && cp "$AETHER_SRC/LICENSE" "$src/LICENSE"
    rm -rf "$src/aether/target"
else
    log "fetching $REPO at $COMMIT"
    # init + fetch rather than clone, so a cached target/ in $src can be restored first.
    if [ ! -d "$src/.git" ]; then
        mkdir -p "$src"
        git -C "$src" init -q
        git -C "$src" remote add origin "$REPO"
    fi
    git -C "$src" fetch -q --depth 1 origin "$COMMIT" 2>/dev/null || git -C "$src" fetch -q origin
    git -C "$src" checkout -q -f "$COMMIT"
    [ "$(git -C "$src" rev-parse HEAD)" = "$COMMIT" ] || die "Aether not at $COMMIT"
fi

rustup toolchain install stable --profile minimal >/dev/null
rustup default stable >/dev/null

# ------------------------------------------------------------------ build

for t in $targets; do
    tr=$(triple "$t")
    exe=""; [ "$t" = win-x64 ] && exe=.exe
    log "$t ($tr)"
    rustup target add "$tr" >/dev/null
    ( cd "$src/aether" && CARGO_TARGET_DIR="$work/target" cargo build --release --locked --target "$tr" )
    bin="$work/target/$tr/release/aether$exe"
    [ -s "$bin" ] || die "$t: aether$exe was not produced"
    out="$desktop/core/$t"
    mkdir -p "$out/licenses"
    cp "$bin" "$out/aether$exe"
    [ "$t" = win-x64 ] || chmod 755 "$out/aether$exe"
    # The macOS cores are signed ad hoc by build/after-pack.js when the app is packed.
    [ -f "$src/LICENSE" ] && cp "$src/LICENSE" "$out/licenses/aether.txt"
    "$out/aether$exe" --version >/dev/null 2>&1 || echo "  warning: core/$t/aether$exe does not run on this host" >&2
    echo "  core/$t/aether$exe: $(wc -c < "$out/aether$exe" | tr -d ' ') bytes"
done
