#!/usr/bin/env bash
set -euo pipefail
package_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="${1:?Usage: install-ui-assets.sh SOURCE_ROOT}"
asset_target="${source_root}/app/src/main/res/drawable-nodpi"
mkdir -p "${asset_target}"
for asset in ghajar_wordmark ghajar_treasury; do
  test -s "${package_root}/branding/soft-ui/${asset}.png"
  cp "${package_root}/branding/soft-ui/${asset}.png" "${asset_target}/${asset}.png"
done

# Install all 33 welcome posters while keeping the original 3.0.4 carousel UI.
for poster in "${package_root}/branding/welcome/"*.jpg; do
  test -s "${poster}"
  name="$(basename "${poster}" .jpg)"
  rm -f "${asset_target}/${name}.png" "${asset_target}/${name}.webp"
  cp "${poster}" "${asset_target}/${name}.jpg"
done
