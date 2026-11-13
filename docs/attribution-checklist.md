# Чек-лист атрибуции: что ещё нужно изменить перед публикацией

**Статус (13.11.2026).** Сверено с историей до коммита `ec31209` (17:59) включительно. После
`9562122` (атрибуция и лицензии) прошли `baa3d2b` (en/ru), `4a962f9` (удалён EPG-матчинг),
`0d1bc0a` (AGENTS.md: ветки без прямого указания запрещены, работа в `main`), `cf8b0b5` (удалены
TMDB/OpenSubtitles) и `ec31209` (удалены VOD, трейлеры, загрузки, настройки, зависимость
android-youtube-player). **Все пять коммитов уже в `main`.** Юридические документы после них
обновлены: [`NOTICE`](../NOTICE), [`THIRD_PARTY_LICENSES.md`](../THIRD_PARTY_LICENSES.md),
[`LICENSES/Apache-2.0.txt`](../LICENSES/Apache-2.0.txt), [`README.md`](../README.md),
[`CONTRIBUTING.md`](../CONTRIBUTING.md), [`CHANGELOG.md`](../CHANGELOG.md).

`LICENSE` оставлен **нетронутым** — это дословный текст GPLv3, менять его нельзя.

**По решению владельца проекта код, ресурсы и CI намеренно НЕ изменялись.** Ниже зафиксирован
полный список унаследованных от апстрима ссылок и формулировок, которые всё ещё создают ложное
впечатление, что это оригинальный OwnTV, а также юридические пункты, требующие реализации в
приложении. Публичного репозитория у форка пока нет: 13.11.2026 через `gh` создан **приватный**
`github.com/dimzon/moe-tv`, `origin` настроен по SSH, код ещё **не запушен**. Поэтому в
документах сохранены нейтральные формулировки («дерево исходников, из которого получена
копия»). Приватный репозиторий **не заменяет** публичный Corresponding Source при
распространении APK (GPLv3 §6).

> Все номера строк ниже — **на коммит `ec31209`**. При новой правке кода их надо пересчитать.

---

## A. Обязательно до любой публикации APK

| # | Где | Сейчас | Почему это проблема | Что сделать |
|---|---|---|---|---|
| A1 | `app/src/main/java/tv/own/owntv/core/CoreBuildInfo.kt:70` **и** `app/src/main/java/tv/own/myowntv/MyOwnTVApp.kt:150` | Значение по умолчанию и явная установка `releaseRepo = "ahXN00/OwnTV"` | `UpdateManager.kt:103` запрашивает `api.github.com/repos/<releaseRepo>/releases/latest`, т.е. встроенный обновляльщик предлагает **релизы апстрима**. Помимо ложной атрибуции это поломка: у апстрима другой `applicationId`, установщик откажет. Правка в одном `CoreBuildInfo.kt` недостаточна — хост-приложение перезаписывает поле. | Прописать **`dimzon/moe-tv`** в **обоих** местах, но только после публикации репозитория: `api.github.com` не отдаёт релизы приватного репозитория. Пока приватный — отключить проверку обновлений или оставить нейтральную заглушку. |
| A2 | `app/src/main/java/tv/own/myowntv/features/shell/components/SettingsScreen.kt:1680` | `GITHUB_REPO = "github.com/ahXN00/OwnTV"` (используется в `MorePages.kt:621`) | Экран More › About показывает **чужой репозиторий** как «репозиторий проекта». Для GPLv3 §6 это ещё и неверный адрес Corresponding Source. | Заменить на URL форка; до публикации — строку вида `Source: см. NOTICE` / скрыть поле. |
| A3 | `app/src/main/java/tv/own/myowntv/features/more/MorePages.kt:555–631` (`AboutPage`, `more_about_license` на `:589`) | Показывает версию, «GPLv3 · free and open source», описание, QR и ссылку на репозиторий | Нет «Appropriate Legal Notices»: нет копирайта, нет указания «модифицированная версия» с датой, нет отказа от гарантий, нет способа просмотреть текст лицензии. Строго по §5(d) это требуется, если их показывал оригинал (у апстрима была только короткая подпись), но для однозначного compliance и добросовестной атрибуции — необходимо. | Добавить на экран: `© 2026 Dmitry Alexandrov · модификация OwnTV (c 09.11.2026) · GPL-3.0-only · БЕЗ ГАРАНТИЙ · LICENSE / NOTICE / THIRD_PARTY_LICENSES.md`. Текст брать из ресурсов (см. B4), не хардкодить. |
| A4 | `values/strings_settings.xml:2177`, `:2179`, `:2181`, `:552`; `values-ru/strings_settings.xml:1189`, `:1190`, `:1191`, `:251` | «GPLv3 · free and open source»; «Your own IPTV player … you bring your own legally accessible sources» (есть ещё `_mobile`-вариант `:2181`/`:1191`); «Contributions … on GitHub» | Описание осталось от апстрима и **неверно по сути**: приложение больше не «bring your own source», оно работает с публичным каталогом RuTube. Ссылка на GitHub ведёт не туда. | Обновить `more_about_license`, `more_about_description`, `more_about_description_mobile`, `settings_contributions`; добавить новые ключи (B4). `values-ru/` обновлять вместе с `values/`. |
| A5 | Релиз / дистрибуция APK | В репозитории лежат `LICENSE`, `NOTICE`, `THIRD_PARTY_LICENSES.md`, `LICENSES/Apache-2.0.txt`, 6 OFL-файлов | При распространении **только APK** эти файлы должны сопровождать сборку (GPLv3 §§4/6, Apache-2.0 §4, OFL §1). Сейчас в `app/src/main/assets/` лежит только `channel_renames.json`. | Либо положить тексты лицензий в assets и показывать их в About, либо прикладывать архив с исходниками к каждому релизу, либо дать письменную оферту на исходники сроком ≥3 лет (§6b). |
| A6 | `README.md` (раздел Installing) | Формулировка «Download the APK from this repository's Releases» уже снабжена оговоркой, что публичного репозитория нет | Оговорка снимает ложное обещание, но реального источника APK по-прежнему нет. | После появления репозитория заменить на реальную ссылку. |

