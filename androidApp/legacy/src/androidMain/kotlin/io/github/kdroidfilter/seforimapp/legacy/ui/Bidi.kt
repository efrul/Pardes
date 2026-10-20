package io.github.kdroidfilter.seforimapp.legacy.ui

/**
 * Bidirectional text on Android 4.4, whose text layout predates the Unicode 6.3 isolates (U+2066 to U+2069): it draws
 * them as boxes. The main app's texts wrap Latin passages in them (the copyright and §7b lines); here they become the
 * older embeddings, which keep a Latin passage left to right inside Hebrew on every version.
 */
object Bidi {
    private const val LRE = '\u202A'
    private const val RLE = '\u202B'
    private const val PDF = '\u202C'

    fun compat(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    '\u2066', '\u2068' -> LRE
                    '\u2067' -> RLE
                    '\u2069' -> PDF
                    else -> c
                },
            )
        }
    }

    /** A path, a file size or a version number kept left to right inside a Hebrew sentence. */
    fun ltr(text: String): String = "$LRE$text$PDF"

    /**
     * The main app's texts put U+200E (left-to-right mark) before a Latin token that starts with a dot (.part01,
     * .manifest.json). Android 4.4 draws that dot on the token's Hebrew side whatever mark or embedding comes before it
     * ("part01."), so each such token goes without its dot (part01, manifest.json): the file it names reads the same.
     */
    fun marks(text: String): String = MARKED_TOKEN.replace(text) { it.groupValues[1] }

    private val MARKED_TOKEN = Regex("\u200E\\.([\\w.-]+)")
}
