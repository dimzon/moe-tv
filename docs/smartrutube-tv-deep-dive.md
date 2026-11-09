# SmartRuTube: линейное ТВ — полный разбор

**Дата:** 2026-11-09  
**Анализируемый репозиторий:** `C:\Work\SmartRuTube`, ветка `master`, коммит `b51c7f7e`  
**Назначение документа:** справочник для агентов и разработчиков, которым нужно понять,
как в SmartRuTube устроены список телеканалов, телегид и воспроизведение live-ТВ,
какие эндпоинты и параметры используются, какие есть «хитрости» и как отбраковывается
платный/неиграбельный контент.

> Документ описывает **фактическое поведение кода**, а не только API RuTube.
> Где поведение проверялось живыми запросами, это помечено ссылкой на документ из `docs/`.
> Где что-то реализовано, но не подключено к UI, это указано явно.

---

## 0. Кратко

- **Линейное ТВ в приложении строится из двух независимых источников:**
  1. раздел **«ТВ»** (`Section.TV`, id 23) — полный список каналов из
     `GET /api/v2/video/stream/tvbroadcastinfo/` с текущей/ближайшей сеткой;
  2. раздел **«В ЭФИРЕ»** (`Section.LIVE`, id 19) — каналы из
     `GET /api/video/topic/1/`, отфильтрованные по `is_on_air=true`.
- **Плеер для ТВ использует отдельную ветку `play/options`:** мобильный User-Agent,
  без авторизации, с параметрами `no_404=true&pver=v2&referer=https://tv-androidtv.rutube.ru`.
  Это отключает рекламное расписание на стороне RuTube.
- **Источник потока — только HLS.** Live: `live_streams.hls[0].url`. VOD:
  `video_balancer.m3u8` (поле `m3u8_m4s` кодом не читается).
- **Платный контент отбраковывается на уровне карточек**, а не в плеере:
  `is_paid=true` **или** непустой `common_subscription_product_codes`
  **или** `origin_type=ifrm` **или** канал в жёстком списке битых ID.
- **Плеер не умеет помечать контент unplayable:** поля `mIsUnplayable`/`mPlayabilityReason`
  в `RuTubeMediaItemFormatInfo` никогда не заполняются. Если платный/iframe-контент
  открыть напрямую, он попадёт в ветку «нет HLS» и будет перезагружаться каждые 30 секунд.

---

## 1. Карта кода

| Слой | Файл | Роль |
|---|---|---|
| Модели API | `rutubeapi/.../models/RuTubeBroadcastPage.java` | каналы `tvbroadcastinfo`: id, author, schedule, is_paid, category |
| Модели API | `rutubeapi/.../models/RuTubeProgram.java` | программа из `tvbroadcastinfo.schedule` (ISO-8601) |
| Модели API | `rutubeapi/.../models/RuTubeTvProgram.java` | программа из `pangolin/tvprogram` (Unix-секунды) |
| Модели API | `rutubeapi/.../models/RuTubePlayOptions.java` | `video_balancer`, `live_streams`, `captions`, `audiotracks`, `stat` |
| Модели API | `rutubeapi/.../models/RuTubeVideoItem.java` | карточка канала/видео: `is_livestream`, `is_on_air`, `is_paid`, `origin_type` |
| Эндпоинты | `rutubeapi/.../api/RuTubeEndpoints.java` | все пути и константы параметров |
| Retrofit | `rutubeapi/.../api/RuTubeApi.java` | объявления запросов |
| HTTP | `rutubeapi/.../common/helpers/RetrofitHelper.java` | два клиента: обычный и TV (мобильный UA) |
| HTTP | `rutubeapi/.../common/helpers/DefaultHeaders.java` | точные строки User-Agent |
| Списки | `rutubeapi/.../service/RuTubeContentService.java` | ТВ- и LIVE-секции, тематические ряды, фильтрация |
| EPG | `rutubeapi/.../service/RuTubeEpgService.java` | каналы `tvbroadcastinfo`, программы на день, кэш |
| Фильтр | `rutubeapi/.../common/helpers/RuTubeChannelFilter.java` | платный/iframe/битый контент |
| Плеер | `rutubeapi/.../service/RuTubeMediaItemService.java` | выбор play options, watchtime, метаданные |
| Формат | `rutubeapi/.../service/data/RuTubeMediaItemFormatInfo.java` | выбор HLS, isLive/isTV, субтитры, аудио |
| UI-секции | `common/.../app/models/section/Section.java` | `TV` и `LIVE`, провайдеры данных |
| UI-сетка | `smarttubetv/.../ui/browse/video/TvChannelPageFragment.java` | сетка каналов, категории, клик |
| Плеер-контроллер | `common/.../playback/controllers/VideoLoaderController.java` | маршрутизация HLS/unplayable/reload |
| ExoPlayer | `common/.../exoplayer/ExoMediaSourceFactory.java` | HLS-источник, UA, data source |
| HLS-парсер | `common/.../exoplayer/hls/RuTubeHlsPlaylistParserFactory.java` | дедуп вариантов, имена аудиодорожек |
| Форк HLS | `exoplayer-amzn-2.10.6/.../HlsPlaylistParser.java` | терпимость к `#EXT-X-MEDIA` без `NAME` |
| EPG в плеере | `common/.../playback/controllers/EpgController.java` | кнопка «Телегид», ближайшие передачи |
| Live-метаданные | `common/.../playback/controllers/LiveViewersController.java` | опрос `views_online` каждые 15 с |
| Чат | `rutubeapi/.../service/RuTubeLiveChatService.java` | polling-чат каждые 5 с |

---

## 2. Как получается список телеканалов

### 2.1. Раздел «ТВ» — полный список с телегидом

**UI-секция:** `Section.TV` (`code=23`, заголовок `header_tv` = «ТВ»),
layout `tvGrid`, провайдер:
`RuTubeContentService.getTvChannelsWithEpgObserve()`
(`common/.../section/Section.java:39-41`).

**Цепочка:**

```text
Section.TV
  → RuTubeContentService.getTvChannelsWithEpgObserve()          (строки 577-648)
    → RuTubeEpgService.getChannelsCached()                       (EpgService:128-168)
      → GET /api/v2/video/stream/tvbroadcastinfo/
           ?client=tv&tvprogramm=true
      → следование по полю next (непрозрачный абсолютный URL)
      → RuTubeChannelFilter.isTvChannelAllowed(channel)
      → кэш 30 минут
    → для каждого канала:
        author.name (fallback title) → имя канала
        schedule[] → текущая передача, процент, «осталось N мин»
        RuTubeMediaItem.fromTvChannel()
    → CardEnricher.putAll(toCardItems(channels))
```

**Параметры запроса `tvbroadcastinfo` в коде:**

| Параметр | Значение в SmartRuTube | Комментарий |
|---|---|---|
| `video_id` | не передаётся (`null`) | получить все каналы |
| `client` | `tv` | обязателен для ТВ-выдачи |
| `tvprogramm` | `true` | вернуть `schedule[]` |
| `page` | не передаётся вручную | пагинация только через `next` |
| `category` | **не используется** | API поддерживает, приложение фильтрует категории локально |

