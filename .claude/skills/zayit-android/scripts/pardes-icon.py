#!/usr/bin/env python3
"""Builds the Pardes icon resources: the word פרדס in two lines (פר / דס), Frank Ruhl Libre Black (SIL OFL) outlines in
gold on an olive-green ground.

Usage (from the repository root; needs fontTools, and for desktop also Pillow and numpy):
    python3 .claude/skills/zayit-android/scripts/pardes-icon.py androidApp
    python3 .claude/skills/zayit-android/scripts/pardes-icon.py desktop .

androidApp: writes res/drawable/ic_launcher_background.xml, ic_launcher_foreground.xml, ic_launcher_monochrome.xml,
pardes_app_icon.xml, the two adaptive mipmaps, the icon colours in res/values/colors.xml, and site/img/icon.svg.
The site PNGs (icon-64/96/180.png) are icon.svg rendered at those sizes (headless Chrome screenshot).

desktop <repo root>: writes the pictures of Pardes for desktop into branding/pardes/, where the brand overlay
(branding/README.md) puts each in place of upstream Zayit's file of the same path, at that file's pixel size and format.
Same letters, layout and colours as above (the font is androidApp's), rasterised here (nonzero fill, supersampled), so
a rerun writes the same files:
- composeResources/drawable/AppIcon.png, 1024 px (window, taskbar and settings icon) and desktopAppIcons/LinuxIcon.png,
  1024 px (the package's icon): the icon as site/img/icon.svg draws it, the 72dp visible part rounded at 16.
- desktopAppIcons/WindowsIcon.ico: the same at 16, 20, 24, 32, 40, 48, 64, 128 and 256 px (PNG entries, as upstream's).
- desktopAppIcons/MacosIcon.icns: the same at 32, 64, 128, 256, 512 and 1024 px (PNG entries; upstream's has one 1024 px
  image, its whole square drawn as this icon's is).
- composeResources/drawable/zayit_transparent.png, 645x746 (the onboarding's welcome): the icon, 560 px, on a
  transparent ground, shown 132dp high as Android's onboarding shows it.
- composeResources/drawable/zayit_logo_branch.png, 82x88 (beside the search results bar): the icon, 82 px.
- composeResources/drawable/zayit_new_logo.png and zayit_new_logo_text.png, 1556x715 (Home, HomeView.kt LogoImage):
  the word פרדס on one line, as Android's Home LogoImage sets it (em 0.62 of the height, the line box centred, the
  font's kerning). The first is the word in gold, the second its mask in white, which HomeView paints gold (or the
  accent, in dark mode), so the word ends up in that colour as on Android.
- assets/common/splash.png, 800x533 (the launch splash): upstream's layout (a dark card with a gold border, the icon
  with a gold rim, the name under it) with the icon and the word פרדס.
"""
import math
import os
import sys

from fontTools.pens.basePen import BasePen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

DESKTOP = sys.argv[1] == "desktop"
if DESKTOP:
    import numpy as np
    from PIL import Image

    root = sys.argv[2]
    app = os.path.join(root, "androidApp")
else:
    app = sys.argv[1]
font_path = os.path.join(app, "app/src/androidMain/res/font/frankruhllibre.ttf")
font = instancer.instantiateVariableFont(TTFont(font_path), {"wght": 900})
cmap = font.getBestCmap()
gs = font.getGlyphSet()
hmtx = font["hmtx"]

# The icon's colours (res/values/colors.xml pardes_*)
OLIVE_LIGHT, OLIVE_DARK = "#3E4D24", "#1C2410"
GOLD_LIGHT, GOLD_DARK = "#F6DC8C", "#B8862A"


def glyph(ch):
    return cmap[ord(ch)]


def bounds(name):
    bp = BoundsPen(gs)
    gs[name].draw(bp)
    return bp.bounds  # xMin, yMin, xMax, yMax


# Line layout in font units, RTL: the first letter of a line sits on the right.
TRACK = 40  # extra space between the two letters of a line, font units
lines = [("פ", "ר"), ("ד", "ס")]
placed = []  # (glyph name, x offset of its origin, line index)
line_boxes = []
for li, (first, second) in enumerate(lines):
    g1, g2 = glyph(first), glyph(second)
    b1, b2 = bounds(g1), bounds(g2)
    # second letter on the left at origin 0; first letter to its right after the ink of the second + TRACK
    x2 = -b2[0]
    x1 = x2 + b2[2] + TRACK - b1[0]
    placed.append((g2, x2, li))
    placed.append((g1, x1, li))
    xmin = 0
    xmax = x1 + b1[2]
    ymin = min(b1[1], b2[1])
    ymax = max(b1[3], b2[3])
    line_boxes.append((xmin, xmax, ymin, ymax))

