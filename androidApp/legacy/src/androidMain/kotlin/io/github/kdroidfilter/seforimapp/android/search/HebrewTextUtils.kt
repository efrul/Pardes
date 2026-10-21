// This edition's variant of the SeforimLibrary 0.6.1 file search/src/jvmMain/.../HebrewTextUtils.kt (submodule
// upstream/SeforimLibrary at 381fbea), which legacy/build.gradle.kts excludes. Only normalizeHebrew differs, and only
// in how it runs: it gives the desktop's result for every string (see there). The main app compiles the desktop file.
package io.github.kdroidfilter.seforimlibrary.search

/**
 * Utility functions for Hebrew text processing.
 * Includes normalization, diacritic removal, and final letter handling.
 */
object HebrewTextUtils {

    /**
     * Map of Hebrew final letters (sofit) to their base forms.
     */
    val SOFIT_MAP = mapOf(
        'ך' to 'כ',  // U+05DA -> U+05DB
        'ם' to 'מ',  // U+05DD -> U+05DE
        'ן' to 'נ',  // U+05DF -> U+05E0
        'ף' to 'פ',  // U+05E3 -> U+05E4
        'ץ' to 'צ'   // U+05E5 -> U+05E6
    )

    /**
     * Normalizes Hebrew text by:
     * - Removing teamim (cantillation marks) U+0591–U+05AF
     * - Removing nikud (vowel points) U+05B0–U+05BD and related
     * - Replacing maqaf U+05BE with space
     * - Removing gershayim/geresh
     * - Normalizing final letters to base forms
     * - Collapsing whitespace
     *
     * @param input The input string to normalize
     * @return The normalized string
     */
    fun normalizeHebrew(input: String): String {
        if (input.isBlank()) return ""
        val s = input.trim()
        // This edition: the desktop's steps in one pass, without its three regular expressions compiled again at each
        // call (Android's are ICU's, compiled in native code, with a finalizer for each pattern and matcher): the
        // dictionary normalizes every form of every entry a word looks up, and on Android 4.4 that was a third of a
        // first search's processor time. Each step changes chars of its own, and none makes a char that a later step
        // changes, so one pass gives the same string: teamim and nikud removed, maqaf a space, gershayim and geresh
        // removed, finals as base letters. The whitespace is then collapsed by the desktop's own expression, compiled
        // once, when the string may hold a char that \s matches here (mayBeSpace); without one, the expression leaves
        // it as it is.
        val out = StringBuilder(s.length)
        var space = false
        for (c in s) {
            when {
                c in '\u0591'..'\u05AF' -> Unit // teamim
                // nikud, meteg and qamatz qatan
                c in '\u05B0'..'\u05B9' || c in '\u05BB'..'\u05BD' ||
                    c == '\u05C1' || c == '\u05C2' || c == '\u05C7' -> Unit
                c == '\u05BE' -> { // maqaf
                    out.append(' ')
                    space = true
                }
                c == '\u05F4' || c == '\u05F3' -> Unit // gershayim, geresh
                else -> {
                    out.append(baseLetter(c))
                    if (!space && mayBeSpace(c)) space = true
                }
            }
        }
        val text = out.toString()
        return (if (space) WHITESPACE_RUN.replace(text, " ") else text).trim()
    }

    /** The desktop's whitespace step (`"\\s+".toRegex()`), compiled once. */
    private val WHITESPACE_RUN = "\\s+".toRegex()

    /**
     * Every char that `\s` may match: the JVM's ASCII whitespace, ICU's (Android's regular expressions: the Unicode
     * White_Space property, which also holds U+0085 and U+180E before Unicode 6.3) and any space separator. More is
     * harmless: such a string only goes through WHITESPACE_RUN, as the desktop's always does.
     */
    private fun mayBeSpace(c: Char): Boolean =
        Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085' || c == '\u180E'

    /**
     * Replaces Hebrew final letters (sofit) with their base forms.
     *
     * @param text The input text
     * @return Text with final letters replaced
     */
    fun replaceFinalsWithBase(text: String): String {
        if (text.none { it != baseLetter(it) }) return text
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(baseLetter(c))
        return sb.toString()
    }

    /** [c] with a final letter (sofit) as its base form: ך כ, ם מ, ן נ, ף פ, ץ צ. */
    fun baseLetter(c: Char): Char = when (c) {
        '\u05DA' -> '\u05DB'
        '\u05DD' -> '\u05DE'
        '\u05DF' -> '\u05E0'
        '\u05E3' -> '\u05E4'
        '\u05E5' -> '\u05E6'
        else -> c
    }

    /**
     * Checks if a character is a Hebrew diacritic (nikud or teamim).
     *
     * @param c The character to check
     * @return true if the character is a diacritic
     */
    fun isNikudOrTeamim(c: Char): Boolean {
        val code = c.code
        return (code in 0x0591..0x05AF) || // teamim
               (code in 0x05B0..0x05BD) || // nikud + meteg
               (c == '\u05BF') || // rafe
               (c == '\u05C1') || (c == '\u05C2') || // shin and sin dots
               (c == '\u05C4') || (c == '\u05C5') || // upper and lower dots
               (c == '\u05C7')
    }

    /**
     * Strips Hebrew diacritics (nikud and teamim) from text and returns
     * both the plain text and an index map from plain indices to original indices.
     *
     * @param src The source string
     * @return Pair of (plain text, index map)
     */
    fun stripDiacriticsWithMap(src: String): Pair<String, IntArray> {
        val out = StringBuilder(src.length)
        val map = IntArray(src.length)
        var count = 0
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            if (!isNikudOrTeamim(ch)) {
                out.append(ch)
                map[count++] = i
            }
            i++
        }
        val arr = if (count == map.size) map else map.copyOf(count)
        return out.toString() to arr
    }

    /**
     * Strips Hebrew diacritics from text without preserving index mapping.
     *
     * @param text The input text
     * @return Text without diacritics
     */
    fun stripDiacritics(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (!isNikudOrTeamim(ch)) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Maps a plain text index back to the original text index.
     *
     * @param mapToOrig The index map from stripDiacriticsWithMap
     * @param plainIndex The index in the plain text
     * @return The corresponding index in the original text
     */
    fun mapToOrigIndex(mapToOrig: IntArray, plainIndex: Int): Int {
        if (mapToOrig.isEmpty()) return plainIndex
        val idx = plainIndex.coerceIn(0, mapToOrig.size - 1)
        return mapToOrig[idx]
    }
}
