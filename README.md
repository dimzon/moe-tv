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
  <img alt="Player" src="https://img.shields.io/badge/engines-libmpv%20%2B%20ExoPlayer-FB8C00">
  <img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue">
  <img alt="Built with the help of AI" src="https://img.shields.io/badge/built%20with-the%20help%20of%20AI-8A2BE2">
</p>

---

MyOwnTV is a native **Android TV** live-TV **player** built with Kotlin and Jetpack Compose for
TV. It runs **two playback engines** — libmpv (FFmpeg) for the widest compatibility and
ExoPlayer (Media3) for near-instant Live TV — and it is a *player only*: the channel list and
the programme guide come from **RuTube's public API**, with no playlist, login or setup screen
needed.

> ⚠️ MyOwnTV ships **no** channels, playlists, subscriptions or streams of its own. It plays
> RuTube's publicly listed live streams. You are responsible for what you watch and for
> complying with the laws and rights that apply to you.

Open source, GPLv3, built with the help of AI. **Android TV only** — leanback launcher,
D-pad first.

---

## ✨ Features

### 🎬 Playback
- **Two engines** — mpv for compatibility, ExoPlayer for instant Live TV, with automatic fallback
- **4K HDR** direct rendering, optional auto frame-rate matching, surround sound, 150% volume boost
- **Subtitles** — text, image and closed captions, plus OpenSubtitles search and local files
- Resume, mini-player/PiP, picture-in-picture when you press Home, audio-only mode, live stream-info overlay
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
| Media | **libmpv** (FFmpeg) · **ExoPlayer/Media3** |
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

The database, sync, parsers, EPG storage and the playback engines live in this module under
`tv/own/owntv/**` — one source tree, no separate library to fetch. Only the libmpv engine
(`tv.own.owntv:libmpv`) comes from a public Maven repository.

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
   x86_64 emulators. The native player only loads on a matching ABI.
4. **Run** ▶. Minimum **Android 8.0 / API 26**.

Command line: `./gradlew assembleDebug` (`gradlew.bat` on Windows). The APK lands in
`app/build/outputs/apk/`.

## 🤝 Contributing

Contributions, bug reports and ideas are welcome — open an issue or a pull request. Please keep
the project's player-only positioning and match the existing code style.

## 🙏 Credits

### 🎨 Brand

The lowercase **owntv** wordmark was designed for OwnTV by [@m3th0d93](https://github.com/m3th0d93)
in [issue #227](https://github.com/ahXN00/OwnTV/issues/227).

### ▶️ Playback engines

- **[mpv](https://mpv.io/)** (via [libmpv](https://github.com/mpv-android/mpv-android), built on
  [FFmpeg](https://ffmpeg.org/)) — the default engine, for the widest codec, audio and HDR support.
- **[Media3 / ExoPlayer](https://github.com/androidx/media)** — the fast-start engine for Live TV
  preview and HLS.

### 🧩 Built with

[Jetpack Compose for TV](https://developer.android.com/jetpack/compose) ·
[Room](https://developer.android.com/jetpack/androidx/releases/room) ·
[Koin](https://insert-koin.io/) ·
[OkHttp](https://square.github.io/okhttp/) ·
[Coil](https://coil-kt.github.io/coil/) ·
[ZXing](https://github.com/zxing/zxing) — and the wider Kotlin / AndroidX open-source ecosystem.

Subtitle search & download is powered by [OpenSubtitles.com](https://www.opensubtitles.com/).
**This product uses the OpenSubtitles API but is not endorsed or certified by OpenSubtitles.**

## ⚖️ Legal

MyOwnTV is a media **player** only, and an unofficial client: it is not affiliated with,
endorsed by or connected to RuTube (ООО «Руформ») or to any other service. “RuTube” and
other names and logos belong to their owners and are used only to describe compatibility.
It ships with no channels of its own and does not endorse or facilitate access to unauthorized
streams.

## 📄 License

Released under the **GNU General Public License v3.0 (GPLv3)** — see [LICENSE](LICENSE).

In short: you're free to use, study, modify, and redistribute MyOwnTV, including commercially —
but any redistributed version (including forks and commercial products built on it) must also
be licensed under GPLv3 and its source made available.

---

<sub>MyOwnTV is an open-source, player-only project, built with the help of AI. Based on
OwnTV by its upstream maintainers.</sub>
