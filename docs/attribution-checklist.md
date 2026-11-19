# Чек-лист атрибуции: что ещё нужно изменить перед публикацией

**Статус (19.11.2026): подготовлено к публикации; visibility переключается в последний момент.**
Сверено с историей до коммита `12dd169` включительно. После
`9562122` (атрибуция и лицензии) прошли `baa3d2b` (en/ru), `4a962f9` (удалён EPG-матчинг),
`0d1bc0a` (AGENTS.md: ветки без прямого указания запрещены, работа в `main`), `cf8b0b5` (удалены
TMDB/OpenSubtitles), `ec31209` (удалены VOD, трейлеры, загрузки, настройки, зависимость
android-youtube-player), `fb83343` (синхронизация юридических документов), `67b511f` (слаги
категорий RuTube), `fb700ad` (русификация документации, чистка CI) и `12dd169` (пересборка
бренд-ассетов). **Вся история `main` запушена в `origin` (`dimzon/moe-tv`).** Юридические документы
после них обновлены:
[`NOTICE`](../NOTICE), [`THIRD_PARTY_LICENSES.md`](../THIRD_PARTY_LICENSES.md),
[`LICENSES/Apache-2.0.txt`](../LICENSES/Apache-2.0.txt), [`README.md`](../README.md),
[`CONTRIBUTING.md`](../CONTRIBUTING.md), [`CHANGELOG.md`](../CHANGELOG.md).

`LICENSE` оставлен **нетронутым** — это дословный текст GPLv3, менять его нельзя.

**Код приложения и строковые ресурсы намеренно НЕ изменялись** — разделы A и B ниже описывают
правки, которые ещё предстоит сделать в исходниках. Исключение: при починке CI (C11) в
`LocalSyncScreen.kt` добавлены два `overflow = TextOverflow.Ellipsis`. Конфигурация репозитория
приведена в порядок: `.github/` (воркфлоу, issue/PR-шаблоны, Dependabot),
`extras/USER_GUIDE.md` и `.gitignore` — подробности в разделе C. Пользовательская и
контрибьюторская документация (`README.md`, `CONTRIBUTING.md`, шаблоны, гайд) переведена на
русский (C10). Ниже зафиксирован полный список унаследованных от апстрима ссылок и
формулировок, которые создают ложное впечатление, что это оригинальный OwnTV, а также
юридические пункты, требующие реализации в приложении. Публичный репозиторий форка —
<https://github.com/dimzon/moe-tv>, ветка `main`, `origin` настроен по SSH. Все ссылки в шаблонах
и юридических документах указывают на этот адрес; он же является Corresponding Source по GPLv3 §6.
Репозиторий переключается в public в последний момент перед публикацией APK.

> Номера строк в разделах A и B — **на коммит `ec31209`**; при новой правке кода их надо
> пересчитать. В разделе C ссылки на `.github/` и `.gitignore` обновлены после правки
> конфигурации.

---

## A. Обязательно до любой публикации APK

