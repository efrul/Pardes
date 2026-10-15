@echo off
rem Copies the library of desktop Zayit to Pardes, the Android app based on Zayit: double-click this file.
rem It runs push-desktop-library.ps1 from the same folder with the execution policy bypassed for this run only
rem (Windows does not run a downloaded .ps1 otherwise, not even with "Run with PowerShell"), passes on any
rem arguments (-Source DIR, -Serial SERIAL, -WithNotes, -DryRun) and keeps the window open for the result.
rem No parenthesised blocks: cmd expands %~dp0 before parsing one, and a ")" in the folder name, as in
rem "push-desktop-library-windows (1)" after a second download, would end the block early.
setlocal
if exist "%~dp0push-desktop-library.ps1" goto run
echo ERROR: push-desktop-library.ps1 must be in the same folder as this file: "%~dp0"
echo.
pause
exit /b 1
:run
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0push-desktop-library.ps1" %*
set rc=%errorlevel%
echo.
pause
exit /b %rc%
