<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="brand/app-logos/logo_eggshell_light.png">
    <img src="brand/app-logos/logo_eggshell.png" alt="OwnTV" width="300">
  </picture>
</p>

<h1 align="center">OwnTV — Master Product Brief</h1>

<p align="center">
  <b>Native Android TV IPTV player · Live TV on ExoPlayer · Jetpack Compose for TV</b><br>
  <sub>Bring your own M3U, Xtream or Stalker/Ministra portal sources</sub>
</p>

---

## 1. Overview

OwnTV is a native **Android TV** IPTV player written in Kotlin with Jetpack Compose for TV, playing
Live TV on **ExoPlayer (Media3)** for near-instant channel changes.

It is a **player only**. You supply an Xtream login, an M3U playlist (URL or a local file) or a
Stalker/Ministra portal (Portal URL + MAC, with an optional username and password, and optional Serial Number, Device ID, Device ID2 and
Signature for stricter portals).

**Key principles**

| | |
|---|---|
| **Player only** | No bundled content, no subscriptions, no paywalls |
| **Remote-first** | D-pad navigation designed for a screen across the room |
| **Language-first** | 2 packaged interface languages (English, Russian), chosen before anything else |
| **One fast engine** | Single ExoPlayer/Media3 engine tuned for near-instant Live TV |
| **Built for scale** | Tested at ~50k channels |
| **Open source** | GPLv3, original code, built with the help of AI |

