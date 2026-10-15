#!/usr/bin/env python3
"""Store-style release images: each screenshot in a device frame under a Hebrew caption.

  release-frame.py manifest.json out_dir

manifest.json is a list of shots, framed in this order:
  [{"file": "shots/home.png", "name": "01-home", "title": "הזמנים, הלוח והחיפוש",
    "subtitle": "מסך הבית כמו בזית למחשב", "device": "phone", "theme": "light"}, ...]

device: phone (1080x1920 portrait), small (Jelly Star, same canvas, smaller device),
        tablet (1920x1200 landscape). theme: light or dark background.
footer: optional; a small extra line under DEFAULT_FOOTER (what the app is), which is always drawn.
The overview repeats DEFAULT_FOOTER in large type.
Writes <name>.png per shot plus overview.png (all shots side by side, scaled down).
Needs Pillow with raqm (python3 -c "from PIL import features; print(features.check('raqm'))")
so the Hebrew lays out right to left. The font is desktop's Open Sans from this repository.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# Desktop's own Open Sans: one variable font with Hebrew, Latin punctuation and digits
# (Noto Sans Hebrew has no comma or digits, which come out as boxes).
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
FONT = os.path.join(REPO, "SeforimApp/src/commonMain/composeResources/font/OpenSans-VariableFont_wdth,wght.ttf")

# Zayit's Islands palette (ui/theme): light title-bar gradient and dark ground, gold accent.
THEMES = {
    "light": {"bg": [(0xFF, 0xFF, 0xFF), (0xF7, 0xF0, 0xDC), (0xEF, 0xE2, 0xBC)],
              "title": (0x1F, 0x1F, 0x1F), "subtitle": (0x6C, 0x70, 0x7E), "accent": (0xBE, 0x91, 0x17)},
    "dark": {"bg": [(0x1E, 0x1F, 0x22), (0x2B, 0x2D, 0x30), (0x3A, 0x34, 0x27)],
             "title": (0xDF, 0xE1, 0xE5), "subtitle": (0x9D, 0xA0, 0xA8), "accent": (0xD4, 0xA8, 0x43)},
}
CANVAS = {"phone": (1080, 1920), "small": (1080, 1920), "tablet": (1920, 1200)}
# Every image says what the app is: they get shared one at a time, away from the README. A manifest "footer" adds a line
# under it and never replaces it.
DEFAULT_FOOTER = "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו"


def gradient(size, stops):
    w, h = size
    col = Image.new("RGB", (1, h))
    px = col.load()
    for y in range(h):
        t = y / max(1, h - 1) * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        f = t - i
        a, b = stops[i], stops[i + 1]
        px[0, y] = tuple(round(a[k] + (b[k] - a[k]) * f) for k in range(3))
    return col.resize((w, h))


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return m


def device(shot, box_w, box_h, kind):
    """Scales the screenshot into a bezel that fits box_w x box_h and returns the RGBA device."""
    bezel = {"phone": 0.035, "small": 0.05, "tablet": 0.025}[kind]
    sw, sh = shot.size
    scale = min(box_w / (sw * (1 + 2 * bezel)), box_h / (sh + sw * 2 * bezel))
    w, h = round(sw * scale), round(sh * scale)
    pad = round(sw * scale * bezel)
    screen_r = round(min(w, h) * (0.06 if kind != "tablet" else 0.03))
    dev = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(dev)
    d.rounded_rectangle((0, 0, dev.width - 1, dev.height - 1), screen_r + pad, fill=(17, 17, 19, 255))
    d.rounded_rectangle((2, 2, dev.width - 3, dev.height - 3), screen_r + pad - 2, outline=(70, 70, 76, 255), width=2)
    screen = shot.convert("RGB").resize((w, h), Image.LANCZOS)
    dev.paste(screen, (pad, pad), rounded_mask((w, h), screen_r))
    return dev


def open_font(weight, size):
    font = ImageFont.truetype(FONT, size)
    font.set_variation_by_name(weight)
    return font


def fit_font(weight, text, max_w, size, draw):
    while size > 24:
        font = open_font(weight, size)
        if draw.textlength(text, font=font, direction="rtl") <= max_w:
            return font
        size -= 4
    return open_font(weight, size)


def frame(item, base):
    kind = item.get("device", "phone")
    theme = THEMES[item.get("theme", "light")]
    W, H = CANVAS[kind]
    img = gradient((W, H), theme["bg"]).convert("RGBA")
    draw = ImageDraw.Draw(img)
    margin = round(W * 0.07)

    top = round(H * (0.045 if kind != "tablet" else 0.05))
    tfont = fit_font(b"Bold", item["title"], W - 2 * margin, round(W * (0.068 if kind != "tablet" else 0.04)), draw)
    draw.text((W / 2, top), item["title"], font=tfont, fill=theme["title"], anchor="ma", direction="rtl")
    y = top + tfont.size * 1.35
    if item.get("subtitle"):
        sfont = fit_font(b"Regular", item["subtitle"], W - 2 * margin, round(tfont.size * 0.58), draw)
        draw.text((W / 2, y), item["subtitle"], font=sfont, fill=theme["subtitle"], anchor="ma", direction="rtl")
        y += sfont.size * 1.5
    bar_w = round(W * 0.08)
    draw.rounded_rectangle((W / 2 - bar_w / 2, y, W / 2 + bar_w / 2, y + 8), 4, fill=theme["accent"])
    y += round(H * 0.035)
    footers = [DEFAULT_FOOTER] + ([item["footer"]] if item.get("footer") else [])
    fsize = round(W * (0.03 if kind != "tablet" else 0.018))
    ffont = min((fit_font(b"Regular", t, W - 2 * margin, fsize, draw) for t in footers), key=lambda f: f.size)
    line_h = round(ffont.size * 1.4)
    footer_h = round(ffont.size * 2) + line_h * (len(footers) - 1)

    shot = Image.open(os.path.join(base, item["file"]))
    box_h = H - y - round(H * 0.03) - footer_h
    box_w = W - 2 * margin if kind != "small" else round(W * 0.62)
    dev = device(shot, box_w, box_h, kind)
    x = (W - dev.width) // 2
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (x + 6, y + 18, x + dev.width + 6, y + dev.height + 18), 60, fill=(0, 0, 0, 90))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(28)))
    img.alpha_composite(dev, (x, round(y)))
    fdraw = ImageDraw.Draw(img)
    for i, text in enumerate(reversed(footers)):
        fdraw.text((W / 2, H - round(H * 0.025) - i * line_h), text, font=ffont, fill=theme["subtitle"], anchor="md", direction="rtl")
    return img.convert("RGB")


def main():
    manifest, out_dir = sys.argv[1], sys.argv[2]
    base = os.path.dirname(os.path.abspath(manifest))
    os.makedirs(out_dir, exist_ok=True)
    framed = []
    for item in json.load(open(manifest, encoding="utf-8")):
        im = frame(item, base)
        path = os.path.join(out_dir, item["name"] + ".png")
        im.save(path, optimize=True)
        framed.append(im)
        print(path, im.size)
    # Overview: right to left like the app, one row at a common height, and the DEFAULT_FOOTER line (Pardes is based on Zayit) under it, large enough
    # to read once the README scales the sheet to its width.
    h = 960
    strip = round(h * 0.17)
    row = [im.resize((im.width * h // im.height, h), Image.LANCZOS) for im in reversed(framed)]
    sheet = Image.new("RGB", (sum(im.width for im in row) + 24 * (len(row) + 1), h + 48 + strip), (250, 247, 238))
    x = 24
    for im in row:
        sheet.paste(im, (x, 24)); x += im.width + 24
    draw = ImageDraw.Draw(sheet)
    note = fit_font(b"Regular", DEFAULT_FOOTER, sheet.width - 96, round(h * 0.1), draw)
    draw.text((sheet.width / 2, sheet.height - (strip + 24) / 2), DEFAULT_FOOTER, font=note, fill=THEMES["light"]["subtitle"],
              anchor="mm", direction="rtl")
    sheet.save(os.path.join(out_dir, "overview.png"), optimize=True)
    print(os.path.join(out_dir, "overview.png"), sheet.size)


if __name__ == "__main__":
    main()
