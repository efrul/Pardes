<#
Copies the library of desktop Zayit to Pardes (an Android app based on Zayit) over adb, so the phone needs no 5.5 GB download.
העתקת הספרייה של זית למחשב אל פרדס בטלפון דרך adb, ללא הורדה של 5.5 ג״ב בטלפון.

  Double-click push-desktop-library.cmd next to this file, or:
  powershell -ExecutionPolicy Bypass -File push-desktop-library.ps1 [-Source DIR] [-Serial SERIAL] [-SdCard [-CardUuid UUID]] [-WithNotes] [-DryRun]

  -Source DIR      desktop library folder (default: the folder of %SEFORIMAPP_DATABASE_PATH%, else the desktop
                   install, %APPDATA%\io.github.kdroidfilter.seforimapp\databases)
  -Serial SERIAL   device to use when several are connected (see `adb devices`)
  -SdCard          copy to the memory card instead of the phone's storage (-CardUuid picks one of several cards):
                   /storage/<UUID>/Android/data/<app>/files/library/. On a FAT32 card seforim.db goes in chunks of
                   2 GiB less 64 KiB (seforim.db, seforim.db001...), which the app reads as one database. Once the copy
                   is complete, a library in the phone's storage is removed: the app keeps one library. Needs
                   Pardes 0.3.0 or later (versionCode 2), the first that opens a library on the card.
  -WithNotes       also copy notes and highlights (settings\user_settings.db); they replace the phone's own
  -DryRun          print the adb commands instead of running them

