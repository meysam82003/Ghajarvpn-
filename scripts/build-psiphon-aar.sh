#!/usr/bin/env bash
set -euo pipefail

# Build ONE combined gomobile AAR (ca.psiphon.aar) that contains BOTH the
# gozarcore (Xray) engine and the Psiphon tunnel engine.
#
# Why combined: gomobile supports exactly one Go runtime per process. Two
# separate gomobile AARs (gozarcore.aar + a psi-only ca.psiphon.aar) ship
# duplicate go.Seq classes and duplicate libgojni.so libraries, which breaks
# :app:checkDebugDuplicateClasses and would break native library merging.
# The Psiphon MobileLibrary (psi) is therefore linked INTO the gozarcore
# module and a single bind emits gozarcore.* and the Psiphon bridge in one
# AAR. ca/psiphon/PsiphonTunnel.java lives in the app sources and wraps the
# Gozarcore.psiphon* facade.

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/.." && pwd)"

source_root="${1:-${SOURCE_ROOT:-$repo/.ghajarvpn-src}}"
source_root="$(cd "$source_root" && pwd)"
psiphon_dir="${2:-${PSIPHON_DIR:-$(cd "$repo/.." && pwd)/psiphon-tunnel-core}}"
psiphon_dir="$(cd "$psiphon_dir" 2>/dev/null && pwd || echo "$psiphon_dir")"
gomobile_version="${GOMOBILE_VERSION:-v0.0.0-20260821190718-4776eadac327}"
android_api="${PSIPHON_ANDROID_API:-35}"
targets="${PSIPHON_TARGETS:-android/arm,android/arm64,android/386,android/amd64}"
destination="$repo/app/libs/ca.psiphon.aar"

if [ ! -f "$source_root/go.mod" ] || [ ! -f "$source_root/gozarcore.go" ]; then
  echo "gozarcore module not found at $source_root" >&2
  echo "bootstrap it first: scripts/bootstrap-from-upstream.sh" >&2
  exit 1
fi

if [ ! -d "$psiphon_dir" ]; then
  echo "psiphon-tunnel-core not found at $psiphon_dir" >&2
  echo "clone https://github.com/CluvexStudio/psiphon-tunnel-core (branch shirokhorshid) there, or set PSIPHON_DIR" >&2
  exit 1
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -z "${ANDROID_NDK_ROOT:-}" ]; then
  echo "set ANDROID_NDK_HOME so gomobile can find the ndk" >&2
  exit 1
fi

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$android_home" ] || [ ! -d "$android_home/platforms" ]; then
  echo "set ANDROID_HOME to an android sdk that has a platform installed" >&2
  exit 1
fi
export ANDROID_HOME="$android_home"

platform="${ANDROID_PLATFORM_VERSION:-}"
if [ -z "$platform" ]; then
  for candidate in $(ls "$android_home/platforms" |
    sed -n 's/^android-\([0-9]\{1,\}\)$/\1/p' | sort -n); do
    if [ -f "$android_home/platforms/android-$candidate/android.jar" ]; then
      platform="$candidate"
    fi
  done
fi
if [ -z "$platform" ] || [ ! -f "$android_home/platforms/android-$platform/android.jar" ]; then
  echo "no usable android.jar under $android_home/platforms" >&2
  ls "$android_home/platforms" >&2 || true
  exit 1
fi
export ANDROID_PLATFORM_VERSION="$platform"

echo "[psiphon] module:   $source_root"
echo "[psiphon] fork:     $psiphon_dir"
echo "[psiphon] gomobile: $gomobile_version"
echo "[psiphon] api:      $android_api"
echo "[psiphon] platform: android-$platform"
echo "[psiphon] targets:  $targets"

go install "golang.org/x/mobile/cmd/gomobile@$gomobile_version"
go install "golang.org/x/mobile/cmd/gobind@$gomobile_version"
export PATH="$(go env GOPATH)/bin:$PATH"

gomobile init

