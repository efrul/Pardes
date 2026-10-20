#!/usr/bin/env bash
# Copies the library of desktop Zayit to Pardes (an Android app based on Zayit) over adb, so the phone needs no 5.5 GB download.
# העתקת הספרייה של זית למחשב אל פרדס בטלפון דרך adb, ללא הורדה של 5.5 ג״ב בטלפון.
#
#   push-desktop-library.sh [--source DIR] [-s SERIAL] [--sdcard [UUID]] [--with-notes] [--dry-run]
#
#   --source DIR   desktop library folder (default: $SEFORIMAPP_DATABASE_PATH's folder, else the desktop install:
#                  Linux ${XDG_DATA_HOME:-~/.local/share}/io.github.kdroidfilter.seforimapp/databases,
#                  macOS ~/Library/Application Support/io.github.kdroidfilter.seforimapp/databases)
#   -s SERIAL      device to use when several are connected (see `adb devices`)
#   --sdcard       copy to the memory card instead of the phone's storage (the card's UUID when there are several):
#                  /storage/<UUID>/Android/data/<app>/files/library/. On a FAT32 card seforim.db goes in chunks of
#                  2 GiB less 64 KiB (seforim.db, seforim.db001…), which the app reads as one database. Once the copy is
#                  complete, a library in the phone's storage is removed: the app keeps one library (below Android
#                  7.0 a copy to the phone's storage removes a library on a card in turn). Needs Pardes
#                  0.3.0 or later (versionCode 2), the first that opens a library on the card, or Pardes for Android
#                  4.4 to 7.1 (versionCode 1). Below Android 6.0 a card has a name, not a UUID (sdcard1, extSdCard).
#   --with-notes   also copy notes and highlights (settings/user_settings.db); they replace the phone's own
#   --dry-run      print the adb commands instead of running them
#
# Copies seforim.db, the search indexes seforim.db.lucene/ and seforim.db.lookup.lucene/, lexical.db, catalog.pb and
# release_info.txt (~12.2 GB) into /sdcard/Android/data/<app>/files/library/. Needs USB debugging on the phone, the app
# installed, and Zayit closed on the computer. No root. Without root, `adb push` cannot write into another app's folder
# (its fchown fails there), so each file is streamed through `adb shell -T` and written in place by the phone's shell:
# no staging copy, so the phone only needs room for the library itself. An item is written as .zayit-incoming-<name> (the app deletes <name>.part when it opens) and renamed
# when complete. The files belong to adb's `shell` user, so they are made writable for the app (SQLite opens the
# database read-write). Folders the app made itself (its own download) cannot be emptied by adb: they are renamed
# .zayit-old-* and the app deletes them when it next starts. Windows: push-desktop-library.ps1.
# Android 4.4 to 6.0 (Pardes for Android 4.4 to 7.1): their adb has no `shell -T` (a shell there always has a terminal,
# which garbles binary input), so each file goes with `adb push`, which writes into the app's folder there; their
# phones have no stat, find or awk (sizes come from toolbox ls -l, read here), print df's sizes as 1.8G, list no
# volumes (`sm`, Android 6.0) and may keep the phone's own storage on a FAT32 card, where seforim.db then goes in
# chunks as on a memory card.
set -euo pipefail
APPID=io.github.kdroidfilter.seforimapp
PKG=io.github.efrul.pardes
FILES=/sdcard/Android/data/$PKG/files
LIB=$FILES/library
PART=.part
# Name of an item while it is being copied. Not <name>.part: the app deletes those as a broken download when it opens.
INCOMING=.zayit-incoming-
# Indexes and small files first, the database last: the app treats the library as installed once seforim.db is there.
ITEMS=(seforim.db.lookup.lucene seforim.db.lucene lexical.db catalog.pb release_info.txt seforim.db)
SRC=""; SERIAL=""; NOTES=0; DRY=0; CARD=""; FORCE_SPLIT=0
# SQLite multiplex chunks for a FAT32 card (SplitDatabase in the app): 2 GiB less 64 KiB, in 64 KiB blocks.
CHUNK=2147418112; BLOCK=65536
say() { printf '==> %s\n    %s\n' "$2" "$1"; }
die() { printf 'ERROR: %s\n       %s\n' "$2" "$1" >&2; exit 1; }
while [ $# -gt 0 ]; do
  case "$1" in
    --source) SRC="${2:?}"; shift 2 ;;
    -s) SERIAL="${2:?}"; shift 2 ;;
    --with-notes) NOTES=1; shift ;;
    --dry-run) DRY=1; shift ;;
    # A card's UUID, or below Android 6.0 its name (checked against the version below)
    --sdcard) CARD=auto; if [[ "${2:-}" =~ ^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$|^[A-Za-z][A-Za-z0-9_]*$ ]]; then CARD=$2; shift; fi; shift ;;
    # For tests: chunks on any card, and their size (a multiple of 64 KiB; the app reads any such size).
    --split) FORCE_SPLIT=1; shift ;;
    --chunk-bytes) CHUNK="${2:?}"; shift 2 ;;
    -h|--help) sed -n 2,33p "$0"; exit 0 ;;
    *) die "אפשרות לא מוכרת: $1" "unknown option $1 (see --help)" ;;
  esac
