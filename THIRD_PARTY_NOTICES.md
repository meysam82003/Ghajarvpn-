# Third-party notices

GhajarVPN uses AndroidX Media3 1.9.0 for standards-based media playback. AndroidX is
licensed under the Apache License 2.0. Source and license information:
https://github.com/androidx/media and https://www.apache.org/licenses/LICENSE-2.0

The Browser and Media code in this repository was implemented for GhajarVPN. No code
was copied from Nira Browser, QDM-Android, Pantegnos, or npvt-terminal-converter.
Those projects were reviewed only for architecture and format research; their current
licenses were checked before implementation (MPL-2.0, Apache-2.0, MIT, and MIT,
respectively).

Ghajar Browser uses AndroidX WebKit for the process-isolated WebView proxy
override and OkHttp through AndroidX Media3's OkHttp data source so Browser-only
video requests follow the same SOCKS route. AndroidX WebKit, AndroidX Media3 and
OkHttp are licensed under the Apache License 2.0. Source and license information:
https://github.com/androidx/androidx, https://github.com/androidx/media,
https://github.com/square/okhttp, and https://www.apache.org/licenses/LICENSE-2.0