---

## B. Атрибуция и тексты приложения

| # | Где | Сейчас | Что сделать |
|---|---|---|---|
| B1 | `SettingsScreen.kt:1681` (`t.me/owntvplayer`, выводится в `MorePages.kt:609`) | Чужое сообщество апстрима; `app/src/main/res/drawable/telegram_qr.jpg` всё ещё в APK | Заменить на свой канал или убрать QR-карточку вместе с рисунком. |
| B2 | `app/src/main/java/tv/own/owntv/core/i18n/SupportedLocales.kt:45` | `https://github.com/ahXN00/OwnTV/issues/new?...` — ссылка «попросить новый язык» ведёт в issue-трекер апстрима | Заменить или убрать. Актуальна в связи с `MorePages.kt:623–624` (счётчик языков = 2). |
| B3 | `app/src/main/java/tv/own/owntv/core/util/CrashUploader.kt:71` | `Referer: https://github.com/ahXN00/OwnTV` | Заменить на адрес форка / убрать. |
| B4 | Новые строковые ресурсы | — | Добавить ключи, напр. `more_about_copyright`, `more_about_modified`, `more_about_no_warranty`, `more_about_licenses_link`, `more_about_upstream`. **Важно:** видимый пользователю текст нельзя хардкодить в Kotlin — сработает гейт `app/build.gradle.kts:338, :359, :369` (`check_hardcoded_strings.py` висит на `preBuild`). Добавлять в `values/strings_settings.xml` и `values-ru/strings_settings.xml` — сейчас упаковываются только `en` и `ru`; непереведённые ключи унаследуют fallback из `values/` (`MissingTranslation` отключён). |
| B5 | `extras/worker/`, `drawable/ic_tmdb_logo.xml`, `drawable/ic_opensubtitles_logo.xml`, `extras/partner-logos/tmdb_logo.svg`, `opensubtitles_logo.svg` | Удалены в `cf8b0b5` вместе с интеграциями, ключами и настройками; активные дисклеймеры заменены историческими в `NOTICE` §5, `README.md`, `CONTRIBUTING.md`, `THIRD_PARTY_LICENSES.md`. В `extras/partner-logos/` остался только `Weblate_logo.svg` | **Закрыто.** См. B6 — исторические упоминания трогать не надо. |
| B6 | `extras/screenshots/` (10 PNG) и `CHANGELOG.md` | Скриншоты апстрима изображают удалённые функции (Downloads, Movies, Series, Playlist, Profile Selection, Settings › Profiles); история релизов до v5.2.1 — апстримовская | Не выдавать за материалы форка. Провенанс зафиксирован в `THIRD_PARTY_LICENSES.md` §3 и в шапке `CHANGELOG.md`; в `CHANGELOG.md` добавлен отдельный раздел «Fork changes — unreleased», а апстримовский «Unreleased» переименован в «Upstream snapshot notes». При первом собственном релизе отделить/архивировать историю апстрима. |
| B7 | `app/src/main/res/values/strings_content.xml:97`, `strings_player.xml:56`, `strings_settings.xml:459` и др. | После `ec31209` в `values/*.xml` осталось ~69 упоминаний VOD/catch-up/movie/series (часть — комментарии переводчикам, но есть и живые строки `content_catchup`, `player_tool_catchup`, `settings_catchup`) | Проверить, не попадают ли они на видимые экраны live-режима; при чистке не забыть `values-ru/` и `tools/i18n/`-базовые линии. Юридически нейтрально, но вводит в заблуждение. |

