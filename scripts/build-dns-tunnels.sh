#!/bin/sh
# Build the DNS-tunnel family clients as native executables, one per ABI, into
# app/src/main/jniLibs/<abi>/lib<name>.so (same pattern as build-dnstt.sh):
#
#   libvaydns.so     net2share/vaydns        v0.2.8 (a0ff701)   CC0-1.0   Go module proxy, checksum-verified
#   libnoizdns.so    anonvector/noizdns      f76c585            AGPL-3.0  + its dnstt fork anonvector/dnstt 51bdba3 (AGPL-3.0)
#   libmasterdns.so  masterking32/MasterDnsVPN acbf1c6          MIT
#   libstormdns.so   NullRoute1970/StormDNS  ca2eb48            MIT
#   libcottendns.so  WhiteDNS/CottenDns      cdf084f            MIT
#
# Each is shipped unmodified as a separate program. Licence texts are copied
# into third_party/dns-tunnels/<name>/ next to what was built.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
work=${DNSFAM_WORKDIR:-/tmp/dnsfam-build}
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[ -n "$ndk" ] && [ -d "$ndk" ] || { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$work"

# name  repo  commit  package-dir  output
fetch() {
    name=$1; repo=$2; commit=$3
    dir="$work/$name"
    [ -d "$dir/.git" ] || git clone -q "$repo" "$dir"
    git -C "$dir" fetch -q origin "$commit" 2>/dev/null || git -C "$dir" fetch -q origin
    git -C "$dir" checkout -q "$commit"
    got=$(git -C "$dir" rev-parse HEAD)
    [ "$got" = "$commit" ] || { echo "::error::$name at $got, expected $commit" >&2; exit 1; }
}

build() { # srcdir pkg output abi goarch cc
    dest="$root/app/src/main/jniLibs/$4"; mkdir -p "$dest"
    ( cd "$1" && CGO_ENABLED=1 GOOS=android GOARCH="$5" CC="$toolchain/$6" \
        go build -trimpath -ldflags "-s -w -buildid= -linkmode=external -extldflags=-Wl,-z,max-page-size=16384" -o "$dest/$3" "$2" )
    machine=$(readelf -h "$dest/$3" | awk -F: '/Machine/ { gsub(/^ +/, "", $2); print $2 }')
    echo "$4 $3: $machine $(stat -c %s "$dest/$3") bytes"
}

licence() { mkdir -p "$root/third_party/dns-tunnels/$1"; cp "$2" "$root/third_party/dns-tunnels/$1/"; }

# vaydns through the module proxy (checksum database verifies the source).
mkdir -p "$work/vay"; cd "$work/vay"
[ -f go.mod ] || go mod init ghajar/vaybuild
go get github.com/net2share/vaydns/vaydns-client@v0.2.8
origin=$(go mod download -json github.com/net2share/vaydns@v0.2.8 | grep -o '"Hash": *"[0-9a-f]*"' | grep -o '[0-9a-f]\{40\}' || true)
[ -z "$origin" ] || [ "$origin" = "a0ff70110d5e96686ab8f4c8e1c44cd09be07a75" ] || { echo "::error::vaydns resolves to $origin" >&2; exit 1; }
licence vaydns "$(go env GOMODCACHE)/github.com/net2share/vaydns@v0.2.8/COPYING"

fetch noizdns https://github.com/anonvector/noizdns f76c585093c7c31e9be6c10a1b425be033eb4938
fetch dnstt https://github.com/anonvector/dnstt 51bdba3948d44b3175bc88ea40b2116bb9daff07
licence noizdns "$work/noizdns/LICENSE"; licence noizdns-dnstt "$work/dnstt/COPYING"
fetch masterdns https://github.com/masterking32/MasterDnsVPN acbf1c61f90786f41b975d2e2f616afbce292b29
fetch stormdns https://github.com/NullRoute1970/StormDNS ca2eb481fddd4a80d26b3a1a7c714b5aecacecfc
fetch cottendns https://github.com/WhiteDNS/CottenDns cdf084f528c5833eeb77f1b9a29ecf0ceae89f86
for n in masterdns stormdns cottendns; do licence $n "$work/$n/LICENSE"; done

for spec in "arm64-v8a arm64 aarch64-linux-android26-clang" "armeabi-v7a arm armv7a-linux-androideabi26-clang"; do
    set -- $spec
    build "$work/vay" github.com/net2share/vaydns/vaydns-client libvaydns.so "$1" "$2" "$3"
    build "$work/noizdns" ./cmd/noizdns-client libnoizdns.so "$1" "$2" "$3"
    build "$work/masterdns" ./cmd/client libmasterdns.so "$1" "$2" "$3"
    build "$work/stormdns" ./cmd/client libstormdns.so "$1" "$2" "$3"
    build "$work/cottendns" ./cmd/client libcottendns.so "$1" "$2" "$3"
done
echo "built the DNS tunnel family for both ABIs"
