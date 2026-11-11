# -*- coding: utf-8 -*-
"""МОЁ ТВ — полная генерация артефактов для Android / Android TV.

Для оригинальной схемы и для каждой из 12 цветовых собирается готовый проект:
  android_res/                   готовый res/ для app/src/main/res
  android_extra/                 AndroidManifest.snippet.xml + ассеты Google Play
  android_variants/<slug>/res/   готовый res/ для выбранной схемы
  android_variants/<slug>/play/  ассеты магазина для этой схемы

Внутри res/:
  mipmap-*        48dp иконки + 108dp слои AdaptiveIconDrawable + монохромный слой
  mipmap-anydpi-* адаптивные иконки v26 и v33 (themed icon)
  drawable-*      баннер 320x180 dp: mdpi / tvdpi / hdpi / xhdpi / xxhdpi / xxxhdpi
  drawable/       векторные tv_mark, splash_icon 288dp, ic_stat_moe_tv 24dp,
                  ic_launcher_monochrome
  values/         colors.xml, strings.xml, тема Leanback
  values-v31/     тема с атрибутами SplashScreen API 31+

Запуск: python build_android.py
"""
import os
import shutil
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont
import build_brand as bb
import build_variants as bv

BASE = os.path.dirname(os.path.abspath(__file__))
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"

# папка, размер обычной иконки 48dp, размер слоя адаптивной иконки 108dp
DENSITIES = [("mipmap-mdpi", 48, 108), ("mipmap-hdpi", 72, 162),
             ("mipmap-xhdpi", 96, 216), ("mipmap-xxhdpi", 144, 324),
             ("mipmap-xxxhdpi", 192, 432)]
# баннер 320x180 dp во всех плотностях ТВ и мобильных устройств
BANNERS = [("drawable", 320, 180), ("drawable-tvdpi", 426, 240),
           ("drawable-hdpi", 480, 270), ("drawable-xhdpi", 640, 360),
           ("drawable-xxhdpi", 960, 540), ("drawable-xxxhdpi", 1280, 720)]


def hexc(c, alpha=True):
    r, g, b = (max(0, min(255, int(v))) for v in c)
    return ("#FF%02X%02X%02X" if alpha else "#%02X%02X%02X") % (r, g, b)


def n(v):
    return "%g" % v


def rr(x0, y0, x1, y1, r):
    """pathData скруглённого прямоугольника."""
    return (f"M{n(x0 + r)},{n(y0)} H{n(x1 - r)} A{n(r)},{n(r)} 0 0 1 {n(x1)},{n(y0 + r)} "
            f"V{n(y1 - r)} A{n(r)},{n(r)} 0 0 1 {n(x1 - r)},{n(y1)} H{n(x0 + r)} "
            f"A{n(r)},{n(r)} 0 0 1 {n(x0)},{n(y1 - r)} V{n(y0 + r)} "
            f"A{n(r)},{n(r)} 0 0 1 {n(x0 + r)},{n(y0)} Z")


def circ(cx, cy, r):
    """pathData окружности кубическими кривыми Безье."""
    k = 0.5522847498
    return (f"M{n(cx)},{n(cy - r)} "
            f"C{n(cx + k * r)},{n(cy - r)} {n(cx + r)},{n(cy - k * r)} {n(cx + r)},{n(cy)} "
            f"C{n(cx + r)},{n(cy + k * r)} {n(cx + k * r)},{n(cy + r)} {n(cx)},{n(cy + r)} "
            f"C{n(cx - k * r)},{n(cy + r)} {n(cx - r)},{n(cy + k * r)} {n(cx - r)},{n(cy)} "
            f"C{n(cx - r)},{n(cy - k * r)} {n(cx - k * r)},{n(cy - r)} {n(cx)},{n(cy - r)} Z")