Метод Retrofit: `RuTubeApi.getBroadcastInfo(videoId, client, tvProgram)`
(`RuTubeApi.java:385-388`); путь — `RuTubeEndpoints.BROADCAST_INFO`
(`RuTubeEndpoints.java:91`).

**Пагинация:** в ответе есть `results`, `next`, `has_next`, `page`, `per_page`.
`RuTubeEpgService` идёт по `next`, пока он непустой, и складывает все страницы
(`RuTubeEpgService.java:141-152`). По проверенным ответам `total≈252`,
`per_page=20`, то есть около 13 последовательных запросов. Поле `next` —
**непрозрачный URL**, его нельзя заменять на `?page=2` вручную: при запросе
с `video_id` `next` может указывать на следующий канал, а не на страницу
(`docs/rutube-reverse-2026-10-27.md:1173-1175`).

**Что приходит в канале `tvbroadcastinfo`:**

```json
{
  "id": "c58f502c7bb34a8fcdd976b221fca292",
  "title": "Пусть говорят",
  "thumbnail_url": "https://pic.rtbcdn.ru/...",
  "author": {"id": 23460655, "name": "Первый канал", "avatar_url": "..."},
  "broadcast_category": [{"id": 4, "name": "Федеральные"}],
  "is_paid": false,
  "common_subscription_product_codes": [],
  "is_livestream": true,
  "stream_type": "broadcast",
  "origin_type": "rst",
  "schedule": [
    {
      "title": "Пусть говорят",
      "subtitle": "...",
      "start": "2026-10-27T19:50:00+03:00",
      "stop": "2026-10-27T21:00:00+03:00",
      "description": "...",
      "ageRating": "16+"
    }
  ]
}
```

Ключевое:

- **`title` — это текущая передача, а не имя канала.** Имя канала — `author.name`
  (`RuTubeMediaItem.fromTvChannel`, `RuTubeMediaItem.java:240-264`).
- `schedule[]` содержит несколько ближайших передач (в проверенном ответе — 3),
  время в ISO-8601 с часовым поясом.
- `stream_type="broadcast"` — маркер ТВ-канала; `origin_type="rst"` — обычный
  RuTube-поток; `origin_type="ifrm"` — внешний iframe-плеер.
- `is_on_air` в `tvbroadcastinfo` не используется для отсева: раздел «ТВ»
  показывает весь список, который вернул сервер, без проверки `is_on_air`
  (в модели `RuTubeBroadcastPage.Channel` этого поля вообще нет).

**Карточка канала:**

`RuTubeMediaItem.fromTvChannel` создаёт `MediaItem.TYPE_VIDEO`:
`videoId = channel.id`, `channelId = channel.id`, `title/author/channelName = author.name`,
`cardImageUrl/backgroundImageUrl/previewUrl = thumbnailUrl`, `isTV = true`
(`RuTubeMediaItem.java:240-264`).

Если в `schedule[]` найдена передача, покрывающая `now`:

- `percent = (now - start) * 100 / (stop - start)` — прогресс передачи;
- `remainingMin = (stop - now) / 60000`;
- второй заголовок: `"<передача> · осталось N мин"` (`rutube_tv_program_remaining`,
  `rutubeapi/src/main/res/values-ru/ui_strings.xml:10`);
- `setTvProgram(text, percent)` записывает это в `mLiveSecondTitle`/`mPercentWatched`
  (`RuTubeMediaItem.java:266-271`).

**Категории ТВ:**

`RuTubeContentService.getTvCategoriesObserve()` (`строки 688-730`):

1. берёт уже закэшированные каналы `RuTubeEpgService.getChannelsCached()`;
2. собирает уникальные `broadcast_category[].id/name`, **пропуская `id <= 1`**
   (id 1 — общая категория «Все прямые эфиры»);
3. первой карточкой добавляет «Все» с `id = -1`;
4. миниатюра категории — миниатюра первого канала.

Категории **не запрашиваются отдельным API**: это локальная группировка поля
`broadcast_category`. Не путать с id топиков `/api/video/topic/{id}/` — это разные
пространства идентификаторов (см. 2.3).

**UI:**

`TvChannelPageFragment` (`smarttubetv/.../TvChannelPageFragment.java`):

- верхний ряд — карусель категорий, ниже — ряды по **2 канала** (`CHANNELS_PER_ROW=2`);
- выбор категории не делает сетевой запрос: `mCurrentCategoryId` и локальная карта
  `videoId → Set<categoryId>` перестраивают ряды (`filterChannels`, `buildCategoryMappings`);
- клик по каналу → `BrowsePresenter.onVideoItemClicked(video)`;
- долгий клик — контекстное меню.

### 2.2. Раздел «В ЭФИРЕ» — только вещающие каналы

**UI-секция:** `Section.LIVE` (`code=19`, заголовок `badge_live` = «В ЭФИРЕ»),
layout `grid`, провайдер `getLiveObserve()` (`Section.java:42-44`).

**Цепочка:**

```text
Section.LIVE
  → RuTubeContentService.getLiveObserve()                  (строки 564-572)
    → fetchLive()                                          (строки 1572-1589)
      → GET /api/video/topic/1/?page=1
      → filterTvChannels(page, requireOnAir=true)          (строки 1151-1170)
      → createGroup(page, TYPE_LIVE, null, topic/1?page=1)
      → markAsTvChannels(group)
```

**Параметры:**

| Параметр | Значение |
|---|---|
| topic id | `1` — «Все прямые эфиры» |
| `page` | `1` при первой загрузке |
| фильтр | `is_on_air == true` **и** не платный/не iframe/не битый |

`topic/1` — это ТВ-топик «Все прямые эфиры». По проверенным данным:
~250 каналов, 3 страницы по 100, `origin=rst`, `is_livestream=true`,
на последней странице встречаются `is_on_air=false`
(`docs/rutube-reverse-2026-10-27.md:1340`).

**Пагинация:**

- `createGroup` сохраняет `page.next` в `MediaGroup.nextPageKey`
  (`RuTubeContentService.java:1616`);
- `BrowsePresenter.continueGroup` передаёт этот URL в
  `RuTubeContentService.continueGroup` (`строки 365-389`);
- `RuTubeFeedLoader` для `type == TYPE_LIVE` **повторно** применяет
  `filterTvChannels(page, true)` (`RuTubeFeedLoader.java:75-77`).

То есть первая страница и все продолжения фильтруются по `is_on_air`.
Начальный экран показывает первую страницу (до 100 сырых карточек, после фильтра — меньше);
остальное подгружается прокруткой.

**Важная деталь:** продолжения загружаются через **обычный** API-клиент
(`RuTubeApiHolder.get()`, desktop UA), а не через TV-клиент. TV-клиент используется
только для первого запроса `topic/1` в `fetchLive`. Это не мешает работе, но
объясняет разный UA в логах.

