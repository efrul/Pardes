"""The "common source" rule, offline, on the app's page: RRF w3 of the first 150 of each side, then keepLeaders with
holdsQuote (fusions.py's near2). When the lines of the first page link (the library's `link` table, both directions)
to one line that is not on it, from at least k distinct books, that line comes up to place p. One line at most: the
one linked from the most books (ties: a base book first, then the higher fused rank). It must be in the search's scope
(a base book when the search ran in the base books).

    python common_source.py results.jsonl seforim.db (--grades grades.tsv | --targets) [--details out.tsv]

Variants: the first page of N = 10 or 20 lines; k = 3 or 5 books; all connection types, or TARGUM and RELATED left out;
(a) re-rank only, the line already among the fused candidates, or (b) any linked line; place p = 1 or 3 (place 2 is
the quote rule's). --grades: eval-real or eval-v1 graded pools (a line never judged counts 0; they are listed at the
end, to judge before trusting a row). --targets: eval-100, its sources by expected.py's heRef patterns.

A second table tries, on N=20, k=3, all types, any linked line, place 3, ways to keep the rule from its errors: a line
already on the page below place 3 may come up; hubs (lines with many links in the library) left out; links asked of
the first five lines or of a quarter of the page; a verse only when the first three hold one; a floor on the dense
side's score for the line. A third shows the common sources apart, in a line of up to three above the list.
"""

import json
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from fusions import keep_leaders, near, rrf  # noqa: E402
from score import NOISE, dcg, load_grades  # noqa: E402

TARGUM, RELATED = 3, 13
VERSES = {2, 3, 4}  # the categories of the Torah, the Prophets and the Writings themselves


def arg(name):
    return sys.argv[sys.argv.index(name) + 1] if name in sys.argv else None


def app_page(r, text):
    """The app's order since #3: RRF w3, then the first two lexical hits holding the query as a quote at places 2, 4."""
    fused = rrf(r["lexCand"], r["denseCand"]) if r["denseCand"] else list(r["lexCand"])
    kept = [d if near(r["query"], text.get(d) or "", 2) else None for d in r["lexCand"][:2]]
    return keep_leaders(fused, kept)


def common_source(*args, **kwargs):
    """The line the first [n] of [fused] link to from the most books (at least [k]) and its book count, or None."""
    found = common_sources(*args, **kwargs)
    return found[0] if found else None


