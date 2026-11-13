# МОЁ ТВ — бренд-кит

Актуальная айдентика приложения **МОЁ ТВ / MyOwnTV**: телевизор с настроечной сеткой и
антенной-«рожками», надпись «МОЁ ТВ» и девять цветовых вариантов, которые поставляются в APK.

Кит собран из двух источников:

* **текущие ресурсы приложения** — `app/src/main/res/` (`owntv_*`, цвета `owntv_icon_bg_*`,
  баннеры `owntv_banner_*`, foreground-слои иконок, wordmark);
* **движок кита** — `extras/branding/` (`build_brand.py`, `build_variants.py`) и палитры из
  `tools/brand/render_brand.py`.

`extras/branding/` — это исходный кит с 12 самостоятельными арт-схемами и мастер-схемой
«Глубокий синий». В APK они **не** попадают: приложение использует девять вариантов из
`AppIcon` enum. Именно они и лежат в этом каталоге.

## Состав

| Путь | Что это | Формат |
|---|---|---|
| `app-icons/icon_<slug>.png` | Квадратная иконка: плоский фон `owntv_icon_bg_<slug>` + знак, скругление 22 % — как у адаптивной иконки в APK | 1024×1024, RGBA |
| `app-icons/icon_<slug>_round.png` | Круглая иконка | 1024×1024, RGBA |
| `app-icons/icon_<slug>_flat.png` | Полный квадрат без скруглений — для стора, фавикона, презентаций | 1024×1024, RGBA |
| `app-logos/logo_<slug>.png` | Горизонтальный lockup «знак + МОЁ ТВ» для **тёмного** фона (белые чернила, «ТВ» — акцентом) | прозрачный PNG, высота 256 px |
| `app-logos/logo_<slug>_light.png` | То же для **светлого** фона (тёмные чернила, «ТВ» — затемнённым акцентом) | прозрачный PNG, высота 256 px |
| `tv-banners/banner_<slug>.png` | Баннер Android TV: та же композиция, что в APK | 1280×720, RGB |
| `legacy/` | Айдентика Original OwnTV до ребрендинга (знак с play-треугольником) | PNG |

В APK баннер лежит в `drawable-xhdpi/owntv_banner_<slug>.png` размером 320×180 — это
уменьшенная копия `tv-banners/banner_<slug>.png` (мастер 1280×720).

## Варианты

Порядок и цвета совпадают с `AppIcon` (`app/src/main/java/tv/own/owntv/core/brand/AppIcon.kt`),
`values/owntv_icon_colors.xml` и `tools/brand/render_brand.py`.

| slug | Фон иконки | Акцент «ТВ» | `AppIcon` | Activity |
|---|---|---|---|---|
| `petrol` | `#1B6663` | `#5CC2BC` | `PETROL` | `MainActivityPetrol` |
| `sunflower` | `#D59D1C` | `#FFD24D` | `SUNFLOWER` | `MainActivitySunflower` |
| `cobalt` | `#3E63D2` | `#8AAAFF` | `COBALT` | `MainActivityCobalt` |
| `tomato` | `#D8523C` | `#FF9C84` | `TOMATO` | `MainActivityTomato` |
| `board` | `#242930` | `#52DBC8` | `BOARD` | `MainActivityBoard` |
| `eggshell` | `#DACAAA` | `#86D3DB` | `EGGSHELL` | `MainActivity` (по умолчанию) |
| `olive` | `#66891E` | `#A5CC4E` | `OLIVE` | `MainActivityOlive` |
| `olive_cream` | `#D8D1B6` | `#A5CC4E` | `OLIVE_CREAM` | `MainActivityOliveCream` |
| `pixel` | `#0E1618` | `#52DBC8` | `PIXEL` | `MainActivityPixel` |

## Соответствие ресурсам APK

| Артефакт кита | Что поставляется в APK |
|---|---|
| `app-icons/icon_<slug>.png` | `mipmap-anydpi-v26/owntv_icon_<slug>.xml` + `mipmap-*/owntv_icon_<slug>_foreground.png` + `@color/owntv_icon_bg_<slug>` |
| `tv-banners/banner_<slug>.png` | `drawable-xhdpi/owntv_banner_<slug>.png` (320×180) |
| `app-logos/logo_<slug>.png` | В приложении lockup собирается из `drawable/owntv_wordmark_own.png` и `owntv_wordmark_tv.png` (тинтуются в `BrandLockup`) |
| — | `drawable/owntv_mark_<slug>.xml` — векторный знак для интерфейса |
| — | `drawable/owntv_splash_<slug>.xml`, `owntv_splash_static_<slug>.xml` — экран запуска |
| — | `drawable-*/owntv_splash_name_<slug>.png` — двухтонный wordmark для splash-экрана |
| — | `drawable/owntv_notification.xml`, `owntv_icon_monochrome.xml` — уведомление и themed icon |

## Перегенерация

Нужны Python 3, Pillow и numpy; шрифт — `C:/Windows/Fonts/segoeuib.ttf` (Segoe UI Bold).

```bash
# ресурсы приложения app/src/main/res
python -I tools/brand/render_brand.py

# внешний кит extras/brand (этот каталог)
python -I tools/brand/render_brand_extras.py
```

Первый скрипт перерисовывает `owntv_*` в `app/src/main/res/`; второй читает те же палитры и
движок `extras/branding`, поэтому кит остаётся синхронным с APK. `legacy/` не
перегенерируется — это исторические ассеты апстрима.

## Лицензия и атрибуция

* Текущий кит (`app-icons/`, `app-logos/`, `tv-banners/`) — собственная работа этого форка,
  распространяется под `GPL-3.0-only`, как и приложение.
* `legacy/` — айдентика Original OwnTV, © ahXN00 и участники OwnTV, `GPL-3.0-only`;
  строчный wordmark «owntv» нарисован [@m3th0d93](https://github.com/m3th0d93) в
  [issue #227](https://github.com/ahXN00/OwnTV/issues/227). Подробности — в
  [`THIRD_PARTY_LICENSES.md`](../../THIRD_PARTY_LICENSES.md) и [`NOTICE`](../../NOTICE).
