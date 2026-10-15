#!/usr/bin/env python3
"""Builds the Pardes icon resources: the word פרדס in two lines (פר / דס), Frank Ruhl Libre Black (SIL OFL) outlines in
gold on an olive-green ground.

Usage (from the repository root; needs fontTools):
    python3 .claude/skills/zayit-android/scripts/pardes-icon.py androidApp

Writes res/drawable/ic_launcher_background.xml, ic_launcher_foreground.xml, ic_launcher_monochrome.xml,
pardes_app_icon.xml, the two adaptive mipmaps, the icon colours in res/values/colors.xml, and site/img/icon.svg.
The site PNGs (icon-64/96/180.png) are icon.svg rendered at those sizes (headless Chrome screenshot).
"""
import os
import sys

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

app = sys.argv[1]
font_path = os.path.join(app, "app/src/androidMain/res/font/frankruhllibre.ttf")
font = instancer.instantiateVariableFont(TTFont(font_path), {"wght": 900})
cmap = font.getBestCmap()
gs = font.getGlyphSet()
hmtx = font["hmtx"]


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
y_cursor = top
for li in range(2):
    xmin, xmax, ymin, ymax = line_boxes[li]
    lw = (xmax - xmin) * scale
    left = cx - lw / 2
    baseline = y_cursor + ymax * scale  # canvas y of the font baseline for this line
    for name, xo, l in placed:
        if l != li:
            continue
        sp = SVGPathPen(gs, ntos=fmt)
        tp = TransformPen(sp, (scale, 0, 0, -scale, left + (xo - xmin) * scale, baseline))
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
    '    <color name="pardes_olive_light">#3E4D24</color>\n'
    '    <color name="pardes_olive_dark">#1C2410</color>\n'
    '    <color name="pardes_gold_light">#F6DC8C</color>\n'
    '    <color name="pardes_gold_dark">#B8862A</color>\n'
    '    <color name="window_background">#F2F3F5</color>\n'
    '</resources>\n')

# Site: the same icon, rounded, as SVG.
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 72 72">
  <defs>
    <radialGradient id="bg" cx="36" cy="28" r="51" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#3E4D24"/><stop offset="1" stop-color="#1C2410"/></radialGradient>
    <linearGradient id="fg" x1="0" y1="{GOLD_TOP - 18:.1f}" x2="0" y2="{GOLD_BOTTOM - 18:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#F6DC8C"/><stop offset="1" stop-color="#B8862A"/></linearGradient>
  </defs>
  <rect width="72" height="72" rx="16" fill="url(#bg)"/>
  <path transform="translate(-18,-18)" fill="url(#fg)" d="{D}"/>
</svg>
'''
open(os.path.join(site, "icon.svg"), "w", encoding="utf-8", newline="\n").write(svg)
print("ok", len(D))
