"""A parity fixture for the app's port of the common-sources line (docs/COMMON_SOURCES_PLAN.md), ids only, no text.

For each eval-100 question with a source set in expected.py: the app's page (the first 20 fused lines, with their
books), the fused rank of the lines below it, the search's scope, each page line's links (the other line and its book,
both directions, without repeats), and what the line shows ([lineId, books] for each source). The line is given twice:
ranked as the measurement ranks (common_source.common_sources), by the place in the whole fused list (app_page), and
ranked as the app can, by the place in the first 20 only. Plus the ids of the base books. Before writing, the rule here is checked against common_source.common_sources
(N=20, k=3, all types, any linked line, the first 3 skipped, up to 3 sources, each from at least half the first one's
books) on every question.

    python3 -I -B common_source_fixture.py eval-100-results.jsonl seforim.db \
        ../../SeforimApp/src/jvmTest/resources/search/common-sources-e100.json.gz

eval-100-results.jsonl is the output of eval-100/harness/Eval.kt (it holds the books' text: it stays out of the repo).
The one in the repo was written from the run on the whole library with the student (the eval-100 column of
results-common-source.md) and seforim.db 20261012193827; on it the line shows on 47 questions, 86 sources.
"""

import gzip
import json
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "eval-100"))
sys.path.insert(0, str(HERE))
from common_source import app_page, common_sources  # noqa: E402
from expected import E  # noqa: E402

with open(sys.argv[1], encoding="utf-8") as results:
    rows = {r["id"]: r for r in map(json.loads, results) if r["id"] in E}
con = sqlite3.connect(f"file:{sys.argv[2]}?mode=ro", uri=True)
base_ids = sorted(i for (i,) in con.execute("SELECT id FROM book WHERE isBaseBook = 1"))
base_set = set(base_ids)


def fetch(sql, ids):
    ids = list(ids)
    for i in range(0, len(ids), 900):
        chunk = ids[i:i + 900]
        yield from con.execute(sql.format(",".join("?" * len(chunk))), chunk)


leads = {d for r in rows.values() for d in r["lexCand"][:2]}
text = dict(fetch("SELECT id, content FROM line WHERE id IN ({})", leads))
pages = {q: app_page(r, text) for q, r in rows.items()}
first = {d for f in pages.values() for d in f[:20]}
ends = defaultdict(list)  # page line -> [(other line, its book)]
links = defaultdict(list)  # page line -> [(other line, connection type)], as common_source.py reads them
sql = "SELECT sourceLineId, targetLineId, sourceBookId, targetBookId, connectionTypeId FROM link WHERE {} IN ({{}})"
for s, t, sb, tb, c in fetch(sql.format("sourceLineId"), first):
    ends[s].append((t, tb))
    links[s].append((t, c))
for s, t, sb, tb, c in fetch(sql.format("targetLineId"), first):
    ends[t].append((s, sb))
    links[t].append((s, c))
book_of = dict(fetch("SELECT id, bookId FROM line WHERE id IN ({})", first | {o for v in ends.values() for o, _ in v}))
base = {i: b in base_set for i, b in book_of.items()}


def rule(f, scope, ranked):
    """The line as the app computes it: the candidate's book from the link row, the cut to half in integers."""
    page, skip = f[:20], set(f[:3])
    rank = {d: i for i, d in enumerate(ranked)}
    books = defaultdict(set)
    for d in page:
        for other, other_book in ends.get(d, ()):
            if other in skip or other == d:
                continue
            books[(other, other_book)].add(book_of[d])
    found = sorted(((-len(bs), other_book not in base_set, rank.get(other, len(ranked))), other, len(bs))
                   for (other, other_book), bs in books.items()
                   if len(bs) >= 3 and (scope != "base" or other_book in base_set))
    top = [(line, nb) for _, line, nb in found][:3]
    return [[d, nb] for d, nb in top if 2 * nb >= top[0][1]]


out, differ = [], []
for q, f in sorted(pages.items()):
    scope = rows[q]["scope"]
    whole = rule(f, scope, f)
    measured = common_sources(f, links, book_of, base, scope, 20, 3, set(), False, shown=3)[:3]
    measured = [[d, nb] for d, nb in measured if nb >= 0.5 * measured[0][1]]
    if whole != measured:
        differ.append(q)
    out.append({
        "qid": q,
        "scope": scope,
        "page": [[d, book_of[d]] for d in f[:20]],
        "fusedRank": {str(d): i for i, d in enumerate(f) if d not in f[:20]},
        "links": {str(d): sorted(set(map(tuple, ends.get(d, ())))) for d in f[:20]},
        "expectedWholeRank": whole,
        "expectedFirst20Rank": rule(f, scope, f[:20]),
    })
if differ:
    sys.exit(f"the rule here differs from common_sources on {differ}")
info = Path(sys.argv[2]).parent / "release_info.txt"
release = info.read_text().strip() if info.exists() else "(release unknown)"
with gzip.open(sys.argv[3], "wt", encoding="utf-8") as w:
    json.dump({"db": f"seforim.db {release}", "baseBookIds": base_ids, "queries": out}, w, separators=(",", ":"))
shown = [x for x in out if x["expectedWholeRank"]]
print(f"{len(out)} questions, the same as common_sources on all; a line on {len(shown)}, "
      f"{sum(len(x['expectedWholeRank']) for x in shown)} sources; written {sys.argv[3]}")
