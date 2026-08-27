#!/usr/bin/env bash
set -euo pipefail

package_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="$(cd "${1:?Usage: verify-source-snapshots.sh SOURCE_ROOT}" && pwd)"

# The readable GitHub files must be the same files that the patch series builds.
snapshots=(
  "app/build.gradle.kts"
  "openvpn/build.gradle.kts"
  "strongswan/build.gradle.kts"
  "settings.gradle.kts"
  "app/src/main/java/net/gozar/app/BrandConfig.kt"
  "app/src/main/java/net/gozar/app/GhajarAccountStore.kt"
  "app/src/main/java/net/gozar/app/GhajarNotificationMonitor.kt"
  "app/src/main/java/net/gozar/app/GhajarShopScreen.kt"
  "app/src/main/java/net/gozar/app/GhajarSplashRepository.kt"
  "app/src/main/java/net/gozar/app/GhajarStoreApi.kt"
  "app/src/main/java/net/gozar/app/SecurePaymentActivity.kt"
  "app/src/main/java/net/gozar/app/GhajarUiRules.kt"
  "app/src/main/java/net/gozar/app/GhajarLinkFlow.kt"
  "app/src/main/java/net/gozar/app/GhajarVisuals.kt"
  "app/src/main/java/net/gozar/app/GhajarCheckoutViewModel.kt"
  "app/src/main/java/net/gozar/app/GhajarCheckoutCards.kt"
  "app/src/main/java/net/gozar/app/GhajarOperation.kt"
  "app/src/main/java/net/gozar/app/GhajarPaymentPolicy.kt"
  "app/src/test/java/net/gozar/app/GhajarCommerceRulesTest.kt"
  "app/src/test/java/net/gozar/app/GhajarUiRulesTest.kt"
  "app/src/test/java/net/gozar/app/GhajarLinkFlowTest.kt"
  "openvpn/src/main/cpp/CMakeLists.txt"
  "openvpn/src/main/cpp/ghajar-openssl-arch.cmake"
)
for path in "${snapshots[@]}"; do
  if ! cmp -s -- "${package_root}/${path}" "${source_root}/${path}"; then
    echo "Snapshot differs from materialized source: ${path}" >&2
    exit 1
  fi
done
echo "Verified ${#snapshots[@]} reviewed source snapshots."
