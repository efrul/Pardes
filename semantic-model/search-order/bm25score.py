"""The lexical side under other settings (harness/Bm25.kt), alone and fused as the app fuses it, scored on eval-real
and eval-100: issue 5 of docs/SEARCH_ORDER_ISSUES.md.

    python bm25score.py real.jsonl e100.jsonl bm25_real.jsonl bm25_e100.jsonl seforim.db

real.jsonl, e100.jsonl: Eval.kt's runs (their dense candidates and scopes); bm25_*.jsonl: Bm25.kt's on the same
queries, any number of settings each, the app's (1.2, 0.75, boost on) among them. A run with another scope brings its
own dense candidates. Then the lexical first three of the app's setting by line length and grade.
"""

import json
import re
import statistics
import sys
from collections import defaultdict

from order_common import Lines, app_fusion, ids, load_eval_real, load_expected

APP = (1.2, 0.75, True, False, False)


def main() -> None:
    evals = {"real": {}, "e100": {}}
    for name, path in (("real", sys.argv[1]), ("e100", sys.argv[2])):
        for r in map(json.loads, open(path, encoding="utf-8")):
            evals[name][r["id"]] = r
    runs = defaultdict(dict)
    for name, path in (("real", sys.argv[3]), ("e100", sys.argv[4])):
        for r in map(json.loads, open(path, encoding="utf-8")):
            key = (round(r["k1"], 1), round(r["b"], 2), r["boost"], r.get("near", False), r.get("keepScope", False))
            runs[key][(name, r["id"])] = r
    lines = Lines(sys.argv[5])
    lines.load(d for run in runs.values() for r in run.values() for k in ("lexical", "lexCand", "denseCand") for d in r.get(k, []))
    lines.load(d for ev in evals.values() for r in ev.values() for k in ("lexCand", "denseCand") for d in ids(r[k]))
    noise, dcg, grades = load_eval_real()
    expected = load_expected()

    def dense(name, q, r):
        return r["denseCand"] if "denseCand" in r else ids(evals[name][q]["denseCand"])

    def hybrid(name, q, r):
        return app_fusion(evals[name][q]["query"], r["lexCand"], dense(name, q, r), lines.text)

    def real_row(lists):
        first = three = source = missing = 0
        nd = []
        for q, top in lists.items():
            if q in noise:
                continue
            g = grades[q]
            top = top[:3]
            missing += sum(1 for d in top if d not in g)
            gg = [g.get(d, 0) for d in top]
            first += bool(gg) and gg[0] >= 2
            three += max(gg, default=0) >= 2
            source += max(gg, default=0) == 3
            ideal = dcg(sorted(g.values(), reverse=True)[:3])
            if ideal:
                nd.append(dcg(gg) / ideal)
        return f"{first} / {three} / {source} / {sum(nd) / len(nd):.3f} ({missing})"

    def e100_row(lists):
        res = {}
        for q, lst in lists.items():
            if q in expected:
                res[q] = next((i + 1 for i, d in enumerate(lst[:10]) if any(re.search(p, lines[d]["ref"]) for p in expected[q])), None)
        mrr = sum(1 / v for v in res.values() if v) / len(res)
        return f"{sum(1 for v in res.values() if v)} / {sum(1 for v in res.values() if v == 1)} / {mrr:.3f}"

    app = runs.get(APP)
    if app:
        same = sum(1 for (n, q), r in app.items() if r["lexCand"] == ids(evals[n][q]["lexCand"]))
        print(f"the app's setting gives Eval.kt's lexical candidates on {same}/{len(app)} queries (the others: ties, deep down)\n")
    print("| k1 | b | order boost | near required | scope kept | to all books | lexical alone: eval-real | eval-100 | "
          "fused: eval-real | eval-100 | fused first 10 as the app's |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for key in sorted(runs, key=lambda k: (k != APP, k[3], k[4], k[0], k[1], not k[2])):
        run = runs[key]
        k1, b, boost, near, kept = key
        lex = {n: {q: r["lexical"] for (nn, q), r in run.items() if nn == n} for n in evals}
        hyb = {n: {q: hybrid(n, q, r) for (nn, q), r in run.items() if nn == n} for n in evals}
        moved = sum(1 for (n, q), r in run.items() if r.get("scope", evals[n][q]["scope"]) == "all" and evals[n][q]["scope"] == "base")
        shared = ""
        if app:
            both = [len(set(hyb[n][q][:10]) & set(hybrid(n, q, app[(n, q)])[:10])) for (n, q) in run if (n, q) in app]
            shared = f"{statistics.mean(both):.1f} / 10"
        print(f"| {k1:.1f} | {b:.2f} | {'on' if boost else 'off'} | {'yes' if near else 'no'} | {'yes' if kept else 'no'} | {moved} | "
              f"{real_row(lex['real'])} | {e100_row(lex['e100'])} | {real_row(hyb['real'])} | {e100_row(hyb['e100'])} | {shared} |")

    # The app's lexical first three by line length and grade
    by_grade = defaultdict(list)
    long_bad = long_bad_alone = searched = 0
    for q, r in evals["real"].items():
        if q in noise or not r["lexCand"]:
            continue
        searched += 1
        g = grades[q]
        for d in ids(r["lexical"])[:3]:
            if d in g:
                by_grade["good (2-3)" if g[d] >= 2 else str(g[d])].append(lines[d]["words"])
        first = ids(r["lexical"])[0] if r["lexical"] else None
        if first and g.get(first, 0) == 0 and lines[first]["words"] > 300:
            long_bad += 1
            long_bad_alone += not any(g.get(d, 0) >= 2 for d in ids(r["lexCand"]))
    print("\nthe app's lexical first three, eval-real, by grade: words of the line")
    for grade, words in sorted(by_grade.items()):
        words.sort()
        print(f"  {grade}: {len(words)} lines, median {statistics.median(words):.0f}, p90 {words[int(len(words) * 0.9)]}")
    lex_only = slots = 0
    for q, r in evals["real"].items():
        lex, den = set(ids(r["lexCand"])), set(ids(r["denseCand"]))
        top = app_fusion(r["query"], ids(r["lexCand"]), ids(r["denseCand"]), lines.text)[:10]
        slots += len(top)
        lex_only += sum(1 for d in top if d in lex and d not in den)
    print(f"the app's fused first 10, eval-real: {lex_only} of {slots} lines found by the lexical side alone")
    print(f"searches whose lexical first is a line of more than 300 words graded 0: {long_bad} of {searched}; "
          f"with no good line (2-3) among its 150 candidates: {long_bad_alone}")


if __name__ == "__main__":
    main()
