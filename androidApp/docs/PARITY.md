# Parity with desktop Zayit 0.6.1

A gap list from comparing the real 0.6.1 sources (upstream `kdroidFilter/Zayit` tag `v0.6.1` and SeforimLibrary `381fbea`) with this Android app, area by area, on 2026-10-13. 119 gaps, plus 12 from a completeness review.

- **Severity:** **H** = a missing or broken feature the user will notice; **M** = a noticeable difference; **L** = polish.
- **Tick an item when it lands.** Before porting anything, read the 0.6.1 code it names. The full findings, with desktop and Android file:line references and fix plans, were produced by the audit workflow; re-run a focused audit for an area rather than trusting stale line numbers.
- **Not portable from public sources:**
  - semantic (meaning-based) search: a private, token-gated package; Android behaves like the desktop community build;
  - Google Drive backup: a desktop loopback OAuth client;
  - the full template siddur: a private package.
- **Different on purpose: authorship.** Desktop's welcome ("שנבנה באהבה על ידי אברך ומפתח חרדי"), settings header ("נוצר על ידי אליהו גמבש"), About and terms describe desktop Zayit. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept. Not a gap.

## Later desktop releases (0.6.2 to 0.6.4)

The base stays desktop 0.6.1 (SeforimLibrary submodule pinned at `381fbea`). From `git diff v0.6.1 v0.6.4` upstream, only these four 0.6.2 fixes were ported, because the bugs they fix exist here too:

| Upstream commit | Fix | Android files |
|---|---|---|
| `2126c238` | Typing `בבא קמא` opens בבא קמא (not בבא בתרא דף קמא), `בבא קמא יז` finds the daf, no crash on a word made only of quotes, book suggestions ranked by title tier | `search/reference/ReferenceParser.kt`, `ReferenceResolver.kt`, `RepositoryReferenceSource.kt`, `search/LuceneLookupSearchService.kt` |
| `90552dbc` | No author or book suggested by reading one typed word twice (`בבא ב` no longer suggests רב בבאר שבע or תוספתא בבא קמא) | `search/LuceneLookupSearchService.kt`, `search/AuthorNames.kt`, `ui/search/SearchBarViewModel.kt` |
| `51463d23` | A title being typed past its first word (`בבא ק`) puts the book before the text search, so Enter opens it | `ui/search/UnifiedSearchBar.kt` (`catBookRows`) |
| `411280b9` | The תפילת היום widget turns to the next Hebrew day at צאת הכוכבים | `ui/home/widgets/LuachWidgets.kt` (`TefilaWidget`) |

Each of these files says in its header that it is the 0.6.1 port plus the named 0.6.2 commit. Deliberately not ported:
- the SeforimLibrary bump (meaning-based highlighting infrastructure; it would need `LuceneSearchEngine.kt` changes with no gain for the user here);
- semantic search, Filament and the Windows solar-system work, and dependency updates (nucleus, seforim-siddur, seforim-semantic), none of which exist in this port;
- `25dde83d` (`IntrinsicSize.Min` in the result preview): a crash guard for a zero-width preview that this layout never produces.

Kept as desktop has it: typing an author's acronym with its gershayim (`ש"ך`) does not suggest that author (הרב שבתי בן מאיר הכהן). The lookup query drops the quote while the index keeps it (`ש"ך`); desktop 0.6.1 misses him the same way. An Android-only reading that finds him was tried and left out, to keep search the same as desktop.

The Android-only subtitle of תפילת היום in the tools list (`ui/tools/ToolsScreen.kt`) follows the same rule through the shared `tefilaDayAt` helper, so it turns at צאת הכוכבים together with the page it opens.

Fixes land in waves:
1. 0.6.1 engine, zmanim, places and network trust.
2. Shell, settings and tools.
3. Search and reader.
4. Final sweep.

## Engine and data

