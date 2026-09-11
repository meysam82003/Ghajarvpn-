# Third-party notices

GhajarVPN uses AndroidX Media3 1.9.0 for standards-based media playback. AndroidX is
licensed under the Apache License 2.0. Source and license information:
https://github.com/androidx/media and https://www.apache.org/licenses/LICENSE-2.0

The Browser and Media code in this repository was implemented for GhajarVPN. No code
was copied from Nira Browser, QDM-Android, Pantegnos, or npvt-terminal-converter.
Those projects were reviewed only for architecture and format research; their current
licenses were checked before implementation (MPL-2.0, Apache-2.0, MIT, and MIT,
respectively).

GhajarVPN integrates the Psiphon tunnel engine. The PsiphonTunnel Java wrapper
(ca/psiphon/PsiphonTunnel.java) is Copyright (c) Psiphon Inc. and is licensed under
the GNU General Public License v3 or later; source:
https://github.com/CluvexStudio/psiphon-tunnel-core (branch shirokhorshid,
pinned commit 83aa73b9b982e7421e00117f5b0c5aceb5dda452). The Go tunnel-core code
is linked into the combined gomobile AAR built by scripts/build-psiphon-aar.sh;
see scripts/PSIPHON-BUILD-INSTRUCTIONS.md.