def common_sources(fused, links, book_of, base, scope, n, k, types, rerank, shown=None, degree=None, max_links=None,
                   support=0, min_lines=0, allow=None, q=None):
    """The lines the first [n] of [fused] link to from at least [k] books, with their book counts, the most linked
    first (ties: a base book first, then the higher fused rank). None is one of the [shown] first lines (the whole page
    by default); [max_links] leaves out a line with more links in the library (a hub); [support] asks that many of the
    first five lines to link to it, [min_lines] that many of the page's lines; [allow] (query id, fused, line) may
    refuse a line."""
    page = fused[:n]
    skip = set(fused[:shown] if shown is not None else page)
    rank = {d: i for i, d in enumerate(fused)}
    books, top, linking = defaultdict(set), defaultdict(int), defaultdict(set)
    for i, d in enumerate(page):
        for other, ctype in links.get(d, ()):
            if other in skip or other == d or (types and ctype in types):
                continue
            books[other].add(book_of[d])
            linking[other].add(d)
            if i < 5:
                top[other] += 1
    found = []
    for line, bs in books.items():
        if len(bs) < k or (rerank and line not in rank) or top[line] < support or len(linking[line]) < min_lines:
            continue
        if allow and not allow(q, fused, line):
            continue
        if scope == "base" and not base.get(line, False):
            continue
        if max_links is not None and degree(line) > max_links:
            continue
        found.append(((-len(bs), not base.get(line, False), rank.get(line, len(fused))), line, len(bs)))
    return [(line, nb) for _, line, nb in sorted(found)]


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

    def fetch(sql, ids):
        ids = list(ids)
        for i in range(0, len(ids), 900):
            chunk = ids[i:i + 900]
            yield from con.execute(sql.format(",".join("?" * len(chunk))), chunk)

    leads = {d for r in rows.values() for d in r["lexCand"][:2]}
    text = dict(fetch("SELECT id, content FROM line WHERE id IN ({})", leads))
    pages = {q: app_page(r, text) for q, r in rows.items()}
    first = {d for f in pages.values() for d in f[:20]}
    links = defaultdict(list)
    sql = "SELECT sourceLineId, targetLineId, connectionTypeId FROM link WHERE {} IN ({{}})"
    for s, t, c in fetch(sql.format("sourceLineId"), first):
        links[s].append((t, c))
    for s, t, c in fetch(sql.format("targetLineId"), first):
        links[t].append((s, c))
    ends = first | {o for v in links.values() for o, _ in v}
    book_of, base, ref = {}, {}, {}
    lines = "SELECT l.id, {} FROM line l JOIN book b ON b.id = l.bookId WHERE l.id IN ({{}})"
    category = {}
    columns = "l.bookId, b.isBaseBook, l.heRef, b.title, b.categoryId"
    for i, b, is_base, h, title, c in fetch(lines.format(columns), ends):
        book_of[i], base[i], ref[i], category[i] = b, bool(is_base), " ".join((h or title).split()), c
    for f in pages.values():
        for d in f[:10]:
            ref.setdefault(d, None)
    missing = [d for d in ref if ref[d] is None]
    for i, h, title in fetch(lines.format("l.heRef, b.title"), missing):
        ref[i] = " ".join((h or title).split())

    def measure(lists):
        if targets:
            ranks = []
            for q, f in lists.items():
                hits = (i + 1 for i, d in enumerate(f[:10]) if any(re.search(p, ref.get(d) or "") for p in E[q]))
                ranks.append(next(hits, None))
            hit, top = sum(1 for x in ranks if x), sum(1 for x in ranks if x == 1)
            return f"{hit}/{len(ranks)} | {top} | {sum(1 / x for x in ranks if x) / len(ranks):.3f}"
        first_good = three = source = 0
        ndcg = []
        for q, f in lists.items():
            g = [grades[q].get(d, 0) for d in f[:3]]
            first_good += bool(g) and g[0] >= 2
            three += max(g, default=0) >= 2
            source += max(g, default=0) == 3
            ideal = dcg(sorted(grades[q].values(), reverse=True)[:3])
            if ideal:
                ndcg.append(dcg(g) / ideal)
        return f"{first_good} | {three} | {source} | {sum(ndcg) / len(ndcg):.3f}"

    same = sum(1 for q, r in rows.items() if [h["lineId"] for h in r["hybrid"]] == pages[q][:len(r["hybrid"])])
    print(f"{len(rows)} queries; the harness's hybrid page == RRF w3 + holdsQuote offline on {same} "
          "(it ran main before #3)\n")
    head = "hit@10 | first | MRR@10" if targets else "first good | good in 3 | source in 3 | nDCG@3"
    print(f"| N | k | types | lines | place | fires | {head} | not judged |")
    print("|---|---|---|---|---|---|---|---|")
    print(f"| the app's page | | | | | 0 | {measure(pages)} | 0 |")
    details, unjudged = [], defaultdict(set)
    for n in (10, 20):
        for k in (3, 5):
            for tname, types in (("all", set()), ("no TARGUM, RELATED", {TARGUM, RELATED})):
                for mode, rerank in (("(a) fused", True), ("(b) any", False)):
                    found = {q: common_source(f, links, book_of, base, rows[q]["scope"], n, k, types, rerank)
                             for q, f in pages.items()}
                    for p in (1, 3):
                        lists, fires, new = {}, 0, 0
                        for q, f in pages.items():
                            if not found[q]:
                                lists[q] = f
                                continue
                            line = found[q][0]
                            out = [d for d in f if d != line]
                            out.insert(p - 1, line)
                            lists[q] = out
                            fires += 1
                            if not targets and line not in grades[q]:
                                new += 1
                                unjudged[q].add(line)
                        print(f"| {n} | {k} | {tname} | {mode} | {p} | {fires} | {measure(lists)} | {new} |")
                    for q, hit in found.items():
                        if hit:
                            line, nb = hit
                            g = "" if targets else grades[q].get(line, "?")
                            details.append((n, k, tname, mode, q, rows[q]["query"], line, nb, ref.get(line), g,
                                            pages[q].index(line) + 1 if line in pages[q] else ""))
    # Ways to keep the rule from its errors, on N=20, k=3, all types, any linked line, place 3: a line already on the
    # page but below place 3 may come up; a hub (a line with more links in the library) is left out; the first five
    # lines must carry some of the links
    degrees = {}

    def degree(line):
        if line not in degrees:
            degrees[line] = sum(con.execute(f"SELECT COUNT(*) FROM link WHERE {c} = ?", (line,)).fetchone()[0]
                                for c in ("sourceLineId", "targetLineId"))
        return degrees[line]

    dense_score = {q: dict(zip(r["denseCand"], r.get("denseScores", []))) for q, r in rows.items()}

    def closer(q, line, third, margin):
        """Whether the dense side scores [line] at least as [third] less [margin] (a line it never scored, no)."""
        scores = dense_score[q]
        if line not in scores:
            return False
        return third not in scores or scores[line] >= scores[third] - margin

    refinements = [
        ("as above", {}),
        ("a line on the page below place 3 too", {"shown": 3}),
        ("hubs of more than 1000 links left out", {"max_links": 1000}),
        ("hubs of more than 500 links left out", {"max_links": 500}),
        ("hubs of more than 200 links left out", {"max_links": 200}),
        ("2 of the first 5 lines link to it", {"support": 2}),
        ("on the page too, hubs of more than 500 out", {"shown": 3, "max_links": 500}),
        ("on the page too, 2 of the first 5", {"shown": 3, "support": 2}),
        ("on the page too, hubs of more than 500 out, 2 of the first 5", {"shown": 3, "max_links": 500, "support": 2}),
        ("on the page too, a quarter of the page's lines link to it", {"shown": 3, "min_lines": 5}),
        ("on the page too, never a verse", {"shown": 3, "allow": lambda q, f, line: category.get(line) not in VERSES}),
        ("on the page too, a verse only when one is in the first 3",
         {"shown": 3, "allow": lambda q, f, line: category.get(line) not in VERSES
          or any(category.get(d) in VERSES for d in f[:3])}),
        ("on the page too, among the fused candidates only", {"shown": 3, "rerank": True}),
        ("on the page too, the model scores it at least as the line at place 3",
         {"shown": 3, "allow": lambda q, f, line: closer(q, line, f[2], 0.0)}),
        ("on the page too, the model scores it within 0.02 of the line at place 3",
         {"shown": 3, "allow": lambda q, f, line: closer(q, line, f[2], 0.02)}),
    ]
    print("\nN=20, k=3, all types, any linked line, place 3, and ways to keep it from its errors:\n")
    print(f"| variant | fires | {head} | not judged |")
    print("|---|---|---|---|")
    for name, opts in refinements:
        lists, fires, new = {}, 0, 0
        for q, f in pages.items():
            opts = dict(opts)
            rerank = opts.pop("rerank", False)
            hit = common_source(f, links, book_of, base, rows[q]["scope"], 20, 3, set(), rerank, degree=degree, q=q,
                                **opts)
            if not hit:
                lists[q] = f
                continue
            line = hit[0]
            out = [d for d in f if d != line]
            out.insert(2, line)
            lists[q] = out
            fires += 1
            if not targets and line not in grades[q]:
                new += 1
                unjudged[q].add(line)
            details.append(("refined", 3, name, "(b) any", q, rows[q]["query"], line, hit[1], ref.get(line),
                            "" if targets else grades[q].get(line, "?"), f.index(line) + 1 if line in f else ""))
        print(f"| {name} | {fires} | {measure(lists)} | {new} |")
    # The sources shown apart from the list, in a line above it: up to M lines the page links to from 3 books or more,
    # none of them already in the first three
    print("\nThe common sources in a line of their own above the list (N=20, k=3, all types, any linked line):\n")
    if targets:
        print("| sources shown | queries with a line | lines shown | the source among them | the source in the "
              "first 3 or in the line |")
        print("|---|---|---|---|---|")
    else:
        print("| sources shown | queries with a line | lines shown | good (2+) among them | queries with a good one | "
              "a good one in the line only | a good one in the first 3 or in the line | not judged |")
        print("|---|---|---|---|---|---|---|---|")
    for m, ratio in ((1, 0), (2, 0), (3, 0), (3, 0.5), (3, 0.75)):
        with_line = shown_lines = good = with_good = rescued = covered = new = 0
        for q, f in pages.items():
            found = common_sources(f, links, book_of, base, rows[q]["scope"], 20, 3, set(), False, shown=3)[:m]
            line = [d for d, nb in found if nb >= ratio * found[0][1]]
            with_line += bool(line)
            shown_lines += len(line)
            if targets:
                def hit(d):
                    return any(re.search(p, ref.get(d) or "") for p in E[q])
                good += sum(1 for d in line if hit(d))
                covered += any(hit(d) for d in f[:3] + line)
                continue
            gs = [grades[q].get(d, 0) for d in line]
            for d in line:
                if d not in grades[q]:
                    new += 1
                    unjudged[q].add(d)
            good += sum(1 for x in gs if x >= 2)
            with_good += any(x >= 2 for x in gs)
            top3 = max((grades[q].get(d, 0) for d in f[:3]), default=0) >= 2
            rescued += not top3 and any(x >= 2 for x in gs)
            covered += top3 or any(x >= 2 for x in gs)
        name = f"{m}, each linked from at least {ratio:.0%} of the first's books" if ratio else str(m)
        if targets:
            print(f"| {name} | {with_line} | {shown_lines} | {good} | {covered}/{len(pages)} |")
        else:
            print(f"| {name} | {with_line} | {shown_lines} | {good} | {with_good} | {rescued} | "
                  f"{covered}/{len(pages)} | {new} |")
    left = {q: sorted(v) for q, v in sorted(unjudged.items())}
    print(f"\nlines brought up and not judged: {sum(len(v) for v in left.values())}", left or "")
    if arg("--details"):
        with open(arg("--details"), "w", encoding="utf-8") as f:
            f.write("N\tk\ttypes\tlines\tqid\tquery\tline\tbooks\tref\tgrade\tfused rank\n")
            for d in details:
                f.write("\t".join(map(str, d)) + "\n")


if __name__ == "__main__":
    main()