- [x] **H** Snippets and search-term highlighting still come from the pre-0.6.1 engine (wrong window, bolds unrelated dictionary variants and 4-gram fragments)
- [x] **H** Pointing the build at seforimlibrary 381fbea: what breaks at compile time, plus a runtime hazard with the shared reader
- [x] **H** Minimum library version is behind desktop's; the 0.6.1 dao would crash getBook() on older libraries
- [x] **H** Author data, author page, author suggestions, knowledge panel and book editions/licenses are missing
- [x] **H** Typed references (חולין יב:, בראשית א ג, שו"ע או"ח שכח ג) do not jump to the place
- [x] **M** Facets and searching are slow on phones: a new reader per search, a full document read per hit, and wide snippet loads
- [x] **M** Magic dictionary ignores ambiguous surfaces and picks the wrong word family (fewer results, different ranking)
- [x] **M** Results are not grouped per book (no 'עוד N תוצאות מ…', no de-duplication)
- [x] **M** Reader find-in-page and search-term highlighting do not use the engine (only visible lines, raw substrings, no count or next/previous)
- [x] **M** No incremental library (delta) updates: every schema bump means a full ~8 GB download (wave 4: `android/delta/`, Settings › ניהול נתונים and the database update flow; in-place transactional apply without desktop's 8 GB backup copy, Lucene lines written like the generator's index writer)
- [x] **M** User settings DB comes from the old schema; history and favorites do not match desktop's user_settings.db
- [x] **L** ~~Semantic (meaning-based) search~~: cannot be built from public sources. Android behaves like the desktop community build (no meaning search).

## Search

- [x] **H** Suggestion dropdown is books-only: no author rows, wrong row order, Enter/IME Search does not run the highlighted row, no row-action buttons
- [x] **H** No reference parsing: 'בראשית א ב', 'חולין יב:', 'שו"ע או"ח שכח ג' cannot jump straight to a passage
- [x] **H** Base-books vs all-books choice: telescope chip instead of the 0.6.1 status line; no way to start an all-books search from home
- [x] **H** Category filtering: checkbox tree and 'סינון' sheet instead of the 0.6.1 category tabs
- [x] **H** No grouping by book, no 'עוד N תוצאות מ…' expander and no 'חזרה לכל התוצאות', no duplicate-text removal
- [x] **H** Opening a result does not seed find-in-page, so the searched words are not highlighted in the reader
- [x] **H** Phones: Back from an opened result does not return to the results, and the results scroll position is lost
- [x] **H** Tablet two-pane: wrong threshold and widths, an embedded reader instead of the 0.6.1 PassagePreview, and the selected result cannot be opened
- [x] **H** No in-book search: book chip, TOC suggestions, 'חיפוש בתוך {book}: {q}', scoped results
- [x] **M** Result row format differs from 0.6.1 (title link, book-font full-size snippet, extra full-path line)
- [x] **M** Results page bar is a plain text field, not the shared smart bar with the olive branch
- [x] **M** No entity knowledge panel for queries naming a book or an author
- [x] **M** Executed searches are not recorded in history
- [x] **L** Search placeholder hints differ from desktop
- [x] **L** Progress/cancel header visuals: an idle grey bar is always drawn
- [x] **L** No find within the results; the top-bar 'חיפוש' opens an empty search tab
- [x] **L** Scoped-search TOC pane with hit counts (tablet)
- [x] **L** A zayit:// link pasted into the bar is searched as text

## Reader

- [x] **H** Tab content not keyed per tab: reader scroll/UI state leaks between tabs; new book in same tab opens at old scroll index; position never persisted
- [x] **H** No text selection inside lines or commentaries: copy, highlight and note only work on whole lines
- [x] **H** Find in page: loaded lines only, per-word matching, no match counter, no previous/next, nothing highlighted in panes
- [x] **H** "דווח על טעות" is fake (toast only)
- [x] **H** Diacritics: one global on/off instead of desktop's 3-state mode saved per root category; button shown for books without diacritics
- [x] **H** Commentaries pane: book-level commentator list, no section gathering for headings, wrong names, no open-at-place, loads every commentary text, single-row layout
- [x] **H** Tablet panes are mutually exclusive; rails and phone toolbar ignore what the book or line actually has
- [x] **M** Line selection model: nothing selected on open, TOC click only scrolls, headings don't select their section, no multi-select
- [x] **M** TOC pane: no alt TOC and no inline alt headings, no TOC search, current entry matched by text, no scroll to current, sole root not collapsed, parents navigate
- [x] **M** Breadcrumb is static text; no copy link, share or favorites with folders; tab title lacks the chapter
- [x] **M** Citation links in texts and commentaries are not clickable
- [x] **M** Book details pane incomplete; parts do nothing on phone; visibility not persisted
- [x] **M** Notes pane: no editing or autosave, no timestamps, no section scope, explicit Save button, misleading hint, no note markers in text
- [x] **M** "ההערות שלי" (all notes) screen missing
- [x] **M** Highlights: whole-line only, different palette, ranges imported from desktop shown as whole lines, none in panes
- [x] **M** "העתק עם מקור" uses a different format; copy-without-nikud shown when meaningless
- [x] **M** Shnayim Mikra reading mode missing from the reader
- [x] **M** Marked passage (daily learning / aliya) not supported
- [x] **M** Author page missing
- [x] **M** No fast-scroll / content-aware scrollbar for long books
- [x] **L** Sources pane mixes in REFERENCE links; targum/source section titles not clickable
- [x] **L** Book tree has no in-pane search
- [x] **L** Tablet commentators list not collapsible; no compact mode
- [x] **L** Hardware-keyboard shortcuts absent (tablets with keyboards)
- [x] **L** Inline images in texts not rendered
- [x] **L** Wording differences in reader strings
- [x] **M** A long-pressed word on a justified line was selected whole but highlighted (handles, note underline) toward the line's start, half on its neighbour: Android positions ignore justification, so the app justifies the text itself (`ui/reader/Justify.kt`; found by the user, 2026-10-15)

