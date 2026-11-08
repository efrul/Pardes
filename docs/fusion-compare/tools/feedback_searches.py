"""The searches and grades of a feedback export (the feedback service's /admin JSON) as the harness reads them.

    python feedback_searches.py feedback.json OUT_DIR

Writes OUT_DIR/searches.tsv (id, query, kind, scopeId, ext: one row for each query and scope), search_meta.json (how
many times each was searched, from which app) and grades.json ("query<TAB>lineId": the latest grade).
"""

import json
import re
import sys
from collections import defaultdict


def norm(q):
    return re.sub(r"\s+", " ", q).strip()


def main():
    d = json.load(open(sys.argv[1], encoding="utf-8"))
    out = sys.argv[2]
    seen = {}
    meta = defaultdict(lambda: {"count": 0, "sources": set()})
    for p in d["packages"]:
        for s in p["package"].get("searches", []):
            q = norm(s["query"])
            if not q:
                continue
            if "bookId" in s:
                k = ("book", s["bookId"], 0)
            elif "categoryId" in s:
                k = ("cat", s["categoryId"], 1 if s["extended"] else 0)
            else:
                k = ("all" if s["extended"] else "auto", "", 1 if s["extended"] else 0)
            key = (q,) + k
            seen.setdefault(key, len(seen) + 1)
            meta[key]["count"] += s.get("count", 1)
            meta[key]["sources"].add(s.get("source") or "pardes")
    with open(f"{out}/searches.tsv", "w", encoding="utf-8") as f:
        f.write("id\tquery\tkind\tscopeId\text\n")
        for (q, kind, sid, ext), i in seen.items():
            f.write(f"{i}\t{q}\t{kind}\t{sid}\t{ext}\n")
    json.dump({str(i): {"count": meta[k]["count"], "sources": sorted(meta[k]["sources"])} for k, i in seen.items()},
              open(f"{out}/search_meta.json", "w", encoding="utf-8"), ensure_ascii=False)
    grades = {}
    for p in d["packages"]:
        for g in p["package"].get("grades", []):
            k = f"{norm(g['query'])}\t{g['lineId']}"
            if k not in grades or g["gradedAt"] > grades[k]["at"]:
                grades[k] = {"grade": g["grade"], "at": g["gradedAt"], "search": g["search"]}
    json.dump(grades, open(f"{out}/grades.json", "w", encoding="utf-8"), ensure_ascii=False)
    print(f"{len(seen)} searches, {len(grades)} graded lines")


if __name__ == "__main__":
    main()
