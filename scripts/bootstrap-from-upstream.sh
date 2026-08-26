#!/usr/bin/env bash
set -euo pipefail

# Reconstruct the complete Ghajarvpn Android tree when GitHub carries the
# reviewed patch series instead of the large inherited engine binaries.
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destination="${1:-${project_root}/.ghajarvpn-src}"
patch_dir="${project_root}/patches/android-series"
upstream_url="https://github.com/SuOracle/GRoute.git"
upstream_commit="a8b4d76d896faac0ffbcd56e6e1f32b9f228f157"

if [[ -e "${destination}" ]]; then
  echo "Destination already exists: ${destination}" >&2
  exit 2
fi

if ! compgen -G "${patch_dir}/ghajarvpn-series.b64.part-*" > /dev/null; then
  echo "Ghajarvpn patch parts were not found in ${patch_dir}" >&2
  exit 3
fi

git clone --filter=blob:none --no-checkout "${upstream_url}" "${destination}"
git -C "${destination}" checkout --detach "${upstream_commit}"
git -C "${destination}" config user.name "Ghajarvpn Build"
git -C "${destination}" config user.email "build@ghajarvpn.local"

patch_file="${destination}/.git/ghajarvpn-series.patch"
cat "${patch_dir}"/ghajarvpn-series.b64.part-* | base64 --decode > "${patch_file}"
git -C "${destination}" am --3way "${patch_file}"
rm -f "${patch_file}"

echo "Ghajarvpn source materialized at ${destination}"
git -C "${destination}" log -3 --oneline
