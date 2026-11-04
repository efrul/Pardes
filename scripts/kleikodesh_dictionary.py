"""Packs Klei Kodesh's dictionary data (Based on Klei Kodesh - https://kleikodesh.github.io/) for the Home's dictionary
card: SeforimApp/src/jvmMain/resources/kleikodesh/dictionary.tsv.gz, which Android copies into its assets.

    python3 -I scripts/kleikodesh_dictionary.py <clone of KleiKodesh/KitveiHakodeshDictionary>

Read as data, none of the clone's scripts run. What it takes (its owners allowed Pardes to use their material):
- their headword index of books our library has (source/seforim_headword.tsv, titles in seforim_headword_book.tsv):
  Metzudat Tzion, Malbim's Biur HaMilot, the Mikropedia Talmudit and Machberet Menachem. Not the Aruch, which the card
  parses itself. The index was built on an Otzaria library of an unrecorded version: the app checks each entry against
  its own library's line and drops the ones that don't match (KleiKodeshDictionary.kt).
- their links between words of kind 1 (נרדף) and 2 (כתיב), checked by a model, not by a person; the ones to a word
  they no longer have are left out.
- their Aramaic-Hebrew dictionary (source_kind 1, 7,605 senses; its origin is not recorded, and their permission covers
  it: the user's word, 2026-11-04).
- the acronyms of Hebrew Wikipedia (source_kind 2, CC BY-SA, Hebrew Wikipedia contributors) and the definitions of
  HaMichlol (source_kind 5, CC BY-SA by their sources.json, HaMichlol contributors; included at the user's word,
  2026-11-04, with Klei Kodesh's permission). Nothing of Otzaria's lists (source_kind 9, 11) nor of the other sources
  whose origin is unknown (3, 4, 6, 7, 8, 10).

Rows, tab separated, by their first field:
  B  dictionary  book title                  (the books, numbered from 0 in their order)
  E  book number  line index  headword        (an entry of a book's line)
  S  dictionary  headword  text               (an entry with its own text, of no book; the headword with its
                                                 niqqud when the source gives it)
  L  kind  word  target                       (1: synonym, 2: another spelling or form)
"""

import argparse
import csv
import gzip
import re
import sys
from collections import defaultdict
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "SeforimApp/src/jvmMain/resources/kleikodesh/dictionary.tsv.gz"
SOURCES = {"metzudat": "METZUDAT", "malbim": "MALBIM", "micropedia": "MIKROPEDIA", "menachem": "MENACHEM"}
SENSES = {"source_01.tsv": "ARAMAIC", "source_02.tsv": "WIKIPEDIA", "source_05.tsv": "MICHLOL"}
KINDS = {"1", "2"}

csv.field_size_limit(sys.maxsize)


def read(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def field(text):
    return " ".join(text.replace("\\N", "").split())


def main():
    p = argparse.ArgumentParser()
    p.add_argument("clone", type=Path)
    p.add_argument("--out", type=Path, default=OUT)
    a = p.parse_args()
    src = a.clone / "source"

    titles = {r["id"]: r["title"] for r in read(src / "seforim_headword_book.tsv")}
    heads = defaultdict(list)
    dictionary = {}
    for r in read(src / "seforim_headword.tsv"):
        if r["source"] in SOURCES:
            heads[(r["bookId"], int(r["lineIndex"]))].append(field(r["word"]))
            dictionary[r["bookId"]] = SOURCES[r["source"]]
    rows, numbers = [], {}
    for book in sorted(dictionary, key=int):
        numbers[book] = len(numbers)
        rows.append(("B", dictionary[book], titles[book]))
    counts = defaultdict(int)
    for (book, index), words in sorted(heads.items(), key=lambda kv: (int(kv[0][0]), kv[0][1])):
        # One row per token and one of the whole headword: the longest is the headword
        rows.append(("E", numbers[book], index, max(words, key=len)))
        counts[dictionary[book]] += 1

    for name, key in SENSES.items():
        for r in read(src / "senses" / name):
            word, text = field(r["nikud"]) or field(r["headword"]), field(r["text"])
            if word and text:
                rows.append(("S", key, word, text))
                counts[key] += 1

    seen = set()
    for r in read(src / "links.tsv"):
        word, target = field(r["headword"]), field(r["target"])
        if r["kind_id"] in KINDS and word and target and word != target and (r["kind_id"], word, target) not in seen:
            seen.add((r["kind_id"], word, target))
            rows.append(("L", r["kind_id"], word, target))
            counts["L" + r["kind_id"]] += 1

    a.out.parent.mkdir(parents=True, exist_ok=True)
    # mtime=0: the same data packs to the same bytes
    with open(a.out, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0) as gz:
        for row in rows:
            gz.write(("\t".join(map(str, row)) + "\n").encode("utf-8"))
    print(dict(counts), f"{a.out.stat().st_size:,} bytes", file=sys.stderr)


if __name__ == "__main__":
    main()