| # | Где | Сейчас | Почему это проблема | Что сделать |
|---|---|---|---|---|
| A1 | `app/src/main/java/tv/own/owntv/core/CoreBuildInfo.kt:70` **и** `app/src/main/java/tv/own/myowntv/MyOwnTVApp.kt:150` | Значение по умолчанию и явная установка `releaseRepo = "ahXN00/OwnTV"` | `UpdateManager.kt:103` запрашивает `api.github.com/repos/<releaseRepo>/releases/latest`, т.е. встроенный обновляльщик предлагает **релизы апстрима**. Помимо ложной атрибуции это поломка: у апстрима другой `applicationId`, установщик откажет. Правка в одном `CoreBuildInfo.kt` недостаточна — хост-приложение перезаписывает поле. | **Закрыто.** В обоих местах прописан канал релизов **`dimzon/moe-tv-site`** (публичный site-репозиторий, откуда обновляльщик читает latest release). Corresponding Source — `dimzon/moe-tv`. |
| A2 | `app/src/main/java/tv/own/myowntv/features/shell/components/SettingsScreen.kt:1680` | `GITHUB_REPO = "github.com/ahXN00/OwnTV"` (используется в `MorePages.kt:621`) | Экран More › About показывает **чужой репозиторий** как «репозиторий проекта». Для GPLv3 §6 это ещё и неверный адрес Corresponding Source. | Заменить на URL форка; до публикации — строку вида `Source: см. NOTICE` / скрыть поле. |
| A3 | `app/src/main/java/tv/own/myowntv/features/more/MorePages.kt:555–631` (`AboutPage`, `more_about_license` на `:589`) | Показывает версию, «GPLv3 · free and open source», описание, QR и ссылку на репозиторий | Нет «Appropriate Legal Notices»: нет копирайта, нет указания «модифицированная версия» с датой, нет отказа от гарантий, нет способа просмотреть текст лицензии. Строго по §5(d) это требуется, если их показывал оригинал (у апстрима была только короткая подпись), но для однозначного compliance и добросовестной атрибуции — необходимо. | Добавить на экран: `© 2026 Dmitry Alexandrov · модификация OwnTV (c 09.11.2026) · GPL-3.0-only · БЕЗ ГАРАНТИЙ · LICENSE / NOTICE / THIRD_PARTY_LICENSES.md`. Текст брать из ресурсов (см. B4), не хардкодить. |
| A4 | `values/strings_settings.xml:2177`, `:2179`, `:2181`, `:552`; `values-ru/strings_settings.xml:1189`, `:1190`, `:1191`, `:251` | «GPLv3 · free and open source»; «Your own IPTV player … you bring your own legally accessible sources» (есть ещё `_mobile`-вариант `:2181`/`:1191`); «Contributions … on GitHub» | Описание осталось от апстрима и **неверно по сути**: приложение больше не «bring your own source», оно работает с публичным каталогом RuTube. Ссылка на GitHub ведёт не туда. | Обновить `more_about_license`, `more_about_description`, `more_about_description_mobile`, `settings_contributions`; добавить новые ключи (B4). `values-ru/` обновлять вместе с `values/`. |
| A5 | Релиз / дистрибуция APK | В репозитории лежат `LICENSE`, `NOTICE`, `THIRD_PARTY_LICENSES.md`, `LICENSES/Apache-2.0.txt`, 6 OFL-файлов | При распространении **только APK** эти файлы должны сопровождать сборку (GPLv3 §§4/6, Apache-2.0 §4, OFL §1). Сейчас в `app/src/main/assets/` лежит только `channel_renames.json`. | Либо положить тексты лицензий в assets и показывать их в About, либо прикладывать архив с исходниками к каждому релизу, либо дать письменную оферту на исходники сроком ≥3 лет (§6b). |
| A6 | `README.md` (раздел Installing) | Формулировка «Download the APK from this repository's Releases» уже снабжена оговоркой, что публичного репозитория нет | Оговорка снимает ложное обещание, но реального источника APK по-прежнему нет. | **Закрыто.** В README Installing даны реальные ссылки на страницу загрузки и релизы `dimzon/moe-tv-site`. |

---

## B. Атрибуция и тексты приложения