**Маркировка:** `markAsTvChannels` ставит `isTV=true` каждому элементу
(`RuTubeContentService.java:1591-1602`). Поэтому карточки «В ЭФИРЕ» не показывают
бейдж LIVE (у них бейдж «TV»), не имеют прогресса просмотра и в плеере идут
по ТВ-ветке.

### 2.3. Тематические ряды с телеканалами

Помимо двух основных разделов, каналы подмешиваются в тематические секции:

| Секция | Топик каналов | Видео-категория | Метод |
|---|---|---|---|
| Спорт | `6` | `16` | `buildThematicSection("ОБЗОРЫ И НОВОСТИ СПОРТА", 6, 16)` |
| Новости | `3` | `8` | `buildThematicSection("НОВОСТИ И СМИ", 3, 8)` |
| Музыка | `8` | `6` | `buildThematicSection("МУЗЫКА", 8, 6)` |
| Детские | `7` + `15` (объединение) | — | `fetchUniqueTopicChannels(7, 15)` |
| Видеоигры | — | `22` | `buildThematicSection("ВИДЕОИГРЫ", 0, 22)` |

Код: `RuTubeContentService.java:497-514, 520-551, 554-562, 737-745`.

`buildThematicSection` собирает до трёх рядов:

1. редакционный ряд из главного фида (если он ещё существует);
2. ряд телеканалов топика (`fetchTopicChannels(topicId)`);
3. ряд видео категории.

`fetchTopicChannels`:

- `GET /api/video/topic/{id}/?page=1`;
- `filterTvChannels(page, false)` — **без** требования `is_on_air`;
- `createGroup(page, TYPE_HOME, null, topicUrl)` — тип HOME, но URL содержит
  `/api/video/topic/`, поэтому `createGroup` всё равно вызывает `markAsTvChannels`
  (`RuTubeContentService.java:1618-1619`);
- `reloadPageKey` = URL первой страницы топика.

`fetchUniqueTopicChannels` для детских объединяет топики 7 и 15, убирает дубли по `id`
и тоже не требует `is_on_air`. Причина объединения — пересечение списков
(комментарий в коде, строка 501).

**Id топиков ТВ Онлайн** (проверено): 1=Все, 2=Развлекательные, 3=Новости,
4=Федеральные, 5=Кино и сериалы, 6=Спорт, 7=Детские, 8=Музыка, 9=Региональные,
10=Познавательные, 11=Телемагазин, 12=Мультфильмы, 14=Религия, 15=18+
(`docs/rutube-reverse-2026-10-27.md:1341`).

### 2.4. Чего в приложении нет

- **Отдельного раздела «ТВ Онлайн» больше нет.** В `SidebarService` остались только
  константы для очистки старых сохранённых id: `LEGACY_TV_ONLINE_ID_BASE = 1000000`,
  `LEGACY_TV_ONLINE_ID_COUNT = 100`; такие элементы удаляются из сайдбара
  (`SidebarService.java:37-38, 323-326, 348-350`). Комментарий в коде:
  `// TV Online was removed; purge its persisted section items.`
- **`RuTubeSectionService`** (динамические секции) работает с видео-категориями
  (`GET /api/video/category/`) и рядами главного фида, а не с ТВ-топиками.
- **`getLiveStreams()`** (`/api/video/list/public/?live=stream...`) объявлен,
  но нигде не вызывается; это бывший источник «В эфире».
- **`GET /api/livestream/categories/`** в коде не используется: категории берутся
  из `broadcast_category` ответа `tvbroadcastinfo`.
- **`GET /api/v2/video/stream/{id}/info/`** в коде отсутствует: это статус стрима,
  а не источник потока.

---

## 3. Телегид (EPG)

### 3.1. Ближайшая сетка в карточках и в плеере

Основной источник — `schedule[]` из `tvbroadcastinfo`:

- карточка канала показывает текущую передачу и прогресс (см. 2.1);
- кнопка «Телегид» в плеере (`EpgController`) запрашивает **тот же** эндпоинт
  с `video_id=<id>`:

```text
GET /api/v2/video/stream/tvbroadcastinfo/?video_id=<id>&client=tv&tvprogramm=true
```

Код: `RuTubeEpgService.getProgramsObserve(videoId)` (`строки 57-78`).

Далее:

- из `results[]` выбирается канал с `id == videoId`;
- его `schedule[]` сортируется по `start`;
- в панели показываются до 3 передач, у которых `stop > now`;
- для каждой: «Сейчас»/«Далее», время `HH:mm–HH:mm`, заголовок, возрастной рейтинг,
  подзаголовок и описание (`EpgController.java:84-116`).

`EpgController` работает только если `video.isTV == true` (`строки 56-59`).

### 3.2. Полная сетка на день

Реализована, но **не подключена к UI**:

```text
GET /pangolin/api/tv/tvprogram/{videoId}?client=tv&programDate=YYYY-MM-DD
```

Код: `RuTubeEpgService.getEpgObserve(date)` и `getProgramsForDayObserve(videoId, date)`
(`строки 84-126`), `fetchProgram` (`строки 188-207`).

Особенности:

- ответ: `result.schedule["YYYY-MM-DD"] = [program...]`;
- `start`/`stop` — **Unix-секунды строками**, а не ISO-8601;
- кэш: 30 минут на пару `videoId + date`;
- метод `getEpgObserve` умеет отдавать снапшот инкрементально: сначала каналы без
  программ, затем обновляет каждый канал по мере загрузки (`строки 84-122`).

Поиск по коду показывает, что `getEpgObserve`/`getProgramsForDayObserve`
**не вызываются из UI** — используется только `getProgramsObserve` (ближайшие
передачи). То есть полноценная сетка на день в приложении не отображается.

### 3.3. Два разных формата времени

| Источник | Поле | Формат | Парсер |
|---|---|---|---|
| `tvbroadcastinfo.schedule` | `start`, `stop` | ISO-8601 со смещением: `2026-10-27T19:50:00+03:00` | `RuTubeProgram.parseTime`: нормализует `Z` → `+0000`, `+03:00` → `+0300`, отбрасывает доли секунды, парсит в UTC (`RuTubeProgram.java:11-52`) |
| `pangolin/tvprogram` | `start`, `stop` | Unix-секунды строками: `"1793066400"` | `RuTubeTvProgram.getStartMs() = start * 1000` (`RuTubeTvProgram.java:22-30`) |

`RuTubeProgram` использует `ThreadLocal<SimpleDateFormat>` вместо
`ThreadLocal.withInitial()`, потому что приложение поддерживает API 17
(комментарий в коде).

### 3.4. Категории ТВ vs топики

| Что | Где | Пример id | Как используется |
|---|---|---|---|
| `broadcast_category` | `tvbroadcastinfo` | 4 = «Федеральные» | карточки категорий в разделе «ТВ», локальная фильтрация |
| `topic` | `/api/video/topic/{id}/` | 1 = «Все прямые эфиры», 6 = «Спорт» | списки каналов для «В ЭФИРЕ» и тематических рядов |

Это **разные id**. Нельзя подставлять `broadcast_category.id` в `/api/video/topic/`
и наоборот. В коде это разделено: категории ТВ нигде не уходят в topic-запросы.

