#!/usr/bin/env bash
# Downloads the official Tor Expert Bundle (tor + lyrebird, the pluggable
# transports obfs4 / meek_lite / webtunnel / snowflake, and the geoip files)
# for the desktop VPN engine's Tor profiles (web-desktop/engine/tor.js) into
# web-desktop/core/<target>/tor/.
#
#   bash web-desktop/scripts/fetch-tor.sh [win-x64] [mac-x64] [mac-arm64] [linux-x64]
#   (no arguments: all four)
#
# Per target it produces (".exe" on Windows):
#
#   core/<target>/tor/tor                         the tor client
#   core/<target>/tor/*.so* | *.dylib | *.dll     its libraries, when the bundle has them
#   core/<target>/tor/pluggable_transports/lyrebird   obfs4, meek_lite, webtunnel, snowflake
#   core/<target>/tor/pluggable_transports/…      whatever else the bundle ships there
#                                                 (snowflake-client / conjure-client in
#                                                 older bundles, pt_config.json)
#   core/<target>/tor/geoip, geoip6               for exit-country selection (ExitNodes)
#   core/<target>/licenses/tor/                   the bundle's licence texts
#
# The bundle is the one Tor Browser <version> ships, from
#   https://dist.torproject.org/torbrowser/<version>/tor-expert-bundle-<os>-<arch>-<version>.tar.gz
# with <os>-<arch> = windows-x86_64, macos-x86_64, macos-aarch64, linux-x86_64.
# Every archive is checked against that release's sha256sums-signed-build.txt;
# with TOR_GPG=1 that file's detached signature is also verified against the
# Tor Browser Developers signing key (fetched by WKD, fingerprint pinned below).
#
# Environment:
#   TOR_VERSION   a Tor Browser release, e.g. 15.0.1 (default: the newest stable
#                 release listed on dist.torproject.org that has expert bundles)
#   TOR_MIRROR    base URL (default https://dist.torproject.org/torbrowser;
#                 https://archive.torproject.org/tor-package-archive/torbrowser works too)
#   TOR_WORKDIR   download cache (default: $RUNNER_TEMP or /tmp, /ghajar-tor)
#   TOR_GPG=1     also verify the checksum file's signature (needs gpg)
#
# Needs: bash 3.2+, curl, tar, sha256sum or shasum.
set -eu

MIRROR=${TOR_MIRROR:-https://dist.torproject.org/torbrowser}
# Tor Browser Developers (signing key), https://support.torproject.org/tbb/how-to-verify-signature/
TB_KEY_FPR=EF6E286DDA85EA2A4BA7DE684E2C6E8793298290

here=$(cd "$(dirname "$0")" && pwd)
desktop=$(cd "$here/.." && pwd)
work=${TOR_WORKDIR:-${RUNNER_TEMP:-/tmp}/ghajar-tor}
mkdir -p "$work"

targets="$*"
[ -n "$targets" ] || targets="win-x64 mac-x64 mac-arm64 linux-x64"
for t in $targets; do
    case "$t" in
        win-x64|mac-x64|mac-arm64|linux-x64) ;;
        *) echo "unknown target '$t' (use win-x64, mac-x64, mac-arm64, linux-x64)" >&2; exit 2 ;;
    esac
done

log() { printf '\n==> %s\n' "$*"; }
die() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error::$*"; else echo "ERROR: $*" >&2; fi
    exit 1
}
sha256() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
fetch() { curl -fsSL --retry 3 --retry-delay 2 -o "$2" "$1"; }

bundle_name() { # target version -> archive name
    case "$1" in
        win-x64) os=windows; arch=x86_64 ;;
        mac-x64) os=macos; arch=x86_64 ;;
        mac-arm64) os=macos; arch=aarch64 ;;
        linux-x64) os=linux; arch=x86_64 ;;
    esac
    echo "tor-expert-bundle-$os-$arch-$2.tar.gz"
}

# ------------------------------------------------------------------ version

version=${TOR_VERSION:-}
if [ -z "$version" ]; then
    log "finding the newest Tor Browser release on $MIRROR"
    index=$(curl -fsSL --retry 3 "$MIRROR/") || die "cannot list $MIRROR (set TOR_VERSION and/or TOR_MIRROR)"
    # Stable releases only: 14.5.7, 15.0 - not 15.0a3.
    # Newest first: version-sorted, then reversed (no `sort -V` / `tail -r` needed).
    candidates=$(printf '%s' "$index" | grep -oE 'href="[0-9]+\.[0-9]+(\.[0-9]+)*/"' | sed 's/href="//; s/\/"//' \
        | sort -t. -k1,1n -k2,2n -k3,3n -u | awk '{ a[NR] = $0 } END { for (i = NR; i > 0; i--) print a[i] }')
    for v in $candidates; do
        # The newest directory can exist before its files do: take the first with sums.
        if curl -fsSIL "$MIRROR/$v/sha256sums-signed-build.txt" >/dev/null 2>&1; then version=$v; break; fi
    done
    [ -n "$version" ] || die "no Tor Browser release with checksums found on $MIRROR"
