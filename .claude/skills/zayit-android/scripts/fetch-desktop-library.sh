#!/usr/bin/env bash
# Downloads the library release that desktop Zayit installs (kdroidFilter/SeforimLibrary, latest release) and
# streams it into a folder: curl parts | zstd -d | tar -x. The compressed bundle (~5.5 GB) is never stored.
#
#   fetch-desktop-library.sh [--dest DIR] [--no-fulltext]
#
#   --dest DIR      target folder (default: ~/.local/share/io.github.kdroidfilter.seforimapp/databases, the desktop
#                   app's own folder on Linux, so desktop Zayit can use it too)
#   --no-fulltext   skip seforim.db.lucene/ (~4.95 GB, the full-text index). Keeps seforim.db (8 GB), the lookup index,
#                   lexical.db, catalog.pb and release_info.txt. Use it when disk is tight and full-text search isn't
#                   being tested; the dev subset (make_subset_db.sh) is built from seforim.db alone.
#
# Needs ~8.2 GB free (~13.1 GB with the full-text index). Check `df -h /` first; the cloud container has a fixed
# disk allowance. Takes 10-30 min depending on the network.
set -euo pipefail
DEST="$HOME/.local/share/io.github.kdroidfilter.seforimapp/databases"; EXCLUDE=()
while [ $# -gt 0 ]; do
  case "$1" in
    --dest) DEST="$2"; shift 2 ;;
    --no-fulltext) EXCLUDE=(--exclude 'seforim.db.lucene'); shift ;;
    -h|--help) sed -n 2,14p "$0"; exit 0 ;;
    *) echo "unknown option $1"; exit 2 ;;
  esac
done
command -v zstd >/dev/null || { echo "installing zstd"; DEBIAN_FRONTEND=noninteractive apt-get install -y -qq zstd >/dev/null; }
mkdir -p "$DEST"

# The release manifest lists the parts. It is read through github.com/.../releases/latest/download/, not the
# GitHub API: the cloud container's proxy refuses api.github.com for repositories not attached to the session,
# while release downloads pass.
BASE=https://github.com/kdroidFilter/SeforimLibrary/releases/latest/download
parts=$(curl -fsSL "$BASE/seforim_bundle.tar.zst.manifest.json" | python3 -c '
import json, sys
m = json.load(sys.stdin)
print("\n".join(p["file"] for p in m["parts"]))
print("total %.1f GB compressed" % (m["size"] / 1e9), file=sys.stderr)')
[ -n "$parts" ] || { echo "could not read the release manifest from $BASE"; exit 1; }
echo "parts:"; echo "$parts" | sed 's/^/  /'

# Parts are byte ranges of one .tar.zst, so concatenating them in order rebuilds the stream (zstd verifies its
# own checksums, so a corrupted download fails instead of leaving a bad file).
# The proxy resets long transfers, and curl --retry would restart a part from byte 0 inside the pipe, so
# stream-ranges.py fetches each part in Range chunks and resumes a dropped chunk from the byte it reached.
urls=(); for p in $parts; do urls+=("$BASE/$p"); done
python3 "$(dirname "$0")/stream-ranges.py" "${urls[@]}" | zstd -d -c | tar -x --no-same-owner -C "$DEST" "${EXCLUDE[@]}"
rm -f "$DEST/seforim.db-wal" "$DEST/seforim.db-shm"
echo "done:"; ls -la "$DEST"; du -sh "$DEST"