# ---------------------------------------------------------------- монохромный слой
def make_monochrome(px=512):
    """Белый силуэт «ТВ с рожками» на прозрачном холсте 108×108 dp."""
    S, W = 3, px * 3
    k = W / 108.0
    P = lambda x, y: (int(round(x * k)), int(round(y * k)))
    R = lambda x0, y0, x1, y1: [*P(x0, y0), *P(x1, y1)]
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    white = (255, 255, 255, 255)
    w = int(4 * k)
    d.rounded_rectangle(R(20, 38, 88, 80), radius=int(5 * k), outline=white, width=w)
    aw = int(3.5 * k)
    d.line([P(54, 38), P(32, 22)], fill=white, width=aw)
    d.line([P(54, 38), P(76, 22)], fill=white, width=aw)
    for x, y in ((32, 22), (76, 22), (54, 38)):
        cx, cy = P(x, y)
        r = int(3 * k)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=white)
    d.line([P(54, 80), P(54, 84)], fill=white, width=w)
    d.line([P(44, 84), P(64, 84)], fill=white, width=w)
    return im.resize((px, px), Image.LANCZOS)


# ---------------------------------------------------------------- векторные drawable
def vector_paths(p):
    """Плоский вектор знака в координатах 1000×1000. Возвращает 3 блока:
    до экрана / содержимое экрана (клипуется) / после экрана."""
    C = lambda c: hexc(c, alpha=False)
    ant = bb.mix(p.body1, p.body2, 0.25)
    body = bb.mix(p.body1, p.body2, 0.35)
    chunks = {"before": [], "screen": [], "after": []}
    cur = chunks["before"]

    def path(data, fill=None, stroke=None, width=None, alpha=None, cap=None, fill_alpha=None):
        a = ['    <path android:pathData="%s"' % data]
        a.append('android:fillColor="%s"' % (fill if fill else "#00000000"))
        if fill_alpha is not None:
            a.append('android:fillAlpha="%s"' % fill_alpha)
        if stroke:
            a.append('android:strokeColor="%s"' % stroke)
            a.append('android:strokeWidth="%s"' % n(width))
        if alpha is not None:
            a.append('android:strokeAlpha="%s"' % alpha)
        if cap:
            a.append('android:strokeLineCap="%s"' % cap)
        cur.append(" ".join(a) + "/>")

    rods = "M500,140 L285,22 M500,140 L715,22"
    path(rr(456, 104, 544, 170, 14), fill=C(p.body2))            # основание антенны
    path(rods, stroke=C(p.bezel), width=30, cap="round")
    path(rods, stroke=C(ant), width=21, cap="round")
    for x in (285, 715):
        path(circ(x, 22, 25), fill=C(p.bezel))
        path(circ(x, 22, 18), fill=C(p.accent))
    path(circ(500, 140, 28), fill=C(p.body2))
    path(circ(500, 140, 13), fill=C(p.accent))
    path(rr(452, 700, 548, 792, 14), fill=C(p.neck1))             # ножка
    path(rr(326, 776, 674, 818, 21), fill=C(p.body1))              # подставка
    path(rr(70, 150, 930, 730, 58), fill=C(body))                  # корпус
    path(rr(70, 150, 930, 730, 58), stroke=C(bv.lighten(p.body1, 0.55)),
         width=5, alpha=0.3)
    path(rr(108, 188, 892, 692, 38), fill=C(p.bezel))              # рамка
    path(rr(130, 210, 870, 670, 24), fill=C(p.glass1))             # стекло

    cur = chunks["screen"]
    grid = " ".join("M%s,210 V610" % n(130 + i * 740 / 6) for i in range(1, 6))
    grid += " " + " ".join("M130,%s H870" % n(210 + j * 100) for j in range(1, 4))
    path(grid, stroke=C(p.grid), width=2.6, alpha=0.38)
    path("M500,210 V610 M130,410 H870", stroke=C(p.axes), width=3.2, alpha=0.6)
    path(circ(500, 410, 88), stroke=C(p.circle), width=5, alpha=0.85)
    path(circ(500, 410, 9), fill=C(p.accent))
    path("M130,244 V210 H164 M836,210 H870 V244 M130,576 V610 H164 M836,610 H870 V576",
         stroke=C(p.marks), width=5, alpha=0.78)
    path("M246,302 h15 v15 h-15 Z", fill=C(p.sq1), fill_alpha=0.75)
    path("M739,502 h15 v15 h-15 Z", fill=C(p.sq2), fill_alpha=0.75)
    path("M130,608 H870 V612 H130 Z", fill=C(p.sep))
    for i, col in enumerate(p.bars):
        x0, x1 = 130 + i * 740 / 7, 130 + (i + 1) * 740 / 7
        path("M%s,612 H%s V670 H%s Z" % (n(x0), n(x1), n(x0)),
             fill=C(col), fill_alpha=0.94)
    path("M130,210 H463 L130,463 Z", fill="#FFFFFF", fill_alpha=0.05)

    cur = chunks["after"]
    path(rr(130, 210, 870, 670, 24), stroke=C(p.scr_out), width=3, alpha=0.4)
    path(rr(474, 694, 526, 706, 6), fill=C(p.power), fill_alpha=0.82)
    return {k: "\n".join(v) for k, v in chunks.items()}


