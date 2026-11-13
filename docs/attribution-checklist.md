# Чек-лист атрибуции: что ещё нужно изменить перед публикацией

**Статус (13.11.2026).** Атрибуция и лицензионные документы приведены в порядок:
[`NOTICE`](../NOTICE), [`THIRD_PARTY_LICENSES.md`](../THIRD_PARTY_LICENSES.md),
[`LICENSES/Apache-2.0.txt`](../LICENSES/Apache-2.0.txt), [`README.md`](../README.md),
[`CONTRIBUTING.md`](../CONTRIBUTING.md), [`CHANGELOG.md`](../CHANGELOG.md).
`LICENSE` оставлен **нетронутым** — это дословный текст GPLv3, менять его нельзя.

**По решению владельца проекта код, ресурсы и CI намеренно НЕ изменялись.** Ниже зафиксирован
полный список унаследованных от апстрима ссылок и формулировок, которые всё ещё создают ложное
впечатление, что это оригинальный OwnTV, а также юридические пункты, требующие реализации в
приложении. Публичного репозитория у форка пока нет — поэтому вместо выдуманного URL в документах
использованы нейтральные формулировки («дерево исходников, из которого получена копия»).

---

## A. Обязательно до любой публикации APK

| # | Где | Сейчас | Почему это проблема | Что сделать |
|---|---|---|---|---|
| A1 | `app/src/main/java/tv/own/owntv/core/CoreBuildInfo.kt:77` | `releaseRepo = "ahXN00/OwnTV"` | `UpdateManager.kt:103` запрашивает `api.github.com/repos/<releaseRepo>/releases/latest`, т.е. встроенный обновляльщик предлагает **релизы апстрима**. Помимо ложной атрибуции это поломка: у апстрима другой `applicationId`, установщик откажет. | Прописать `owner/repo` форка или отключить проверку обновлений до появления репозитория. |
| A2 | `app/src/main/java/tv/own/myowntv/features/shell/components/SettingsScreen.kt:1687` | `GITHUB_REPO = "github.com/ahXN00/OwnTV"` | Экран More › About показывает **чужой репозиторий** как «репозиторий проекта». Для GPLv3 §6 это ещё и неверный адрес Corresponding Source. | Заменить на URL форка; до публикации — строку вида `Source: см. NOTICE` / скрыть поле. |
| A3 | `app/src/main/java/tv/own/myowntv/features/more/MorePages.kt:555–631` (`AboutPage`) | Показывает версию, `more_about_license` = «GPLv3 · free and open source», описание, QR и ссылку на репозиторий | Нет «Appropriate Legal Notices»: нет копирайта, нет указания «модифицированная версия» с датой, нет отказа от гарантий, нет способа просмотреть текст лицензии. Строго по §5(d) это требуется, если их показывал оригинал (у апстрима была только короткая подпись), но для однозначного compliance и добросовестной атрибуции — необходимо. | Добавить на экран: `© 2026 Dmitry Alexandrov · модификация OwnTV (c 09.11.2026) · GPL-3.0-only · БЕЗ ГАРАНТИЙ · LICENSE / NOTICE / THIRD_PARTY_LICENSES.md`. Текст брать из ресурсов (см. B3), не хардкодить. |
| A4 | `app/src/main/res/values/strings_settings.xml:2358`, `:2360`, `:665` | «GPLv3 · free and open source»; «Your own IPTV player … you bring your own legally accessible sources»; «Contributions … on GitHub» | Описание осталось от апстрима и **неверно по сути**: приложение больше не «bring your own source», оно работает с публичным каталогом RuTube. Ссылка на GitHub ведёт не туда. | Обновить `more_about_license`, `more_about_description`, `settings_contributions`; добавить новые ключи (B3). `values-ru/` обновить вместе с `values/`. |
| A5 | Релиз / дистрибуция APK | В репозитории лежат `LICENSE`, `NOTICE`, `THIRD_PARTY_LICENSES.md`, `LICENSES/Apache-2.0.txt`, 6 OFL-файлов | При распространении **только APK** эти файлы должны сопровождать сборку (GPLv3 §§4/6, Apache-2.0 §4, OFL §1). Сейчас в `app/src/main/assets/` лежит только `channel_renames.json`. | Либо положить тексты лицензий в assets и показывать их в About, либо прикладывать архив с исходниками к каждому релизу, либо дать письменную оферту на исходники сроком ≥3 лет (§6b). |
| A6 | `README.md` (раздел Installing) | «Download the APK from this repository's Releases» | Релизов у форка нет; фраза обещает источник, которого не существует. | После появления репозитория заменить на реальную ссылку, сейчас — оставить с пометкой или убрать. |

---

## B. Атрибуция и тексты приложения

