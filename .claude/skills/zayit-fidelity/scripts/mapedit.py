#!/usr/bin/env python3
"""Edits of the change map (docs/ZAYIT_CHANGE_MAP.md) by entry, for a script written for one change.

A text shared by two entries (a Size line, a "Tests: none.") is hit twice by a global sed; here every edit names its
entry's path, and fails unless it finds its text exactly as many times as it expects.

    import sys; sys.path.insert(0, '.claude/skills/zayit-fidelity/scripts')
    from mapedit import Map
    m = Map()
    m.change_counts('settings.gradle.kts')                     # "+N −M" as git has them now
    m.size('SeforimApp/src/jvmMain/kotlin/…/NewFile.kt')       # "Size: N lines" as the file is now
    m.in_entry('settings.gradle.kts', 'old words', 'new words')
    m.before_heading('### Branding', '### A new feature\\n\\n#### `path`\\n\\n- **Feature:** …')
    m.row('branding/brand.gradle', 'old words', 'new words')   # a row of section 3
    m.counts()                                                 # the entries of each section, for the header's numbers
    m.save()

Run it from the repository's root, then check_change_map.py.
"""
import re
import subprocess

MAP = 'docs/ZAYIT_CHANGE_MAP.md'


class Map:
    def __init__(self, path=MAP):
        self.path = path
        self.text = open(path, encoding='utf-8').read()
        self.base = re.search(r'<!--\s*zayit-base:\s*v\S+\s+([0-9a-f]{7,40})\s*-->', self.text).group(1)

    def save(self):
        open(self.path, 'w', encoding='utf-8').write(self.text)

    def span(self, path):
        """Where the entry headed "#### `path`" starts and ends."""
        head = f'#### `{path}`\n'
        assert self.text.count(head) == 1, (path, self.text.count(head))
        start = self.text.index(head)
        nxt = re.search(r'^#{2,4} ', self.text[start + len(head):], re.M)
        end = start + len(head) + (nxt.start() if nxt else len(self.text) - start - len(head))
        return start, end

    def entry(self, path):
        a, b = self.span(path)
        return self.text[a:b]

    def in_entry(self, path, old, new, count=1):
        a, b = self.span(path)
        body = self.text[a:b]
        assert body.count(old) == count, (path, old, body.count(old))
        self.text = self.text[:a] + body.replace(old, new) + self.text[b:]

    def numstat(self, path):
        out = subprocess.run(['git', 'diff', '--numstat', self.base, '--', path], capture_output=True, text=True, check=True)
        added, removed = out.stdout.split()[:2]
        return int(added), int(removed)

    def change_counts(self, path):
        """Sets the entry's "+N −M lines" to what git says now."""
        added, removed = self.numstat(path)
        a, b = self.span(path)
        new, n = re.subn(r'modified, \+\d+ −\d+ lines', f'modified, +{added} −{removed} lines', self.text[a:b])
        assert n == 1, (path, n)
        self.text = self.text[:a] + new + self.text[b:]

    def size(self, path):
        """Sets the entry's Size to the file's line count."""
        lines = sum(1 for _ in open(path, encoding='utf-8'))
        a, b = self.span(path)
        new, n = re.subn(r'- \*\*Size:\*\* \d+ lines\.', f'- **Size:** {lines} lines.', self.text[a:b])
        assert n == 1, (path, n)
        self.text = self.text[:a] + new + self.text[b:]

    def before_heading(self, heading, block):
        """Puts a block (entries, a table) before a heading line that the map has once."""
        assert self.text.count(heading + '\n') == 1, (heading, self.text.count(heading + '\n'))
        i = self.text.index(heading + '\n')
        self.text = self.text[:i] + block.rstrip('\n') + '\n\n' + self.text[i:]

    def after_entry(self, path, block):
        _, b = self.span(path)
        self.text = self.text[:b] + block.rstrip('\n') + '\n\n' + self.text[b:]

    def row(self, path, old, new):
        """Replaces words in the table row of a file of section 3."""
        found = re.search(r'^\| `%s` \|.*$' % re.escape(path), self.text, re.M)
        assert found, path
        line = found.group(0)
        assert line.count(old) == 1, (path, old, line.count(old))
        self.text = self.text[:found.start()] + line.replace(old, new) + self.text[found.end():]

    def replace(self, old, new, count=1):
        """A text outside the entries (the header's numbers, the kinds' table), found exactly count times."""
        assert self.text.count(old) == count, (old, self.text.count(old))
        self.text = self.text.replace(old, new)

    def counts(self):
        """The entries of each section, as the header's "(N files)" and section 3's opening line must say."""
        marks = [self.text.index('\n## 1. '), self.text.index('\n## 2. '), self.text.index('\n## 3. '), self.text.index('\n## 4. ')]
        sections = [self.text[marks[i]:marks[i + 1]] for i in range(3)]
        return [len(re.findall(r'^#### `', s, re.M)) + len(re.findall(r'^\| `[^`]+` \|', s, re.M)) for s in sections]


if __name__ == '__main__':
    one, two, three = Map().counts()
    print(f'section 1: {one} files, section 2: {two}, section 3: {three}')
