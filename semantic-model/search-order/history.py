"""The searches of the apps' histories, for harness/History.kt: the `visit_history` of one user_settings.db or more
(Zayit's, Pardes's), merged in time order, one line per search (the same query, book and "all books" once).

    python history.py out_dir [--until "YYYY-MM-DD HH:MM"] NAME=user_settings.db [NAME=user_settings.db ...]

Writes history.tsv (id, query, book, ext) and history_meta.json (each search's apps, visits, time). Searches in a
category or a table-of-contents entry are left out (the app searches them by their words alone), and so are zayit://
links, and with --until (local time) the searches made after it. Prints how often an "all books" search came right
after another one in the same app (an automatic switch stays on in the tab: SearchResultViewModel).

visit_history keeps one row per search (query and scope) with the time of its last visit: a search made again later
moves, so a list is reproduced only from the same copy of the database. The history is the user's: it stays out of the
repo, and so does every output made from it.
"""

import json
import re
import sqlite3
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path


def main() -> None:
    args = sys.argv[1:]
    until = None
    if "--until" in args:
        at = args.index("--until")
        until = datetime.strptime(args[at + 1], "%Y-%m-%d %H:%M").timestamp() * 1000
        del args[at:at + 2]
    out = Path(args[0])
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for arg in args[1:]:
        app, db = arg.split("=", 1)
        con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
        q = ("SELECT visitedAt, searchQuery, visitCount, searchCategoryId, searchBookId, searchTocId, searchExtended "
             "FROM visit_history WHERE kind = 'search' ORDER BY visitedAt")
        for at, query, count, cat, book, toc, ext in con.execute(q):
            if until is not None and at > until:
                continue
            rows.append(dict(app=app, at=at, query=re.sub(r"\s+", " ", query or "").strip(), count=count, cat=cat, book=book, toc=toc, ext=bool(ext)))
        # An "all books" search (no book) right after another one, in this app's own order
        mine = [r for r in rows if r["app"] == app]
        after = Counter()
        for prev, cur in zip(mine, mine[1:]):
            if cur["ext"] and not cur["book"]:
                after["after another all-books search" if prev["ext"] and not prev["book"] else "after another search"] += 1
        print(f"{app}: {len(mine)} searches; all books: {dict(after)}")
    rows.sort(key=lambda r: r["at"])
    seen, merged = {}, []
    for r in rows:
        if not r["query"] or r["query"].startswith("zayit://") or r["cat"] or r["toc"]:
            continue
        key = (r["query"], r["book"], r["ext"])
        if key in seen:
            seen[key]["apps"].add(r["app"])
            seen[key]["visits"] += r["count"]
            continue
        seen[key] = dict(query=r["query"], book=r["book"], ext=r["ext"], apps={r["app"]}, visits=r["count"], time=r["at"])
        merged.append(seen[key])
    with open(out / "history.tsv", "w", encoding="utf-8", newline="\n") as f:
        f.write("id\tquery\tbook\text\n")
        for i, s in enumerate(merged, 1):
            f.write(f"{i}\t{s['query']}\t{s['book'] or ''}\t{int(s['ext'])}\n")
    meta = [dict(s, id=i, apps=sorted(s["apps"])) for i, s in enumerate(merged, 1)]
    json.dump(meta, open(out / "history_meta.json", "w", encoding="utf-8"), ensure_ascii=False)
    kinds = Counter("book" if s["book"] else "all books" if s["ext"] else "base books first" for s in merged)
    print(f"{len(rows)} records, {len(merged)} searches: {dict(kinds)}")


if __name__ == "__main__":
    main()