---

## 4. Как играется ТВ

### 4.1. Маршрутизация в плеере

`VideoLoaderController.loadFormatInfo` (`строки 255-279`):

```java
Observable<MediaItemFormatInfo> formatInfoObservable =
        video.isTV && video.mediaItem != null
                ? mediaItemManager.getFormatInfoObserve(video.mediaItem)
                : mediaItemManager.getFormatInfoObserve(video.videoId);
```

То есть **ТВ-ветка включается только если у `Video` есть `isTV=true` и непустой
`mediaItem`**. Если карточка была восстановлена из истории/кэша без `mediaItem`,
запрос уйдёт в обычную VOD-ветку.

`RuTubeMediaItemService.getFormatInfo(MediaItem)` (`строки 100-112`):

```java
if (item instanceof RuTubeMediaItem && ((RuTubeMediaItem) item).isTV) {
    RuTubePlayOptions options = fetchPlayOptions(item.getVideoId(), mTvApi, true);
    return RuTubeMediaItemFormatInfo.from(options, item);
}
return getFormatInfo(item.getVideoId()); // обычная ветка
```

`mTvApi` создан через `RetrofitHelper.createTv(...)` (`строка 68`).

### 4.2. TV-ветка play options

`fetchPlayOptions` (`RuTubeMediaItemService.java:129-153`):

```text
GET /api/play/options/{videoId}/
    ?no_404=true
    &pver=v2
    &referer=https%3A%2F%2Ftv-androidtv.rutube.ru
```

Параметры:

| Параметр | Значение | Зачем |
|---|---|---|
| `no_404` | `true` | не спамить 404 при отсутствии потока |
| `pver` | `v2` | версия play options; ответ заметно больше |
| `referer` | `https://tv-androidtv.rutube.ru` | **query-параметр**, не заголовок; отключает рекламное расписание на сервере |

Проверено: `pver=v2&referer=https://tv-androidtv.rutube.ru` даёт `advert: []`;
`no_404=true` 404 не обходит, только глушит
(`docs/rearutube-reference-2026-10-30.md:18-30`,
`docs/rutube-uwp-reverse-2026-10-30.md:150-158`).

**Авторизация:** TV-клиент создаётся с `useAuthorization=false`
(`RetrofitHelper.java:33-35`), Bearer не добавляется. Live-ТВ играет без токена.

**Fallback для обычной ветки:** если обычный `play/options` вернул `404` и в теле
есть `"blocking_rule"`, код повторяет запрос через TV-ветку
(`RuTubeMediaItemService.java:141-145`). Это лечит часть WAF-блокировок.
Обратная сторона: проверка `tv` в этой ветке отсутствует. Если **TV-ветка** тоже
вернёт `404` с `blocking_rule`, произойдёт рекурсивный вызов самого себя
(`fetchPlayOptions(videoId, mTvApi, true)`), то есть потенциальный stack overflow.
На практике `no_404=true` обычно превращает такой ответ в `200/пусто`, но это
неустранённый риск.

**Обычная (не ТВ) ветка:**

```text
GET /api/play/options/{videoId}/?client=wdp&mq=all
```

`client=wdp&mq=all` — веб-клиент; по проверенным данным это открывает 2160p
на VOD (`RuTubeEndpoints.java:22-28`). На live не влияет.

### 4.3. Выбор HLS-URL

`RuTubeMediaItemFormatInfo.from` (`строки 40-97`):

1. `video_balancer.m3u8`, если непустой;
2. иначе `video_balancer.default`;
3. если оба пустые — первый непустой `live_streams.hls[].url` (`getLiveHlsUrl`,
   `строки 142-156`);
4. в `mUrlFormats` добавляется ровно один HLS-формат с `"auto"`, 0×0.

Для live `video_balancer` обычно `{}`, поэтому срабатывает `live_streams.hls[0].url`.
Для VOD — подписанный `video_balancer.m3u8`.

**Чего код не делает:**

- не читает `video_balancer.m3u8_m4s` (в модели `RuTubeVideoBalancer` только
  `default` и `m3u8`, `RuTubeVideoBalancer.java`);
- не проверяет `live_streams.hls[].is_video`/`is_audio`/`is_dvr` — берёт первый
  непустой URL;
- не различает iframe: в `RuTubePlayOptions` нет `origin_type`, `has_video`,
  `iframe_url`; если потока нет, `mHlsManifestUrl` останется `null`.

### 4.4. Что происходит после выбора URL

`VideoLoaderController.processFormatInfo` (`строки 281-341`):

```text
if (formatInfo.isUnplayable())        → заглушка/оверлей (ветка мертва, см. 8.4)
else if (formatInfo.containsHlsUrl()) → player.openHlsUrl(url, subtitles, audioTracks)
else                                  → оверлей + reloadVideo(30_000)
```

Для ТВ-канала `isUnplayable()` всегда `false`: поля `mIsUnplayable` и
`mPlayabilityReason` в `RuTubeMediaItemFormatInfo` нигде не присваиваются
(объявлены в строках 37-38, геттеры — 313-326; поиск по `mIsUnplayable =` пуст).
`getPaidContentText()` всегда `null`.

Если HLS нет (iframe-канал, платный контент, будущий live без данных), плеер
показывает фон/оверлей и планирует повтор через 30 секунд (`строки 330-337`).

### 4.5. ExoPlayer и HLS

`ExoMediaSourceFactory` (`common/.../exoplayer/ExoMediaSourceFactory.java`):

- HLS-источник: `HlsMediaSource.Factory` с `DefaultHlsExtractorFactory(0, false)`
  и кастомным `RuTubeHlsPlaylistParserFactory` (`строки 160-171`);
- источник данных: OkHttp или `DefaultHttpDataSource` в зависимости от настройки
  `PlayerTweaksData.PLAYER_DATA_SOURCE_*` (`строки 145-152`);
- **UA для медиа — desktop Chrome** (`USER_AGENT = DefaultHeaders.APP_USER_AGENT`,
  `строки 44-45`);
- `Referer` для медиа-запросов не ставится: `addCommonHeaders` пуст
  (`строки 210-214`).

`RuTubeHlsPlaylistParserFactory`:

- **дедуплицирует варианты** по ключу
  `width×height|codecs|bitrate|frameRate|videoGroup|audioGroup|subtitleGroup|captionGroup`
  (`строки 56-87`);
- **подписывает аудиодорожки**: берёт из рендиции query-параметр `streams=aN`,
  находит `audiotracks` с `track_index == N`, ставит `name`/`language`,
  добавляет `:tN` к `format.id` (`строки 89-170`).

Зачем: RuTube отдаёт master-манифест, где каждый вариант качества продублирован
(два CDN-зеркала), а аудио-рендиции `#EXT-X-MEDIA` идут без `NAME`/`LANGUAGE`.
Единственный источник имён — `audiotracks` из play options
(`docs/rutube-hls-urls-2026-11-07.md:45-80`).

В форке ExoPlayer также ослаблен парсер: `#EXT-X-MEDIA` без `NAME` больше не
валит весь плейлист (`HlsPlaylistParser.java:392-396`, коммит `1f2155ad`).

