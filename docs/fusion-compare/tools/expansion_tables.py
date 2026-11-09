"""The extra-forms tables of EXPANSIONS.md, from Klei Kodesh's dictionary data as Pardes ships it.

    python expansion_tables.py OUT_DIR [dictionary.tsv.gz]

dictionary.tsv.gz is SeforimApp/src/jvmMain/resources/kleikodesh/dictionary.tsv.gz (scripts/kleikodesh_dictionary.py).
Writes OUT_DIR/aramaic.tsv and OUT_DIR/synonyms.tsv: a word, then its forms, tab separated, in the text index's letters
(no niqqud, final letters as their base form), for ExtraForms.kt (-DextraForms):
- aramaic: Torat Emet's Aramaic-Hebrew dictionary (source_kind 1), both ways: an Aramaic headword gets the one-word
  Hebrew senses of its gloss ("בראש, בתחילה" gives בראש and בתחילה), and a Hebrew word the headwords it glosses;
- synonyms: Klei Kodesh's synonym links (kind 1), both ways.
Words and forms of fewer than 3 letters are left out (function words would match everything). ExtraForms.kt then keeps
the forms some line of the library holds, the most common first.
"""

import collections
import gzip
import re
import sys
from pathlib import Path

out = Path(sys.argv[1])
src = sys.argv[2] if len(sys.argv) > 2 else str(
    Path(__file__).resolve().parents[3] / "SeforimApp/src/jvmMain/resources/kleikodesh/dictionary.tsv.gz")
rows = [r.split("\t") for r in gzip.open(src, "rt", encoding="utf-8").read().splitlines()]
FINALS = str.maketrans("ךםןףץ", "כמנפצ")


def norm(s):
    s = re.sub(r"[֑-ׇ]", lambda m: " " if m.group() == "־" else "", s)  # niqqud, teamim; maqaf a space
    s = s.replace("״", "").replace("׳", "").replace('"', "").replace("'", "")
    return " ".join(re.sub(r"[^א-ת ]", " ", s).translate(FINALS).split())


def senses(text):
    found = []
    for part in re.split(r"[,;/|]| או ", re.sub(r"\([^)]*\)", " ", text)):
        w = norm(part)
        if w and " " not in w and len(w) >= 3:
            found.append(w)
    return found


def add(table, a, b):
    if b not in table[a]:
        table[a].append(b)


aramaic, synonyms = collections.defaultdict(list), collections.defaultdict(list)
for r in rows:
    if r[0] == "S" and r[1] == "ARAMAIC":
        head = norm(r[2])
        if head and " " not in head and len(head) >= 3:
            for s in senses(r[3]):
                if s != head:
                    add(aramaic, head, s)
                    add(aramaic, s, head)
    elif r[0] == "L" and r[1] == "1":
        a, b = norm(r[2]), norm(r[3])
        if a and b and a != b and " " not in a + b and len(a) >= 3 and len(b) >= 3:
            add(synonyms, a, b)
            add(synonyms, b, a)
out.mkdir(parents=True, exist_ok=True)
for name, table in (("aramaic", aramaic), ("synonyms", synonyms)):
    with open(out / f"{name}.tsv", "w", encoding="utf-8") as f:
        for k in sorted(table):
            f.write(k + "\t" + "\t".join(table[k]) + "\n")
    print(f"{name}: {len(table)} words, {sum(len(v) for v in table.values())} forms")