done

# --- desktop library (FileKit.databasesDir of the desktop app; the desktop installer always unpacks there)
case "$(uname -s)" in
  Darwin) DEFAULT="$HOME/Library/Application Support/$APPID/databases" ;;
  *) DEFAULT="${XDG_DATA_HOME:-$HOME/.local/share}/$APPID/databases" ;;
esac
if [ -z "$SRC" ] && [ -n "${SEFORIMAPP_DATABASE_PATH:-}" ]; then
  SRC="$SEFORIMAPP_DATABASE_PATH"; [ -f "$SRC" ] && SRC="$(dirname "$SRC")"
fi
SRC="${SRC:-$DEFAULT}"
[ -s "$SRC/seforim.db" ] || die "לא נמצא seforim.db בתיקייה $SRC. התקינו את זית במחשב או ציינו --source" "no seforim.db in $SRC; install desktop Zayit or pass --source DIR"
NOTES_DB="$SRC/settings/user_settings.db"; [ -f "$NOTES_DB" ] || NOTES_DB="$DEFAULT/settings/user_settings.db"
for db in "$SRC/seforim.db" "$NOTES_DB"; do
  [ -s "$db-wal" ] && die "סגרו את זית במחשב ונסו שוב ($db-wal אינו ריק)" "close Zayit on the computer first ($db-wal is not empty)"
done
for d in seforim.db.lucene seforim.db.lookup.lucene; do
  [ -d "$SRC/$d" ] || say "חסר $d: החיפוש לא יעבוד בטלפון" "warning: $d missing, search will not work on the phone"
done
if [ $NOTES = 1 ] && [ ! -s "$NOTES_DB" ]; then die "לא נמצא קובץ ההערות $NOTES_DB" "no notes database at $NOTES_DB"; fi
TOTAL_KB=0; PRESENT=()
for i in "${ITEMS[@]}"; do
  [ -e "$SRC/$i" ] || continue
  PRESENT+=("$i"); TOTAL_KB=$(( TOTAL_KB + $(du -sk "$SRC/$i" | cut -f1) ))
done
say "מקור: $SRC ($(( TOTAL_KB / 1024 )) מ״ב)" "source: $SRC ($(( TOTAL_KB / 1024 )) MB): ${PRESENT[*]}"

# --- adb and the device
ADB=$(command -v adb || true)
for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -z "$ADB" ] && [ -n "$c" ] && [ -x "$c/platform-tools/adb" ] && ADB="$c/platform-tools/adb"; done
HERE="$(cd "$(dirname "$0")" && pwd)"
for c in "$HERE/adb" "$HERE/platform-tools/adb"; do [ -z "$ADB" ] && [ -x "$c" ] && ADB="$c"; done
if [ -z "$ADB" ]; then
  [ $DRY = 1 ] && ADB=adb || die "adb לא נמצא. התקינו Android platform-tools" "adb not found; install Android platform-tools (or set ANDROID_HOME)"
