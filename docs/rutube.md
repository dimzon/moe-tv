# RuTube: API, интеграция и ограничения

Документ фиксирует всё, что нужно помнить при работе с RuTube как единственным источником
каналов и программы передач в MyOwnTV. Составлен по живым запросам и по текущей реализации
в этом репозитории.

- Дата: 2026-11-09
- Источник данных: публичное API RuTube (без авторизации)
- Реализация: `app/src/main/java/tv/own/owntv/core/sync/` (клиент, парсер, синк, EPG)
- Встраивание в приложение: `BuiltInSource`, `SourceImporter.rutube()`, онбординг

---

## 1. Общие требования к запросам

| Что | Значение |
|---|---|
| User-Agent | `com.rutube.mobile/31.18.0 (Android 13)` — **обязателен** для `play/options`; с десктопным UA приходит 404 на каждый видео-id |
| Прокси | **запрещён**. Через системный прокси RuTube отдаёт ложные `blocking_rule` 404 и пустой `live_streams` |
| Referer | `https://rutube.ru/` (не обязателен, но ставим) |
| Accept | `application/json` |
| Авторизация | нет, всё публичное |

В коде: отдельный `OkHttpClient` с `java.net.Proxy.NO_PROXY` и Koin-квалификатором
`"rutubeOkHttp"`, обёрнутый в `HttpClient` с квалификатором `"rutube"`
(`core/di/DataModule.kt`).

---

## 2. Эндпоинты

### 2.1 Категории

```
GET https://rutube.ru/api/livestream/categories/
```

Ответ:

```json
{
  "results": [
    { "id": 1, "name": "Все прямые эфиры", "alias": "all" },
    { "id": 4, "name": "Федеральные", "alias": "federal" }
  ]
}
```

Наблюдаемые id (для справки; набор может меняться):

| id | Категория |
|---|---|
| 1 | Все прямые эфиры (не используется как основная категория) |
| 2 | Развлекательные |
| 3 | Новости |
| 4 | Федеральные |
| 5 | Кино и сериалы |
| 6 | Спорт |
| 7 | Детские |
| 8 | Музыка |
| 9 | Региональные |
| 10 | Познавательные |
| 11 | Телемагазин |
| 12 | 18+ |
| 14 | Религия |
| 15 | Мультики |

### 2.2 Каталог live-каналов (основной)

```
GET https://rutube.ru/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=false
```

- Пагинация: поле `next` — абсолютный URL следующей страницы; на последней странице `"next": null`.
- Объём: ~13 страниц, ~246 строк, из них ~243 уникальных канала.
- В выборке 40 каналов / 142 варианта качества — **только `avc1.64001f,mp4a.40.2`**
  (H.264 High + AAC-LC), HLS/MPEG-TS, 360p/720p/1080p. Других кодеков в каталоге нет.

Поля строки `results[]`:

| Поле | Смысл |
|---|---|
| `id` | videoId — **ключ канала** (плейбек + EPG) |
| `author.name` | имя вещателя (может повторяться) |
| `author.avatar_url` | логотип |
| `title` | заголовок эфира («Прямой эфир …») |
| `thumbnail_url` | запасной логотип, если нет `avatar_url` |
| `broadcast_category[].id` | категории канала (включая 1) |
| `order_number` | порядок; `999` = «без порядка», скрываем |
| `is_paid` | платный канал → `play/options` вернёт `login_required` |

### 2.3 Каталог с краткими расписаниями

```
GET https://rutube.ru/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true
```

Тот же ответ плюс `results[].schedule[]` — несколько ближайших передач:

```json
{
  "title": "Большая игра",
  "subtitle": "…",
  "description": "…",
  "start": "2026-11-09T17:00:00+03:00",
  "stop":  "2026-11-09T18:00:00+03:00"
}
```

Время — ISO-8601 с offset. Используется как дешёвый сид «сейчас/далее» перед полной сеткой.

### 2.4 Сетка на день (основной загрузчик EPG)

```
GET https://rutube.ru/pangolin/api/tv/tvprogram/{videoId}?client=tv&programDate=YYYY-MM-DD
```

