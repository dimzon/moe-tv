# МОЁ ТВ / MyOwnTV — Руководство пользователя (актуальный форк)

> **Это руководство описывает текущий форк.** МОЁ ТВ / MyOwnTV — **клиент только прямого эфира
> публичного каталога каналов и программы передач RuTube для Android TV**. Не нужно добавлять
> плейлист, провайдера, аккаунт и проходить мастер настройки. Обновлено: 2026-11-13.
>
> Полный список возможностей: [README.md](../README.md#features) · контракт API и правила данных:
> [docs/rutube.md](../docs/rutube.md) · юридические уведомления: [NOTICE](../NOTICE),
> [THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md).

## Первый запуск

1. Установите APK (см. [«Установка» в README](../README.md#install)). На Fire TV обычно
   используют приложение **Downloader**, в остальных случаях — установку с USB-накопителя,
   *Send files to TV* или `adb install MyOwnTV.apk`.
2. Запустите из leanback-лаунчера Android TV. **Настраивать ничего не нужно**: при первом старте
   список каналов и недельная программа загружаются из публичного API RuTube.
3. Выберите канал кнопкой **OK**. **Back** — на уровень вверх, **D-pad** перемещает фокус.

Требуется Android TV / Fire OS **8.0 (API 26)** или новее. Приложение рассчитано на пульт и не
предназначено для сенсорных телефонов.

## Навигация

- **Рельса** слева переключает Главную, Прямой эфир, Программу, Поиск, Избранное и
  «Ещё»/Настройки. Секции Главной можно переставлять в Настройках.
- **Долгое нажатие OK** (или кнопка **Menu** на пульте) на канале открывает меню: избранное,
  переименование, скрытие. Карточки на Главной сразу запускают канал или открывают раздел, а меню
  для них доступно на том же канале в Прямом эфире.
- В **Поиске** есть экранная клавиатура; избранное и недавно просмотренные каналы собраны в
  разделах медиатеки.

## Прямой эфир и программа

- Каталог — это **публичный список эфирных каналов RuTube** (~250) с категориями, логотипами и
  уточнёнными названиями для одноимённых брендов. В APK ничего не встроено: приложение не
  поставляет собственных каналов, потоков и подписок.
- Программа хранит **расписание RuTube на неделю**, загружается по дням через upsert, поэтому
  неудачное обновление не стирает уже сохранённое. В строках каналов видны «сейчас» и «далее»,
  из программы можно поставить напоминание о передаче.
- Каналы можно добавлять в избранное, скрывать, переименовывать и группировать по категориям;
  доступ к категории можно закрыть **PIN-кодом**. Поиск работает по каналам и передачам.

## Воспроизведение и пульт

- Один движок — **ExoPlayer (Media3)**, заточенный под быстрое переключение эфира. Доступны 4K HDR
  без промежуточной обработки, необязательное автосогласование частоты кадров, объёмный звук и
  усиление громкости до 150%.
- Настройка вида субтитров (масштаб, шрифт, цвет, положение, фон) применяется к дорожкам,
  которые предоставляет поток. Поддерживаются мини-плеер/PiP по кнопке **Home**, режим «только
  звук» и оверлей с информацией о потоке; кнопки медиасессии и audio focus работают как у
  ТВ-приложения.
- Если поток не запустился, плеер показывает понятное сообщение и маленькую строку
  **`err: ...`** внизу — при отправке баг-репорта скопируйте её дословно.

## Настройки, языки, обновления

- В Настройках: воспроизведение, субтитры, поведение программы, раскладка Главной, PIN-код
  категорий и проверка обновлений. Упаковано **четыре языка интерфейса — русский, английский, украинский и белорусский**
  (апстрим поставлял 26).
- Встроенный обновляльщик читает релизы GitHub; пока репозиторий форка приватный, он не видит
  ни одного релиза. Релизы (когда появятся) содержат `MyOwnTV.apk` (arm) и сборку x86_64.

## Сообщить об ошибке или предложить идею

Используйте формы issue в репозитории. Перед отправкой прочитайте раздел форка в этом
руководстве, проверьте Настройки, поищите существующие issues и убедитесь, что у вас последняя
версия (Ещё → О приложении). Полезнее всего приложить logcat и фото экрана с ошибкой — сначала
удалите личные данные и токены. Просьбы вернуть провайдеров Xtream/M3U/Stalker, фильмы/сериалы,
трейлеры, загрузки, catch-up или профили **не рассматриваются** в этом форке.

## Что убрано намеренно

Удалено после импорта 2026-11-09: источники Xtream / M3U / Stalker и модель нескольких
плейлистов, mpv / libmpv / FFmpeg, VOD (фильмы и сериалы), трейлеры, загрузки, catch-up,
профили, файловый бэкап, интеграции TMDB и OpenSubtitles, сопоставление каналов с EPG и сравнение
«Sources» в плеере. См. [«Происхождение и атрибуция» в README](../README.md#origins) и
[CHANGELOG.md](../CHANGELOG.md).

---

# Историческое руководство апстрима — OwnTV v5.2.1 (архив, НЕ относится к этому форку)

> ⚠️ **Всё ниже — неизменённое Руководство пользователя, унаследованное от апстримового OwnTV**
> (снимок импортирован 2026-11-09, сразу после апстримового v5.2.1). Оно сохранено только для
> провенанса и атрибуции. Там описаны плейлисты, порталы Xtream/M3U/Stalker, профили, VOD, спорт,
> catch-up, загрузки и сервисы TMDB/OpenSubtitles — **ничего из этого в форке нет**. Не используйте
> этот текст при работе с МОЁ ТВ / MyOwnTV; смотрите разделы выше.

---

# OwnTV — User Guide

Everything OwnTV can do, **where to find it**, and the remote shortcuts that make it fast.

Each entry is one line about what it does and one line about where it lives. Nothing here is
required reading — skim the headings and stop where something looks useful.

> **The basics:** **D-pad** moves · **OK** selects · **Back** goes up a level.
> The menu on the left is the **rail**. **Long-press OK** (or the remote's **Menu** key) on almost
> anything opens its menu — that is where favourites, rename and hide live. Home's
> cards have no menu: they play or open, and the menu is on the same channel in Live TV.

---

## 🚀 Start here

A fresh install walks you through this, in order. Nothing here is permanent — every choice can be
changed later in Settings.

```
Welcome  →  Display  →  Disclaimer  →  Set up OwnTV  →  Profile
                                                          ↓
              All set + TV guide  ←  Import  ←  Add a playlist
```

Every step has the same frame: step dots at the top, **Back** / **Next** at the bottom, and the keys
for that step shown in the corner.

1. **Welcome** — the language picker is on this screen: English or Russian, or keep **System
   default**. Press **Get started**.
2. **Display** — step UI Zoom and Font size while a sample row resizes, so you judge it from your
   sofa, and pick the **App icon**. Changeable later in Settings → Appearance and Settings → App.
3. **Disclaimer** — OwnTV is a player; you bring the sources.
4. **Set up OwnTV** — three ways to begin: **New profile**, **Restore a backup**, or
   **From another device** (copy everything off a TV you already have — see below). A restore asks
   which parts of the backup to bring back before it applies anything, so you can take the playlists
   and leave the old box's settings behind. Untick **Sources** and it warns you: the TV then gets no
   playlists. The app opens only once the whole restore is done, and starts downloading the restored
   playlists straight away. An Xtream playlist whose password was not in the backup waits instead:
   the last screen names it, and typing the password in that playlist's edit form in Settings loads
   it. An encrypted backup asks for its
   password; press ▶ on the field to see what you typed.
5. **Profile** — name it, pick an avatar, and optionally make it a **Kids** profile or give it a PIN.
6. **Add a playlist** — four cards: **Remote** (fill it in on your phone), **Type it here** (type it
   with the remote: M3U, Xtream or Stalker), **Import** a backup file, or **Existing** (only shown
   when another profile already has one). **Skip for now** is a valid answer; you can add one later
   from Settings.
7. **Import** runs, then **All set** — with an **Add a TV guide** button that opens Settings → EPG
   sources → Add as soon as the app is up.

### 🔄 Already have OwnTV on another device?
**Where:** the **Set up OwnTV** screen → **From another device**
Copies your profiles, playlists, favourites and history straight over your Wi-Fi —
no account, no cloud, no file to find. On the device you already have, open **⋯ More → Local sync**
and turn **Sync mode** on; it stays on while that screen is open. The new television then finds it on
the network, you type its 6-digit PIN once, tick what to bring across, and see exactly what will
change before it changes. A television being set up only ever *receives* — nothing on the old device
is altered.

### 📱 Type the playlist on your phone instead
**Where:** step 6 → **Remote**
The TV shows a QR code and a 6-digit PIN. Scan it with a phone on the same Wi-Fi, fill the form
there — or upload an `.m3u` file straight from a computer — and press **Send to TV**. You still press
**Start Import** on the TV. Available from Settings later too, see
[Remote](#-remote--fill-the-form-on-your-phone). *(Idea from community PR #66 by @zarga03.)*

### ⏳ Don't wait for the import
**Where:** the import screen → **Run in background**
Enter the app immediately. A status pill at the bottom shows progress.

---

## 🧭 Getting around

```
┌──────┬─────────────────────────────────────┬──────────────────────────────┐
│ Rail │  Title · crumb          [Continue] [Playlist ▾]  Clock · weather   │
│ logo │  search · sort · view                                              │
│  🔍  │  Channels / posters / rows            │  Preview / details         │
│  🏠  │                                       │                            │
│  📺  │  ◀ Left opens the categories          │                            │
│  🗓️  │                                       │                            │
│  ⋯   │                                       │                            │
│  🙂  │                                       │                            │
└──────┴─────────────────────────────────────┴──────────────────────────────┘
```

### 🧭 The rail
Search, Home, Live TV, TV Guide and **⋯ More**, with the logo at the top and
your avatar at the bottom. **Left** from anything reaches it; **Right** always goes back to exactly the
control you left. While something plays in the mini-player or in Audio mode, a **Now playing** item
appears under the logo.

**How it looks is yours to choose:** Settings → Layout → **Navigation** (see
[Navigation](#-navigation--the-rail-your-way)).

### 🔝 The top-right corner
- **Continue** — one press resumes your **Last channel**.
- **Playlist** — switch between your playlists or **All playlists**; each shows its short mark and
  channel count.
- **Clock, date and weather** — colours set in Settings → Appearance → **Date, time & weather**.

### ⋯ More
The last rail item: **Settings**, **Favourites**, **History**, **Backup & Restore**, **Local sync**,
**Error log** and **About**, with your profile (and **Switch**) at the top. **Up/Down** picks an item
and shows its page beside the list; **Right** enters that page; **Back** returns to the list.

---

## 🎛️ Remote shortcuts — the cheat sheet

| Key | Where | What it does |
|---|---|---|
| **Long-press OK** / **Menu** key | any channel (not on Home) | The options menu — favourite, rename, hide, move |
| **Left** | Live TV lists | Opens the categories (a sheet, or the column in **Separate** layouts) |
| **Right** / **Info** | Live TV channel list | That channel's Program details page |
| **Hold Up / Down** | Live TV lists | Runs through the list to its ends |
| **Left** | full-screen live | Channel list for the folder you came from |
| **Left ×2** | full-screen live | The categories (the same sheet as Live TV's) beside the channels of the one you are in. **OK** on a category shows its channels; **Right** goes into them, **Left** back; **OK** on a channel plays it. **Back** closes both. Looking around never changes what CH+/CH− step through — the channel you pick does |
| **Right** | full-screen live | The last 30 channels you watched, each with when you watched it |
| **CH+ / CH−** | full-screen live | Next / previous channel. Wraps around. Always works |
| **CH+ / CH−** | the channel, history or all-categories list over the player, Multiview's channel picker | Page by 10 items, like any browse list. **Long-press** jumps to first/last |
| **Last channel** key | full-screen live | Back to the channel you watched before (press again to flip back). Also the clock button on the player bar |
| **Up / Down** | full-screen live, controls hidden | Same as CH+/CH− |
| **0–9** | full-screen live | Type a channel number to tune. OK submits, Back cancels. A playlist that carries no channel numbers (no `tvg-chno`) is numbered 1, 2, 3… in its file order |
| **CH+ / CH−** | any browse list | Page by 10 items. **Long-press** jumps to first/last. In a category list it only moves the highlight — **OK** opens the category |
| **Back** | category list with a search typed | Clears the search and jumps to the search box. The search stays while you watch a channel |
| **Left** | anywhere in a screen, nothing further left | Back to the rail |
| **Back** | full-screen player | Leaves full screen (there is no exit button) |
| **OK** | full-screen player | Shows / hides the controls |

**Want different keys?** **Settings → Layout → Remote shortcuts** maps spare colour, number, channel
and media keys to 26 actions — **Previous channel** among them. D-pad, Back, OK, volume, Home and power stay protected.

**Player buttons**, on the same page, change four player keys. Every default is the behaviour above:

| Option | Choices | Default |
|---|---|---|
| **Hold OK in the player** | Off, Channel list, Guide, Programme info | Off |
| **OK when the controls are hidden** | Show controls, Channel list, Guide | Show controls |
| **Swap channel up/down** | CH+ and Up go to the previous channel, CH− and Down to the next | Off |
| **Number keys tune channels** | Off stops 0–9 from typing a channel number in the player | On |

---

## 📥 Playlists & sources

### ➕ Add a playlist
**Where:** Settings → Sources & guide → Playlists → Add Source
First choose **Remote** (below) or **Type it here**, then fill the form — every field is a row; OK
types into it, and **▶** on the side panel starts the import: **Xtream** (server + user +
password), **M3U** (URL or a local file), or **Stalker/Ministra** (portal URL + MAC).
An M3U link of the form `…/get.php?username=…&password=…` is really an Xtream account: when the
provider's server confirms it, it is added as **Xtream**.
A name another playlist already has gets a number, so two unnamed playlists are **My IPTV** and
**My IPTV 2**.
Under **User-Agent** there is an optional **Referer** — leave it empty unless your provider asks
for one; it is then sent with every stream of that playlist.

### 📱 Remote — fill the form on your phone
**Where:** Settings → Sources & guide → Playlists → Add Source → **Remote**
The same option the first-run wizard offers, and it stays available forever — you do not have to
type an Xtream password with the remote. The TV shows a QR code and a 6-digit PIN; scan it with any
phone on the same Wi-Fi, fill the form in the browser there, or upload an `.m3u` file from a
computer, and press **Send to TV**. The details land in the TV's form and you press **Start Import**.
*(Idea from community PR #66 by @zarga03.)*

**Editing a saved playlist works the same way:** Playlists → a playlist → **Edit** → **Edit from
another device**. The page shows only that playlist's details; passwords, MAC addresses and M3U
playlist addresses are never sent to it — leave those fields empty to keep them. What you send fills
the edit form on the TV, and nothing changes until you press **Save** there.

### 🧪 Test connection
**Where:** inside the Add/Edit form, and on each saved playlist row
Says whether the account is active, when it expires, whether it's a trial, and how many connections
are in use. Nothing is imported — it only asks.

### ⚡ Choose what to sync
**Where:** while adding an Xtream or Stalker playlist
Per section: **Now** (import first), **Later** (background), **Off** (never fetched). Stalker
defaults to Live **Now**.

### 🔄 Re-sync
**Where:** Settings → Sources & guide → Playlists → the playlist
**Resync now** adds and updates. **Resync and remove missing titles** also drops what the provider
has stopped listing. Neither touches your favourites, history or manual order.

### ✨ Clean channel names
**Where:** Settings → Sources & guide → Playlists → the playlist → **Names**
Turn on **Clean channel names** and that playlist's channels lose a country prefix (`UK|`, `[US]`,
`DE:`) and a quality tag at the end (`HD`, `FHD`, `4K`, `HEVC`) everywhere they appear: "UK| News
One HD" shows as "News One". **Also remove** takes your own words, separated by `;`, removed from
the start or end of every name. Saving refreshes the playlist. Guide matching still uses the
provider's names, channels you renamed in Customize keep your name, and favourites and history stay
put. Switch it off and the next refresh brings the original names back.

### 🗂️ Several playlists at once
**Where:** the playlist pill in the top-right
Pick **All playlists** or just one — it applies everywhere at once and survives a restart. A
**DEFAULT** badge in Settings marks your default. When two are active, a small provider label appears
beside categories and items so you always know which is which, and each row carries the playlist's
short mark (for example **GOLD**). With two or more playlists the pill's menu also has **Resync now**,
for the playlist chosen (or all of them).

### 📶 Prefer HLS (Xtream only)
**Where:** Add/Edit playlist
Ask the panel for HLS instead of MPEG-TS. Try it if live channels are unstable. **Test HLS support**
just above it checks whether your provider really serves it.

### 📡 Stalker portals
**Where:** Add source → Stalker (MAC)
Portal URL plus MAC. A portal that also asks for a login takes it in **Username** and **Password**,
right under the MAC; leave both empty otherwise. Stricter portals also take Serial Number, Device ID,
Device ID2 and Signature under **Advanced device identification**.
> **"Portal refused the login"?** Check the MAC, and check the TV's **date & time** — Stalker
> validates timestamps and a clock a few minutes out fails the handshake.

---

## 📺 Live TV

### ▶️ Watch a channel
**Where:** Live TV → focus a channel → **OK**
The preview pane plays it first; OK goes full screen with no reload. Beside the video: the programme,
its progress and time left, the synopsis, and what's on next and later.
**OK previews first** (Settings → Video player → Live TV, shown while Live preview is on) has a
switch for Live TV and one for the TV Guide. With Live TV's on, moving through the list no longer
plays anything: the first **OK** plays the channel in the preview, **OK** on that channel again goes
full screen, and the video stays on it (its name on the picture) while you look around the list.
If the stream stalls, the last picture stays on screen; after about 10 seconds the player says
**Stream interrupted. Reconnecting…** and the picture comes back by itself when the network does.
A live channel paused for more than a minute goes back to the live edge when you press Play;
a shorter pause carries on where it was. Switching the TV off and on also brings the channel back by
itself, at the live edge.

### 🗂️ Categories
**Where:** Live TV → **Left**
The categories slide in as a sheet over the list — Favorites, History, All, your custom
categories, then the provider's groups, each with its channel count. **Back** closes it.
**Settings → Layout → Live TV layout** → **Separate** keeps them on screen as a column instead.

### 📅 Program details
**Where:** Live TV → a channel → **Right**, the remote's **Info** key, or hold OK → **Program details**
A full page with the channel's day: every programme on one line, grouped **Earlier · On now ·
Later**, the one under the cursor opened with its times, facts, categories and whole description.
**▲▼** move through the programmes; **Right** or **OK** goes to the opened programme's buttons —
**Watch channel**, **Remind me**, whichever fit it — **Left/Right** between them, **OK** runs one. **Up**
from the first programme reaches **Yesterday · Today · Tomorrow**. **Back** closes. The next and
later lines under the preview are for reading only.

### 🗓️ List or guide
**Where:** the **Guide view** button in the Live TV tool row
Shows the guide grid right inside Live TV; **List view** goes back. **Settings → Layout → Live TV
opens in** chooses which one you start in.

### ⭐ Favourite, rename, hide
**Where:** long-press **OK** (or **Menu**) on a channel
The channel menu is grouped **Watch · Channel · Guide data · Organise** and also holds **Match EPG**,
**Move**, **Add to Multiview**, **Remove from this category** (inside a
custom category) and **Play in external player**.

### ↕️ Move
**Where:** **Move** in a channel's menu, **hold OK** on a category → **Move**, or
⋯ More → Favourites → hold OK → **Move**
The Move window opens with that item picked up. **▲ ▼** carry it a row, **CH+ / CH−** a page, and a
**long press** of them to the first or last place. **OK** puts it down or picks up another, so several
can move in one go. **Save** keeps the new order; **Cancel** or **Back** leaves it as it was.
What you moved keeps its place in **A–Z** and **Provider** alike; everything you never moved follows
the sort around it. **Restore to playlist default** on a category clears its moved items.

### 🏷️ What the row tells you
Number, logo, name, the programme on now with its time left and a progress bar, the provider's tags
(quality, country), a heart for favourites and the playlist's mark.

### 🏛️ How a channel starts
Live TV plays on one engine: **ExoPlayer**. A channel is worked through the stream formats —
HLS first, then MPEG-TS — each tried once.
**A channel that won't play is retried on both formats. Then it stops and tells you, rather than
spinning for ever. Settings → Live TV → Give up on a channel after** sets how long that may take
(30 s default); **Give up after, per playlist** gives one provider its own time.

### ⏳ "Too many connections"
The spinner stays up with a live countdown and OwnTV retries by itself. Don't press Retry — just
wait.

### 🔒 Protected (DRM) channels
Widevine and ClearKey channels play with nothing to set up — your TV does the unlocking. That
includes ClearKey channels whose playlist writes the key itself (`license_key=kid:key`); a playlist
added before this update needs one refresh to pick the key up. Some cheaper boxes only allow standard
definition; that is the device's decision, not OwnTV's. Apple FairPlay channels cannot play on any
Android device — OwnTV says so instead of a general error; those playlists are made for Apple devices.

---

## 🗓️ TV Guide

### 📖 Open the guide
**Where:** the rail → **TV Guide** (or **Guide view** inside Live TV)
The focused channel plays in the video at the top, with the programme under the cursor beside it.
Below, one control line — **Today**, **Now**, **Category**, **Order**, **Search**, **Auto-match EPG** —
then the grid, opened at now with the now-line about a third of the way across.
- **Two steps:** focus picks a channel row; **OK** or **Right** steps into its programmes; **Back**
  steps out. Across an empty stretch, Left/Right move 30 minutes at a time.
- **Hold OK** on a programme: **Program details** (the channel's day as a page, opened on this
  programme — see Live TV › Program details), **Remind me** (upcoming),
  **Watch channel** and more. The remote's **Info** key opens Program details
  straight away.
- **OK on a programme** plays its channel in the video at the top; **OK** again watches it full
  screen. With the TV Guide's switch in **OK previews first** off, OK watches at once and the video
  follows the highlighted row.
- **Order:** Provider, A–Z or Favourites first. **Category** uses Live TV's own list,
  and in Provider or A–Z the channels come in Live TV's order, moved ones in their place.
  Its **Show** group: **Channels without guide**, and **Video preview** — off, the top shows the
  channel's logo instead of playing it (also off whenever Live TV's preview is off).
- **The preview stays put while you browse:** moving through the rows does not change it. The first
  **OK** on a row brings that channel into the preview; **OK** again on it watches it full screen.

### 🔔 Programme reminders
**Where:** hold OK on an upcoming programme → **Remind me** · Settings → Sources & guide →
**Program reminders** / **Reminder time**
When it is due (5 minutes before by default) OwnTV **asks to switch** — over any screen, the player
included — or **switches** by itself, or only **notifies**.

### ➕ Add guide data
**Where:** Settings → Sources & guide → EPG Sources
The guide is opt-in. After importing a playlist you are offered a one-tap sync. Later, add XMLTV
feeds here — **Fill from playlist** takes the URL your playlist already carries — and set each feed's
User-Agent and refresh interval. The refresh interval can also be **every N days**, the same choice a
playlist offers.

When two feeds list the same channel, the one higher on the list is used. Open a feed and choose
**Move up** or **Move down** to set the order; a lower feed still fills any hours the higher one
leaves empty.

If a feed syncs but none of its channels match your playlist's, the feed says so; open the TV
Guide, which then offers **Auto-match EPG** to pair them by name.

> **Playlist has no channel logos?** Turn on **Use this guide's channel logos** on the feed. Logos
> then come from the XMLTV guide instead; channels the feed has no logo for keep the playlist's one.

### 🎯 Match channels to the guide
**Where:** Guide → **Auto-match EPG**, or long-press a channel → **Match EPG**
Auto-match does the bulk — only for the playlist(s) the guide is showing, so it takes seconds; the manual picker lists the most similar guide channels first. Tick
**Logos** in the review window and every matched channel also takes the logo of the guide channel it
is matched to. Auto-match keeps going if you leave the Guide: a message says how it went when it
finishes, and the review window is waiting when you come back.

### 📅 Guide days to keep
**Where:** Settings → Sources & guide → **Guide days to keep**
How many days of upcoming guide the app stores, 1–14, seven by default. The same number decides how
much is downloaded, how much is kept, and how far the grid scrolls — so raising it gives you a longer
guide, and lowering it frees space.

### 🕰️ Guide time offset
**Where:** Settings → Sources & guide → **EPG time offset** (global) · long-press a channel (just that one)
For a provider publishing its guide in another time zone. A re-sync never undoes it.

### 📋 Other guide touches
- **Icons on a programme** — a reminder is set.
- **Long-press a channel label** for the channel's own menu (favourite, Match EPG…).
- **Settings → Layout → Guide column widths** — channel list vs timeline, must total 100%.

---

## 🔲 Multiview — up to four channels

**Turn it on first:** Settings → Watching & recording → **Multiview**. It is off by default, and
the button does not appear in the player until it is on. **Max tiles** on the same screen sets the
ceiling — the grid still opens with two and grows only when you ask.

**Where:** the **Multiview** button in the player, or long-press channels → **Add to Multiview**
The channel on screen becomes the first tile. **One tile carries the sound**, marked with a speaker;
tap another to move it. **Sound only** gives up a tile's picture and keeps its commentary.
**Back stops everything** — nothing is left playing behind it.

A tile that cannot start says why rather than sitting blank.

### 🔌 How many channels your provider allows
**Where:** Settings → Sources & guide → Playlists → **Info**
OwnTV measures this once, at a playlist's first sync, and warns before refusing a stream. **Re-test**
is inside Info — it stops anything playing and takes a few minutes; what it measures is kept, even when
the provider's own number says less. **Ignore connection limit**, beside Re-test, lets Multiview
try more streams than the playlist reports — for a provider that allows more than it says.
A stream the provider refuses still says so.

---

## 🏆 Sports

Live scores, fixtures and match details come from ESPN. OwnTV then looks in **your own TV guide** for
the channels that show a match — nothing is streamed from ESPN.

### 🏁 First time
**Where:** rail → **Sports**
Nothing is followed at first. Open **Settings → App → Sports → Sports and leagues**, choose a sport on the
left and switch on its leagues on the right. The more you choose, the longer loading takes.
**Sport buttons → All sports** fetches every league instead.

### 🗂️ The Sports page
Three tabs sit after the title: **Scores | Calendar | Leagues**. Sports always opens on **Scores**;
**▲** from the sport buttons reaches the tabs, **OK** opens one, **Back** on Calendar or Leagues
returns to Scores.
Two rows of buttons filter every tab: sports (and ★ My teams) on top, that sport's leagues below.
**OK** switches a button on or off, so you can pick Bundesliga and Premier League together; none
chosen shows everything. The choice stays across the tabs until you close the app. **⟳** at the end
of the top row rebuilds the sports data now; the bottom status pill says **Rebuilding sports…** while
it runs.
**Scores** rows: **Live now** (red LIVE mark), **Sport on your channels now** (from your guide) and
**Starting soon**. **OK** on a match opens its page; **hold OK** offers Follow, Remind me (once Find
channels has found a channel), each side's team page and the match page.

### 📅 Calendar
The month on the left — each day with how many matches each sport has (one league chosen: the
pairings), ★ for a day one of My teams plays, a red dot while something is live — and the chosen day's
matches on the right, by league. **◀ ▶** move a day, **▲ ▼** a week; past the last week the month
turns. **OK** or **▶** from the last column goes into the day's list, **◀** comes back. **‹ ›** and
**Today** at the top change month; months go back to the season's start. **OK** on a league heading
opens that league in Leagues. Finished matches show the result; with Spoiler-free on, **OK** shows it.
A month is downloaded when you open it and saved on the TV; without internet the saved copy shows.

### 🏆 Leagues
One card per followed league: who leads and what is next. **OK** opens the league in the tab,
**hold OK** follows or unfollows it. A league shows **Table | Fixtures | Results** (Formula 1:
**Drivers | Constructors | Races** with the next race weekend; other racing: Drivers | Races; ATP and
WTA: **Rankings | Matches**). **OK** on a table row opens that team's page. A match page's **Table**
tab and its league name at the top open the league here.

### 👕 Team pages
**Fixtures | Results** for the whole season, cups included, with the next match, the last five
results and the team's place in the table. Open one from a match page's team (▲ from the tabs, then
**OK**), a table row, **hold OK** on a match, or **Settings → App → Sports → My teams** (hold OK there
stops following). Racing drivers, tennis players, fighters and cricket teams have none.

### 📋 A match page
The box at the top shows the score, the time, the round and the venue with its city and country.
Tabs show the timeline, statistics, line-ups, the table, each side's form and **Broadcasts**.
**Find channels** searches your guide for that match — from **30 minutes before kick-off until 30
minutes after the end**; earlier, the page says when it opens. The found channels appear on the
right, best first, with a preview. **OK** plays one full screen; **Back** returns to the match page.
Below the guide's own hits come the channels that **usually show** the competition: their name carries
it by any name it goes by ("Sky Sport F1", "EPL 3", "DAZN LaLiga") — never a different competition
with a similar name (Formula E, Indian Premier League).
**Filter channels** narrows the list by language, country, picture and playlist; **Show "likely"
channels** turns those usual channels on or off. **Strict filtering** on hides the others; off lists
them below. **Remember** keeps the filter for every match.

### 📺 Broadcasts
**In your guide** lists the channels Find channels found, one per row under their country, in order;
**▲ ▼** scroll a long list. **From ESPN** is ESPN's own list, which covers the United States only.
A channel's country comes from its name: "UK| …" and "UK: …" both count.

### ↕️ Your own order
**Where:** Settings → App → Sports → Sports and leagues → **hold OK** on a sport, or on a league you
switched on. In the Move window, **▲ ▼** choose a row, **OK** picks it up and puts it down, and
**Save** keeps the new order. The Sports page and the Home row follow it.

### ⚙️ Settings → App → Sports
**Auto rebuild** every 1, 2, 4 or 8 hours, on every app start, or by hand. **Calendar refresh**
(every 1–30 days, 7 at first) is how often a saved current or coming month is downloaded again;
past months never are. **Live refresh** updates live scores every 30–120 seconds while something
sports is on screen. **Refresh sports data now** shows when it last updated, and also marks the saved
months out of date.
Also here: My teams, goal alerts, playlist and channel filters.

### 🏠 Sports on Home
**Settings → Layout → Home screen** can show a **Sports** row: live and upcoming matches of your leagues and teams,
in your order. **OK** opens the match page.

## 🏠 Home

### 🧩 Rearrange Home
**Where:** Settings → Layout → **Home screen** (per profile)
Show or hide each row, **hold OK** to move a row with Up/Down, and
switch channel rows between **Cards** and **On now** (an inline mini-guide).

### 🧭 Navigation — the rail your way
**Where:** Settings → Layout → **Navigation**
- **Floating** — rests as a small capsule over the content, opens when you move Left, and hides
  itself after **Hide after** (2, 4 or 8 s); Left at the edge calls it back. **Docked** — always on
  screen; the content makes room (recommended).
- **Menu labels** — the one switch for names beside the icons: **Off**, **When focused** (the names
  slide out over the content while the rail has focus) or **Always** (Docked only; the content makes
  room). It sets Size and Widen on focus for you, and shows what they already say. Floating offers
  Off and When focused, because a floating rail hides while you browse.
- **Size** — **Compact** (icons), **Normal** (names), **Wide** (names and counts, profile line),
  **Extra wide** (also a detail line: last channel and more…).
- **Length** — **Fit to items**, or **Full height** with the profile at the bottom.
- **Widen on focus** — off by default; Docked + Compact only: the names slide out over the content while the rail has
  focus.
- **Menu items** — **Dynamic** follows what the playlist has; **Static** lists Home, Live TV and TV
  Guide so you tick each one. Search and More always stay.

---

## 🔎 Search · 🕐 History

### 🔎 Search
**Where:** the rail → Search
Two filter buttons — **Live TV / TV Shows**, each with its count — narrow the results: **OK** turns one on, **OK** again turns it off,
several can be on, and none on shows everything. Every match is listed, up to 500 of each kind. Words match from their start, and a dash or other mark
counts as a gap. A channel shows Live TV's preview beside the
results.
**OK takes you to the item** — the channel in its own category — where play and every option live; **Back** returns to Search with your query kept.
**TV Shows** lists programmes from the stored guide whose title matches, on now or within the next 12
hours, with the channel and time; **OK** goes to that channel. With
the box empty: **Jump to** and your recent searches.
**Voice search:** the 🎤 button right of the box listens through your TV's speech recogniser and
searches what you say. It only appears where the device has one (most Google TV and Android TV
devices do; many Fire TV and generic boxes don't).

### ⭐ Favourites
**Where:** ⋯ More → Favourites
Filters, channels as On-now cards. Hold OK → **Remove** or **Move**.

### 🕐 History
**Where:** ⋯ More → History
Filters, rows with picture, time and progress; **Watch** or **Remove** per item, or **Clear watch
history**.

---

## 👥 Profiles

**Where:** Settings → **Profile**
Each profile has its own favourites, history and layout. Add a **PIN lock**, or turn on
**Kids mode** to hide adult content everywhere.

### 🙂 Change your avatar
**Where:** ⋯ More → OK on your avatar · or hold OK on the avatar at the bottom of the rail
Pick a drawn avatar, or **Your own picture** — a file on this TV or a photo sent from your phone.
A new profile can have its picture straight away: **Your own picture** is also in the window that
creates a profile, and in Settings → **Profile** when you edit one.

### 🔐 Settings lock
**Where:** Settings → App → **Settings lock**
A PIN of its own, per profile, that **Settings**, **Backup & Restore** and **Local sync** ask for
before they open. It is neither the profile PIN nor the category lock PIN — three locks, three PINs,
and none of them opens another; 5 wrong tries on one make only that one wait 30 s. **Change PIN** and
**Turn off** ask for the current Settings PIN.
- **Also lock long-press menu changes** (shown while the lock is on, off by default): **Hide**,
  **Move**, **Rename**, locking a category and **Remove from History** ask for the Settings PIN too.
  One right PIN opens them until OwnTV goes to the background or you switch profile. Favourites and
  playing never ask.

### 🛡️ Protected profiles
A profile with a profile PIN or a Settings lock is **protected**, and so are its playlists:
- A **new profile** gets only the playlists no protected profile uses — none at all if every playlist
  belongs to one; then it adds its own.
- From another profile, ticking it for a **backup**, merging into or replacing it in a **restore**,
  and **editing or deleting** it in Settings → Profile ask one of its PINs (profile PIN or Settings PIN).
  Local sync never touches it unless it is the profile in use.

### 🚀 Where a profile opens
**Where:** Settings → App → **App startup**
**Home**, **Last channel** (full screen), **Last channel · TV Guide** or **Last channel · Live TV**
(that screen opens on the channel's category with the channel focused and in the preview),
**Live Favorites**, or one chosen channel picked from a searchable list.

**Open OwnTV with the TV:** Settings → App → **Auto start on boot** · **Auto start on wake-up**, both
off by default. *On boot* opens OwnTV once the TV has started up; *on wake-up* opens it each time the
TV comes out of sleep, which is what most TVs do when you press the power button. A cold start lands
on the App startup choice above; an OwnTV that was still running comes back where you left it, but a
profile with a PIN asks for it again.
- Android 10 and newer let an app open by itself only when it may **display over other apps**.
  Turning an option on opens that permission's screen (or the list of apps, where the TV has no page
  for one app); until it is allowed the row says so. If your TV has no such screen, the option cannot
  work there. Some TVs also have an auto-start list of their own that has to allow OwnTV for *on boot*.
- With **App startup** on a channel, *on wake-up* starts that stream every time — also when you woke
  the TV for another app.
- *On wake-up* keeps a small background listener, with a silent notification, while it is on. It is
  offered on TVs only. If Android ends the listener, it comes back with the next boot, app start or
  visit to Settings.

---

## 🎛️ The player

| Control | Where |
|---|---|
| Clock | top centre, always |
| **Sources** | control bar, live channels — the same channel in another quality, category or playlist; pick one to switch. Locked and hidden channels are left out |
| **Aspect** | control bar — a press steps to the next mode (Fit, Fill, Stretch, Original, 16:9, 4:3) and names it; **hold OK** for the list |
| ⓘ **Stream info** | right-most button — codec, resolution, HDR, bitrate, decoder, audio, buffer |
| **Multiview** | control bar, once enabled |
| **Quality** | control bar, beside Aspect — only when the stream offers several picture sizes; Auto or one size for what is playing now |
| 🌙 **Sleep timer** | control bar, beside Stream info — 15–90 min; green while running. **Also turn off the screen** puts the TV to standby when it ends — Android asks for permission once |
| Exit | **Back** — there is no exit button |

- **Remote transport keys** work — play/pause, from the remote, a headset or a voice
  assistant.
- **A notification won't pause your channel** — the sound dips and comes back.
- **Mini-player** — subtitles show in it too. Get back to it from any screen via **Now Playing**. Browsing Live TV or the Guide behind it does not change it: expanded, the player, its guide card and CH± are still about the channel playing.
- **Picture-in-picture** — with Settings → Watching & recording → **Picture-in-picture when leaving
  the app** on (off by default), pressing **Home** while a video plays full screen or in the mini-player
  keeps it playing in a small window. Open OwnTV again for full screen; close the window to stop. Not
  in Multiview, not when OwnTV itself opens another app (an external player),
  and only on TVs that offer picture-in-picture.

### 🎧 Audio Mode — listen with the screen free
**Where:** the **headphones** button on the player controls
Drops the picture, keeps the sound, and leaves a small pill in the top-right corner. Focus it to see
the title and time, press **OK** to use its buttons; **Fullscreen** returns, **✕** stops, **Back**
leaves it. *(An item that has no picture at all — a radio channel — says so
by itself.)*

---

## 💬 Subtitles & audio

### ⏱️ Fix subtitle timing
**Where:** player → Subtitles → the **Subtitle timing** row
◀ ▶ move the subtitles 0.1 s earlier or later; OK sets them back to zero.

### 🗣️ Preferred languages
**Where:** Settings → Sound & subtitles → **Preferred audio / subtitle language**
Picks the right track automatically when a stream carries several. Each **profile** has its own, and
the list has 50 languages. **Original language** (audio) plays a channel in the language the
stream was made in, when it has that track; otherwise the stream's main track. A track or subtitle
you pick yourself is **remembered** for that channel (subtitles off too), per profile.

### 🎚️ A/V sync
**Where:** player → Audio → the **A/V sync** row (◀ ▶ in 25 ms steps) · Settings → Sound &
subtitles → **Audio sync** for everything
Nudge until lips match, then **Remember this delay** to keep it for that channel. Works on any
audio path, including sound sent as-is to a soundbar.
**Delay for this output** (Settings → Sound & subtitles) adds a delay of its own for the output the
sound is going to right now — HDMI, a soundbar on HDMI ARC, Bluetooth, the TV speakers or headphones.
Each output keeps its own; set it while listening on that output.

### 🔊 Surround sound
**Where:** Settings → Sound & subtitles → **Surround sound**
**Auto** (recommended — tries surround, falls back to stereo if your TV can't) · **Stereo only** (the
right answer for TV speakers) · **Surround** (send Dolby/DTS to a real receiver).

### 🌙 Night mode, Volume leveling and passthrough
**Where:** Settings → Sound & subtitles
**Night mode** turns loud scenes down and quiet dialogue up; **Volume leveling** brings every channel
to the same loudness. Both are off by default and work on the player.
**Dolby and DTS passthrough** (on) sends Dolby/DTS undecoded to the TV or
receiver on ExoPlayer; off, or while Night mode or leveling is on, OwnTV decodes it itself.

### 📺 Picture quality and tunneled playback
**Where:** Settings → Picture
**Maximum video quality** caps the picture when a stream offers several sizes; the player's
**Quality** button picks one for what is playing now. **Software decoding above 1080p** (off) lets
4K play when hardware decoding is off or fails — only for a TV with a fast processor; most stutter. **Tunneled playback** (experimental, off) only
appears on a TV that supports it, and switches itself off for good if a channel fails with it.

---

## 🎨 Make it yours

### 🎨 Theme & accent
**Where:** Settings → Appearance
**System** (follows the TV's own dark/light setting), **Dark** or **Light** (◀ ▶ on the row) — Light turns
the pages, panels and the player's controls and lists paper-white with dark text and an accent ring on the
focused item; the video and posters stay as they are — plus an **Accent color**: presets, a hex code, or a
colour square and hue bar (OK enters each, OK or Back leaves); the screen re-tints live and **Cancel**
restores. The accent reaches the player too — seek bar, active buttons, badges. A live preview of Home
beside the rows shows each change before you leave it.

### 🃏 App icon
**Where:** Settings → App → **App icon**
Eight colours for the OwnTV icon and logo — Petrol, Sunflower, Cobalt, Tomato, Station Board,
**Eggshell** (default), Olive and Olive on Cream — or **Pixel**, a dotted TV set. It changes the app row
banner, the launch screen and every OwnTV logo in the app. **Accent-colored logo** (on) tints the
logo's play triangle with your accent. **Restart now** switches at once: OwnTV closes and opens again by itself, on
Home, with the new colour. **Later** switches when you next leave the app. Some launchers take a moment to show the new banner.

### 🔦 Focus highlight
**Where:** Settings → Appearance → **Focus highlight**
The colour and thickness of the ring around whatever is selected. Applies everywhere.

### 🪟 Glass & background
**Where:** Settings → Appearance → **Glass & background**
The background is **Stage** (soft colour light), **Picture** (your own image, from this TV or sent from
a phone) or **Plain**. A picture comes in three looks — **Sharp**, **Soft** and **Dark** — with its own
darkening and blur, and **Accent light** adds a glow of your accent in the corner. **Glass** frosts
the rail, sheets, menus and popups; **Glass opacity** sets how much background shows through. A live
Live TV preview sits beside the rows; **Reset** goes back to Stage colours with glass on.

### ✨ Ambient Glow
**Where:** Settings → Appearance → **Ambient glow** — only shown with Theme **Dark** and Glass off
A soft glow around the edges of the screen, with an optional slow pulse.

### 🔤 Font & size
**Where:** Settings → Appearance → **Font customization** · **Popup size** · **UI zoom**
One font family for the whole app and popups (Plus Jakarta Sans by default), main text 60–140% and
popup text separately. **Popup size** and **UI zoom** step with ◀ ▶ right on their rows.
> Below **85% zoom** OwnTV warns first — very small sizes draw many more items and can exhaust a
> low-memory TV.

### 🕒 Date, time & weather
**Where:** Settings → Appearance → **Date, time & weather**
The weather beside the clock, a location set by hand (for example behind a VPN), °C / °F, and your own
colour for the time, the date and the weather.

### 📺 Live TV layout
**Where:** Settings → Layout → **Live TV layout**
**Stage** — the categories come in as a sheet when you press Left. **Separate** — they stay on
screen as a column.

### 📐 Panel widths
**Where:** Settings → Layout → **Panel widths**
The widths of each screen, named by the layout it uses. In **Separate** layouts: category column,
list and preview/details, totalling 100% — the third can be **0%** to hide it. With Live TV on
**Stage**: list and preview, plus the categories sheet's own width.

### 🗂️ Categories & items
**Where:** long-press a category → **Customize**, or Settings → Content & metadata → **Customize categories & items**
Hide, rename, reorder, and **unhide**. Highlights:
- **Bulk rename** — add or strip a prefix/suffix across a whole category, with **Auto cleanup** for
  country, quality and codec tags, a review step, and **Restore original names** as the undo. For the
  **category names themselves**: **⋯ → Bulk rename** or **⋯ → ✨ Auto cleanup** on the categories page.
- **Custom combined categories** — **＋ New category**, then **Move to category…** from any item.
  **Remove from this category** (long-press a channel inside it, or here) takes it out of that category
  only. Hiding a channel's own provider category no longer hides it from your custom categories.
- **Restore to playlist default** — long-press a category → **Restore to playlist default**
  brings back its hidden items, returns items you moved out to another category, and clears your
  custom order. It lists what it will change first, or says the category is already at the default.
  In Settings → **Customize categories & items**, **⋯ More → Restore playlist defaults** does the same
  for every category of the playlist(s) shown (the playlist picker at the top right), in the section you
  are on (Live TV): hidden categories and items, renames, category order and custom
  order. Other playlists, combined categories, favorites and guide matches are kept.
- **Span select** — long-press a **Hide** or **move** button to act on a whole block at once.
- **Category lock PIN** — **⋯ More → Set category lock PIN** (then **Change** / **Remove category lock
  PIN**, which ask for the current one first). It is the PIN locked categories ask for, and nothing
  else: it does not open Settings.
- **Lock a category with a PIN** — long-press a playlist category in Live TV →
  **Lock with PIN**. It uses the category lock PIN (the first lock asks you to set one). The category stays
  in the list with a lock; its channels stay out of everything else (search, Guide,
  Home rows, CH+/−, counts, history) until you open it with **Enter PIN**. It stays open
  until OwnTV goes to the background or you switch profile. **Remove lock** in the same menu asks the
  PIN, and removing the category lock PIN unlocks every category. 5 wrong PINs → a 30 s wait.

### 🔀 Reorder or hide the long-press menus
**Where:** Settings → Layout → **Long-press menus**
Each menu starts in the order it shows today. **OK** on an action shows or hides it (hidden ones stay
in the list, dimmed); **hold OK** picks it up to move it. **Reset** brings back every menu as shipped.
Some actions only appear where they apply — Remove from history in History.

### 📋 Browsing & lists
**Where:** Settings → Layout → **Browsing & lists**
Whether each section remembers the last category and item — each profile its own, so switching
profile lands everyone where they left off, no restart. Only a category you pick is remembered; a
startup choice or jumping to a channel from Search does not replace it. **TV Guide follows Live TV**
(off by default): the TV Guide opens on Live TV's remembered category, and a category picked in the
Guide becomes Live TV's too. **Live TV categories:** switch off
**All channels** to drop it from Live TV, the guide, the player and Multiview; without All
channels, Live TV opens on your first category (or Favorites).

### 🎞️ Animations
**Where:** Settings → Appearance → **Animations** (a switch)
Turn interface motion off for a snappier feel on a slower box. With it off, the launch screen shows
a still logo too.

---

## ⚙️ Settings worth knowing

Settings lives under **More → Settings**: twelve group cards (Quick, Profile, Sources & guide,
Appearance, Layout, Content & metadata, Player, Picture, Sound & subtitles, Live TV, Watching &
recording, App). A group opens as one page — its settings on the left, and on the right an
explanation of the setting you are on, with its choices and the **RECOMMENDED** one marked. Switches
and short choices change right in the row with **OK** or **◀ ▶**; a list of choices or a number opens
in that right-hand panel instead of a popup; a **›** opens a page of its own. **Quick** is yours to
build: **hold OK on any setting** and choose **Pin to Quick** (hold OK there to **Unpin**). **Search
all settings** lists every setting whose name contains what you type, with its path and value.

| Setting | Where | Why |
|---|---|---|
| **Multiview** | Watching & recording | Watch up to four channels at once. Off by default; also sets the tile ceiling |
| **Picture-in-picture when leaving the app** | Watching & recording | Home keeps the video in a small window. Off by default; hidden on TVs without picture-in-picture |
| **Live latency** | Live TV | How close to the live edge to play. Lower = less delay, more stutter risk |
| **Pre-buffer live streams** | Live TV | Collect a few seconds first on a flaky provider |
| **Give up on a channel after** | Live TV | 30 s default — bounds how long a dead channel can spin (+ per playlist) |
| **Hardware decoder** | Picture | On for smooth 4K; off only to diagnose |
| **Prefer SDR** | Picture | When a stream offers both SDR and HDR, play the SDR one. Off by default |
| **Auto frame rate for Live TV** | Picture | Match the TV's refresh rate to the live channel. Off by default |
| **Network timeout** · **Reconnect attempts** | Player | Streams on a bad line. Auto / 1 = as before |
| **Default volume** | Sound & subtitles | 0–150%, where everything starts |
| **Reset saved player choices / zoom / volume** | Player, Picture, Sound & subtitles | Forget everything the player remembered per channel |
| **Delay for this output** | Sound & subtitles | A delay for HDMI, ARC, Bluetooth, speakers or headphones, each kept on its own |
| **Custom DNS** | App → DNS | System, Google, Cloudflare, Quad9, or DNS-over-HTTPS |
| **External player** | Player | Live TV streams open in VLC, MX Player and so on |
| **Programme reminders** · **Reminder time** | Sources & guide | Ask to switch, switch, or notify; how early |
| **Measured stream stats** | App | Live fps, bitrate and dropped frames in Stream info |
| **Check updates on startup** | App | Be told when a newer version is on GitHub |
| **Auto start on boot** · **Auto start on wake-up** | App | Open OwnTV when the TV starts up or wakes from sleep. Off by default |
| **Error log** | ⋯ More → Error log | The last crash and recent playback failures — exportable |
| **Detailed playback logging** | App | Turn on before reproducing a playback bug |

### 💾 Backup & Restore
**Where:** ⋯ More → **Backup & Restore**
One `.own` file with your profiles, sources, every setting and your background image —
**optionally encrypted with your own password**. Restore locally or over Wi-Fi.
> Android's own automatic backup is deliberately disabled, so app data only ever leaves the device
> through this screen. Passwords ride along **only** if you set a backup password.

**Same name on both sides:** restoring a profile whose name is already here asks, per profile:
**Merge into it** (keep what it has, add the backup's), **Replace it** (clear its data here for the
parts being restored, then restore) or **Keep both** (the backup's comes in as "<name> (restored)").
Merging into or replacing a locked profile other than the one in use asks its PIN; everything else
needs only the backup password. A locked profile stays locked after a restore.

### 🔄 Local sync with your phone
**Where:** ⋯ More → **Local sync**
Swap favourites and history with the OwnTV mobile app over your own Wi-Fi — no
account, no cloud. **Only the profile in use syncs**, into the profile in use on the other device,
whatever their names — like one viewer on two screens. **Both devices must open the Local sync screen**; you see exactly what will change
before it changes. On a brand-new television this is offered during setup instead — see
**From another device** under *Start here*.

**Auto-sync** — on a paired device's row, **Auto-sync** keeps the two in step by themselves. Turn it
on with Local sync open on both: the other device asks "Allow auto-sync?" (**Don't allow** is focused).
- It pairs the profile in use here with the one in use on the other device when it is turned on
  (the row shows "Profiles: B ↔ X"); switching profile later does not change the pair.
- Each device ticks what it lets through — Favorites, Watch history, Customizations
  and Manual reorder by default; **Sources** (the playlists, with their logins) and **App settings** only
  where they are ticked. A merge both ways, and deletions stay deleted.
- **How often** is the least time between two syncs: each time either app opens or closes, 6 hours,
  daily or weekly. A sync is tried when OwnTV opens or closes and when the Wi-Fi comes back.
- **Keep auto-sync listening** (on this page, off by default): on, the phone can sync with the TV
  whenever OwnTV is open on the TV; off, only while Sync mode is on.
- The row says the last auto-sync, "Couldn't reach …, will try again" (it backs off), or "Auto-sync
  turned off on …" when the other device turned it off. Turning it off here tells the other device too.

---

## 🛠️ Build your own M3U playlist

OwnTV reads each entry's tags and puts every stream it lists into Live TV:

### One line, explained

```m3u
#EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://…/bbc1.png" group-title="UK" ,BBC One
http://your-provider/live/user/pass/1234.ts
```

- **`group-title`** — the category the channel lands in.
- **`tvg-logo`** — the logo (optional).
- **`tvg-id`** — the EPG id used to match guide data.
- **after the comma** — the title shown in the app.

---

## 🩺 Troubleshooting

| Problem | Try this |
|---|---|
| A channel stutters or shows artifacts on 4K | Check **Hardware decoder** is on, and try **Prefer HLS** for that playlist |
| One provider glitches, another is fine | Turn on **Prefer HLS** for that playlist |
| Channel won't open at all | It already tried HLS and MPEG-TS. Check **Error log** (⋯ More) |
| "Too many connections" | Wait — OwnTV counts down and retries by itself. Don't press Retry |
| Sound but a black picture | OwnTV retries the channel on the other stream format; if it still fails, it says so |
| Stalker portal refuses the login | Re-check the MAC, and the TV's **date & time** |
| Guide is blank | **Settings → Sources & guide → EPG Sources** — add a feed and sync it, then run **Auto-match EPG** |
| Audio out of sync | Player → Audio → **A/V sync**, then **Remember this delay** |
| Update says there isn't enough space | In-app updates need room for the whole APK — free some and retry |

**Still stuck?** **⋯ More → Error log** holds the last crash and recent playback failures, and
exports to `Download/owntv-playback-report.txt`. Bring it to
[t.me/owntvplayer](https://t.me/owntvplayer).

---

## 💡 Tips

- **Long-press OK** is the answer to "where is that option?" nine times out of ten.
- **Left, then Left again** in full screen gets you from channel list to every category without
  stopping the stream.
- Set a **default playlist** if you mostly use one — everything narrows to it at once.
- Turn on **Run in background** during a big import and just start watching.
