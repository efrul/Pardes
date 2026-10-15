#!/usr/bin/env python3
"""Side-by-side sheet of screenshots, e.g. desktop vs Android, to send to the user in one image.

  contact-sheet.py out.png a.png b.png [c.png ...] [--height 1200] [--bg 255,255,255]

Every image is scaled to the same height and placed left to right with a 20 px gap.
Needs Pillow (python3 -c "import PIL"; apt-get install -y python3-pil if missing).
"""
import sys
from PIL import Image

args = sys.argv[1:]
height, bg = 1200, (255, 255, 255)
if "--height" in args:
    i = args.index("--height"); height = int(args[i + 1]); del args[i:i + 2]
if "--bg" in args:
    i = args.index("--bg"); bg = tuple(int(v) for v in args[i + 1].split(",")); del args[i:i + 2]
out, files = args[0], args[1:]
ims = [Image.open(f).convert("RGB") for f in files]
ims = [im.resize((max(1, im.width * height // im.height), height)) for im in ims]
sheet = Image.new("RGB", (sum(im.width for im in ims) + 20 * (len(ims) - 1), height), bg)
x = 0
for im in ims:
    sheet.paste(im, (x, 0)); x += im.width + 20
sheet.save(out, optimize=True)
print(out, sheet.size)