Copies seforim.db, the search indexes seforim.db.lucene\ and seforim.db.lookup.lucene\, lexical.db, catalog.pb and
release_info.txt (~12.2 GB) into /sdcard/Android/data/<app>/files/library/, and the semantic search's pack when the
desktop library has it (pardes-model\ and pardes.vec.lucene\, ~2.8 GB, both or neither: without them the phone's pack
is left as it is, and the app checks that it belongs to the library). Needs USB debugging on the phone, the app
installed, and Zayit closed on the computer. No root. Without root, `adb push` cannot write into another app's folder
(its fchown fails there), and adb on Windows reads its standard input as text, so a stream through `adb shell -T`
stops at the first 0x1A byte. So each file is pushed to /data/local/tmp/zayit-push and copied into place by the
phone's shell, one file at a time, a file over 2 GiB in pieces of that size (written one by one in this computer's
TEMP folder): the phone needs room for the library plus 2 GiB (with -SdCard: the card for the library, the phone
for 2 GiB). An item is
written as .zayit-incoming-<name> and renamed when complete (the app deletes <name>.part when it opens). The files belong to adb's `shell` user, so they are made writable
for the app (SQLite opens the database read-write). Folders the app made itself (its own download) cannot be emptied
by adb: they are renamed .zayit-old-* and the app deletes them when it next starts. Linux/macOS: push-desktop-library.sh.
#>
param(
    [string]$Source = '', [string]$Serial = '', [switch]$SdCard, [string]$CardUuid = '', [switch]$WithNotes, [switch]$DryRun, [switch]$Help,
    # For tests: chunks on any card, and their size (a multiple of 64 KiB; the app reads any such size).
    [switch]$Split, [long]$ChunkBytes = 2147418112
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if ($Help) { foreach ($h in (Get-Content -LiteralPath $PSCommandPath -Encoding UTF8 | Select-Object -Skip 1)) { if ($h -eq '#>') { break }; $h }; exit 0 }
$AppId = 'io.github.kdroidfilter.seforimapp'
$Pkg = 'io.github.efrul.pardes'
$Files = "/sdcard/Android/data/$Pkg/files"
$Lib = "$Files/library"
$Part = '.part'
# Name of an item while it is being copied. Not <name>.part: the app deletes those as a broken download when it opens.
$Incoming = '.zayit-incoming-'
$Stage = '/data/local/tmp/zayit-push'
$Staging = $false
# Indexes and small files first, the database last: the app treats the library as installed once seforim.db is there.
$Items = 'seforim.db.lookup.lucene', 'seforim.db.lucene', 'lexical.db', 'catalog.pb', 'release_info.txt', 'seforim.db'
# Pardes's semantic pack (SEMANTIC_SEARCH.md): its two folders always go together, before the database. What a pack
# downloaded in the app left for its next start is dropped when one is copied.
$Pack = 'pardes-model', 'pardes.vec.lucene'
$PackStaged = '.pardes-semantic-staged'
function Say($he, $en) { Write-Host "==> $en"; Write-Host "    $he" }
function Die($he, $en) {
    Write-Host "ERROR: $en" -ForegroundColor Red; Write-Host "       $he" -ForegroundColor Red
    # A failed copy must not leave up to 8 GB behind in the staging folder.
    if ($script:Staging) { try { $null = & $Adb (Full @('shell', "rm -rf $Stage")) } catch { } }
    exit 1
}
if ($args.Count) { Die "אפשרות לא מוכרת: $args (האפשרויות: -Source, -Serial, -SdCard, -CardUuid, -WithNotes, -DryRun)" "unknown option(s) $args; the options are -Source, -Serial, -SdCard, -CardUuid, -WithNotes, -DryRun, -Help" }
if ($ChunkBytes -le 0 -or $ChunkBytes % 65536) { Die 'גודל החלק חייב להיות כפולה של 65536' '-ChunkBytes must be a multiple of 65536' }
function SizeOf($p) {
    $item = Get-Item -LiteralPath $p
    if (-not $item.PSIsContainer) { return [long]$item.Length }
    return [long](Get-ChildItem -LiteralPath $p -Recurse -File -Force | Measure-Object Length -Sum).Sum
}

# --- desktop library (FileKit.databasesDir of the desktop app; the desktop installer always unpacks there)
$Default = if ($env:APPDATA) { Join-Path $env:APPDATA "$AppId\databases" }
           elseif ($IsMacOS) { "$HOME/Library/Application Support/$AppId/databases" }
           else { $(if ($env:XDG_DATA_HOME) { $env:XDG_DATA_HOME } else { "$HOME/.local/share" }) + "/$AppId/databases" }
if (-not $Source -and $env:SEFORIMAPP_DATABASE_PATH) {
    $Source = $env:SEFORIMAPP_DATABASE_PATH
    if (Test-Path -LiteralPath $Source -PathType Leaf) { $Source = Split-Path -Parent $Source }
}
if (-not $Source) { $Source = $Default }
$Db = Join-Path $Source 'seforim.db'
if (-not (Test-Path -LiteralPath $Db -PathType Leaf)) { Die "לא נמצא seforim.db בתיקייה $Source. התקינו את זית במחשב או ציינו -Source" "no seforim.db in $Source; install desktop Zayit or pass -Source DIR" }
# Absolute, because adb push resolves a relative path against its own working folder, not PowerShell's location.
$Source = (Get-Item -LiteralPath $Source -Force).FullName
$Db = Join-Path $Source 'seforim.db'
$NotesDb = Join-Path $Source 'settings\user_settings.db'
if (-not (Test-Path -LiteralPath $NotesDb)) { $NotesDb = Join-Path $Default 'settings\user_settings.db' }
# The notes only matter with -WithNotes: a desktop Zayit left open keeps their WAL, which must not stop a library copy
# (a small library built from chosen books, say, is a folder of its own).
$Wals = @("$Db-wal")
if ($WithNotes) { $Wals += "$NotesDb-wal" }
foreach ($wal in $Wals) {
    if ((Test-Path -LiteralPath $wal) -and (Get-Item -LiteralPath $wal).Length -gt 0) { Die "סגרו את זית במחשב ונסו שוב ($wal אינו ריק)" "close Zayit on the computer first ($wal is not empty)" }
}
foreach ($d in 'seforim.db.lucene', 'seforim.db.lookup.lucene') {
    if (-not (Test-Path -LiteralPath (Join-Path $Source $d))) { Say "חסר $d`: החיפוש לא יעבוד בטלפון" "warning: $d missing, search will not work on the phone" }
}
if ($WithNotes -and -not (Test-Path -LiteralPath $NotesDb)) { Die "לא נמצא קובץ ההערות $NotesDb" "no notes database at $NotesDb" }
$PackHere = @($Pack | Where-Object { Test-Path -LiteralPath (Join-Path $Source $_) -PathType Container })
$HasPack = $PackHere.Count -eq $Pack.Count
if ($PackHere.Count -and -not $HasPack) { Say 'בתיקייה יש רק אחת משתי התיקיות של החיפוש לפי משמעות, ולכן החבילה לא תועתק' "warning: only one of the semantic pack's two folders ($Pack) is in $Source`: the pack is not copied" }
# The semantic pack before the database, as the indexes
$Present = @($Items | Where-Object { Test-Path -LiteralPath (Join-Path $Source $_) } | ForEach-Object { if ($_ -eq 'seforim.db' -and $HasPack) { $Pack }; $_ })
$TotalKb = [long](($Present | ForEach-Object { SizeOf (Join-Path $Source $_) } | Measure-Object -Sum).Sum / 1024)
# Each file passes through the staging folder on the phone, a large one in pieces: the largest piece needs room twice.
$LargestKb = [long]([Math]::Min(($Present | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $Source $_) -Recurse -File -Force } | Measure-Object Length -Maximum).Maximum, $ChunkBytes) / 1024)
Say "מקור: $Source ($([long]($TotalKb / 1024)) מ״ב)" "source: $Source ($([long]($TotalKb / 1024)) MB): $Present"