## Home and tools

- [x] **H** Zmanim calculation engine and opinions do not match desktop (wrong alos, MGA, tzais, candle lighting, havdala; community ignored)
- [x] **H** City/location table is a 15-city subset with different coordinates; location is not reactive
- [x] **H** Home is a fixed column, not the desktop editable widget grid (17 widgets, gallery, persistence)
- [x] **H** Earth globe, Sky and 3D solar system (מעגל השנה) widgets are missing; Android YearCycle is a 2D approximation with a different orbit model
- [x] **M** Zmanim cards: layout, titles/abbreviations and click behaviour differ from desktop
- [x] **M** Shared day/moment state missing: calendar day and zman taps don't drive other tools; no luach popup on dates
- [x] **M** Daily study (לימוד יומי): 6 cycles missing, option groups, ordering/wording, Vezot Haberacha, no marked range
- [x] **M** תפילת היום: rows, wording and order differ from desktop tefilaOfDay
- [x] **M** Molad & Kiddush Levana: window opinion ignores community; molad month logic differs
- [x] **M** Shnayim Mikra: double parshiyot, Vezot Haberacha, progress storage and opening differ
- [x] **M** Dictionary: per-dictionary parsing, normalisation, lookup and result rows differ
- [x] **M** Smart siddur: desktop is a private-package template siddur; Android fallback misses the portable behaviours
- [x] **M** Upcoming events and Next zman (luach widgets) missing; Android substitutes differ in content and wording
- [x] **L** Hebrew calendar (לוח עברי): no holiday names in cells, civil toggle not persisted, extra day card uses wrong zmanim
- [x] **L** Measures (ממיר מידות): Chazon Ish value, number formatting, per-opinion options and sources differ
- [x] **L** Temple countdown: no Jerusalem-sunset rollover, anniversary semantics, subtitle wording, static image
- [x] **L** Home chrome: wallpaper blending and search placeholder hints differ
- [x] **L** Tools menu presentation: subtitles built on divergent data; tablet opens every tool full-screen

## Settings, onboarding, database updates

- [x] **H** No startup database-compatibility gate and no 'עדכון מסד הנתונים' (DatabaseUpdateWindow) flow
- [x] **H** Database version rules differ from 0.6.1: old minimum, and databases without a version are accepted
- [x] **H** User-data backup is not compatible with 0.6.1 (bare .db instead of the zip archive; no preferences; restore not staged)
- [x] **H** 'שמור מצב בין הפעלות' is shown (default on) but nothing saves or restores the session
- [x] **H** Region (country/city) list: 15 Android cities vs desktop's worldPlaces; onboarding region not prefilled; no type-to-search
- [x] **M** Online install wipes the current library before checking that the release is new enough; the fallback manifest can point to an old release
- [x] **M** Fonts page offers 5 fonts instead of desktop's 28
- [x] **M** Setting keys, enum values and defaults differ from desktop AppSettings
- [x] **M** 'איפוס האפליקציה' leaves history, bookmarks, tool settings and leftover files behind
- [x] **M** 'מצב קומפקטי לסרגל הצדדי' (compact mode) toggle has no effect
- [x] **M** ~~Google Drive backup card~~: desktop uses a loopback OAuth client that Android cannot reuse. Left out, like the desktop community build. Backup to a file (and from there to Drive) works.
- [x] **M** Settings › תנאים shows the old licence paragraph
- [x] **L** Onboarding: an existing library is handled differently from desktop's licence step
- [x] **L** Settings page icons are Material icons instead of desktop's

## Shell: tabs, history, favourites, links

