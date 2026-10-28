package io.github.kdroidfilter.seforimapp.features.search.semantic

import java.util.regex.Pattern

/**
 * The cleaning before the open model's tokenizer: semantic-model/pardes_semantic/clean.py in Kotlin, the same
 * function, checked by `HfTokenizerParityTest` against the fixtures the Python one writes. [ID] is what the model's
 * manifest names (`cleaner`); changing anything here means a new id and a re-embedded corpus.
 *
 * Unlike [HebrewNormalizer] (the legacy model's), final letters stay and niqqud is left to the tokenizer.
 */
object OpenModelCleaner {
    const val ID = "pardes-clean-1"

    private val TAG = Pattern.compile("<[^>]*>")
    private val ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos|nbsp);")

    // Regex escapes, not Kotlin ones: the pattern stays readable (no invisible spaces in the source)
    private val SPACES =
        Pattern.compile(
            "[\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+",
        )
    private const val GERSHAYIM = '\u05F4'
    private const val GERESH = '\u05F3'
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    fun clean(text: String): String {
        var s = TAG.matcher(text).replaceAll(" ")
        s = replaceEntities(s)
        s = s.replace(GERSHAYIM, '"').replace(GERESH, '\'')
        return SPACES.matcher(s).replaceAll(" ").trim(' ')
    }

    private fun replaceEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        val m = ENTITY.matcher(s)
        val sb = StringBuilder(s.length)
        var last = 0
        while (m.find()) {
            sb.append(s, last, m.start())
            sb.append(entity(m.group(1)) ?: m.group())
            last = m.end()
        }
        sb.append(s, last, s.length)
        return sb.toString()
    }

    private fun entity(name: String): String? {
        if (name[0] != '#') return NAMED[name]
        val code = if (name[1] == 'x' || name[1] == 'X') name.substring(2).toInt(16) else name.substring(1).toInt()
        // An invalid or surrogate code point stays as written, as in clean.py
        if (code > 0x10FFFF || code in 0xD800..0xDFFF || code == 0) return null
        return String(Character.toChars(code))
    }
}