def vector_mark(p, width_dp=240, splash=False):
    """Вектор знака; для splash — холст 288dp, содержимое в круге Ø192dp."""
    head = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%ddp" android:height="%ddp"\n'
            '    android:viewportWidth="1000" android:viewportHeight="1000">\n' % (width_dp, width_dp))
    v = vector_paths(p)
    parts = [head]
    if splash:
        parts.append('  <group android:scaleX="0.55" android:scaleY="0.55"'
                     ' android:pivotX="500" android:pivotY="485">\n')
    parts.append('  <group android:translateY="76">\n')
    parts.append(v["before"] + "\n")
    parts.append('    <group>\n')
    parts.append('      <clip-path android:pathData="%s"/>\n' % rr(130, 210, 870, 670, 24))
    parts.append(v["screen"] + "\n")
    parts.append('    </group>\n')
    parts.append(v["after"] + "\n")
    parts.append('  </group>\n')
    if splash:
        parts.append('  </group>\n')
    parts.append('</vector>\n')
    return "".join(parts)


def vector_notification():
    white = "#FFFFFFFF"
    paths = (f'  <path android:fillColor="{white}" android:fillType="evenOdd"\n'
             f'        android:pathData="{rr(2, 8, 22, 19, 2)} {rr(4, 10, 20, 17, 1)}"/>\n'
             f'  <path android:fillColor="#00000000" android:strokeColor="{white}"\n'
             f'        android:strokeWidth="1.6" android:strokeLineCap="round"\n'
             f'        android:pathData="M12,8 L5,2 M12,8 L19,2"/>\n'
             f'  <path android:fillColor="{white}" android:pathData="{circ(5, 2, 1.2)} {circ(19, 2, 1.2)}"/>\n'
             f'  <path android:fillColor="{white}" android:pathData="M10,19 H14 V21 H10 Z"/>\n')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24">\n'
            + paths + '</vector>\n')


def vector_monochrome():
    white = "#FFFFFFFF"
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'  <path android:fillColor="#00000000" android:strokeColor="{white}"\n'
            f'        android:strokeWidth="4" android:pathData="{rr(20, 38, 88, 80, 5)}"/>\n'
            f'  <path android:fillColor="#00000000" android:strokeColor="{white}"\n'
            f'        android:strokeWidth="3.5" android:strokeLineCap="round"\n'
            f'        android:pathData="M54,38 L32,22 M54,38 L76,22 M54,80 V84 M44,84 H64"/>\n'
            f'  <path android:fillColor="{white}" android:pathData="'
            f'{circ(32, 22, 3)} {circ(76, 22, 3)} {circ(54, 38, 3)}"/>\n'
            '</vector>\n')


# ---------------------------------------------------------------- XML ресурсов
def adaptive_xml(mono=False):
    m = ('    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
         if mono else "")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
            + m + '</adaptive-icon>\n')


