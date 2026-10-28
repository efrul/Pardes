"""What the pages of the replayed history (harness/History.kt's jsonl) are made of, scope by scope: where the lines come
from, short lines and their kinds, where the lexical side's first lands, base books against Zayit's page, repeated
texts, the dense side's first score, the rows the screen shows of the first 25, and the short-line rule's effect.
Issues 1, 2 and 6-10 of docs/SEARCH_ORDER_ISSUES.md.

    python analyze.py history.jsonl history_meta.json seforim.db [--report order.md]

--report writes every search's first 10 (where each line comes from, its places on both sides, its words, its ref and
the beginning of its text) and Zayit's first 5: a file with the books' text and the user's searches, kept out of the repo.
"""

import json
import re
import statistics
import sys
from collections import Counter, defaultdict

from order_common import Lines, holds_quote, ids, keep, rrf

SHORT_WORDS = 8
SCOPES = {"book": "a chosen book", "base": "base books", "all": "all books (on)", "fallback": "base books, then all books"}


def kind(raw: str) -> str:
    if re.search(r"<h\d", raw):
        return "heading"
    if raw.lstrip().startswith(("<b", "<big", "<strong")):
        return "bold start"
    return "plain"


def shown(scope, hybrid, lines):
    """flattenResults on the first 25: in a chosen book every line; otherwise a run of one book shows its first line,
    and a text already shown is skipped."""
    if scope == "book":
        return list(hybrid[:25])
    out, seen, run = [], set(), None
    for d in hybrid[:25]:
        if lines[d]["book"] == run:
            continue
        run = lines[d]["book"]
        if lines[d]["key"] in seen:
            continue
        seen.add(lines[d]["key"])
        out.append(d)
    return out


def short_rule(r, lines):
    """Issue 1's rule: dense lines of SHORT_WORDS words or fewer the lexical side doesn't have taken out."""
    lex, dense = ids(r["lexCand"]), ids(r["denseCand"])
    dense = [d for d in dense if lines[d]["words"] > SHORT_WORDS or d in set(lex)]
    fused = rrf(lex, dense) if dense else list(lex)
    return keep(fused, [(d, (1, 3)[j]) for j, d in enumerate(lex[:2]) if holds_quote(lines.text(d), r["query"])])


def main() -> None:
    rows = [json.loads(line) for line in open(sys.argv[1], encoding="utf-8")]
    meta = {m["id"]: m for m in json.load(open(sys.argv[2], encoding="utf-8"))}
    lines = Lines(sys.argv[3]).load(d for r in rows for k in ("hybrid", "lexPage", "lexCand", "denseCand", "hybridBase") for d in ids(r[k]))
    count = defaultdict(Counter)
    values = defaultdict(list)
    lexical_first = defaultdict(Counter)
    for r in rows:
        scope, query = r["scope"], r["query"]
        hyb, lex, dense = ids(r["hybrid"]), ids(r["lexCand"]), ids(r["denseCand"])
        in_lex, in_dense = set(lex), set(dense)
        c = count[scope]
        c["searches"] += 1
        c["the lexical side found nothing"] += not lex
        quoted = '"' in query
        c["quoted (the words alone)"] += quoted
        plain = rrf(lex, dense) if dense else list(lex)
        c["keepLeaders changed the page"] += not quoted and hyb != plain[:len(hyb)]
        for d in hyb[:10]:
            c["first 10: lines"] += 1
            src = "both" if d in in_lex and d in in_dense else "lexical alone" if d in in_lex else "dense alone"
            c[f"first 10: {src}"] += 1
            c["first 10: base books"] += lines[d]["base"]
            if lines[d]["words"] <= SHORT_WORDS:
                c[f"first 10: short ({SHORT_WORDS} words or fewer)"] += 1
                c[f"first 10: short, {src}"] += 1
                if src == "dense alone" and scope != "book":
                    count["(not in a chosen book)"][f"short dense-alone lines: {kind(lines.text(d))}"] += 1
        for d in ids(r["lexPage"])[:10]:
            c["Zayit's first 10: lines"] += 1
            c["Zayit's first 10: base books"] += lines[d]["base"]
        keys = [lines[d]["key"] for d in hyb[:10]]
        c["searches with a text twice in the first 10"] += len(keys) != len(set(keys))
        if lex and not quoted:
            p = hyb.index(lex[0]) + 1 if lex[0] in hyb else None
            lexical_first[scope]["1" if p == 1 else "2-3" if p and p <= 3 else "4-10" if p and p <= 10 else "11-50" if p else "not in the first 50"] += 1
            values[f"{scope}: Zayit's first 10 in the hybrid's first 10"].append(len(set(ids(r["lexPage"])[:10]) & set(hyb[:10])))
        if dense and not quoted:
            values[f"{scope}: dense first score"].append(r["denseCand"][0][1] if isinstance(r["denseCand"][0], list) else 0)
        values[f"{scope}: rows on screen of the first 25"].append(len(shown(scope, hyb, lines)))
        if not quoted:
            after = short_rule(r, lines)[:10]
            c["short rule: short lines in the first 10"] += sum(1 for d in after if lines[d]["words"] <= SHORT_WORDS)
    for scope in list(SCOPES) + ["(not in a chosen book)"]:
        if scope not in count:
            continue
        print(f"\n## {SCOPES.get(scope, scope)}")
        for k, v in sorted(count[scope].items()):
            print(f"  {k}: {v}")
        if lexical_first[scope]:
            print("  the lexical first in the hybrid, at: " + ", ".join(f"{k} {v}" for k, v in sorted(lexical_first[scope].items())))
        for k, v in sorted(values.items()):
            if k.startswith(scope + ":") and v:
                v = sorted(v)
                print(f"  {k.split(': ', 1)[1]}: median {statistics.median(v):.3f}, p10 {v[len(v) // 10]:.3f}, p90 {v[int(len(v) * 0.9)]:.3f}, n {len(v)}")
    low = [(r["query"], r["denseCand"][0][1]) for r in rows if r["denseCand"] and r["scope"] != "book" and not r["lexCand"]]
    print("\nno lexical hit, dense first score: " + ", ".join(f"{q[:30]} {s:.3f}" for q, s in sorted(low, key=lambda x: x[1])[:6]))

    if "--report" in sys.argv:
        report(rows, meta, lines, sys.argv[sys.argv.index("--report") + 1])


