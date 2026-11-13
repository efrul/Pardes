"""The daf view's layouts (צורת הדף) made a release of this repository (daf-release.yml): each tractate's layout file
gzipped, a zip of them all to unpack beside the library, and the manifest Pardes for desktop reads to download them
(features/bookcontent/daf/DafDownloads.kt, DafManifest). The layouts are pardes-rashi-ocr's layouts/*.json, pushed
to the branch daf-layouts.

    python3 daf_release.py LAYOUTS_DIR OUT_DIR META_DIR --number N [--previous MANIFEST] [--note TEXT]

OUT_DIR: <Tractate>.json.gz, pardes-daf.zip and pardes-daf.manifest.json, the release's files.
META_DIR: tag.txt, title.txt and notes.md.

Each tractate has a version of its own, written in its file's header ("version") and in the manifest: 1 at first, one
more in a release where its layout is not the one of the release before (MANIFEST, that release's manifest: its
"contentSha256" is the layout's without its version), the same otherwise. A tractate that did not change is then the
same file, byte for byte, and Pardes downloads only those that did.
"""

import argparse
import gzip
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path

MANIFEST = "pardes-daf.manifest.json"
ZIP = "pardes-daf.zip"
FOLDER = "pardes-daf"
FORMATS = {1, 2}
# The Bavli's tractates the layouts leave out, as the release says (pardes-rashi-ocr's README)
LEFT_OUT = "מעילה, נדרים, תמיד ושקלים"
# The Shas's order, which the manifest and the notes list the tractates in
ORDER = ["Berakhot", "Shabbat", "Eruvin", "Pesachim", "Shekalim", "Yoma", "Sukkah", "Beitzah", "Rosh Hashanah", "Taanit",
         "Megillah", "Moed Katan", "Chagigah", "Yevamot", "Ketubot", "Nedarim", "Nazir", "Sotah", "Gittin", "Kiddushin",
         "Bava Kamma", "Bava Metzia", "Bava Batra", "Sanhedrin", "Makkot", "Shevuot", "Avodah Zarah", "Horayot", "Zevachim",
         "Menachot", "Chullin", "Bekhorot", "Arakhin", "Temurah", "Keritot", "Meilah", "Tamid", "Niddah"]

TITLE = "קובצי צורת הדף, גרסה {number}"

NOTES = """\
{note}הקבצים של צורת הדף בפרדס למחשב, גרסה {number}: לכל מסכת של התלמוד הבבלי, כל שורה של הגמרא, רש"י ותוספות במקומה בדף של ש"ס וילנא, במילים שנדפסו בה. {count} מסכתות: {tractates}. אינן כאן: {left_out}.

{changed}

**התקנה מתוך פרדס** (פרדס למחשב 1.0.8 ומעלה)
בצורת הדף של מסכת שאין לה עדיין קובץ לוחצים **הורדת צורת הדף של מסכת…**. בהגדרות › **צורת הדף** (בגרסה 1.0.8: הגדרות › ניהול נתונים › צורת הדף) מורידים את כל המסכתות, ומעדכנים אותן כשיוצאת גרסה חדשה של הקבצים. הסימונים של בדיקת השורות נשמרים.

**התקנה ידנית**
פורסים את `{zip}` לתיקיית הספרייה, ליד `seforim.db`: נוצרת בה התיקייה `{folder}`, ובה קובץ לכל מסכת. הגדרות › ניהול נתונים › מיקום הספרייה מראה איפה היא. אפשר גם להוריד מסכת אחת (`<שם המסכת>.json.gz`), לפרוס אותה, ובצורת הדף של המסכת ללחוץ **ייבוא קובץ צורת הדף…**.

**מקורות ורישיון**
- מקום השורות נמדד בסריקות של מהדורה מאוחרת ששומרת על הדף של ש"ס וילנא, שורה בשורה. מהן נלקח רק איפה כל שורה נשברת ויושבת, בלי תמונה ובלי מילה.
- מילות הגמרא הן מהנוסח של ויקיטקסט (Wikisource Talmud Bavli), דרך ספריא, ברישיון [CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/). רש"י ותוספות הם ממהדורת וילנא שבספריא, שהיא נחלת הכלל.
- לכן גם הקבצים האלה מופצים ברישיון CC BY-SA: מי שמעביר אותם הלאה מציין את המקורות האלה, ומפיץ אותם באותו רישיון.

פרדס מבוסס על [זית](https://github.com/kdroidFilter/Zayit) מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה. קובצי צורת הדף הם של פרדס.
"""


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def gzip_bytes(data: bytes) -> bytes:
    """gzip -9 without a time or a name in its header: the same layout packs to the same bytes."""
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0, filename="") as gz:
        gz.write(data)
    return buf.getvalue()


