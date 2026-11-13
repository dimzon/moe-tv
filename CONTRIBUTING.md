# Contributing to MyOwnTV / МОЁ ТВ

Thanks for your interest in improving MyOwnTV! Contributions of all sizes are welcome — bug fixes,
features, docs, or ideas.

> This project is a **derivative of [OwnTV](https://github.com/ahXN00/OwnTV)** (`GPL-3.0-only`).
> Read [NOTICE](NOTICE) before your first change: it records the origin, the modification
> notice and the credits that must stay intact.

## Ground rules

- MyOwnTV is a **player only**, and a **RuTube live-TV-only** client. No bundled channels,
  playlists, subscriptions or content, no bring-your-own-source providers, and nothing that
  facilitates access to unauthorized streams.
- It targets **Android TV** (D-pad / leanback first). Keep navigation remote-friendly.
- Match the existing code style and structure (Kotlin, Jetpack Compose for TV, Koin, Room).
- Never remove or rewrite an existing copyright, license or attribution notice from upstream code,
  resources or documentation. New files may carry
  `// SPDX-License-Identifier: GPL-3.0-only`.

## Project setup

1. Install **Android Studio** (a version that supports **AGP 9.x**).
2. Clone your fork and open the project; let Gradle sync.
3. Run the `app` config on an **Android TV emulator or device**, or build from the CLI:
   ```bash
   ./gradlew assembleDebug
   ```

A quick tour of the codebase lives in the [README](README.md), the RuTube API/sync/EPG contract in
[docs/rutube.md](docs/rutube.md), and the broader design reference in
[`extras/OwnTV_Master_Product_Brief.md`](extras/OwnTV_Master_Product_Brief.md) (upstream-derived).

## Workflow (branch → Pull Request)

We use a simple fork-and-PR flow:

1. **Fork** the repo (or, if you're a collaborator, create a branch directly).
2. Create a branch off `main`:
   ```bash
   git checkout -b feature/short-description
   ```
3. Make your changes and **commit** them with clear messages — these become the release changelog,
   so write them for a reader (e.g. `Fix EPG grid scroll desync on D-pad`).
4. Push your branch and **open a Pull Request** against `main`.
5. CI builds the app on your PR — please make sure it's green.
6. A maintainer reviews and merges.

> Keep each PR focused on one thing. Small, reviewable PRs get merged faster.

## Commit messages

- Write in the imperative: "Add…", "Fix…", "Refactor…".
- One logical change per commit where practical.
- Add `[skip ci]` to the message for doc-only or config commits that don't need a build.

## Reporting bugs / requesting features

Open an issue with what you expected, what happened, your device / Android TV version, and steps to
reproduce. Logs (`adb logcat`) help a lot for playback issues.

## Attribution & licensing

- The whole project, including your contribution, is **GPL-3.0-only** (no "or later" clause —
  the upstream work was published that way). See [LICENSE](LICENSE) and [NOTICE](NOTICE).
- Upstream portions stay © ahXN00 and the OwnTV contributors; this fork's modifications are
  © 2026 Dmitry Alexandrov. Your contribution is © you, licensed under GPL-3.0-only. There is no
  CLA and no copyright assignment.
- New dependencies must be **GPLv3-compatible** (Apache-2.0, MIT, BSD, OFL-1.1, GPL-family, or
  an explicit GPL option in a multi-license). Add the component, its version, license, copyright
  holder and source URL to [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) in the same PR —
  Apache-2.0 §4 and MIT require their notices to travel with any redistribution, and OFL-1.1
  fonts need their license text alongside the `.ttf` files.
- New bundled assets (fonts, logos, artwork) need a provenance line and a license file. Logos of
  Weblate, RuTube and OwnTV are third-party trademarks: keep the required notices in
  [NOTICE](NOTICE).
- Before any public APK release, work through
  [docs/attribution-checklist.md](docs/attribution-checklist.md) — it lists the remaining
  upstream URLs and the in-app legal notices that still need to be implemented.

By contributing, you agree your contributions are licensed under the project's
[GPL-3.0-only license](LICENSE).
