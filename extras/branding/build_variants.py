# -*- coding: utf-8 -*-
"""МОЁ ТВ — 12 цветовых схем.

Использует тот же рисующий движок, что и build_brand.py, но подменяет палитру.
Для каждой схемы: квадратная иконка, круглая иконка, плоский баннер Android TV.
Плюс галерея variants.html и контактный лист variants/contact_sheet.png.

Запуск: python build_variants.py
"""
import os
from PIL import Image, ImageDraw, ImageFont
import build_brand as bb

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "variants")
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG = "C:/Windows/Fonts/segoeui.ttf"


def h(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(x * (1 - t) + y * t)) for x, y in zip(a, b))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)


def hexs(c):
    return "#%02X%02X%02X" % tuple(max(0, min(255, int(v))) for v in c)


CANON = [(240, 246, 255), (255, 210, 63), (53, 214, 232), (59, 215, 122),
         (232, 83, 182), (242, 73, 75), (46, 99, 246)]
BARS_GREEN = [(225, 255, 215), (175, 255, 155), (125, 240, 112), (85, 220, 92),
              (52, 190, 76), (30, 150, 60), (14, 100, 44)]
BARS_GRAY = [(245, 247, 250), (215, 220, 228), (185, 192, 202), (150, 158, 170),
             (115, 124, 138), (80, 88, 102), (45, 50, 60)]
BARS_GOLD = [(255, 248, 220), (255, 225, 150), (245, 195, 95), (225, 160, 55),
             (190, 125, 35), (145, 90, 25), (95, 60, 20)]
BARS_PASTEL = [(255, 245, 250), (255, 225, 180), (190, 240, 235), (205, 235, 195),
               (240, 195, 225), (250, 190, 190), (190, 205, 250)]


# slug, название, фон1, фон2, корпус1, корпус2, акцент, текст, «тёмная база» экрана, полосы, светлая
SPECS = [
    ("01_neon",       "Неон",             "#1A0330", "#08010F", "#FF4FD8", "#7B2BFF", "#4BF5FF", "#FFFFFF", "#120323", CANON,       False),
    ("02_ember",      "Янтарь",           "#2E1505", "#120802", "#FFC25C", "#E0561B", "#FFD166", "#FFF6E8", "#1B0D04", CANON,       False),
    ("03_emerald",    "Изумруд",          "#032A22", "#010F0C", "#63F5B0", "#0E9B72", "#7BFFC9", "#F0FFF8", "#041A16", CANON,       False),
    ("04_crimson",    "Красный канал",    "#2E0710", "#100206", "#FF8A80", "#D01F3C", "#FF9AA2", "#FFF2F3", "#1B0409", CANON,       False),
    ("05_royal",      "Королевский",      "#1E0B45", "#0A041E", "#C3A4FF", "#6A32D9", "#D9BCFF", "#F7F1FF", "#120830", CANON,       False),
    ("06_phosphor",   "Фосфор CRT",       "#04120A", "#000603", "#9BFF7A", "#1E9F4A", "#B6FF8A", "#E8FFE0", "#03160B", BARS_GREEN,  False),
    ("07_mono",       "Монохром",         "#20252E", "#0A0C10", "#F2F5FA", "#79879B", "#FFFFFF", "#FFFFFF", "#101318", BARS_GRAY,   False),
    ("08_arctic",     "Лёд · светлая",    "#EAF6FF", "#BFD9EF", "#1B4FA8", "#0A2A66", "#0B63D6", "#102A4C", "#0A1B33", CANON,       True),
    ("09_sand",       "Песок · светлая",  "#FFF6E6", "#EBD3B0", "#3A3F4B", "#141A24", "#C2410C", "#2A2118", "#1B1712", CANON,       True),
    ("10_sunset",     "Закат",            "#3A1050", "#170A2E", "#FFB25C", "#E0457B", "#FFC978", "#FFF3EC", "#230B2B", CANON,       False),
    ("11_graphite",   "Графит + золото",  "#1C1D21", "#08090B", "#F5D98B", "#B8862B", "#FFD97A", "#FFF8E7", "#141312", BARS_GOLD,   False),
    ("12_candy",      "Карамель · светлая", "#FFF0F5", "#F6D7E7", "#7A3E9D", "#3B1D5C", "#D6247A", "#2E1030", "#24102E", BARS_PASTEL, True),
]