# --- adb and the device
$Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
# @(...) keeps a single match an array: otherwise += below would glue the paths into one string.
$candidates = @(@($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' })) |
    Where-Object { $_ } | ForEach-Object { Join-Path $_ 'platform-tools\adb.exe' })
$candidates += (Join-Path $PSScriptRoot 'adb.exe'), (Join-Path $PSScriptRoot 'platform-tools\adb.exe')
if (-not $Adb) { $Adb = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1 }
# Google's Platform-Tools unzipped anywhere in this folder (Explorer's "Extract All" nests it one level deeper).
if (-not $Adb -and $PSScriptRoot) {
    $Adb = Get-ChildItem -LiteralPath $PSScriptRoot -Filter adb.exe -Recurse -Depth 3 -File -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$PlatformTools = 'https://developer.android.com/tools/releases/platform-tools'
if (-not $Adb) {
    if ($DryRun) { $Adb = 'adb' } else {
        Die "adb לא נמצא. הורידו את Android SDK Platform-Tools מ-$PlatformTools ופתחו את קובץ ה-zip בתיקייה הזו: $PSScriptRoot" "adb not found. Download Android SDK Platform-Tools from $PlatformTools and unzip it into this folder: $PSScriptRoot (or put adb on PATH)"
    }
}
function Full([string[]]$a) { if ($Serial) { @('-s', $Serial) + $a } else { $a } }
# Run: changes the device (printed with -DryRun). Ask: reads from it ($fake is the -DryRun answer).
# A remote command holds no double quotes: Windows PowerShell 5.1 passes them to adb.exe unescaped, which drops them.
function Run([string[]]$a) {
    $f = Full $a
    if ($DryRun) { Write-Host "    `$ adb $f"; return $true }
    & $Adb @f | Out-Host
    return $LASTEXITCODE -eq 0
}
function Ask([string]$fake, [string[]]$a) {
    $f = Full $a
    if ($DryRun) { Write-Host "    `$ adb $f"; return $fake }
    return ((& $Adb @f) -join "`n") -replace "`r", ''
}
if (-not $Serial) {
    if ($DryRun) { $Serial = 'DEVICE' } else {
        $devices = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        if ($devices.Count -eq 0) { Die 'לא נמצא מכשיר. חברו את הטלפון, הפעילו ניפוי באגים ב-USB ואשרו את המחשב בטלפון' 'no device; connect the phone, enable USB debugging and accept this computer on the phone' }
        if ($devices.Count -gt 1) { Die "מחוברים כמה מכשירים, בחרו אחד עם -Serial: $devices" "several devices, pick one with -Serial: $devices" }
        $Serial = $devices[0]
    }
}
Say "מכשיר: $Serial" "device: $Serial"
if ((Ask "package:/data/app/$Pkg/base.apk" @('shell', 'pm', 'path', $Pkg)) -notmatch '^package:') { Die 'פרדס אינו מותקן במכשיר' "Pardes ($Pkg) is not installed on the device" }
# Android 11's adb shell is not in the ext_data_rw group (Android 12 added it): Android/data/<app>/ is out of its reach.
# Android 8 to 10 reach it (their sdcardfs gives the shell sdcard_rw), and take no chmod there (see the copy below).
$sdk = 0; [void][int]::TryParse(((Ask '33' @('shell', 'getprop', 'ro.build.version.sdk')) -replace '\D', ''), [ref]$sdk)
$SdcardFs = $sdk -gt 0 -and $sdk -lt 30
if ($sdk -eq 30 -and (Ask 'yes' @('shell', 'ls /sdcard/Android/data >/dev/null 2>&1 && echo yes')) -ne 'yes') {
    Die 'באנדרואיד 11 כלי ההעברה (adb) אינו יכול לגשת לתיקייה של פרדס בטלפון; זה אפשרי מאנדרואיד 12. במקום זאת הורידו מעמוד ההפצה של הספרייה את הקבצים ‎.part ואת ‎.manifest.json, העתיקו אותם לתיקיית Download בטלפון ובחרו אותם בפרדס: מדריך ההגדרה ← התקנה לא-מקוונת' "Android 11: adb cannot reach Pardes's folder on the phone (it can from Android 12). Instead, download the library's .part and .manifest.json files from its release page, copy them to the phone's Download folder and pick them in Pardes: setup guide, offline install"
}
# Adds up the numbers an Ask returned (the phone's file sizes): stock Android 8.x has no awk to do it there.
function SumOf([string]$out) {
    [long]$sum = 0
    foreach ($n in ("$out" -split '\s+')) { if ($n -match '^\d+$') { $sum += [long]$n } }
    return $sum
}

# --- the memory card (-SdCard): its app folder, and its filesystem (FAT32 takes no file of 4 GB)
$PhoneFiles = $Files
$PhoneLib = $Lib
$Fs = ''
if ($SdCard -or $CardUuid) {
    # An older Pardes finds no library on the card, and the phone's copy is removed at the end.
    $vc = 0
    if ((Ask '    versionCode=2 minSdk=33 targetSdk=36' @('shell', 'dumpsys', 'package', $Pkg)) -match 'versionCode=(\d+)') { $vc = [int]$Matches[1] }
    if ($vc -lt 2) { Die 'כדי להעתיק לכרטיס הזיכרון צריך פרדס 0.3.0 ומעלה. עדכנו את פרדס בטלפון ונסו שוב.' "copying to the memory card needs Pardes 0.3.0 or later (versionCode 2, installed $vc): update the app first" }
    $vols = @((Ask 'public:179,1 mounted 1234-ABCD' @('shell', 'sm', 'list-volumes', 'public')) -split "`n" |
        ForEach-Object { $f = $_.Trim() -split '\s+'; if ($f.Count -ge 3 -and $f[1] -eq 'mounted' -and $f[2] -ne 'null') { $f[2] } })
    if (-not $CardUuid) {
        if ($vols.Count -eq 0) { Die 'לא נמצא כרטיס זיכרון מחובר' 'no memory card is mounted' }
        if ($vols.Count -gt 1) { Die "יש כמה כרטיסים, בחרו אחד עם -CardUuid: $vols" "several cards, pick one with -CardUuid: $vols" }
        $CardUuid = $vols[0]
    }
    if ($vols -notcontains $CardUuid) { Die "הכרטיס $CardUuid אינו מחובר" "card $CardUuid is not mounted" }
    $Files = "/storage/$CardUuid/Android/data/$Pkg/files"
    $Lib = "$Files/library"
    # Read here, not on the phone: stock Android 8.x has no awk.
    foreach ($m in ((Ask "/dev/block/vold/public:179,1 /mnt/media_rw/$CardUuid vfat rw 0 0" @('shell', 'cat', '/proc/mounts')) -split "`n")) {
        $f = $m.Trim() -split '\s+'
        if ($f.Count -ge 3 -and $f[1] -eq "/mnt/media_rw/$CardUuid") { $Fs = $f[2]; break }
    }
    Say "כרטיס זיכרון: $CardUuid ($Fs)" "memory card: $CardUuid ($Fs)"
}
$SplitDb = ($SdCard -or $CardUuid) -and ($Split -or ($Fs -eq 'vfat' -and (Get-Item -LiteralPath $Db).Length -gt 4294967295))
if ($SplitDb) { Say 'הכרטיס מפורמט ב-FAT32: seforim.db יועתק בחלקים' 'FAT32 card: seforim.db is copied in chunks (seforim.db, seforim.db001...)' }
# FAT32 takes no file of 4 GiB or more, and only the database is split: the pack's files must each be smaller
# (pack 2's largest, its vectors, is 2.2 GB).
if (($SdCard -or $CardUuid) -and $Fs -eq 'vfat' -and $HasPack) {
    $big = $Pack | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $Source $_) -Recurse -File -Force } | Where-Object { $_.Length -gt 4294967295 } | Select-Object -First 1
    if ($big) { Die "בחבילה של החיפוש לפי משמעות יש קובץ של 4 ג״ב או יותר ($($big.FullName)), וכרטיס FAT32 אינו יכול להכיל אותו" "the semantic pack has a file of 4 GB or more ($($big.FullName)), which a FAT32 card cannot hold" }
}

