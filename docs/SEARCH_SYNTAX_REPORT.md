# How Pardes search handles quotes, geresh, prefixes and word distance

Measured on 2026-11-03, on Pardes 5c6a545 (before the fixed lexicon of 57c4ee0, which may change the free-search counts, not the quote rules), against the October 2026 library (SeforimLibrary release v3-20261012193827), running the app's own engine (`LuceneSearchEngine` + `LongQuerySearchEngine`, near = 5). The count is the exact number of lines found (the facets' count). Android runs the same code (`android/search/LuceneSearchEngine.kt`, same `SearchQueryParser`, near = 5), so the same results apply to it.

## Quotes (")

| Query | What happens | Lines |
|---|---|---|
| `רש"י` | acronym, free search (dictionary) | 483,843 |
| `"רש"י"` | exact | 305,919 |
| `רש״י` (gershayim from the Hebrew keyboard) | **becomes `רשי`: finds mostly רַשַּׁי ("permitted"), not Rashi** | 6,573 |
| `"רש״י"` | same | 6,573 |
| `רש"י"` (quote after the acronym) | the last quote is ignored, free search | 483,843 |
| `"רש"י` (quote before only) | exact | 305,919 |
| `אמר רבי יוחנן` | free (each word anywhere in the line) | 85,121 |
| `"אמר רבי יוחנן"` / `״אמר רבי יוחנן״` | exact | 14,273 |
| `"אמר רבי יוחנן` (opened, not closed) | exact | 14,273 |
| `אמר רבי יוחנן"` (closed only) | **ignored, free search** | 85,121 |
| `“אמר רבי יוחנן”` (typographic quotes) | **ignored, free search** | 85,121 |
| `אמר"רבי יוחנן"` (no space before the quote) | **taken as the acronym `אמר"רבי`: nothing found** | 0 |
| `"` / `""` / `" "` | no search | — |

## Geresh (ר', ה')

| Query | Lines |
|---|---|
| `ר' יוחנן` | 82,451 |
| `ר׳ יוחנן` (geresh from the Hebrew keyboard) | 127,884 (**the ר is dropped: same as `יוחנן` alone**) |
| `"ר' יוחנן"` | 38,514 |
| `"ר׳ יוחנן"` | **658** |
| `ה' אלקים` / `ה׳ אלקים` | 66,635 / **14,361** |
| `"ה' אלקים"` / `"ה׳ אלקים"` | 2,213 / **85** |

Why: the index keeps `"` and `'` inside a word (רש"י and ר' are words of their own) and drops ״ and ׳ (רש״י becomes רשי, ר׳ becomes ר). The texts use both forms (in a sample of 300,000 lines: רש"י 1,821, רש״י 630; ר' 8,342, ר׳ 1,662), so each keyboard finds only part of them.

## Maqaf and nikud: fine

`על פי` = `על־פי` = `על-פי` (498,762; exact 148,193). `בְּרֵאשִׁית בָּרָא` = `בראשית ברא` (18,321; exact 3,340).

## Prefixes (ו, ה, ב, כ, ל, מ, ש) and suffixes

- Free search: automatic. Of the first 3,200 hits for `בראשית`, 1,358 do not hold the bare word (ובראשית, מבראשית…); `אבות` 1,614 (לאבות, באבות); `שבת` 319 (שבתות…). Words of 4+ letters also match as part of a longer word (4-letter substrings); shorter words get their forms from the dictionary.
- In quotes: none. `"שבת"` 225,772 lines against 379,566 free; `"בראשית"` finds no ובראשית. There is no way to ask for "these exact words, but with prefixes".

## Word distance and order

- The app always passes near = 5, with no setting. But it is not a filter: it only ranks. Every line holding all the words is found. `אמר יוחנן` has hits with the words 209 words apart, `ואהבת כמוך` 402.
- Order is ignored: `אמר יוחנן` and `יוחנן אמר` both find 94,128.
- Old strings for a 5-level "search level" (`search_level_1..5_explanation`) are still in strings.xml, with no screen.

## Semantic search

Any real quoted phrase turns off the search by meaning for that query (`hasExactPhrases`). `שבת"` (quote at the end only) does not.

## Not built

Recommended to the user: count ״ as " and ׳ as ' (both spellings found), typographic quotes as phrase quotes, a quote at the end only as a phrase, no acronym when three or more letters follow the quote; then a "search options" window (word distance as a real filter, word order, prefixes inside quotes, a syntax help tab crediting "Based on Klei Kodesh — https://kleikodesh.github.io/"). The user answered "not now" (2026-11-03). The per-word ^ + = options wait on the licence of Klei Kodesh's synonyms and inflections.

## Klei Kodesh (KitveiHakodesh, FtsLib)

- Quotes: the same acronym rule; also the typographic quotes; an unclosed quote is ignored.
- Options window ("אפשרויות חיפוש"), with a "תחביר חיפוש" tab: max word distance (a real filter, default 10), context words, word order; for all words or per word: prefixes `*מילה`, suffixes `מילה*`, grammatical prefixes/suffixes `%מילה` / `מילה%`, fuzzy `~` (1–3), ketiv `^`, inflections `+`, synonyms `=`; optional letter `?`; OR `|`.