def report(rows, meta, lines, path):
    titles = dict(lines.con.execute("SELECT id, title FROM book"))
    scope_he = {"book": "בספר שנבחר", "base": "ספרי יסוד", "all": "כל הספרים (מופעל)", "fallback": "ספרי יסוד ← כל הספרים (מעבר אוטומטי)"}
    src_he = {"B": "שניהם", "L": "רגיל", "D": "סמנטי"}
    out = ['<div dir="rtl">', "", "# הסדר של כל חיפוש", "",
           "עשר הראשונות של המשולב: מקור (ר = מקום ברגיל, ס = מקום בסמנטי וציונו), מ = מילים. ⬆ = הועלה על ידי keepLeaders,",
           "~ = לא מוצג במסך (flattenResults).", ""]
    for r in rows:
        hyb, lex, dense = ids(r["hybrid"]), ids(r["lexCand"]), ids(r["denseCand"])
        lr, dr = {d: i for i, d in enumerate(lex)}, {d: i for i, d in enumerate(dense)}
        score = {d: s for d, s in r["denseCand"]} if dense and isinstance(r["denseCand"][0], list) else {}
        plain = rrf(lex, dense) if dense else list(lex)
        on_screen = set(shown(r["scope"], hyb, lines))
        where = scope_he[r["scope"]] + (f" — {titles.get(r['book'], r['book'])}" if r["book"] else "")
        apps = " + ".join("זית" if a == "zayit" else "פרדס" if a == "pardes" else a for a in meta.get(r["id"], {}).get("apps", []))
        out += [f"## {r['id']}. {r['query'][:150]}", f"{where}; הרגיל מצא {r['lexScopeTotal']} שורות; {apps}", "",
                "| # | מקור | מ | הפניה | טקסט |", "|---|---|---|---|---|"]
        for i, d in enumerate(hyb[:10], 1):
            src = "B" if d in lr and d in dr else "L" if d in lr else "D"
            tag = src_he[src] + (f" ר{lr[d] + 1}" if d in lr else "") + (f" ס{dr[d] + 1} ({score.get(d, 0):.3f})" if d in dr else "")
            mark = ("⬆" if d in lr and (d not in plain or hyb.index(d) < plain.index(d)) else "") + ("" if d in on_screen else "~")
            text = lines[d]["raw"]
            text = " ".join(re.sub(r"<[^>]*>", " ", text).split()).replace("|", "¦")[:110]
            out.append(f"| {i}{mark} | {tag} | {lines[d]['words']} | {lines[d]['ref'][:50]} | {text} |")
        if r["lexPage"]:
            out += ["", "זית (רגיל): " + " · ".join(lines[d]["ref"][:40] for d in ids(r["lexPage"])[:5])]
        if r["hybridBase"]:
            out += ["", "בלי המעבר (משולב בספרי יסוד): " + " · ".join(lines[d]["ref"][:40] for d in ids(r["hybridBase"])[:5])]
        out.append("")
    out.append("</div>")
    open(path, "w", encoding="utf-8").write("\n".join(out))


if __name__ == "__main__":
    main()