LINE_GAP = 90  # font units between the lines' ink
heights = [b[3] - b[2] for b in line_boxes]
widths = [b[1] - b[0] for b in line_boxes]
block_w = max(widths)
block_h = sum(heights) + LINE_GAP

# Fit the block into the safe zone: a w x h box inside the 33-unit circle around (54, 54), a little margin.
TARGET_R = 30.5
scale = TARGET_R / (((block_w / 2) ** 2 + (block_h / 2) ** 2) ** 0.5)
cx, cy = 54.0, 54.0
top = cy - block_h * scale / 2


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


paths = []
letters = []  # (glyph name, its transform from font units to the 108-unit canvas), for the desktop pictures
y_cursor = top
for li in range(2):
    xmin, xmax, ymin, ymax = line_boxes[li]
    lw = (xmax - xmin) * scale
    left = cx - lw / 2
    baseline = y_cursor + ymax * scale  # canvas y of the font baseline for this line
    for name, xo, l in placed:
        if l != li:
            continue
        transform = (scale, 0, 0, -scale, left + (xo - xmin) * scale, baseline)
        letters.append((name, transform))
        sp = SVGPathPen(gs, ntos=fmt)
        tp = TransformPen(sp, transform)
        gs[name].draw(tp)
        paths.append(sp.getCommands())
    y_cursor += (ymax - ymin) * scale + LINE_GAP * scale


res = os.path.join(app, "app/src/androidMain/res")
site = os.path.join(app, "site/img")
D = " ".join(paths)

# The gold runs over the letters' height.
GOLD_TOP, GOLD_BOTTOM = top, 108 - top

HEAD = '<?xml version="1.0" encoding="utf-8"?>\n'
AAPT = 'xmlns:aapt="http://schemas.android.com/aapt"'


def gold_fill(dy=0.0):
    return f'''        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="54"
                android:startY="{GOLD_TOP + dy:.1f}"
                android:endX="54"
                android:endY="{GOLD_BOTTOM + dy:.1f}"
                android:startColor="@color/pardes_gold_light"
                android:endColor="@color/pardes_gold_dark" />
        </aapt:attr>'''


def olive_fill(cx, cy, r):
    return f'''        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{cx}"
                android:centerY="{cy}"
                android:gradientRadius="{r}"
                android:startColor="@color/pardes_olive_light"
                android:endColor="@color/pardes_olive_dark" />
        </aapt:attr>'''


def vector(comment, size, body, aapt=True):
    ns = f'\n    {AAPT}' if aapt else ''
    return (HEAD + f'<!-- {comment} -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{size}"\n    android:viewportHeight="{size}">\n{body}</vector>\n')


