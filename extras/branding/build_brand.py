# -*- coding: utf-8 -*-
"""МОЁ ТВ — генератор брендинга для Android TV.

Рисует единый знак (телевизор с настроечной сеткой) и собирает из него:
  - ic_launcher.png            квадратная иконка 512x512
  - ic_launcher_round.png      круглая иконка 512x512
  - tv_banner.png              баннер 1280x720 с названием приложения
  - слои адаптивной иконки, набор плотностей и превью

Всё рисуется с суперсэмплингом, текст — системным шрифтом с кириллицей,
поэтому «Ё» и сетка остаются чёткими на любом размере.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

BASE = os.path.dirname(os.path.abspath(__file__))
MASTER = os.path.join(BASE, "master")
RES = os.path.join(BASE, "android_res")
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG = "C:/Windows/Fonts/segoeui.ttf"

# ---------------------------------------------------------------- палитра
BARS = [(240, 246, 255), (255, 210, 63), (53, 214, 232), (59, 215, 122),
        (232, 83, 182), (242, 73, 75), (46, 99, 246)]


class Palette:
    """Полный набор цветов брендинга. Заменяется через set_palette()."""
    def __init__(self, d):
        self.__dict__.update(d)


def default_palette():
    """Оригинальная схема «Глубокий синий» — эталон, менять нельзя."""
    return Palette(dict(
        bg1=(12, 44, 105), bg2=(4, 11, 30),
        body1=(86, 238, 250), body2=(26, 88, 208),
        accent=(72, 222, 255), ink=(255, 255, 255),
        glass1=(10, 40, 82), glass2=(3, 10, 26), bezel=(4, 14, 34),
        bars=BARS,
        glow1=(24, 112, 220), glow2=(24, 190, 235),
        bglow1=(20, 120, 225), bglow2=(18, 90, 190),
        sglow=(18, 86, 150), radial=(16, 110, 205),
        grid=(130, 228, 255), axes=(210, 246, 255),
        circle=(240, 252, 255), marks=(255, 255, 255),
        dot=(90, 232, 255), power=(110, 240, 255),
        sq1=(255, 96, 178), sq2=(255, 214, 72),
        sep=(2, 10, 26), scr_out=(0, 3, 16),
        body_out=(255, 255, 255), base_out=(255, 255, 255),
        halo=(40, 190, 255), shadow=(0, 4, 20),
        grid_ov=(150, 215, 255), ring=(150, 230, 255),
        neck1=(36, 140, 228), neck2=(14, 52, 128),
        base1=(74, 232, 248), base2=(26, 92, 206),
        mark_glow=(28, 165, 240), mark_shadow=(0, 4, 18),
        glow_scale=1.0, halo_alpha=0.38, shadow_alpha=0.45,
        vignette_icon=0.26, vignette_banner=0.30,
        light=False,
    ))


PAL = default_palette()


def set_palette(p):
    global PAL
    PAL = p


def mix(a, b, t):
    return tuple(int(round(x * (1 - t) + y * t)) for x, y in zip(a, b))


# ---------------------------------------------------------------- helpers
def _shapes(base, fn):
    """Рисуем на отдельном слое и корректно композитим по альфе."""
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    fn(ImageDraw.Draw(layer))
    return Image.alpha_composite(base, layer)


def _grad_round(base, box, radius, c1, c2, direction="v"):
    """Скруглённый прямоугольник с плавным градиентом."""
    x0, y0, x1, y1 = [int(v) for v in box]
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    if direction == "v":
        t = yy / h
    elif direction == "h":
        t = xx / w
    else:  # diag
        t = np.clip(0.60 * xx / w + 0.40 * yy / h, 0, 1)
    a = np.array(c1, np.float32)
    b = np.array(c2, np.float32)
    rgb = a * (1 - t[..., None]) + b * t[..., None]
    rgba = np.dstack([rgb, np.full((h, w), 255, np.float32)]).astype(np.uint8)
    tile = Image.fromarray(rgba, "RGBA")
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1],
                                          radius=max(1, int(radius)), fill=255)
    tile.putalpha(mask)
    out = base.copy()
    out.alpha_composite(tile, (x0, y0))
    return out


def _radial(base, cx, cy, rad_x, rad_y, color, strength):
    """Мягкое свечение поверх RGBA-изображения с сохранением альфы."""
    w, h = base.size
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = (xx - cx) / rad_x
    dy = (yy - cy) / rad_y
    g = np.exp(-(dx * dx + dy * dy) * 1.7) * strength
    arr = np.asarray(base, np.float32).copy()
    arr[..., :3] += g[..., None] * np.array(color, np.float32)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")


def make_bg(w, h, glows=(), grid_step=0, vignette=0.22, diag=True):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    t = np.clip(0.55 * xx / w + 0.45 * yy / h, 0, 1) if diag else xx / w
    a = np.array(PAL.bg1, np.float32)
    b = np.array(PAL.bg2, np.float32)
    arr = a * (1 - t[..., None]) + b * t[..., None]
    for (cx, cy, rad, color, strength, ellipse) in glows:
        dx = (xx - cx * w) / (rad * w)
        dy = (yy - cy * h) / (rad * h * ellipse)
        arr += (np.exp(-(dx * dx + dy * dy) * 1.8) * strength)[..., None] * np.array(color, np.float32)
    dx = (xx - w / 2) / (w / 2)
    dy = (yy - h / 2) / (h / 2)
    arr *= (1 - vignette * np.clip(dx * dx + dy * dy, 0, 2) / 2)[..., None]
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    if grid_step:
        def grid(d):
            for x in range(0, w + grid_step, grid_step):
                d.line([(x, 0), (x, h)], fill=(*PAL.grid_ov, 10), width=1)
            for y in range(0, h + grid_step, grid_step):
                d.line([(0, y), (w, y)], fill=(*PAL.grid_ov, 10), width=1)
        img = _shapes(img, grid)
    return img


# ---------------------------------------------------------------- знак
def make_tv_mark(px=1200):
    """Знак в прозрачном квадрате. Внутренняя система координат 1000x1000."""
    ss = 2
    W = px * ss
    k = W / 1000.0
    R = lambda *v: tuple(int(round(x * k)) for x in v)
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))

    # мягкое свечение и тень под корпусом
    glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse(R(90, 130, 910, 840), fill=(*PAL.mark_glow, 52))
    im = Image.alpha_composite(im, glow.filter(ImageFilter.GaussianBlur(int(46 * k))))
    shadow = Image.new("RGBA", (W, W), (0, 0, 0, 0))

    def sh(d):
        d.rounded_rectangle(R(70, 180, 930, 758), radius=int(58 * k), fill=(*PAL.mark_shadow, 145))
        d.rounded_rectangle(R(330, 798, 670, 838), radius=int(20 * k), fill=(*PAL.mark_shadow, 130))
    _ = sh(ImageDraw.Draw(shadow))
    im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(int(24 * k))))

    # подставка (рисуется до корпуса)
    im = _grad_round(im, R(452, 700, 548, 792), 14 * k, PAL.neck1, PAL.neck2, "v")
    im = _grad_round(im, R(326, 776, 674, 818), 21 * k, PAL.base1, PAL.base2, "h")
    im = _shapes(im, lambda d: d.rounded_rectangle(R(326, 776, 674, 818), radius=int(21 * k),
                                                   outline=(*PAL.base_out, 55), width=max(1, int(3 * k))))

    # --- антенна «рожки» (рисуется до корпуса, основание перекроется панелью) ---
    ant = mix(PAL.body1, PAL.body2, 0.25)
    cx, cy = int(500 * k), int(140 * k)

    def antenna(d):
        d.rounded_rectangle(R(456, 104, 544, 170), radius=int(14 * k),
                            fill=(*PAL.body2, 255))
        for tx, ty in ((285, 22), (715, 22)):
            X, Y = int(tx * k), int(ty * k)
            d.line([(cx, cy), (X, Y)], fill=(*PAL.bezel, 255), width=int(30 * k))
            d.line([(cx, cy), (X, Y)], fill=(*ant, 255), width=int(21 * k))
            r = int(25 * k)
            d.ellipse([X - r, Y - r, X + r, Y + r], fill=(*PAL.bezel, 255))
            r2 = int(18 * k)
            d.ellipse([X - r2, Y - r2, X + r2, Y + r2], fill=(*PAL.accent, 255))
        r = int(28 * k)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(*PAL.body2, 255))
        r2 = int(13 * k)
        d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=(*PAL.accent, 235))
    im = _shapes(im, antenna)

    # корпус телевизора
    im = _grad_round(im, R(70, 150, 930, 730), 58 * k, PAL.body1, PAL.body2, "diag")
    im = _shapes(im, lambda d: d.rounded_rectangle(R(70, 150, 930, 730), radius=int(58 * k),
                                                   outline=(*PAL.body_out, 80), width=max(1, int(5 * k))))

    # рамка вокруг экрана
    im = _shapes(im, lambda d: d.rounded_rectangle(R(108, 188, 892, 692), radius=int(38 * k),
                                                   fill=(*PAL.bezel, 255)))

    # --- экран: отдельный слой, обрезанный по скруглению ---
    sw, shh = int(740 * k), int(460 * k)
    LX = lambda x: int(round((x - 130) * k))
    LY = lambda y: int(round((y - 210) * k))
    screen = Image.new("RGBA", (sw, shh), (0, 0, 0, 0))
    screen = _grad_round(screen, (0, 0, sw, shh), 24 * k, PAL.glass1, PAL.glass2, "v")
    screen = _radial(screen, sw * 0.5, shh * 0.42, sw * 0.62, shh * 0.62,
                     PAL.sglow, 0.55)

    def grid(d):
        lw = max(1, int(2.6 * k))
        for i in range(7):
            x = 130 + i * (740 / 6)
            d.line([(LX(x), LY(210)), (LX(x), LY(610))], fill=(*PAL.grid, 92), width=lw)
        for j in range(5):
            y = 210 + j * 100
            d.line([(LX(130), LY(y)), (LX(870), LY(y))], fill=(*PAL.grid, 92), width=lw)
        # центральные оси и перекрестие
        d.line([(LX(500), LY(210)), (LX(500), LY(610))], fill=(*PAL.axes, 150), width=max(1, int(3.2 * k)))
        d.line([(LX(130), LY(410)), (LX(870), LY(410))], fill=(*PAL.axes, 150), width=max(1, int(3.2 * k)))
        # прицельная окружность
        cx, cy, r = LX(500), LY(410), int(88 * k)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(*PAL.circle, 215), width=max(1, int(5 * k)))
        rr = int(9 * k)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(*PAL.dot, 255))
        # угловые метки
        L, w = int(34 * k), max(1, int(5 * k))
        for x, sx in ((LX(130), 1), (LX(870), -1)):
            for y, sy in ((LY(210), 1), (LY(610), -1)):
                d.line([(x, y), (x + sx * L, y)], fill=(*PAL.marks, 195), width=w)
                d.line([(x, y), (x, y + sy * L)], fill=(*PAL.marks, 195), width=w)
        # калибровочные квадраты
        for x, y, col in ((253.33, 310, (*PAL.sq1, 185)), (746.66, 510, (*PAL.sq2, 185))):
            s = int(15 * k)
            d.rectangle([LX(x) - s // 2, LY(y) - s // 2, LX(x) + s // 2, LY(y) + s // 2], fill=col)
    screen = _shapes(screen, grid)
    # блик стекла — отдельным слоем, чтобы не затирать сетку под собой
    screen = _shapes(screen, lambda d: d.polygon(
        [(0, 0), (int(sw * 0.45), 0), (0, int(shh * 0.55))], fill=(255, 255, 255, 13)))

    def bars(d):
        y0, y1 = LY(612), shh
        d.rectangle([0, LY(608), sw, LY(612)], fill=(*PAL.sep, 255))
        for i, c in enumerate(PAL.bars):
            x0, x1 = LX(130 + i * 740 / 7), LX(130 + (i + 1) * 740 / 7)
            d.rectangle([x0, y0, x1, y1], fill=(*c, 240))
    screen = _shapes(screen, bars)

    mask = Image.new("L", (sw, shh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, shh - 1], radius=int(24 * k), fill=255)
    screen.putalpha(ImageChops.multiply(screen.getchannel("A"), mask))
    im.alpha_composite(screen, (int(130 * k), int(210 * k)))

    # внутренняя обводка экрана и индикатор
    def finish(d):
        d.rounded_rectangle(R(130, 210, 870, 670), radius=int(24 * k),
                            outline=(*PAL.scr_out, 110), width=max(1, int(3 * k)))
        d.rounded_rectangle(R(474, 694, 526, 706), radius=int(6 * k), fill=(*PAL.power, 210))
    im = _shapes(im, finish)
    # антенна добавила массу сверху — опускаем всю композицию, чтобы знак
    # остался визуально отцентрирован в квадрате 1000×1000
    shift = int(76 * k)
    centered = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    centered.alpha_composite(im, (0, shift))
    return centered.resize((px, px), Image.LANCZOS)


# ---------------------------------------------------------------- иконки
def compose_icon(mark, size=512, kind="square"):
    bg = make_bg(size, size,
                 glows=[(0.5, 0.40, 0.62, PAL.glow1, 0.55 * PAL.glow_scale, 1.0),
                        (0.5, 0.82, 0.45, PAL.glow2, 0.16 * PAL.glow_scale, 1.0)],
                 grid_step=0, vignette=PAL.vignette_icon)
    frac = {"square": 0.78, "round": 0.72, "foreground": 0.76,
            "fullbleed": 0.78}[kind]
    m = mark.resize((int(size * frac),) * 2, Image.LANCZOS)
    off = (size - m.width) // 2
    bg.alpha_composite(m, (off, off))
    if kind == "round":
        bg = _shapes(bg, lambda d: d.ellipse([2, 2, size - 3, size - 3],
                                             outline=(*PAL.ring, 48), width=max(1, size // 170)))
    # маска рисуется с 4-кратным суперсэмплингом -> гладкий край скругления
    S = 4
    big = Image.new("L", (size * S, size * S), 0)
    md = ImageDraw.Draw(big)
    if kind == "square":
        md.rounded_rectangle([0, 0, size * S - 1, size * S - 1],
                             radius=int(size * 0.22 * S), fill=255)
    elif kind == "round":
        md.ellipse([0, 0, size * S - 1, size * S - 1], fill=255)
    else:
        md.rectangle([0, 0, size * S - 1, size * S - 1], fill=255)
    bg.putalpha(big.resize((size, size), Image.LANCZOS))
    return bg


def adaptive_background(size=512):
    return make_bg(size, size,
                   glows=[(0.5, 0.40, 0.62, PAL.glow1, 0.55 * PAL.glow_scale, 1.0),
                          (0.5, 0.82, 0.45, PAL.glow2, 0.16 * PAL.glow_scale, 1.0)],
                   grid_step=0, vignette=PAL.vignette_icon)


def adaptive_foreground(mark, size=512):
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    m = mark.resize((int(size * 0.76),) * 2, Image.LANCZOS)
    fg.alpha_composite(m, ((size - m.width) // 2, (size - m.width) // 2))
    return fg


# ---------------------------------------------------------------- баннер
def _tracked_width(draw, parts, font, tracking):
    total = 0
    for text, gap, _ in parts:
        for ch in text:
            total += draw.textlength(ch, font=font) + tracking
        total += gap
    return total - tracking


def draw_wordmark(img, parts, font, tracking, x, y):
    tl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(tl)
    cx = x
    for text, gap, color in parts:
        for ch in text:
            d.text((cx, y), ch, font=font, fill=color)
            cx += d.textlength(ch, font=font) + tracking
        cx += gap
    # мягкий голубой ореол + тень
    a = tl.getchannel("A")
    halo = Image.new("RGBA", img.size, (*PAL.halo, 0))
    halo.putalpha(a.point(lambda p: int(p * PAL.halo_alpha)).filter(ImageFilter.GaussianBlur(26)))
    sa = a.point(lambda p: int(p * PAL.shadow_alpha)).filter(ImageFilter.GaussianBlur(16))
    shifted = Image.new("L", img.size, 0)
    shifted.paste(sa.crop((0, 0, img.width, img.height - 9)), (0, 9))
    shadow = Image.new("RGBA", img.size, (*PAL.shadow, 255))
    shadow.putalpha(shifted)
    out = Image.alpha_composite(img, shadow)
    out = Image.alpha_composite(out, halo)
    return Image.alpha_composite(out, tl), int(cx - tracking)


def make_banner(mark, w=1280, h=720):
    bg = make_bg(w, h,
                 glows=[(0.24, 0.48, 0.55, PAL.bglow1, 0.62 * PAL.glow_scale, 0.85),
                        (0.85, 0.20, 0.45, PAL.bglow2, 0.18 * PAL.glow_scale, 0.9)],
                 grid_step=80, vignette=PAL.vignette_banner, diag=False)
    bg = _radial(bg, 300, 360, 330, 300, PAL.radial, 0.28 * PAL.glow_scale)

    m = mark.resize((440, 440), Image.LANCZOS)
    bg.alpha_composite(m, (80, 140))

    # подбираем кегль под доступную ширину
    parts = [("МОЁ", 26, (*PAL.ink, 255)), ("ТВ", 0, (*PAL.accent, 255))]
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    size, tracking = 156, 7
    while size > 40:
        font = ImageFont.truetype(FONT_BOLD, size)
        if _tracked_width(probe, parts, font, tracking) <= 610:
            break
        size -= 2
    font = ImageFont.truetype(FONT_BOLD, size)
    y = 277
    bg, end_x = draw_wordmark(bg, parts, font, tracking, 566, y)

    # акцент из цветных полос тест-таблицы под названием
    def accent(d):
        bx, by, bw, bh = 566, y + size + 34, 300, 12
        for i, c in enumerate(PAL.bars):
            d.rectangle([int(bx + i * bw / 7), by, int(bx + (i + 1) * bw / 7), by + bh],
                        fill=(*c, 215))
    bg = _shapes(bg, accent)
    return bg.convert("RGB")


# ---------------------------------------------------------------- SVG знака
SVG = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" width="1000" height="1000">
<defs>
 <linearGradient id="body" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#56EEFA"/><stop offset="1" stop-color="#1A58D0"/></linearGradient>
 <linearGradient id="stand" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#4AE8F8"/><stop offset="1" stop-color="#1A5CCE"/></linearGradient>
 <linearGradient id="glass" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A2852"/><stop offset="1" stop-color="#030A1A"/></linearGradient>
 <clipPath id="screenClip"><rect x="130" y="210" width="740" height="460" rx="24"/></clipPath>
</defs>
<g transform="translate(0 76)">
 <!-- антенна «рожки» -->
 <rect x="456" y="104" width="88" height="66" rx="14" fill="#1A58D0"/>
 <g stroke="#040E22" stroke-width="30" stroke-linecap="round" fill="none">
  <path d="M500 140L285 22M500 140L715 22"/>
 </g>
 <g stroke="#47C9F0" stroke-width="21" stroke-linecap="round" fill="none">
  <path d="M500 140L285 22M500 140L715 22"/>
 </g>
 <circle cx="285" cy="22" r="25" fill="#040E22"/>
 <circle cx="285" cy="22" r="18" fill="#48DEFF"/>
 <circle cx="715" cy="22" r="25" fill="#040E22"/>
 <circle cx="715" cy="22" r="18" fill="#48DEFF"/>
 <circle cx="500" cy="140" r="28" fill="#1A58D0"/>
 <circle cx="500" cy="140" r="13" fill="#48DEFF" opacity=".92"/>
 <!-- подставка -->
 <rect x="452" y="700" width="96" height="92" rx="14" fill="#1D4FA8"/>
 <rect x="326" y="776" width="348" height="42" rx="21" fill="url(#stand)"/>
 <!-- корпус -->
 <rect x="70" y="150" width="860" height="580" rx="58" fill="url(#body)" stroke="#FFFFFF" stroke-opacity=".3" stroke-width="5"/>
 <rect x="108" y="188" width="784" height="504" rx="38" fill="#040E22"/>
 <g clip-path="url(#screenClip)">
  <rect x="130" y="210" width="740" height="460" fill="url(#glass)"/>
  <g stroke="#82E4FF" stroke-opacity=".38" stroke-width="2.6">
   <path d="M253 210V610M377 210V610M500 210V610M623 210V610M747 210V610"/>
   <path d="M130 310H870M130 410H870M130 510H870"/>
  </g>
  <g stroke="#D2F6FF" stroke-opacity=".6" stroke-width="3.2"><path d="M500 210V610M130 410H870"/></g>
  <circle cx="500" cy="410" r="88" fill="none" stroke="#F0FCFF" stroke-opacity=".85" stroke-width="5"/>
  <circle cx="500" cy="410" r="9" fill="#5AE8FF"/>
  <g stroke="#FFFFFF" stroke-opacity=".78" stroke-width="5" fill="none">
   <path d="M130 244V210H164M836 210H870V244M130 576V610H164M836 610H870V576"/>
  </g>
  <rect x="246" y="302" width="15" height="15" fill="#FF60B2" opacity=".75"/>
  <rect x="739" y="502" width="15" height="15" fill="#FFD648" opacity=".75"/>
  <rect x="130" y="608" width="740" height="4" fill="#020A1A"/>
  <g opacity=".94">
   <rect x="130" y="612" width="105.7" height="58" fill="#F0F6FF"/>
   <rect x="235.7" y="612" width="105.7" height="58" fill="#FFD23F"/>
   <rect x="341.4" y="612" width="105.7" height="58" fill="#35D6E8"/>
   <rect x="447.1" y="612" width="105.7" height="58" fill="#3BD77A"/>
   <rect x="552.9" y="612" width="105.7" height="58" fill="#E853B6"/>
   <rect x="658.6" y="612" width="105.7" height="58" fill="#F2494B"/>
   <rect x="764.3" y="612" width="105.7" height="58" fill="#2E63F6"/>
  </g>
  <polygon points="130,210 463,210 130,463" fill="#FFFFFF" opacity=".05"/>
 </g>
 <rect x="130" y="210" width="740" height="460" rx="24" fill="none" stroke="#00030F" stroke-opacity=".4" stroke-width="3"/>
 <rect x="474" y="694" width="52" height="12" rx="6" fill="#6EF0FF" opacity=".82"/>
</g>
</svg>
'''


PREVIEW = '''<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>МОЁ ТВ — брендинг Android TV</title>
<style>
 :root{--ink:#12203a;--muted:#5c6b85;--line:#dfe6f0;--accent:#0b63c9}
 *{box-sizing:border-box} body{margin:0;background:#f4f7fc;color:var(--ink);
  font:15px/1.5 "Segoe UI",Arial,sans-serif;padding:44px}
 .wrap{max-width:1120px;margin:0 auto}
 h1{margin:0 0 4px;font-size:30px;letter-spacing:-.5px} .sub{color:var(--muted);margin-bottom:32px}
 h2{font-size:15px;text-transform:uppercase;letter-spacing:1.4px;color:var(--muted);margin:36px 0 14px}
 .card{background:#fff;border:1px solid var(--line);border-radius:18px;padding:24px;
  box-shadow:0 8px 30px rgba(20,45,90,.06)}
 .tv{background:radial-gradient(120% 120% at 15% 20%,#123b7d,#050b1e 70%);border-radius:0;
  padding:34px;position:relative;overflow:hidden}
 .tv:before{content:"";position:absolute;inset:0;background-image:linear-gradient(#ffffff08 1px,transparent 1px),
  linear-gradient(90deg,#ffffff08 1px,transparent 1px);background-size:64px 64px}
 .tv>*{position:relative}
 /* Плитка Android TV: строго прямоугольная, без скруглений и объёма */
 .banner{width:min(100%,760px);border-radius:0;display:block;box-shadow:none}
 .lbl{color:#a9c7ee;font-size:12px;letter-spacing:1.5px;text-transform:uppercase;margin-bottom:14px}
 .row{display:flex;flex-wrap:wrap;gap:38px;align-items:flex-end;margin-top:24px}
 .cell{text-align:center;color:#a9c7ee;font-size:12px}
 .cell img{display:block;margin:0 auto 10px;filter:drop-shadow(0 10px 18px rgba(0,0,0,.45))}
 .sizes{display:flex;gap:18px;align-items:flex-end;justify-content:center}
 .swatches{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px}
 .sw{width:88px;height:64px;border-radius:10px;border:1px solid #e3e9f2;padding:8px;
  font-size:10px;color:#fff;display:flex;align-items:flex-end;font-family:Consolas,monospace}
 .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:18px}
 code{background:#eef3fa;padding:2px 6px;border-radius:5px;font-size:12px}
 footer{color:var(--muted);font-size:13px;margin-top:34px;border-top:1px solid var(--line);padding-top:18px}
</style></head><body><div class="wrap">
<h1>МОЁ ТВ</h1><div class="sub">Брендинг приложения для Android TV · телевизор с настроечной сеткой и антенной-«рожками»</div>

<h2>Баннер на главном экране</h2>
<div class="card"><div class="tv">
 <div class="lbl">Android TV · home screen · плоская плитка без скруглений</div>
 <img class="banner" src="master/tv_banner.png" alt="Баннер МОЁ ТВ">
 <div class="row">
  <div class="cell"><div class="sizes">
   <img src="master/ic_launcher.png" width="96" height="96" alt="">
   <img src="master/ic_launcher.png" width="64" height="64" alt="">
   <img src="master/ic_launcher.png" width="48" height="48" alt="">
  </div>квадратная · 96 / 64 / 48 px</div>
  <div class="cell"><div class="sizes">
   <img src="master/ic_launcher_round.png" width="96" height="96" alt="">
   <img src="master/ic_launcher_round.png" width="64" height="64" alt="">
   <img src="master/ic_launcher_round.png" width="48" height="48" alt="">
  </div>круглая · 96 / 64 / 48 px</div>
 </div>
</div></div>

<h2>Мастер-файлы</h2>
<div class="card grid">
 <div class="cell" style="color:var(--ink)"><img src="master/ic_launcher.png" width="150" height="150" style="border-radius:22px"><br>
  <b>ic_launcher.png</b><br><code>512 × 512</code></div>
 <div class="cell" style="color:var(--ink)"><img src="master/ic_launcher_round.png" width="150" height="150"><br>
  <b>ic_launcher_round.png</b><br><code>512 × 512</code></div>
 <div class="cell" style="color:var(--ink)"><img src="master/ic_launcher_foreground.png" width="150" height="150" style="background:#0d2b60;border-radius:16px"><br>
  <b>foreground</b><br><code>адаптивная иконка</code></div>
 <div class="cell" style="color:var(--ink)"><img src="master/play_store_512.png" width="150" height="150" style="border-radius:16px"><br>
  <b>play_store_512.png</b><br><code>листинг Google Play</code></div>
</div>

<h2>Палитра</h2>
<div class="card"><div class="swatches">
 <div class="sw" style="background:#061029">#061029</div>
 <div class="sw" style="background:#0C2C69">#0C2C69</div>
 <div class="sw" style="background:#1F60D8">#1F60D8</div>
 <div class="sw" style="background:#3ADEEF">#3ADEEF</div>
 <div class="sw" style="background:#48DEFF">#48DEFF</div>
 <div class="sw" style="background:#FFD23F">#FFD23F</div>
 <div class="sw" style="background:#E853B6">#E853B6</div>
 <div class="sw" style="background:#F0F6FF;color:#12203a">#F0F6FF</div>
</div></div>
<footer>Полный комплект Android: <code>android_res/</code> — готовый <code>res/</code> (108dp-слои, все плотности баннера,
векторные drawable, темы, splash), <code>android_extra/</code> — манифест и ассеты Google Play,
<code>android_variants/&lt;схема&gt;/res/</code> — такое же дерево для каждой из 12 схем.
Вектор знака: <code>mark.svg</code> и <code>android_res/drawable/tv_mark.xml</code>.
Пересборка: <code>python build_brand.py &amp;&amp; python build_variants.py &amp;&amp; python build_android.py</code>.</footer>
</div></body></html>
'''


def main():
    os.makedirs(MASTER, exist_ok=True)
    mark = make_tv_mark(1200)
    mark.save(os.path.join(MASTER, "tv_mark.png"))

    square = compose_icon(mark, 512, "square")
    round_ = compose_icon(mark, 512, "round")
    fg = adaptive_foreground(mark, 512)
    bg = adaptive_background(512)
    banner = make_banner(mark, 1280, 720)
    fullbleed = compose_icon(mark, 512, "fullbleed")
    for name, img in (("ic_launcher", square), ("ic_launcher_round", round_),
                      ("ic_launcher_foreground", fg), ("ic_launcher_background", bg),
                      ("tv_banner", banner)):
        img.save(os.path.join(MASTER, name + ".png"))
    # листинг Google Play требует непрозрачный квадрат без скругления;
    # тот же файл пригодится как «плоская» иконка с прямыми углами
    flat = fullbleed.convert("RGB")
    flat.save(os.path.join(MASTER, "play_store_512.png"))
    flat.save(os.path.join(MASTER, "ic_launcher_flat.png"))

    with open(os.path.join(BASE, "mark.svg"), "w", encoding="utf-8") as f:
        f.write(SVG)
    with open(os.path.join(BASE, "preview.html"), "w", encoding="utf-8") as f:
        f.write(PREVIEW)

    # плотности для реального Android-проекта
    for folder, px in (("mipmap-mdpi", 48), ("mipmap-hdpi", 72),
                       ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144),
                       ("mipmap-xxxhdpi", 192)):
        p = os.path.join(RES, folder)
        os.makedirs(p, exist_ok=True)
        square.resize((px, px), Image.LANCZOS).save(os.path.join(p, "ic_launcher.png"))
        round_.resize((px, px), Image.LANCZOS).save(os.path.join(p, "ic_launcher_round.png"))
        fg.resize((px, px), Image.LANCZOS).save(os.path.join(p, "ic_launcher_foreground.png"))
        bg.resize((px, px), Image.LANCZOS).save(os.path.join(p, "ic_launcher_background.png"))

    # адаптивная иконка (API 26+)
    p = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(p, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
           '</adaptive-icon>\n')
    with open(os.path.join(p, "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    with open(os.path.join(p, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
        f.write(xml)

    # баннер в трёх разрешениях (официальный размер 320×180 dp)
    for folder, dims in (("drawable-mdpi", (320, 180)), ("drawable-xhdpi", (640, 360)),
                         ("drawable-xxxhdpi", (1280, 720))):
        p = os.path.join(RES, folder)
        os.makedirs(p, exist_ok=True)
        banner.resize(dims, Image.LANCZOS).save(os.path.join(p, "tv_banner.png"))

    print("OK ->", MASTER)


if __name__ == "__main__":
    main()
