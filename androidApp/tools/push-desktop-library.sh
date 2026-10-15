#!/usr/bin/env bash
# Copies the library of desktop Zayit to Pardes (an Android app based on Zayit) over adb, so the phone needs no 5.5 GB download.
# העתקת הספרייה של זית למחשב אל פרדס בטלפון דרך adb, ללא הורדה של 5.5 ג״ב בטלפון.
#
#   push-desktop-library.sh [--source DIR] [-s SERIAL] [--with-notes] [--dry-run]
#
#   --source DIR   desktop library folder (default: $SEFORIMAPP_DATABASE_PATH's folder, else the desktop install:
#                  Linux ${XDG_DATA_HOME:-~/.local/share}/io.github.kdroidfilter.seforimapp/databases,
#                  macOS ~/Library/Application Support/io.github.kdroidfilter.seforimapp/databases)
#   -s SERIAL      device to use when several are connected (see `adb devices`)
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
SRC=""; SERIAL=""; NOTES=0; DRY=0
say() { printf '==> %s\n    %s\n' "$2" "$1"; }
die() { printf 'ERROR: %s\n       %s\n' "$2" "$1" >&2; exit 1; }
while [ $# -gt 0 ]; do
  case "$1" in
    --source) SRC="${2:?}"; shift 2 ;;
    -s) SERIAL="${2:?}"; shift 2 ;;
    --with-notes) NOTES=1; shift ;;
    --dry-run) DRY=1; shift ;;
    -h|--help) sed -n 2,21p "$0"; exit 0 ;;
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
[[ "$PM" == package:* ]] || die "פרדס אינו מותקן במכשיר" "Pardes ($PKG) is not installed on the device"

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
DF=$(ask $'Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/fuse 0 0 52428800 0% /storage/emulated' shell df -k $FILES)
AVAIL_KB=$(echo "$DF" | awk 'NR == 2 { print $4 + 0 }')
OLD_KB=$(ask 0 shell "cd $LIB && du -sk ${ITEMS[*]} 2>/dev/null; true" | awk '{ s += $1 } END { print s + 0 }')
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

# Bytes in a file or folder, here and on the phone.
local_bytes() { find "$1" -type f -exec ls -ln {} + | awk '{ s += $5 } END { print s + 0 }'; }
remote_bytes() { ask "$2" shell "find '$1' -type f -exec stat -c %s {} + 2>/dev/null | awk '{ s += \$1 } END { print s + 0 }'" || echo 0; }

# Streams one file to the phone. `shell -T` (no terminal) passes the bytes untouched, waits until the phone has written
# them and returns its exit code; `exec-in` can drop the end of the stream.
send_file() {
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
for i in seforim.db seforim.db-wal seforim.db-shm seforim.db-journal release_info.txt seforim.db.applying seforim.db.applying.tmp delta-cache; do clear_item $i; done
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
  else
    send_file "$SRC/$i" "$LIB/$INCOMING$i" &
    SEND=$!
    show_progress "$LIB/$INCOMING$i" "$WANT" $SEND
    wait $SEND || die "ההעתקה של $i נכשלה" "copying $i failed"
  fi
  [ "$(remote_bytes "$LIB/$INCOMING$i" "$WANT")" = "$WANT" ] || die "ההעתקה של $i לא הושלמה, נסו שוב" "$i did not arrive complete; run the script again"
  # Files 666 / folders 777: the app is not their owner but must read, write (SQLite WAL) and later delete them.
  run shell "cd $LIB && mv $INCOMING$i $i && find $i -type d -exec chmod 777 {} + && find $i -type f -exec chmod 666 {} +" \
    || die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder"
done
if [ $NOTES = 1 ]; then
  say "מעתיק הערות והדגשות (יחליפו את אלה שבטלפון)" "copying notes and highlights (they replace the phone's)"
  IMPORT=$FILES/import
  run shell "mkdir -p $IMPORT; chmod 777 $IMPORT 2>/dev/null; rm -f $IMPORT/user_settings.db*"
  send_file "$NOTES_DB" "$IMPORT/user_settings.db$PART" || die "ההעתקה של ההערות נכשלה" "copying the notes failed"
  run shell "cd $IMPORT && mv user_settings.db$PART user_settings.db && chmod 666 user_settings.db" \
    || die "לא ניתן להעביר את ההערות לתיקיית היישום" "could not move the notes into the app's folder"
fi
if [ -n "$(ask "" shell "ls -a $LIB | grep '^\.zayit-old-'; true")" ]; then
  say "עותקים ישנים שהיישום יצר בעצמו ימחקו כשיפתח" "old copies the app made itself were renamed .zayit-old-*; it deletes them when it next opens"
fi

say "הושלם. פתחו את פרדס בטלפון; אם יופיע מסך ההתקנה, בחרו \"שימוש בספרייה הקיימת\"" \
  "done. Open Pardes on the phone; if the setup screens appear, choose \"use existing library\""
run shell ls -l $LIB
