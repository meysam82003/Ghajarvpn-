# Ghajar VPN — Canonical Source Policy

This repository's `main` branch is the single, self-contained source of
truth for the Ghajar VPN Android app, its native VPN engine sources, and
its backend (Faoxima panel) source.

## Provenance

- Baseline: GitHub Release `1.0.0`
  (https://github.com/meysam82003/Ghajarvpn-/releases/tag/1.0.0),
  target commit `c1e2c3eb113b942a68e0267fdc8c32924e97397c`.
- Import source: release asset `Ghajarvpn-1.0.0-complete-source.tar.gz`
  (SHA-256 `4d522971ed71996e65dc477c50fb07ba666b3dc70bdf52f2c4d757775811570d`,
  matches the asset digest published by GitHub).
- The archive's `SOURCE-MANIFEST.json` lists a per-file SHA-256 for all
  27,546 files it contains, against commit `c1e2c3eb1...`. Every file was
  verified byte-for-byte against that manifest before import; 0 mismatches,
  0 missing files. `SOURCE-MANIFEST.json` is kept at the repo root for
  future audits.
- Layout mapping from the archive to this repository:
  - `Android/` → repository root (app module, native engine wrapper
    modules `strongswan/`, `openvpn/`, `browser/`, Gradle wrapper).
  - `NativeSources/` → `native/` (Aether, Psiphon upstream sources).
  - `Backend/Faoxima-1.0.0/` → `backend/Faoxima-1.0.0/` (panel + bot).
  - The archive's `ReleaseTools/` directory (an older overlay-era copy of
    `branding/`, `patches/`, `scripts/`, `server/`) was intentionally not
    imported: it duplicates content already present in this layout and
    belonged to the retired upstream-reconstruction build architecture
    (see "Retired architecture" below). It remains recoverable from the
    release asset and from archived branches if ever needed.

## Retired architecture

Previous CI on this repository reconstructed the build tree at CI time
from a pinned upstream fork plus ~70 sequential patches, then overlaid
this repo's own `app/src` on top (`scripts/bootstrap-from-upstream.sh`,
`patches/android-incremental/*`, and a pinned-SHA256 "byte-for-byte"
source check). That architecture is retired as of this consolidation.
CI now checks out this repository and builds directly — no upstream
clone, no patch application, no rsync overlay.

## Known deviation from the packaged release tooling

The release asset's own bundled `Android/.github/workflows/release.yml`
checks for `app/libs/gozarcore.aar`, but the committed engine AAR in this
same archive is `app/libs/ca.psiphon.aar` (already merged into a single
gomobile AAR combining the Xray engine and Psiphon tunnel, matching
`app/build.gradle.kts`'s `implementation(files("libs/ca.psiphon.aar"))`).
That check would fail as shipped. This repository's `.github/workflows/android.yml`
checks for `ca.psiphon.aar` instead, matching what is actually committed
and what `build.gradle.kts` actually references.

## Rules going forward

1. `main` is built directly from this repository's own tree. No build
   step may fetch, clone, or apply patches from any external source
   repository.
2. The GitHub Release `1.0.0` tag, its target commit, and its assets are
   permanent and must never be overwritten, retargeted, or deleted.
3. Any change to `app/libs/ca.psiphon.aar`, `native/`, or the strongSwan
   generated sources under `strongswan/` must be committed directly to
   this repository — never reconstructed by CI.
4. Backend (`backend/Faoxima-1.0.0/`) and Android sources evolve together
   in this one repository; there is no separate backend repo to sync.