A companion **[OwnTV Mobile](https://github.com/ahXN00/OwnTV_Mobile)** app for phones and tablets
shares the same engine through a common core library.

---

## 2. Playback

All playback runs on one engine, chosen for live streaming.

### 2.1 Single-engine design

| Engine | Role |
|---|---|
| **ExoPlayer / Media3** | The only playback engine. Opens HLS almost instantly, so the preview and full screen appear with no wait; the running preview is promoted to full screen with no reload; zero-copy 4K/HDR rendering |

- **Track memory** — the audio and subtitle language picked in the player is remembered per
  channel (per profile).
- **Per-playlist provider quirks** — the "give up after" time and an HTTP Referer
  can be set for one playlist without touching the others.
- **The fallback ladder** — a failing live channel walks up to two rungs, each tried once:
  `ExoPlayer+HLS → ExoPlayer+TS`. **Give up on a
  channel after** (15/30/60 s or Never, default 30 s) bounds the whole tune; provider-requested
  back-off waits are not charged against it.
- **Reset saved player choices** forgets everything the player remembered per channel in one step.
- **Protected (DRM) playback** — Widevine and ClearKey over DASH and HLS, read from the playlist's
  `#KODIPROP` licence properties (a licence server, or ClearKey keys written inline), for live channels. The device's own CDM does
  the work, so there is nothing to configure and no licence to buy; the device's security level
  decides whether HD is served. Such a channel never leaves for an external player. Apple FairPlay has no Android CDM: such an item shows its own
  message at once instead of failing like a broken stream, and ClearKey keys filed under a FairPlay
  label play as ClearKey.
- **Container is decided from evidence, not from the file extension** — the declared
  `manifest_type`, then what the response actually turns out to be, then what the same provider has
  already been caught serving. This is what lets a DASH channel published at an extensionless address
  play at all, and it is the only route available to Stalker portals and Xtream panels, whose stream
  addresses can carry no declaration.

### 2.2 Rendering

- **Direct-to-display zero-copy pipeline** — the decoder writes frames straight to the screen, giving
  smooth 4K HDR with the panel's own HDR handling.
- App-drawn subtitles on the direct path; automatic **software-decode fallback** for streams the
  hardware decoder mangles; late-frame dropping to hold A/V sync on high-bitrate content.
- **Frame-rate matching** (opt-in) — asks the display for the video's native rate to remove judder.
  When a live stream declares no rate it is measured, and used only when two samples agree. Android
  TV's own *Match content frame rate* preference is honoured above the app's toggle.
- **Live buffering under user control** — the **Live latency** choice sizes the real buffer,
  and a separate **Pre-buffer** gate (off / 2 / 5 / 10 s) holds playback until enough video
  is collected. Both overridable per playlist.

### 2.3 Audio

- Every audio track is exposed with language labels and switchable on the fly.
- **Surround sound** — *Auto* / *Stereo only* / *Surround*. A shared,
  non-disableable output watchdog (silence, sink error, repeated underruns) latches the session to
  stereo when the sink accepts a format it cannot actually play. ExoPlayer's recovery recreates
  the surface and resumes at the same position rather than mistaking an audio problem for a video one.
- **Volume boost to 150%** with a soft limiter.
- **A/V sync nudge** in 25 ms steps, optionally remembered per channel.
- **Previous channel** — a player-bar button, the remote's Last-channel key and the media controls' "previous".
- **Night mode and Volume leveling**, and a
  **Dolby/DTS passthrough** switch; either of the first two makes ExoPlayer decode in the app.
- **Maximum video quality** plus a **Quality** button for what is playing now; experimental **tunneled playback** for
  live ExoPlayer, offered only where a decoder supports it and switched off after the first failure.
- **Audio-only items are labelled, not failed** — a radio channel shows an *Audio only* plate, so
  sound with no picture is never mistaken for a fault. Distinct from **Audio Mode**, which is the
  user switching the picture off.

### 2.4 Subtitles

| Kind | How |
|---|---|
| Text (SRT/ASS) | Drawn by the app on the direct render path |
| Image (PGS/VOBSUB/DVB) | Handed to a second ExoPlayer layer, keeping video on the zero-copy/HDR path |
| Closed captions (CEA-608/708) | Decoded from the video stream into a selectable track |

Independent **font, scale, colour, position and background** on the app-drawn
overlay; *Default* preserves authored styling. **Preferred audio/subtitle language** (per profile, 50
languages, plus **Original language** for audio — the stream's original-language track,
else the stream's main track) selects the matching track.
A timing row in the Subtitles menu (◀ ▶ 0.1 s, OK = back to zero) fixes subtitle timing.

### 2.5 Player HUD

Play/pause · audio and subtitle pickers · zoom and aspect (Fit · Fill · Stretch · Original · Force 16:9 · Force 4:3 — a press steps to the next, hold OK for the list) · **Sources** (the same channel in another quality, category or playlist, from core) · volume
with mute · favourite the current channel · **stream info overlay** (codec, resolution, fps, bit depth,
HDR type, bitrate, decoder, audio, buffer, dropped frames, masked source URL) · a **clock** in every
mode · auto-hiding controls, with **Back**
hiding them first and then exiting.

### 2.6 Channel zapping

**CH+/CH−** always switch channels for the whole Live TV session, including while the
controls are up or playback is starting, wrapping at both ends. **Up/Down** (controls hidden) and the
media ⏮/⏭ keys do the same. **Left** opens the channel list for the context the channel was opened
from; **Left again** opens a two-pane browser without leaving full screen — Live TV's own category
sheet (Favorites, Recently watched, All channels and every group) beside the chosen
category's channels. OK on a category changes the right-hand list; Right / Left move between the
panes; OK on a channel plays it; Back closes both. Browsing never tunes or changes the CH± list —
only the channel picked arms it from its list. **Right** shows the last 30 channels with when
each was watched. All three are Stage glass sheets at the screen edge, and CH+/CH− page through them. Typing a **channel number** tunes it directly.

### 2.7 The mini-player

The **mini-player** docks a channel to a corner and keeps playing across the whole
app; selecting another channel updates it in place. Size and position are one popup in Settings.
With **Picture-in-picture when leaving the app** on (off by default, only on TVs that offer it), Home
keeps a playing video going in the system's small window; a screen OwnTV opens itself never does.

### 2.8 Robustness

Memory budget scales to the device · a decode watchdog blocks 4K/8K software-decode death spirals
with a clear error · backgrounding releases the stream immediately · every player command runs off
the UI thread · auto-reconnect with backoff on dropped live streams · screensaver
restore re-tunes a live channel to the edge · the same
happens when the TV is switched off and on while the box keeps running (its last sound output gone
for 6 s) · Play after a pause of over a minute on live goes back to the live edge.

---

## 3. Browse

### 3.1 Home

Home opens with the **Favourite channels** row (cards or an On-now
mini-guide) and an optional **Recent channels** row; channel rows can be
switched between Cards and On now, and a **Sports** row can show followed matches that are live
or about to start.
Home feeds the system **Watch Next** row on stock Android TV launchers.

### 3.2 Sections

**Live TV** — a header with the category and channel count, a tool row (search, sort, Guide view) and
84-high channel rows (number, logo plate, name, programme and time left, progress, provider tags,
favourite, playlist mark). Beside them the preview pane plays the channel with a LIVE badge,
the real quality / fps / sound, the programme with progress and synopsis, and next / later (for reading). **▶**, the Info key or the channel menu's first item opens **Program
details**: the channel's day as one-line rows grouped Earlier · On now · Later, the focused one opened
in place with facts, tags, the whole description and its actions; Yesterday / Today / Tomorrow on top.
**OK previews first** (one switch for Live TV, off by default; one for the TV Guide, on): with it on,
focus no longer plays the preview — the first OK does, OK again goes full screen, and the video stays
on that channel while the info pane follows the highlight. **◀** brings the
categories in as a glass sheet (**Stage** layout) or keeps them as a column (**Separate**). The
channel menu is a grouped Stage menu (Watch / Channel / Guide data / Organise), opened by holding OK
or the Menu key. **Guide view** shows the guide grid inside Live TV, and *Live TV opens in* remembers
List or Guide. The last category of Live TV and the last channel are remembered per
profile and follow a profile switch at once; only a category the user picks is saved. *TV Guide follows
Live TV* (Browsing & lists, off by default) gives the TV Guide Live TV's remembered category.

**Search** — filter buttons Live TV / TV Shows with counts, switched on and off
with OK (none on = everything); a channel result shows Live TV's own
preview pane. It is a show-only page: **OK goes to the item** in
its own category, and Back returns to Search with the query kept. Empty, it offers Jump to,
recent searches. A **TV Shows** group lists stored-guide programmes whose title
matches, on now or in the next 12 hours, and OK goes to the channel.

**Sports** — live scores, today's and tomorrow's matches and match pages from ESPN, for the
sports and leagues the profile follows (none at first; Settings → App → Sports). A match page has the
score, venue, timeline, stats, line-ups, table, form and Broadcasts, and **Find channels** ranks the
channels in the user's own guide that show it (from 30 minutes before kick-off), then the channels
named after the competition by any of its names, with a filter by language, country, picture and
playlist and a switch for those usual channels. Matching runs on the device; no playlist data leaves it.
The page has three tabs sharing one set of sport and league buttons: **Scores** (the above),
**Calendar** (a month grid of the followed leagues with the chosen day's matches; months saved on the
device and fetched again per the Calendar refresh setting) and **Leagues** (league cards, then one
league's table, fixtures and results; Formula 1 drivers, constructors and races; tennis rankings).
Teams have their own pages: the season's fixtures and results, next match, last five and table place.
A Home **Sports** row shows followed matches that are live or about to start.

**More** — a glass section list with the profile row: Settings, Favourites, History, Backup &
Restore, Local sync, Error log and About, each an enterable page beside the list.

### 3.3 Multiple playlists

Merge every playlist into one browse, or narrow the whole app to one. The choice applies everywhere
at once and survives a restart; the **playlist pill** in the top-right switches it, listing each
playlist with its short mark (IPTV_GOLD → GOLD) and channel count. It is
display-only — nothing is deleted or re-imported. When two sources are active for a section, compact
**provider labels** identify categories and items throughout. **Test connection** reports account
status, expiry, trial and connections in use, and writes nothing.

### 3.4 Stalker / Ministra portals

A third source type alongside M3U and Xtream: Portal URL + MAC, with an optional login (username +
password, for portals that ask for one) and optional Serial Number, Device
ID, Device ID2 and Signature. MAG handshake auth with in-memory tokens and User-Agent presets. Play
links are minted per play and silently re-resolved on expiry, so long sessions survive token resets.
Full feature parity: external
player, backup, auto refresh and the playlist switcher. The catalogue crawl backs off when a
portal refuses parallel requests and remembers that limit per playlist.

### 3.5 Layout

The **Stage** interface (designed in #227) is the app's one interface. Every screen sits on a full-width Stage page in the
**Plus Jakarta Sans** font, with one line-icon set, CSS-exact focus glows and a shared Stage popup kit.

- **Navigation rail** — Search, Home, Live TV, Sports, TV Guide and More, the
  wordmark at the top and the profile's avatar (or own picture) at the bottom; a **Now playing** item
  appears while something is docked or in Audio mode. Settings → Layout → **Navigation**: **Floating**
  (a capsule over the content that opens on ◀ and hides after 2 / 4 / 8 s) or **Docked** (always
  present, content reflows; recommended); **Size** Compact / Normal / Wide (+ counts and profile line)
  / Extra wide (+ a details line per item); **Length** Fit to items / Full height; **Widen on focus** (off by default)
  for Docked + Compact (off by default); **Menu items** Dynamic (follows the playlist) or Static (tick Home, Live TV and
  TV Guide; Search and More are never hideable). ▶ out of the rail always
  returns to the exact control left from.
- **Top-right cluster** — the **Continue** pill (Last channel), the
  playlist pill, and a large clock with date and weather in user-chosen colours. **Audio mode** rests
  as a glass pill in the Continue pill's place.
- **Panel widths** — named by the layout each screen uses: Separate layouts keep three shares
  (the third may be 0%), Live TV Stage sizes list + preview and the categories sheet on its own scale.
  The Guide's two columns split independently.
  Theme is System / Dark / Light. Light repaints the whole app — pages, glass, focus rings (the accent with a
  page-coloured gap), the player HUD and its lists — from one dark/light Stage palette; video stays as it is,
  white logo art is redrawn in dark ink, and a background picture fades towards the paper.

**Live TV layout** is **Stage** (categories as a sheet on ◀) or **Separate** (an always-visible
column).

### 3.6 Categories, search and memory

Folder rails with Favorites and History per section; full category names, never abbreviated; a
category search box. **Customize** (per profile) hides, renames and reorders categories and
individual items, recovers hidden ones, and filters to All/Visible/Hidden; it holds the **category lock PIN**.
**Bulk rename** applies ordered prefix/suffix rules with automatic cleanup, a review step and a
restore-original undo — over a category's items, or over the category names themselves (⋯ menu). **Custom combined categories** gather items from anywhere. A category can also
be hidden, moved or restored to the playlist's default (hidden items, items moved out and custom
order are listed and undone together) straight from the browse screen by holding OK; **Restore
playlist defaults** in Customize's More menu does the same for the playlists shown in a section. All
customizations survive re-syncs. Removing a channel from a custom category is recorded so a sync cannot bring it back, and
hiding a provider category no longer empties custom categories. Custom categories also appear in the
player's channel list and the Multiview picker. A typed category search is kept while a channel plays;
Back in the list clears it.

Search covers Live TV and the guide together (3.2), with voice search where the device has a speech
recogniser. Live TV reopens on the category you last used, with focus on the last channel. **App
startup** is per profile: Home, last channel, the last channel in the TV Guide or Live TV, Live
Favorites, or one chosen channel. **Auto start** (device-wide, off by default) opens OwnTV when the TV
boots or wakes from sleep; it needs "Display over other apps", and a profile with a PIN is locked
again on every wake.

---

## 4. EPG / TV Guide

- **Full time × channel grid** (XMLTV), opening with the current time 3/8 across and two recent
  hours already loaded. The focused channel plays in the top video through Live TV's own preview
  player (no second decoder), with the programme under the cursor beside it and key hints at the
  corner; below, one control line — Today, Now, Category, Order, Search, Auto-match EPG — and a ruler
  with the now-line. The grid is also reachable as **Guide view** inside Live TV.
- Two-stage rows: focus selects the row, **OK** or **Right** steps into its programmes, **Back** steps
  out. Cells show reminder icons; overlapping programmes are drawn one after
  another; an empty stretch is a focusable "No program at this time" cell that Left/Right cross in
  30-minute steps. Every programme action (Program details, Remind me,
  Watch channel…) is in the Hold-OK menu; the Info key opens Program details directly — the channel's day as a page, opened on that
  programme.
- Order by Provider, A–Z or Favourites first; the category filter is Live TV's own
  list, and Provider/A–Z follow Live TV's order with moved channels in their place; *Show → Channels without guide* and *Show → Video preview* (off shows the channel's logo
  instead of playing it; kept in backups).
- **Programme reminders** — at the chosen lead time (5 min by default) OwnTV asks to switch, over any
  screen including the player, or switches, or only notifies.
- The grid reloads only once a guide sync finishes, instead of rebuilding every ~25 s during one.
- **Auto-match EPG** links channels to guide data when `tvg-id` is missing or wrong, scanning only the
  playlist(s) the guide is showing (a 400-channel playlist in about 3 s); confident matches
  apply automatically and the rest go to a review list. Matches are per profile and survive re-syncs.
  A **guide time offset** corrects a feed published in another time zone, globally or per channel.
- **Several XMLTV feeds:** the order in EPG Sources is the guide priority — where two feeds list one
  channel, the higher one is drawn and a lower one only fills its gaps.
- **Multiple XMLTV feeds** merge into one guide. Opt-in: importing a playlist does not auto-download
  its guide. Each feed can use its own channel logos and has its own auto-refresh interval.
- **Performance** — pre-loaded in the background; only your channels' programmes are stored; a
  malformed tag no longer aborts the guide; batched loading instead of a query per row.

---

## 5. Multiview & connections

### 5.1 Multiview

Up to four live channels at once, entered from the player or by marking channels with **Add to
Multiview**. One tile carries the sound at a time; **sound only** gives up a tile's picture and keeps
its commentary. A tile that cannot start explains why rather than sitting blank. Leaving the grid
stops everything.

Tiles draw through a **TextureView**, not a SurfaceView: a device has very few hardware video planes
and several have exactly one, so a second SurfaceView would get audio and no picture.

### 5.2 Connection limits

Most providers never publish how many streams an account may run, so OwnTV **measures it once**, at a
playlist's first sync, before any channel rows exist and while nothing is playing. The result is
stored with the playlist, carried in backups (it describes the account, not the device) and used to
warn before a stream is refused. A broken channel cannot fool it — it stores nothing rather than a
wrong number. **Settings → Playlists → Info** shows it; **Re-test** re-measures behind a warning.

---

## 6. Profiles

Multiple profiles, each with its own favourites, history and layout. Optional
**PIN locks** (salted hash) and a **"Who's watching?"** launch gate. Three separate locks, each with its
own PIN and wrong-entry limit — profile, category and a per-profile **Settings lock** (Settings, Backup &
Restore, Local sync, and optionally long-press changes). A profile with a profile or Settings lock is
**protected**: a new profile does not inherit its playlists, and another profile backs it up, merges or
replaces it in a restore, edits or deletes it only after one of its PINs. Local sync is profile-based:
the profile in use merges with the one in use on the other device. **Kids mode** hides adult
provider categories and items across every surface — Live, Home, Search, Guide,
launcher recommendations and direct playback;
the Guide itself is hidden. Detection uses multilingual category markers with an *Adult Swim*
exception. Sources can be shared between profiles, and profiles switch without leaving the app.

---

## 7. Personalization & settings

**Settings is twelve group pages** under More → Settings, each opened from a card with its row count:
the group's rows on the left, and a glass **context panel** on the right with the focused row's
explanation, its CHOICES (the recommended one tagged) and key hints. Values sit right on each row —
switches, choices, steppers, segmented controls (◀ ▶), "N saved · Reset", accent swatches — with a
chevron only where a row opens another page; a list of choices or a number is edited **inside the
panel**, not in a popup. Larger editors (Playlists, EPG sources, Customize, Home screen, Proxy, DNS,
Panel and Guide widths, Long-press menus, Fonts & text size, Browsing & lists, Subtitle appearance,
Glass & background…) are full Stage pages with a breadcrumb. Returning from a sub-page restores focus
to the exact row. **Search all settings** matches setting names and shows path, title and value.
**Hold OK** on any row pins it to **Quick**; pins of player rows say "Pinned from <group>" and open
their group.

| Group | Holds |
|---|---|
| **Quick** | The user's pinned rows |
| **Profile** | Profiles, Add a profile, kids mode and PIN per profile |
| **Sources & guide** | Playlists, EPG sources · EPG time offset, Programme reminders, Reminder time, Guide days to keep |
| **Appearance** | Theme, Accent color, Focus highlight, Glass & background, Ambient Glow (Dark + Glass off only), Fonts & text size, Popup size, UI zoom, Animations, Date, time & weather — with a live Home preview |
| **Layout** | Navigation, Live TV layout, Live TV opens in, Panel widths, Guide column widths, Browsing & lists, Home screen, Long-press menus, Remote shortcuts |
| **Content & metadata** | Customize categories & items |
| **Player** | Saved player choices, External player · Network timeout, Reconnect attempts |
| **Picture** | Hardware decoding, Prefer SDR, Maximum video quality, Tunneled playback, Default zoom, saved zoom · Auto frame rate (Live TV) |
| **Sound & subtitles** | Default volume, Surround, Dolby/DTS passthrough, Night mode, Volume leveling, Audio sync (+ saved) · Preferred audio / subtitle language, Subtitle appearance |
| **Live TV** | Channel numbers, Live preview, Preview audio, OK previews first · Live latency, Pre-buffer, Give up after (each + per playlist) |
| **Watching & recording** | Multiview, Max tiles, Mini-player, Picture-in-picture when leaving the app |
| **App** | Language, App icon, Accent-colored logo, App startup, Auto start on boot / on wake-up, Check for updates (+ on startup) · Proxy, DNS · Measured stream stats, Detailed playback logging |

- **Appearance** — any accent colour (presets, hex, a saturation square and hue bar as separate focus
  stops, live re-tint, Cancel restores), a separate **focus highlight**, one font family for the app and
  popups (**Plus Jakarta Sans** by default), main and popup text sizes, popup size and UI zoom stepped
  in the row, and clock / date / weather colours.
- **App icon** — the flip-card icon in eight colours (Eggshell by default) or **Pixel**, the
  dot-matrix TV set, changing the app row banner, the launch screen (a 600 ms dot sweep on Android 12+,
  a still mark with Animations off) and every in-app logo. **Accent-colored logo** tints the play
  triangle. The lowercase **owntv** wordmark is #227's, by @m3th0d93.
- **Glass & background** — the background is **Stage** (soft accent light), **Picture** (a local or
  phone-sent image in a Sharp, Soft or Dark look, with Darken and Blur) or **Plain**, with an optional
  **Accent light**; **Glass** frosts the rail, sheets, menus and popups at the chosen opacity, with a
  live Live TV preview. The older per-surface glass controls are kept in core for the phone only.
- **Ambient Glow** — a separate radiance (optionally pulsing) for the solid interface, offered only
  with the explicit Dark theme while Glass is off.
- **Remote Shortcuts** — short and long presses of spare colour, number, channel and media keys
  mapped to 26 actions. Essential keys stay protected; the shipped CH+/− paging remains the default.
  **Player buttons** on the same page: what holding or pressing OK opens in the full-screen player,
  swapped channel up/down, and number keys on or off; every default is the player's own behaviour.
- **Stage defaults** are written once at the first start of v5.1.0 (core `applyStageDefaults`); a
  value the user already stored is kept.
- **Developer** (dev-tools builds only) lives under More, not Settings.
- **Error log** (More) — the last crash plus a readable history of playback failures, fallbacks and
  reports, with optional detailed tracing. A crash is written to disk as it happens, so it survives
  the process dying. Export writes `Download/owntv-playback-report.txt`.

---

## 8. Language & first run

A fresh installation opens with a **language selector before Get Started**. English plus Russian;
further requested languages stay catalogue-only until they reach the reviewed readiness threshold. Every wizard step shares one frame (lockup, step dots, glass panel, Back / Next,
key hints). The first run is welcome (which carries the language selector) → display (UI zoom and
font steppers, app icon picker, a sample row) → disclaimer → **Set up OwnTV** → profile → **add a
playlist** → the import → **All set**, whose **Add a TV guide** opens Settings → EPG sources → Add once
the shell is up. **Set up OwnTV** offers three routes, not two: create a profile, restore a backup
file, or **copy everything from another OwnTV device** over the local network — so replacing a box
does not mean finishing setup first and then finding Local sync in the menus. **Add a playlist** is
four cards — **Remote**, **Type it here**, **Import**, **Existing** — and Remote is what keeps an
Xtream password off the D-pad: it hands the form to a phone over Wi-Fi. The same Remote / Type it here
pages are reached from Settings → Playlists → Add later, so the choice is never a first-run-only
opportunity.

App language is independent of profiles, and survives
restart and backup/restore. Locale-aware plurals, dates, times, numbers, RTL navigation, font
fallback and English fallback apply across the interface, notifications, launcher text, companion
pages, player messages and diagnostics — without changing playback or sync behaviour. A generated
locale catalogue, six resource domains, Hosted Weblate contribution paths, tooling tests and CI
checks protect placeholders, plural forms, formatting, overflow and release packaging.

---

## 9. Backup, sync & updates

**Backup & Restore** writes a single `.own` container: the backup data and the background picture.
An optional **backup password** encrypts the whole container
(AES-256-GCM, PBKDF2); without one the file is unencrypted and every secret is omitted — source and
proxy passwords, Stalker identity, PIN hashes. Restore accepts
`.own` and legacy `.json` — from a file on this TV or sent from a phone (**Remote**) — detected by
content rather than extension, and **merge-restore remaps
every id** the file carries. Sources are matched on type + URL + username; a Stalker portal on type + URL + MAC.

> Android's automatic backup is deliberately **disabled**: the raw stores hold plaintext credentials,
> so this screen is the single explicit, encryptable path off the device.

**Local sync** swaps favourites and history with the OwnTV mobile app over the
local network — no account, no cloud. Both devices enter Sync mode deliberately, an arriving
container is previewed before it is applied, and a deletion propagates as a deletion rather than
being undone by the merge. The same sync is offered during first run, where a device that has
nothing yet only receives and never hosts, so only the established device enters Sync mode.

**Updates** — in-app, from GitHub Releases, with an optional startup check, the full changelog on a
manual check, and installation on the TV itself.

---

## 10. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.20 (no `kotlin-android` plugin; the Compose compiler plugin pulls the Kotlin Gradle plugin to this version) |
| Build | AGP 9.4.0 / Gradle 9.8.2, KSP2 2.3.11, JDK 25 |
| UI | Jetpack Compose for TV (`androidx.tv:tv-material` 1.1.0), Compose BOM 2026.08.00 |
| Media | ExoPlayer/Media3 1.11.1 |
| Database | Room 2.8.5 + Paging 3.5.1 + FTS4 (WAL) |
| DI | Koin 4.2.2 |
| Networking | OkHttp 5 — the panel-facing client is pinned to HTTP/1.1 for flaky IPTV panels, while the image client keeps h2 so poster grids multiplex on one connection |
| Images | Coil 3.6.0 |
| Preferences | DataStore |
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 37`, `applicationId tv.own.myowntv` |

---

## 11. Architecture
**Re-test** measures for real, and a measured limit outranks the Xtream panel's own number at the next
sync. **Ignore connection limit** (per playlist) lets Multiview try more streams than
the playlist reports; the provider's refusal still stops a stream with its sentence.

### 11.1 Three repositories

| Repo | Holds |
|---|---|
| **[OwnTV_Core](https://github.com/ahXN00/OwnTV_Core)** | Database, sync, parsers, EPG, backup, profiles, settings storage, the playback engine, **and every translated string** |
| **[OwnTV](https://github.com/ahXN00/OwnTV)** | This app — Compose-for-TV interface, navigation, player HUD |
| **[OwnTV_Mobile](https://github.com/ahXN00/OwnTV_Mobile)** | The phone and tablet interface |

Core is published as `tv.own.myowntv:core` and `tv.own.myowntv:player-core`; both apps always consume the
same version.

### 11.2 Parsing & sync

M3U playlists are line-streamed and Xtream `player_api` JSON is read with `android.util.JsonReader`,
so a huge provider payload is never fully buffered. Every listed stream is read as a live channel.
A playlist that numbers
none of its channels is numbered 1, 2, 3… in file order. A per-playlist **Clean channel names** option
tidies country prefixes and trailing quality tags at sync time, while guide matching keeps using the
provider's own names. Re-syncs diff by content hash and keep the provider's order without rewriting
unchanged rows, so an unchanged re-sync writes almost nothing. `SyncManager` inserts in chunked transactions with progress and
cancellation, with the database write on its own coroutine fed over a rendezvous channel — so
downloading and parsing overlap with writing the previous batch. A truncated bulk list (HTTP 512)
falls back to per-category fetching.

### 11.3 Companion server

The companion server — reached over the local network with a QR code and a 6-digit PIN — can
receive an uploaded `.m3u` file, fill in a source form from a phone, and edit one saved playlist
from a browser; secrets are never sent to that page, an empty field keeps the stored value, and the
TV's own Save applies it. The same channel hands over a personal key or credentials when a feature
needs one, so nothing long has to be typed with a remote.

### 11.4 Storage

A Room schema covering profiles and sources (cross-referenced for sharing), content (categories and
channels), per-profile favourites/history/progress, EPG
channels and programmes, and FTS4
search tables. Paging 3 with a bounded `maxSize` keeps memory flat
across 50k-item lists. `ANALYZE` runs after sync and at startup to keep query-planner stats fresh.

### 11.5 Player

One ExoPlayer-based engine behind the `PlaybackEngine` interface, with the fallback ladder and its watchdogs in
`:player-core` and therefore shared with the mobile app. Live promotes the running preview straight
to full screen; the shell hoists the active surface between full screen and mini-player; player state
is published as `StateFlow`s for the Compose HUD.

### 11.6 Dependency injection

Koin modules: `appModule`, `databaseModule`, `dataModule`, `playerModule`.

---

## 12. Legal

OwnTV is a media **player** only. It ships with no channels, playlists, subscriptions or content, and
does not endorse or facilitate access to unauthorized streams. Users are solely responsible for the
sources they add.

Released under the **GNU General Public License v3.0**.

Translations hosted free of charge by
[Hosted Weblate](https://hosted.weblate.org/projects/owntv/).
