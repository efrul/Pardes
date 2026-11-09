#!/usr/bin/env python3
"""Checks the change map (docs/ZAYIT_CHANGE_MAP.md) against git: every file that differs from the Zayit release
Pardes is based on has its entry, with the details its kind needs, and no entry is left for a file that no longer
differs.

  check_change_map.py [--repo DIR] [--map PATH] [--committed] [--stats]

The base is the map's `<!-- zayit-base: v<x.y.z> <sha> -->` line. It must be the commit the last
"Merge Zayit v<x.y.z> into Pardes" brought (its second parent). By default the files compared are the working tree's,
untracked ones included, so the check runs before a commit; --committed compares HEAD only (CI). --stats prints
how many files differ, by status and by top folder.

An entry is a heading line "#### `path`" followed by its fields, or a table row "| `path` | ... |".
- A file of Zayit's that Pardes modified, deleted or renamed needs a heading with the fields
  Change, What, Why, Kind, Merge risk and Tests (and Renamed from, for a rename). The Change field of a modified or
  renamed file holds its line counts against the base, "+12 −3", which must be the file's: when the file changes, the
  check fails until its entry has been gone over again.
- A file Pardes added inside one of Zayit's Gradle modules (SeforimApp, buildSrc, navigation, ...) needs a heading
  with the fields Feature, Size, What, Reached from, Follows and Tests; Size is "N lines" (or "binary") and must be
  the file's, for the same reason.
- Any other added file (androidApp/, semantic-model/, branding/, docs/, workflows, skills, ...) needs at least a row.

Exits 0 when the map matches, 1 otherwise, listing what to fix.
"""

import argparse
import collections
import re
import subprocess
import sys
from pathlib import Path

BASE_LINE = re.compile(r"<!--\s*zayit-base:\s*(v\S+)\s+([0-9a-f]{7,40})\s*-->")
HEADING = re.compile(r"^(#{2,6})\s+`([^`]+)`\s*$")
ROW = re.compile(r"^\|\s*`([^`]+)`\s*\|")
ANY_HEADING = re.compile(r"^#{1,6}\s")
ZAYIT_MERGE = re.compile(r"^Merge Zayit (v\S+) into Pardes")

MODIFIED_FIELDS = ("Change", "What", "Why", "Kind", "Merge risk", "Tests")
ADDED_IN_MODULE_FIELDS = ("Feature", "Size", "What", "Reached from", "Follows", "Tests")
COUNTS = re.compile(r"\*\*Change:\*\*[^\n]*?\+([\d,]+)\s*[\u2212-]\s*([\d,]+)")
SIZE = re.compile(r"\*\*Size:\*\*\s*(?:([\d,]+)\s+lines?|(binary))")


def git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True).stdout


def read_map(path):
    """The map's base and its entries: path -> (kind of entry, text of the entry)."""
    text = path.read_text(encoding="utf-8")
    base = BASE_LINE.search(text)
    entries = {}
    duplicates = []
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        heading = HEADING.match(line)
        row = ROW.match(line)
        if heading:
            # The entry runs to the next heading or the next table row of a file
            body = []
            j = i + 1
            while j < len(lines) and not ANY_HEADING.match(lines[j]) and not ROW.match(lines[j]):
                body.append(lines[j])
                j += 1
            key = heading.group(2)
            if key in entries:
                duplicates.append(key)
            entries[key] = ("heading", "\n".join(body))
            i = j
            continue
        if row:
            key = row.group(1)
            if key in entries:
                duplicates.append(key)
            entries[key] = ("row", line)
        i += 1
    return base, entries, duplicates


def differing_files(repo, base, committed):
    """path -> (status letter, old path for a rename) between the base and the tree (or HEAD), and the untracked."""
    target = ["HEAD"] if committed else []
    fields = git(repo, "diff", "-z", "--name-status", "-M", base, *target).split("\0")
    changed = {}
    i = 0
    while i < len(fields) and fields[i]:
        status = fields[i][0]
        if status in "RC":
            changed[fields[i + 2]] = (status, fields[i + 1])
            i += 3
        else:
            changed[fields[i + 1]] = (status, None)
            i += 2
    untracked = []
    if not committed:
        tracked_in_base = set(git(repo, "ls-tree", "-r", "-z", "--name-only", base).split("\0"))
        for path in git(repo, "ls-files", "-z", "--others", "--exclude-standard").split("\0"):
            if path and path not in tracked_in_base:
                changed[path] = ("A", None)
                untracked.append(path)
    return changed, untracked


def line_counts(repo, base, committed, untracked):
    """path -> (lines added, lines deleted) against the base, or None for a binary file."""
    target = ["HEAD"] if committed else []
    fields = git(repo, "diff", "-z", "--numstat", "-M", base, *target).split("\0")
    counts = {}
    i = 0
    while i < len(fields) and fields[i]:
        added, deleted, path = fields[i].split("\t", 2)
        if not path:
            # A rename: the old and the new path follow
            path = fields[i + 2]
            i += 3
        else:
            i += 1
        counts[path] = None if added == "-" else (int(added), int(deleted))
    for path in untracked:
        data = (Path(repo) / path).read_bytes()
        lines = data.count(b"\n") + (1 if data and not data.endswith(b"\n") else 0)
        counts[path] = None if b"\0" in data else (lines, 0)
    return counts


def number(text):
    return int(text.replace(",", ""))