---

## C. Репозиторий, сборка и гигиена (не менялось, но требует правки)

| # | Где | Сейчас | Что сделать |
|---|---|---|---|
| C1 | `.github/ISSUE_TEMPLATE/bug_report.yml:20`, `feature_request.yml:15`, `.github/PULL_REQUEST_TEMPLATE.md:5` | `https://github.com/opentvproject/opentv/blob/main/extras/USER_GUIDE.md` | Ссылка на **совершенно другой проект** (`opentvproject/opentv`), не на OwnTV и не на форк. Заменить на локальный `extras/USER_GUIDE.md` или URL форка. |
| C2 | `.github/workflows/android.yml:258`, `:262`, `:264` | `Name the APK (OwnTV-v<version>)`, `OUT="OwnTV-v…apk"`, `OUT="OwnTV-dev-…apk"` | Имя артефакта от апстрима; привести к имени форка (`MyOwnTV` / `МОЁ ТВ`). |
| C3 | `settings.gradle.kts:25` | `rootProject.name = "MyOwnTV"` | Приложение переименовано в МОЁ ТВ (`applicationId com.github.dimzon.owntv`), имя проекта осталось старым. Косметика, но влияет на названия артефактов. |
| C4 | `git remote -v` | `origin git@github.com:dimzon/moe-tv.git` — приватный репозиторий, **пустой** (коммиты не запушены) | `owner/repo` теперь известен: **`dimzon/moe-tv`**. Порядок: закоммитить 6 документов → запушить `main` → сделать репозиторий публичным → подставить URL в `NOTICE` §4, `README.md`, A1/A2/A6 и B1–B3. Пока репозиторий приватный, `NOTICE` корректно использует нейтральную формулировку Corresponding Source, но это **не заменяет** обязанность выдать исходники при распространении APK. |
| C5 | Заголовки исходников | SPDX-заголовков нет ни у апстрима, ни у форка | Для новых файлов желательно `// SPDX-License-Identifier: GPL-3.0-only`. Массово переписывать унаследованные файлы не обязательно и не рекомендуется: достаточно `NOTICE` + `README` + `CHANGELOG` (§5(a)/(b)). |
| C6 | `.gitignore:40–42`; `extras/branding/__pycache__/*.pyc` (3 файла, закоммичены в `cf8b0b5`) | Комментарий объясняет, почему не игнорируется `extras/worker/` — каталог удалён в `cf8b0b5`; три бинарных `.pyc` (~100 КБ) случайно попали в коммит | Обновить комментарий, добавить `__pycache__/` и снять `.pyc` с отслеживания (`git rm --cached`). Заодно решить, игнорировать ли `.workbuddy-ai/` (журнал проекта) — сейчас он не в `.gitignore` и попадёт при `git add -A`. |
| C7 | `app/build.gradle.kts:450` | `implementation(libs.juniversalchardet)` объявлен, но ни один исходник его не импортирует после `ec31209` | Убрать зависимость из `build.gradle.kts`, `gradle/libs.versions.toml` и строку в `THIRD_PARTY_LICENSES.md` одним коммитом (правило зафиксировано в `CONTRIBUTING.md`). Пока она объявлена, tri-license и выбор GPL-3.0 остаются в перечне. |
| C8 | `AGENTS.md:30–33` ↔ `CONTRIBUTING.md` | В `AGENTS.md` ветки без прямого указания запрещены, работа в `main`; `CONTRIBUTING.md` до этой правки предписывал `git checkout -b` и fork-and-PR | Приведено в соответствие: `CONTRIBUTING.md` теперь описывает коммиты в `main` и отсылает к `AGENTS.md`. Новые ветки не создавать. |