| # | Где | Сейчас | Что сделать |
|---|---|---|---|
| B1 | `SettingsScreen.kt:1688` | `TELEGRAM_LINK = "t.me/owntvplayer"` | Чужое сообщество апстрима. Заменить на свой канал или убрать QR-карточку (`telegram_qr.jpg`) из `AboutPage`. |
| B2 | `app/src/main/java/tv/own/owntv/core/i18n/SupportedLocales.kt:45` | `https://github.com/ahXN00/OwnTV/issues/new?...` | Ссылка «попросить новый язык» ведёт в issue-трекер апстрима. Заменить или убрать. |
| B3 | `app/src/main/java/tv/own/owntv/core/util/CrashUploader.kt:71` | `Referer: https://github.com/ahXN00/OwnTV` | Заменить на адрес форка / убрать. |
| B4 | Новые строковые ресурсы | — | Добавить ключи, напр. `more_about_copyright`, `more_about_modified`, `more_about_no_warranty`, `more_about_licenses_link`, `more_about_upstream`. **Важно:** видимый пользователю текст нельзя хардкодить в Kotlin — сработает гейт `tools/i18n/check_hardcoded_strings.py` (зависит от `preBuild`). Добавлять в `values/strings_settings.xml` и `values-ru/strings_settings.xml` — сейчас упаковываются только `en` и `ru`; непереведённые ключи унаследуют fallback из `values/` (`MissingTranslation` отключён). |
| B5 | `extras/worker/` (удалён) | Воркеры апстрима для TMDB и OpenSubtitles | Удалены вместе с интеграциями, логотипами и ключами. Закрыто. |
| B6 | `CHANGELOG.md:3` и ниже | История релизов до v5.2.1 — апстримовская, с функциями, которых у форка уже нет (M3U/Xtream/mpv/VOD/профили) | Не выдавать за changelog форка. Верхняя сноска уже разделяет «upstream import point» и форк; при первом собственном релизе отделить/архивировать историю апстрима. |

---

## C. Репозиторий и CI (не менялось, но требует правки)

| # | Где | Сейчас | Что сделать |
|---|---|---|---|
| C1 | `.github/ISSUE_TEMPLATE/bug_report.yml:20`, `feature_request.yml:15`, `.github/PULL_REQUEST_TEMPLATE.md:5` | `https://github.com/opentvproject/opentv/blob/main/extras/USER_GUIDE.md` | Ссылка на **совершенно другой проект** (`opentvproject/opentv`), не на OwnTV и не на форк. Заменить на локальный `extras/USER_GUIDE.md` или URL форка. |
| C2 | `.github/workflows/android.yml:258`, `:262` | `Name the APK (OwnTV-v<version>)`, `OUT="OwnTV-v…apk"` | Имя артефакта от апстрима; привести к имени форка (`MyOwnTV` / `МОЁ ТВ`). |
| C3 | `settings.gradle.kts:25` | `rootProject.name = "MyOwnTV"` | Приложение переименовано в МОЁ ТВ (`applicationId com.github.dimzon.owntv`), имя проекта осталось старым. Косметика, но влияет на названия артефактов. |
| C4 | `git remote -v` | пусто | Добавить `origin` форка и указать его во всех пунктах выше + в `NOTICE` §4. Пока репозитория нет, `NOTICE` использует нейтральную формулировку Corresponding Source — это корректно, но **не заменяет** обязанность выдать исходники при распространении APK. |
| C5 | Заголовки исходников | SPDX-заголовков нет ни у апстрима, ни у форка | Для новых файлов желательно `// SPDX-License-Identifier: GPL-3.0-only`. Массово переписывать унаследованные файлы не обязательно и не рекомендуется: достаточно `NOTICE` + `README` + `CHANGELOG` (§5(a)/(b)). |

---

## D. Что уже закрыто (для справки)

* `LICENSE` — дословный GPLv3, не изменён. Версия зафиксирована как **GPL-3.0-only**: у апстрима нет оговорки «or later», значит добавлять её к производной работе нельзя.
* §5(a) — явное уведомление «THIS IS A SUBSTANTIALLY MODIFIED VERSION OF OwnTV» с датой 09.11.2026 и перечнем изменений: `NOTICE` §2, `README` (Origins & attribution), `CHANGELOG.md` (сноска).
* §5(b)/(c) — заявление, что **вся** производная работа лицензирована под GPL-3.0-only: `NOTICE` §3.
* §4 — сохранены кредиты апстрима: @m3th0d93 (wordmark, issue #227), переводчики и Weblate, исторические упоминания TMDB/OpenSubtitles (интеграции, воркеры и логотипы удалены), исторические упоминания mpv/libmpv/FFmpeg.
* Атрибуция `OwnTV_Core` (GPL-3.0-only) как второго источника кода и всех 26 локалей — `NOTICE` §1.
* Лицензии зависимостей сведены в `THIRD_PARTY_LICENSES.md`: Apache-2.0 (полный текст — `LICENSES/Apache-2.0.txt`), tri-license juniversalchardet с выбором GPL-3.0, OFL-1.1 для 6 семейств шрифтов, ISRG Root X2, логотип Weblate.
* `CONTRIBUTING.md` приведён к текущему проекту (имя, RuTube-only позиционирование, рабочая ссылка на документацию, условия лицензирования вклада).

**Совместимость.** Apache-2.0, MIT и OFL-1.1 совместимы с GPLv3. MPL-1.1 сама по себе несовместима, но `juniversalchardet` предлагает tri-license, поэтому в `THIRD_PARTY_LICENSES.md` явно избран GPL-3.0. Конфликтующих лицензий в зависимостях не обнаружено; mpv/FFmpeg (LGPL/GPL-стек) из сборки удалён.
