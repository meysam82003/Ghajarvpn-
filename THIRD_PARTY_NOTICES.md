# Third-party notices

GhajarVPN uses AndroidX Media3 1.9.0 for standards-based media playback. AndroidX is
licensed under the Apache License 2.0. Source and license information:
https://github.com/androidx/media and https://www.apache.org/licenses/LICENSE-2.0

The Browser and Media code in this repository was implemented for GhajarVPN. No code
was copied from Nira Browser, QDM-Android, Pantegnos, or npvt-terminal-converter.
Those projects were reviewed only for architecture and format research; their current
licenses were checked before implementation (MPL-2.0, Apache-2.0, MIT, and MIT,
respectively).

## Pantegnos (NPVS / NPVO1 container format)

`app/src/main/java/net/gozar/app/configtoolkit/NpvContainer.kt` is a Kotlin port of parts of
https://github.com/FrontierTM/Pantegnos (`internal/modules/impl/npvs*.go`), limited to open
exports and passphrase-sealed files.

```
MIT License

Copyright (c) 2026 FrontierTM

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