fi
adbs() { "$ADB" ${SERIAL:+-s "$SERIAL"} "$@"; }
# run: changes the device (printed in --dry-run). ask FAKE: reads from it (FAKE is the --dry-run answer).
run() { if [ $DRY = 1 ]; then echo "    \$ adb${SERIAL:+ -s $SERIAL} $*"; else adbs "$@"; fi; }
ask() { local fake="$1"; shift; if [ $DRY = 1 ]; then echo "    \$ adb${SERIAL:+ -s $SERIAL} $*" >&2; echo "$fake"; else adbs "$@" | tr -d '\r'; fi; }
if [ -z "$SERIAL" ]; then
  if [ $DRY = 1 ]; then SERIAL=DEVICE; else
    DEVICES=$("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }' | tr '\n' ' ')
    set -- $DEVICES
    [ $# = 0 ] && die "לא נמצא מכשיר. חברו את הטלפון, הפעילו ניפוי באגים ב-USB ואשרו את המחשב בטלפון" "no device; connect the phone, enable USB debugging and accept this computer on the phone"
    [ $# -gt 1 ] && die "מחוברים כמה מכשירים, בחרו אחד עם -s: $DEVICES" "several devices, pick one with -s: $DEVICES"
    SERIAL=$1
  fi
fi
say "מכשיר: $SERIAL" "device: $SERIAL"
PM=$(ask "package:/data/app/$PKG/base.apk" shell pm path $PKG || true)
# A line of its own: the Android 4.4 emulator's x86 Dalvik prints a linker warning first
[[ $'\n'"$PM" == *$'\n'package:* ]] || die "פרדס אינו מותקן במכשיר" "Pardes ($PKG) is not installed on the device"
# Android 11's adb shell is not in the ext_data_rw group (Android 12 added it): Android/data/<app>/ is out of its reach.
# Android 8 to 10 reach it (their sdcardfs gives the shell sdcard_rw), and take no chmod there (see the copy below).
SDK=$(ask 33 shell getprop ro.build.version.sdk); SDK=${SDK//[^0-9]/}; SDK=${SDK:-33}
# Android 4.4 to 6.0 (below 7.0, API 24): no `adb shell -T`, stat, find or awk; see the top of this file.
OLD=0; [ "$SDK" -lt 24 ] && OLD=1
if [ $OLD = 0 ] && [ -n "$CARD" ] && [ "$CARD" != auto ] && ! [[ "$CARD" =~ ^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$ ]]; then
  die "אפשרות לא מוכרת: $CARD" "unknown option $CARD (see --help)"
fi
if [ "$SDK" -eq 30 ] && [ "$(ask yes shell "ls /sdcard/Android/data >/dev/null 2>&1 && echo yes")" != yes ]; then
  die "באנדרואיד 11 כלי ההעברה (adb) אינו יכול לגשת לתיקייה של פרדס בטלפון; זה אפשרי מאנדרואיד 12. במקום זאת הורידו מעמוד ההפצה של הספרייה את הקבצים ‎.part ואת ‎.manifest.json, העתיקו אותם לתיקיית Download בטלפון ובחרו אותם בפרדס: מדריך ההגדרה ← התקנה לא-מקוונת" "Android 11: adb cannot reach Pardes's folder on the phone (it can from Android 12). Instead, download the library's .part and .manifest.json files from its release page, copy them to the phone's Download folder and pick them in Pardes: setup guide, offline install"
fi

# --- the memory card (--sdcard): its app folder, and its filesystem (FAT32 takes no file of 4 GB)
PHONE_FILES=$FILES; PHONE_LIB=$LIB; FS=""; PRIMARY=""
if [ $OLD = 1 ]; then
  # Below Android 7.0 the mounts are read for the cards (no `sm` before 6.0) and for the phone's own storage, which
  # on some phones is the card itself (FAT32, /storage/sdcard): vold mounts each at /mnt/media_rw/<name>, under the
  # FUSE view /storage/<name>. Read here, not on the phone: it has no awk.
  MOUNTS=$(ask "/dev/block/vold/179:1 /mnt/media_rw/sdcard1 vfat rw 0 0" shell cat /proc/mounts)
  PRIMARY=$(ask /storage/emulated/0 shell 'echo $EXTERNAL_STORAGE'); PRIMARY=${PRIMARY%/}
  PRIMARY_NAME=${PRIMARY#/storage/}; PRIMARY_NAME=${PRIMARY_NAME%%/*}
  media_fs() { echo "$MOUNTS" | awk -v m="/mnt/media_rw/$1" '$2 == m { print $3 }'; }
fi
# The memory cards mounted, by name below Android 6.0 (what vold mounted besides the phone's own storage, from the
# mounts: no `sm` there), else by UUID. Not /storage/*: the phone's own storage shows there under other names too
# (sdcard0 is emulated/legacy on Android 4.4).
card_volumes() {
  if [ "$SDK" -lt 23 ]; then
    echo "$MOUNTS" | awk -v p="$PRIMARY_NAME" '$2 ~ /^\/mnt\/media_rw\// { n = substr($2, 15); if (n != p) print n }'
  else
    ask "public:179,1 mounted 1234-ABCD" shell sm list-volumes public | awk '$2 == "mounted" && $3 != "null" { print $3 }'
  fi
}
if [ -n "$CARD" ]; then
  # An older Pardes finds no library on the card, and the phone's copy is removed at the end. Below Android 8.0 the
  # app can only be Pardes for Android 4.4 to 7.1 (versionCode 1), which looks for its library on the cards too.
  DUMP=$(ask "    versionCode=2 minSdk=33 targetSdk=36" shell dumpsys package $PKG || true)
  VC=0; [[ "$DUMP" =~ versionCode=([0-9]+) ]] && VC=${BASH_REMATCH[1]}
  [ "$VC" -ge 2 ] || [ "$SDK" -lt 26 ] || die "כדי להעתיק לכרטיס הזיכרון צריך פרדס 0.3.0 ומעלה. עדכנו את פרדס בטלפון ונסו שוב." "copying to the memory card needs Pardes 0.3.0 or later (versionCode 2, installed $VC): update the app first"
  VOLS=$(card_volumes)
  if [ "$CARD" = auto ]; then
    set -- $VOLS
    [ $# = 0 ] && die "לא נמצא כרטיס זיכרון מחובר" "no memory card is mounted"
    [ $# -gt 1 ] && die "יש כמה כרטיסים, בחרו אחד: --sdcard UUID ($VOLS)" "several cards, pick one with --sdcard UUID: $VOLS"
    CARD=$1
  fi
  echo "$VOLS" | grep -qx "$CARD" || die "הכרטיס $CARD אינו מחובר" "card $CARD is not mounted"
  FILES=/storage/$CARD/Android/data/$PKG/files
  LIB=$FILES/library
  # Read here, not on the phone: stock Android 8.x has no awk.
  if [ $OLD = 1 ]; then FS=$(media_fs "$CARD"); else
    FS=$(ask "/dev/block/vold/public:179,1 /mnt/media_rw/$CARD vfat rw 0 0" shell cat /proc/mounts | awk -v m="/mnt/media_rw/$CARD" '$2 == m { print $3 }')
  fi
  say "כרטיס זיכרון: $CARD ($FS)" "memory card: $CARD ($FS)"
elif [ $OLD = 1 ]; then
  FS=$(media_fs "$PRIMARY_NAME")
  if [ -n "$FS" ]; then say "זיכרון הטלפון: $PRIMARY ($FS)" "phone storage: $PRIMARY ($FS)"; fi
fi
SPLIT=0
DB_BYTES=$(wc -c < "$SRC/seforim.db" | tr -d ' ')
[ $(( CHUNK % BLOCK )) = 0 ] || die "גודל החלק חייב להיות כפולה של 65536" "--chunk-bytes must be a multiple of 65536"
if { [ -n "$CARD" ] || [ $OLD = 1 ]; } && { [ $FORCE_SPLIT = 1 ] || { [ "$FS" = vfat ] && [ "$DB_BYTES" -gt 4294967295 ]; }; }; then
  SPLIT=1
  say "הכרטיס מפורמט ב-FAT32: seforim.db יועתק בחלקים" "FAT32 card: seforim.db is copied in chunks (seforim.db, seforim.db001…)"
fi

# --- the app's folder exists only once it has run
if [ "$(ask yes shell "[ -d $FILES ] && echo yes")" != yes ]; then
  say "מפעיל את היישום פעם אחת ליצירת התיקייה שלו" "starting the app once so it creates its folder"
  run shell am start -n $PKG/io.github.kdroidfilter.seforimapp.android.MainActivity
  for _ in $(seq 30); do [ "$(ask yes shell "[ -d $FILES ] && echo yes")" = yes ] && break; sleep 2; done
  [ "$(ask yes shell "[ -d $FILES ] && echo yes")" = yes ] || die "התיקייה $FILES לא נוצרה. פתחו את היישום ונסו שוב" "$FILES was not created; open the app once and retry"
fi
run shell am force-stop $PKG
# A shell redirection, not touch: touch also sets the times, which fails on a leftover probe owned by the app.
run shell "mkdir -p $LIB; chmod 777 $LIB 2>/dev/null; rm -f $LIB/.zayit-push; : > $LIB/.zayit-push && rm $LIB/.zayit-push" \
  || die "אין הרשאת כתיבה ל-$LIB. אם הטלפון הופעל מחדש, בטלו את נעילת המסך ונסו שוב" "cannot write to $LIB (if the phone just restarted, unlock it and retry)"

# --- free space: what is free now plus what the old copies take
if [ $OLD = 1 ]; then
  # toolbox df takes no -k and prints its sizes as 1.8G (toybox, Android 6.0: 1K blocks); the free one is the 4th
  DF=$(ask $'Filesystem Size Used Free Blksize\n/storage/sdcard 2.0G 240.5M 50.0G 4096' shell df $FILES)
  AVAIL_KB=$(echo "$DF" | awk 'NR == 2 { v = $4; u = substr(v, length(v)); n = v + 0
    print int(u == "T" ? n * 1073741824 : u == "G" ? n * 1048576 : u == "M" ? n * 1024 : n) }')
else
  DF=$(ask $'Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/fuse 0 0 52428800 0% /storage/emulated' shell df -k $FILES)
  AVAIL_KB=$(echo "$DF" | awk 'NR == 2 { print $4 + 0 }')
fi
OLD_KB=$(ask 0 shell "cd $LIB && du -sk ${ITEMS[*]} ${ITEMS[*]/#/$INCOMING} seforim.db[0-9][0-9][0-9] $INCOMING""seforim.db[0-9][0-9][0-9] 2>/dev/null; true" | awk '{ s += $1 } END { print s + 0 }')
NEED_KB=$(( TOTAL_KB + 512 * 1024 ))
say "פנוי במכשיר: $(( (AVAIL_KB + OLD_KB) / 1024 )) מ״ב, נדרש: $(( NEED_KB / 1024 )) מ״ב" "free on device: $(( (AVAIL_KB + OLD_KB) / 1024 )) MB (incl. the old library), needed: $(( NEED_KB / 1024 )) MB"
[ $(( AVAIL_KB + OLD_KB )) -ge $NEED_KB ] || die "אין מספיק מקום פנוי במכשיר" "not enough free space on the device"

# Removes an item from the library, or renames it .zayit-old-* when adb cannot empty it (a folder the app made), for
# the app to delete when it next starts.
STAMP=$(date +%s)
clear_item() {
  run shell "cd $LIB && { rm -rf $1 2>/dev/null; [ ! -e $1 ] || mv $1 .zayit-old-$1-$STAMP; }" \
    || die "לא ניתן להסיר את $1 הישן מתיקיית היישום" "could not remove the old $1 from the app's folder"
}

# Bytes in a file or folder, here and on the phone. The phone's sizes are added up here: stock Android 8.x has no awk.
local_bytes() { find "$1" -type f -exec ls -ln {} + | awk '{ s += $5 } END { print s + 0 }'; }
remote_bytes() {
  if [ $OLD = 1 ]; then
    # No stat or find below Android 7.0: ls -lR's sizes, the field before the date (toolbox and toybox alike)
    { ask "-rw-rw---- u0_a1 sdcard_r $2 2026-01-01 00:00 x" shell "ls -lR '$1' 2>/dev/null; true" || echo; } | ls_bytes
  else
    { ask "$2" shell "find '$1' -type f -exec stat -c %s {} + 2>/dev/null; true" || echo 0; } | awk '{ s += $1 } END { print s + 0 }'
  fi
}
ls_bytes() { awk '/^-/ { for (i = 2; i <= NF; i++) if ($i ~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]$/) { s += $(i - 1); break } } END { print s + 0 }'; }

# Streams one file to the phone. `shell -T` (no terminal) passes the bytes untouched, waits until the phone has written
# them and returns its exit code; `exec-in` can drop the end of the stream.
# Runs a command here (printed in --dry-run).
run_local() { if [ $DRY = 1 ]; then echo "    \$ $*"; else "$@"; fi; }

# Below Android 7.0 `adb push` writes the file in place instead, quietly.
send_file() {
  if [ $OLD = 1 ]; then
    if [ $DRY = 1 ]; then echo "    \$ adb${SERIAL:+ -s $SERIAL} push $1 $2"; return 0; fi
    adbs push -q "$1" "$2"
    return
  fi
  if [ $DRY = 1 ]; then echo "    \$ adb${SERIAL:+ -s $SERIAL} shell -T \"cat > '$2'\" < $1"; return 0; fi
  adbs shell -T "cat > '$2'" < "$1"
}

# Shows how much of a large file has arrived while the sender (pid) runs.
show_progress() {
  local path=$1 want=$2 pid=$3
  [ $DRY = 1 ] || [ "$want" -lt $(( 64 * 1048576 )) ] && return 0
  while kill -0 "$pid" 2>/dev/null; do
    printf '\r    %s / %s MB ' $(( $(remote_bytes "$path" 0) / 1048576 )) $(( want / 1048576 ))
    sleep 3
  done
  printf '\r    %s / %s MB \n' $(( $(remote_bytes "$path" 0) / 1048576 )) $(( want / 1048576 ))
}

# --- copy. The old database goes first, with anything the source lacks, so an interrupted copy or a partial
# source never pairs an old database with new indexes (or the reverse).
say 'אל תפתחו את פרדס בטלפון עד שתופיע כאן ההודעה "הושלם"' "don't open Pardes on the phone until this says done"
# With the old database goes any unfinished delta of it (its marker and work folder): never resumed on the new one
for i in seforim.db seforim.db-wal seforim.db-shm seforim.db-journal release_info.txt local_update_version.txt seforim.db.applying seforim.db.applying.tmp delta-cache; do clear_item $i; done
# A split database's chunks (seforim.db001…), whatever their number
run shell "cd $LIB && rm -f seforim.db[0-9][0-9][0-9] $INCOMING""seforim.db[0-9][0-9][0-9]"
for i in "${ITEMS[@]}"; do [ -e "$SRC/$i" ] || clear_item $i; done
for i in "${PRESENT[@]}"; do
  say "מעתיק $i" "copying $i"
  clear_item $i; clear_item $i$PART; clear_item $INCOMING$i  # the old copy goes first, so its space is free for the new one
  WANT=$(local_bytes "$SRC/$i")
  if [ -d "$SRC/$i" ]; then
    run shell "cd $LIB && mkdir -p $(cd "$SRC" && find "$i" -type d | sed "s|^$i|$INCOMING$i|" | tr '\n' ' ')"
    while IFS= read -r f; do
      send_file "$SRC/$f" "$LIB/$INCOMING$i${f#"$i"}" || die "ההעתקה של $f נכשלה" "copying $f failed"
    done < <(cd "$SRC" && find "$i" -type f)
  elif [ "$i" = seforim.db ] && [ $SPLIT = 1 ]; then
    # Chunk k holds bytes k*CHUNK.. of the database; each is streamed on its own and renamed with the others below.
    n=$(( (WANT + CHUNK - 1) / CHUNK ))
    for (( k = 0; k < n; k++ )); do
      c=seforim.db; [ $k -gt 0 ] && c=$(printf 'seforim.db%03d' $k)
      say "חלק $(( k + 1 )) מתוך $n" "chunk $(( k + 1 )) of $n: $c"
      if [ $OLD = 1 ]; then
        # No stream into the phone's shell: each chunk is written here first (2 GiB in TMPDIR), then pushed
        PIECE=${TMPDIR:-/tmp}/pardes-push-chunk.bin
        run_local dd if="$SRC/$i" of="$PIECE" bs=$BLOCK skip=$(( k * CHUNK / BLOCK )) count=$(( CHUNK / BLOCK )) 2>/dev/null \
          || die "ההעתקה של $c נכשלה" "copying $c failed"
        send_file "$PIECE" "$LIB/$INCOMING$c" || { rm -f "$PIECE"; die "ההעתקה של $c נכשלה" "copying $c failed"; }
        rm -f "$PIECE"
      elif [ $DRY = 1 ]; then
        echo "    \$ dd if=$SRC/$i bs=$BLOCK skip=$(( k * CHUNK / BLOCK )) count=$(( CHUNK / BLOCK )) | adb shell -T \"cat > '$LIB/$INCOMING$c'\""
      else
        dd if="$SRC/$i" bs=$BLOCK skip=$(( k * CHUNK / BLOCK )) count=$(( CHUNK / BLOCK )) 2>/dev/null | adbs shell -T "cat > '$LIB/$INCOMING$c'" \
          || die "ההעתקה של $c נכשלה" "copying $c failed"
      fi
    done
    if [ $OLD = 1 ]; then
      GOT=$(ask "-rw-rw---- u0_a1 sdcard_r $WANT 2026-01-01 00:00 x" shell "cd $LIB && ls -l $INCOMING$i $INCOMING""seforim.db[0-9][0-9][0-9] 2>/dev/null; true" | ls_bytes)
    else
      GOT=$(ask "$WANT" shell "cd $LIB && stat -c %s $INCOMING$i $INCOMING""seforim.db[0-9][0-9][0-9] 2>/dev/null; true" | awk '{ s += $1 } END { print s + 0 }')
    fi
    [ "$GOT" = "$WANT" ] || die "ההעתקה של $i לא הושלמה, נסו שוב" "$i did not arrive complete; run the script again"
    # The overflow chunks first, the first chunk (seforim.db) last: the app sees a library only once it is all there.
    run shell "cd $LIB && for c in $INCOMING""seforim.db[0-9][0-9][0-9]; do [ -e \"\$c\" ] && mv \"\$c\" \"\${c#$INCOMING}\"; done; mv $INCOMING$i $i" \
      || die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder"
    continue
  else
    send_file "$SRC/$i" "$LIB/$INCOMING$i" &
    SEND=$!
    show_progress "$LIB/$INCOMING$i" "$WANT" $SEND
    wait $SEND || die "ההעתקה של $i נכשלה" "copying $i failed"
  fi
  [ "$(remote_bytes "$LIB/$INCOMING$i" "$WANT")" = "$WANT" ] || die "ההעתקה של $i לא הושלמה, נסו שוב" "$i did not arrive complete; run the script again"
  # Files 666 / folders 777: the app is not their owner but must read, write (SQLite WAL) and later delete them. On a
  # card the filesystem has no such permissions, and the app owns its folder there. Below Android 11 the phone's shared
  # storage (sdcardfs) refuses chmod and needs none, giving the app its folder's files whoever wrote them: tried only.
  if [ -n "$CARD" ]; then
    run shell "cd $LIB && mv $INCOMING$i $i" || die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder"
  elif [ "$SDK" -lt 30 ]; then
    run shell "cd $LIB && mv $INCOMING$i $i" || die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder"
    run shell "cd $LIB && { find $i -type d -exec chmod 777 {} +; find $i -type f -exec chmod 666 {} +; } 2>/dev/null; true"
  else
    run shell "cd $LIB && mv $INCOMING$i $i && find $i -type d -exec chmod 777 {} + && find $i -type f -exec chmod 666 {} +" \
      || die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder"
  fi
done
if [ -n "$CARD" ]; then
  # One library: the app would keep using the one it chose before, in the phone's storage.
  if [ "$(ask "" shell "[ -f $PHONE_LIB/seforim.db ] && echo yes")" = yes ]; then
    say "מסיר את הספרייה שבזיכרון הטלפון: פרדס ישתמש בזו שבכרטיס" "removing the library in the phone's storage: Pardes uses the card's"
    run shell "cd $PHONE_LIB && rm -rf seforim.db seforim.db-wal seforim.db-shm seforim.db-journal seforim.db[0-9][0-9][0-9] ${ITEMS[*]} local_update_version.txt seforim.db.applying delta-cache 2>/dev/null; for i in seforim.db ${ITEMS[*]} delta-cache; do [ ! -e \$i ] || mv \$i .zayit-old-\$i-$STAMP; done; true"
  fi
elif [ $OLD = 1 ]; then
  # Pardes for Android 4.4 to 7.1 also keeps one library, and opens the folder it opened last: a library it used on a
  # memory card would leave this copy unused. It has no setting to switch.
  for v in $(card_volumes); do
    CARD_LIB=/storage/$v/Android/data/$PKG/files/library
    [ "$(ask "" shell "[ -f $CARD_LIB/seforim.db ] && echo yes")" = yes ] || continue
    say "מסיר את הספרייה שבכרטיס הזיכרון $v: פרדס ישתמש בזו שבזיכרון הטלפון" "removing the library on the memory card $v: Pardes uses the phone's"
    run shell "cd $CARD_LIB && rm -rf seforim.db seforim.db-wal seforim.db-shm seforim.db-journal seforim.db[0-9][0-9][0-9] ${ITEMS[*]} local_update_version.txt seforim.db.applying delta-cache 2>/dev/null; true"
    if [ "$(ask "" shell "[ -f $CARD_LIB/seforim.db ] && echo yes")" = yes ]; then
      say "לא ניתן להסיר אותה מכאן. מחקו את התיקייה $CARD_LIB (בטלפון או מהמחשב), אחרת פרדס עלול להמשיך לפתוח אותה" \
        "could not remove it from here: delete the folder $CARD_LIB (on the phone or from a computer), or Pardes may keep opening it"
    fi
  done
elif [ -n "$(ask "" shell "ls /storage/*/Android/data/$PKG/files/library/seforim.db 2>/dev/null | grep -v emulated${PRIMARY:+ | grep -v '^$PRIMARY/'}; true")" ]; then
  say "שימו לב: גם בכרטיס הזיכרון יש ספרייה. אם פרדס ממשיך להשתמש בה, עברו לספרייה שבטלפון בהגדרות › ניהול נתונים › מיקום הספרייה" \
    "note: the memory card has a library too; if Pardes keeps using it, switch in Settings › ניהול נתונים › מיקום הספרייה"
fi
if [ $NOTES = 1 ]; then
  say "מעתיק הערות והדגשות (יחליפו את אלה שבטלפון)" "copying notes and highlights (they replace the phone's)"
  # The app imports them from the phone's storage only, also when its library is on the card.
  IMPORT=$PHONE_FILES/import
  run shell "mkdir -p $IMPORT; chmod 777 $IMPORT 2>/dev/null; rm -f $IMPORT/user_settings.db*"
  send_file "$NOTES_DB" "$IMPORT/user_settings.db$PART" || die "ההעתקה של ההערות נכשלה" "copying the notes failed"
  if [ "$SDK" -lt 30 ]; then
    run shell "cd $IMPORT && mv user_settings.db$PART user_settings.db" \
      || die "לא ניתן להעביר את ההערות לתיקיית היישום" "could not move the notes into the app's folder"
    run shell "chmod 666 $IMPORT/user_settings.db 2>/dev/null; true"
  else
    run shell "cd $IMPORT && mv user_settings.db$PART user_settings.db && chmod 666 user_settings.db" \
      || die "לא ניתן להעביר את ההערות לתיקיית היישום" "could not move the notes into the app's folder"
  fi
fi
if [ -n "$(ask "" shell "ls -a $LIB | grep '^\.zayit-old-'; true")" ]; then
  if [ $OLD = 1 ]; then
    # Pardes for Android 4.4 to 7.1 leaves them
    say "עותקים ישנים שהיישום יצר בעצמו שונו לשם ‎.zayit-old-*‎. מחקו אותם מהתיקייה $LIB כדי לפנות את מקומם" \
      "old copies the app made itself were renamed .zayit-old-*; delete them from $LIB to free their space"
  else
    say "עותקים ישנים שהיישום יצר בעצמו ימחקו כשיפתח" "old copies the app made itself were renamed .zayit-old-*; it deletes them when it next opens"
  fi
fi

say "הושלם. פתחו את פרדס בטלפון; אם יופיע מדריך ההגדרה, הוא ימצא את הספרייה אחרי אישור הרישיון" \
  "done. Open Pardes on the phone; if its setup guide appears, it finds the library after the licence step"
run shell ls -l $LIB