# Keep the reconstructed module's manifests pristine: the bridge file and the
# module edits only exist for the duration of this build.
manifests="$(mktemp -d)"
cp "$source_root/go.mod" "$source_root/go.sum" "$manifests/"
bridge_file="$source_root/psiphonbind.go"
cleanup() {
  cp "$manifests/go.mod" "$source_root/go.mod"
  cp "$manifests/go.sum" "$source_root/go.sum"
  rm -f "$bridge_file"
  rm -rf "$manifests" "${inspect:-}"
}
trap cleanup EXIT

# The bridge re-declares the Psiphon MobileLibrary interfaces inside the
# gozarcore package and wraps every psi entry point, so the single bind
# emits gozarcore.Gozarcore (Xray facade, unchanged) plus gozarcore.Psiphon*
# bridge types. ca/psiphon/PsiphonTunnel.java (app source) consumes them.
cat > "$bridge_file" <<'GOEOF'
package gozarcore

// Psiphon bridge for the combined gomobile bind. gomobile supports exactly
// one Go runtime per process, so the Psiphon tunnel (psi MobileLibrary) is
// linked into the gozarcore module instead of shipping a second AAR.

import (
	psi "github.com/Psiphon-Labs/psiphon-tunnel-core/MobileLibrary/psi"
)

// PsiphonProviderNoticeHandler receives tunnel notices as JSON strings.
type PsiphonProviderNoticeHandler interface {
	Notice(noticeJSON string)
}

// PsiphonProviderNetwork mirrors android.net connectivity capabilities.
type PsiphonProviderNetwork interface {
	HasNetworkConnectivity() int
	GetNetworkID() string
	IPv6Synthesize(IPv4Addr string) string
	HasIPv6Route() int
}

// PsiphonProvider is the full host-service contract psi.Start expects.
type PsiphonProvider interface {
	PsiphonProviderNoticeHandler
	PsiphonProviderNetwork
	BindToDevice(fileDescriptor int) (string, error)
	GetDNSServersAsString() string
}

// PsiphonProviderFeedbackHandler reports feedback upload completion.
type PsiphonProviderFeedbackHandler interface {
	SendFeedbackCompleted(err error)
}

type psiphonProviderAdapter struct{ inner PsiphonProvider }

func (a psiphonProviderAdapter) Notice(noticeJSON string) {
	a.inner.Notice(noticeJSON)
}

func (a psiphonProviderAdapter) HasNetworkConnectivity() int {
	return a.inner.HasNetworkConnectivity()
}

func (a psiphonProviderAdapter) GetNetworkID() string {
	return a.inner.GetNetworkID()
}

func (a psiphonProviderAdapter) IPv6Synthesize(ipv4Addr string) string {
	return a.inner.IPv6Synthesize(ipv4Addr)
}

func (a psiphonProviderAdapter) HasIPv6Route() int {
	return a.inner.HasIPv6Route()
}

func (a psiphonProviderAdapter) BindToDevice(fileDescriptor int) (string, error) {
	return a.inner.BindToDevice(fileDescriptor)
}

func (a psiphonProviderAdapter) GetDNSServersAsString() string {
	return a.inner.GetDNSServersAsString()
}

type psiphonNoticeAdapter struct{ inner PsiphonProviderNoticeHandler }

func (a psiphonNoticeAdapter) Notice(noticeJSON string) {
	a.inner.Notice(noticeJSON)
}

type psiphonNetworkAdapter struct{ inner PsiphonProviderNetwork }

func (a psiphonNetworkAdapter) HasNetworkConnectivity() int {
	return a.inner.HasNetworkConnectivity()
}

func (a psiphonNetworkAdapter) GetNetworkID() string {
	return a.inner.GetNetworkID()
}

func (a psiphonNetworkAdapter) IPv6Synthesize(ipv4Addr string) string {
	return a.inner.IPv6Synthesize(ipv4Addr)
}

func (a psiphonNetworkAdapter) HasIPv6Route() int {
	return a.inner.HasIPv6Route()
}

type psiphonFeedbackAdapter struct{ inner PsiphonProviderFeedbackHandler }

func (a psiphonFeedbackAdapter) SendFeedbackCompleted(err error) {
	a.inner.SendFeedbackCompleted(err)
}

