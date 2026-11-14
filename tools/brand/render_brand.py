# -*- coding: utf-8 -*-
"""МОЁ ТВ — генератор брендинг-ресурсов OwnTV (воссоздание отсутствовавшего render_brand.py).

Рисует знак/иконки/баннеры/сплэши/wordmark кита «МОЁ ТВ» в 9 существующих цветовых
вариантах приложения и раскладывает их по app/src/main/res под прежними именами owntv_*,
чтобы манифест, enum AppIcon и темы не требовали правок.

Цветовые токены приложения (owntv_icon_bg_*, accent в AppIcon) НЕ меняются — новый знак
лишь перекрашивается под каждую палитру.

Запуск:  python -I tools/brand/render_brand.py
"""
import os
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
KIT = os.path.join(REPO, "extras", "branding")
RES = os.path.join(REPO, "app", "src", "main", "res")
sys.path.insert(0, KIT)

from PIL import Image, ImageDraw, ImageFont  # noqa: E402
import build_brand as bb  # noqa: E402
import build_android as ba  # noqa: E402
import build_variants as bv  # noqa: E402

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"

# slug-имя варианта -> (icon_bg hex из owntv_icon_colors.xml, accent hex из AppIcon.kt, светлая?)
VARIANTS = [
    ("petrol",      "#1B6663", "#5CC2BC", False),
    ("sunflower",   "#D59D1C", "#FFD24D", False),
    ("cobalt",      "#3E63D2", "#8AAAFF", False),
    ("tomato",      "#D8523C", "#FF9C84", False),
    ("board",       "#242930", "#52DBC8", False),
    ("eggshell",    "#DACAAA", "#86D3DB", True),
    ("olive",       "#66891E", "#A5CC4E", False),
    ("olive_cream", "#D8D1B6", "#A5CC4E", True),
    ("pixel",       "#0E1618", "#52DBC8", False),
]

# Плотности адаптивного foreground-слоя (108dp-холст): mipmap-dp -> px
FG_DENSITIES = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]
# splash_name (branding image): mdpi 200x80 .. xxxhdpi 800x320
NAME_DENSITIES = [("mdpi", 200, 80), ("hdpi", 300, 120), ("xhdpi", 400, 160), ("xxhdpi", 600, 240), ("xxxhdpi", 800, 320)]


