#!/usr/bin/env python3
"""Two ScopeFlowBench runs side by side: per scope and step, over every round and query, the median and the slowest
time to the step's first results (to its end when its lines did not change).

    python3 compare.py <before> <after>
"""
import re
import statistics
import sys
from collections import defaultdict

STEP = re.compile(r'^r(\d+) (.+?) \| (.+?) \| (.+?): first (\S+) tabs (\S+) done (\S+) hits (-?\d+)')


def load(path):
    times = defaultdict(list)
    order = []
    for line in open(path, encoding='utf-8'):
        m = STEP.match(line)
        if not m:
            continue
        _rnd, _query, scope, step, first, _tabs, done, _hits = m.groups()
        key = (scope, step)
        if key not in order:
            order.append(key)
        value = float(first)
        times[key].append(value if value == value else float(done))
    return times, order


def stats(values):
    known = [v for v in values if v == v]
    return f"{statistics.median(known):5.0f} {max(known):5.0f}" if known else '  nan'


def main(before_path, after_path):
    before, order = load(before_path)
    after, _ = load(after_path)
    print(f"{'scope':28} {'step':12} | before: median, slowest | after: median, slowest")
    for key in order:
        print(f"{key[0]:28} {key[1]:12} | {stats(before[key]):>11} | {stats(after[key]):>11}")


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
