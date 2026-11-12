#!/usr/bin/env python3
"""What the judges said of sample.py's lines, by their place among the lines found by meaning alone and by their score's
gap from the search's closest line; with Meant.kt's output (--run), also by how far each line stands out of its search's
candidates (its score above their mean, in standard deviations of theirs, as HybridSearchEngine.standingOf reckons it),
and what each rule of which lines to keep keeps: of the judged lines, and how many of every search's.

    judged.py key.json grades-A.tsv grades-B.tsv [--run real.jsonl] [--set grades.tsv ...] [--tsv judged.tsv]

A grades file is `item<TAB>grade<TAB>note`. --set gives grade files of the evaluation set itself (qid, lineId, grade), to
compare the new grades with those it already has for the same lines. --tsv writes the judged lines with their place,
standing and grade, without their text.
"""
import collections
import csv
import json
import statistics
import sys

NOISE = {75, 76}
STANDINGS = ((1.0, '<1'), (1.5, '1-1.5'), (2.0, '1.5-2'), (2.5, '2-2.5'), (3.0, '2.5-3'), (99, '>=3'))


def standing(line, run):
    """How far [line] stands out of its search's dense candidates, as HybridSearchEngine.standingOf reckons it."""
    scores = run['denseScores']
    spread = statistics.pstdev(scores)
    return (line['denseScore'] - statistics.mean(scores)) / spread if spread > 0 else 0.0


def rules(runs, key, grades):
    """What each rule keeps: of the judged lines (how many, related, answering, sources), and of every search."""
    def by_place(n):
        return lambda run: run['meant'][:n]

    def by_gap(gap):
        def keep(run):
            top = max((line['denseScore'] for line in run['meant']), default=0)
            return [line for line in run['meant'] if top - line['denseScore'] <= gap + 1e-9]
        return keep

    def by_standing(least):
        return lambda run: [line for line in run['meant'] if standing(line, run) >= least]

    chosen = [('the first 6', by_place(6)), ('the first 10', by_place(10)), ('gap <= 0.01 from the closest', by_gap(0.01)),
              ('gap <= 0.02 from the closest', by_gap(0.02)), ('standing >= 1.75', by_standing(1.75)),
              ('standing >= 2', by_standing(2.0)), ('standing >= 2.25', by_standing(2.25))]
    print(f"\n{'rule':<30} kept  related answers sources | dropped related | a search keeps: median, 10% to 90%, most")
    for name, keep in chosen:
        kept, dropped = collections.Counter(), collections.Counter()
        for item, grade in grades.items():
            line = key[item]
            ids = {m['lineId'] for m in keep(runs[line['qid']])}
            c = kept if line['lineId'] in ids else dropped
            c['n'] += 1
            c['related'] += grade >= 1
            c['answers'] += grade >= 2
            c['sources'] += grade == 3
        counts = sorted(len(keep(run)) for run in runs.values())
        q = lambda p: counts[int(p * (len(counts) - 1))]
        print(f"{name:<30} {kept['n']:>4} {kept['related'] / max(1, kept['n']):>8.0%} {kept['answers'] / max(1, kept['n']):>7.0%} "
              f"{kept['sources']:>7} | {dropped['related'] / max(1, dropped['n']):>15.0%} | {statistics.median(counts)}, {q(.1)} to {q(.9)}, {counts[-1]}")

GAPS = ((0.01, '<0.01'), (0.02, '0.01-0.02'), (0.03, '0.02-0.03'), (0.05, '0.03-0.05'), (0.07, '0.05-0.07'), (9, '>=0.07'))


def table(title, groups):
    print(f'\n{title:<14}   n   g3  g2  g1  g0   answers (2, 3)   related (1 to 3)')
    for name, c in groups:
        n = sum(c.values())
        print(f'{name:<14} {n:>3}   {c[3]:>2}  {c[2]:>2}  {c[1]:>2}  {c[0]:>2}   {(c[3] + c[2]) / n:>10.0%}   {(n - c[0]) / n:>13.0%}')


def main(args):
    key = json.load(open(args[0], encoding='utf-8'))
    files, own, out, run = [], [], None, None
    rest = args[1:]
    while rest:
        arg = rest.pop(0)
        if arg == '--set':
            own.append(rest.pop(0))
        elif arg == '--tsv':
            out = rest.pop(0)
        elif arg == '--run':
            run = rest.pop(0)
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

    runs = {}
    if run:
        for text in open(run, encoding='utf-8'):
            r = json.loads(text)
            if r['id'] not in NOISE:
                runs[r['id']] = r
        for item, line in key.items():
            meant = next(m for m in runs[line['qid']]['meant'] if m['lineId'] == line['lineId'])
            line['standing'] = standing(meant, runs[line['qid']])
        by_standing = collections.defaultdict(collections.Counter)
        for item, grade in grades.items():
            by_standing[next(i for i, (top, _) in enumerate(STANDINGS) if key[item]['standing'] < top)][grade] += 1
        table('standing', [(STANDINGS[i][1], by_standing[i]) for i in sorted(by_standing)])
        rules(runs, key, grades)

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
            w.writerow(['item', 'qid', 'lineId', 'place', 'denseRank', 'gap', 'standing', 'grade', 'judge', 'ref'])
            for item in sorted(grades):
                line = key[item]
                standing_text = f"{line['standing']:.2f}" if 'standing' in line else ''
                w.writerow([item, line['qid'], line['lineId'], line['place'], line['denseRank'], f"{line['gap']:.4f}", standing_text,
                            grades[item], judge[item], line.get('ref', '')])
        print(f'wrote {out}')


if __name__ == '__main__':
    main(sys.argv[1:])
