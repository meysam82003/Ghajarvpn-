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

# Install the official 0.0.1 launcher artwork in every Android density.
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  icon_source="${package_root}/branding/app-icon/${density}.webp"
  icon_target="${source_root}/app/src/main/res/mipmap-${density}"
  test -s "${icon_source}"
  mkdir -p "${icon_target}"
  for icon_name in gnet_icon ic_launcher ic_launcher_foreground ic_launcher_round; do
    cp "${icon_source}" "${icon_target}/${icon_name}.webp"
  done
done

# Install all 33 welcome posters while keeping the current 0.0.1 welcome behavior.
for poster in "${package_root}/branding/welcome/"*.jpg; do
  test -s "${poster}"
  name="$(basename "${poster}" .jpg)"
  rm -f "${asset_target}/${name}.png" "${asset_target}/${name}.webp"
  cp "${poster}" "${asset_target}/${name}.jpg"
done