Субтитры: если в play options есть `captions[]` с `format=srt|vtt`, создаётся
`SingleSampleMediaSource` и объединяется с HLS через `MergingMediaSource`
(`ExoMediaSourceFactory.java:66-120`). MIME: `srt → application/x-subrip`,
`vtt → text/vtt`.

Аудиодорожки: если `audiotracks` больше одной, UI показывает кнопку выбора
(`VideoLoaderController.java:327-329`).

### 4.6. Live-специфика

- **Онлайн-зрители:** `LiveViewersController` опрашивает
  `GET https://goya.rutube.ru/video/{id}/?online=1` каждые **15 секунд**
  (`LiveViewersController.java:66-99`), результат — `views_online`.
- **Чат:** `RuTubeLiveChatService` — polling каждые **5 секунд**, `limit=25`,
  `direction=past` для первой загрузки, затем `direction=future&time=<unix>`,
  `format=json` (`RuTubeLiveChatService.java:21-56`). Ключ чата появляется в
  метаданных, если `isLive && !hide_chat` (`RuTubeMediaItemService.java:833`).
- **Позиция просмотра:** для live не отправляется. `watchtime`-шаблон из
  `play/options.stat` запоминается, но при `template.live` отправка пропускается
  (`RuTubeMediaItemService.java:339-341, 400-403`). Для VOD — раз в 10 секунд.
- **DVR:** `isLiveDvrEnabled()` возвращает `false` (`RuTubeMediaItemFormatInfo.java:239-242`).

---

## 5. User-Agent, заголовки, авторизация

### 5.1. Точные строки

`DefaultHeaders.java:4-8`:

```text
Desktop:
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36

Mobile:
Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36

Referer (заголовок):
https://rutube.ru/
```

### 5.2. Где какой UA

| Клиент | UA | Авторизация | Заголовки |
|---|---|---|---|
| Обычный API (`RetrofitHelper.create`) | Desktop Chrome | Bearer, если есть токен | `User-Agent`, `Referer: https://rutube.ru/`, `Accept: application/json` |
| TV API (`RetrofitHelper.createTv`) | Mobile Chrome | **нет** | те же `User-Agent`/`Referer`/`Accept`, но UA мобильный |
| TV play options | Mobile Chrome | нет | плюс query-параметры `no_404`, `pver`, `referer` |
| Медиа (ExoPlayer) | Desktop Chrome | нет | только `User-Agent`; `Referer` не ставится |
| `watchtime` (VOD) | Desktop Chrome | нет | `User-Agent`, `Referer: https://rutube.ru/` |

`RetrofitHelper.create` (`строки 37-73`) задаёт три заголовка вручную:
`User-Agent`, `Referer`, `Accept: application/json`. Для TV-клиента меняется только UA
(`строки 30-35, 39`); `Referer` остаётся `https://rutube.ru/`.
Поэтому не путать:

- **заголовок** `Referer: https://rutube.ru/` — всегда;
- **query-параметр** `referer=https://tv-androidtv.rutube.ru` — только в TV play options.

### 5.3. Почему мобильный UA

Комментарий в коде: «TV branch: mobile user agent (server gates /api/play/options
by it) and no auth token» (`RetrofitHelper.java:31-32`).

Живые проверки: на некоторых live-каналах `play/options` с обычным
`Mozilla/5.0` возвращает `244 blocking_rule`, а с UA, содержащим `Android`, —
`200` + HLS. `no_404` эту блокировку не снимает
(`docs/rutube-uwp-reverse-2026-10-30.md:150-158`).

Также важно: запросы к RuTube нужно делать **без системного прокси** — он даёт
ложные `blocking_rule` и пустые `live_streams`
(`docs/rutube-reverse-2026-10-27.md:1326-1334`).

---

## 6. Каталог эндпоинтов

### 6.1. Линейное ТВ

| Метод и путь | Параметры | Где используется | Назначение |
|---|---|---|---|
| `GET /api/v2/video/stream/tvbroadcastinfo/` | `client=tv`, `tvprogramm=true`, опционально `video_id`; пагинация через `next` | `RuTubeEpgService.getChannelsCached`, `getProgramsObserve` | полный список каналов + ближайшая сетка |
| `GET /pangolin/api/tv/tvprogram/{videoId}` | `client=tv`, `programDate=YYYY-MM-DD` | `RuTubeEpgService.fetchProgram` (не подключено к UI) | полная сетка канала на день |
| `GET /api/video/topic/{id}/` | `page` | `fetchLive` (id=1), `fetchTopicChannels` (3/6/8), детские (7/15) | списки ТВ-каналов |
| `GET /api/play/options/{videoId}/` | TV: `no_404=true`, `pver=v2`, `referer=https://tv-androidtv.rutube.ru`; VOD: `client=wdp`, `mq=all` | `RuTubeMediaItemService.fetchPlayOptions` | источник потока, субтитры, аудио, watchtime |
| `GET /api/video/{id}/` | — | `getMetadata` | метаданные канала/видео |
| `GET https://goya.rutube.ru/video/{id}/?online=1` | — | `RuTubeLiveViewersService` | `views_online` |
| `GET /api/chat/{videoId}/` | `limit`, `direction`, `time`, `format` | `RuTubeLiveChatService` | live-чат |
| `GET /api/acl/blockingrule/{ruleId}` | — | объявлен, не вызывается | диагностика `blocking_rule` |
| `GET /api/video/list/public/?live=stream&type=tag&sort=publication_d&show_hidden_videos=False` | `page` | объявлен, не вызывается | бывший источник «В эфире» |

### 6.2. Смежные TV-эндпоинты (не линейное ТВ)

| Метод и путь | Статус в коде | Что это |
|---|---|---|
| `GET /pangolin/api/tv/feeds/{slug}/` | используется для `TV_SHOWS`/`MOVIES` через showcase | TV-витрины (шоу/сериалы) |
| `GET /pangolin/api/tv/resource/{slug}/{resourceId}` | используется там же | ряды витрины |
| `GET /api/metainfo/tv/` | объявлен, TODO | каталог шоу |
| `GET /api/metainfo/tv/{id}/season/` | объявлен, TODO | сезоны |
| `GET /api/metainfo/tv/{id}/video` | объявлен, TODO | серии |
| `GET /api/metainfo/tv/{id}/last_episode/` | объявлен, TODO | последняя серия |
| `GET /api/playlist/tv/{id}/season/{season}` | объявлен, TODO | лёгкий список серий |
| `GET /pangolin/api/tv/video/{videoId}/annotation` | объявлен, TODO | skip-intro/credits; без `client=tv` → 400 |
| `GET /api/v2/video/end-screen/{videoId}/` | объявлен, TODO | next video + рекомендации |
| `GET /api/search/combined/cards/list` | объявлен, TODO | поиск каналов и видео |
| `POST /pangolin/api/tv/reactions` | используется | лайк/дизлайк |

Это важно, чтобы не искать «список каналов» в `metainfo/tv` — там шоу и сериалы,
а не линейные телеканалы.