| # | Где | Сейчас | Что сделать |
|---|---|---|---|
| B1 | `SettingsScreen.kt:1681` (`t.me/owntvplayer`, выводится в `MorePages.kt:609`) | Чужое сообщество апстрима; `app/src/main/res/drawable/telegram_qr.jpg` всё ещё в APK | Заменить на свой канал или убрать QR-карточку вместе с рисунком. |
| B2 | `app/src/main/java/tv/own/owntv/core/i18n/SupportedLocales.kt:45` | `https://github.com/ahXN00/OwnTV/issues/new?...` — ссылка «попросить новый язык» ведёт в issue-трекер апстрима | Заменить или убрать. Актуальна в связи с `MorePages.kt:623–624` (счётчик языков = 4). |
| B3 | `app/src/main/java/tv/own/owntv/core/util/CrashUploader.kt:71` | `Referer: https://github.com/ahXN00/OwnTV` | Заменить на адрес форка / убрать. |
| B4 | Новые строковые ресурсы | — | Добавить ключи, напр. `more_about_copyright`, `more_about_modified`, `more_about_no_warranty`, `more_about_licenses_link`, `more_about_upstream`. **Важно:** видимый пользователю текст нельзя хардкодить в Kotlin — сработает гейт `app/build.gradle.kts:338, :359, :369` (`check_hardcoded_strings.py` висит на `preBuild`). Добавлять в `values/strings_settings.xml` и все локализованные `values-*/strings_settings.xml` — сейчас упаковываются `en`, `ru`, `uk`, `be`; непереведённые ключи унаследуют fallback из `values/` (`MissingTranslation` отключён). |
| B5 | `extras/worker/`, `drawable/ic_tmdb_logo.xml`, `drawable/ic_opensubtitles_logo.xml`, `extras/partner-logos/tmdb_logo.svg`, `opensubtitles_logo.svg` | Удалены в `cf8b0b5` вместе с интеграциями, ключами и настройками; активные дисклеймеры заменены историческими в `NOTICE` §5, `README.md`, `CONTRIBUTING.md`, `THIRD_PARTY_LICENSES.md`. В `extras/partner-logos/` остался только `Weblate_logo.svg` | **Закрыто.** См. B6 — исторические упоминания трогать не надо. |
| B6 | `extras/screenshots/` (10 PNG) и `CHANGELOG.md` | Скриншоты апстрима изображают удалённые функции (Downloads, Movies, Series, Playlist, Profile Selection, Settings › Profiles); история релизов до v5.2.1 — апстримовская | Не выдавать за материалы форка. Провенанс зафиксирован в `THIRD_PARTY_LICENSES.md` §3 и в шапке `CHANGELOG.md`; в `CHANGELOG.md` добавлен отдельный раздел «Fork changes — unreleased», а апстримовский «Unreleased» переименован в «Upstream snapshot notes». При первом собственном релизе отделить/архивировать историю апстрима. |
| B7 | `app/src/main/res/values/strings_content.xml:97`, `strings_player.xml:56`, `strings_settings.xml:459` и др. | После `ec31209` в `values/*.xml` осталось ~69 упоминаний VOD/catch-up/movie/series (часть — комментарии переводчикам, но есть и живые строки `content_catchup`, `player_tool_catchup`, `settings_catchup`) | Проверить, не попадают ли они на видимые экраны live-режима; при чистке не забыть `values-ru/` и `tools/i18n/`-базовые линии. Юридически нейтрально, но вводит в заблуждение. |

---

## C. Репозиторий, сборка и гигиена (не менялось, но требует правки)