# --- the app's folder exists only once it has run
$probe = @('shell', "[ -d $Files ] && echo yes")
if ((Ask 'yes' $probe) -ne 'yes') {
    Say 'מפעיל את היישום פעם אחת ליצירת התיקייה שלו' 'starting the app once so it creates its folder'
    $null = Run @('shell', 'am', 'start', '-n', "$Pkg/io.github.kdroidfilter.seforimapp.android.MainActivity")
    for ($n = 0; $n -lt 30 -and (Ask 'yes' $probe) -ne 'yes'; $n++) { Start-Sleep -Seconds 2 }
    if ((Ask 'yes' $probe) -ne 'yes') { Die "התיקייה $Files לא נוצרה. פתחו את היישום ונסו שוב" "$Files was not created; open the app once and retry" }
}
$null = Run @('shell', 'am', 'force-stop', $Pkg)
# A shell redirection, not touch: touch also sets the times, which fails on a leftover probe owned by the app.
if (-not (Run @('shell', "mkdir -p $Lib; chmod 777 $Lib 2>/dev/null; rm -f $Lib/.zayit-push; : > $Lib/.zayit-push && rm $Lib/.zayit-push"))) { Die "אין הרשאת כתיבה ל-$Lib. אם הטלפון הופעל מחדש, בטלו את נעילת המסך ונסו שוב" "cannot write to $Lib (if the phone just restarted, unlock it and retry)" }

