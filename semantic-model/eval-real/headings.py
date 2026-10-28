"""Headings with a lower score, offline: a heading the search does not quote is marked as such and moved down.

    python headings.py results.jsonl seforim.db (--grades grades.tsv | --targets) [--cache lines.pkl] [--details]

A heading: a line the book's table of contents points to (tocEntry.lineId), a line wholly in <h1>..<h6>, or a short line
(10 words at most) whose words are all in bold (<b>, <strong>), like '<small><b>ז. טבילת כלים</b></small>'. A heading
that holds the search as a quote (holdsQuote, fusions.py's near2) is left as it is: the user searched for it. Variants,
on the app's page (RRF w3 of the first 150 of each side, then holdsQuote's leaders at places 2 and 4):
  no dense headings  headings left out of the dense side's candidates before the fusion;
  dense weight 1     a heading's weight on the dense side 1 instead of 3;
  below 3, below 10  the headings of the first 3 (10) move to right after it.
Measures as level_order.py's: --grades (eval-real, eval-v1) first good, good in 3, source in 3, source first, nDCG@3,
a line no one judged counts 0 and is listed at the end; --targets (eval-100) its sources in the first ten, first,
MRR@10.
"""

import json
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from common_source import arg  # noqa: E402
from fusions import keep_leaders, near  # noqa: E402
from level_order import fetch_lines  # noqa: E402
from score import NOISE, dcg, load_grades  # noqa: E402

HEADING_TAG = re.compile(r"^\s*<h[1-6][^>]*>.*</h[1-6]>\s*$", re.S)
BOLD = re.compile(r"<(b|strong)\b[^>]*>.*?</\1>", re.S)
TAG = re.compile(r"<[^>]+>")
LETTER = re.compile(r"[א-תA-Za-z0-9]")


def is_heading(content, in_toc):
    if in_toc or HEADING_TAG.match(content):
        return True
    words = TAG.sub(" ", content).split()
    if not 0 < len(words) <= 10 or BOLD.search(content) is None:
        return False
    return not LETTER.search(TAG.sub("", BOLD.sub("", content)))


def fuse(lexical, dense, weight):
    """fusions.rrf, with each dense line's own weight."""
    score = defaultdict(float)
    for i, d in enumerate(lexical):
        score[d] += 1 / (61 + i)
    for i, d in enumerate(dense):
        score[d] += weight(d) / (61 + i)
    return [d for d, _ in sorted(score.items(), key=lambda x: -x[1])]


def page_of(r, text, dense, weight=lambda d: 3):
    """common_source.app_page over [dense] and [weight]: RRF, then the first two lexical hits holding the query as a
    quote at places 2 and 4."""
    fused = fuse(r["lexCand"], dense, weight) if dense else list(r["lexCand"])
    kept = [d if near(r["query"], text.get(d) or "", 2) else None for d in r["lexCand"][:2]]
    return keep_leaders(fused, kept)


def heading_lines(con, rows, text):
    """{query id: the headings among its candidates that do not hold it as a quote}."""
    need = sorted({d for r in rows.values() for d in r["lexCand"] + r["denseCand"]})
    toc = set()
    for i in range(0, len(need), 900):
        chunk = need[i:i + 900]
        sql = f"SELECT lineId FROM tocEntry WHERE lineId IN ({','.join('?' * len(chunk))})"
        toc |= {d for (d,) in con.execute(sql, chunk)}
    heading = {d for d in need if is_heading(text[d], d in toc)}
    return {q: {d for d in set(r["lexCand"]) | set(r["denseCand"]) if d in heading and not near(r["query"], text[d], 2)}
            for q, r in rows.items()}


def below(page, low, n):
    """[page] with the lines of [low] among its first n moved to right after the nth."""
    top = page[:n]
    return [d for d in top if d not in low] + [d for d in top if d in low] + page[n:]


