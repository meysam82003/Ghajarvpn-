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

for asset in ghajar_launcher.jpg ghajar_payment_frame.jpg ghajar_royal_characters.webp; do
  test -s "${package_root}/branding/native/${asset}"
  cp "${package_root}/branding/native/${asset}" "${asset_target}/${asset}"
done
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  target="${source_root}/app/src/main/res/mipmap-${density}"
  mkdir -p "${target}"
  cp "${package_root}/branding/native/mipmap-${density}/ic_launcher.webp" "${target}/ic_launcher.webp"
  cp "${package_root}/branding/native/mipmap-${density}/ic_launcher.webp" "${target}/ic_launcher_round.webp"
done

# Welcome originals are archived outside the runtime resources. Install one
# optimized JPEG per name; Android must not package both PNG and JPEG copies.
for poster in "${package_root}/branding/welcome/"*.jpg; do
  test -s "${poster}"
  name="$(basename "${poster}" .jpg)"
  rm -f "${asset_target}/${name}.png" "${asset_target}/${name}.webp"
  cp "${poster}" "${asset_target}/${name}.jpg"
done
