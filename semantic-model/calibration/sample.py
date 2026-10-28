"""The calibration sample: 100 (query, line) pairs from the judged pools of eval-real and eval-v1 batch 2, 50 from each,
stratified by Claude's grade (about 25 of each grade 0-3 in all), one pair per query, for the owner to judge blind.
python sample.py <semantic-model dir> seforim.db out.json key.tsv
The sample was drawn from the grades.tsv files of bd428a5 (lines judged later change it); out.json holds the text the
page showed (kept out of the repository), key.tsv Claude's grades."""
import csv, html, json, random, re, sqlite3, sys
from collections import defaultdict
root, dbpath, out_json, out_key = sys.argv[1:5]
rnd = random.Random(5788)
NOISE = {75, 76}

def load(qpath, gpath, qid_of):
    rows = csv.DictReader(open(qpath, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE)
    queries = {qid_of(r["id"]): r for r in rows}
    grades = list(csv.DictReader(open(gpath, encoding="utf-8"), delimiter="\t", quoting=csv.QUOTE_NONE))
    return queries, grades

sets = {
    "real": load(f"{root}/eval-real/queries.tsv", f"{root}/eval-real/grades.tsv", int),
    "v1": load(f"{root}/eval-v1/queries.tsv", f"{root}/eval-v1/grades.tsv", lambda s: int(s[3:])),
}
quota = {"real": {0: 13, 1: 12, 2: 13, 3: 12}, "v1": {0: 12, 1: 13, 2: 12, 3: 13}}
picked = []
for name, (queries, grades) in sets.items():
    by_grade = defaultdict(list)
    for r in grades:
        qid = int(r["qid"])
        if name == "real" and qid in NOISE:
            continue
        by_grade[int(r["grade"])].append(r)
    used = set()
    # the rarest grades first, so each query's one pair goes where it is scarcest
    for g in sorted(quota[name], key=lambda g: len(by_grade[g])):
        pool = by_grade[g][:]
        rnd.shuffle(pool)
        n = 0
        for r in pool:
            if n == quota[name][g]:
                break
            if int(r["qid"]) in used:
                continue
            used.add(int(r["qid"]))
            picked.append((name, r, queries[int(r["qid"])]))
            n += 1
        assert n == quota[name][g], (name, g, n)
rnd.shuffle(picked)

con = sqlite3.connect(f"file:{dbpath}?mode=ro", uri=True)
TEAMIM = re.compile("[֑-ֽ֯׀׃׆]")

def clean(t):
    t = re.sub(r"<br\s*/?>", " ", t or "", flags=re.I)
    t = re.sub(r"<[^>]+>", "", t)
    t = html.unescape(t).replace(" ", " ").replace(" ", " ")
    return " ".join(TEAMIM.sub("", t).split())

items, key = [], []
for i, (name, r, q) in enumerate(picked, 1):
    content, heref, title = con.execute(
        "SELECT l.content, l.heRef, b.title FROM line l JOIN book b ON b.id = l.bookId WHERE l.id = ?",
        (int(r["lineId"]),)).fetchone()
    text = clean(content)
    cut = len(text) > 3000
    cid = f"c{i:03d}"
    items.append({"id": cid, "query": q["query"], "hint": (q.get("hint_book") or "") if name == "real" else "",
                  "own": name == "real", "ref": " ".join((heref or title).split()), "text": text[:3000], "cut": cut})
    key.append((cid, name, r["qid"], r["lineId"], r["grade"], " ".join((heref or title).split()), q["query"]))
json.dump(items, open(out_json, "w", encoding="utf-8"), ensure_ascii=False)
with open(out_key, "w", encoding="utf-8") as f:
    f.write("id\tset\tqid\tlineId\tclaude\tref\tquery\n")
    for k in key:
        f.write("\t".join(map(str, k)) + "\n")
print(len(items), "items;", sum(len(x["text"]) for x in items), "chars of text;", sum(x["cut"] for x in items), "cut")
