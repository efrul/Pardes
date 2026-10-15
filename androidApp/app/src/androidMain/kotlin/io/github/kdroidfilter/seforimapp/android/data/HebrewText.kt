package io.github.kdroidfilter.seforimapp.android.data

import java.time.LocalDate

/** Hebrew numerals and weekday names used by the tools (same spellings as desktop); months and parshiyos come from KosherKotlin's HebrewDateFormatter. */
object HebrewText {
    private val ones = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val tens = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val hundreds = arrayOf("", "ק", "ר", "ש")

    /** Plain letters, thousands dropped: 25 → "כה", 15 → "טו", 5787 → "תשפז". */
    fun letters(n: Int): String {
        if (n <= 0) return ""
        var x = n % 1000
        val sb = StringBuilder()
        while (x >= 400) {
            sb.append('ת')
            x -= 400
        }
        if (x >= 100) {
            sb.append(hundreds[x / 100])
            x %= 100
        }
        when (x) {
            15 -> sb.append("טו")
            16 -> sb.append("טז")
            else -> {
                sb.append(tens[x / 10])
                sb.append(ones[x % 10])
            }
        }
        return sb.toString()
    }

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

    /** With geresh / gershayim: 3 → "ג׳", 25 → "כ״ה". */
    fun numeral(n: Int): String {
        val s = letters(n)
        return when {
            s.isEmpty() -> ""
            s.length == 1 -> s + "׳"
            else -> s.dropLast(1) + "״" + s.last()
        }
    }

    /** "התשפ״ז", as in the desktop calendar header. */
    fun year(y: Int): String = "ה" + numeral(y)

    /** Day of week 1 (Sunday) … 7 (Shabbat). */
    val weekdays = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
    val weekdaysShort = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

    fun weekday(dow: Int): String = weekdays[(dow - 1).coerceIn(0, 6)]

    /** "יום שלישי" / "שבת". */
    fun dayLabel(dow: Int): String = if (dow == 7) "שבת" else "יום ${weekday(dow)}"

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
