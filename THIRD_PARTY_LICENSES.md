# Third-party licenses and notices — MyOwnTV / МОЁ ТВ

This file is part of the Corresponding Source and of every redistribution of the app.
The project's own license is **GNU GPL v3.0 only** (`GPL-3.0-only`) — see [LICENSE](LICENSE)
and [NOTICE](NOTICE). Versions below are the ones pinned in
[gradle/libs.versions.toml](gradle/libs.versions.toml); the actually packaged dependencies are
the `implementation(...)` entries in [app/build.gradle.kts](app/build.gradle.kts).

All licenses listed here are compatible with GPL-3.0-only. Where a library offers a choice, the
entry states which option this project uses. **None of these components is relicensed**: each
remains under its own license; the GPLv3 obligation applies to the combined derivative work as a
whole (GPLv3 section 5(c)).

---

## 1. Gradle dependencies shipped in the APK

| Component | Version | License | Notes / required notices |
|---|---|---|---|
| AndroidX: `core-ktx`, `activity-compose`, Compose BOM + `ui`/`ui-graphics`/`foundation`/`material-icons-core`, `tv-material`, `tvprovider`, `lifecycle-*`, `navigation-compose`, `datastore-preferences`, `work-runtime-ktx`, `profileinstaller`, `core-splashscreen`, `documentfile` | see catalogue | **Apache-2.0** | © The Android Open Source Project. Full text: [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt). |
| AndroidX Room, Paging, SQLite (`room-*`, `paging-*`, `sqlite*`) | 2.8.5 / 3.5.1 / 2.6.2 | **Apache-2.0** | Room compiler runs at build time only and is not packaged. |
| AndroidX Media3 / ExoPlayer (`exoplayer`, `exoplayer-hls`, `exoplayer-dash`, `ui`, `datasource-okhttp`) | 1.11.1 | **Apache-2.0** | The app's only playback engine. |
| Kotlin stdlib / `kotlinx-coroutines-android` | 2.4.20 / 1.11.0 | **Apache-2.0** | © JetBrains and contributors. |
| OkHttp (`com.squareup.okhttp3:okhttp`) | 5.5.0 | **Apache-2.0** | © Square, Inc. |
| Coil 3 (`coil-compose`, `coil-network-okhttp`) | 3.6.0 | **Apache-2.0** | © 2026 Coil Contributors. |
| Koin (`koin-android`, `koin-androidx-compose`, BOM) | 4.2.2 | **Apache-2.0** | © 2017–present Arnaud Giuliani. |
| ZXing (`com.google.zxing:core`) | 3.5.4 | **Apache-2.0** | QR generation only. |
| `com.github.albfernandez:juniversalchardet` | 2.5.0 | **MPL-1.1 / GPL-3.0 / LGPL-3.0 (tri-license)** | This project elects **GPL-3.0** (MPL-1.1 alone is not GPL-compatible). © Alberto Fernández; source: <https://github.com/albfernandez/juniversalchardet>. Local subtitle charset detection only. |
| `com.pierfrancescosoffritti.androidyoutubeplayer:core` | 13.0.0 | **MIT** | © 2025 Pierfrancesco Soffritti. MIT requires the copyright notice and permission notice to be reproduced — full text in section 4 below. |
| SIL Open Font License fonts in `app/src/main/res/font/` | — | **OFL-1.1** | See section 2. Font files are bundled in the APK. |

**Apache-2.0 section 4 obligations**: a copy of the license and the retained attribution notices
must accompany any redistribution. `LICENSES/Apache-2.0.txt` holds the license text; the
attribution notices are the copyright lines in the table above and the upstream notices in
[NOTICE](NOTICE).

### Test / build-time only (not shipped in the APK)

