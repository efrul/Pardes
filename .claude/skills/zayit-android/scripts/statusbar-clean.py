#!/usr/bin/env python3
"""Redraws the emulator's status bar on a screenshot taken without demo mode, for release images.

  statusbar-clean.py in.png out.png [density] [HH:MM]

The warning triangle, the settings gear, "3G" and the signal bars go: the bar (found from where its icons end) is refilled, column by column, with the
colour of its own top row (above the icons, so it follows the title bar's gradient), then gets a clock (default 10:00,
as emu-demo.sh) and a full battery, drawn in the colour the old clock had. density is the screen's (2.625 for
1080x2400 at 420 dpi); without it, it is guessed from the width (<=600 -> 2.0, <=1200 -> 2.625, wider -> 1.5). New shots
should use emu-demo.sh instead; this is for ones already taken.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
FONT = os.path.join(REPO, "SeforimApp/src/commonMain/composeResources/font/OpenSans-VariableFont_wdth,wght.ttf")


def guess_density(width):
    return 2.0 if width <= 600 else 2.625 if width <= 1200 else 1.5


src, out = sys.argv[1], sys.argv[2]
img = Image.open(src).convert("RGB")
w, h = img.size
dp = float(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3] else guess_density(w)
clock = sys.argv[4] if len(sys.argv) > 4 else "10:00"
px = img.load()
# The bar ends a little below the old icons' last dark row (24dp on a phone, taller on a tablet)
# A dark theme has light icons on a dark bar
dark_bar = sum(sum(px[x, 1]) for x in range(0, w, 7)) / len(range(0, w, 7)) < 384


def has_ink(y):
    if dark_bar:
        return max(sum(px[x, y]) for x in range(w // 3)) > 500
    return min(sum(px[x, y]) for x in range(w // 3)) < 300


end = None
for y in range(round(48 * dp)):
    if has_ink(y):
        end = y
    elif end is not None:
        break  # the first band of dark rows is the icons'; what follows is the app's own bar
bar = (end if end is not None else round(18 * dp)) + round(5 * dp)

# The ink of the old icons: the darkest (on a dark bar, the lightest) pixel of the bar's left part
ink = (max if dark_bar else min)((px[x, y] for x in range(0, w // 3) for y in range(bar)), key=sum)
for x in range(w):
    top = px[x, 1]
    for y in range(bar):
        px[x, y] = top

d = ImageDraw.Draw(img)
font = ImageFont.truetype(FONT, round(14 * dp))
font.set_variation_by_name(b"SemiBold")
d.text((round(16 * dp), bar / 2), clock, font=font, fill=ink, anchor="lm")
# A full battery: body and cap
bw, bh = round(7 * dp), round(12 * dp)
bx, by = w - round(16 * dp) - bw, round((bar - bh) / 2)
d.rounded_rectangle((bx, by + round(1.5 * dp), bx + bw, by + bh), radius=round(1.2 * dp), fill=ink)
d.rectangle((bx + round(2 * dp), by, bx + bw - round(2 * dp), by + round(1.5 * dp)), fill=ink)
img.save(out, optimize=True)
print(out, img.size, f"bar {bar}px")
