"""The title and notes of a semantic pack's release on GitHub, from the pack's manifest (package_pack.py's
pardes-semantic.tar.zst.manifest.json): semantic-release.yml publishes a new pack with them, and semantic-notes.yml
writes them again on a pack already released, after a change here. The user reads them: they say "the files of the
search by meaning" and their version, never "a pack".

    python release_notes.py pack/pardes-semantic.tar.zst.manifest.json > notes.md
    python release_notes.py --title pack/pardes-semantic.tar.zst.manifest.json
"""

import argparse
import json

TITLE = "קובצי החיפוש לפי משמעות, גרסה {pack}"

NOTES = """\
הקבצים של החיפוש לפי משמעות בפרדס, גרסה {pack}: המודל ואינדקס הווקטורים של כל הספרייה. מורידים אותם פעם אחת, בנפרד מהיישום ומהספרייה, והם מתאימים לכל גרסה של הספרייה. לפרדס למחשב ולפרדס לאנדרואיד 0.6.0 ומעלה.

**התקנה מתוך פרדס**
בהגדרות › ניהול נתונים › חיפוש לפי משמעות לוחצים **הורדה והתקנה**. פרדס מוריד את הקבצים, בודק אותם, והחיפוש לפי משמעות פועל מההפעלה הבאה.

**בלי חיבור ל־GitHub מתוך פרדס**
מורידים בדפדפן, גם במחשב אחר, רק את שני קובצי ה־zip: `pardes-model.zip` ו־`pardes.vec.lucene.zip`. באותו מסך לוחצים **ייבוא מקבצים…** ובוחרים אחד מהם. שאר הקבצים כאן הם בשביל ההורדה מתוך פרדס.

**התקנה ידנית**
1. סוגרים את פרדס.
2. מוחקים מתיקיית הספרייה את התיקיות הישנות `pardes-model` ו־`pardes.vec.lucene`, אם יש. מחליפים את שתיהן יחד: מודל ואינדקס מגרסאות שונות לא עובדים זה עם זה.
3. פורסים את שני קובצי ה־zip לתיקיית הספרייה, ליד `seforim.db`. הגדרות › ניהול נתונים › מיקום הספרייה מראה איפה היא.
4. פותחים את פרדס.

**מה יש כאן**
- `pardes-model`: המודל לשאילתות, נגזר מ־[neodictabert-bilingual-embed](https://huggingface.co/dicta-il/neodictabert-bilingual-embed) של דיקטה, מאומן לספרייה. ONNX int8, ‏{dimension} ממדים (`{model}`).
- `pardes.vec.lucene`: אינדקס הווקטורים של הספרייה כולה, {lines} שורות. נבנה על הספרייה מגרסת SeforimLibrary ‏`{library}`, ומתאים גם לגרסאות האחרות שלה.

**רישיון**
המודל נגזר מ־neodictabert-bilingual-embed של דיקטה: המרכז הישראלי לניתוח טקסטים, ומופץ לפי [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). מי שמעביר אותו הלאה מעביר גם את `pardes-model/NOTICE.md`. דיקטה אינה ממליצה על פרדס.

פרדס מבוסס על [זית](https://github.com/kdroidFilter/Zayit) מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה. המודל והאינדקס הם של פרדס, ואינם באים מהחיפוש הסמנטי של זית.
"""


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest", help="the pack's pardes-semantic.tar.zst.manifest.json")
    ap.add_argument("--title", action="store_true", help="the release's title instead of its notes")
    args = ap.parse_args()
    with open(args.manifest, encoding="utf-8") as f:
        manifest = json.load(f)
    values = {"pack": manifest["pack"], "model": manifest["model"], "dimension": manifest["dimension"],
              "library": manifest["library"], "lines": f"{manifest['lines']:,}"}
    print((TITLE if args.title else NOTES).format(**values), end="\n" if args.title else "")


if __name__ == "__main__":
    main()
