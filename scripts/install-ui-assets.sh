#!/usr/bin/env bash
set -euo pipefail
package_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="${1:?Usage: install-ui-assets.sh SOURCE_ROOT}"
asset_target="${source_root}/app/src/main/res/drawable-nodpi"
mkdir -p "${asset_target}"
for asset in ghajar_wordmark ghajar_treasury ghajar_welcome_world ghajar_welcome_connection ghajar_welcome_locations ghajar_welcome_royal; do
  test -s "${package_root}/branding/soft-ui/${asset}.png"
  cp "${package_root}/branding/soft-ui/${asset}.png" "${asset_target}/${asset}.png"
done