// PsiphonStart starts the Psiphon tunnel controller.
func PsiphonStart(
	configJson string,
	embeddedServerEntryList string,
	embeddedServerEntryListFilename string,
	provider PsiphonProvider,
	useDeviceBinder bool,
	useIPv6Synthesizer bool,
	useHasIPv6RouteGetter bool) error {
	return psi.Start(
		configJson,
		embeddedServerEntryList,
		embeddedServerEntryListFilename,
		psiphonProviderAdapter{provider},
		useDeviceBinder,
		useIPv6Synthesizer,
		useHasIPv6RouteGetter)
}

// PsiphonStop stops the Psiphon tunnel controller.
func PsiphonStop() {
	psi.Stop()
}

// PsiphonReconnectTunnel terminates the current tunnel so a new one starts.
func PsiphonReconnectTunnel() {
	psi.ReconnectTunnel()
}

// PsiphonNetworkChanged tells tunnel-core the network changed.
func PsiphonNetworkChanged() {
	psi.NetworkChanged()
}

// PsiphonAppResumed tells tunnel-core the app resumed.
func PsiphonAppResumed() {
	psi.AppResumed()
}

// PsiphonExportExchangePayload exports the in-proxy exchange payload.
func PsiphonExportExchangePayload() string {
	return psi.ExportExchangePayload()
}

// PsiphonImportExchangePayload imports an exchange payload.
func PsiphonImportExchangePayload(payload string) bool {
	return psi.ImportExchangePayload(payload)
}

// PsiphonImportPushPayload imports a push-delivered payload.
func PsiphonImportPushPayload(payload []byte) bool {
	return psi.ImportPushPayload(payload)
}

// PsiphonStartSendFeedback uploads user feedback.
func PsiphonStartSendFeedback(
	configJson string,
	diagnosticsJson string,
	uploadPath string,
	feedbackHandler PsiphonProviderFeedbackHandler,
	networkInfoProvider PsiphonProviderNetwork,
	noticeHandler PsiphonProviderNoticeHandler,
	useIPv6Synthesizer bool,
	useHasIPv6RouteGetter bool) error {
	return psi.StartSendFeedback(
		configJson,
		diagnosticsJson,
		uploadPath,
		psiphonFeedbackAdapter{feedbackHandler},
		psiphonNetworkAdapter{networkInfoProvider},
		psiphonNoticeAdapter{noticeHandler},
		useIPv6Synthesizer,
		useHasIPv6RouteGetter)
}

// PsiphonStopSendFeedback cancels an ongoing feedback upload.
func PsiphonStopSendFeedback() {
	psi.StopSendFeedback()
}

// PsiphonWriteRuntimeProfiles writes CPU/block profiling output.
func PsiphonWriteRuntimeProfiles(outputDirectory string, cpuSampleDurationSeconds int, blockSampleDurationSeconds int) {
	psi.WriteRuntimeProfiles(outputDirectory, cpuSampleDurationSeconds, blockSampleDurationSeconds)
}

// PsiphonUpgradeDownloadFilePath returns the upgrade download path.
func PsiphonUpgradeDownloadFilePath(rootDataDirectoryPath string) string {
	return psi.UpgradeDownloadFilePath(rootDataDirectoryPath)
}
GOEOF

cd "$source_root"

# Dependency split: xray (apernet quic-go) needs qpack v0.6 API while the
# Psiphon quic-go fork still uses the v0.4 API (DecodeFull, 2-arg NewDecoder).
# One Go build can hold only ONE version of a module path, so give the fork
# private copies of qpack v0.4.0 and of its own quic-go fork under distinct
# module paths and rewrite the imports. xray keeps upstream qpack v0.6.
qpack_v4_dir="$psiphon_dir/third_party/qpack-v4legacy"
if [ ! -f "$qpack_v4_dir/go.mod" ]; then
  rm -rf "$qpack_v4_dir"
  git clone --depth 1 -b v0.4.0 https://github.com/quic-go/qpack "$qpack_v4_dir"
  printf 'module github.com/quic-go/qpack/v4legacy\n\ngo 1.21\n' > "$qpack_v4_dir/go.mod"
  rm -rf "$qpack_v4_dir/.git"
