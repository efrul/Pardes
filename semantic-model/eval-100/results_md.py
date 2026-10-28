"""The first ten of each engine for each query, as references only (results-*.md and the summary table).

    python results_md.py results.jsonl results.md summary.md "<what was run>"

No text of the lines: some results are modern works of the library that are not in the public domain.
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from expected import E  # noqa: E402
from score import rank, relaxed  # noqa: E402


def mark(ref: str, pats: list[str]) -> str:
    if not pats:
        return ""
    if any(re.search(p, ref) for p in pats):
        return " ✅"
    return " ≈" if any(re.search(p, ref) for p in relaxed(pats)) else ""


def place(hits: list[dict], pats: list[str]) -> str:
    if not pats:
        return f"{len(hits)} תוצאות"
    exact, near = rank(hits, pats), rank(hits, relaxed(pats))
    return str(exact) if exact else (f"≈{near}" if near else "—")


def main() -> None:
    src, out_md, out_summary, what = sys.argv[1:5]
    rows = [json.loads(line) for line in open(src, encoding="utf-8")]
    full = ["# 100 השאילתות: עשר התוצאות הראשונות של כל מנוע", "", what, "",
            "הפניות בלבד, בלי טקסט: חלק מהתוצאות מספרים מודרניים שאינם נחלת הכלל. ✅ המקור הצפוי, ≈ אותו סימן/פרק/דף.", ""]
    summary = ["| # | שאילתה | מקור צפוי | רגיל | משולב | הראשון במשולב |", "|---|---|---|---|---|---|"]
    for r in rows:
        pats = E.get(r["id"], [])
        top = r["hybrid"][0]["heRef"] if r["hybrid"] else "(אין)"
        summary.append(f"| {r['id']} | {r['query']} | {r['expected']} | {place(r['lexical'], pats)} | "
                       f"{place(r['hybrid'], pats)} | {top} |")
        scope = "ספרי יסוד" if r["scope"] == "base" else "כל הספרים"
        full += [f"## {r['id']}. {r['query']}", "",
                 f"מקור צפוי: {r['expected']} · חיפוש ב{scope} (רגיל בספרי יסוד: {r['lexicalBaseHits']} תוצאות) · "
                 f"רגיל {place(r['lexical'], pats)}, משולב {place(r['hybrid'], pats)}, סמנטי {place(r['dense'], pats)}", "",
                 "| # | רגיל | משולב | סמנטי בלבד |", "|---|---|---|---|"]
        for i in range(max(len(r[s]) for s in ("lexical", "hybrid", "dense")) or 0):
            cells = [(r[s][i]["heRef"] + mark(r[s][i]["heRef"], pats)) if i < len(r[s]) else "" for s in
                     ("lexical", "hybrid", "dense")]
            full.append(f"| {i + 1} | " + " | ".join(cells) + " |")
        full.append("")
    Path(out_md).write_text("\n".join(full) + "\n", "utf-8")
    Path(out_summary).write_text("\n".join(summary) + "\n", "utf-8")


if __name__ == "__main__":
    main()