# --- free space: what is free now plus what the old copies take. A stage left by an interrupted run (stopped while
# the phone copied a file into place) is removed first, so its space counts as free.
$null = Run @('shell', "rm -rf $Stage")
$df = Ask "Filesystem 1K-blocks Used Available Use% Mounted on`n/dev/fuse 0 0 52428800 0% /storage/emulated" @('shell', 'df', '-k', $Files)
$AvailKb = [long](((($df -split "`n")[1]).Trim() -split '\s+')[3])
# The phone's pack is replaced, so counts as free, only when one is copied
$OldPack = if ($HasPack) { "$Pack $($Pack | ForEach-Object { "$Incoming$_" }) $PackStaged" } else { '' }
$du = Ask '0' @('shell', "cd $Lib && du -sk $Items $($Items | ForEach-Object { "$Incoming$_" }) $OldPack seforim.db[0-9][0-9][0-9] $($Incoming)seforim.db[0-9][0-9][0-9] 2>/dev/null; true")
$OldKb = [long](($du -split "`n" | Where-Object { $_ -match '^\d+' } | ForEach-Object { [long](($_ -split '\s+')[0]) } | Measure-Object -Sum).Sum)
$OnCard = $SdCard -or $CardUuid
$NeedKb = $TotalKb + 512 * 1024
# The stage is on the phone's /data, which /sdcard shares; a card does not.
if (-not $OnCard) { $NeedKb += $LargestKb }
Say "פנוי במכשיר: $([long](($AvailKb + $OldKb) / 1024)) מ״ב, נדרש: $([long]($NeedKb / 1024)) מ״ב" "free on device: $([long](($AvailKb + $OldKb) / 1024)) MB (incl. the old library), needed: $([long]($NeedKb / 1024)) MB"
if ($AvailKb + $OldKb -lt $NeedKb) { Die 'אין מספיק מקום פנוי במכשיר' 'not enough free space on the device' }
if ($OnCard) {
    $sdf = Ask "Filesystem 1K-blocks Used Available Use% Mounted on`n/dev/block/dm-0 0 0 52428800 0% /data" @('shell', 'df', '-k', '/data/local/tmp')
    $StageAvailKb = [long](((($sdf -split "`n")[1]).Trim() -split '\s+')[3])
    $StageNeedKb = $LargestKb + 64 * 1024
    if ($StageAvailKb -lt $StageNeedKb) { Die "אין מספיק מקום פנוי בזיכרון הטלפון להעברה: נדרשים $([long]($StageNeedKb / 1024)) מ״ב" "not enough free space in the phone's internal storage for the transfer stage (/data/local/tmp): $([long]($StageNeedKb / 1024)) MB needed" }
}