def values_xml(p):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            f'    <color name="brand_bg">{hexc(p.bg2)}</color>\n'
            f'    <color name="brand_bg_top">{hexc(p.bg1)}</color>\n'
            f'    <color name="brand_primary">{hexc(p.body2)}</color>\n'
            f'    <color name="brand_accent">{hexc(p.accent)}</color>\n'
            f'    <color name="brand_cyan">{hexc(p.body1)}</color>\n'
            f'    <color name="brand_ink">{hexc(p.ink)}</color>\n'
            f'    <color name="splash_background">{hexc(p.bg2)}</color>\n'
            f'    <color name="splash_icon_background">{hexc(p.bg1)}</color>\n'
            '</resources>\n')


def strings_xml():
    return ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <string name="app_name">МОЁ ТВ</string>\n'
            '    <string name="app_name_short">МОЁ ТВ</string>\n'
            '    <string name="app_banner_label">МОЁ ТВ</string>\n'
            '</resources>\n')


def theme_xml(splash=False):
    items = ['        <item name="android:colorPrimary">@color/brand_primary</item>',
             '        <item name="android:colorAccent">@color/brand_accent</item>',
             '        <item name="android:windowBackground">@color/brand_bg</item>']
    if splash:
        items += [
            '        <item name="android:windowSplashScreenBackground">@color/splash_background</item>',
            '        <item name="android:windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>',
            '        <item name="android:windowSplashScreenIconBackgroundColor">@color/splash_icon_background</item>',
            '        <item name="android:windowSplashScreenAnimationDuration">1000</item>',
        ]
    return ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <style name="Theme.MoeTV" parent="@style/Theme.Leanback">\n'
            + "\n".join(items) + '\n    </style>\n</resources>\n')


MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Фрагмент AndroidManifest.xml для «МОЁ ТВ» (Android TV).
     Это не готовый манифест: перенесите атрибуты <application> и <activity>
     в свой AndroidManifest.xml. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="true"
        android:banner="@drawable/tv_banner"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.MoeTV">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:banner="@drawable/tv_banner"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:theme="@style/Theme.MoeTV">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'''


# ---------------------------------------------------------------- Google Play
def make_feature_graphic(mark, p, w=1024, h=500):
    bg = bb.make_bg(w, h,
                    glows=[(0.22, 0.5, 0.55, p.bglow1, 0.62 * p.glow_scale, 0.85),
                           (0.85, 0.2, 0.45, p.bglow2, 0.18 * p.glow_scale, 0.9)],
                    grid_step=64, vignette=p.vignette_banner, diag=False)
    m = mark.resize((330, 330), Image.LANCZOS)
    bg.alpha_composite(m, (30, 86))
    parts = [("МОЁ", 18, (*p.ink, 255)), ("ТВ", 0, (*p.accent, 255))]
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    size, tracking = 120, 5
    while size > 30:
        font = ImageFont.truetype(FONT_BOLD, size)
        if bb._tracked_width(probe, parts, font, tracking) <= 570:
            break
        size -= 2
    font = ImageFont.truetype(FONT_BOLD, size)
    y = 155
    bg, _ = bb.draw_wordmark(bg, parts, font, tracking, 400, y)

    def bars(d):
        bx, by, bw, bh = 400, y + size + 26, 260, 10
        for i, col in enumerate(p.bars):
            d.rectangle([int(bx + i * bw / 7), by,
                         int(bx + (i + 1) * bw / 7), by + bh], fill=(*col, 215))
    return bb._shapes(bg, bars).convert("RGB")


