"""Other fusions of the app's candidates, offline, scored on eval-real (grades.tsv, as eval-real/score.py) and eval-100
(expected.py): the rows of docs/SEARCH_ORDER_ISSUES.md, issues 1, 2, 7 and 9.

    python fusions.py real.jsonl e100.jsonl seforim.db [--diff NAME] [--trace history.jsonl LINE_IDS]

real.jsonl and e100.jsonl: eval-100/harness/Eval.kt on eval-real's and eval-100's queries (the first 150 of each side,
lexCand and denseCand). --diff lists the eval-real searches whose first three change from the app's to fusion NAME.
--trace prints, for each search of a History.kt (or Eval.kt) jsonl, the places of the given lines under each fusion.

eval-real: first good / good in the first 3 / the source in the first 3 / nDCG@3 (lines not judged in a first 3,
counted 0). eval-100: hit@10 / first / MRR@10, and its ten exact quotes (76-85): in the first 10 / first.
"""

import json
import re
import sys

from order_common import (LEADER_PLACES, Lines, holds_quote, holds_verbatim, ids, keep, load_eval_real, load_expected,
                          rrf, verbatim_key)

SHORT_WORDS = 8


def fuse(r, lines, short=False, short_except_base=False, headings=False, dedupe=False, quote=None, quote_min_words=2,
         quote_rare=False, quote_weight=3.0, lead_agree=None, lead_quotes=None):
    """The app's fusion with one change or more:
    - short: dense lines of SHORT_WORDS words or fewer that the lexical side doesn't have taken out (the others move up);
      short_except_base: base books' kept;
    - headings: dense lines that are headings (<h1>...) taken out;
    - dedupe: a text already higher in the list dropped;
    - quote ("verbatim" or "near"): a lexical line that holds the query word for word (or as holdsQuote does) weighs
      quote_weight, for a query of quote_min_words words or more; quote_rare: only when the lexical side has fewer than 150;
    - lead_agree N: the first two lexical lines also kept at places 2 and 4 when the dense side has them in its first N;
    - lead_quotes (n, k, places): instead of the app's two leaders, the first n quotes among the lexical first k.
    """
    lex, dense, query = ids(r["lexCand"]), ids(r["denseCand"]), r["query"]
    in_lex = set(lex)
    if short:
        dense = [d for d in dense if lines[d]["words"] > SHORT_WORDS or d in in_lex or (short_except_base and lines[d]["base"])]
    if headings:
        dense = [d for d in dense if not lines[d]["heading"]]
    words = len(verbatim_key(query).split())
    on = quote and words >= quote_min_words and (not quote_rare or len(lex) < 150)
    rule = holds_verbatim if quote == "verbatim" else holds_quote
    fused = rrf(lex, dense, lambda d: quote_weight if on and rule(lines.text(d), query) else 1.0) if dense else list(lex)
    if dedupe:
        seen, out = set(), []
        for d in fused:
            if lines[d]["key"] not in seen:
                seen.add(lines[d]["key"])
                out.append(d)
        fused = out
    if lead_quotes:
        n, k, places = lead_quotes
        quotes = [d for d in lex[:k] if holds_quote(lines.text(d), query)][:n]
        return keep(fused, list(zip(quotes, places)))
    dense_rank = {d: i for i, d in enumerate(ids(r["denseCand"]))}

    def leads(d):
        return holds_quote(lines.text(d), query) or (lead_agree is not None and dense_rank.get(d, 999) < lead_agree)

    return keep(fused, [(d, LEADER_PLACES[j]) for j, d in enumerate(lex[:2]) if leads(d)])


FUSIONS = {
    "the app": {},
    "1: short dense lines out": dict(short=True),
    "1: short dense lines out, base books' kept": dict(short=True, short_except_base=True),
    "1: dense headings out": dict(headings=True),
    "2: word for word (3+ words) weighs 3": dict(quote="verbatim", quote_min_words=3),
    "2: word for word, fewer than 150 lexical hits, weighs 3": dict(quote="verbatim", quote_rare=True),
    "2: a quote (holdsQuote) weighs 3": dict(quote="near"),
    "2: up to 5 quotes of the lexical first 10 at places 2, 4, 6, 8, 10": dict(lead_quotes=(5, 10, (1, 3, 5, 7, 9))),
    "1 + 2 (word for word, 3+ words)": dict(short=True, quote="verbatim", quote_min_words=3),
    "7: leaders also when in the dense first 150": dict(lead_agree=150),
    "7: leaders also when in the dense first 50": dict(lead_agree=50),
    "9: each text once": dict(dedupe=True),
}


