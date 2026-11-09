package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils

private val SPACES = Regex("[ \\t\\u00A0]+")
private const val GERESH = "'׳"
private const val GERSHAYIM = "\"״"

/**
 * [text] as its words alone, for a copy without its marks: no nikud, no teamim and no punctuation, each mark between
 * two words a space (a maqaf, a dash), its lines kept. A geresh or gershayim that is part of a word stays (רש״י, וגו׳).
 */
fun lettersOnly(text: String): String =
    HebrewTextUtils
        .removeAllDiacritics(text)
        .lines()
        .joinToString("\n") { line ->
            buildString(line.length) {
                line.forEachIndexed { i, c ->
                    when {
                        c.isLetterOrDigit() || isInWord(line, i) -> append(c)
                        // A mark set on a letter, or a direction mark, is no gap between two words
                        c.isJoinedMark() -> Unit
                        else -> append(' ')
                    }
                }
            }.replace(SPACES, " ").trim()
        }

/** Whether [text] has anything [lettersOnly] takes out: a copy of its words alone is then worth offering. */
fun hasMoreThanLetters(text: String): Boolean = text.any { !it.isLetterOrDigit() && !it.isWhitespace() }

// A geresh after a letter (ר׳, וגו׳) or gershayim between two (רש״י): the word's own spelling, not a quotation mark
private fun isInWord(
    line: String,
    i: Int,
): Boolean {
    val before = line.getOrNull(i - 1)?.isLetter() == true
    return when (line[i]) {
        in GERESH -> before
        in GERSHAYIM -> before && line.getOrNull(i + 1)?.isLetter() == true
        else -> false
    }
}

private fun Char.isJoinedMark(): Boolean =
    when (category) {
        CharCategory.NON_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.FORMAT -> true
        else -> false
    }