---

## D. Что уже закрыто (для справки)

* `LICENSE` — дословный GPLv3, не изменён. Версия зафиксирована как **GPL-3.0-only**: у апстрима нет оговорки «or later», значит добавлять её к производной работе нельзя.
* §5(a) — явное уведомление «THIS IS A SUBSTANTIALLY MODIFIED VERSION OF OwnTV» с датой 09.11.2026 и развёрнутым перечнем изменений (включая `cf8b0b5`, `4a962f9`, `ec31209`, `baa3d2b`): `NOTICE` §2, `README.md` (Origins & attribution), `CHANGELOG.md` (шапка + раздел «Fork changes»).
* §5(b)/(c) — заявление, что **вся** производная работа лицензирована под GPL-3.0-only: `NOTICE` §3.
* §4 — сохранены кредиты апстрима: @m3th0d93 (wordmark, issue #227), переводчики и Weblate, исторические упоминания TMDB/OpenSubtitles, mpv/libmpv/FFmpeg и android-youtube-player. Активные дисклеймеры удалённых сервисов заменены историческими.
* Атрибуция `OwnTV_Core` (GPL-3.0-only) как второго источника кода и строк: `NOTICE` §1. Апстрим поставлял 26 переводов; **форк упаковывает только `en` и `ru`** — во всех документах это различие оговорено.
* Лицензии зависимостей сведены в `THIRD_PARTY_LICENSES.md`: Apache-2.0 (полный текст — `LICENSES/Apache-2.0.txt`), tri-license `juniversalchardet` с выбором GPL-3.0, OFL-1.1 для 6 семейств шрифтов (11 `.ttf`), ISRG Root X2, логотип Weblate. Исправлена нумерация разделов (был пропущен §5), добавлен исторический раздел об удалённых компонентах.
* `README.md` — исправлены фактические ошибки: `applicationId com.github.dimzon.owntv` (а не `tv.own.myowntv`, это namespace), удалены профили из списка возможностей, убрано утверждение, что reference Worker code остаётся в дереве, обновлён layout `features/`; в Installing добавлена оговорка об отсутствии публичного репозитория.
* `CONTRIBUTING.md` — workflow приведён к `AGENTS.md`, убрано упоминание действующего MIT-уведомления, добавлены правила «удаляешь интеграцию — чисти документы» и «зависимость добавил/обновил/удалил — обнови перечень лицензий».
* `CHANGELOG.md` — добавлен собственный лог форка, апстримовский «Unreleased» явно переименован в «Upstream snapshot notes». Парсеры `.github/workflows/android.yml` и `release-notes.yml` ищут заголовок `## <тег>` до следующего `## v…`, поэтому правка заголовков на извлечение release notes не влияет.

**Совместимость.** Из упаковываемых компонентов остались Apache-2.0 и OFL-1.1 — обе совместимы с GPLv3; MIT-компонентов больше нет (`android-youtube-player` удалён в `ec31209`, MIT-текст убран). MPL-1.1 сама по себе несовместима, но `juniversalchardet` предлагает tri-license, поэтому явно избран GPL-3.0. JUnit (EPL-1.0) и `org.json` (JSON License) используются **только в тестах** и в APK не поставляются, поэтому требование совместимости к ним не применяется; в `THIRD_PARTY_LICENSES.md` это разделено явным образом. Конфликтующих лицензий в зависимостях не обнаружено; mpv/FFmpeg (LGPL/GPL-стек) из сборки удалён.