fi
# Rename self-imports inside the qpack copy (quote-anchored => idempotent).
grep -rl '"github.com/quic-go/qpack"' "$qpack_v4_dir" --include='*.go' | \
  xargs -r sed -i 's|"github.com/quic-go/qpack"|"github.com/quic-go/qpack/v4legacy"|g' || true

quicgo_dir="$psiphon_dir/third_party/quic-go-fork"
if [ ! -d "$quicgo_dir/.git" ]; then
  rm -rf "$quicgo_dir"
  git clone --depth 1 https://github.com/Psiphon-Labs/quic-go "$quicgo_dir"
fi
quicgo_pin='79fe45fb83b1cbcf9e9aa4b50419c7ad836ee786'
quicgo_sha="$(git -C "$quicgo_dir" rev-parse HEAD)"
if [ "$quicgo_sha" != "$quicgo_pin" ]; then
  echo "::error::Psiphon-Labs/quic-go moved: expected $quicgo_pin, got $quicgo_sha; re-pin the combined build" >&2
  exit 1
fi
# Point the fork's qpack imports at the v4legacy copy (quote-anchored).
grep -rl '"github.com/quic-go/qpack"' "$quicgo_dir" --include='*.go' | \
  xargs -r sed -i 's|"github.com/quic-go/qpack"|"github.com/quic-go/qpack/v4legacy"|g' || true

# Rewrite the tunnel-core fork's own qpack references, then override both
# modules with the patched local copies.
grep -rl '"github.com/quic-go/qpack"' "$psiphon_dir" --include='*.go' --exclude-dir=vendor | \
  xargs -r sed -i 's|"github.com/quic-go/qpack"|"github.com/quic-go/qpack/v4legacy"|g' || true
sed -i 's|github.com/quic-go/qpack v0.4.0|github.com/quic-go/qpack/v4legacy v0.4.0|' "$psiphon_dir/go.mod"

go mod edit -require=github.com/Psiphon-Labs/psiphon-tunnel-core@v0.0.0
go mod edit -replace=github.com/Psiphon-Labs/psiphon-tunnel-core="$psiphon_dir"
go mod edit -require=github.com/quic-go/qpack/v4legacy@v0.0.0
go mod edit -replace=github.com/quic-go/qpack/v4legacy="$qpack_v4_dir"
go mod edit -require=github.com/Psiphon-Labs/quic-go@v0.0.0-20250527153145-79fe45fb83b1
go mod edit -replace=github.com/Psiphon-Labs/quic-go="$quicgo_dir"
go mod tidy

# -checklinkname=0 is required by psiphon's in-proxy dependency (wlynxg/anet).
# max-page-size=16384 keeps every LOAD segment 16 KB aligned for modern Android.
export GOFLAGS=-mod=mod
export CGO_LDFLAGS="${CGO_LDFLAGS:-} -Wl,-z,max-page-size=16384,-z,common-page-size=16384"
LDFLAGS="-checklinkname=0 -s -w -extldflags=-Wl,-z,max-page-size=16384,-z,common-page-size=16384"

mkdir -p "$(dirname "$destination")"
gomobile bind -v -androidapi "${android_api}" -target="${targets}" -ldflags="$LDFLAGS" -o "$destination" .

# Also drop the AAR into the build tree's libs so Gradle picks it up without
# depending on the repo checkout location.
mkdir -p "$source_root/app/libs"
cp "$destination" "$source_root/app/libs/ca.psiphon.aar"

echo "[psiphon] wrote $destination"

if command -v readelf >/dev/null 2>&1; then
  inspect="$(mktemp -d)"
  unzip -qo "$destination" -d "$inspect" 'jni/*'
  for library in "$inspect"/jni/*/libgojni.so; do
    alignments="$(readelf -lW "$library" | awk '$1=="LOAD"{print $NF}' | sort -u)"
    if [ "$alignments" != "0x4000" ]; then
      echo "::error::$(basename "$(dirname "$library")")/libgojni.so is not 16 KB aligned: $alignments" >&2
      exit 1
    fi
  done
  echo "[psiphon] every libgojni.so is 16 KB aligned"
fi

unzip -l "$destination" | grep -E 'gozarcore|ca/psiphon|classes.jar' | head -5