fi
echo "Tor Browser $version"

# ------------------------------------------------------------------ checksums

sums="$work/$version-sha256sums-signed-build.txt"
[ -s "$sums" ] || fetch "$MIRROR/$version/sha256sums-signed-build.txt" "$sums" || die "no sha256sums-signed-build.txt for $version"
if [ "${TOR_GPG:-0}" = 1 ]; then
    command -v gpg >/dev/null 2>&1 || die "TOR_GPG=1 needs gpg"
    fetch "$MIRROR/$version/sha256sums-signed-build.txt.asc" "$sums.asc" || die "no signature for the checksum file"
    export GNUPGHOME="$work/gnupg"; mkdir -p "$GNUPGHOME"; chmod 700 "$GNUPGHOME"
    gpg --batch --quiet --auto-key-locate nodefault,wkd --locate-keys torbrowser@torproject.org >/dev/null 2>&1 || true
    gpg --batch --status-fd 1 --verify "$sums.asc" "$sums" 2>/dev/null | grep -q "VALIDSIG $TB_KEY_FPR" \
        || die "the checksum file of $version is not signed by $TB_KEY_FPR"
    echo "checksum file signature ok"
fi

# ------------------------------------------------------------------ per target

for t in $targets; do
    name=$(bundle_name "$t" "$version")
    log "$t: $name"
    want=$(grep " $name\$" "$sums" | cut -d' ' -f1 | head -1 || true)
    if [ -z "$want" ]; then
        echo "  $name is not in this release; expert bundles listed:" >&2
        grep -o 'tor-expert-bundle-[^ ]*' "$sums" >&2 || true
        die "$name not found in $version"
    fi
    archive="$work/$name"
    if [ ! -s "$archive" ] || [ "$(sha256 "$archive")" != "$want" ]; then
        fetch "$MIRROR/$version/$name" "$archive" || die "download of $name failed"
    fi
    [ "$(sha256 "$archive")" = "$want" ] || die "$name: checksum mismatch"

    unpack="$work/unpack-$t"
    rm -rf "$unpack"; mkdir -p "$unpack"
    tar -xzf "$archive" -C "$unpack"
    # The bundle is tor/ (tor, its libraries, pluggable_transports/), data/
    # (geoip, geoip6), docs/ and debug/; tolerate one extra top-level folder.
    src=$unpack
    [ -d "$src/tor" ] || src=$(dirname "$(find "$unpack" -type d -name tor -maxdepth 3 | head -1)")
    [ -d "$src/tor" ] || die "$name: no tor/ folder inside"

    out="$desktop/core/$t/tor"
    rm -rf "$out"; mkdir -p "$out"
    cp -R "$src/tor/." "$out/"
    for g in geoip geoip6; do
        f=$(find "$src" -type f -name "$g" | head -1)
        [ -n "$f" ] && cp "$f" "$out/$g" || echo "  warning: $g missing: exit countries will be ignored" >&2
    done
    lic="$desktop/core/$t/licenses/tor"; rm -rf "$lic"; mkdir -p "$lic"
    find "$src" -type f \( -iname 'LICENSE*' -o -iname 'COPYING*' \) -exec cp {} "$lic/" \; 2>/dev/null || true
    [ -d "$src/docs" ] && cp -R "$src/docs/." "$lic/" 2>/dev/null || true

    exe=""; [ "$t" = win-x64 ] && exe=.exe
    [ -f "$out/tor$exe" ] || die "$t: tor$exe missing after unpacking"
    lyre=$(find "$out" -type f -name "lyrebird$exe" | head -1)
    [ -n "$lyre" ] || echo "  warning: no lyrebird$exe: bridges (obfs4, snowflake, …) will be unavailable" >&2
    case "$t" in win-*) ;; *) find "$out" -type f \( -name tor -o -name lyrebird -o -name '*-client' \) -exec chmod 755 {} \; ;; esac
    printf 'Tor Expert Bundle %s (%s)\nsha256 %s\n' "$version" "$name" "$want" > "$lic/SOURCE"
    echo "  $(du -sh "$out" | cut -f1) in core/$t/tor: $(cd "$out" && ls | tr '\n' ' ')"
done
