"""Helper of ../check-min-api.sh below API 21:

    dexdump -d classes.dex | python3 -I dexcatch.py - levels.txt

The Dalvik verifier of Android 4.4 (getCaughtExceptionType, dalvik/vm/analysis/CodeVerify.cpp, kitkat-release) types
the exception at a handler's move-exception as the common superclass of the catch types at that address that resolve;
when none resolves, that is VERIFY_ERROR_GENERIC and the whole class is rejected (VerifyError). D8 gives each catch type
its own handler address, so one catch of a class Android 4.4 lacks (java.nio.file.NoSuchFileException without the nio
desugared library) rejects the class. Missing types are those of the "above API" part of DexApiLevels' levels.txt.

Prints every handler address whose catch types are all missing, and whether it starts with move-exception (hard) or
not (the exception is never read: no check). The last line is "<n> hard, <m> handlers with only missing catch types".
"""
import io
import re
import sys


def missing_types(levels):
    """The types of the "above API" part of levels.txt, as dex descriptors."""
    missing = set()
    section = None
    with open(levels, encoding='utf-8') as lines:
        for line in lines:
            if line.startswith('above API'):
                section = 'above'
            elif line.startswith('not in api'):
                section = 'info'
            elif section == 'above' and line.startswith('  '):
                parts = line.rstrip('\n').split('\t')
                if parts[1] == 'type':
                    missing.add('L%s;' % parts[2])
    return missing


def handlers(dump, missing):
    """(class, method, handler address, catch types, first opcode) of each handler whose catch types are all missing."""
    found = []
    cls = method = name = None
    insns = {}
    catches = {}
    in_catches = False

    def flush():
        for address, types in catches.items():
            if types and all(t in missing for t in types):
                found.append((cls, method, address, sorted(types), insns.get(address, '?')))

    for line in dump:
        match = re.match(r"\s+Class descriptor\s+: '(.*)'", line)
        if match:
            flush()
            insns, catches, in_catches = {}, {}, False
            cls = match.group(1)
            continue
        match = re.match(r"\s+name\s+: '(.*)'", line)
        if match:
            name = match.group(1)
            continue
        match = re.match(r"\s+type\s+: '(\(.*)'", line)
        if match and name:
            flush()
            method = name + match.group(1)
            insns, catches, in_catches = {}, {}, False
            continue
        match = re.search(r'\|([0-9a-f]{4}): (\S+)', line)
        if match and not in_catches:
            insns[int(match.group(1), 16)] = match.group(2)
            continue
        if re.match(r'\s+catches\s+:', line):
            in_catches = True
            continue
        if in_catches:
            match = re.match(r'\s+(\S+) -> 0x([0-9a-f]+)', line)
            if match:
                catches.setdefault(int(match.group(2), 16), set()).add(match.group(1))
            elif re.match(r'\s+(positions|locals)\s+:', line):
                in_catches = False
    flush()
    return found


def main():
    dump, levels = sys.argv[1:3]
    missing = missing_types(levels)
    if dump == '-':
        found = handlers(io.TextIOWrapper(sys.stdin.buffer, encoding='utf-8', errors='replace'), missing)
    else:
        with open(dump, encoding='utf-8', errors='replace') as lines:
            found = handlers(lines, missing)
    hard = 0
    for cls, method, address, types, opcode in found:
        if opcode == 'move-exception':
            hard += 1
            verdict = 'HARD (move-exception: no resolvable catch type)'
        else:
            verdict = 'ok (exception not read)'
        print('%s\t%s\t0x%04x\t%s\t%s\t%s' % (cls, method, address, ','.join(types), opcode, verdict))
    print(hard, 'hard,', len(found), 'handlers with only missing catch types')


main()
