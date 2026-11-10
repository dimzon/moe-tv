# OwnTV → live-only: редизайн на одном RuTube

Статус: **план принят, выполнение не начато.** Фиксирует дизайн-обсуждение 09–10 ноября 2026.
Ветка-носитель: `remove-mpv` (worktree `C:\Work\OWNTV-remove-mpv`, от `main`).

---

## 0. Отправная точка

**Уже сделано (закоммичено, `ac5cc39`):**
- mpv удалён полностью: AAR `tv.own.owntv:libmpv`, `MPVLib`, mpv-движок `OwnTVPlayer` (5800 стр.), `ExoSubtitleEngine`, FFmpeg-аудио декодер (`libowntvffmpeg` жил в том же AAR — ушёл по решению), репозиторий OwnTV, proguard-keep'и.
- Удалены VOD-функции: вкладки Фильмы/Сериалы, Загрузки, Записи (DVR), catch-up и timeshift-rewind; движок один — `LivePreviewEngine` (ExoPlayer/Media3).
- Схема сдулась: v56 (пины движка `enginePin`/`vodEnginePreference`) — в коммите; **v57 (удаление VOD-таблиц) — в рабочем дереве, ~480 файлов не закоммичены**.
- Строки 26 локалей вычищены, i18n-манифесты перегенерированы, docs переписаны, тесты адаптированы (1796 unit зелёные), смоук на redroid пройден: 205 каналов RuTube, EPG, «Первый канал» играет на ExoPlayer, dropped=0.

**Живёт и удаляется этим планом:** профили (ProfileGate/PIN/детский), источники M3U/Xtream/Stalker + wizard, файловый бэкап `.own`, Wi-Fi-синхронизация в текущем (профильно-источниковом) объёме, `BundledSQLiteDriver` + 57 версий миграций.

**Первое действие плана:** закоммитить висящую в рабочем дереве волну 2 (v57 + тесты + docs + строки) — дальше резать только от чистого состояния.

---

## 1. Продуктовые решения (итог обсуждения)

| # | Область | Решение |
|---|---|---|
| 1 | Источник | **RuTube единственный.** `sources`/`profile_source`/кридшеллы/`BuiltInSource`-обвязка, sync-ветки M3U/Xtream/Stalker, XMLTV-загрузчик и матчинг-слой `epg_channels` — удалить. Каталог один на всех, ~250 каналов |
| 2 | Профили | Удалить полностью: таблица, PIN, детский профиль, аватары, `profileId` из каждой таблицы и каждой DAO-сигнатуры, ProfileGate/ProfileLaunchGate на старте |
| 3 | Файловый бэкап | Удалить: `core/backup` (кроме `DeviceIdentity` → переедет в sync), BackupScreen/RemoteBackupRestoreScreen, `.own`-контейнер, 26 версий формата. Новая коробка настраивается pairing + sync, не файлом |
| 4 | Каталог | Не сидировать в APK. Фоновое обновление при каждом запуске (13 запросов, секунды). Первый запуск: «загружаем каналы» → список через ~5 c |
| 5 | EPG | Окно 2 суток. **Мгновенный гид**: сид из самого каталога (`tvprogramm=true` даёт now/next для всех каналов бесплатно). Полная сетка по каналам-дням докачивается фоном, возобновляемо через `epg_state(hexId, fetchedUntilDay)` |
| 6 | Поток | URL не хранится: `resolveStream(hexId)` → `play/options` (короткоживущий, резолв на каждый tune/reconnect — текущий `StreamUrlResolver`-путь уже так делает, маркер `rutube://` сменится на чистый hexId) |
| 7 | Категории | Честные **M:N** из `broadcast_category[]`; убрать схлопывание в «основную категорию по приоритету» |
| 8 | Порядок каналов | Глобальная ручная сортировка («как на обычном телевизоре»), per-item LWW = домашний пресет + локальные переопределения |
| 9 | Свои категории | Удалить (`custom_category_members`) — возразил никто |
| 10 | Избранное / история | Остаются. «Продолжить» = **последний канал этого ТВ** (позиции у live нет, catch-up у RuTube нет); рельс «недавно в доме» = история по всем устройствам. Твой сценарий «гостиная→кухня» — acceptance-кейс |
| 11 | Напоминания | Остаются как есть (код есть — не инвестировать), синхронизируются по галочкам; срабатывание локальное |
| 12 | Android TV home screen | Остаётся, глобализуется; `lastPositionMs`/`durationMs` — мёртвые колонки для live, вычистить |
| 13 | Sync | Матрица **4 категории × отдавать/читать** (см. §3); настройки галочек локальны и между ТВ не синхронизируются |
| 14 | Движок БД | Системный SQLite: `AndroidSQLiteDriver`, схема **v1 без миграций** (0 инсталляций — история 1→57 удаляется), `androidx.sqlite:sqlite-bundled` долой → **−2.6 МБ** нативного веса. Оставшиеся `.so` (graphics.path, datastore counter, по ~10 КБ) — обязательны Compose/DataStore, не трогаем |
| 15 | Онбординг | Сжимается до минимального первого запуска: язык (авто с системного) + размер экрана. Источников/профилей не выбираем — приложение открывается сразу в Live |

