<#
Copies the library of desktop Zayit to Pardes (an Android app based on Zayit) over adb, so the phone needs no 5.5 GB download.
העתקת הספרייה של זית למחשב אל פרדס בטלפון דרך adb, ללא הורדה של 5.5 ג״ב בטלפון.

  Double-click push-desktop-library.cmd next to this file, or:
  powershell -ExecutionPolicy Bypass -File push-desktop-library.ps1 [-Source DIR] [-Serial SERIAL] [-WithNotes] [-DryRun]

  -Source DIR      desktop library folder (default: the folder of %SEFORIMAPP_DATABASE_PATH%, else the desktop
                   install, %APPDATA%\io.github.kdroidfilter.seforimapp\databases)
  -Serial SERIAL   device to use when several are connected (see `adb devices`)
  -WithNotes       also copy notes and highlights (settings\user_settings.db); they replace the phone's own
  -DryRun          print the adb commands instead of running them

Copies seforim.db, the search indexes seforim.db.lucene\ and seforim.db.lookup.lucene\, lexical.db, catalog.pb and
release_info.txt (~12.2 GB) into /sdcard/Android/data/<app>/files/library/. Needs USB debugging on the phone, the app
installed, and Zayit closed on the computer. No root. Without root, `adb push` cannot write into another app's folder
(its fchown fails there), and adb on Windows reads its standard input as text, so a stream through `adb shell -T`
stops at the first 0x1A byte. So each file is pushed to /data/local/tmp/zayit-push and copied into place by the
phone's shell, one file at a time: the phone needs room for the library plus its largest file (8 GB). An item is
written as .zayit-incoming-<name> and renamed when complete (the app deletes <name>.part when it opens). The files belong to adb's `shell` user, so they are made writable
for the app (SQLite opens the database read-write). Folders the app made itself (its own download) cannot be emptied
by adb: they are renamed .zayit-old-* and the app deletes them when it next starts. Linux/macOS: push-desktop-library.sh.
#>
param([string]$Source = '', [string]$Serial = '', [switch]$WithNotes, [switch]$DryRun, [switch]$Help)
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
function Say($he, $en) { Write-Host "==> $en"; Write-Host "    $he" }
function Die($he, $en) {
    Write-Host "ERROR: $en" -ForegroundColor Red; Write-Host "       $he" -ForegroundColor Red
    # A failed copy must not leave up to 8 GB behind in the staging folder.
    if ($script:Staging) { try { $null = & $Adb (Full @('shell', "rm -rf $Stage")) } catch { } }
    exit 1
}
if ($args.Count) { Die "אפשרות לא מוכרת: $args (האפשרויות: -Source, -Serial, -WithNotes, -DryRun)" "unknown option(s) $args; the options are -Source, -Serial, -WithNotes, -DryRun, -Help" }
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
foreach ($wal in "$Db-wal", "$NotesDb-wal") {
    if ((Test-Path -LiteralPath $wal) -and (Get-Item -LiteralPath $wal).Length -gt 0) { Die "סגרו את זית במחשב ונסו שוב ($wal אינו ריק)" "close Zayit on the computer first ($wal is not empty)" }
}
foreach ($d in 'seforim.db.lucene', 'seforim.db.lookup.lucene') {
    if (-not (Test-Path -LiteralPath (Join-Path $Source $d))) { Say "חסר $d`: החיפוש לא יעבוד בטלפון" "warning: $d missing, search will not work on the phone" }
}
if ($WithNotes -and -not (Test-Path -LiteralPath $NotesDb)) { Die "לא נמצא קובץ ההערות $NotesDb" "no notes database at $NotesDb" }
$Present = @($Items | Where-Object { Test-Path -LiteralPath (Join-Path $Source $_) })
$TotalKb = [long](($Present | ForEach-Object { SizeOf (Join-Path $Source $_) } | Measure-Object -Sum).Sum / 1024)
# Each file passes through the staging folder on the phone, so the largest one needs room twice.
$LargestKb = [long](($Present | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $Source $_) -Recurse -File -Force } | Measure-Object Length -Maximum).Maximum / 1024)
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
$du = Ask '0' @('shell', "cd $Lib && du -sk $Items $($Items | ForEach-Object { "$Incoming$_" }) 2>/dev/null; true")
$OldKb = [long](($du -split "`n" | Where-Object { $_ -match '^\d+' } | ForEach-Object { [long](($_ -split '\s+')[0]) } | Measure-Object -Sum).Sum)
$NeedKb = $TotalKb + $LargestKb + 512 * 1024
Say "פנוי במכשיר: $([long](($AvailKb + $OldKb) / 1024)) מ״ב, נדרש: $([long]($NeedKb / 1024)) מ״ב" "free on device: $([long](($AvailKb + $OldKb) / 1024)) MB (incl. the old library), needed: $([long]($NeedKb / 1024)) MB"
if ($AvailKb + $OldKb -lt $NeedKb) { Die 'אין מספיק מקום פנוי במכשיר' 'not enough free space on the device' }