def android():
    open(os.path.join(res, "drawable/ic_launcher_background.xml"), "w", encoding="utf-8", newline="\n").write(vector(
        "Pardes launcher icon background: olive green, lighter at the middle, in Zayit's olive-and-gold spirit.", 108,
        f'    <path android:pathData="M0,0 H108 V108 H0 Z">\n{olive_fill(54, 46, 76)}\n    </path>\n'))

    open(os.path.join(res, "drawable/ic_launcher_foreground.xml"), "w", encoding="utf-8", newline="\n").write(vector(
        "Pardes launcher icon foreground: the word פרדס in two lines (פר / דס), Frank Ruhl Libre Black (SIL OFL) outlines\n"
        "     in gold, inside the 66dp safe zone of the 108dp layer. Made by .claude/skills/zayit-android/scripts/pardes-icon.py.", 108,
        f'    <path android:pathData="{D}">\n{gold_fill()}\n    </path>\n'))

    open(os.path.join(res, "drawable/ic_launcher_monochrome.xml"), "w", encoding="utf-8", newline="\n").write(vector(
        "Themed-icon (Android 13) layer of the Pardes launcher icon: the letters alone, tinted by the system.", 108,
        f'    <path\n        android:fillColor="#FF000000"\n        android:pathData="{D}" />\n', aapt=False))

    # The 72dp visible part of the two layers, as one picture.
    open(os.path.join(res, "drawable/pardes_app_icon.xml"), "w", encoding="utf-8", newline="\n").write(vector(
        "The Pardes launcher icon as one picture (both layers, the 72dp visible part of the 108dp canvas) for the כללי\n"
        "     header, the onboarding and the search results bar; callers clip it to their own shape.", 72,
        f'    <path android:pathData="M0,0 H72 V72 H0 Z">\n{olive_fill(36, 28, 51)}\n    </path>\n'
        f'    <group\n        android:translateX="-18"\n        android:translateY="-18">\n'
        f'        <path android:pathData="{D}">\n' + gold_fill().replace("\n", "\n    ") + '\n        </path>\n    </group>\n'))

    for f in ("ic_launcher", "ic_launcher_round"):
        open(os.path.join(res, f"mipmap-anydpi-v26/{f}.xml"), "w", encoding="utf-8", newline="\n").write(
            HEAD + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '  <background android:drawable="@drawable/ic_launcher_background"/>\n'
            '  <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '  <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
            '</adaptive-icon>\n')

    open(os.path.join(res, "values/colors.xml"), "w", encoding="utf-8", newline="\n").write(
        HEAD + '<resources>\n'
        '    <!-- The Pardes icon: gold letters on olive green -->\n'
        f'    <color name="pardes_olive_light">{OLIVE_LIGHT}</color>\n'
        f'    <color name="pardes_olive_dark">{OLIVE_DARK}</color>\n'
        f'    <color name="pardes_gold_light">{GOLD_LIGHT}</color>\n'
        f'    <color name="pardes_gold_dark">{GOLD_DARK}</color>\n'
        '    <color name="window_background">#F2F3F5</color>\n'
        '</resources>\n')

    # Site: the same icon, rounded, as SVG.
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 72 72">
  <defs>
    <radialGradient id="bg" cx="36" cy="28" r="51" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{OLIVE_LIGHT}"/><stop offset="1" stop-color="{OLIVE_DARK}"/></radialGradient>
    <linearGradient id="fg" x1="0" y1="{GOLD_TOP - 18:.1f}" x2="0" y2="{GOLD_BOTTOM - 18:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{GOLD_LIGHT}"/><stop offset="1" stop-color="{GOLD_DARK}"/></linearGradient>
  </defs>
  <rect width="72" height="72" rx="16" fill="url(#bg)"/>
  <path transform="translate(-18,-18)" fill="url(#fg)" d="{D}"/>