# Removes an item from the library, or renames it .zayit-old-* when adb cannot empty it (a folder the app made), for
# the app to delete when it next starts.
$Stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
function ClearItem($i) {
    if (-not (Run @('shell', "cd $Lib && { rm -rf $i 2>/dev/null; [ ! -e $i ] || mv $i .zayit-old-$i-$Stamp; }"))) { Die "לא ניתן להסיר את $i הישן מתיקיית היישום" "could not remove the old $i from the app's folder" }
}

# Bytes in a file or folder on the phone.
function RemoteBytes([string]$path, [long]$fake) {
    return SumOf (Ask "$fake" @('shell', "find '$path' -type f -exec stat -c %s {} + 2>/dev/null; true"))
}

# Writes $length bytes of $src from $offset to $dst (adb push takes whole files).
function WritePiece([string]$src, [long]$offset, [long]$length, [string]$dst) {
    $in = [IO.File]::OpenRead($src)
    $out = [IO.File]::Create($dst)
    try {
        $null = $in.Seek($offset, [IO.SeekOrigin]::Begin)
        $buf = New-Object byte[] (4MB)
        $left = $length
        while ($left -gt 0) {
            $n = $in.Read($buf, 0, [int][Math]::Min([long]$buf.Length, $left))
            if ($n -le 0) { break }
            $out.Write($buf, 0, $n)
            $left -= $n
        }
    } finally { $in.Dispose(); $out.Dispose() }
}

# Sends a file larger than $ChunkBytes in pieces of that size, each through this computer's TEMP folder and the
# phone's stage: appended into one file, or (a database for a FAT32 card) each piece a chunk of its own, $remote
# then $remote + 001, 002...
function SendPieces([string]$local, [string]$remote, [bool]$chunks) {
    $size = (Get-Item -LiteralPath $local -Force).Length
    $n = [long][Math]::Ceiling($size / $ChunkBytes)
    $piece = Join-Path ([IO.Path]::GetTempPath()) 'pardes-push-piece.bin'
    try {
        for ($k = 0; $k -lt $n; $k++) {
            Write-Host "    piece $($k + 1) of $n"
            $target = if ($chunks -and $k -gt 0) { $remote + ('{0:D3}' -f $k) } else { $remote }
            $op = if ($chunks -or $k -eq 0) { '>' } else { '>>' }
            if ($DryRun) { Write-Host "    (bytes $($k * $ChunkBytes)..: $piece -> $target)" } else {
                WritePiece $local ($k * $ChunkBytes) ([Math]::Min($ChunkBytes, $size - $k * $ChunkBytes)) $piece
            }
            if (-not (SendFile $piece $target $op)) { return $false }
        }
    } finally { if (Test-Path -LiteralPath $piece) { Remove-Item -LiteralPath $piece -Force } }
    return $true
}

# Sends one file to the phone. adb on Windows reads its standard input as text, so a stream through `adb shell -T`
# (or exec-in) ends at the first 0x1A byte; adb push reads the file itself. It cannot write into the app's folder, so
# the file lands in $Stage and the phone's shell copies it into place (separate mounts: a move would copy too).
function SendFile([string]$local, [string]$remote, [string]$op = '>') {
    $tmp = "$Stage/file"
    # A piece that -DryRun did not write counts as large.
    $big = if (Test-Path -LiteralPath $local) { (Get-Item -LiteralPath $local -Force).Length -ge 64MB } else { $true }
    # Large files show adb's own progress line; small ones (the index files) stay quiet.
    $argv = Full $(if ($big) { @('push', $local, $tmp) } else { @('push', '-q', $local, $tmp) })
    if ($DryRun) { Write-Host "    `$ adb $argv" } else {
        # Started without redirection, adb writes its progress straight to this console.
        $psi = New-Object Diagnostics.ProcessStartInfo
        $psi.FileName = $Adb
        $psi.Arguments = ($argv | ForEach-Object { if ($_ -match '[\s''<>|&]') { '"' + $_ + '"' } else { $_ } }) -join ' '
        $psi.UseShellExecute = $false
        $p = [Diagnostics.Process]::Start($psi)
        $p.WaitForExit()
        if ($p.ExitCode -ne 0) { return $false }
    }
    if ($big) { Write-Host '    placing it in the app''s folder' }
    return Run @('shell', "cat $tmp $op '$remote' && rm -f $tmp")
}