# Removes an item from the library, or renames it .zayit-old-* when adb cannot empty it (a folder the app made), for
# the app to delete when it next starts.
$Stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
function ClearItem($i) {
    if (-not (Run @('shell', "cd $Lib && { rm -rf $i 2>/dev/null; [ ! -e $i ] || mv $i .zayit-old-$i-$Stamp; }"))) { Die "לא ניתן להסיר את $i הישן מתיקיית היישום" "could not remove the old $i from the app's folder" }
}

# Bytes in a file or folder on the phone.
function RemoteBytes([string]$path, [long]$fake) {
    $out = Ask "$fake" @('shell', "find '$path' -type f -exec stat -c %s {} + 2>/dev/null | awk '{ s += `$1 } END { print s + 0 }'")
    return [long]("$out".Trim())
}

# Sends one file to the phone. adb on Windows reads its standard input as text, so a stream through `adb shell -T`
# (or exec-in) ends at the first 0x1A byte; adb push reads the file itself. It cannot write into the app's folder, so
# the file lands in $Stage and the phone's shell copies it into place (separate mounts: a move would copy too).
function SendFile([string]$local, [string]$remote) {
    $tmp = "$Stage/file"
    $big = (Get-Item -LiteralPath $local -Force).Length -ge 64MB
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
    return Run @('shell', "cat $tmp > '$remote' && rm -f $tmp")
}

# --- copy. The old database goes first, with anything the source lacks, so an interrupted copy or a partial
# source never pairs an old database with new indexes (or the reverse).
Say 'אל תפתחו את פרדס בטלפון עד שתופיע כאן ההודעה "הושלם"' "don't open Pardes on the phone until this says done"
$null = Run @('shell', "rm -rf $Stage; mkdir -p $Stage")
$Staging = $true
try {
    # With the old database goes any unfinished delta of it (its marker and work folder): never resumed on the new one
    foreach ($i in @('seforim.db', 'seforim.db-wal', 'seforim.db-shm', 'seforim.db-journal', 'release_info.txt', 'seforim.db.applying', 'seforim.db.applying.tmp', 'delta-cache') + @($Items | Where-Object { $Present -notcontains $_ })) { ClearItem $i }
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
        } elseif (-not (SendFile $local "$Lib/$Incoming$i")) { Die "ההעתקה של $i נכשלה" "copying $i failed" }
        if ((RemoteBytes "$Lib/$Incoming$i" $want) -ne $want) { Die "ההעתקה של $i לא הושלמה, נסו שוב" "$i did not arrive complete; run the script again" }
        # Files 666 / folders 777: the app is not their owner but must read, write (SQLite WAL) and later delete them.
        $fix = "cd $Lib && mv $Incoming$i $i && find $i -type d -exec chmod 777 {} + && find $i -type f -exec chmod 666 {} +"
        if (-not (Run @('shell', $fix))) { Die "לא ניתן להעביר את $i למקומו בתיקיית היישום" "could not move $i into place in the app's folder" }
    }
    if ($WithNotes) {
        Say 'מעתיק הערות והדגשות (יחליפו את אלה שבטלפון)' "copying notes and highlights (they replace the phone's)"
        $import = "$Files/import"
        $null = Run @('shell', "mkdir -p $import; chmod 777 $import 2>/dev/null; rm -f $import/user_settings.db*")
        if (-not (SendFile $NotesDb "$import/user_settings.db$Part")) { Die 'ההעתקה של ההערות נכשלה' 'copying the notes failed' }
        if (-not (Run @('shell', "cd $import && mv user_settings.db$Part user_settings.db && chmod 666 user_settings.db"))) { Die 'לא ניתן להעביר את ההערות לתיקיית היישום' "could not move the notes into the app's folder" }
    }
} finally {
    # Also on Ctrl+C, which skips Die: adbd stops the phone's shell with the adb client, before its own `rm -f`.
    if ($Staging -and -not $DryRun) { try { $null = & $Adb (Full @('shell', "rm -rf $Stage")) } catch { } }
    $Staging = $false
}
if (Ask '' @('shell', "ls -a $Lib | grep '^\.zayit-old-'; true")) {
    Say 'עותקים ישנים שהיישום יצר בעצמו ימחקו כשיפתח' 'old copies the app made itself were renamed .zayit-old-*; it deletes them when it next opens'
}

Say 'הושלם. פתחו את פרדס בטלפון; אם יופיע מסך ההתקנה, בחרו "שימוש בספרייה הקיימת"' 'done. Open Pardes on the phone; if the setup screens appear, choose "use existing library"'
$null = Run @('shell', 'ls', '-l', $Lib)