def make_palette(spec):
    slug, title, bg1, bg2, body1, body2, accent, ink, dark, bars, light = spec
    bg1, bg2 = h(bg1), h(bg2)
    body1, body2 = h(body1), h(body2)
    accent, ink, dark = h(accent), h(ink), h(dark)
    d = bb.default_palette().__dict__.copy()
    d.update(
        bg1=bg1, bg2=bg2, body1=body1, body2=body2, accent=accent, ink=ink,
        bars=bars,
        glass1=mix(dark, body2, 0.32), glass2=darken(dark, 0.05),
        bezel=darken(dark, 0.40),
        glow1=mix(body2, accent, 0.35), glow2=accent,
        bglow1=mix(body2, accent, 0.30), bglow2=mix(bg1, accent, 0.35),
        sglow=mix(dark, accent, 0.30), radial=mix(bg1, accent, 0.38),
        grid=lighten(accent, 0.28), axes=lighten(accent, 0.60),
        circle=lighten(accent, 0.80), marks=lighten(accent, 0.75),
        dot=lighten(accent, 0.18), power=lighten(accent, 0.35),
        sq1=bars[4], sq2=bars[1],
        sep=darken(dark, 0.55), scr_out=darken(dark, 0.65),
        body_out=lighten(body1, 0.55), base_out=lighten(body1, 0.55),
        halo=accent, shadow=darken(dark, 0.70),
        grid_ov=darken(bg2, 0.35) if light else lighten(accent, 0.45),
        ring=lighten(accent, 0.45),
        neck1=body2, neck2=darken(body2, 0.45),
        base1=body1, base2=body2,
        mark_glow=mix(accent, body1, 0.50),
        mark_shadow=mix(dark, bg2, 0.45) if light else darken(dark, 0.60),
        glow_scale=0.35 if light else 1.0,
        halo_alpha=0.14 if light else 0.38,
        shadow_alpha=0.28 if light else 0.45,
        vignette_icon=0.10 if light else 0.26,
        vignette_banner=0.10 if light else 0.30,
        light=light,
    )
    return bb.Palette(d)


def render_for(pal, folder):
    bb.set_palette(pal)
    os.makedirs(folder, exist_ok=True)
    mark = bb.make_tv_mark(1200)
    square = bb.compose_icon(mark, 512, "square")
    round_ = bb.compose_icon(mark, 512, "round")
    flat = bb.compose_icon(mark, 512, "fullbleed")   # прямые углы, без скругления
    banner = bb.make_banner(mark, 1280, 720)         # RGB, жёсткий прямоугольник без скруглений
    square.save(os.path.join(folder, "ic_launcher.png"))
    round_.save(os.path.join(folder, "ic_launcher_round.png"))
    flat.save(os.path.join(folder, "ic_launcher_flat.png"))
    banner.save(os.path.join(folder, "tv_banner.png"))
    return square, round_, banner


def contact_sheet(entries, path):
    """3 колонки × 5 рядов: сравнение всех схем + оригинал."""
    CW, CH, COLS = 420, 380, 3
    rows = (len(entries) + COLS - 1) // COLS
    sheet = Image.new("RGB", (CW * COLS, CH * rows + 74), (244, 247, 252))
    d = ImageDraw.Draw(sheet)
    f_title = ImageFont.truetype(FONT_BOLD, 30)
    f_sub = ImageFont.truetype(FONT_REG, 16)
    f_name = ImageFont.truetype(FONT_BOLD, 19)
    f_small = ImageFont.truetype(FONT_REG, 13)
    d.text((28, 16), "МОЁ ТВ · цветовые схемы", font=f_title, fill=(18, 32, 58))
    d.text((30, 53), "13 вариантов · антенна-«рожки» · плитка Android TV плоская, без скруглений",
           font=f_sub, fill=(92, 107, 133))

    for i, e in enumerate(entries):
        col, row = i % COLS, i // COLS
        x0, y0 = col * CW + 14, 74 + row * CH + 12
        d.rounded_rectangle([x0, y0, x0 + CW - 28, y0 + 356], radius=12,
                            fill=(255, 255, 255), outline=(223, 230, 240), width=1)
        d.text((x0 + 16, y0 + 13), e["label"], font=f_name, fill=(18, 32, 58))
        # образцы цветов
        for j, c in enumerate(e["swatches"]):
            sx = x0 + 16 + j * 26
            d.rectangle([sx, y0 + 42, sx + 22, y0 + 58], fill=c,
                        outline=(220, 228, 238), width=1)
        # плоская плитка — ничего не закругляем
        b = e["banner"].resize((360, 203), Image.LANCZOS)
        sheet.paste(b, (x0 + 16, y0 + 68))
        # иконки
        for j, key in enumerate(("square", "round")):
            ic = e[key].resize((72, 72), Image.LANCZOS).convert("RGBA")
            px, py = x0 + 16 + j * 84, y0 + 282
            sheet.paste(ic, (px, py), ic)
        d.text((x0 + 190, y0 + 294), "512 × 512", font=f_small, fill=(92, 107, 133))
        d.text((x0 + 190, y0 + 312), "баннер 1280 × 720", font=f_small, fill=(92, 107, 133))
    sheet.save(path)


