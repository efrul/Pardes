"""Scores a run of the 100 queries (harness/Eval.kt's jsonl): is the expected source in the first ten, and where.

    python score.py results.jsonl [--details]

Two levels: the exact source (expected.py's heRef patterns), and the same siman / chapter / daf (the seif or verse
dropped, and the Tur on the same siman of the Shulchan Aruch). Queries 96-100 have no source: noise checks.
"""

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from expected import E  # noqa: E402

NAMES = {"A": "שאלה הלכתית", "B": "פסוק/משנה במילים אחרות", "C": "עברית→ארמית", "D": "סיפור לפי תיאור",
         "E": "כתיב/ר\"ת/נרדפים", "F": "ציטוט מדויק", "G": "שמות ומונחים", "H": "אנגלית", "I": "רעש (אין תשובה)"}
SYSTEMS = ("lexical", "hybrid", "dense", "hybridBase")


def relaxed(pats: list[str]) -> list[str]:
    """Same siman / chapter / daf: the seif or verse dropped, and the Tur on the same siman of the Shulchan Aruch."""
    out = list(pats)
    for p in pats:
        q = re.sub(r", \(?[^,]*?\$$", ", ", p)
        out.append(q)
        m = re.match(r"\^שולחן ערוך, (אורח חיים|יורה דעה|חושן משפט|אבן העזר) (\S+), ", q)
        if m:
            out.append(rf"^טור, {m.group(1)},\s+{m.group(2)}, ")
    return out


def rank(hits: list[dict], pats: list[str]) -> int | None:
    """1-based place of the first of [hits] (first ten) whose heRef matches one of [pats]."""
    for i, h in enumerate(hits[:10]):
        if any(re.search(p, h.get("heRef") or "") for p in pats):
            return i + 1
    return None


def main() -> None:
    rows = [json.loads(line) for line in open(sys.argv[1], encoding="utf-8")]
    systems = [s for s in SYSTEMS if any(r.get(s) for r in rows)]
    per = defaultdict(lambda: defaultdict(list))
    detail = []
    for r in rows:
        if r["id"] not in E:
            continue
        ranks = {s: rank(r[s], E[r["id"]]) for s in systems}
        loose = {s: rank(r[s], relaxed(E[r["id"]])) for s in systems}
        detail.append((r["id"], r["group"], r["scope"], ranks))
        for g in (r["group"], "ALL"):
            for s in systems:
                per[g][s].append(ranks[s])
                per[g][s + "~"].append(loose[s])

    def fmt(ranks):
        hit = sum(1 for x in ranks if x) / len(ranks)
        mrr = sum(1 / x for x in ranks if x) / len(ranks)
        return f"{hit:.2f} / {mrr:.2f} / {sum(1 for x in ranks if x == 1)}"

    for suffix, title in (("", "מקור מדויק"), ("~", "אותו סימן/פרק/דף")):
        print(f"\n{title}: hit@10 / MRR@10 / מקום ראשון")
        print("| קבוצה | n | " + " | ".join(systems) + " |")
        print("|---|---|" + "---|" * len(systems))
        for g in list("ABCDEFGH") + ["ALL"]:
            if g in per:
                n = len(per[g][systems[0]])
                print(f"| {NAMES.get(g, 'הכל')} | {n} | " + " | ".join(fmt(per[g][s + suffix]) for s in systems) + " |")
    if "--details" in sys.argv:
        for qid, g, scope, ranks in detail:
            print(qid, g, scope, " ".join(f"{s}={ranks[s] or '-'}" for s in systems))


if __name__ == "__main__":
    main()
