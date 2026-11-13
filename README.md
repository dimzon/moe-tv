<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="extras/brand/app-logos/logo_eggshell_light.png">
    <img src="extras/brand/app-logos/logo_eggshell.png" alt="MyOwnTV" width="360">
  </picture>
</p>

<p align="center">
  <b>MyOwnTV — your RuTube live-TV player for Android TV</b><br>
  <sub>Fast · modern · remote-first — the public live catalogue and guide, no setup needed</sub>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose for TV" src="https://img.shields.io/badge/Jetpack%20Compose-for%20TV-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Player" src="https://img.shields.io/badge/player-ExoPlayer%20%2F%20Media3-FB8C00">
  <img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue">
  <img alt="Built with the help of AI" src="https://img.shields.io/badge/built%20with-the%20help%20of%20AI-8A2BE2">
</p>

---

MyOwnTV is a native **Android TV** live-TV **player** built with Kotlin and Jetpack Compose for
TV. It plays Live TV on **ExoPlayer (Media3)** for near-instant channel changes, and it is a
*player only*: the channel list and the programme guide come from **RuTube's public API**, with no
playlist, login or setup screen needed.

> ⚠️ MyOwnTV ships **no** channels, playlists, subscriptions or streams of its own. It plays
> RuTube's publicly listed live streams. You are responsible for what you watch and for
> complying with the laws and rights that apply to you.

Open source, **GPL-3.0-only**, built with the help of AI. **Android TV only** — leanback launcher,
D-pad first. **This is a derivative work**, not the original OwnTV — see below.

---

## 🧬 Origins & attribution

