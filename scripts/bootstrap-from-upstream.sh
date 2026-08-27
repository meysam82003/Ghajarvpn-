#!/usr/bin/env bash
set -euo pipefail

# Reconstruct the complete Ghajarvpn Android tree when GitHub carries the
# reviewed patch series instead of the large inherited engine binaries.
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destination="${1:-${project_root}/.ghajarvpn-src}"
patch_dir="${project_root}/patches/android-series"
incremental_patch_dir="${project_root}/patches/android-incremental"
upstream_url="https://github.com/SuOracle/GRoute.git"
upstream_commit="a8b4d76d896faac0ffbcd56e6e1f32b9f228f157"

# git -C changes the process working directory, so a relative patch path would
# otherwise be resolved twice (for example .ghajarvpn-src/.ghajarvpn-src/...).
if [[ "${destination}" != /* ]]; then
  destination="$(pwd)/${destination}"
fi

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

# Keep the large reviewed base series immutable. Small follow-up commits are
# applied in lexical order so GitHub reviews show only the current changes.
if [[ -d "${incremental_patch_dir}" ]]; then
  while IFS= read -r incremental_patch; do
    git -C "${destination}" am --3way "${incremental_patch}"
  done < <(find "${incremental_patch_dir}" -maxdepth 1 -type f -name '*.patch' -print | sort)
fi

bash "${project_root}/scripts/install-ui-assets.sh" "${destination}"

echo "Ghajarvpn source materialized at ${destination}"
git -C "${destination}" log -5 --oneline
