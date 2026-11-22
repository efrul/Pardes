package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser

// Pardes (request 43 of docs/DESKTOP_STAGE3_PLAN.md): the files of a folder of scanned tractates named otherwise than
// the tractates, "18 - עבודה זרה הוריות עדיות מורחב - מחובר": a number before the name, words after it, and several
// tractates joined in one file.

/** How the name of a scan's file fits a tractate, the best first. */
enum class ScanFit {
    /** Named the tractate itself (`ברכות.pdf`, `03 ברכות.pdf`). */
    EXACT,

    /** The tractate's name among other words, and no other tractate's. */
    ALONE,

    /** The tractate's name among other tractates': several joined in the file. */
    JOINED,
}

/**
 * Scan files' names read as the tractates they hold: their words, with nikud, quotes and punctuation left aside, and
 * the numbers at their start. A tractate is named by its words as a run of whole words, so that בבא קמא is not found in
 * בבא מציעא nor ברכות in מכות; else so with the letters yod and vav left aside (ערובין for עירובין).
 */
internal object ScanNames {
    /** The words of the file name [fileName] (its extension left aside), without the numbers at its start. */
    fun words(fileName: String): List<String> =
        ReferenceParser
            .normalizeName(fileName.replace(PDF_EXTENSION, ""))
            .split(NOT_A_WORD)
            .filter { it.isNotEmpty() }
            .dropWhile { word -> word.all { it.isDigit() } }

    /** Where the words of [tractate] stand in [words] as a run of whole words (from 0), else -1. */
    fun indexIn(
        words: List<String>,
        tractate: String,
    ): Int {
        val name = words(tractate)
        if (name.isEmpty()) return -1
        return runOf(words, name).takeIf { it >= 0 } ?: runOf(words.map(::plain), name.map(::plain))
    }

    /**
     * How the file named [fileName] fits [tractate], the other tractates being [tractates]; null when it does not hold
     * it.
     */
    fun fit(
        fileName: String,
        tractate: String,
        tractates: Collection<String>,
    ): ScanFit? {
        val words = words(fileName)
        return when {
            indexIn(words, tractate) < 0 -> null
            words == words(tractate) -> ScanFit.EXACT
            tractates.any { it != tractate && indexIn(words, it) >= 0 } -> ScanFit.JOINED
            else -> ScanFit.ALONE
        }
    }

    /** The tractates of [tractates] that the file named [fileName] holds, in their order in its name. */
    fun tractatesIn(
        fileName: String,
        tractates: Collection<String>,
    ): List<String> {
        val words = words(fileName)
        return tractates
            .map { it to indexIn(words, it) }
            .filter { it.second >= 0 }
            .sortedBy { it.second }
            .map { it.first }
    }

    // The first place where [name]'s words follow each other in [words], else -1
    private fun runOf(
        words: List<String>,
        name: List<String>,
    ): Int = (0..words.size - name.size).firstOrNull { start -> name.indices.all { words[start + it] == name[it] } } ?: -1

    // A word without yod and vav, which spellings add or leave out
    private fun plain(word: String): String = word.filterNot { it == 'י' || it == 'ו' }

    private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val PDF_EXTENSION = Regex("\\.pdf$", RegexOption.IGNORE_CASE)
}
