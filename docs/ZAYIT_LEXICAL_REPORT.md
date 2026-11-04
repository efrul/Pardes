# lexical.db: empty bases hide the paradigm of common words, and the 32 kept forms are not the common ones

Library release `20261012193827` (`lexical.db`, 81,035,264 bytes), SeforimLibrary `4f07145` (`MagicDictionaryIndex`,
`LuceneSearchEngine`).

## 1. Bases with no surface win over the real paradigm

`lexical.db` has 33,108 bases. **4,711 of them have no surface** (no `surface.base_id` and no `surface_extra_base.base_id`
points to them), and **2,479 of those are spelled exactly like a surface of another base**:

```sql
SELECT count(*) FROM base b
WHERE NOT EXISTS (SELECT 1 FROM surface s WHERE s.base_id = b.id)
  AND NOT EXISTS (SELECT 1 FROM surface_extra_base e WHERE e.base_id = b.id)
  AND EXISTS (SELECT 1 FROM surface s WHERE s.value = b.value);   -- 2479 (4711 without the last line)
```

For a query word spelled like one of them, the lookup's `matches` returns both the empty base (`b.value = ?`) and the real
one (through the surface). `MagicDictionaryIndex.expansionFor` then prefers "the expansion whose base matches the token",
which is the empty one, whose only term is the word itself. **The word gets no other form.**

| query word | is a surface of | empty base | forms the search uses today | with the empty base gone |
|---|---|---|---|---|
| ברכות | ברכה (76 surfaces) | ברכות | 1 (ברכות) | 267 |
| הלכות | הלכה | הלכות | 1 | 94 |
| לאכול | אכל (395 surfaces) | לאכול | 1 | 528 |
| תפלין | תפילין | תפלין | 1 | 35 (תפילין, בתפילין, …) |
| דברים | דבר | דברים | 1 | many |
| בבית, ולא | בית, לא | בבית, ולא | 1 | many |

Of the 666 distinct words in our two query sets, 72 had no expansion; most of them are this case.

## 2. The 32 forms kept are the first by surface id, not the common ones

`buildLimitedTermsForToken` keeps the word, its base, then the surfaces in the order the lookup returns them (by
`surface_base_id_index`, so by surface id), up to `MAX_SYNONYM_TERMS_PER_TOKEN` (32). For a large paradigm the common
forms are often cut. For example, for שלוש (492 forms) שלש is #69 and שלשה #53, and "חזקת הבתים שלוש שנים" does not find
Bava Batra 28a.

## How it was found

While comparing the regular search's expansions with another dictionary, word by word over the queries of two
evaluation sets: many common words had a single form. A per-word dump of `expansionFor` (word, base, surfaces) showed the
matched base had no surfaces.

## Measured effect (lexical search only)

Two evaluation sets: 95 user-style questions with a known source, and 153 real searches from a user's history with a
graded pool. Pardes's engines, `near` 5, base books first and then all books when the base books find nothing, as in
the app.

| dictionary | source in the first 10 (of 95) | good line first (of 153) | good line in the first 3 | nDCG@3 |
|---|---|---|---|---|
| lexical.db as shipped | 13 | 33 | 42 | 0.229 |
| empty bases removed | 14 | 33 | 42 | 0.231 |
| empty bases removed, each base's surfaces ordered by document frequency | **16** | **36** | **47** | **0.256** |

17 searches improve and 7 get worse. Most of the 7 are searches whose base books now find something, so the search no
longer falls back to all books. Examples: "נעלבין ואינם עולבין" now finds Yoma 23a, "רבי שמעון בר יוחאי ובנו במערה"
Bereshit Rabba 79:6, "להעיד גברא" Bava Kamma 24b. Cost: about 10 ms more per search (real paradigms instead of a single
term).

## Suggested fix

In the generator (cleanest, no runtime cost):
1. Drop bases that no surface (or `surface_extra_base` row) references, or fold them into the base their spelling is a
   surface of.
2. Number each base's surfaces by their document frequency in the text index (or add a rank column and `ORDER BY` it in
   the lookup), so the 32 kept are the commonest. Spelling variants then get in naturally (שלש, שלשה).

Or at runtime in `MagicDictionaryIndex`:
- in `expansionFor`, skip a matching base whose expansion is only itself when another expansion holds the token among its
  inflections;
- order the surfaces by `IndexReader.docFreq` before they are cut to 32.

Pardes currently does the generator-side fix at runtime: it builds a corrected copy of `lexical.db` next to it after each
library install or update, about 10 s, 55 MB.