**MyOwnTV / МОЁ ТВ is a substantially modified version of
[OwnTV](https://github.com/ahXN00/OwnTV)** by **ahXN00 and the OwnTV contributors**, licensed
under the **GNU General Public License v3.0 only** (`GPL-3.0-only`). The upstream `main` snapshot
was imported on **2026-11-09** (just after upstream v5.2.1, 2026-10-21) as this repository's root
commit `214719f`; upstream history was squashed at the fork point.

It also incorporates source from **[OwnTV_Core](https://github.com/ahXN00/OwnTV_Core)**
(likewise `GPL-3.0-only`), folded into `app/src/main/java/tv/own/owntv/**`: the database, sync,
parsers, EPG and playback engine, **plus the translated UI strings** (upstream shipped 26 locales;
this fork currently packages English and Russian). The docs,
i18n tooling, upstream brand assets in `extras/brand/` and the reference Workers in
`extras/worker/` are upstream-derived too. Full legal notice: [NOTICE](NOTICE).

> **Modified since 2026-11-09 by Dmitry Alexandrov (dimzon).** Copyright (C) 2026 Dmitry
> Alexandrov for the modifications and additions; the original portions remain © ahXN00 and the
> OwnTV contributors. This is the prominent modification notice required by GPLv3 §5(a).

What changed:

- **RuTube-only live TV** — the channel catalogue and week-long programme guide now come from
  RuTube's public API; Xtream / M3U / Stalker providers and their source model are gone.
- **One playback engine** — the mpv / libmpv / FFmpeg engine was removed; playback is
  ExoPlayer (Media3) only. VOD (movies/series), catch-up, downloads, profiles and the file-backup
  UI were dropped; the live-only database schema was rebuilt repeatedly.
- **Rebrand** — МОЁ ТВ wordmark, launcher icons/banner, `applicationId com.github.dimzon.owntv`
  and `moetv://` deep links; the new brand kit lives in `extras/branding/`.
- Some upstream settings screens, strings and reference Worker code remain in the tree even
  though the features behind them are gone.

MyOwnTV / МОЁ ТВ is **not affiliated with, endorsed by, sponsored by or connected to** the
original OwnTV project, ahXN00 or its contributors. "OwnTV" is used here only to identify the
work this one derives from. The complete list of links and texts still pointing at the upstream
project (and awaiting a fork repository URL) is in
[docs/attribution-checklist.md](docs/attribution-checklist.md).

---

## ✨ Features

### 🎬 Playback
- **ExoPlayer (Media3)** — one playback engine tuned for instant Live TV zapping
- **4K HDR** direct rendering, optional auto frame-rate matching, surround sound, 150% volume boost
- **Subtitles** — text, image and closed captions
- Mini-player/PiP, picture-in-picture when you press Home, audio-only mode, live stream-info overlay
- Media-session transport keys and proper audio focus — it behaves like a TV app

### 📺 Live TV & guide
- **RuTube live catalogue** — the public list (~250 channels) with categories, logos and
  distinguishing names for duplicated brands
- **A guide that stays put** — RuTube's schedule for a full week, fetched per day and upserted
  so a failed refresh never wipes what you already have
- “Now” and “next” everywhere, plus programme reminders

### 📚 Library & personalisation
- Favourites, categories, hide channels, search with an on-screen keyboard
- Profiles, a parental PIN, multiple UI languages

## 🧱 Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.4.20 |
| Build | AGP 9.4.0 / Gradle 9.8.2, KSP2, JDK 25 |
| UI | Jetpack Compose for TV (`androidx.tv:tv-material`), Compose BOM 2026.08.00 |
| Media | **ExoPlayer/Media3** |
| Database | Room + Paging + FTS4 |
| DI · Network · Images | Koin · OkHttp · Coil |

`minSdk 26`, `targetSdk 36`, `applicationId tv.own.myowntv`.

### Project layout

```
MyOwnTV/  (this repo)
tv.own.myowntv/
├── player/      the TV player HUD, surfaces and mini-player
├── ui/          theme + reusable components
├── features/    setup, shell, live, search, epg, profiles, settings
└── di/          Koin modules
```

The database, sync, parsers, EPG storage and the live playback engine live in this module under
`tv/own/owntv/**` — one source tree, no separate library to fetch.

The RuTube API contract, channel/EPG rules, database details and the native-library inventory are
documented in [docs/rutube.md](docs/rutube.md).

## 📥 Installing (Fire TV / Android TV)

1. Download the APK from this repository's **Releases**.
2. Install it — on Fire TV, use the **Downloader** app; elsewhere sideload from a USB drive,
   *Send files to TV*, or `adb install MyOwnTV.apk`.

> Each release ships **two APKs**: `MyOwnTV.apk` (arm — every real TV box) and
> `MyOwnTV-x86_64.apk` (emulators and rare Intel boxes).

## 🛠️ Building & running

1. **Get the code** — `git clone <this repository>` (or download the ZIP).
2. **Open it** in [Android Studio](https://developer.android.com/studio) and let Gradle sync.
3. **Pick the build variant** — `standard` for real devices and arm emulators, `x86_64` for
   x86_64 emulators.
4. **Run** ▶. Minimum **Android 8.0 / API 26**.

Command line: `./gradlew assembleDebug` (`gradlew.bat` on Windows). The APK lands in
`app/build/outputs/apk/`.

## 🤝 Contributing

Contributions, bug reports and ideas are welcome — open an issue or a pull request. Please keep
the project's player-only positioning and match the existing code style.

## 🙏 Credits

### 🌍 Translations

The locale resources retained here (currently English and Russian) derive from upstream's 26
translations, the work of volunteer translators on
[Hosted Weblate](https://hosted.weblate.org/projects/owntv/), which hosts libre software free of
charge. The strings themselves originate in
[OwnTV_Core](https://github.com/ahXN00/OwnTV_Core). Thank you to Weblate and to every translator.

### 🎨 Brand

The lowercase **owntv** wordmark was designed for OwnTV by [@m3th0d93](https://github.com/m3th0d93)
in [issue #227](https://github.com/ahXN00/OwnTV/issues/227); the upstream brand assets in
`extras/brand/` remain © their authors. The **МОЁ ТВ** brand kit in `extras/branding/` is this
fork's own work.

### ▶️ Playback

- **[Media3 / ExoPlayer](https://github.com/androidx/media)** — the only playback engine, for
  Live TV and HLS (Apache-2.0).
- **mpv / libmpv / FFmpeg** powered the second engine in upstream OwnTV; **they are no longer
  shipped** by this fork. The historical credits are preserved in [NOTICE](NOTICE) and
  [CHANGELOG.md](CHANGELOG.md).

### 🛰️ Third-party services

- **TMDB** — *"This product uses the TMDB API but is not endorsed or certified by TMDB."*
  Metadata and trailers are provided by [TMDB](https://www.themoviedb.org/); the retained
  upstream Worker reference is in `extras/worker/tmdb/`.
- **OpenSubtitles** — *"This product uses the OpenSubtitles API but is not endorsed or certified
  by OpenSubtitles."* ([opensubtitles.com](https://www.opensubtitles.com/)); the Worker reference
  is in `extras/worker/opensub/`.

### 🧩 Built with

[Jetpack Compose for TV](https://developer.android.com/jetpack/compose) ·
[Room](https://developer.android.com/jetpack/androidx/releases/room) ·
[Koin](https://insert-koin.io/) ·
[OkHttp](https://square.github.io/okhttp/) ·
[Coil](https://coil-kt.github.io/coil/) ·
[ZXing](https://github.com/zxing/zxing) — and the wider Kotlin / AndroidX open-source ecosystem.
Thank you to all their maintainers. Every license, copyright notice and bundled asset is listed
in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## ⚖️ Legal

MyOwnTV is a media **player** only, and an unofficial client: it is not affiliated with,
endorsed by or connected to RuTube (ООО «Руформ»), to the original
[OwnTV](https://github.com/ahXN00/OwnTV) project, or to any other service. "RuTube", "OwnTV",
"TMDB", "OpenSubtitles" and other names and logos belong to their owners and are used only to
describe origin and compatibility. It ships with no channels of its own and does not endorse or
facilitate access to unauthorized streams.

## 📄 License

**GNU General Public License, version 3 only (`GPL-3.0-only`)** — see [LICENSE](LICENSE)
(unmodified GPLv3 text), [NOTICE](NOTICE) (attribution and modification notice) and
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

This is a **modified version of OwnTV**, changed since **2026-11-09** (GPLv3 §5(a)). As required
by §5(c), the entire derivative work — upstream portions and this fork's additions alike — is
licensed under GPLv3. No additional terms under §7 are added. Upstream published GPLv3 without
an "or later" clause, so no later version is offered here.

In short: you're free to use, study, modify and redistribute MyOwnTV, including commercially —
but any redistributed version (including forks and commercial products built on it) must also be
licensed under GPLv3, keep all copyright and attribution notices intact, and make its complete
Corresponding Source available (§6). **There is NO WARRANTY** — see §§15–16 of [LICENSE](LICENSE).

**Corresponding Source:** the complete source tree from which your copy was built, including
`LICENSE`, `NOTICE`, `THIRD_PARTY_LICENSES.md`, the OFL font license texts and the Gradle build
scripts. No public repository is configured for this fork yet; if you received a binary-only
build, request the source from whoever distributed it. Before publishing an APK, see the
[attribution checklist](docs/attribution-checklist.md).

---

<sub>MyOwnTV / МОЁ ТВ — open-source, player-only, built with the help of AI. A substantially
modified derivative of OwnTV (© ahXN00 and the OwnTV contributors, GPL-3.0-only); modifications
© 2026 Dmitry Alexandrov, GPL-3.0-only.</sub>