# --- copy. The old database goes first, with anything the source lacks, so an interrupted copy or a partial
# source never pairs an old database with new indexes (or the reverse).
Say 'אל תפתחו את פרדס בטלפון עד שתופיע כאן ההודעה "הושלם"' "don't open Pardes on the phone until this says done"
$null = Run @('shell', "rm -rf $Stage; mkdir -p $Stage")
$Staging = $true
try {
    # With the old database goes any unfinished delta of it (its marker and work folder): never resumed on the new one
    foreach ($i in @('seforim.db', 'seforim.db-wal', 'seforim.db-shm', 'seforim.db-journal', 'release_info.txt', 'local_update_version.txt', 'seforim.db.applying', 'seforim.db.applying.tmp', 'delta-cache') + @($Items | Where-Object { $Present -notcontains $_ })) { ClearItem $i }
    # A split database's chunks (seforim.db001...), whatever their number
    $null = Run @('shell', "cd $Lib && rm -f seforim.db[0-9][0-9][0-9] $($Incoming)seforim.db[0-9][0-9][0-9]")
    # A pack the app staged for its next start would replace the one copied now
    if ($HasPack) { ClearItem $PackStaged }
    foreach ($i in $Present) {
        Say "מעתיק $i" "copying $i"
        ClearItem $i; ClearItem "$i$Part"; ClearItem "$Incoming$i"  # the old copy goes first, so its space is free for the new one
        $local = Join-Path $Source $i
        $want = SizeOf $local
        if (Test-Path -LiteralPath $local -PathType Container) {
            $root = (Get-Item -LiteralPath $local).FullName.TrimEnd('\', '/')
            $dirs = @("$Incoming$i") + @(Get-ChildItem -LiteralPath $local -Recurse -Directory -Force | ForEach-Object { "$Incoming$i/" + $_.FullName.Substring($root.Length + 1).Replace('\', '/') })
            $null = Run @('shell', "cd $Lib && mkdir -p $dirs")
            foreach ($f in Get-ChildItem -LiteralPath $local -Recurse -File -Force) {
                $rel = $f.FullName.Substring($root.Length + 1).Replace('\', '/')
                if (-not (SendFile $f.FullName "$Lib/$Incoming$i/$rel")) { Die "ההעתקה של $i/$rel נכשלה" "copying $i/$rel failed" }
            }
        } elseif ($i -eq 'seforim.db' -and $SplitDb) {
            if (-not (SendPieces $local "$Lib/$Incoming$i" $true)) { Die "ההעתקה של $i נכשלה" "copying $i failed" }
            $got = SumOf (Ask "$want" @('shell', "cd $Lib && stat -c %s $Incoming$i $($Incoming)seforim.db[0-9][0-9][0-9] 2>/dev/null; true"))
            if ($got -ne $want) { Die "ההעתקה של $i לא הושלמה, נסו שוב" "$i did not arrive complete; run the script again" }
            # The overflow chunks first, the first chunk (seforim.db) last: the app sees a library only once it is all there.
            $fix = "cd $Lib && for c in $($Incoming)seforim.db[0-9][0-9][0-9]; do [ -e `$c ] && mv `$c `${c#$Incoming}; done; mv $Incoming$i $i"
            if (-not (Run @('shell', $fix))) { Die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder" }
            continue
        } elseif ((Get-Item -LiteralPath $local -Force).Length -gt $ChunkBytes) {
            if (-not (SendPieces $local "$Lib/$Incoming$i" $false)) { Die "ההעתקה של $i נכשלה" "copying $i failed" }
        } elseif (-not (SendFile $local "$Lib/$Incoming$i")) { Die "ההעתקה של $i נכשלה" "copying $i failed" }
        if ((RemoteBytes "$Lib/$Incoming$i" $want) -ne $want) { Die "ההעתקה של $i לא הושלמה, נסו שוב" "$i did not arrive complete; run the script again" }
        # Files 666 / folders 777: the app is not their owner but must read, write (SQLite WAL) and later delete them. On
        # a card the filesystem has no such permissions, and the app owns its folder there. Below Android 11 the phone's
        # shared storage (sdcardfs) refuses chmod and needs none, giving the app its folder's files whoever wrote them:
        # tried only.
        $fix = if ($SdCard -or $CardUuid -or $SdcardFs) { "cd $Lib && mv $Incoming$i $i" } else { "cd $Lib && mv $Incoming$i $i && find $i -type d -exec chmod 777 {} + && find $i -type f -exec chmod 666 {} +" }
        if (-not (Run @('shell', $fix))) { Die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder" }
        if (-not ($SdCard -or $CardUuid) -and $SdcardFs) { $null = Run @('shell', "cd $Lib && { find $i -type d -exec chmod 777 {} +; find $i -type f -exec chmod 666 {} +; } 2>/dev/null; true") }
    }
    if ($SdCard -or $CardUuid) {
        # One library: the app would keep using the one it chose before, in the phone's storage.
        if ((Ask '' @('shell', "[ -f $PhoneLib/seforim.db ] && echo yes")) -eq 'yes') {
            Say 'מסיר את הספרייה שבזיכרון הטלפון: פרדס ישתמש בזו שבכרטיס' "removing the library in the phone's storage: Pardes uses the card's"
            $names = "seforim.db seforim.db-wal seforim.db-shm seforim.db-journal $Items $Pack $PackStaged local_update_version.txt seforim.db.applying delta-cache"
            $null = Run @('shell', "cd $PhoneLib && rm -rf $names seforim.db[0-9][0-9][0-9] 2>/dev/null; for i in $names; do [ ! -e `$i ] || mv `$i .zayit-old-`$i-$Stamp; done; true")
        }
    } elseif (Ask '' @('shell', "ls /storage/*/Android/data/$Pkg/files/library/seforim.db 2>/dev/null | grep -v emulated; true")) {
        Say 'שימו לב: גם בכרטיס הזיכרון יש ספרייה. אם פרדס ממשיך להשתמש בה, עברו לספרייה שבטלפון בהגדרות › ניהול נתונים › מיקום הספרייה' 'note: the memory card has a library too; if Pardes keeps using it, switch in Settings › ניהול נתונים › מיקום הספרייה'
    }
    if ($WithNotes) {
        Say 'מעתיק הערות והדגשות (יחליפו את אלה שבטלפון)' "copying notes and highlights (they replace the phone's)"
        # The app imports them from the phone's storage only, also when its library is on the card.
        $import = "$PhoneFiles/import"
        $null = Run @('shell', "mkdir -p $import; chmod 777 $import 2>/dev/null; rm -f $import/user_settings.db*")
        if (-not (SendFile $NotesDb "$import/user_settings.db$Part")) { Die 'ההעתקה של ההערות נכשלה' 'copying the notes failed' }
        $move = if ($SdcardFs) { "cd $import && mv user_settings.db$Part user_settings.db" } else { "cd $import && mv user_settings.db$Part user_settings.db && chmod 666 user_settings.db" }
        if (-not (Run @('shell', $move))) { Die 'לא ניתן להעביר את ההערות לתיקיית היישום' "could not move the notes into the app's folder" }
        if ($SdcardFs) { $null = Run @('shell', "chmod 666 $import/user_settings.db 2>/dev/null; true") }
    }
} finally {
    # Also on Ctrl+C, which skips Die: adbd stops the phone's shell with the adb client, before its own `rm -f`.
    if ($Staging -and -not $DryRun) { try { $null = & $Adb (Full @('shell', "rm -rf $Stage")) } catch { } }
    $Staging = $false
}
if (Ask '' @('shell', "ls -a $Lib | grep '^\.zayit-old-'; true")) {
    Say 'עותקים ישנים שהיישום יצר בעצמו ימחקו כשיפתח' 'old copies the app made itself were renamed .zayit-old-*; it deletes them when it next opens'
}

Say 'הושלם. פתחו את פרדס בטלפון; אם יופיע מדריך ההגדרה, הוא ימצא את הספרייה אחרי אישור הרישיון' 'done. Open Pardes on the phone; if its setup guide appears, it finds the library after the licence step'
$null = Run @('shell', 'ls', '-l', $Lib)