def gallery_html(entries, path):
    css = """
    :root{--ink:#12203a;--muted:#5c6b85;--line:#dfe6f0}
    *{box-sizing:border-box}
    body{margin:0;background:#f4f7fc;color:var(--ink);font:15px/1.5 "Segoe UI",Arial,sans-serif;padding:40px}
    .wrap{max-width:1280px;margin:0 auto}
    h1{font-size:30px;margin:0 0 4px;letter-spacing:-.5px}
    .sub{color:var(--muted);margin-bottom:12px}
    .note{display:inline-block;background:#e7f1ff;border:1px solid #c9dfff;color:#124a94;
      padding:8px 14px;border-radius:0;font-size:13px;margin-bottom:28px}
    .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:22px}
    .card{background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;
      box-shadow:0 6px 22px rgba(20,45,90,.06)}
    .head{padding:16px 18px 12px;display:flex;justify-content:space-between;align-items:center;gap:10px}
    .head b{font-size:16px} .head span{font-size:12px;color:var(--muted)}
    .sw{display:flex;gap:4px;padding:0 18px 12px}
    .sw i{width:26px;height:12px;display:block;border:1px solid #00000012}
    .stage{padding:18px;position:relative}
    .stage .banner{display:block;width:100%;border-radius:0;box-shadow:none}
    .icons{display:flex;gap:14px;align-items:center;margin-top:16px}
    .icons img{width:72px;height:72px;display:block}
    .icons small{color:var(--muted);margin-left:auto;font-size:11px;text-align:right}
    footer{margin-top:32px;padding-top:18px;border-top:1px solid var(--line);
      color:var(--muted);font-size:13px}
    code{background:#eef3fa;padding:2px 6px;border-radius:4px}
    """
    parts = ["<!doctype html><html lang='ru'><head><meta charset='utf-8'>",
             "<title>МОЁ ТВ · цветовые схемы</title><style>", css,
             "</style></head><body><div class='wrap'>",
             "<h1>МОЁ ТВ · цветовые схемы</h1>",
             "<div class='sub'>13 вариантов: оригинал + 12 новых палитр. Знак с антенной-«рожками», сетка и название неизменны — меняется только цвет.</div>",
             "<div class='note'>Плитка Android TV — плоский прямоугольник, без скруглений и теней (и в файлах, и в превью).</div>",
             "<div class='grid'>"]
    for e in entries:
        parts.append("<div class='card'>")
        parts.append("<div class='head'><b>%s</b><span>%s</span></div>" % (e["label"], e["tag"]))
        parts.append("<div class='sw'>" + "".join(
            "<i style='background:%s'></i>" % hexs(c) for c in e["swatches"]) + "</div>")
        parts.append("<div class='stage' style='background:linear-gradient(135deg,%s,%s)'>"
                     % (hexs(e["swatches"][0]), hexs(e["swatches"][1])))
        parts.append("<img class='banner' src='%s' alt='баннер'>" % e["banner_rel"])
        parts.append("<div class='icons'><img src='%s' alt='иконка'><img src='%s' alt='круглая иконка'>"
                     "<small>512 × 512<br>баннер 1280 × 720</small></div>" % (e["sq_rel"], e["rd_rel"]))
        parts.append("</div></div>")
    parts.append("</div><footer>Превью: <code>variants/&lt;схема&gt;/</code>. "
                 "Для каждой схемы уже собран полный комплект Android в "
                 "<code>android_variants/&lt;схема&gt;/res/</code> (иконки всех плотностей, "
                 "108dp-слои, баннеры, темы, манифест, Google Play). "
                 "Пересборка: <code>python build_variants.py &amp;&amp; python build_android.py</code>.</footer>"
                 "</div></body></html>")
    with open(path, "w", encoding="utf-8") as f:
        f.write("".join(parts))


def main():
    os.makedirs(OUT, exist_ok=True)
    entries = []

    # 00 — оригинал как точка сравнения
    orig = {
        "label": "00 · Глубокий синий (оригинал)", "tag": "эталон",
        "swatches": [h("#0C2C69"), h("#061029"), h("#1F60D8"), h("#3ADEEF"), h("#48DEFF")],
        "banner": Image.open(os.path.join(BASE, "master", "tv_banner.png")),
        "square": Image.open(os.path.join(BASE, "master", "ic_launcher.png")).convert("RGBA"),
        "round": Image.open(os.path.join(BASE, "master", "ic_launcher_round.png")).convert("RGBA"),
        "banner_rel": "master/tv_banner.png",
        "sq_rel": "master/ic_launcher.png",
        "rd_rel": "master/ic_launcher_round.png",
    }
    entries.append(orig)

    for spec in SPECS:
        pal = make_palette(spec)
        folder = os.path.join(OUT, spec[0])
        sq, rd, bn = render_for(pal, folder)
        entries.append({
            "label": "%s · %s" % (spec[0][:2], spec[1]),
            "tag": "светлая" if spec[10] else "тёмная",
            "swatches": [h(spec[2]), h(spec[3]), h(spec[6]), pal.body1, pal.accent],
            "banner": bn, "square": sq, "round": rd,
            "banner_rel": "variants/%s/tv_banner.png" % spec[0],
            "sq_rel": "variants/%s/ic_launcher.png" % spec[0],
            "rd_rel": "variants/%s/ic_launcher_round.png" % spec[0],
        })
        print(" ", spec[0], spec[1])

    contact_sheet(entries, os.path.join(OUT, "contact_sheet.png"))
    gallery_html(entries, os.path.join(BASE, "variants.html"))
    bb.set_palette(bb.default_palette())  # вернуть эталонную палитру
    print("OK ->", OUT, "| variants.html")


if __name__ == "__main__":
    main()