- Ответ ~20–60 КБ на канал-день (в 30–50 раз дешевле мобильного эндпоинта).
- Время — epoch-**секунды строками** (`"start": "1794838282"`).
- `result.schedule` — объект `дата → массив передач`.
- **HTTP 404 — нормальный ответ**: у региональных каналов, вебкамер и событийных потоков
  программы нет. Это не ошибка и не повод идти в тяжёлый мобильный фолбэк.

Поля передачи:

| Поле | Смысл |
|---|---|
| `title` | название |
| `subtitle` / `description` | описание |
| `start` / `stop` | epoch-секунды (строкой или числом) |
| `ext_categories[].name` | жанр (идёт в `EpgProgrammeEntity.categories`) |
| `preview_picture_url` | картинка передачи (не сохраняем) |

### 2.5 Мобильное расписание (fallback)

```
GET https://rutube.ru/api/v1/livestream/video/schedule/{videoId}/
```

- Отдаёт все дни одним ответом (~1.9 МБ на канал за ~20 дней).
- Структура: `schedule[] → { program_date, items[] }`, поля передач те же.
- Используется **только** при реальной ошибке pangolin (сеть/5xx), не при 404.

### 2.6 Play options (получение playable-URL)

```
GET https://rutube.ru/api/play/options/{videoId}/
```

- Live-поток: `live_streams.hls[0].url` (HLS master/m3u8).
- VOD (не используется в этом проекте): `video_balancer.m3u8_m4s`, затем `video_balancer.m3u8`.
- Платный канал: HTTP 404, тело `{"detail": {"name": "login_required"}}`.
- Iframe-канал: `origin_type = "ifrm"`, `iframe_url` есть, потока нет.
- URL живёт ограниченное время → **резолвить при каждом tune и при reconnect**, не кешировать
  на диск.

---

## 3. Как это встроено в приложение

### 3.1 Встроенный источник

Одна запись `SourceType.RUTUBE` (таблица `sources`), создаётся идемпотентно:

- `BuiltInSource.template()`: `name = "RuTube"`, `url = https://rutube.ru`,
  `userAgent = com.rutube.mobile/31.18.0 (Android 13)`, `syncLive = true`,
  `syncMovies = false`, `syncSeries = false`.
- `BuiltInSource.ensure(sourceDao, profileId)` создаёт строку и линкует её к профилю;
  повторный вызов возвращает существующую.
- `SourceDao.rutubeSource()` — выборка `WHERE type = 'RUTUBE' LIMIT 1`.
- При неудачном первом синке built-in источник **не удаляется** (в отличие от IPTV-плейлистов):
  `SourceImporter.cleanupFailedAdd` чистит только контент.

### 3.2 Синхронизация каталога

`SyncManager.sync()` → `SourceType.RUTUBE` → `RuTubeSyncer.syncLive()`:

1. `RutubeApi.channels()` — все страницы по `next`.
2. `RutubeApi.categories()` → `SyncSupport.refreshCategories` (mediaType LIVE,
   `remoteId = id.toString()`).
3. Имена:
   - уникальное `author.name` — как есть;
   - дубли имён — `"author · очищенный title"`, где из title убраны префиксы
     `Прямой эфир`, `Прямая трансляция`, `Прямое включение`;
   - `order_number = 999` → `number = null` (скрыт).
4. Основная категория — по приоритету:
   `4, 3, 9, 5, 6, 7, 15, 8, 10, 2, 11, 14, 12, 1`; категория `1` не назначается.
5. Маппинг в `ChannelEntity`:
   - `remoteId = videoId`
   - `epgChannelId = videoId`
   - `streamUrl = "rutube://{sourceId}/{videoId}"` (маркер, **никогда не отдаётся плееру**)
6. Upsert через `SyncSupport.chunked` (`insertFresh` для первого синка,
   `upsertStable` для повторных) + prune после полного прохода под защитой
   catalog-shrink guard.

Пустой ответ каталога = исключение: «refusing to wipe the catalogue».

### 3.3 Программа передач

`EpgRepository`:

- `guideUrl(source)` для `RUTUBE` возвращает `null` — XMLTV-загрузчик к RuTube не применяется.
- `hasGuide(source)` = `true` для `RUTUBE`.
- `refresh(source)` → `refreshRutube()`:
  1. `channelDao.guideKeysForSource(sourceId)` → список videoId;
  2. `RuTubeEpgSyncer.sync(sourceId, ids)`:
     - быстрый сид `briefSchedules()` (tvprogramm=true);
     - по каналам: pangolin на 7 дней, конкурентность 6;
     - если день 0 пуст (404) — остальные дни не запрашиваются;
     - мобильный `/schedule/{id}/` — только при реальной ошибке;
     - запись `EpgChannelEntity` + `EpgProgrammeEntity` (sourceId, epgChannelId = videoId);
     - prune программ старше 2 суток.