def zayit_modules(repo, base):
    """Zayit's Gradle modules: the base's top folders that hold a Gradle build script, plus buildSrc and gradle."""
    modules = {"buildSrc", "gradle"}
    for name in git(repo, "ls-tree", "--name-only", "-d", base).splitlines():
        scripts = git(repo, "ls-tree", "--name-only", base, "--", f"{name}/build.gradle.kts", f"{name}/build.gradle")
        if scripts.strip():
            modules.add(name)
    return modules


def last_zayit_merge(repo):
    """(version, sha Zayit's side brought) of the newest "Merge Zayit v<x.y.z> into Pardes" commit."""
    for line in git(repo, "log", "--merges", "--format=%H %P%x09%s", "HEAD").splitlines():
        shas, subject = line.split("\t", 1)
        match = ZAYIT_MERGE.match(subject)
        if match:
            parents = shas.split()[1:]
            return match.group(1), parents[1]
    return None


def missing_fields(body, fields):
    return [f for f in fields if not re.search(r"\*\*" + re.escape(f) + r":\*\*", body)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", default=None, help="the repository (default: the one this script is in)")
    parser.add_argument("--map", default="docs/ZAYIT_CHANGE_MAP.md", help="the map, relative to the repository")
    parser.add_argument("--committed", action="store_true", help="compare HEAD, not the working tree")
    parser.add_argument("--stats", action="store_true", help="print how many files differ, by status and folder")
    args = parser.parse_args()

    repo = Path(args.repo) if args.repo else Path(git(Path(__file__).parent, "rev-parse", "--show-toplevel").strip())
    map_path = repo / args.map
    if not map_path.exists():
        print(f"No change map at {map_path}")
        return 1

    base_match, entries, duplicates = read_map(map_path)
    if not base_match:
        print(f"{args.map} has no '<!-- zayit-base: v<x.y.z> <sha> -->' line")
        return 1
    version, base = base_match.groups()
    problems = []

    merged = last_zayit_merge(repo)
    if merged:
        merged_version, merged_sha = merged
        if not merged_sha.startswith(base) and not base.startswith(merged_sha):
            problems.append(
                f"base: the map says Zayit {version} ({base[:10]}), but the last merge brought Zayit "
                f"{merged_version} ({merged_sha[:10]}); set the base line to it and go over the entries again",
            )
        elif merged_version != version:
            problems.append(f"base: the map names {version}, the last merge {merged_version}; fix the base line")

    changed, untracked = differing_files(repo, base, args.committed)
    counts = line_counts(repo, base, args.committed, untracked)
    modules = zayit_modules(repo, base)

    for path in duplicates:
        problems.append(f"twice in the map: {path}")
    for path, (status, _old) in sorted(changed.items()):
        entry = entries.get(path)
        if entry is None:
            problems.append(f"no entry ({status}): {path}")
            continue
        kind, body = entry
        if status in "MDRT":
            fields = MODIFIED_FIELDS + (("Renamed from",) if status == "R" else ())
            if kind != "heading":
                problems.append(f"needs a '#### `{path}`' entry with its fields, not a row ({status})")
            else:
                lacking = missing_fields(body, fields)
                if lacking:
                    problems.append(f"entry lacks {', '.join(lacking)}: {path}")
                elif status in "MR" and counts.get(path):
                    found = COUNTS.search(body)
                    actual = counts[path]
                    if not found:
                        problems.append(f"Change field lacks the line counts (+{actual[0]} \u2212{actual[1]}): {path}")
                    elif (number(found.group(1)), number(found.group(2))) != actual:
                        problems.append(
                            f"changed since its entry was written (map +{found.group(1)} \u2212{found.group(2)}, "
                            f"file +{actual[0]} \u2212{actual[1]}): go over the entry again: {path}",
                        )
        elif path.split("/", 1)[0] in modules and "/" in path:
            if kind != "heading":
                problems.append(f"added in Zayit's module {path.split('/', 1)[0]}: needs a '#### `{path}`' entry")
            else:
                lacking = missing_fields(body, ADDED_IN_MODULE_FIELDS)
                if lacking:
                    problems.append(f"entry lacks {', '.join(lacking)}: {path}")
                else:
                    found = SIZE.search(body)
                    actual = counts.get(path)
                    expected = "binary" if actual is None else f"{actual[0]} lines"
                    written = None
                    if found:
                        written = "binary" if found.group(2) else f"{number(found.group(1))} lines"
                    if written != expected:
                        problems.append(
                            f"Size is {written or 'unreadable'}, the file has {expected}: "
                            f"go over the entry again: {path}",
                        )
    for path in sorted(set(entries) - set(changed)):
        problems.append(f"no longer differs from Zayit {version} (drop or fix the entry): {path}")

    if args.stats:
        by_status = collections.Counter(status for status, _ in changed.values())
        by_folder = collections.Counter(path.split("/", 1)[0] if "/" in path else "(root)" for path in changed)
        statuses = ", ".join(f"{s} {n}" for s, n in sorted(by_status.items()))
        print(f"Against Zayit {version} ({base[:10]}): {len(changed)} files, {statuses}")
        for folder, n in by_folder.most_common():
            print(f"  {n:5}  {folder}")

    if problems:
        print(f"The change map does not match the tree ({len(problems)} problems):")
        for problem in problems:
            print(f"  - {problem}")
        print("Every file that differs from Zayit has its entry, with all its details (the zayit-fidelity skill).")
        return 1
    print(f"The change map covers the {len(changed)} files that differ from Zayit {version}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