def h(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def palette_for(bg_hex, accent_hex, light):
    """Собрать полную палитру движка из плоского цвета тайла + акцента варианта."""
    bg1 = h(bg_hex)
    bg2 = bv.darken(bg1, 0.55)
    accent = h(accent_hex)
    body1 = accent
    body2 = bv.mix(accent, bg1, 0.5)
    ink = (26, 30, 34) if light else (255, 255, 255)
    spec = ("variant", "variant", bg_hex, "#%02X%02X%02X" % bg2,
            "#%02X%02X%02X" % body1, "#%02X%02X%02X" % body2, accent_hex,
            "#%02X%02X%02X" % ink, "#%02X%02X%02X" % bg2, bv.CANON, light)
    return bv.make_palette(spec)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG")


def render_wordmark_layers(p, height=182):
    """Два прозрачных слоя «МОЁ» (own) и «ТВ» (tv) белым, для тинта в BrandLockup.

    Возвращает (own, tv, W, H). Оба слоя на одном холсте W×H,字形 в тех же позициях,
    что compose-баннер кита, чтобы наложение давало «МОЁ ТВ».
    """
    parts = [("МОЁ", 26, (255, 255, 255, 255)), ("ТВ", 0, (255, 255, 255, 255))]
    font = ImageFont.truetype(FONT_BOLD, height)
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    tracking = max(4, int(height * 0.06))
    total_w = int(bb._tracked_width(probe, parts, font, tracking))
    W, H = total_w + height, int(height * 1.35)
    own = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tv = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # «МОЁ»
    d1 = ImageDraw.Draw(own)
    d1.text((height // 2, H // 2), "МОЁ", font=font, fill=(255, 255, 255, 255), anchor="lm")
    w1 = d1.textbbox((0, 0), "МОЁ", font=font)
    # «ТВ» правее на ширину МОЁ + tracking
    d2 = ImageDraw.Draw(tv)
    x_tv = height // 2 + (w1[2] - w1[0]) + tracking
    d2.text((x_tv, H // 2), "ТВ", font=font, fill=(255, 255, 255, 255), anchor="lm")
    return own, tv, W, H


def render_splash_name(p, w, hh):
    """Двухтонный wordmark «МОЁ»(белый) «ТВ»(accent) на прозрачном, под branding-image.

    Branding-image показывается только на тёмном фоне сплеша (@color/owntv_window_bg,
    Android 12+), поэтому «МОЁ» всегда светлое — независимо от флага light варианта
    (у светлых вариантов p.ink тёмный и рассчитан на светлые поверхности в приложении,
    не на сплеш). Аналог INK_ON_DARK в tools/brand/render_brand_extras.py.
    """
    img = Image.new("RGBA", (w, hh), (0, 0, 0, 0))
    font = ImageFont.truetype(FONT_BOLD, int(hh * 0.72))
    d = ImageDraw.Draw(img)
    tracking = max(2, int(hh * 0.04))
    moe = "МОЁ"
    tv = "ТВ"
    wb = d.textbbox((0, 0), moe, font=font)
    w1 = wb[2] - wb[0]
    wb2 = d.textbbox((0, 0), tv, font=font)
    w2 = wb2[2] - wb2[0]
    total = w1 + tracking + w2
    x = (w - total) // 2
    yc = hh // 2
    d.text((x, yc), moe, font=font, fill=(255, 255, 255, 255), anchor="lm")
    d.text((x + w1 + tracking, yc), tv, font=font, fill=(*p.accent, 255), anchor="lm")
    return img


def adaptive_xml(color):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated by tools/brand/render_brand.py. Do not edit by hand. -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@color/owntv_icon_bg_{color}"/>\n'
        f'    <foreground android:drawable="@mipmap/owntv_icon_{color}_foreground"/>\n'
        '    <monochrome android:drawable="@drawable/owntv_icon_monochrome"/>\n'
        '</adaptive-icon>\n'
    )


def main():
    # shared monochrome + notification (белый глиф, один на всех)
    ba_write_mono = ba.vector_monochrome()
    write(os.path.join(RES, "drawable", "owntv_icon_monochrome.xml"), ba_write_mono)
    write(os.path.join(RES, "drawable", "owntv_notification.xml"), ba.vector_notification())

    # wordmark PNG (один на всех: own/tv слои белым, тинт в BrandLockup)
    p0 = palette_for(*VARIANTS[5][1:])  # eggshell as neutral reference for geometry
    own, tv, W, H = render_wordmark_layers(p0)
    save_png(own, os.path.join(RES, "drawable", "owntv_wordmark_own.png"))
    save_png(tv, os.path.join(RES, "drawable", "owntv_wordmark_tv.png"))
    # удалить старые векторные wordmark (переход на PNG)
    for nm in ("owntv_wordmark_own.xml", "owntv_wordmark_tv.xml"):
        fp = os.path.join(RES, "drawable", nm)
        if os.path.exists(fp):
            os.remove(fp)
    print(f"wordmark PNG {W}x{H}")

    for color, bg_hex, accent_hex, light in VARIANTS:
        p = palette_for(bg_hex, accent_hex, light)
        bb.set_palette(p)
        mark = bb.make_tv_mark(1200)

        # adaptive foreground PNG по плотностям
        fg = bb.adaptive_foreground(mark, 512)
        for dp, px in FG_DENSITIES:
            fg.resize((px, px), Image.LANCZOS).save(
                os.path.join(RES, f"mipmap-{dp}", f"owntv_icon_{color}_foreground.png"))

        # banner (только drawable-xhdpi, 320x180 как сейчас)
        banner = bb.make_banner(mark, 1280, 720)
        banner.resize((320, 180), Image.LANCZOS).convert("RGB").save(
            os.path.join(RES, "drawable-xhdpi", f"owntv_banner_{color}.png"))

        # in-app mark (large + small = один и тот же вектор знака)
        mv = ba.vector_mark(p, 240, splash=False)
        write(os.path.join(RES, "drawable", f"owntv_mark_{color}.xml"), mv)
        write(os.path.join(RES, "drawable", f"owntv_mark_small_{color}.xml"), mv)

        # splash static + animated (plain vector, содержимое в круге)
        sv = ba.vector_mark(p, 288, splash=True)
        write(os.path.join(RES, "drawable", f"owntv_splash_static_{color}.xml"), sv)
        write(os.path.join(RES, "drawable", f"owntv_splash_{color}.xml"), sv)

        # splash_name branding-image по плотностям
        for dp, ww, hh in NAME_DENSITIES:
            save_png(render_splash_name(p, ww, hh),
                     os.path.join(RES, f"drawable-{dp}", f"owntv_splash_name_{color}.png"))

        # adaptive xml (структура прежняя: background-токен + foreground + monochrome)
        write(os.path.join(RES, "mipmap-anydpi-v26", f"owntv_icon_{color}.xml"), adaptive_xml(color))

    bb.set_palette(bb.default_palette())
    print("rendered 9 variants into", RES)


if __name__ == "__main__":
    main()
