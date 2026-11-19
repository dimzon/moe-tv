# Third-party licenses and notices — MyOwnTV / МОЁ ТВ

This file is part of the Corresponding Source and of every redistribution of the app.
The project's own license is **GNU GPL v3.0 only** (`GPL-3.0-only`) — see [LICENSE](LICENSE)
and [NOTICE](NOTICE). Versions below are the ones pinned in
[gradle/libs.versions.toml](gradle/libs.versions.toml); the actually packaged dependencies are
the `implementation(...)` entries in [app/build.gradle.kts](app/build.gradle.kts).

All licenses of the components **shipped in the APK** are compatible with GPL-3.0-only. Where a
library offers a choice, the entry states which option this project uses. **None of these
components is relicensed**: each remains under its own license; the GPLv3 obligation applies to the
combined derivative work as a whole (GPLv3 section 5(c)). Test- and build-time-only components
(section 1, second table) are never distributed to end users.

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

`app/src/main/res/font/` ships 10 `.ttf` files from 5 families. Each is licensed under the
**SIL Open Font License, Version 1.1**. The complete, unmodified license text and the copyright
notice for each family live in `extras/font-licenses/` and **must accompany the font files in any
redistribution** (including an APK-only release — either as app assets or in the release bundle).
Modified versions may not use a Reserved Font Name without permission.

| Family | Bundled files | Copyright notice | License file |
|---|---|---|---|
| JetBrains Mono | `jetbrains_mono_semibold.ttf` | © 2020 The JetBrains Mono Project Authors | `extras/font-licenses/OFL-JetBrains-Mono.txt` |
| Lora | `lora_variable.ttf`, `lora_italic_variable.ttf` | © 2011 The Lora Project Authors (RFN: "Lora") | `extras/font-licenses/OFL-Lora.txt` |
| Play | `play_regular.ttf`, `play_bold.ttf` | © 2011 Jonas Hecksher, Playtypes, e-types AS (RFN: "Play", "Playtype", "Playtype Sans") | `extras/font-licenses/OFL-Play.txt` |
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
* **Brand assets** — `extras/brand/legacy/` is upstream OwnTV artwork (GPL-3.0-only, © its
  authors; the lowercase "owntv" wordmark is by @m3th0d93, issue #227). The current
  `extras/brand/` kit (icons, logos and TV banners generated from `app/src/main/res/`) and
  `extras/branding/` are this fork's own МОЁ ТВ artwork. `extras/screenshots/` are upstream
  screenshots retained for historical reference only — several of them depict features this fork
  no longer has (Downloads, Movies, Series, Playlist, Profile Selection, Settings › Profiles).

---

## 4. Not bundled (historical)

Recorded so that a removed component is not silently resurrected, and so that upstream credits
remain traceable. **None of these is distributed by this fork.**

* **mpv / libmpv (FFmpeg)** — upstream's second playback engine, removed in `ac5cc39` and
  `933cc94` together with the external `tv.own.owntv:libmpv` dependency. No mpv, FFmpeg or
  LGPL/GPL-licensed native `.so` from that stack is distributed here; the only native libraries
  in the APK are the stripped, dependency-supplied `androidx.sqlite` ones.
* **android-youtube-player 13.0.0 (MIT, © 2025 Pierfrancesco Soffritti)** — used by upstream for
  trailers. Removed with the trailer code in `ec31209`, including its MIT notice. **No MIT-licensed
  component is currently packaged**, so no MIT text needs to travel with an APK build.
* **TMDB / OpenSubtitles Workers and logos** — `extras/worker/`, the drawables
  `app/src/main/res/drawable/ic_tmdb_logo.xml` and `ic_opensubtitles_logo.xml`, and the partner
  SVGs `extras/partner-logos/tmdb_logo.svg` and `opensubtitles_logo.svg` were upstream-derived
  material removed in `cf8b0b5` (only `Weblate_logo.svg` remains in that directory). The
  historical trademark credits stay in [NOTICE](NOTICE) §5 and [README.md](README.md), but no
  active API disclaimer applies: this fork makes no TMDB or OpenSubtitles requests.

The corresponding upstream credits are kept in [NOTICE](NOTICE) and [CHANGELOG.md](CHANGELOG.md)
for historical accuracy.

---

## 5. Maintaining this file

When a dependency is added, upgraded or removed, update the table above and the version
catalogue together, and check that the new license is GPL-3.0-only-compatible. Removing an
integration also means checking for its logos, API keys and trademark disclaimers: delete the
active notice, keep the historical one. Before publishing an APK, verify that `LICENSE`,
`NOTICE`, this file, `LICENSES/Apache-2.0.txt` and the six OFL
texts are shipped with it or reachable through a GPLv3 section 6 written offer. See
[docs/attribution-checklist.md](docs/attribution-checklist.md).
