#!/usr/bin/env python3
"""ScopeFlowBench's step lines as a table: per scope and step, over the queries, the cold first round's median and the
warm rounds' median (and their slowest), to its first results and to its end.

    python3 table.py <the bench's output>
"""
import re
import statistics
import sys
from collections import defaultdict

STEP = re.compile(r'^r(\d+) (.+?) \| (.+?) \| (.+?): first (\S+) tabs (\S+) done (\S+) hits (-?\d+)')


def median(values):
    known = [v for v in values if v == v]
    return statistics.median(known) if known else float('nan')


def main(path):
    first = defaultdict(lambda: defaultdict(list))
    done = defaultdict(lambda: defaultdict(list))
    order = []
    for line in open(path, encoding='utf-8'):
        m = STEP.match(line)
        if not m:
            continue
        rnd, _query, scope, step, f, _tabs, d, _hits = m.groups()
        key = (scope, step)
        if key not in order:
            order.append(key)
        first[key][int(rnd)].append(float(f))
        done[key][int(rnd)].append(float(d))
    print(f"{'scope':28} {'step':12} {'cold first':>10} {'cold done':>10} {'warm first':>10} {'warm done':>10} {'warm max':>9}")
    for key in order:
        warm_first = [v for r, vs in first[key].items() if r > 0 for v in vs]
        warm_done = [v for r, vs in done[key].items() if r > 0 for v in vs]
        slowest = max((v for v in warm_done if v == v), default=float('nan'))
        print(f"{key[0]:28} {key[1]:12} {median(first[key].get(0, [])):10.0f} {median(done[key].get(0, [])):10.0f} "
              f"{median(warm_first):10.0f} {median(warm_done):10.0f} {slowest:9.0f}")


if __name__ == '__main__':
    main(sys.argv[1])