| Component | License |
|---|---|
| JUnit 4.13.2 | Eclipse Public License 1.0 |
| `org.json` (real implementation for JVM tests) | JSON License (<https://www.json.org/license.html>) |
| Gson 2.11.0 | Apache-2.0 |
| AndroidX Test / Espresso / UI Automator / Macrobenchmark / `kotlinx-coroutines-test` | Apache-2.0 |
| AGP, Gradle, KSP, Compose compiler plugin, Baseline Profile Gradle plugin | their respective upstream licenses |

These are still part of the Corresponding Source and of the build; no binary of theirs is
distributed to end users.

---

## 2. Bundled fonts — SIL Open Font License 1.1

`app/src/main/res/font/` ships 11 `.ttf` files from 6 families. Each is licensed under the
**SIL Open Font License, Version 1.1**. The complete, unmodified license text and the copyright
notice for each family live in `extras/font-licenses/` and **must accompany the font files in any
redistribution** (including an APK-only release — either as app assets or in the release bundle).
Modified versions may not use a Reserved Font Name without permission.

| Family | Bundled files | Copyright notice | License file |
|---|---|---|---|
| Dancing Script | `dancing_script_variable.ttf` | © 2016 The Dancing Script Project Authors (RFN: "Dancing Script") | `extras/font-licenses/OFL-Dancing-Script.txt` |
| JetBrains Mono | `jetbrains_mono_semibold.ttf` | © 2020 The JetBrains Mono Project Authors | `extras/font-licenses/OFL-JetBrains-Mono.txt` |
| Lora | `lora_variable.ttf`, `lora_italic_variable.ttf` | © 2011 The Lora Project Authors (RFN: "Lora") | `extras/font-licenses/OFL-Lora.txt` |
| Playfair Display | `playfair_display_variable.ttf`, `playfair_display_italic_variable.ttf` | © 2017 The Playfair Display Project Authors (RFN: "Playfair Display") | `extras/font-licenses/OFL-Playfair-Display.txt` |
| Plus Jakarta Sans | `plus_jakarta_sans_variable.ttf` | © 2020 The Plus Jakarta Sans Project Authors | `extras/font-licenses/OFL-Plus-Jakarta-Sans.txt` |
| Poppins | `poppins_regular/medium/semibold/bold.ttf` | © 2020 The Poppins Project Authors | `extras/font-licenses/OFL-Poppins.txt` |

---

## 3. Other bundled assets

* **`app/src/main/res/raw/isrg_extra_roots.pem`** — the ISRG Root X2 trust anchor published by
  Internet Security Research Group (Let's Encrypt), used as an extra trust anchor on older
  devices (`ExtraTrustAnchors.kt`). Source:
  <https://letsencrypt.org/certs/isrg-root-x2.pem>, documented at
  <https://letsencrypt.org/certificates/>. The upstream page states no copyright or license terms
  for the PEM file; the certificate itself is factual trust-anchor data, not a creative work. It
  is attributed here for provenance.
* **Weblate logo** — `extras/partner-logos/Weblate_logo.svg`, used to credit the translation
  platform. Trademark of its owner.
* **Brand assets** — `extras/brand/` and `extras/brand/legacy/` are upstream OwnTV artwork
  (GPL-3.0-only, © its authors; the lowercase "owntv" wordmark is by @m3th0d93, issue #227).
  `extras/branding/` is this fork's own МОЁ ТВ kit. `extras/screenshots/` are upstream
  screenshots retained for historical reference.

---

## 4. MIT notice — android-youtube-player

Reproduced verbatim, as required by the MIT license:

```
MIT License

Copyright (c) 2025 Pierfrancesco Soffritti

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

Source: <https://github.com/PierfrancescoSoffritti/android-youtube-player/blob/master/LICENSE>

---

## 5. Not bundled (historical)

Upstream OwnTV shipped **mpv / libmpv (FFmpeg)** as a second playback engine. This fork removed
it (commits `ac5cc39`, `933cc94`) and removed the external `tv.own.owntv:libmpv` dependency.
No mpv, FFmpeg or LGPL/GPL-licensed native `.so` from that stack is distributed here; the only
native libraries in the APK are the stripped, dependency-supplied `androidx.sqlite` ones. The
corresponding upstream credits are kept in [NOTICE](NOTICE) and [CHANGELOG.md](CHANGELOG.md)
for historical accuracy.

---

## 6. Maintaining this file

When a dependency is added, upgraded or removed, update the table above and the version
catalogue together, and check that the new license is GPL-3.0-only-compatible. Before publishing
an APK, verify that `LICENSE`, `NOTICE`, this file, `LICENSES/Apache-2.0.txt` and the six OFL
texts are shipped with it or reachable through a GPLv3 section 6 written offer. See
[docs/attribution-checklist.md](docs/attribution-checklist.md).