- [x] **H** Session restore not implemented (persist-session setting is a no-op)
- [x] **H** Per-tab UI state leaks between tabs; reading position lost on tab switch; tab ViewModels never cleared
- [x] **M** Back or "בית" from a book opened in the Home's own tab (search bar, catalog, drawer) built a new Home: blank, then its widgets one by one with spinners, its catalog row arriving ~2.4 s later and pushing the page down. The tab now keeps its Home composed behind the book (parked: back at its top, its bar empty), and the widgets' and catalog's values are kept app-wide for a Home built anew (found by the user, 2026-10-15)
- [x] **H** Workspaces (מרחבי עבודה) missing entirely
- [x] **H** History storage and recording diverge from desktop (wrong data, no searches, previews pollute it, desktop rows ignored)
- [x] **H** Favorites: no folders, no search, wrong key, separate store; star behaviour differs
- [x] **H** Deep links: desktop search links do not open on Android; no tab-level link/share
- [x] **M** History UI: desktop popup + full day-grouped page vs one flat overlay
- [x] **M** No tab context menu (close others/left/right/all, copy link, pin, move to workspace)
- [x] **M** Pinned tabs missing
- [x] **M** Reopen closed tab (Ctrl+Shift+T) missing; no undo for closing a tab
- [x] **M** Hardware-keyboard shortcuts absent (tablets/DeX/Chromebooks)
- [x] **M** Title-bar magnifier opens an empty search tab instead of toggling find-in-page
- [x] **M** Tab titles: no '<book> - <chapter>' suffix; Home tab label differs
- [x] **M** Startup routing lacks the database-version check (DatabaseUpdateWindow)
- [x] **L** Deep link opens underneath an open overlay
- [x] **L** Tab order: new tabs not placed next to their opener; no drag reorder; strip layout
- [x] **L** System Back turns any tab into Home instead of returning to the opener tab
- [x] **L** Theme toggle and title-bar labels use non-desktop wording
- [x] **L** No launcher shortcuts / Recents title (jump-list & dock-menu equivalents)

## Cross-cutting (completeness review)

- [x] **H** Certificates installed by content filters (NetFree / Rimon / Etrog) are not trusted, so library download and release lookup fail behind Israeli filtered internet
- [x] **M** Tablet search preview writes reading history and leaks one ReaderViewModel per selected result
- [x] **M** Home catalog dropdowns list the wrong books and titles (e.g. שולחן ערוך shows 171 entries including commentaries)
- [x] **M** Tab management missing: pin, close others/all/left/right, reopen closed tab, copy link, reorder, open next to current, keyboard shortcuts
- [x] **M** Search deep links shared from desktop (zayit://search/<encoded>) do not open on Android
- [x] **M** History page lacks desktop's day groups, per-entry delete, clear confirmation, per-chapter rows and open-in-place
- [x] **M** Favorites page has no folders, search, move or folder management, and opens in a new tab
- [x] **M** Tablet reader panes have fixed sizes (no draggable dividers, no per-tab split); home book tree cannot be hidden
- [x] **L** Book tree lists subcategories before a category's own books (desktop: books first)
- [ ] **L** Virtual desktops ('מרחבים') and 'open in new window' are missing
- [ ] **L** No app-update check (desktop: title-bar badge, settings banner, dialog)
- [x] **L** No launcher shortcuts (Android equivalent of desktop's jump list / dock menu)

## Integration review (wave 4)

- [x] **H** Session restore, "בטל" and Ctrl+Shift+T reopened a book at its selected line, not where it was read (the reader's history call overwrote the tab's anchor)
- [x] **M** A dialog or sheet opened in a tab (ההערות שלי, the breadcrumb, selection, commentator filter, luach, widget, history dialogs) stayed over the tab a deep link or "לשונית חדשה" opened
- [x] **M** Phones had no find-in-page on the search results (magnifier, or the overflow under 300dp)
- [x] **M** Back from an opened search result first closed the seeded find bar instead of returning to the results
- [x] **M** Shnayim Mikra ticks of older Android builds reached the user DB only once the tool was opened (Home widget, backups)
- [x] **L** Catalog and dictionary stamps (and the library folder) were read on the main thread
- [x] **L** Restored book tabs reopened the search result's find bar
- [x] **L** "איפוס האפליקציה" left the last books read in the launcher's shortcuts
- [x] **L** Each search-engine reopen (library update, reinstall) leaked lexical.db handles
- [x] **L** Phone drawers covered the whole screen at 240dp, their tabs overflowing
- [x] **L** Attaching a hardware keyboard (or changing the font scale) restarted the Activity
- [x] **L** Search tabs came back at the top without their category tab (desktop SearchPersistedState)
- [x] **L** Import and reset were allowed during a library update
- [ ] **L** A staged restore (PendingUserDataRestore) still runs on the main thread, once, at the first launch after an import
