#!/usr/bin/env python3
"""Writes assets/licenses/Fonts-NOTICE.txt: the copyright and licence notices that every bundled font carries in its
name table (IDs 0, 13 and 14), verbatim, one section per family. The licences page (Settings › רישיונות) shows it.

Usage (from the repository root; needs fontTools), after adding, removing or updating a font in res/font:
    python3 .claude/skills/zayit-android/scripts/font-notices.py androidApp
"""
import os
import sys

from fontTools.ttLib import TTFont

app = sys.argv[1]
font_dir = os.path.join(app, "app/src/androidMain/res/font")
out_path = os.path.join(app, "app/src/androidMain/assets/licenses/Fonts-NOTICE.txt")

families = {}
for name in sorted(os.listdir(font_dir)):
    table = TTFont(os.path.join(font_dir, name), lazy=True)["name"]

    def field(i):
        text = table.getDebugName(i) or ""
        return "\n".join(line.rstrip() for line in text.replace("\r\n", "\n").replace("\r", "\n").strip().split("\n"))

    family = field(16) or field(1)
    notice = tuple(field(i) for i in (0, 13, 14))
    families.setdefault(family, {}).setdefault(notice, []).append(name)

parts = [
    "The notices below are those embedded in the fonts bundled with Pardes (name table: copyright, licence "
    "description and licence URL), copied verbatim. See also GPL-2.0.txt, Culmus-LICENSE.txt, "
    "Bitstream-Charter-LICENSE.txt, Apache-2.0.txt and the OFL-*.txt files."
]
for family in sorted(families, key=str.lower):
    for notice, files in families[family].items():
        copyright_, licence, url = notice
        lines = [f"{family} ({', '.join(files)})", "=" * 78]
        if copyright_:
            lines += ["", copyright_]
        if licence:
            lines += ["", licence]
        if url:
            lines += ["", url]
        parts.append("\n".join(lines))

os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n\n\n".join(parts) + "\n")
print(out_path, sum(len(v) for v in families.values()), "notice sections")
