#!/bin/sh
# Build app/libs/libbox.aar: the sing-box core, as a gomobile AAR.
#
# CI runs the same commands (see the "Build the sing-box core" step in
# .github/workflows/android.yml). This script exists so a local build is
# possible at all: libbox.aar is a compile-time dependency, not an optional
# runtime one like zeptun, so without it the app does not build.
#
# Needs: go 1.25+, git, the Android SDK and NDK (ANDROID_HOME / ANDROID_NDK_HOME),
# and a JDK. Takes several minutes and downloads a large module cache.
#
# Usage:  sh scripts/build-singbox-aar.sh
set -eu

# Pinned, not "latest". An unpinned core would change what the app is under
# you, and the protocol assertions at the end of this script are only
# meaningful against a known tree.
SINGBOX_COMMIT=8330820fa62505f9574e4c35cd969d9af6eb7769
SINGBOX_REPO=https://github.com/SagerNet/sing-box

# These two come from sing-box's OWN CI, not from its go.mod, and the
# difference is not academic - reading go.mod instead cost a failed build:
#
#   go.mod says "go 1.25.5", which is the minimum LANGUAGE version. Their CI
#   builds with 1.26.8. experimental/libbox/pidfd_android.go has a
#   //go:linkname to os.checkPidfdOnce, a private runtime symbol, and on 1.25
#   the link fails outright with "invalid reference to os.checkPidfdOnce".
#
#   go.mod pins gomobile v0.1.12 as a LIBRARY. Their Makefile's lib_install
#   installs the v0.1.13 TOOL. Those are different things and the tool is the
#   one that has to match.
GO_VERSION=1.26.8
GOMOBILE_VERSION=v0.1.13

root=$(cd "$(dirname "$0")/.." && pwd)
work=${SINGBOX_WORKDIR:-/tmp/sing-box}

# Which protocols exist in the binary is decided by build tags: a protocol
# whose registration sits behind a tag is absent, not disabled - sing-box ships
# an include/<name>_stub.go for each one. These are the tag-gated protocols
# this app wants.
#
#   with_quic        hysteria, hysteria2, tuic
#   with_wireguard   wireguard endpoints
#   with_utls        client hello fingerprinting
#   with_openconnect Cisco AnyConnect
#   with_openvpn     sing-box's own OpenVPN client
#   with_clash_api   the stats and selector API the UI reads
#
# snell, anytls, ssh, tor, shadowtls, shadowsocksr, vless, vmess, trojan and
# shadowsocks need no tag and are always present.
#
# Left out on purpose: with_tailscale, with_usbip, with_naive_outbound. None
# was asked for and each pulls in a large dependency tree, and this APK is
# already about 150 MB.
#
# badlinkname and tfogo_checklinkname0 are not features. They are the linkname
# workarounds sing-box's own release build passes, and the build fails on a
# current Go toolchain without them.
TAGS=with_quic,with_wireguard,with_utls,with_openconnect,with_openvpn,with_clash_api,badlinkname,tfogo_checklinkname0

if [ ! -d "$work/.git" ]; then
    git clone "$SINGBOX_REPO" "$work"
fi
git -C "$work" fetch --all --tags
git -C "$work" checkout "$SINGBOX_COMMIT"

# Say the toolchain out loud rather than hoping. A mismatch here fails at the
# link step with a message about a runtime symbol, which reads like a compiler
# bug rather than a version problem.
have=$(go env GOVERSION)
echo "go toolchain: $have (this core needs go$GO_VERSION or newer)"
case "$have" in
    go1.2[6-9]*|go[2-9]*) ;;
    *)
        echo "ERROR: sing-box needs go$GO_VERSION; $have will fail at the link" >&2
        echo "step with 'invalid reference to os.checkPidfdOnce'." >&2
        exit 1
        ;;
esac

go install "github.com/sagernet/gomobile/cmd/gomobile@$GOMOBILE_VERSION"
go install "github.com/sagernet/gomobile/cmd/gobind@$GOMOBILE_VERSION"
PATH="$(go env GOPATH)/bin:$PATH"
export PATH

mkdir -p "$root/app/libs"

# Only the two ABIs this app ships. gomobile's default "android" target builds
# four, which adds well over a hundred megabytes for emulator architectures no
# release targets.
#
# -javapkg and -libname are not cosmetic: they decide the Java package the
# bindings land in (io.nekohasekai.libbox) and the name of the shared library.
# The Kotlin wrapper imports that exact package.
cd "$work"
gomobile bind -v \
    -o "$root/app/libs/libbox.aar" \
    -target android/arm64,android/arm \
    -androidapi 24 \
    -javapkg=io.nekohasekai \
    -libname=box \
    -tags "$TAGS" \
    ./experimental/libbox

ls -lh "$root/app/libs/libbox.aar"

# Assert the protocols are in the artifact rather than trusting the tag list.
# A typo in a tag is silent, and a core without OpenConnect is
# indistinguishable from one with it until the first connect fails.
check=$(mktemp -d)
unzip -o -q "$root/app/libs/libbox.aar" -d "$check"
missing=0
for proto in openconnect snell anytls shadowtls ssh tor hysteria2 tuic; do
    if strings -n 4 "$check/jni/arm64-v8a/libgojni.so" | grep -q "protocol/$proto"; then
        echo "ok: $proto"
    else
        echo "MISSING: $proto - check the build tags" >&2
        missing=1
    fi
done
[ "$missing" -eq 0 ] || { rm -rf "$check"; exit 1; }

# Print the generated Java signatures for the types this app has to implement.
#
# This is not decoration. gomobile rewrites Go names on the way into Java -
# the first letter is lowercased, `error` returns become `throws Exception`,
# `(T, error)` becomes a T return that throws - and PlatformInterface alone
# has thirty methods. Reading the Go source and guessing the Java shape is how
# you get thirty compile errors at once, so the truth gets printed here and the
# Kotlin is written against this output.
#
# Set SINGBOX_DUMP_API=0 to skip it.
if [ "${SINGBOX_DUMP_API:-1}" = "1" ] && command -v javap >/dev/null 2>&1; then
    echo "--- generated Java API ---"
    for type in PlatformInterface CommandServerHandler CommandServer \
                SetupOptions OverrideOptions TunOptions StringIterator \
                NetworkInterface NetworkInterfaceIterator Libbox; do
        echo "=== io.nekohasekai.libbox.$type ==="
        javap -classpath "$check/classes.jar" "io.nekohasekai.libbox.$type" 2>&1 || true
    done
    echo "--- end generated Java API ---"
fi

rm -rf "$check"

echo "built app/libs/libbox.aar at sing-box $SINGBOX_COMMIT"
