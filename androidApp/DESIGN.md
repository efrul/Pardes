# Pardes (an Android app based on Zayit): design spec

Goal: the Android app should feel like Zayit 0.6.1 on desktop, adapted to touch. It targets **minSdk 26** (Android 8.0). Screen sizes run from the Unihertz Jelly Star (≈240×427dp) through ordinary phones (≈360–412dp wide) to tablets (≥840dp wide). All reference screenshots were taken from the real desktop build 0.6.1.

## 1. Visual language (copied from desktop "Islands" style)

| Token | Light | Dark | Where it's used on desktop |
|---|---|---|---|
| Window ground | `#F2F3F5` | `#1E1F22` | Space between panels |
| Panel ("island") | `#FFFFFF` | `#2B2D30` | Tree, TOC, text, commentaries |
| Panel border | `#EBECF0` | `#393B40` | 1dp around panels |
| Primary text | `#1F1F1F` | `#DFE1E5` | Book text, UI labels |
| Secondary text | `#6C707E` | `#9DA0A8` | Breadcrumbs, captions |
| Accent (Gold, default) | `#BE9117` | `#D4A843` | Buttons, selected line bar, links, chips |
| Accent tint | accent at 12–16% | accent at 18% | Selected tab, selected tree row, chip fill |
| Title bar | gradient `#F3E9CF → #FFFFFF` | `#2B2D30` | Tab strip / top bar |
| Highlight colours | yellow, green, blue, pink, orange | same, darker | "סמן" swatches |

The user can pick other accents (Blue, Teal, Green, Gold), as in the desktop settings ("צבע מבטא").

- **Shapes:** panels have a 10dp radius and 6dp gaps on the ground colour. Buttons are pills with an 8dp radius, and the primary button is filled with the accent colour.
- **Typography:** defaults match the desktop font settings.
  - Book text: Noto Serif Hebrew.
  - Commentaries: Frank Ruhl Libre.
  - Targum: Taamey Ashkenaz.
  - Sources: Tinos.
  - UI: the same serif family, like the desktop's Hebrew fallback.
- **Text sizes:** book text starts at 18sp on phones and 20sp on tablets. Zoom runs from 14 to 50sp, the same limits as desktop.
- **Direction:** everything is RTL.

## 2. Structure by window size

| Desktop | Phone (compact, < 600dp) | Tablet (expanded, ≥ 840dp) |
|---|---|---|
| Title bar: tabs, "+", home, search, theme, settings | Top bar: current tab title, open-books counter (tab switcher), home, search, overflow (theme, settings, tools) | Tab strip as on desktop, with the same actions on the left |
| Right rail: ספרים / תוכן עניינים / הערות | "ספרים" and "תוכן עניינים" open a right-side drawer (RTL start) with the tree or the TOC | Rail and panels as on desktop |
| Left rail: הגדל, הקטן, ניקוד, שניים מקרא, פרטי הספר, תרגומים, מקור, מפרשים | Bottom toolbar in the same order: מפרשים, תרגומים, מקור, ניקוד, Aa (zoom), and "more" for פרטי הספר and שניים מקרא | Left rail as on desktop |
| Commentaries pane under the text, with a commentator checklist on the right and a grid of columns | Bottom sheet (half or full height): commentator chips (the same checklist, horizontal), then one commentator per page in a pager with page dots | Pane under the text with checklist and grid (2–3 columns) |
| Targum / sources pane beside the text | Bottom-sheet tab | Pane beside the text |
| Notes pane | Bottom-sheet tab "הערות" | Pane |
| Right-click context menu (copy variants, link, search, note, highlight colours) | Long-press on a line: action sheet with the same items and the colour swatches | Long-press / right-click: same menu |
| Find in page (Ctrl+F) | Search icon in the top bar opens a find bar | Same |

## 3. Screens

1. **Onboarding (first run):** welcome → licence (checkbox, "המשך") → disk space → install type (online / offline) → download → extract → personal details → region → done ("פתח את היישום"). Android only: with a memory card in the device, the disk step lists the phone and the card (radio rows with their free space) and the pie follows the one chosen.
   - Full-screen steps with the same titles, illustrations and gold buttons. A thin progress bar runs at the top.
   - The download runs as a foreground job so it survives backgrounding.
   - The offline route imports a database that is already on the device (`<external files>/seforim.db`, or picked through the system file picker).
2. **Home:** wallpaper, the "פרדס" logo (the name in gold lettering), a search field with a typewriter placeholder, catalog chips (תנ״ך, משנה, תלמוד בבלי, תלמוד ירושלמי, משנה תורה, טור, שולחן ערוך) that open a dropdown of books, and zmanim cards in a grid (2 columns on phone, 5 on tablet).
3. **Library tree ("רשימת הספרים"):** expandable categories with folder icons and books with book icons. Tap opens the book in the current tab; long-press opens it in a new tab.
4. **Reader:**
   - Header: book title, then the chapter heading.
   - Lines: verse markers "(א)", heading levels from HTML, and the selected line marked with a 3dp accent bar on the right (RTL start).
   - Paged loading, with pinch zoom.
   - Breadcrumb (תנ״ך › תורה › בראשית › פרק א) under the text on tablet, and in the top-bar subtitle on phone.
5. **Commentaries / targum / sources / notes:** see table 2.
   - Commentators are grouped as on desktop (ראשונים על התנ״ך…).
   - The defaults come from `default_commentator`.
6. **Search:** the desktop results screen: query field with the telescope chip (base books only, or every book), "נמצאו N תוצאות" with a progress line and cancel, results as "book > section", a snippet with the hits in bold accent and the full path, loaded 25 at a time while scrolling. The category/book tree with counts and checkboxes is a bottom sheet on phones ("סינון" chip, checked filters as removable chips) and a side panel from 1200dp; on tablets a tap shows the result in the preview pane.
   - Phone: list, then tap opens the reader at that line.
   - Tablet: list on the right, preview on the left, like desktop.
7. **Settings:** a list of pages, the same as desktop (כללי, תצוגה, פרופיל, גופנים, ניהול נתונים, אודות, תנאים) with the same controls, plus Android-only parts: in כללי, desktop's update banner at the top of the app card and "בדוק עדכונים בכל הפעלה" (the title bar has desktop's "עדכון זמין" badge: a button on a tablet, the phone's overflow dot and first item, both opening the update dialog); in ניהול נתונים, "מיקום הספרייה" (phone or memory card, moving between them) and "עדכון מקבצים" / "עדכון מתיקייה" beside the incremental update; and one page at the end, רישיונות: Pardes's licence and every bundled library, icon set, font and data file with its licence texts, which open full-screen and offline. Where desktop says who made the app (the welcome step, the settings header, About, the terms), Pardes says instead that it is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it; his copyright, AGPL and §7b notices are kept.
8. **Tools menu:** the same items as desktop (סידור חכם, לוח עברי, מילון, לימוד יומי, מולד וקידוש לבנה, שנים מקרא ואחד תרגום, תפילת היום, ממיר מידות, מעגל השנה, זמני היום). On phone they are full-screen pages; on tablet, dialogs.

## 4. Data

- **Reused code:** `SeforimLibrary/core` and `dao` are compiled as-is. Sources are included via `srcDir`; the SQLDelight `.sq` files come from the dao module.
- **PRAGMAs:** `PragmaSafeDriver` routes the repository's PRAGMAs through `executeQuery`, because Android rejects row-returning statements in `execute`.
- **Dev data:** `tools/make_subset_db.sh` builds a small `seforim.db` (Bereshit + Berakhot + Mishnah Berakhot, with their commentaries and targumim) for the emulator.