# ---------------------------------------------------------------- сборка
def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def build_res(root, p, mark):
    """Готовое дерево res/ для одной палитры."""
    square = bb.compose_icon(mark, 512, "square")
    round_ = bb.compose_icon(mark, 512, "round")
    flat = bb.compose_icon(mark, 512, "fullbleed")
    fg = bb.adaptive_foreground(mark, 512)      # холст 108dp
    bg = bb.adaptive_background(512)            # холст 108dp
    mono = make_monochrome(512)                 # холст 108dp
    banner = bb.make_banner(mark, 1280, 720)

    for folder, icon_px, layer_px in DENSITIES:
        d = os.path.join(root, folder)
        os.makedirs(d, exist_ok=True)
        square.resize((icon_px, icon_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
        round_.resize((icon_px, icon_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_round.png"))
        flat.resize((icon_px, icon_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_flat.png"))
        # 108 x 108 dp — обязательный размер слоёв AdaptiveIconDrawable
        fg.resize((layer_px, layer_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"))
        bg.resize((layer_px, layer_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_background.png"))
        mono.resize((layer_px, layer_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_monochrome.png"))

    for folder, w, h in BANNERS:
        d = os.path.join(root, folder)
        os.makedirs(d, exist_ok=True)
        banner.resize((w, h), Image.LANCZOS).save(os.path.join(d, "tv_banner.png"))

    for folder in ("mipmap-anydpi-v26", "mipmap-anydpi-v33"):
        os.makedirs(os.path.join(root, folder), exist_ok=True)
        mono_xml = folder.endswith("v33")
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            write(os.path.join(root, folder, name), adaptive_xml(mono_xml))

    dr = os.path.join(root, "drawable")
    write(os.path.join(dr, "tv_mark.xml"), vector_mark(p, 240, splash=False))
    write(os.path.join(dr, "splash_icon.xml"), vector_mark(p, 288, splash=True))
    write(os.path.join(dr, "ic_stat_moe_tv.xml"), vector_notification())
    write(os.path.join(dr, "ic_launcher_monochrome.xml"), vector_monochrome())

    write(os.path.join(root, "values", "colors.xml"), values_xml(p))
    write(os.path.join(root, "values", "strings.xml"), strings_xml())
    write(os.path.join(root, "values", "themes.xml"), theme_xml(False))
    write(os.path.join(root, "values-v31", "themes.xml"), theme_xml(True))
    return flat, banner


def build_play(folder, flat, banner, mark, p):
    os.makedirs(folder, exist_ok=True)
    flat.convert("RGB").save(os.path.join(folder, "play_store_icon_512.png"))
    banner.save(os.path.join(folder, "tv_banner_1280x720.png"))
    make_feature_graphic(mark, p).save(os.path.join(folder, "feature_graphic_1024x500.png"))


def build_bundle(out, p, mark, res_subdir, label):
    """out/res_subdir — дерево res/, out/play, out/AndroidManifest.snippet.xml."""
    bb.set_palette(p)
    flat, banner = build_res(os.path.join(out, res_subdir), p, mark)
    build_play(os.path.join(out, "play"), flat, banner, mark, p)
    write(os.path.join(out, "AndroidManifest.snippet.xml"), MANIFEST)
    print("  ", label, "->", out)


def validate(root):
    bad = []
    for base, _, files in os.walk(root):
        for f in files:
            if f.endswith(".xml"):
                try:
                    ET.parse(os.path.join(base, f))
                except Exception as e:
                    bad.append((os.path.join(base, f), str(e)))
    return bad


def main():
    for folder in ("android_res", "android_extra", "android_variants"):
        p = os.path.join(BASE, folder)
        if os.path.isdir(p):
            shutil.rmtree(p)

    # --- оригинал: android_res/ как готовый res/ ---
    pal0 = bb.default_palette()
    bb.set_palette(pal0)
    mark0 = bb.make_tv_mark(1200)
    flat, banner = build_res(os.path.join(BASE, "android_res"), pal0, mark0)
    build_play(os.path.join(BASE, "android_extra", "play"), flat, banner, mark0, pal0)
    write(os.path.join(BASE, "android_extra", "AndroidManifest.snippet.xml"), MANIFEST)
    print("  00 · Глубокий синий -> android_res/ + android_extra/")

    # --- 12 схем ---
    for spec in bv.SPECS:
        p = bv.make_palette(spec)
        bb.set_palette(p)
        mark = bb.make_tv_mark(1200)
        build_bundle(os.path.join(BASE, "android_variants", spec[0]), p, mark,
                     "res", spec[1])

    bb.set_palette(bb.default_palette())
    problems = validate(os.path.join(BASE, "android_res"))
    problems += validate(os.path.join(BASE, "android_extra"))
    problems += validate(os.path.join(BASE, "android_variants"))
    print("XML validation:", "OK" if not problems else problems)


if __name__ == "__main__":
    main()