| # | Где | Сейчас | Что сделать |
|---|---|---|---|
| C1 | `.github/ISSUE_TEMPLATE/*`, `.github/PULL_REQUEST_TEMPLATE.md`, `extras/USER_GUIDE.md` | Ссылки на чужой проект `opentvproject/opentv` и сообщество `t.me/owntvplayer`, вопросы про Xtream/M3U/плейлисты/VOD в баг-репорте, 874 строки гайда про удалённые функции | **Закрыто.** Все ссылки ведут на `github.com/dimzon/moe-tv/blob/main/extras/USER_GUIDE.md`, контакт апстрима удалён. Формы переписаны под live-only RuTube: вместо «типа плейлиста» спрашивают канал/программу, версию приложения и устройство. В `extras/USER_GUIDE.md` добавлен актуальный раздел форка, а унаследованный текст явно помечен как исторический архив. Тексты переведены на русский (C10); needles `pr-checklist.yml` (`проверил **Настройки**`, `искал существующие issues/PR`) обновлены синхронно с PR-шаблоном. |
| C2 | `.github/workflows/android.yml` | Имена артефактов и заголовок релиза от апстрима (`OwnTV-v…`, `OwnTV <tag>`) | **Закрыто.** `MyOwnTV-v<version>.apk`, `MyOwnTV-dev-<sha>.apk`, стабильные `MyOwnTV.apk` и `MyOwnTV-x86_64-v<version>.apk`, заголовок релиза `MyOwnTV <tag>`. Заодно `files:` у релиза заменён на `dist/*.apk`: раньше на debug-сборке без keystore он ссылался на несуществующий x86_64-файл. |
| C3 | `settings.gradle.kts:25` | `rootProject.name = "MyOwnTV"` | Приложение переименовано в МОЁ ТВ (`applicationId com.github.dimzon.owntv`), имя проекта осталось старым. Косметика, но влияет на названия артефактов. |
| C4 | `git remote -v` | `origin git@github.com:dimzon/moe-tv.git` — приватный репозиторий, код **запушен** в `main` 13.11.2026, `origin/main` существует | **Закрыто.** `owner/repo` — **`dimzon/moe-tv`**; URL подставлен в `NOTICE` §4, `README.md`, `CONTRIBUTING.md` и этом чек-листе. visibility переключается в последний момент; вместе с этим становятся доступны branch protection и rulesets. Канал релизов — `dimzon/moe-tv-site`. |
| C5 | Заголовки исходников | SPDX-заголовков нет ни у апстрима, ни у форка | Для новых файлов желательно `// SPDX-License-Identifier: GPL-3.0-only`. Массово переписывать унаследованные файлы не обязательно и не рекомендуется: достаточно `NOTICE` + `README` + `CHANGELOG` (§5(a)/(b)). |
| C6 | `.gitignore`; `extras/branding/__pycache__/*.pyc` (3 файла) | Комментарий про несуществующий `extras/worker/` (удалён в `cf8b0b5`), три бинарных `.pyc` (~100 КБ) в индексе, локальный журнал агента не игнорировался | **Закрыто.** `.workbuddy-ai/`, `.p1contract/`, `.p2contract/`, `PHASE*_CONTRACT.md`, `__pycache__/`, `*.pyc` добавлены в `.gitignore` (проверено `git check-ignore -v`); комментарий про `/worker/` переписан; три `.pyc` сняты с отслеживания `git rm --cached` (на диске остались). |
| C7 | `app/build.gradle.kts:450` | `implementation(libs.juniversalchardet)` объявлен, но ни один исходник его не импортирует после `ec31209` | **Закрыто.** Зависимость удалена из `build.gradle.kts`, `gradle/libs.versions.toml` и `THIRD_PARTY_LICENSES.md`; упоминания в `docs/rutube.md` и этом чек-листе обновлены. |
| C8 | `AGENTS.md:30–33` ↔ `CONTRIBUTING.md` | В `AGENTS.md` ветки без прямого указания запрещены, работа в `main`; `CONTRIBUTING.md` до этой правки предписывал `git checkout -b` и fork-and-PR | Приведено в соответствие: `CONTRIBUTING.md` теперь описывает коммиты в `main` и отсылает к `AGENTS.md`. Новые ветки не создавать. |
| C9 | `.github/workflows/android.yml`, `i18n.yml`, `pr-checklist.yml`, `automerge-core-bump.yml`, `dependabot.yml` | Мёртвая логика внешнего `OwnTV_Core`: job `verify-pin`, исключения `bump/core-*`, секреты `OWNTV_EDGE_*`, комментарии про Maven-репозиторий и mpv; workflow, автоматически вливавший PR | **Закрыто.** Внешнего пина нет (`gradle/libs.versions.toml` не содержит `owntvCore`, исходники ядра лежат в `app/src/main/java/tv/own/owntv/**`), `OWNTV_EDGE_KEY` больше не читается `app/build.gradle.kts`. `verify-pin`, все исключения `bump/core-*` и `automerge-core-bump.yml` удалены; `check`/`build` и i18n-проверки работают без исключений. В `i18n.yml` добавлен `chmod +x ./gradlew` — в индексе `gradlew` имеет mode `100644`, без этого job `pseudo` падал бы. |
| C10 | `README.md`, `CONTRIBUTING.md`, `extras/USER_GUIDE.md`, `.github/ISSUE_TEMPLATE/*`, `PULL_REQUEST_TEMPLATE.md`, `pr-checklist.yml`, `dependabot.yml` | Вся пользовательская и контрибьюторская документация была по-английски, хотя продукт и аудитория русскоязычные | **Закрыто.** Переведено на русский; правило зафиксировано в `CONTRIBUTING.md`. Намеренно оставлены в оригинале: `LICENSE` (дословный GPLv3), `NOTICE`, `THIRD_PARTY_LICENSES.md`, `LICENSES/*` (юридические тексты), исторический `CHANGELOG.md` (парсеры release notes ищут `## <тег>`), английские комментарии в CI и 874 строки архивного апстримового гайда. Якоря README (`#origins`, `#features`, `#install`, `#tech`, `#build`, `#legal`, `#license`) заданы тегами `<a id=...>`, поэтому не зависят от языка заголовков; needles `pr-checklist.yml` переведены синхронно с PR-шаблоном. |
| C11 | `.github/workflows/i18n.yml`, `tools/i18n/number_format_allowlist.txt`, `app/src/main/java/tv/own/myowntv/features/settings/LocalSyncScreen.kt` | Первый же запуск CI после пуша (`12dd169`) упал в job `validate`: три «stale number-format allowlist entry» — записи от импорта `214719f` на переехавшие в `tv/own/myowntv/**` и удалённые (`VodStage.kt` в `ec31209`) файлы. Следующий шаг `check_text_overflow.py` тоже был бы красным: два `Text(maxLines = 1)` без `overflow` в `LocalSyncScreen.kt` (из `ce8f53e`) | **Закрыто.** Мёртвые записи удалены (актуальный `%.1f` в `LivePreviewEngine.kt` использует `Locale.ROOT` и разрешения не требует), в оба `Text` добавлен `overflow = TextOverflow.Ellipsis`. Локально зелёные: `check_number_locale.py` (498 файлов), `check_text_overflow.py` (498 файлов), `check_hardcoded_strings.py verify --bootstrap` (283 + 2585). Это **не регрессия `12dd169`**: до пуша CI вообще никогда не запускался, а `[skip ci]` на `fb700ad` отложил первый запуск до следующего коммита. Исправление закоммичено в `main`; чтобы `origin` стал зелёным, остался пуш. |
| C12 | `.github/workflows/android.yml` (job `build`, `concurrency`) | После пуша `da55bfa` job `check` был **success**, но `build` снова `failure`: шаг `Build APK` оборван на ~11-й минуте сообщением `The operation was canceled.` — без единой ошибки компиляции (в логе только Kotlin-warning'и). Таймаутов в workflow не было, новых пушей в момент отмены не было; первый такой run (`12dd169`) отменил сам пуш `da55bfa` через `cancel-in-progress: true` | **Закрыто.** Тяжёлый `assembleDebug` больше не гоняется на каждый push в `main`: `build` работает только на тегах `v*` и `workflow_dispatch`, на обычных пушах/PR гейтом остаётся `check`. `cancel-in-progress` включён только для PR, добавлен явный `timeout-minutes: 30`. Dev-APK теперь собирается вручную через Actions → *Run workflow*; релизный процесс на тегах не изменился. Проверка `check` на `da55bfa` уже была зелёной, поэтому новый запуск CI должен закрыться успехом без 11-минутной сборки. |

**Настройки репозитория (`gh api repos/dimzon/moe-tv`, 19.11.2026).** `default_branch: main`,
`has_issues: true`, `has_projects: true`, `has_wiki: false`, `has_discussions: false`, все три
способа слияния разрешены, `allow_auto_merge: false`, `delete_branch_on_merge: false`,
`web_commit_signoff_required: false`. Actions включены (`allowed_actions: all`). Заведены четыре
секрета подписи: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — релизный
тег собирает подписанный universal APK. `private: true` меняется на `false` в момент публикации;
вместе с этим становятся доступны branch protection и rulesets. Все теги экшенов в воркфлоу
существуют: `checkout@v7`, `setup-java@v6`, `setup-python@v7`, `upload-artifact@v7`,
`github-script@v9`, `setup-android@v4`, `setup-gradle@v6`, `action-gh-release@v3`.

---

## D. Что уже закрыто (для справки)

* `LICENSE` — дословный GPLv3, не изменён. Версия зафиксирована как **GPL-3.0-only**: у апстрима нет оговорки «or later», значит добавлять её к производной работе нельзя.
* §5(a) — явное уведомление «THIS IS A SUBSTANTIALLY MODIFIED VERSION OF OwnTV» с датой 09.11.2026 и развёрнутым перечнем изменений (включая `cf8b0b5`, `4a962f9`, `ec31209`, `baa3d2b`): `NOTICE` §2, `README.md` (Origins & attribution), `CHANGELOG.md` (шапка + раздел «Fork changes»).
* §5(b)/(c) — заявление, что **вся** производная работа лицензирована под GPL-3.0-only: `NOTICE` §3.
* §4 — сохранены кредиты апстрима: @m3th0d93 (wordmark, issue #227), переводчики и Weblate, исторические упоминания TMDB/OpenSubtitles, mpv/libmpv/FFmpeg и android-youtube-player. Активные дисклеймеры удалённых сервисов заменены историческими.
* Атрибуция `OwnTV_Core` (GPL-3.0-only) как второго источника кода и строк: `NOTICE` §1. Апстрим поставлял 26 переводов; **форк упаковывает четыре языка: `en`, `ru`, `uk`, `be`** — во всех документах это различие оговорено.
* Лицензии зависимостей сведены в `THIRD_PARTY_LICENSES.md`: Apache-2.0 (полный текст — `LICENSES/Apache-2.0.txt`), OFL-1.1 для 6 семейств шрифтов (11 `.ttf`), ISRG Root X2, логотип Weblate. Неиспользуемый `juniversalchardet` удалён из зависимостей и перечня лицензий (C7). Исправлена нумерация разделов (был пропущен §5), добавлен исторический раздел об удалённых компонентах.
* `README.md` — исправлены фактические ошибки: `applicationId com.github.dimzon.owntv` (а не `tv.own.myowntv`, это namespace), удалены профили из списка возможностей, убрано утверждение, что reference Worker code остаётся в дереве, обновлён layout `features/`; в Installing даны ссылки на страницу загрузки и релизы.
* `CONTRIBUTING.md` — workflow приведён к `AGENTS.md`, убрано упоминание действующего MIT-уведомления, добавлены правила «удаляешь интеграцию — чисти документы» и «зависимость добавил/обновил/удалил — обнови перечень лицензий».
* `CHANGELOG.md` — добавлен собственный лог форка, апстримовский «Unreleased» явно переименован в «Upstream snapshot notes». Парсеры `.github/workflows/android.yml` и `release-notes.yml` ищут заголовок `## <тег>` до следующего `## v…`, поэтому правка заголовков на извлечение release notes не влияет.

**Совместимость.** Из упаковываемых компонентов остались Apache-2.0 и OFL-1.1 — обе совместимы с GPLv3; MIT-компонентов больше нет (`android-youtube-player` удалён в `ec31209`, MIT-текст убран). Несовместимых лицензий в упаковываемых компонентах нет: `juniversalchardet` (MPL-1.1 / GPL-3.0 / LGPL-3.0) удалён вместе с неиспользуемой зависимостью (C7). JUnit (EPL-1.0) и `org.json` (JSON License) используются **только в тестах** и в APK не поставляются, поэтому требование совместимости к ним не применяется; в `THIRD_PARTY_LICENSES.md` это разделено явным образом. Конфликтующих лицензий в зависимостях не обнаружено; mpv/FFmpeg (LGPL/GPL-стек) из сборки удалён.