</svg>
'''
    open(os.path.join(site, "icon.svg"), "w", encoding="utf-8", newline="\n").write(svg)


# ---------------------------------------------------------------- desktop

class FlatPen(BasePen):
    """Glyph contours as polygons, each curve cut into STEPS straight pieces (quadratics reach here as cubics)."""

    STEPS = 24

    def __init__(self):
        super().__init__(gs)
        self.contours = []

    def _moveTo(self, p):
        self.contours.append([p])

    def _lineTo(self, p):
        self.contours[-1].append(p)

    def _curveToOne(self, p1, p2, p3):
        x0, y0 = self.contours[-1][-1]
        for i in range(1, self.STEPS + 1):
            t = i / self.STEPS
            a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
            self.contours[-1].append(
                (a * x0 + b * p1[0] + c * p2[0] + d * p3[0], a * y0 + b * p1[1] + c * p2[1] + d * p3[1]))


def outlines(items, k, dx, dy):
    """The contours of [(glyph name, transform)], the transform then followed by (x, y) -> (x k + dx, y k + dy)."""
    pen = FlatPen()
    for name, (a, b, c, d, e, f) in items:
        gs[name].draw(TransformPen(pen, (a * k, b * k, c * k, d * k, e * k + dx, f * k + dy)))
    return pen.contours


def rounded(x0, y0, x1, y1, r):
    """A rectangle with round corners of radius r, as one polygon."""
    n = max(16, int(r))
    corners = ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180))
    return [(x + r * math.cos(math.radians(a + 90 * i / n)), y + r * math.sin(math.radians(a + 90 * i / n)))
            for x, y, a in corners for i in range(n + 1)]


def kerning(first, second):
    """GPOS Hebrew 'kern' of first before second, as HarfBuzz applies it: first's (x placement, x advance) change."""
    gpos = font["GPOS"].table
    features = gpos.FeatureList.FeatureRecord
    script = next(s.Script for s in gpos.ScriptList.ScriptRecord if s.ScriptTag == "hebr")
    lookups = sorted({i for f in script.DefaultLangSys.FeatureIndex if features[f].FeatureTag == "kern"
                      for i in features[f].Feature.LookupListIndex})
    dx = dadv = 0
    for index in lookups:
        lookup = gpos.LookupList.Lookup[index]
        for sub in lookup.SubTable:
            kind = lookup.LookupType
            if kind == 9:  # extension: the real subtable inside
                kind, sub = sub.ExtensionLookupType, sub.ExtSubTable
            if kind != 2 or first not in sub.Coverage.glyphs:
                continue
            if sub.Format == 1:
                pair = sub.PairSet[sub.Coverage.glyphs.index(first)].PairValueRecord
                record = next((r for r in pair if r.SecondGlyph == second), None)
                if record is None:
                    continue
                value = record.Value1
            else:
                value = sub.Class1Record[sub.ClassDef1.classDefs.get(first, 0)].Class2Record[
                    sub.ClassDef2.classDefs.get(second, 0)].Value1
            dx += getattr(value, "XPlacement", 0) or 0
            dadv += getattr(value, "XAdvance", 0) or 0
            break
    return dx, dadv


def word(text):
    """text on one line, right to left, as HarfBuzz sets it: [(glyph name, x of its origin)] and the advance width."""
    names = [glyph(ch) for ch in text]
    kerns = [kerning(a, b) for a, b in zip(names, names[1:])] + [(0, 0)]
    laid, pen = [], 0
    for name, (dx, dadv) in reversed(list(zip(names, kerns))):  # visual order, left to right
        laid.append((name, (1, 0, 0, -1, pen + dx, 0)))
        pen += hmtx[name][0] + dadv
    return laid, pen


def ink(items):
    """The ink box (xMin, yMin, xMax, yMax) of word()'s glyphs, in font units, y up."""
    pen = BoundsPen(gs)
    for name, (a, b, c, d, e, f) in items:
        gs[name].draw(TransformPen(pen, (a, b, c, -d, e, f)))
    return pen.bounds


def rgb(colour):
    return np.array([int(colour[i:i + 2], 16) / 255 for i in (1, 3, 5)])


class Canvas:
    """Premultiplied RGBA pixels. Shapes are filled at their coverage: the share of SS x SS samples per pixel inside
    their contours (nonzero winding, as Android and SVG fill these paths)."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 4))
        self.ss = int(min(16, max(4, (40e6 / (w * h)) ** 0.5)))
        self.xx, self.yy = np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)

    def cover(self, contours):
        s, w, h = self.ss, self.w * self.ss, self.h * self.ss
        diff = np.zeros((h, w + 1), np.int32)
        for contour in contours:
            p = np.asarray(contour, float) * s
            q = np.roll(p, -1, axis=0)
            x0, y0, x1, y1 = p[:, 0], p[:, 1], q[:, 0], q[:, 1]
            # sample rows j (at y = j + 0.5) with min(y0, y1) <= y < max(y0, y1)
            j0 = np.clip(np.ceil(np.minimum(y0, y1) - 0.5), 0, h).astype(int)
            n = np.clip(np.ceil(np.maximum(y0, y1) - 0.5), 0, h).astype(int) - j0
            n = np.maximum(n, 0)
            edge = np.repeat(np.arange(len(p)), n)
            j = np.repeat(j0, n) + np.arange(n.sum()) - np.repeat(np.cumsum(n) - n, n)
            x = x0[edge] + (j + 0.5 - y0[edge]) * (x1[edge] - x0[edge]) / (y1[edge] - y0[edge])
            np.add.at(diff, (j, np.clip(np.ceil(x - 0.5), 0, w).astype(int)), np.where(y1[edge] > y0[edge], 1, -1))
        inside = np.cumsum(diff[:, :w], axis=1) != 0
        return inside.reshape(self.h, s, self.w, s).mean(axis=(1, 3))

    def fill(self, coverage, colour):
        a = coverage[..., None]
        self.px[..., :3] = colour * a + self.px[..., :3] * (1 - a)
        self.px[..., 3:] = a + self.px[..., 3:] * (1 - a)

    def linear(self, y0, y1, c0, c1):
        t = np.clip((self.yy - y0) / (y1 - y0), 0, 1)[..., None]
        return rgb(c0) * (1 - t) + rgb(c1) * t

    def radial(self, x, y, r, c0, c1):
        t = np.clip(np.hypot(self.xx - x, self.yy - y) / r, 0, 1)[..., None]
        return rgb(c0) * (1 - t) + rgb(c1) * t

    def icon(self, x, y, size, rim=0.0):
        """The icon as site/img/icon.svg draws it, size px wide at (x, y); rim: a gold border that many px wide."""
        k = size / 72
        r = 16 * k
        self.fill(self.cover([rounded(x, y, x + size, y + size, r)]),
                  self.radial(x + 36 * k, y + 28 * k, 51 * k, OLIVE_LIGHT, OLIVE_DARK))
        self.fill(self.cover(outlines(letters, k, x - 18 * k, y - 18 * k)),
                  self.linear(y + (GOLD_TOP - 18) * k, y + (GOLD_BOTTOM - 18) * k, GOLD_LIGHT, GOLD_DARK))
        if rim:
            inner = rounded(x + rim, y + rim, x + size - rim, y + size - rim, r - rim)[::-1]
            self.fill(self.cover([rounded(x, y, x + size, y + size, r), inner]),
                      self.linear(y, y + size, GOLD_LIGHT, GOLD_DARK))

    def image(self):
        a = self.px[..., 3:]
        colour = np.where(a > 0, self.px[..., :3] / np.maximum(a, 1e-9), 0)
        return Image.fromarray(np.round(np.concatenate([colour, a], axis=2) * 255).astype(np.uint8), "RGBA")


def save(image, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, optimize=True)


def desktop():
    brand = os.path.join(root, "branding/pardes")
    drawable = os.path.join(brand, "composeResources/drawable")

    def icon(size):
        cv = Canvas(size, size)
        cv.icon(0, 0, size)
        return cv.image()

    # Window, taskbar and settings icon; the Linux package's icon
    save(icon(1024), os.path.join(drawable, "AppIcon.png"))
    save(icon(1024), os.path.join(brand, "desktopAppIcons/LinuxIcon.png"))
    # Windows: every size drawn at its own size
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    ico = os.path.join(brand, "desktopAppIcons/WindowsIcon.ico")
    os.makedirs(os.path.dirname(ico), exist_ok=True)
    images = [icon(s) for s in sizes]
    images[-1].save(ico, format="ICO", sizes=[(s, s) for s in sizes], append_images=images[:-1])
    # macOS: the sizes an icns holds, each drawn at its own size (Pillow writes them as its PNG entries)
    icns = os.path.join(brand, "desktopAppIcons/MacosIcon.icns")
    icon(1024).save(icns, format="ICNS", append_images=[icon(s) for s in [32, 64, 128, 256, 512]])

    # Onboarding (InitScreen, 176dp high): the icon 132dp high, as Android's onboarding shows it
    cv = Canvas(645, 746)
    cv.icon((645 - 560) / 2, (746 - 560) / 2, 560)
    save(cv.image(), os.path.join(drawable, "zayit_transparent.png"))

    # Beside the search results bar (44dp high, 634:684): the icon, as Android's search bar shows it
    cv = Canvas(82, 88)
    cv.icon(0, 3, 82)
    save(cv.image(), os.path.join(drawable, "zayit_logo_branch.png"))

    # Home: פרדס as Android's Home LogoImage sets it, the em 0.62 of the height and the line box (hhea) centred
    w, h = 1556, 715
    laid, advance = word("פרדס")
    em = 0.62 * h / font["head"].unitsPerEm
    ascent, descent = font["hhea"].ascent, font["hhea"].descent
    left = (w - advance * em) / 2
    baseline = (h - (ascent - descent) * em) / 2 + ascent * em
    _, y_min, _, y_max = ink(laid)
    contours = outlines(laid, em, left, baseline)
    cv = Canvas(w, h)
    cv.fill(cv.cover(contours), cv.linear(baseline - y_max * em, baseline - y_min * em, GOLD_LIGHT, GOLD_DARK))
    save(cv.image(), os.path.join(drawable, "zayit_new_logo.png"))
    mask = Canvas(w, h)
    mask.fill(mask.cover(contours), np.ones(3))
    save(mask.image(), os.path.join(drawable, "zayit_new_logo_text.png"))

    # Splash: upstream's card (dark, gold border, square corners), the icon with a gold rim, the name under it
    w, h = 800, 533
    cv = Canvas(w, h)
    card = (18, 25, 770, 508)
    cv.fill(cv.cover([rounded(*card, 0)]), cv.radial(394, 170, 560, "#232C16", "#090C05"))
    inner = rounded(card[0] + 3.5, card[1] + 3.5, card[2] - 3.5, card[3] - 3.5, 0)[::-1]
    cv.fill(cv.cover([rounded(*card, 0), inner]), cv.linear(card[1], card[3], GOLD_LIGHT, GOLD_DARK))
    cv.icon(394 - 101, 96, 202, rim=3)
    laid, advance = word("פרדס")
    x_min, y_min, x_max, y_max = ink(laid)
    em = 64 / (y_max - y_min)  # the letters 64 px high, as upstream's name
    left = 394 - (x_min + x_max) / 2 * em
    baseline = 361 + (y_max + y_min) / 2 * em
    cv.fill(cv.cover(outlines(laid, em, left, baseline)),
            cv.linear(baseline - y_max * em, baseline - y_min * em, GOLD_LIGHT, GOLD_DARK))
    save(cv.image(), os.path.join(brand, "assets/common/splash.png"))


if DESKTOP:
    desktop()
else:
    android()
print("ok", len(D))