def main() -> None:
    rows = {r["id"]: r for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
    con = sqlite3.connect(f"file:{sys.argv[2]}?mode=ro", uri=True)
    targets = "--targets" in sys.argv
    if targets:
        sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "eval-100"))
        from expected import E
        rows = {q: r for q, r in rows.items() if q in E}
    else:
        grades = load_grades(arg("--grades"))
        rows = {q: r for q, r in rows.items() if q not in NOISE and q in grades}
    rows = {q: dict(r, lexCand=r["lexCand"][:150]) for q, r in rows.items()}
    need = {d for r in rows.values() for d in r["lexCand"] + r["denseCand"]}
    lines = fetch_lines(con, need, arg("--cache"))
    text = {d: v[2] for d, v in lines.items()}
    title = dict(con.execute("SELECT id, title FROM book"))
    lows = heading_lines(con, rows, text)
    pages = {q: page_of(r, text, r["denseCand"]) for q, r in rows.items()}
    variants = {
        "the app's page": pages,
        "no dense headings": {
            q: page_of(r, text, [d for d in r["denseCand"] if d not in lows[q]]) for q, r in rows.items()
        },
        "dense weight 1": {
            q: page_of(r, text, r["denseCand"], lambda d, q=q: 1 if d in lows[q] else 3) for q, r in rows.items()
        },
        "below 3": {q: below(f, lows[q], 3) for q, f in pages.items()},
        "below 10": {q: below(f, lows[q], 10) for q, f in pages.items()},
    }

    def ref(d):
        b, h, _ = lines[d]
        return " ".join((h or title[b]).split())

    unjudged = defaultdict(set)

    def measure(lists):
        if targets:
            ranks = [next((i + 1 for i, d in enumerate(f[:10]) if any(re.search(p, ref(d)) for p in E[q])), None)
                     for q, f in lists.items()]
            hit, top = sum(1 for x in ranks if x), sum(1 for x in ranks if x == 1)
            return f"{hit}/{len(ranks)} | {top} | {sum(1 / x for x in ranks if x) / len(ranks):.3f}"
        first_good = three = source = source_first = 0
        ndcg = []
        for q, f in lists.items():
            for d in f[:3]:
                if d not in grades[q]:
                    unjudged[q].add(d)
            g = [grades[q].get(d, 0) for d in f[:3]]
            first_good += bool(g) and g[0] >= 2
            three += max(g, default=0) >= 2
            source += max(g, default=0) == 3
            source_first += bool(g) and g[0] == 3
            ideal = dcg(sorted(grades[q].values(), reverse=True)[:3])
            if ideal:
                ndcg.append(dcg(g) / ideal)
        return f"{first_good} | {three} | {source} | {source_first} | {sum(ndcg) / len(ndcg):.3f}"

    head = "hit@10 | first | MRR@10" if targets else "first good | good in 3 | source in 3 | source first | nDCG@3"
    shown = sum(1 for q, f in pages.items() for d in f[:3] if d in lows[q])
    print(f"{len(rows)} queries; headings the search does not quote in the app's first 3: {shown}")
    if not targets:
        grade = [grades[q].get(d) for q, f in pages.items() for d in f[:3] if d in lows[q]]
        print("their grades: " + ", ".join(f"{g} {grade.count(g)}" for g in (0, 1, 2, 3, None)))
    print(f"\n| order | changed in the first 3 | {head} |")
    print("|---|---|---|")
    for name, lists in variants.items():
        changed = sum(1 for q in lists if lists[q][:3] != pages[q][:3])
        print(f"| {name} | {changed} | {measure(lists)} |")
    if unjudged:
        print(f"\nNot judged ({sum(map(len, unjudged.values()))}):")
        for q in sorted(unjudged):
            for d in sorted(unjudged[q]):
                print(f"{q}\t{d}\t{ref(d)}")
    if "--details" in sys.argv:
        for q, f in pages.items():
            for d in f[:3]:
                if d in lows[q]:
                    g = "" if targets else grades[q].get(d, "?")
                    print(f"#{q} {rows[q]['query']} | {ref(d)} | {TAG.sub('', text[d])[:80]} | {g}")


if __name__ == "__main__":
    main()
