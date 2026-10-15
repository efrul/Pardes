#!/usr/bin/env python3
"""Builds the Temple countdown widget's picture from David Roberts, "The Siege and Destruction of Jerusalem by the
Romans Under the Command of Titus, A.D. 70" (1850), public domain:
https://commons.wikimedia.org/wiki/File:David_Roberts_-_The_Siege_and_Destruction_of_Jerusalem_by_the_Romans_Under_the_Command_of_Titus,_A.D._70.jpg
It replaced the Shutterstock picture inherited from desktop Zayit ("No use without permission").

The crop is the burning city under the smoke, with the Temple at the lower left and none of the painting's people.
Writes the still picture (androidApp res/drawable-nodpi/temple_small.jpg and desktop's
temple_jerusalem_in_fire_{small,medium,large}.jpg) and the animated one (res/raw/temple_jerusalem_in_fire.webp): the
fires' glow flickers in a seamless loop.

Usage (from the repository root; needs Pillow and numpy), with the painting downloaded at 3840 px or more:
    python3 .claude/skills/zayit-android/scripts/temple-picture.py <painting.jpg> .
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

painting, repo = sys.argv[1], sys.argv[2]
# The crop on a 1920-wide copy of the painting (5:4, like the widget's picture)
CROP = (560, 40, 1460, 760)
FRAMES, FRAME_MS = 40, 90

src = Image.open(painting).convert("RGB")
s = src.width / 1920
region = src.crop(tuple(round(v * s) for v in CROP))


def still(width, height):
    return region.resize((width, height), Image.LANCZOS)


def save_jpg(img, path):
    img.save(path, "JPEG", quality=88, optimize=True, progressive=True)


android = os.path.join(repo, "androidApp/app/src/androidMain/res")
desktop = os.path.join(repo, "SeforimApp/src/commonMain/composeResources/drawable")
base = still(400, 320)
save_jpg(base, os.path.join(android, "drawable-nodpi/temple_small.jpg"))
for name, size in (("small", (400, 320)), ("medium", (600, 480)), ("large", (1000, 800))):
    save_jpg(still(*size), os.path.join(desktop, f"temple_jerusalem_in_fire_{name}.jpg"))

# Fire mask: bright, warm pixels, softened so the flicker has no hard edges
rgb = np.asarray(base, dtype=np.float32) / 255
r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
mask = np.clip((r - b - 0.22) / 0.3, 0, 1) * np.clip((r - 0.5) / 0.3, 0, 1)
mask = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)), dtype=np.float32) / 255
ys, xs = np.mgrid[0 : rgb.shape[0], 0 : rgb.shape[1]].astype(np.float32)

frames = []
for t in range(FRAMES):
    phase = 2 * math.pi * t / FRAMES
    # Sums of travelling waves whose time frequencies are whole multiples of the loop: the last frame meets the first
    noise = (
        0.5 * np.sin(phase + xs * 0.045 + ys * 0.02)
        + 0.3 * np.sin(2 * phase - xs * 0.07 + ys * 0.05 + 1.3)
        + 0.2 * np.sin(3 * phase + xs * 0.11 - ys * 0.09 + 2.1)
    )
    factor = 1 + 0.22 * mask * noise
    frame = rgb * factor[..., None]
    frame[..., 0] += 0.04 * mask * np.clip(noise, 0, 1)
    frames.append(Image.fromarray((np.clip(frame, 0, 1) * 255).astype(np.uint8)))

webp = os.path.join(android, "raw/temple_jerusalem_in_fire.webp")
frames[0].save(webp, save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0, quality=80, method=6)
print("wrote", webp, os.path.getsize(webp), "bytes")
