# AGENTS.md

## Стенды и отладка

Все доступные устройства, их характеристики, адреса и команды управления виртуальными
машинами и контейнерами описаны в [DEVICES.md](DEVICES.md).

- Отладка и тестирование выполняются на стендах из `DEVICES.md`. Не поднимать
  собственные VM, контейнеры или эмуляторы без необходимости.
- Основной автоматизируемый стенд — `redroid-smartrutube` (`172.18.237.39:5556`).
- Старый `redroid` (`172.18.237.39:5555`) — свободный дополнительный стенд, можно использовать.
- Android 7 emulator (`127.0.0.1:5559`) использовать только для проверки API 24/25.

## TV-бокс

TV-бокс `192.168.1.22:5555` использовать **только по прямому указанию пользователя**.

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
- Тестирование: основной стенд — API 24 (эмулятор `127.0.0.1:5559`), но любой коммит должен
  сохранять сборку и lint зелёными при `minSdk = 23`. Реальные проверки на API 23 выполняют
  волонтёры — не ломать совместимость «на всякий случай ради нового API».

## Сборка

Собирать **только батниками** из корня репозитория и **только флейвор `universal`**
(arm64-v8a + armeabi-v7a + x86_64 в одном APK):

- `build-debug.cmd universal` — debug-сборка;
- `build-release.cmd universal` — release-сборка (R8, подпись из `keystore.properties`);
- `publish.cmd <версия> [notes.md]` — release-сборка + публикация релиза `v<версия>`
  в `dimzon/moe-tv-site` + обновление версии на странице загрузки.

Задачи `assemble*` через `gradlew` напрямую не запускать. Флейворы `standard`,
`x86_64` и `all` не собирать — они остались историческим заделом.

## Git

**Строго запрещено создавать новые ветки без прямого указания пользователя** — в том числе
`git checkout -b`, `git switch -c`, `git branch <имя>` и `git worktree add -b`.

Работа и коммиты — в текущей ветке (`main`), если пользователь не сказал иное.
