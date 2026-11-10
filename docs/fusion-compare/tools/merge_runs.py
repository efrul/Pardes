"""Several runs of fuse_all.py side by side on one page (EXPANSIONS.md): each run's methods as columns of their own.

    python merge_runs.py grades.json OUT.json [--feedback] [--methods app,p12wl,lex] BASE_DIR SUFFIX:LABEL=DIR ...

BASE_DIR and every DIR are fuse_all.py outputs (orderings.json) over the same searches. The base run's methods keep their
keys (make_html.py needs its `app`); another run's become KEY_SUFFIX, named "<name> + LABEL". Only --methods are taken
(default app, p12wl, lex), and the base run's `dense`, the same in every run here. A line's grade is read again from
grades.json (feedback_searches.py's, or source_searches.py's grades_target.json), so grades given since the runs show; its
lexical and dense places are those of the first run that shows it. Then:

    python make_html.py OUT.json page.html "title"
"""

import json
import sys

args = [a for a in sys.argv[1:] if not a.startswith("--")]
methods = ["app", "p12wl", "lex"]
if "--methods" in sys.argv:
    methods = sys.argv[sys.argv.index("--methods") + 1].split(",")
    args.remove(sys.argv[sys.argv.index("--methods") + 1])
grades = json.load(open(args[0], encoding="utf-8"))
out_path, base_dir, others = args[1], args[2], args[3:]
runs = [("", "", base_dir)] + [(o.split(":", 1)[0], o.split(":", 1)[1].split("=", 1)[0], o.split("=", 1)[1]) for o in others]
datas = [(suffix, label, json.load(open(f"{d}/orderings.json", encoding="utf-8"))) for suffix, label, d in runs]

names = {k: (n, desc) for k, n, desc in datas[0][2]["methods"]}
merged_methods, order = [], []
for m in methods + ["dense"]:
    for suffix, label, _ in (datas if m != "dense" else datas[:1]):
        key = m if not suffix else f"{m}_{suffix}"
        name, desc = names[m]
        merged_methods.append([key, name if not suffix else f"{name} + {label}",
                               desc if not suffix else f"{desc}. הצד הרגיל עם {label}"])
        order.append((key, m, suffix))
by_run = {suffix: {x["id"]: x for x in data["searches"]} for suffix, _, data in datas}
lines, books = {}, {}
for _, _, data in datas:
    for k, v in data["lines"].items():
        lines.setdefault(k, v)
    for k, v in data["books"].items():
        books.setdefault(k, v)
searches = []
for base in datas[0][2]["searches"]:
    sid, q = base["id"], base["q"]
    tops, per_line = {}, {}
    for key, m, suffix in order:
        x = by_run[suffix][sid]
        tops[key] = x["tops"][m]
        for d in x["tops"][m]:
            if str(d) not in per_line:
                p = list(x["lines"][str(d)])
                g = grades.get(f"{q}\t{d}")
                p[2] = g["grade"] if g else -1
                p[4] = {"claude": "c", "target": "t"}.get(g.get("by"), "u") if g else "u"
                per_line[str(d)] = p
    searches.append({**base, "tops": tops, "lines": per_line, "graded": sum(1 for p in per_line.values() if p[2] >= 0)})
json.dump({"methods": merged_methods, "searches": searches, "lines": lines, "books": books,
           "defaultMethods": [k for k, _, _ in order], "storeKey": "fusion.methods." + out_path.rsplit("/", 1)[-1],
           "feedback": "--feedback" in sys.argv},
          open(out_path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(searches)} searches, {len(merged_methods)} columns")
