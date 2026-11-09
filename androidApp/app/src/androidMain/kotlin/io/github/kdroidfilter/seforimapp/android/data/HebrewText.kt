package io.github.kdroidfilter.seforimapp.android.data

import java.time.LocalDate

/** Hebrew numerals and weekday names used by the tools (same spellings as desktop); months and parshiyos come from KosherKotlin's HebrewDateFormatter. */
object HebrewText {
    /** Gematria of a numeral written in letters ("כה", "ט״ו" → 15); 0 when it is not one. */
    fun value(s: String): Int {
        var sum = 0
        for (ch in s) {
            sum += when (ch) {
                'א' -> 1; 'ב' -> 2; 'ג' -> 3; 'ד' -> 4; 'ה' -> 5; 'ו' -> 6; 'ז' -> 7; 'ח' -> 8; 'ט' -> 9
                'י' -> 10; 'כ', 'ך' -> 20; 'ל' -> 30; 'מ', 'ם' -> 40; 'נ', 'ן' -> 50; 'ס' -> 60; 'ע' -> 70; 'פ', 'ף' -> 80; 'צ', 'ץ' -> 90
                'ק' -> 100; 'ר' -> 200; 'ש' -> 300; 'ת' -> 400
                '"', '\'', '״', '׳' -> 0
                else -> return 0
            }
        }
        return sum
    }

    val gregorianMonths = listOf("ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר")

    /** "13 באוקטובר 2026". */
    fun gregorian(d: LocalDate): String = "${d.dayOfMonth} ב${gregorianMonths[d.monthValue - 1]} ${d.year}"

    /** Removes nikud/teamim, quotes and geresh so titles and TOC texts can be compared loosely. */
    fun normalize(s: String): String = buildString {
        for (ch in s) {
            when {
                ch == '־' -> if (isNotEmpty() && last() != ' ') append(' ')
                ch.code in 0x0591..0x05C7 -> Unit
                ch == '"' || ch == '\'' || ch == '״' || ch == '׳' || ch == '`' -> Unit
                ch.isWhitespace() -> if (isNotEmpty() && last() != ' ') append(' ')
                else -> append(ch)
            }
        }
    }.trim()
}