---

## 2. Целевая схема данных (Room, `version = 1`)

Одна идентичность на всё: **`hexId` (RuTube videoId, TEXT)** — ключ канала, EPG, напоминаний, prefs. Стабилен вечно.

```
channels           (hexId TEXT PK, name, logoUrl, orderNumber)
categories         (id INTEGER PK, name, alias)
channel_category   (hexId, categoryId)  PK(hexId,categoryId), FK→channels CASCADE, index(categoryId)
channel_order      (hexId PK, position, updatedAt)          -- NULL позиции нет = порядок провайдера
programmes         (hexId, startMs, stopMs, title, description?)  PK(hexId,startMs), index(startMs)
epg_state          (hexId PK, fetchedUntilDay)              -- курсор возобновляемого докача
favorites          (hexId PK, addedAt, updatedAt)
watch_history      (deviceId, hexId, watchedAt)  PK(deviceId,hexId)
playback_prefs     (hexId PK, zoom, volume, delay, audioLang, subLang)   -- локальные, НЕ sync
programme_reminders(uuid TEXT PK, hexId, title, startMs, stopMs, leadMin, updatedAt)
tv_provider_programs (…глобальный, без profileId, без lastPositionMs)
tombstones         (kind, identity, deletedAt)  PK(kind,identity)  -- kind ∈ {favorite, order, reminder}
```

12 таблиц. Нет: `sourceId`, `profileId`, `contentHash`, `contentKey` (= hexId), `epg_channels`, `custom_category_members`, `content_order` в старом виде, `playback_quirks` (quirk-механика была про разнородные IPTV-потоки; RuTube homogeneous — проверить при фазе ④, при отсутствии потребителей удалить).

Объём: ~250 каналов, ~24 тыс. строк программ (2 суток) — всё перетирается при синке дешевой полной заменой; `SyncSupport`-машинery (chunked upsert, catalog-shrink guard «refusing to wipe») оставить в урезанном виде.

---

## 3. Синхронизация между телевизорами (дом: 4 ТВ)

**Матрица (галочки на каждом устройстве, локальный DataStore, не sync):**

```
                      отдавать    читать     дефолт
  Закладки              [✓]        [✓]       оба on
  Порядок каналов       [✓]        [✓]       оба on
  История               [ ]        [✓]       только читать
  Напоминания           [✓]        [✓]       оба on
```

**Мерж:** LWW построчно по `updatedAt` (не список целиком). Следствия, которые надо честно показать в UI у «читать»: свежие локальные изменения не перезаписываются удалёнными более старыми.
**Удаления:** tombstone пишется только если `отдавать` категории включён; при `читать` tombstones применяются.
**Идентичность устройства:** `DeviceIdentity` (из `core/backup`) переезжает в `core/sync/local`.
**Транспорт:** существующий стек `core/sync/local` (discovery, pairing/`PairedDeviceStore`, auth, `LocalAutoSync` по расписанию) — остаётся; из payload вырезать профили/источники/настройки/prefs/quirks, оставить 4 категории.
**Топология:** попарная, как сейчас; LWW сходится при любом порядке и пропусках (любой ТВ может быть выключен месяцами). Проверить на паре redroid 5555↔5556.
**Не синхронизируется:** playback_prefs (акустика своя на каждом ТВ), настройки устройства, каталог/EPG (каждый качает с RuTube сам — идентичны по построению).

---

## 4. RuTube API (справка, детали в `docs/rutube.md`)

- Каталог: `GET /api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true` — ~13 страниц по ~20, пагинация полем `next`; поля: `id`(hexId), `author.name` (дубли — клининг «вещатель · очищенный title» уже написан), `author.avatar_url`/`thumbnail_url`, `broadcast_category[].id`, `order_number` (999 = без номера), `common_subscription_product_codes` (реальный признак подписки; `is_paid` всегда false — колонку из v55 приговорить на фазе ④), `schedule[]` = сид гида.
- Категории: `GET /api/livestream/categories/` (id/name/alias; id=1 «Все прямые эфиры» — служебная, в M:N не писать).
- EPG: `GET /pangolin/api/tv/tvprogram/{hexId}?client=tv&programDate=YYYY-MM-DD` — epoch-секунды строками; **404 = нет программы (норма, не ошибка)**; конкурентность 6.
- Поток: `GET /api/play/options/{hexId}/?no_404=true&pver=v2&referer=https%3A%2F%2Ftv-androidtv.rutube.ru` → `live_streams.hls[0].url`; 244/404 `common_subscription` → LOGIN_REQUIRED; `origin_type=ifrm` → нет потока. Кодек-потолок каталога: H.264 High + AAC, 360/720/1080p.
- Заголовки/UA (`com.rutube.mobile/…`) — свойство резолвера, не колонка канала.