Полуавтоматический диалог после онбординга (`EpgSemiAutoSync`) для `RUTUBE` вызывает
`epgRepository.refresh(source)` и не создаёт запись EPG-источника.

### 3.4 Воспроизведение

- `ChannelEntity.streamUrl` — маркер `rutube://…`.
- `StreamUrlResolver.needsResolve(source)` = `true` для `STALKER` **и** `RUTUBE`.
- `StreamUrlResolver.resolve()` для `RUTUBE` → `resolveRutube()`:
  - вырезает videoId из маркера;
  - вызывает `RutubeApi.playUrl(videoId)`;
  - `Ready` → HLS URL; `Unavailable` → `IOException` с причиной
    (`LOGIN_REQUIRED`, `IFRAME`, `NO_STREAM`, `NETWORK`).
- Резолв происходит на каждом tune/reconnect — URL из `play/options` короткоживущий.
- `RutubePlaybackResolver` (60-секундный кеш) существует для сценариев предпросмотра,
  но основной путь приложения — `StreamUrlResolver` без кеша.

---

## 4. База данных

- `SourceType.RUTUBE` — хранится по имени (TEXT), отдельной схемы не требует.
- v54 (`MIGRATION_53_54`): `sources.isPaid INTEGER NOT NULL DEFAULT 0`.
  Флаг RuTube `is_paid`; UI может помечать платные каналы, плеер всё равно получит
  `login_required` от API.
- Версия БД: **54**, имя `owntv.db`.
- Естественный ключ программ: `(sourceId, epgChannelId, startMs)` — не менялся.

---

## 5. Известные ограничения

1. **Платные каналы** (`is_paid = true`) не играют: `play/options` → 404 `login_required`.
2. **Iframe-каналы** (`origin_type = "ifrm"`) не имеют HLS-потока — только внешний плеер.
3. **Часть каналов без программы** — 404 на pangolin. По замеру на TVTube (тот же каталог):
   гид был у ~86 из 243 каналов.
4. **Дубли имён вещателей** (17 групп, 79 лишних строк в каталоге) — решаются
   disambiguation-именем; без него в списке несколько «Первый канал».
5. **`org.json` и JSON-null**: `optString("next", null)` возвращает строку `"null"`,
   а не null. Из-за этого последняя страница каталога давала URL `null` → падение синка.
   В `RutubeParser` для всех строковых полей используется helper `str(name)`, который
   считает JSON-null отсутствием. **Не заменять обратно на `optString(..., null)`.**
6. **Кеш play-URL**: не хранить дольше минуты; при reconnect резолвить заново.

---

## 6. Нативные библиотеки

В APK (arm64 — 22.7 MiB нативных `.so`):

| Библиотека | Источник | Размер | Можно ли убрать |
|---|---|---|---|
| `libmpv.so`, `libavcodec/format/util/filter/swscale/swresample`, `libc++_shared`, `libplayer`, `libowntvffmpeg` | `tv.own.owntv:libmpv` | ~21.8 MiB | Да, если оставить только ExoPlayer. Для RuTube потерь по кодекам нет: каталог — только H.264+AAC. Теряется софт-фолбэк на «капризных» MediaCodec |
| `libsqliteJni.so` | `androidx.sqlite:sqlite-bundled` | ~1.2 MiB | Только вместе с переходом на `AndroidSQLiteDriver` + правкой SQL (DAO используют `ON CONFLICT DO UPDATE` и `OVER (…)`, нужен SQLite ≥3.25; на Android 8 системный 3.18) |
| `libandroidx.graphics.path.so` | Compose UI | ~0.01 MiB | Нет смысла — нужна Compose |
| `libdatastore_shared_counter.so` | DataStore | ~0.01 MiB | Нет смысла — нужна DataStore |

ZXing (QR), juniversalchardet, OkHttp, Coil, Koin, Room, Media3 — чистый Java/Kotlin, без `.so`.

---

## 7. Карта файлов

