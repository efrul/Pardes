# The search: what comes next (2026-11-10)

The plan that follows the work of 2026-11-08 to 2026-11-10 (pull request #28): the hybrid search's fusion is now
`p12wl` (`HybridSearchEngine.fusedScores`) and the dictionary copy adds the books' defective spellings
(`PardesLexicon`, version 2). The measurements behind each step are in `docs/fusion-compare/`: `IMPLEMENTATION.md`,
`SPELLING.md`, `EXPANSIONS.md`, `OTZARIA_LEXICAL.md`, `SOURCE_TEST.md` and `CASE_LIMONI.md`. The steps are in the order
recommended; the ones marked **decision** are the owner's.

## 1. Pardes for desktop 1.0.9

- **Done:** a pre-release for Windows x64 only (Desktop release with `quick`), built from the branch that holds the
  changelog's 1.0.9 section on top of main. Installed apps do not take a pre-release.
- **Next, after testing:**
  - merge the changelog's 1.0.9 section into main through a pull request (its push deploys the site's
    `changelog-desktop.md`, so it waits for the public release);
  - run Desktop release on main without `quick` or `prerelease`: every platform, and installed apps update;
  - delete the quick pre-release (`gh release delete <tag> -R efrul/Pardes --cleanup-tag -y`): Pardes for Android
    0.3.0 to 0.4.1 reads only the 30 newest releases.

## 2. Per-word forms in the regular search (**decision**: SeforimLibrary is Zayit's)

The largest gain left in the regular search. A word keeps its root's forms in the root's order, cut at 32 (fewer in a
long query), so a word's own spelling can be cut:
- וחיב is at place 204 of חייב's 225;
- דומין is at place 91 of the verb דמה's forms, the base the search picks for דומים.

A word the dictionary does not have gets no forms at all, and its 4-grams do not bridge a spelling:
- "לימוני דמי לפגיעין" finds nothing of its source, Shibbolei HaLeket 90:4, which writes לפגעין;
- לפגיעין is not in the dictionary, and none of its 4-grams (לפגי, פגיע, גיעי, יעינ) is in לפגעין;
- without the yod ("לימוני דמי לפגעין") the app puts the source first.

`PardesLexicon` cannot help in either case. It orders forms per root, not per word (`SPELLING.md`), and it adds
spellings only to words it has. A hook in `LuceneSearchEngine.kt` that puts a word's own forms right after it can: the
measurement build does it (`docs/fusion-compare/tools/ExtraForms.kt`, `extra_forms_patch.py`). Otzaria's rule (an
inner yod left out) turns לפגיעין into לפגעין.

1. **Measure first**, in the measurement build only, which needs no change to SeforimLibrary:
   - the forms: Otzaria's spelling forms (an inner ו or י added or left out), plus the plural ים↔ין, which no rule
     covers yet (`CASE_LIMONI.md`);
   - the sets: the four (the 157 source searches, eval-100, eval-real, the graded feedback) and the six forms of
     "לימוני דומים לפגעי";
   - with spelling alone, the lexical side found the source in its first ten in 151 of 157 quotes instead of 137, and
     `p12wl` went from 114 / 145 / 154 to 115 / 148 / 156 (first / in 3 / in 10).
2. **Then the owner decides** between three ways:
   - propose the hook to Zayit, with an English report like `docs/ZAYIT_LEXICAL_REPORT.md`. The owner sends it:
     sessions never open a pull request on Zayit;
   - keep a Pardes copy of SeforimLibrary, with every Zayit merge to carry it;
   - leave it.

   The same report can name the base `expansionFor` picks: the largest, which gave דומים the verb's paradigm and not
   the adjective's.

## 3. The new grades into pardes-semantic

- **What:** `docs/fusion-compare/eval_real/grades_new.tsv` holds 160 lines of eval-real that Claude graded blind to the
  version, in the format of pardes-semantic's `eval-real/grades.tsv`; none of them is graded there yet.
- **Why:** added there, every later measurement starts from a fuller pool.
- **Who:** a session with write access to efrul/pardes-semantic, after the owner looks them over.
- **Note:** the eval-real numbers in `IMPLEMENTATION.md` and `SPELLING.md` are computed with them.

## 4. Pardes for Android

Android's copies are behind desktop's (`androidApp/docs/PARITY.md`, and the change map's rows):
- `search/PardesLexicon.kt` is at version 1: the same loop as desktop's `surfaces()` and `defectiveForms`, and the
  version raised;
- `search/semantic/HybridSearchEngine.kt` fuses by RRF alone: desktop's `fusedScores`, with the library's lines read
  through the app's connection.

Port both, build, check on the emulator (the `zayit-android` skill), then update PARITY.md and the change map.

## 5. The next semantic pack: one re-embedding for two fixes

Both fixes need the corpus embedded again (about 4.5 hours on Kaggle) and the vector index built again (about 2 hours),
so they go in one pack:
- **short lines with their siman's heading** (`SEARCH_ORDER_ISSUES.md` issue 1's way forward): a short Shulchan Aruch
  seif that is the answer ("תלמוד תורה גדול מכבוד אב ואם") is left out by `p12wl`'s rule. Two variants that keep such
  lines gained a single search (`IMPLEMENTATION.md`);
- **the "(א)" numbers out of the cleaning** (issue 3, `pardes-clean-2`): the number at the start of a line moves a
  short verse far down the dense side. The change goes in `clean.py` and `OpenModelCleaner.kt`, with new parity
  fixtures.

Measure on eval-100 and eval-real against the current pack before releasing it.

## 6. Open questions, measured or not

- **Searches in a chosen book** (43% of the history, issue 10) have no grades. `p12wl`'s word-for-word weight and window
  apply there, its short-line rule does not.
- **The fusion buries a short, exact lexical list.**
  - "הלימוני דמי לפגעין": the regular search finds three lines, all of them the source and its copies, at places 1 to 3,
    and the app shows them at 41.
  - The line holds דומין, not דמי, so it is not word for word, and the dense side does not find it.
  - Without the ה the dense side finds it, at 34, and the app puts it first: that is how fragile it is.
  - An idea not measured yet: the lexical side's own scores. Its phrase bonus already rewards the query's words found
    together in their dictionary forms, while RRF looks only at places. When the lexical first hit is far ahead of its
    second, it could weigh more. Measure it on the four sets; a short lexical list weighing 3 regardless was worse
    (`CASE_LIMONI.md`).
- **The window does not read abbreviations or other forms.** "אין ישנים בב"ה" does not count בית הכנסת. Three fusion
  variants that read endings or count a short lexical list (`CASE_LIMONI.md`) were mixed or worse; the fix belongs in
  the regular search.
- **Measured and not to do:**
  - synonyms in the automatic expansion (every ordering a little lower; as a choice the searcher makes, at most);
  - Otzaria's phrase-only search in place of Pardes's (it finds nothing in two thirds of the searches for ideas);
  - a lighter dense weight (×2, ×1.5: no gain);
  - score fusion (CombMNZ, min-max: far lower on the real searches).
