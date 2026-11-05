#!/usr/bin/env bash
# A small library for running the desktop app: the released library (no full-text index), cut down to a few books and
# their commentaries and targumim, with the Talmud layouts that pardes-rashi-ocr made beside it (pardes-daf/).
#
#   make-library.sh [base book ids...]     default 103 (Berakhot, Bavli); Bava Batra is 124
#
# Env: LIB (default /home/user/pardes-lib), FULL (where the release is unpacked, default /home/user/lib-full),
# LAYOUTS (default /home/user/pardes-rashi-ocr/layouts). Needs ~8.2 GB free while it runs: the full seforim.db is
# deleted once the subset is made (the disk is a fixed allowance; check `df -h /`). About 5-10 min.
set -euo pipefail
ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
LIB="${LIB:-/home/user/pardes-lib}"
FULL="${FULL:-/home/user/lib-full}"
LAYOUTS="${LAYOUTS:-/home/user/pardes-rashi-ocr/layouts}"
IDS="${*:-103}"
[ -f "$FULL/seforim.db" ] || bash "$ROOT/.claude/skills/zayit-android/scripts/fetch-desktop-library.sh" --dest "$FULL" --no-fulltext
mkdir -p "$LIB"
# shellcheck disable=SC2086 # the ids are separate arguments
CONN_TYPES=1,3 bash "$ROOT/androidApp/tools/make_subset_db.sh" "$FULL/seforim.db" "$LIB/seforim.db" $IDS
cp -r "$FULL/catalog.pb" "$FULL/lexical.db" "$FULL/release_info.txt" "$FULL/seforim.db.lookup.lucene" "$LIB/"
rm -f "$FULL/seforim.db"
mkdir -p "$LIB/pardes-daf"
for f in "$LAYOUTS"/*.json; do [ -f "$f" ] && cp "$f" "$LIB/pardes-daf/"; done
ls -la "$LIB" "$LIB/pardes-daf"
