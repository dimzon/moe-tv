# AGENTS.md

## Стенды и отладка

Все доступные устройства, их псевдонимы, адреса, характеристики и команды управления
VM, контейнерами и эмуляторами описаны в каноническом справочнике
`C:\Users\User\Desktop\SCRCPY\DEVICES.md` — это единственный источник правды по стендам.
Локальный [DEVICES.md](DEVICES.md) является проектной копией; при расхождениях используй
канонический справочник и helper-ы из `C:\Users\User\Desktop\SCRCPY`.

- Отладка и тестирование выполняются только на стендах из канонического справочника.
  Не поднимать собственные VM, контейнеры или эмуляторы без необходимости.
- Назначение, псевдонимы и текущее состояние стендов — в разделах 1 и 10 канонического
  справочника. Эмуляторы не запускаются параллельно: правила подъёма и остановки — в разделе 6.
- Для adb/scrcpy использовать `C:\Users\User\Desktop\SCRCPY\adb.cmd <alias>` и
  `C:\Users\User\Desktop\SCRCPY\scrcpy.cmd <alias>`, не копируя IP VM в проекты.

### Завершение работы на виртуальных стендах

На **всех виртуальных стендах** (redroid-контейнеры и эмуляторы) после отладки или тестирования
не оставлять воспроизведение и приложение в фоне. Перед завершением работы остановить приложение,
чтобы ExoPlayer, декодеры и аудио не оставались активными:

```bat
C:\Users\User\Desktop\SCRCPY\adb.cmd <alias> shell am force-stop com.github.dimzon.owntv
```

Команда безопасна, если приложение уже остановлено. Данные приложения очищать не требуется.

## TV-бокс

Физический TV-бокс (адрес и псевдоним — в каноническом справочнике
`C:\Users\User\Desktop\SCRCPY\DEVICES.md`) использовать **только по прямому указанию пользователя**.

Без такого указания запрещено:

- подключаться к боксу;
- устанавливать, обновлять или удалять приложения;
- перезагружать устройство;
- менять настройки;
- очищать данные приложений.

Бокс — эталонное физическое устройство.

## Совместимость с Android 6 (API 23)

**Целевая минимальная версия — Android 6.0 (API 23).** `minSdk = 23` в `app/build.gradle.kts`.
Весь новый код обязан работать на API 23 без крешей и `NoSuchMethodError`/`NoSuchFieldError`.

Правила для кода и ревью:

- Не использовать API уровня 24+ напрямую. В частности:
  - `Configuration.locales`, `LocaleList`, `config.setLocales()` — вместо них `LocaleCompat.firstLocaleCompat()`
    / `localeTagsCompat()` / `LocaleCompat.listOf()` из `core/i18n` (на базе `ConfigurationCompat`);
  - `android.icu.util.ULocale.*`, `CompactDecimalFormat`, `ConcurrentHashMap.newKeySet()`/`KeySetView`,
    `Map.compute/merge/putIfAbsent`, `Math.floorMod`, `ConnectivityManager.registerDefaultNetworkCallback`,
    `Activity.isInPictureInPictureMode` — только под `Build.VERSION.SDK_INT >= Build.VERSION_CODES.N`
    с деградацией на API 23, либо через совместимые аналоги.
- Прежде чем писать вызов, проверять минимальный API level. Если он выше 23 — добавить guard или
  compat-обёртку. `coreLibraryDesugaring` включён (`desugar_jdk_libs`) и покрывает Java 8-методы
  коллекций, но **не** покрывает Android-API.
- Тестирование: для проверки совместимости используй подходящий стенд из `DEVICES.md`,
  но любой коммит должен сохранять сборку и lint зелёными при `minSdk = 23`. Реальные
  проверки на API 23 выполняют волонтёры — не ломать совместимость «на всякий случай
  ради нового API».

## Сборка

Собирать **только батниками** из корня репозитория и **только флейвор `universal`**
(arm64-v8a + armeabi-v7a + x86_64 в одном APK):

- `build-debug.cmd universal` — debug-сборка;
- `build-release.cmd universal` — release-сборка (R8, подпись из `keystore.properties`);
- `publish.cmd <версия> [notes.md]` — release-сборка + публикация релиза `v<версия>`
  в `dimzon/moe-tv-site` + обновление версии на странице загрузки.

Задачи `assemble*` через `gradlew` напрямую не запускать. Флейворы `standard`,
`x86_64` и `all` не собирать — они остались историческим заделом.

## R8 mapping (retrace)

Карта обфускации — единственный способ прочитать release-стек; терять её нельзя.
Ниже — готовый порядок поиска, чтобы не исследовать скрипты заново.

### Где хранится mapping

1. **Локальная копия после `build-release.cmd` / `publish.cmd`:**
   `build/mapping/<variant>-<versionName_>-<versionCode>.txt`.
   Для universal и опубликованной версии это, например,
   `build/mapping/universalRelease-1.14_145.txt`.
   Если `VERSION_NAME` не задан, имя версии в файле отсутствует:
   `build/mapping/universalRelease-145.txt`.
   Архивирование выполняет `tools/archive-mapping.cmd`; каталог `build/` в `.gitignore`.

2. **Публичная release-копия (основная для чужих crash reports):**
   `publish.cmd` загружает mapping в релиз `v<версия>` репозитория `dimzon/moe-tv-site`
   с именем `MoeTV_<версия>_<versionCode>_universal_mapping.txt`.
   Прямая ссылка:
   `https://github.com/dimzon/moe-tv-site/releases/download/v<версия>/MoeTV_<версия>_<versionCode>_universal_mapping.txt`.
   Эта копия переживает `gradle clean`, смену машины и является приоритетной для опубликованных APK.

3. **CI artifact:**
   `.github/workflows/android.yml` кладёт mapping рядом с APK:
   `MoeTV_<версия>_<код>_mapping.txt` для тега и
   `MoeTV-dev-<sha>_mapping.txt` для manual/dev сборки.
   CI artifacts временные; для публичного релиза использовать release-копию из п. 2.

### Как сопоставить crash report

В шапке отчёта берутся `МОЁ ТВ <версия> (<versionCode>)`, `build <FINGERPRINT>` и
`signature <хеш>`. Нужен mapping именно этого `<versionCode>`.

Пример для отчёта **МОЁ ТВ 1.14 (145)**:

- локально: `build/mapping/universalRelease-1.14_145.txt`;
- публично: `MoeTV_1.14_145_universal_mapping.txt` в релизе
  `https://github.com/dimzon/moe-tv-site/releases/tag/v1.14`;
- скачать без браузера:
  `gh release download v1.14 --repo dimzon/moe-tv-site --pattern "MoeTV_1.14_145_universal_mapping.txt" --dir build/mapping`.

### Retrace

- CLI: `java -cp "C:\Work\tools\androidsdk\cmdline-tools\latest\lib\r8.jar" com.android.tools.r8.retrace.Retrace mapping.txt stack.txt`.
  Это проверенный путь на текущей машине; R8 jar не хранится в репозитории.
  В Android Studio можно использовать встроенный **Analyze Stack Trace**.
- Без карты строки `SourceFile:NN` бессмысленны.

Сборка без карты не публикуется: `publish.cmd` падает, если `mapping.txt` не найден;
`build-release.cmd` падает, если архиватор не нашёл карту для собранного флейвора.

## Git

**Строго запрещено создавать новые ветки без прямого указания пользователя** — в том числе
`git checkout -b`, `git switch -c`, `git branch <имя>` и `git worktree add -b`.

Работа и коммиты — в текущей ветке (`main`), если пользователь не сказал иное.
