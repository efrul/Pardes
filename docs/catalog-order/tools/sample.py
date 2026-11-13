#!/usr/bin/env python3
"""A blind sample of the lines a search found by meaning alone, for judging: from the searches of Meant.kt's output that
have 145 such lines or more, 30 drawn at random (seed 31), and of each the lines at places 1, 3, 6, 12, 25, 50, 100 and
145 among them (the fused list's order).

    sample.py real.jsonl eval-real/queries.tsv OUT_DIR

Writes OUT_DIR/judge-A.md and judge-B.md (15 searches each, a search's lines shuffled, each line under an id that says
nothing of its place) and OUT_DIR/key.json (id to search, place, line, its rank, score and gap on the dense side). The
judging files hold the library's text and stay out of the repository; key.json and the grades are what is kept.
"""
import csv
import json
import random
import re
import sys

PLACES = (1, 3, 6, 12, 25, 50, 100, 145)
NOISE = {75, 76}  # eval-real's two noise searches (its score.py)


def clean(text):
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', '', text)).strip()


def main(run, queries, out):
    rows = [json.loads(line) for line in open(run, encoding='utf-8')]
    hints = {int(r['id']): r['hint_book'] for r in csv.DictReader(open(queries, encoding='utf-8'), delimiter='\t', quoting=csv.QUOTE_NONE)}
    full = [r for r in rows if r['id'] not in NOISE and len(r['meant']) >= PLACES[-1]]
    rnd = random.Random(31)
    chosen = rnd.sample(full, 30)
    key = {}
    for name, searches in (('A', chosen[:15]), ('B', chosen[15:])):
        text = ['# Lines to judge\n']
        for r in searches:
            text.append(f"\n## Search {r['id']}: {r['query']}\n")
            if hints.get(r['id']):
                text.append(f"(the user had limited this search to the book: {hints[r['id']]}; a hint of what was meant, not a requirement)\n")
            items = [(place, r['meant'][place - 1]) for place in PLACES]
            rnd.shuffle(items)
            for place, line in items:
                item = f"{name}{len(key) + 1:03d}"
                key[item] = {
                    'qid': r['id'], 'place': place, 'lineId': line['lineId'], 'ref': line['heRef'], 'book': line['book'],
                    'denseRank': line['denseRank'], 'denseScore': line['denseScore'], 'gap': r['denseTop'] - line['denseScore'],
                    'fusedPlace': line['fusedPlace'], 'scope': r['scope'],
                }
                text.append(f"\n[{item}] {line['book']} | {line['heRef']}\n{clean(line['text'])}\n")
        open(f'{out}/judge-{name}.md', 'w', encoding='utf-8').write(''.join(text))
    json.dump(key, open(f'{out}/key.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
    print(f'{len(full)} searches with {PLACES[-1]} lines or more, {len(chosen)} drawn, {len(key)} lines')


if __name__ == '__main__':
    main(*sys.argv[1:4])
