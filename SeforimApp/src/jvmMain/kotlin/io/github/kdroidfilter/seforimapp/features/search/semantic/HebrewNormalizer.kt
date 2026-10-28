package io.github.kdroidfilter.seforimapp.features.search.semantic

import java.util.regex.Pattern

object HebrewNormalizer {
    private val HTML_TAG_PATTERN = Pattern.compile("<.*?>")

    fun normalize(text: String): String {
        val stripped = HTML_TAG_PATTERN.matcher(text).replaceAll("")
        val unescaped = unescapeHtml(stripped)

        val sb = StringBuilder(unescaped.length)
        var prevSpace = true

        for (i in 0 until unescaped.length) {
            val c = unescaped[i]
            val code = c.code

            // Nikud diacritics
            if ((code in 0x05B0..0x05BD) || code == 0x05C1 || code == 0x05C2 || code == 0x05C7) {
                continue
            }
            // Teamim cantillation
            if (code in 0x0591..0x05AF) {
                continue
            }
            // Gershayim / Geresh
            if (code == 0x05F3 || code == 0x05F4) {
                continue
            }
            // Maqaf or whitespace
            if (code == 0x05BE || Character.isWhitespace(c)) {
                if (!prevSpace) {
                    sb.append(' ')
                    prevSpace = true
                }
                continue
            }

            // Final-letter normalization
            val mapped =
                when (code) {
                    0x05DA -> 'כ' // ך
                    0x05DD -> 'מ' // ם
                    0x05DF -> 'נ' // ן
                    0x05E3 -> 'פ' // ף
                    0x05E5 -> 'צ' // ץ
                    else -> c
                }

            sb.append(mapped)
            prevSpace = false
        }

        return sb.toString().trim()
    }

    private fun unescapeHtml(str: String): String {
        if (!str.contains('&')) return str
        return str
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }
}