### 6.3. Практические запросы

Все запросы к RuTube — **без прокси** (`--noproxy '*'`). Для TV-ветки нужен
мобильный UA из раздела 5.

```bash
UA='Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36'

# 1. Полный список каналов с ближайшей сеткой (первая страница)
curl --noproxy '*' -s -A "$UA" \
  'https://rutube.ru/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true'

# 2. Следующая страница — подставить next из ответа как есть
curl --noproxy '*' -s -A "$UA" '<next из ответа>'

# 3. Каналы «В эфире» (потом отфильтровать is_on_air=true)
curl --noproxy '*' -s -A "$UA" 'https://rutube.ru/api/video/topic/1/?page=1'

# 4. TV play options (referer — query-параметр, URL-encoded)
curl --noproxy '*' -s -A "$UA" \
  'https://rutube.ru/api/play/options/<videoId>/?no_404=true&pver=v2&referer=https%3A%2F%2Ftv-androidtv.rutube.ru'

# 5. Полная сетка канала на день
curl --noproxy '*' -s -A "$UA" \
  'https://rutube.ru/pangolin/api/tv/tvprogram/<videoId>?client=tv&programDate=2026-11-09'

# 6. Онлайн-зрители live-канала
curl --noproxy '*' -s 'https://goya.rutube.ru/video/<videoId>/?online=1'
```

Для проверки HLS из ответа play options:

- live → `live_streams.hls[0].url`;
- VOD → `video_balancer.m3u8` (подписанный URL, играть как есть);
- если `video_balancer` пуст, а `live_streams` пуст — это iframe/платный/битый канал.

---

## 7. Параметры и «хитрости»

| Параметр / поле | Где | Смысл и нюанс |
|---|---|---|
| `client=tv` | `tvbroadcastinfo`, `tvprogram`, `annotation` | ТВ-режим API. Для `annotation` без него — `400`. |
| `tvprogramm=true` | `tvbroadcastinfo` | вернуть `schedule[]`. `false` — те же каналы без расписания (проверено). |
| `video_id` | `tvbroadcastinfo` | фильтр по одному каналу. `next` после такого запроса может указывать на **другой канал**, а не на страницу. |
| `category` | `tvbroadcastinfo` | API поддерживает, приложение **не передаёт**; категории считаются локально из `broadcast_category`. |
| `programDate` | `tvprogram` | `YYYY-MM-DD`. Таймзона не подтверждена (в доках — unknown). |
| `page` | `topic`, `category`, `search` | Номер страницы для списков. Для `tvbroadcastinfo` вручную не используется. |
| `next` | все пагинированные ответы | Непрозрачный абсолютный URL. Всегда следовать ему, не собирать `page=2` самостоятельно. |
| `no_404=true` | TV `play/options` | Глушит 404, но **не обходит** `blocking_rule`. |
| `pver=v2` | TV `play/options` | Версия play options; ответ больше (в нём есть нужные поля). |
| `referer=<url>` (query) | TV `play/options` | `https://tv-androidtv.rutube.ru`; отключает рекламное расписание. Это **не** HTTP-заголовок. |
| `client=wdp` | VOD `play/options` | Веб-клиент; вместе с `mq=all` открывает 2160p на VOD. |
| `mq=all` | VOD `play/options` | «Все качества». |
| `show_hidden_videos=False` | legacy live list | Скрытые видео не отдавать. |
| `streams=aN` | HLS-рендиция | Номер аудиодорожки; сопоставляется с `audiotracks[].track_index`. |
| `stream_type=broadcast` | карточка/play options | Маркер ТВ-канала; включает `isTV` и live-поведение. |
| `is_livestream` | карточка | Признак live. |
| `is_on_air` | карточка | Используется только для фильтра раздела «В ЭФИРЕ». |
| `origin_type=ifrm` | карточка | Внешний iframe-плеер; потока RuTube нет; отбраковывается. |
| `is_paid` | карточка | Может быть `false` даже у платного контента — см. 8.1. |
| `common_subscription_product_codes` | карточка | **Главный** признак платного контента. |
| `broadcast_category` | `tvbroadcastinfo` | Категории ТВ; id ≤ 1 отбрасывается. |
| `audiotracks[].track_index` | play options | Единственный источник имён/языков аудиодорожек для HLS. |
| `captions[].format` | play options | `srt`/`vtt`; другие форматы игнорируются. |

---

## 8. Фильтрация платного и неиграбельного контента

### 8.1. Правило

`RuTubeChannelFilter.isUnplayableContent(id, isPaid, subscriptionCodes, originType)`
(`RuTubeChannelFilter.java:71-77`):

```java
return isBadChannelId(id) ||
       Boolean.TRUE.equals(isPaid) ||
       subscriptionCodes != null && !subscriptionCodes.isEmpty() ||
       ORIGIN_IFRAME.equals(originType); // "ifrm"
```

Четыре независимых причины:

1. **ID в жёстком списке битых каналов** (`BAD_CHANNEL_IDS`, 13 штук);
2. **`is_paid == true`**;
3. **непустой `common_subscription_product_codes`**;
4. **`origin_type == "ifrm"`** — внешний iframe-плеер.

Ключевой нюанс из комментария к классу: у платного контента `is_paid` может быть
`false`, реальный маркер — `common_subscription_product_codes`
(`RuTubeChannelFilter.java:17-22`). Поэтому нельзя проверять только `is_paid`.

### 8.2. Жёсткий список битых каналов

`BAD_CHANNEL_IDS` (`RuTubeChannelFilter.java:32-50`) — 13 ID, проверенных вручную
(2026-10-29, мобильный UA), сгруппированы по причине:

| Причина | Каналы |
|---|---|
| `hls404` | Прямой эфир РБК-Уфа, Прямой эфир телеканала CTV |
| `empty_hls` (master есть, дочерний плейлист пуст) | Прямой эфир ИЛЛИ ТВ |
| `no_hls` | Домашний, Пятый канал, РЕН ТВ, СТС, ТВ Центр |
| `player_stub` | КИНОПРЕМЬЕРА, КИНОСВИДАНИЕ, КИНОСЕМЬЯ, Кинохит, Мужское кино |

Комментарий в коде: временный список, заменить удалённым, когда появится.

### 8.3. Где применяется

| Место | Что фильтруется | Код |
|---|---|---|
| `RuTubeMediaGroup.toMediaItems` | **все** карточки всех групп (главная, поиск, подписки, история, плейлисты) | `RuTubeMediaGroup.java:78-100` |
| `RuTubeContentService.filterTvChannels` | списки топиков; для LIVE дополнительно `is_on_air` | `RuTubeContentService.java:1151-1170` |
| `RuTubeEpgService.getChannelsCached` | все каналы `tvbroadcastinfo` | `RuTubeEpgService.java:154-156` |
| `RuTubeContentService.createHistoryGroup` | история | `RuTubeContentService.java:1398-1405` |
| `RuTubeContentService.createShowcaseCardGroup` | карточки витрин | `строки 1732-1736, 1776` |
| `CardEnricher.filterPlayable` | не кладёт платные/битые карточки в кэш | `CardEnricher.java:221-240` |
| `CardEnricher.toMediaItems` | кэшированные карточки | `CardEnricher.java:350-368` |
| `LocalSubscriptionsFeed.sanitize` | локальная лента подписок | `LocalSubscriptionsFeed.java:347-369` |
| `RuTubeContentService.localPlaylistItems` | превью локальных плейлистов | `RuTubeContentService.java:1476-1480` |