def packed(layout: dict) -> bytes:
    """The layout as its file holds it: one line, as pardes-rashi-ocr's export_layout.py writes it."""
    return json.dumps(layout, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def versioned(layout: dict, version: int) -> dict:
    """The layout with its version after its names, at the head of its file, where the app reads it."""
    out = {}
    for key, value in layout.items():
        if key == "version":
            continue
        out[key] = value
        if key == "tractateHe":
            out["version"] = version
    return out


def version_of(content_sha: str, before: dict | None) -> int:
    """A tractate's version: the one before it for the same layout, one more for another, 1 for a first."""
    if before is None:
        return 1
    known = before.get("version") if isinstance(before.get("version"), int) else 0
    return known if known and before.get("contentSha256") == content_sha else known + 1


def read_layout(path: Path) -> dict:
    """The layout's header and size, after checking it is one: a format the app reads, its names, its amudim."""
    data = json.loads(path.read_bytes())
    if data.get("format") not in FORMATS:
        raise SystemExit(f"{path.name}: format {data.get('format')} is not one the app reads ({sorted(FORMATS)})")
    for key in ("tractate", "tractateHe"):
        if not isinstance(data.get(key), str) or not data[key].strip():
            raise SystemExit(f"{path.name}: no {key}")
    if not data.get("amudim"):
        raise SystemExit(f"{path.name}: no amudim")
    return data


def build(layouts: Path, out: Path, meta: Path, number: int, note: str = "", previous: Path | None = None) -> dict:
    """The release's files written to [out] and its tag, title and notes to [meta]; its manifest."""
    files = sorted(layouts.glob("*.json"))
    if not files:
        raise SystemExit(f"No layouts in {layouts}")
    out.mkdir(parents=True, exist_ok=True)
    meta.mkdir(parents=True, exist_ok=True)
    before = {}
    if previous is not None and previous.is_file():
        before = {e["tractate"]: e for e in json.loads(previous.read_bytes()).get("tractates", [])}

    entries, seen, changed = [], set(), []
    zip_path = out / ZIP
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for path in files:
            layout = read_layout(path)
            name = layout["tractate"]
            if name in seen:
                raise SystemExit(f"{path.name}: {name} twice")
            seen.add(name)
            # What the layout is, whatever version it was given: a release's tractate changed when this did
            content_sha = sha256(packed({key: value for key, value in layout.items() if key != "version"}))
            version = version_of(content_sha, before.get(name))
            if before and (name not in before or before[name].get("version") != version):
                changed.append((layout["tractateHe"], version))
            raw = packed(versioned(layout, version))
            # A release's file name has no spaces (GitHub makes them dots): "Avodah Zarah" is AvodahZarah
            safe = re.sub(r"[^A-Za-z0-9]", "", name)
            gz = gzip_bytes(raw)
            (out / f"{safe}.json.gz").write_bytes(gz)
            z.writestr(f"{FOLDER}/{safe}.json", raw)
            entries.append({
                "tractate": name,
                "tractateHe": layout["tractateHe"],
                "version": version,
                "file": f"{safe}.json.gz",
                "size": len(gz),
                "sha256": sha256(gz),
                "jsonSha256": sha256(raw),
                "contentSha256": content_sha,
                "amudim": len(layout["amudim"]),
            })
    entries.sort(key=lambda e: (ORDER.index(e["tractate"]) if e["tractate"] in ORDER else len(ORDER), e["tractate"]))
    manifest = {"format": 1, "daf": number, "tractates": entries}
    (out / MANIFEST).write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", "utf-8")

    note = f"{note.strip()}\n\n" if note.strip() else ""
    tractates = ", ".join(e["tractateHe"] for e in entries)
    (meta / "tag.txt").write_text(f"daf-{number}", "utf-8")
    (meta / "title.txt").write_text(TITLE.format(number=number), "utf-8")
    (meta / "notes.md").write_text(
        NOTES.format(note=note, number=number, count=len(entries), tractates=tractates, left_out=LEFT_OUT,
                     changed=changes(changed, len(entries), bool(before)), zip=ZIP, folder=FOLDER),
        "utf-8",
    )
    total = sum(e["size"] for e in entries)
    print(f"daf-{number}: {len(entries)} tractates, {len(changed) if before else len(entries)} of them new or changed, "
          f"{total / 1e6:.1f} MB gzipped, {zip_path.stat().st_size / 1e6:.1f} MB zip", file=sys.stderr)
    return manifest


def changes(changed: list[tuple[str, int]], count: int, has_before: bool) -> str:
    """The notes' word on which tractates this release changes: Pardes downloads those alone."""
    if not has_before or len(changed) == count:
        return "לכל מסכת גרסה משלה, והיא עולה רק כשהקובץ של המסכת משתנה. פרדס מעדכן רק את המסכתות שהשתנו."
    if not changed:
        return "אף מסכת לא השתנתה מאז המהדורה הקודמת של הקבצים."
    names = ", ".join(f"{he} (גרסה {version})" for he, version in changed)
    return f"מאז המהדורה הקודמת של הקבצים השתנו {len(changed)} מסכתות: {names}. שאר המסכתות נשארו כמו שהן, ופרדס לא מוריד אותן שוב."


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("layouts", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("meta", type=Path)
    ap.add_argument("--number", type=int, required=True)
    ap.add_argument("--previous", type=Path, default=None, help="the manifest of the release before this one")
    ap.add_argument("--note", default="")
    args = ap.parse_args()
    build(args.layouts, args.out, args.meta, args.number, args.note, args.previous)


if __name__ == "__main__":
    main()
