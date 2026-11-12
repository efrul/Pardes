#!/usr/bin/env python3
"""What the judges said of sample.py's lines, by their place among the lines found by meaning alone and by their score's
gap from the search's closest line.

    judged.py key.json grades-A.tsv grades-B.tsv [--set grades.tsv ...] [--tsv judged.tsv]

A grades file is `item<TAB>grade<TAB>note`. --set gives grade files of the evaluation set itself (qid, lineId, grade), to
compare the new grades with those it already has for the same lines. --tsv writes the judged lines with their place and
grade, without their text.
"""
import collections
import csv
import json
import statistics
import sys

GAPS = ((0.01, '<0.01'), (0.02, '0.01-0.02'), (0.03, '0.02-0.03'), (0.05, '0.03-0.05'), (0.07, '0.05-0.07'), (9, '>=0.07'))


def table(title, groups):
    print(f'\n{title:<14}   n   g3  g2  g1  g0   answers (2, 3)   related (1 to 3)')
    for name, c in groups:
        n = sum(c.values())
        print(f'{name:<14} {n:>3}   {c[3]:>2}  {c[2]:>2}  {c[1]:>2}  {c[0]:>2}   {(c[3] + c[2]) / n:>10.0%}   {(n - c[0]) / n:>13.0%}')


def main(args):
    key = json.load(open(args[0], encoding='utf-8'))
    files, own, out = [], [], None
    rest = args[1:]
    while rest:
        arg = rest.pop(0)
        if arg == '--set':
            own.append(rest.pop(0))
        elif arg == '--tsv':
            out = rest.pop(0)
        else:
            files.append(arg)
    grades, judge = {}, {}
    for path in files:
        for r in csv.DictReader(open(path, encoding='utf-8'), delimiter='\t', quoting=csv.QUOTE_NONE):
            grades[r['item'].strip()] = int(r['grade'])
            judge[r['item'].strip()] = r['item'].strip()[0]
    print(f'{len(grades)} lines judged of {len(key)}')

    by_place = collections.defaultdict(collections.Counter)
    by_gap = collections.defaultdict(collections.Counter)
    ranks = collections.defaultdict(list)
    for item, grade in grades.items():
        line = key[item]
        by_place[line['place']][grade] += 1
        by_gap[next(i for i, (top, _) in enumerate(GAPS) if line['gap'] < top)][grade] += 1
        ranks[line['place']].append(line['denseRank'])
    table('place', [(str(p), by_place[p]) for p in sorted(by_place)])
    table('places', [
        ('1 to 6', sum((by_place[p] for p in by_place if p <= 6), collections.Counter())),
        ('12 and 25', sum((by_place[p] for p in by_place if 6 < p <= 25), collections.Counter())),
        ('50 to 145', sum((by_place[p] for p in by_place if p > 25), collections.Counter())),
    ])
    table('gap', [(GAPS[i][1], by_gap[i]) for i in sorted(by_gap)])
    print('\nthe median rank on the dense side of each place:', {p: statistics.median(v) for p, v in sorted(ranks.items())})

    old = {}
    for path in own:
        for r in csv.DictReader(open(path, encoding='utf-8'), delimiter='\t', quoting=csv.QUOTE_NONE):
            old[(int(r['qid']), int(r['lineId']))] = int(r['grade'])
    pairs = [(old[(key[i]['qid'], key[i]['lineId'])], g) for i, g in grades.items() if (key[i]['qid'], key[i]['lineId']) in old]
    if pairs:
        same = sum(1 for a, b in pairs if a == b)
        near = sum(1 for a, b in pairs if abs(a - b) <= 1)
        side = sum(1 for a, b in pairs if (a >= 2) == (b >= 2))
        print(f"\nagainst the set's own grades: {len(pairs)} lines, the same grade {same}, within one {near}, the same side of 'answers' {side}")

    if out:
        with open(out, 'w', encoding='utf-8', newline='') as f:
            w = csv.writer(f, delimiter='\t', quoting=csv.QUOTE_NONE, quotechar=None, lineterminator='\n')
            w.writerow(['item', 'qid', 'lineId', 'place', 'denseRank', 'gap', 'grade', 'judge', 'ref'])
            for item in sorted(grades):
                line = key[item]
                w.writerow([item, line['qid'], line['lineId'], line['place'], line['denseRank'], f"{line['gap']:.4f}", grades[item], judge[item], line.get('ref', '')])
        print(f'wrote {out}')


if __name__ == '__main__':
    main(sys.argv[1:])