Для ТВ это означает:

- **раздел «ТВ»**: `getChannelsCached` уже выкинул платные/iframe/битые каналы;
- **раздел «В ЭФИРЕ»**: `fetchLive` дополнительно требует `is_on_air=true`;
- **тематические ряды**: `fetchTopicChannels` фильтрует платные, но **не** требует
  `is_on_air`;
- **детские**: объединённый список 7+15, тоже без требования эфира.

### 8.4. Чего фильтр не делает

- **Не проверяет `is_adult`.** Поле парсится в `RuTubeVideoItem` (`строки 37-38`),
  но в `RuTubeChannelFilter` не используется. 18+ контент не отсекается этим слоем.
- **Не проверяет `hide_comments`, `hide_chat`, `is_deleted`, `blocking_rule`.**
  `blocking_rule` обрабатывается только как retry в play options.
- **Не работает в плеере.** `RuTubeMediaItemFormatInfo.mIsUnplayable` и
  `mPlayabilityReason` нигде не присваиваются; `getPaidContentText()` всегда `null`.
  Поэтому если платный/iframe-канал открыть напрямую (например, из истории или
  сохранённой карточки), он не будет помечен как unplayable: плеер уйдёт в ветку
  «нет HLS» и будет перезагружать его каждые 30 секунд.
- **Не даёт purchase flow.** Комментарий в `RuTubeChannelFilter`: у приложения нет
  покупки подписок; платные элементы могут играть только у аккаунта с правом доступа.
  TV-ветка при этом вообще не отправляет Bearer, поэтому для платного live это
  заведомо не сработает.

---

## 9. Кэширование

| Что | TTL | Где |
|---|---|---|
| Список каналов `tvbroadcastinfo` | 30 минут | `RuTubeEpgService.CACHE_TTL_MS`, статическое поле `sChannels` |
| Программы на день `tvprogram` | 30 минут на `videoId_date` | `sProgramCache` |
| Первая страница фида (анонимная) | 20 минут | `RuTubeFeedLoader.FEED_CACHE_TTL_MS` |
| Мета TV-витрины (`getTvFeedCached`) | 10 минут | `RuTubeContentService.FEED_CACHE_TTL_MS` |
| Мета фида каталога | 20 минут | `FEED_META_CACHE_TTL_MS` |
| Первая страница ряда | 5 минут | `RuTubePageFetcher.ROW_CACHE_TTL_MS` |

Кэши in-memory: после перезапуска процесса список каналов и программы загружаются
заново. Пустой ответ не кэшируется (чтобы временная ошибка не залипла).

---

## 10. Что осталось от «ТВ Онлайн»

- Раздел «ТВ Онлайн» удалён; в `SidebarService` остались только константы
  `LEGACY_TV_ONLINE_ID_BASE=1000000` и `LEGACY_TV_ONLINE_ID_COUNT=100`
  для очистки старых сохранённых элементов сайдбара
  (`SidebarService.java:37-38, 323-326, 348-350`).
- При загрузке сохранённого состояния такие элементы удаляются, а если на них
  указывал `bootSectionId`, он сбрасывается на HOME (`строки 398-400`).
- Топики ТВ Онлайн (`/api/video/topic/{id}/`) остались и используются для
  «В ЭФИРЕ» и тематических рядов.
- `RuTubeSectionService` — это не ТВ Онлайн: он строит динамические секции из
  видео-категорий и рядов главного фида.

---

## 11. Риски, странности и что не проверено

1. **TV-ветка зависит от `mediaItem`.** `video.isTV && video.mediaItem != null`
   (`VideoLoaderController.java:268-270`). Если `Video` восстановлен без `mediaItem`,
   ТВ-канал уйдёт в обычную VOD-ветку с desktop UA и `client=wdp&mq=all`.
2. **Продолжения topic-списков идут через обычный API-клиент.** Первая страница
   «В ЭФИРЕ» — мобильный UA, продолжения — desktop UA. Если сервер начнёт гейтить
   страницы по UA, продолжения сломаются.
3. **`getGroup(reloadPageKey)` не применяет `requireOnAir`.** `RuTubeContentService.getGroup`
   грузит с `MediaGroup.TYPE_UNDEFINED` (`строки 355-361`), поэтому фильтр `is_on_air`
   там не срабатывает. Если ручное обновление пойдёт через этот путь, в «В ЭФИРЕ»
   могут появиться невещающие каналы. Основной путь обновления секции —
   `getLiveObserve`, где фильтр есть.
4. **`m3u8_m4s` не читается.** Модель `RuTubeVideoBalancer` содержит только
   `default` и `m3u8`. Если RuTube начнёт отдавать fMP4-вариант только в `m3u8_m4s`,
   приложение его не увидит.
5. **`mIsUnplayable` мёртв.** Ветка `formatInfo.isUnplayable()` в
   `VideoLoaderController` недостижима. Платный/iframe-контент, попавший в плеер
   в обход списков, будет циклически перезагружаться.
6. **iframe не поддерживается.** В `RuTubePlayOptions` нет `iframe_url`, `has_video`,
   `origin_type`; нативные TV/мобильные клиенты RuTube тоже не играют iframe
   (`docs/rutube-reverse-2026-10-27.md:1308-1320`). Фильтр прячет такие каналы
   из списков, но плеер их не умеет.
7. **Полная сетка на день не подключена.** `getEpgObserve`/`getProgramsForDayObserve`
   реализованы, но UI их не вызывает; используется только ближайшая сетка
   из `tvbroadcastinfo`.
8. **`programDate` и таймзона.** Точная таймзона `programDate` в `pangolin` не
   подтверждена (`docs/rutube-reverse-2026-10-27.md:1367-1369`). Для `tvbroadcastinfo`
   время приходит с явным смещением, для `tvprogram` — Unix-секунды.
9. **`category` в `tvbroadcastinfo` не используется.** API умеет фильтровать по
   категории, приложение группирует локально. Это не ошибка, но при переносе кода
   легко получить лишние запросы.
10. **Жёсткий список битых каналов устареет.** 13 ID проверены вручную 2026-10-29;
    список нужно периодически перепроверять или заменить удалённым.
11. **Платный контент определяется только по полям карточки.** В `RuTubeVideoInfo`
    нет `common_subscription_product_codes`; если карточка пришла из источника без
    этих полей, фильтр её не отсечёт.
12. **`is_adult` не фильтруется.** 18+ контент может попасть в списки, если RuTube
    вернёт его без `is_paid`/iframe.