def main() -> None:
    real = [json.loads(line) for line in open(sys.argv[1], encoding="utf-8")]
    e100 = [json.loads(line) for line in open(sys.argv[2], encoding="utf-8")]
    lines = Lines(sys.argv[3]).load(d for r in real + e100 for k in ("lexCand", "denseCand") for d in ids(r[k]))
    noise, dcg, grades = load_eval_real()
    expected = load_expected()

    def real_row(kw):
        first = three = source = missing = 0
        nd = []
        for r in real:
            q = r["id"]
            if q in noise:
                continue
            g = grades[q]
            top = fuse(r, lines, **kw)[:3]
            missing += sum(1 for d in top if d not in g)
            gg = [g.get(d, 0) for d in top]
            first += bool(gg) and gg[0] >= 2
            three += max(gg, default=0) >= 2
            source += max(gg, default=0) == 3
            ideal = dcg(sorted(g.values(), reverse=True)[:3])
            if ideal:
                nd.append(dcg(gg) / ideal)
        return f"{first} / {three} / {source} / {sum(nd) / len(nd):.3f} ({missing})"

    def rank(lst, pats):
        for i, d in enumerate(lst[:10]):
            if any(re.search(p, lines[d]["ref"]) for p in pats):
                return i + 1

    def e100_row(kw):
        res = {r["id"]: rank(fuse(r, lines, **kw), expected[r["id"]]) for r in e100 if r["id"] in expected}
        quotes = [res[i] for i in range(76, 86) if i in res]
        mrr = sum(1 / v for v in res.values() if v) / len(res)
        return (f"{sum(1 for v in res.values() if v)} / {sum(1 for v in res.values() if v == 1)} / {mrr:.3f} | "
                f"{sum(1 for v in quotes if v)} / {sum(1 for v in quotes if v == 1)}")

    if "--trace" in sys.argv:
        at = sys.argv.index("--trace")
        rows = [json.loads(line) for line in open(sys.argv[at + 1], encoding="utf-8")]
        targets = [int(x) for x in sys.argv[at + 2].split(",")]
        lines.load(d for r in rows for k in ("lexCand", "denseCand") for d in ids(r[k]))
        lines.load(targets)
        for r in rows:
            lex, dense = ids(r["lexCand"]), ids(r["denseCand"])
            print(f"\n[{r.get('scope')}] {r['query']}")
            for t in targets:
                where = lambda lst: (lst.index(t) + 1) if t in lst else "-"  # noqa: E731
                cells = ", ".join(f"{name} {where(fuse(r, lines, **kw))}" for name, kw in FUSIONS.items() if name[0] in "t12")
                print(f"  {lines[t]['ref']}: lexical {where(lex)}, dense {where(dense)}; {cells}")
        return

    same = sum(1 for r in real if ids(r["hybrid"]) == fuse(r, lines)[:len(r["hybrid"])])
    same100 = sum(1 for r in e100 if ids(r["hybrid"]) == fuse(r, lines)[:len(r["hybrid"])])
    print(f"the app's page == the fusion offline: eval-real {same}/{len(real)}, eval-100 {same100}/{len(e100)}"
          " (eval-100's other four are quoted: the app searches them by their words alone)\n")
    print("| fusion | eval-real | eval-100 | quotes |")
    print("|---|---|---|---|")
    for name, kw in FUSIONS.items():
        print(f"| {name} | {real_row(kw)} | {e100_row(kw)} |")

    if "--diff" in sys.argv:
        kw = FUSIONS[sys.argv[sys.argv.index("--diff") + 1]]
        better, worse = [], []
        for r in real:
            q = r["id"]
            if q in noise:
                continue
            g = grades[q]
            ideal = dcg(sorted(g.values(), reverse=True)[:3]) or 1
            a = [g.get(d, 0) for d in fuse(r, lines)[:3]]
            b = [g.get(d, 0) for d in fuse(r, lines, **kw)[:3]]
            delta = (dcg(b) - dcg(a)) / ideal
            if delta > 0:
                better.append((q, r["query"], a, b, round(delta, 3)))
            elif delta < 0:
                worse.append((q, r["query"], a, b, round(delta, 3)))
        print(f"\n{len(better)} better, {len(worse)} worse (grades of the first 3, the app -> the fusion, nDCG@3 change)")
        for x in sorted(worse, key=lambda x: x[4]) + sorted(better, key=lambda x: -x[4]):
            print("  ", x)


if __name__ == "__main__":
    main()
