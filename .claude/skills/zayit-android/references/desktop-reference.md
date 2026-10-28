# Using desktop Zayit as the reference

The Android app must look and behave like **desktop Zayit 0.6.11**, the released version it is matched against since 2026-10-28 (0.6.10 from 2026-10-26, 0.6.7 from 2026-10-20; the port was first matched against 0.6.1; androidApp/docs/PARITY.md records what changed between them). One exception is authorship. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. So a desktop/Android comparison differs there on purpose (SKILL.md › Authorship and name).

There are three ways to learn how desktop does something, best first:

1. **Read the 0.6.11 source in this repository.** Upstream Zayit v0.6.11 was merged into it on 2026-10-28 (v0.6.10 on 2026-10-26, v0.6.7 on 2026-10-20), so `SeforimApp/`, the desktop modules at the root (`htmlparser/`, `navigation/`, `luach/`, `earthwidget/`, `icons/`, …) and the `SeforimLibrary/` submodule (`4f07145`, the commit v0.6.11 builds with) are desktop 0.6.11, apart from Pardes's own few edits (Rashi Amiti removed, for one; `git diff --stat v0.6.11 HEAD -- SeforimApp` lists them). Run `git submodule update --init SeforimLibrary` once after cloning.
   - UI: `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/{features,core,framework}/**`.
   - Strings: `SeforimApp/src/commonMain/composeResources/values/strings.xml` (Hebrew).
   - Library, search engine and data: `SeforimLibrary/{core,dao,search}`. It uses Lucene 10.5.2, plus lucene-highlighter and lucene-queryparser.
   - Upstream `master` moves on past v0.6.11. Match the version the user runs, and ask before following a newer one.
2. **Upstream's tags**, for upstream's exact file and for what changed since the port's first base. `v0.6.1`, `v0.6.7`, `v0.6.10` and `v0.6.11` are fetched into this repository from https://github.com/kdroidFilter/zayit; their commits are already in its history, so a fresh clone fetches only the tags. The session's git proxy serves anonymous fetches of public repos (no `add_repo` attach needed; call it once if unsure, it answers "read_available"). Never push `v*` tags to origin (efrul/Pardes holds only `android-*` tags):
   ```bash
   git fetch --no-tags https://github.com/kdroidFilter/zayit tag v0.6.1 tag v0.6.7 tag v0.6.10 tag v0.6.11
   git show v0.6.11:SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/SearchResultScreen.kt
   git diff --stat v0.6.1 v0.6.11 -- SeforimApp/src htmlparser navigation   # what desktop changed since 0.6.1
   git log --oneline --no-merges v0.6.10..v0.6.11 -- <desktop file>         # and by which commits
   git -C SeforimLibrary diff --stat 381fbea 4f07145 -- core dao search delta-updater   # 0.6.1's library pin to 0.6.11's
   ```
   For a whole checkout of one of them: `GIT_LFS_SKIP_SMUDGE=1 git worktree add /home/user/zayit-0.6.11 v0.6.11` (its `SeforimLibrary/` stays empty; use the submodule above).
3. **Run the released app** headless and screenshot it. This is the visual reference and the only way to see real data.

## Running desktop 0.6.11 headless

```bash
S=.claude/skills/zayit-android/scripts
$S/desktop-start.sh            # run_in_background: true; installs /opt/zayit from the .deb if missing, Xvfb :99 1440x900
$S/desktop-shot.sh home        # -> /tmp/zayit-desktop-shots/home.png (set SHOTS=... to change)
$S/desktop-shot.sh settings 20 20 3   # click (20,20), wait 3 s, capture
```

- **The version.** `desktop-start.sh` installs `zayit_<version>_amd64.deb` from the upstream release `v<version>` (0.6.11 unless given another) only when no `zayit` is installed, and keeps an installed one whatever its version: run `apt-get remove -y zayit` first to replace an older install.
- **The library.** Desktop reads it from `~/.local/share/io.github.kdroidfilter.seforimapp/databases/`. If that folder is empty, desktop starts its own onboarding and downloads 5.5 GB. `fetch-desktop-library.sh` fills it with the same data, faster and with no stored archive.
- **Input:**
  - `xdotool type --delay 100 "בראשית"` works with `LC_ALL=C.UTF-8`.
  - Keys: `xdotool key ctrl+t` (new tab), `ctrl+w` (close tab), `ctrl+f` (find in page), `Return`, `Escape`.
  - Wheel: `xdotool click 5` scrolls down, `click 4` up. Move the mouse over the list first.
  - Right-click (context menu): `xdotool mousemove X Y click 3`.
- **Separate windows.** Settings and tools open as their own windows. List them with `xwininfo -root -tree | grep zayit`; capture one with `import -window <id> out.png`. Close a window by clicking its top-right corner; Xvfb has no window manager.

## Click map of the 1440×900 main window

The window must be maximised to 0,0 (`desktop-start.sh` does this). Coordinates are approximate (measured on 0.6.1), so take a screenshot to confirm before a chain of clicks.

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
| Tools | `features/zmanim`, `features/siddur`, modules `luach`, `earthwidget` (at the repository root) | `ui/tools/*`, `data/*` |

If a path above has moved, `grep -rn "<Hebrew string>" SeforimApp/src` finds the screen through its strings. Search the string's key in `strings.xml` first, then its usages.

## Making comparisons for the user

```bash
.claude/skills/zayit-android/scripts/contact-sheet.py /tmp/cmp-home.png /tmp/zayit-desktop-shots/home.png /tmp/zayit-shots/home.png --height 1100
```
Send the sheet with SendUserFile and a one-line Hebrew caption naming what to compare. The user reviews by looking at these sheets.