13. **Master-манифест нестабилен.** RuTube случайно отдаёт то muxed-вариант, то
    демукс с 36 аудио-рендициями без `NAME`; парсер форка и кастомная фабрика
    это переживают, но полагаться на структуру нельзя
    (`docs/rutube-hls-urls-2026-11-07.md:94-110`).
14. **Подпись VOD-манифеста.** Голый `bl.rutube.ru/route/<id>.m3u8` всегда 403;
    играет только подписанный URL из `video_balancer.m3u8`. В логе
    `VideoLoaderController.stripQuery` показывает голый URL — это не значит, что
    в плеер уходит голый (`VideoLoaderController.java:324, 580-587`).
15. **`isStreamSeekable()` всегда `true`, а `isLiveDvrEnabled()` — `false`.**
    Для live это противоречиво; поведение seek зависит от UI/движка, а не от
    этого флага.
16. **Рекурсивный fallback `play/options`.** `fetchPlayOptions` при `404 blocking_rule`
    всегда вызывает TV-ветку, не проверяя, не является ли текущий вызов уже TV-веткой
    (`RuTubeMediaItemService.java:141-145`). Если TV-ветка тоже ответит
    `404 blocking_rule`, будет бесконечная рекурсия. `no_404=true` снижает
    вероятность, но не устраняет её.
17. **Пустые строки HLS не проверяются.** `RuTubeMediaItemFormatInfo.from` выбирает
    `m3u8`/`default`/`live_streams.hls[].url` по `!= null`, а не по непустоте
    (`строки 58-70, 142-156`). Пустая строка даст `containsHlsUrl() == true`
    и открытие пустого URL. В проверенных ответах поля либо заполнены, либо
    отсутствуют, но защита отсутствует.

---

## 12. Индекс кода

| Что | Файл:строки |
|---|---|
| TV-секция | `common/.../section/Section.java:39-41` |
| LIVE-секция | `common/.../section/Section.java:42-44` |
| `getTvChannelsWithEpgObserve` | `rutubeapi/.../RuTubeContentService.java:577-648` |
| `getTvCategoriesObserve` | `.../RuTubeContentService.java:688-730` |
| `fetchLive` | `.../RuTubeContentService.java:1573-1589` |
| `fetchTopicChannels` | `.../RuTubeContentService.java:1137-1145` |
| `fetchUniqueTopicChannels` | `.../RuTubeContentService.java:1099-1130` |
| `filterTvChannels` | `.../RuTubeContentService.java:1151-1170` |
| `createGroup` (next/reload/markTV) | `.../RuTubeContentService.java:1605-1627` |
| topic id-константы | `.../RuTubeContentService.java:80-91` |
| TV URL builders | `.../RuTubeContentService.java:1853-1859` |
| `getChannelsCached` | `rutubeapi/.../RuTubeEpgService.java:128-168` |
| `getProgramsObserve` | `.../RuTubeEpgService.java:57-78` |
| `getEpgObserve` / full day | `.../RuTubeEpgService.java:84-126, 170-207` |
| Модель канала | `rutubeapi/.../RuTubeBroadcastPage.java:12-32` |
| ISO-парсер программы | `rutubeapi/.../RuTubeProgram.java:11-52` |
| Unix-парсер программы | `rutubeapi/.../RuTubeTvProgram.java:22-30` |
| `fromTvChannel` | `rutubeapi/.../RuTubeMediaItem.java:236-264` |
| `setTvProgram` | `.../RuTubeMediaItem.java:266-271` |
| `fromTvCategory` | `.../RuTubeMediaItem.java:273-288` |
| `isTV`/`isLive` карточки | `.../RuTubeMediaItem.java:83-84, 119-120, 381-386` |
| TV play options | `rutubeapi/.../RuTubeMediaItemService.java:100-153` |
| выбор HLS | `rutubeapi/.../RuTubeMediaItemFormatInfo.java:40-97, 142-156` |
| субтитры/аудио | `.../RuTubeMediaItemFormatInfo.java:89-148` |
| флаги unplayable | `.../RuTubeMediaItemFormatInfo.java:37-38, 313-326` |
| загрузка формата | `common/.../VideoLoaderController.java:255-279` |
| выбор HLS/unplayable/reload | `.../VideoLoaderController.java:281-341` |
| ExoPlayer HLS | `common/.../ExoMediaSourceFactory.java:160-171, 189-214` |
| HLS дедуп/аудио | `common/.../RuTubeHlsPlaylistParserFactory.java:56-170` |
| HLS-парсер без NAME | `exoplayer-amzn-2.10.6/.../HlsPlaylistParser.java:392-396` |
| UA/Referer | `rutubeapi/.../DefaultHeaders.java:4-8` |
| два клиента/заголовки | `rutubeapi/.../RetrofitHelper.java:30-73` |
| TV play options endpoint | `rutubeapi/.../RuTubeEndpoints.java:14-28` |
| tvbroadcastinfo/tvprogram | `.../RuTubeEndpoints.java:63-64, 91` |
| topic/live/chat/goya | `.../RuTubeEndpoints.java:86-91` |
| TV shows/metainfo и прочие TV-эндпоинты | `rutubeapi/.../RuTubeApi.java:145-232, 345-388` |
| фильтр платного | `rutubeapi/.../RuTubeChannelFilter.java:26-89` |
| фильтр в группе | `rutubeapi/.../RuTubeMediaGroup.java:78-100` |
| фильтр в кэше карточек | `rutubeapi/.../CardEnricher.java:221-240, 350-368` |
| legacy TV Online | `common/.../SidebarService.java:37-38, 323-326, 348-350, 398-400` |
| UI-сетка ТВ | `smarttubetv/.../TvChannelPageFragment.java:70-421` |
| EPG-кнопка | `common/.../EpgController.java:43-116` |
| live-зрители | `common/.../LiveViewersController.java:66-99` |
| live-чат | `rutubeapi/.../RuTubeLiveChatService.java:21-56` |
| watchtime live-skip | `rutubeapi/.../RuTubeMediaItemService.java:339-341, 400-403` |

---

## 13. Источники

**Код SmartRuTube** (пути выше).

**Документы репозитория:**

- `docs/rutube-reverse-2026-10-27.md` — живые ответы API, `tvbroadcastinfo`,
  `tvprogram`, топики, iframe, `blocking_rule`, прокси.
- `docs/rutube-hls-urls-2026-11-07.md` — HLS, подпись VOD, master-манифест,
  аудиодорожки, `TRACK_INDEX`, UA/параметры.
- `docs/rutube-uwp-reverse-2026-10-30.md` — UA-гейт `play/options`,
  `no_404` не обходит блокировку.
- `docs/rearutube-reference-2026-10-30.md` — TV-режим play options,
  `advert: []` с `pver=v2&referer=...`.
- `docs/rutube-mobile-tvonline-2026-10-29.md` — EPG в мобильном RuTube.
- `docs/rutube-official-php-client-2026-10-30.md` — `referer` как официальный
  query-параметр play options.

**Проверенные сырые ответы:** `C:\Work\tools\noproxy_play_*.json`,
`noproxy_epg_*.json`, `noproxy_chat_*.json`, `noproxy_goya_online_*.json`.
