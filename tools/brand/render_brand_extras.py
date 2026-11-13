# -*- coding: utf-8 -*-
"""МОЁ ТВ — внешний бренд-кит `extras/brand`.

Источники:
  * текущие ресурсы приложения — `app/src/main/res/` (`owntv_*`, палитры `values/owntv_icon_colors*.xml`,
    баннеры `drawable-xhdpi/owntv_banner_<slug>.png`, foreground-слои иконок);
  * движок бренд-кита `extras/branding` (`build_brand.py`, `build_variants.py`);
  * палитры приложения — `tools/brand/render_brand.py` (те же 9 вариантов, что в APK).

Пишет:
  app-icons/icon_<slug>.png          1024×1024 — квадратная плитка, как у адаптивной иконки в APK
  app-icons/icon_<slug>_round.png    1024×1024 — круглая версия
  app-icons/icon_<slug>_flat.png     1024×1024 — полный квадрат без скруглений (стор/фавикон)
  app-logos/logo_<slug>.png          прозрачный lockup «знак + МОЁ ТВ» для тёмного фона
  app-logos/logo_<slug>_light.png    то же с тёмными чернилами для светлого фона
  tv-banners/banner_<slug>.png       1280×720 — мастер-версия `drawable-xhdpi/owntv_banner_*`
  legacy/                            не трогается: апстримные ассеты Original OwnTV

Запуск:  python -I tools/brand/render_brand_extras.py
"""
import os
import shutil
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
KIT = os.path.join(REPO, "extras", "branding")
OUT = os.path.join(REPO, "extras", "brand")

sys.path.insert(0, KIT)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import build_brand as bb  # noqa: E402
import build_variants as bv  # noqa: E402
import render_brand as rb  # noqa: E402

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"

ICON_PX = 1024
LOGO_H = 256
BANNER_W, BANNER_H = 1280, 720

# Чернила wordmark: белые на тёмном фоне, почти чёрные на светлом.
INK_ON_DARK = (255, 255, 255, 255)
INK_ON_LIGHT = (26, 30, 34, 255)


def rgb(hex_value):
    return bv.h(hex_value)


def compose_icon(bg_hex, pal, mark, kind="square", size=ICON_PX):
    """Иконка из плоского фона адаптивной иконки APK и знака приложения."""
    img = Image.new("RGBA", (size, size), (*rgb(bg_hex), 255))
    frac = {"square": 0.76, "round": 0.72, "flat": 0.72}[kind]
    m = mark.resize((int(size * frac),) * 2, Image.LANCZOS)
    img.alpha_composite(m, ((size - m.width) // 2, (size - m.height) // 2))
    if kind == "round":
        d = ImageDraw.Draw(img)
        d.ellipse([2, 2, size - 3, size - 3],
                  outline=(*pal.ring, 48), width=max(1, size // 170))
    # маска с 4× суперсэмплингом, как в build_brand.compose_icon
    s = 4
    mask = Image.new("L", (size * s, size * s), 0)
    md = ImageDraw.Draw(mask)
    if kind == "square":
        md.rounded_rectangle([0, 0, size * s - 1, size * s - 1],
                             radius=int(size * 0.22 * s), fill=255)
    elif kind == "round":
        md.ellipse([0, 0, size * s - 1, size * s - 1], fill=255)
    else:
        md.rectangle([0, 0, size * s - 1, size * s - 1], fill=255)
    img.putalpha(mask.resize((size, size), Image.LANCZOS))
    return img


def tracked_text(draw, parts, font, tracking, x, baseline, gap):
    """Пишет части строки посимвольно с трекингом, как в баннере кита."""
    cx = x
    for text, color in parts:
        for ch in text:
            draw.text((cx, baseline), ch, font=font, fill=color, anchor="ls")
            cx += draw.textlength(ch, font=font) + tracking
        cx += gap
    return cx - tracking - gap


def compose_logo(pal, mark, light=False):
    """Прозрачный горизонтальный lockup: mark + «МОЁ»(ink) «ТВ»(accent)."""
    ink = INK_ON_LIGHT if light else INK_ON_DARK
    accent = bv.darken(pal.accent, 0.35) if light else pal.accent

    font_size = 150
    font = ImageFont.truetype(FONT_BOLD, font_size)
    tracking = max(3, int(font_size * 0.055))
    gap = int(font_size * 0.16)
    parts = [("МОЁ", ink), ("ТВ", (*accent, 255))]

    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    text_w = 0
    for text, _ in parts:
        for ch in text:
            text_w += probe.textlength(ch, font=font) + tracking
        text_w += gap
    text_w -= tracking + gap

    pad = 8
    mark_px = LOGO_H - 2 * pad
    mark_gap = int(font_size * 0.28)
    width = pad + mark_px + mark_gap + int(text_w) + pad
    img = Image.new("RGBA", (width, LOGO_H), (0, 0, 0, 0))
    img.alpha_composite(mark.resize((mark_px, mark_px), Image.LANCZOS), (pad, pad))

    baseline = LOGO_H // 2 + int(font_size * 0.35)
    tracked_text(ImageDraw.Draw(img), parts, font, tracking,
                 pad + mark_px + mark_gap, baseline, gap)
    return img


def reset_dir(path):
    if os.path.isdir(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def main():
    icons = os.path.join(OUT, "app-icons")
    logos = os.path.join(OUT, "app-logos")
    banners = os.path.join(OUT, "tv-banners")
    for path in (icons, logos, banners):
        reset_dir(path)

    for slug, bg_hex, accent_hex, light in rb.VARIANTS:
        pal = rb.palette_for(bg_hex, accent_hex, light)
        bb.set_palette(pal)
        mark = bb.make_tv_mark(1200)

        # Иконки: фон — цвет owntv_icon_bg_<slug> из APK, знак — foreground-слой.
        for kind, suffix in (("square", ""), ("round", "_round"), ("flat", "_flat")):
            compose_icon(bg_hex, pal, mark, kind).save(
                os.path.join(icons, f"icon_{slug}{suffix}.png"))

        # Баннер: та же композиция, что drawable-xhdpi/owntv_banner_<slug>.png (320×180).
        bb.make_banner(mark, BANNER_W, BANNER_H).save(
            os.path.join(banners, f"banner_{slug}.png"))

        # Логотипы: тёмный фон (белые чернила) и светлый фон (тёмные чернила).
        compose_logo(pal, mark, light=False).save(
            os.path.join(logos, f"logo_{slug}.png"))
        compose_logo(pal, mark, light=True).save(
            os.path.join(logos, f"logo_{slug}_light.png"))

        print(f"{slug}: icons, banner, logos")

    bb.set_palette(bb.default_palette())
    print("brand kit ->", OUT)


if __name__ == "__main__":
    main()
