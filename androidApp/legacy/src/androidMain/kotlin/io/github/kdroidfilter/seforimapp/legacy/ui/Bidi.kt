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
}