---

## 5. Фазы работ

Порядок фиксирован: каждый следующий срез опирается на чистый компилятор предыдущего. Метод как в волне VOD: параллельные агенты по непересекающимся деревом-пакетам, интеграция компилятором, смоук между фазами.

**⓪ Коммит висящей волны 2** (v57, тесты, docs, строки) — один коммит, дерево чистое.

**① Профили + файловый бэкап** (срослись — одним срезом)
- Удалить: `features/profiles/**`, ProfileGate/launch-гейты в shell, `profiles`/`profile_source` таблицы и DAO, `profileId` из всех DAO-сигнатур и запросов (самая широкая механическая правка), PIN/детский/аватары, `core/backup/**` кроме `DeviceIdentity` (→ `core/sync/local`), BackupScreen/BackupViewModel/RemoteBackupRestoreScreen, Settings-секции «Резервная копия», `UserSettingsExport` профильные хуки.
- Sync пока оставить как есть (он ещё пересылает профили — переживёт срез, ужмётся в ③).
- Acceptance: старт сразу в Live без выбора профиля; сборка/тесты зелёные.

**② Источники: RuTube единственный**
- Удалить: `SourceType.{XTREAM,STALKER,M3U}`-ветки (sync'еры, клиенты, парсеры, `StreamUrlResolver`-ветки кроме RuTube), setup-wizard источников + `AddSource*`, `sources`/`epg_channels` таблицы+DAO, XMLTV-загрузчик, кридшеллы/`BackupCrypto`, per-playlist оверрайды в SettingsRepository, экран «Источники и телепрограмма».
- `channels`: `sourceId/categoryId/streamUrl/…` → целевой набор (переходно, финал в ④).
- Acceptance: чистый первый запуск → каталог наливается фоном; гид/тюнинг работают.

**③ Sync-матрица**
- Payload: 4 категории; LWW по `updatedAt`; tombstones-per-kind; галочки в DataStore; UI «Настройки → Синхронизация» (матрица 4×2 + подпись про свежие локальные изменения); история с `deviceId`.
- Acceptance: **redroid 5555 ↔ 5556**: на одном ставим закладку/меняем порядок/ставим напоминание → на втором появляется; отмена на одном не воскресает; «читать off» не трогает; «гостиная→кухня»: на «ТВ2» рельс «недавно в доме» → тюнинг того же канала.

**④ Схема v1 + драйвер**
- Удалить все миграции (1→57) и `Migration5xTo56/57`, `version = 1`, таблицы §2, `BundledSQLiteDriver` → `AndroidSQLiteDriver`, удалить `androidx.sqlite:sqlite-bundled` (+toml), пересоздать `app/schemas/…/1.json`, убрать self-heal/bulk-машинery где потеряли смысл, `healSchemaIfDrifted` — оставить только если ещё нужен.
- Acceptance: APK без `libsqliteJni.so` (проверка `unzip -l`), размер −~2.6 МБ; миграционный androidTest-файл удалён/заменён на schema-creation тест; всё на системном SQLite работает на API 26 (проверка на боксе Android 9 — **только по прямому указанию**).

**⑤ Каталог/EPG-стратегия + M:N**
- M:N категории в синкере (не «главная по приоритету»), окно EPG 2 суток, `epg_state`-чекпойнт, каталог на каждый запуск + период (параметр WorkManager — значения решаем в конце, на архитектуру не влияет), приговор `channels.isPaid`/`providerName` если не нужны UI.

---

## 6. Критерии всего плана

1. Первый запуск с нуля: ≤10 c до списка каналов, ≤30 c до работающего «сейчас/далее» по всем каналам (сид), сетка доливается фоном.
2. Ноль профилей/источников/файловых бэкапов в UI и коде; `grep` по `sourceId|profileId|Xtream|Stalker|M3U|Backup` в `core/` — пусто.
3. Синк 4 категорий между двумя redroid — acceptance ③.
4. APK: нет `libmpv*/libplayer/libowntvffmpeg/libav*/libsqliteJni`; остаются два 10-килобайтных androidx-`.so`.
5. Тесты зелёные; смоук-сценарий (запуск→гид→канал→EPG-карточка) повторяем на 5556 после каждой фазы.

## 7. Открытые / мелкие решения (не блокируют старт)

- Частоты фонового обновления (каталог на запуск + раз в сутки? EPG-докач период) — параметры WorkManager, решаем в ⑤.
- `playback_quirks` — выживет ли после ② (проверить потребителей).
- `orderNumber` из RuTube vs `channel_order` — совместимость: номер провайдера как дефолт.
- Доли UI: что показывает гид по каналу, чья сетка ещё не долита (полоса now/next из сида).
- `RemoteSetupScreen`/пульт-настройка — остаётся (свойство железа), уточнить в ②.
