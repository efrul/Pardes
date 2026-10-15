# Using desktop Zayit as the reference

The Android app must look and behave like **desktop Zayit 0.6.1**, which is the released version the port was matched against. One exception is authorship. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. So a desktop/Android comparison differs there on purpose (SKILL.md › Authorship and name).

There are three ways to learn how desktop does something, best first:

1. **Read the exact 0.6.1 source.** The fork's own `SeforimApp/` and `SeforimLibrary/` are from June 2026 and older than 0.6.1: tools, siddur, favourites, history, authors and the current search UI are missing or different there. The released code is public upstream, and the session's git proxy serves anonymous clones of public repos (no `add_repo` attach needed; call it once if unsure, it answers "read_available"):
   ```bash
   mkdir -p /home/user/kdroidfilter
   GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 https://github.com/kdroidFilter/zayit /home/user/kdroidfilter/zayit
   git -C /home/user/kdroidfilter/zayit fetch --depth 1 origin tag v0.6.1
   git -C /home/user/kdroidfilter/zayit worktree add /home/user/kdroidfilter/zayit-0.6.1 v0.6.1
   # SeforimLibrary at the commit 0.6.1 was built with (git -C .../zayit-0.6.1 ls-tree HEAD SeforimLibrary):
   GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 https://github.com/kdroidFilter/seforimlibrary /home/user/kdroidfilter/seforimlibrary
   git -C /home/user/kdroidfilter/seforimlibrary fetch --depth 1 origin 381fbea2a0997a91a1100db20db9904839e78b96
   git -C /home/user/kdroidfilter/seforimlibrary worktree add /home/user/kdroidfilter/seforimlibrary-0.6.1 381fbea2a0997a91a1100db20db9904839e78b96
   ```
   - UI: `zayit-0.6.1/SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/{features,core,framework}/**`.
   - Strings: `zayit-0.6.1/SeforimApp/src/commonMain/composeResources/values/strings.xml` (Hebrew).
   - Library, search engine and data: `seforimlibrary-0.6.1/{core,dao,search}`. It uses Lucene 10.5.2, plus lucene-highlighter and lucene-queryparser.
   - Upstream `master` moves on (v0.6.3 and later). Match the version the user runs, and ask before following a newer one.
2. **The fork's sources** (`SeforimApp/`, `SeforimLibrary/` in this repo): only for what hasn't changed since June.
3. **Run the released app** headless and screenshot it. This is the visual reference and the only way to see real data.

## Running desktop 0.6.1 headless

```bash
S=.claude/skills/zayit-android/scripts
$S/desktop-start.sh            # run_in_background: true; installs /opt/zayit from the .deb if missing, Xvfb :99 1440x900
$S/desktop-shot.sh home        # -> /tmp/zayit-desktop-shots/home.png (set SHOTS=... to change)
$S/desktop-shot.sh settings 20 20 3   # click (20,20), wait 3 s, capture
```

- **The library.** Desktop reads it from `~/.local/share/io.github.kdroidfilter.seforimapp/databases/`. If that folder is empty, desktop starts its own onboarding and downloads 5.5 GB. `fetch-desktop-library.sh` fills it with the same data, faster and with no stored archive.
- **Input:**
  - `xdotool type --delay 100 "בראשית"` works with `LC_ALL=C.UTF-8`.
  - Keys: `xdotool key ctrl+t` (new tab), `ctrl+w` (close tab), `ctrl+f` (find in page), `Return`, `Escape`.
  - Wheel: `xdotool click 5` scrolls down, `click 4` up. Move the mouse over the list first.
  - Right-click (context menu): `xdotool mousemove X Y click 3`.
- **Separate windows.** Settings and tools open as their own windows. List them with `xwininfo -root -tree | grep zayit`; capture one with `import -window <id> out.png`. Close a window by clicking its top-right corner; Xvfb has no window manager.

## Click map of the 1440×900 main window

The window must be maximised to 0,0 (`desktop-start.sh` does this). Coordinates are approximate, so take a screenshot to confirm before a chain of clicks.

| Target | x, y |
|---|---|
| Settings (title bar) | 20, 20 |
| Theme toggle (light/dark) | 60, 20 |
| Tools menu | 100, 20, then items at x=90, y=59, 84, 109 … 284 (10 tools, 25 px apart) |
| History popup | 140, 20 |
| Bookmarks popup | 180, 20 |
| Workspaces ("desktops") | 330, 20 |
| Home search field | 588, 285 (first tab) / 588, 373 (a new tab after ctrl+t). Type, then Return opens the first suggestion |
| Catalog chip (e.g. תנ״ך) | ~935, 88 on the home page |
| A text line in the reader | ~850, 203 (first line), 238 (second) |
| Left rail: commentaries / targum / sources | 33, 858 / 33, 720 / 33, 650 |
| Right rail: notes | 1407, 858 |
| Right rail: book tree / TOC | 1407, 78 / 1407, 150 |
| Settings window sidebar (window at default position) | x=995: general 271, display 305, profile 339, fonts 373, data 407, about 441, terms 475 |

## Screen ↔ source ↔ Android

| Desktop screen | Desktop source | Android |
|---|---|---|
| Onboarding | `features/onboarding/**` | `ui/onboarding/*` |
| Home (logo, search, catalog, zmanim) | `features/bookcontent/ui/panels/bookcontent/views/HomeView.kt`, `components/CatalogRow.kt`, `features/zmanim` | `ui/home/HomeScreen.kt` |
| Book tree | `features/bookcontent/ui/panels/categorytree/**` | `ui/library/LibraryTree.kt` |
| Reader + commentaries/targum/sources | `features/bookcontent/**` (panels, `usecases/CommentariesUseCase.kt`) | `ui/reader/*`, `data/CommentatorGrouping.kt` |
| Search results | `features/search/**` (`SearchResultViewModel`, `SearchHomeViewModel`), `framework/search/**` | `ui/search/*`, `search/*` |
| Settings window | `features/settings/**` | `ui/settings/*` |
| Title bar (tabs, history, bookmarks, theme, tools) | `core/presentation/components/MainTitleBar.kt`, `core/presentation/tabs/**` | `ui/shell/*` |
| Tools | `features/zmanim`, `features/siddur`, modules `luach`, `earthwidget` (in the 0.6.1 source, not in the fork) | `ui/tools/*`, `data/*` |

If a path above has moved, `grep -rn "<Hebrew string>" SeforimApp/src` finds the screen through its strings. Search the string's key in `strings.xml` first, then its usages.

## Making comparisons for the user

```bash
.claude/skills/zayit-android/scripts/contact-sheet.py /tmp/cmp-home.png /tmp/zayit-desktop-shots/home.png /tmp/zayit-shots/home.png --height 1100
```
Send the sheet with SendUserFile and a one-line Hebrew caption naming what to compare. The user reviews by looking at these sheets.