```
app/src/main/java/tv/own/owntv/core/sync/
├── RutubeApi.kt          # HTTP-клиент: categories/channels/brief/scheduleDay/scheduleAllDays/playUrl
├── RutubeParser.kt       # org.json-парсер; helper str() для JSON-null
├── RutubeModels.kt       # RutubeChannel/Category/Programme/Playback
├── RuTubeSyncer.kt       # live-синк + RuTubeEpgSyncer + RutubePlaybackResolver
└── SyncManager.kt        # диспетчер SourceType.RUTUBE

app/src/main/java/tv/own/owntv/core/repository/
├── BuiltInSource.kt      # ensure() встроенного источника
└── EpgRepository.kt      # refresh()/refreshRutube()

app/src/main/java/tv/own/owntv/core/stalker/StreamUrlResolver.kt
                          # needsResolve/resolve для rutube:// маркера

app/src/main/java/tv/own/owntv/core/database/
├── Migration53To54.kt    # sources.isPaid
└── OwnTVDatabase.kt      # version = 54

app/src/main/java/tv/own/owntv/core/di/DataModule.kt
                          # rutubeOkHttp (NO_PROXY), rutube HttpClient, RutubeApi, resolver

app/src/main/java/tv/own/myowntv/features/setup/
├── SetupViewModel.kt     # startRutube()
└── SetupWizard.kt        # профиль → RuTube-импорт → гид

app/src/main/java/tv/own/myowntv/features/settings/EpgSemiAutoSync.kt
                          # RuTube-ветка refresh()
```

---

## 8. Проверка вручную (curl)

Все запросы — без прокси:

```bash
UA='com.rutube.mobile/31.18.0 (Android 13)'

curl -s -A "$UA" https://rutube.ru/api/livestream/categories/ | head -c 500

curl -s -A "$UA" \
  'https://rutube.ru/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=false' \
  | python -c "import sys,json;d=json.load(sys.stdin);print(len(d['results']), d['next'])"

curl -s -A "$UA" \
  'https://rutube.ru/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true' \
  | python -c "import sys,json;d=json.load(sys.stdin);r=d['results'][0];print(r['id'], r.get('schedule',[])[:1])"

# сетка на день (подставить реальный videoId)
curl -s -A "$UA" \
  'https://rutube.ru/pangolin/api/tv/tvprogram/<videoId>?client=tv&programDate=2026-11-09' \
  | head -c 500

# play options (подставить реальный videoId)
curl -s -A "$UA" 'https://rutube.ru/api/play/options/<videoId>/' \
  | python -c "import sys,json;d=json.load(sys.stdin);print(d['live_streams']['hls'][0]['url'])"
```

Проверка кодаков у потока:

```bash
curl -s '<hls-url>' | grep -E 'CODECS|RESOLUTION' | head
# ожидаемо: avc1.64001f,mp4a.40.2
```

---

## 9. Сборка и проверка приложения

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot"
export ANDROID_SDK_ROOT="C:/Work/tools/androidsdk"

cd C:/Work/OWNTV
./gradlew :app:assembleStandardDebug
./gradlew :app:compileStandardDebugUnitTestKotlin
```

Установка и smoke на ТВ:

```bash
adb install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
adb shell am force-stop tv.own.myowntv
adb shell am start -n tv.own.myowntv/.MainActivity
adb logcat | grep -E 'OwnTVPerf|SyncManager|Rutube|OwnTV-LivePreviewEngine'
```

Ожидаемый результат первого запуска:
- профиль создаётся, каталог ~243 канала;
- в Live TV есть «сейчас/далее»;
- канал играет HLS-сегментами (200, `isPlaying=true`, dropped=0).

---

## 10. История изменений по RuTube

- `SourceType.RUTUBE`, v54, `RutubeApi/Parser/Models`, `RuTubeSyncer`, `BuiltInSource`.
- Гид переведён с XMLTV-загрузчика на `RuTubeEpgSyncer` (`EpgRepository.refreshRutube`).
- `StreamUrlResolver` резолвит `rutube://` на каждом tune.
- Исправлен JSON-null в `next` (падение синка на последней странице).
- Имя профиля в онбординге предзаполняется — кнопка «Создать профиль» активна сразу.
- Ядро (`core` + `player-core`) перенесено в `:app`; отдельный Maven-модуль больше не используется.
