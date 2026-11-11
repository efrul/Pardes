package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.seforimapp.siddur.SiddurPart.MAARIV
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart.MINCHA
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart.SHACHARIT
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

private val weekdayNames = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
private val hebrewWeek = (listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.take(6)).zip(weekdayNames).associate { (day, name) ->
    name to day
}
private val weekdaySong = Regex("^ב(ראשון|שני|שלישי|רביעי|חמישי|שישי|ששי) בשבת:?$")
private val namedSong = Regex("^מזמור ליום (ראשון|שני|שלישי|רביעי|חמישי|שישי|ששי|שבת)(?: |:|$)")
private val sourceReference = Regex("\\([^)]*(?:תהלים|תהילים)[^)]*\\)")
private val spaces = Regex("[\\s\\u2009\\u202f]+")

internal fun prayerText(text: String): String =
    text.replace(sourceReference, "").replace("׀", "").replace(spaces, " ").trim()

private val hoshienuStart = Regex("<b>([^<>]+)</b>")
private val hoshienuReference = Regex("""\s*<small>וממשיך הושיענו</small>\s*$""")

/** Edot HaMizrach prints the common ending at the end of the Friday psalm. */
internal fun List<SiddurLine>.splitHoshienu(showAll: Boolean): List<SiddurLine> {
    val heading = indexOfFirst { it.kind == SiddurLine.Kind.HEADING && plain(it.html) == "שיר של יום" }
    if (heading < 0) return this
    val end = (heading + 1 until size).firstOrNull { this[it].kind == SiddurLine.Kind.HEADING } ?: size
    val friday = (heading + 1 until end).firstOrNull { plain(this[it].html).startsWith("מזמור ליום ששי ") }
        ?.plus(1) ?: return this
    if (friday >= end) return this
    val row = this[friday]
    val start = hoshienuStart.findAll(row.html).firstOrNull { plain(it.groupValues[1]) == "הושיענו" }?.range?.first ?: return this
    val ending = row.html.substring(start)
    if (!prayerText(plain(ending)).startsWith("הושיענו יהוה אלהינו וקבצנו")) return this
    return buildList {
        for ((index, line) in this@splitHoshienu.withIndex()) {
            when {
                index == friday -> {
                    add(row.copy(html = row.html.take(start).trimEnd()))
                    add(row.copy(html = ending))
                }
                !showAll && index in heading until friday -> add(line.copy(html = line.html.replace(hoshienuReference, "")))
                else -> add(line)
            }
        }
    }
}

/** Some editions print the condition in a rubric, with the body under the same TOC entry. */
internal fun calendarPassages(
    lines: List<SiddurLine>,
    text: List<String>,
    part: SiddurPart?,
    nusach: Nusach?,
    conditions: List<MutableSet<String>>,
) {
    val family = part?.familyOf()
    if (family !in setOf(SHACHARIT, MINCHA, MAARIV) && part != SiddurPart.LEIL_YOM_TOV) return
    val labels = text.map(::prayerText)
    fun mark(start: Int, end: Int, vararg required: String) {
        for (index in start until end.coerceAtMost(lines.size)) conditions[index].addAll(required)
    }
    fun markUntil(start: Int, end: (String) -> Boolean, vararg required: String) {
        val boundary = (start + 1 until lines.size).firstOrNull { end(labels[it]) }
        if (boundary != null) mark(start, boundary, *required)
    }
    var inDailySong = false
    var inAvinuMalkeinu = false
    var inHaftarah = false
    for ((index, label) in labels.withIndex()) {
        if (lines[index].kind == SiddurLine.Kind.HEADING || label == "שיר של יום") {
            inDailySong = label == "שיר של יום"
        }
        if (lines[index].kind == SiddurLine.Kind.HEADING) {
            inAvinuMalkeinu = label == "אבינו מלכנו"
            inHaftarah = label in setOf("הפטרה", "ברכות ההפטרה")
        }
        val day = if (inDailySong) weekdaySong.find(label) ?: namedSong.find(label) else null
        if (day != null) {
            val name = day.groupValues[1].replace("ששי", "שישי")
            val weekday = hebrewWeek.getValue(name)
            // The traditional Hebrew rubric is followed by an introduction and a psalm. In Edot
            // HaMizrach the rubric and introduction share a line. The following Hoshienu is common.
            val end = index + if (weekdaySong.matches(label)) 3 else 2
            mark(index, end, "weekday_${weekday.name.lowercase()}")
            if (labels.getOrNull(end)?.startsWith("(הושיענו") == true) mark(end, end + 1, "printed_cross_reference")
        }
        when {
            inDailySong && label == "בצום גדליה ובעשרה בטבת אומרים" -> mark(index, index + 2, "psalm_83")
            inDailySong && label == "למחרת יום הכיפורים אומרים" -> mark(index, index + 2, "psalm_85")
            inDailySong && label == "בחנוכה אומרים" -> mark(index, index + 2, "chanuka")
            inDailySong && label.startsWith("בחנוכה אומרים מזמור") -> mark(index, index + 1, "chanuka")
            inDailySong && label == "בתענית אסתר ובפורים אומרים" -> mark(index, index + 2, "psalm_22")
            inDailySong && label == "בשבעה עשר בתמוז אומרים" -> mark(index, index + 2, "psalm_79")
            inDailySong && label == "בבית האבל אומרים" -> mark(index, index + 3, "beit_avel")
            label.startsWith("בראש חדש אחר שיר של יום אומרים ברכי נפשי") ->
                markUntil(index, { it.startsWith("מראש חדש אלול") || it.startsWith("סדר קריאת התורה") }, "rosh_chodesh")
            label.startsWith("מראש חדש אלול עד אחר הושענא רבה מוסיפים מזמור זה") ->
                mark(index, index + 5, "ledavid_ori")
            label.startsWith("מנהג טוב לומר אחר כל תפלה מזמור לדוד") -> mark(index, index + 3, "ledavid_ori")
            label.startsWith("לדוד יהוה אורי וישעי") && nusach == Nusach.EDOT_HAMIZRACH ->
                mark(index, index + 1, "ledavid_ori")
            label.startsWith("בבית האבל בבוקר אחר שיר של יום ואחר מנחה") ->
                markUntil(index, { it.startsWith("ובימים שאין בהם תחנון") || it in setOf("אבינו מלכנו", "קוה") },
                    "beit_avel", "calendar_tachanun")
            label == "ובימים שאין בהם תחנון אומרים זה:" -> markUntil(index, { it == "קוה" }, "beit_avel", "!calendar_tachanun")
            label.startsWith("בשבת אחר סוכות מתחילין לומר ברכי נפשי") ->
                markUntil(index, { it == "פרקי אבות" }, "winter_psalms")
            label.startsWith("בימים שאין אומרים בהם תחנון בחול") && "צדקתך" in label -> {
                mark(index, index + 2, "tzidkatcha")
                if (labels.getOrNull(index + 2) == "יש שנוהגים לומר בסדר הזה:") {
                    mark(index + 2, index + 4, "occasional_blessings")
                }
            }
            label.startsWith("צדקתך כהררי") && family == MINCHA -> mark(index, index + 1, "tzidkatcha")
            label.startsWith("בימים שאין אומרים תחנון אין אומרים תפלה לדוד") -> mark(index + 1, index + 2, "tachanun")
            label.startsWith("תפלה לדוד הטה") -> mark(index, index + 1, "tachanun")
            nusach == Nusach.SEFARD && family == SHACHARIT &&
                (label.startsWith("בית יעקב") || label.startsWith("שיר המעלות לדוד לולי")) ->
                mark(index, index + 1, "lamenatzeach")
            label.startsWith("למנצח מזמור לדוד") && "יענך" in label || label.startsWith("ביום שאין אומרים בו תחנון מדלגים למנצח") ->
                mark(index, index + 1, "lamenatzeach")
            label.startsWith("אל ארך אפים ורב חסד ואמת") || label.startsWith("אל ארך אפים ומלא רחמים") ->
                mark(index, index + 1, "lamenatzeach")
            label == "בימים שאין בהם תחנון אומרים" -> mark(index, index + 2, "!tachanun")
            label == "ביום שאין בו תחנון אומרים:" -> mark(index, index + 2, "!tachanun")
            label.startsWith("לפני הוצאת ספר תורה ביום שיש בו תחנון") -> mark(index, index + 3, "tachanun")
            label.startsWith("בשני וחמישי יאמר והוא רחום") && nusach != Nusach.EDOT_HAMIZRACH -> {
                val end = (index + 1 until labels.size).firstOrNull {
                    labels[it] in setOf("נפילת אפיים", "נפילת אפים") || labels[it].startsWith("שומר ישראל שמור")
                }
                if (end != null) {
                    mark(index, end, "tachanun", "mon_thu")
                    if (nusach == Nusach.ASHKENAZ) {
                        val endPiyyut = (end + 1 until labels.size).firstOrNull { labels[it] == "שומר ישראל" }
                        if (endPiyyut != null) mark(end + 3, endPiyyut, "tachanun", "mon_thu")
                    }
                }
            }
            inAvinuMalkeinu && (label.startsWith("בעשית:") || label == "בעשרת ימי תשובה:") ->
                mark(index, avinuVariantEnd(index, label, labels), "aseret_yemei_teshuva")
            inAvinuMalkeinu && (label == "בתענית ציבור" || label.startsWith("בתענית ציבור:")) ->
                mark(index, avinuVariantEnd(index, label, labels), "fast_avinu_variant")
            label == "בעשרת ימי תשובה אומרים:" && labels.getOrNull(index + 1)?.startsWith("אבינו מלכנו") == true ->
                markUntil(index, { it.startsWith("בימים שאין בהם תחנון") }, "aseret_yemei_teshuva")
            label == "אבינו מלכנו בשבת תשובה אומרים:" ->
                markUntil(index, { it.startsWith("יהי שם יהוה מברך") }, "aseret_yemei_teshuva")
            teshuvaAddition.matches(label) -> mark(index, index + 1, "aseret_yemei_teshuva")
            label.startsWith("בימי שני וחמישי מוסיפים") -> markUntil(index, { it == "קריאת התורה" }, "mon_thu")
            label.startsWith("בימים שני וחמישי אין אומרים את הקדיש הזה") -> mark(index, index + 2, "!mon_thu", "minyan")
            family == MINCHA && label.startsWith("בערב שבת אין אומרים") && "למנצח" in label ->
                mark(index, index + 2, "!weekday_friday", "!chol_hamoed")
            family == MINCHA && label.startsWith("בערב שבת אומרים מזמור") ->
                mark(index, index + 2, "weekday_friday")
            family == MINCHA && label == "בתענית ציבור אומרים" && labels.getOrNull(index + 1)?.startsWith("תפלה לעני") == true ->
                mark(index, index + 2, "taanit")
            inHaftarah && label.startsWith("בשבת, ובשבת חול המועד פסח אומרים:") ->
                mark(index, index + 1, "sat", "!festival_haftarah", "!rosh_hashana", "!yom_kippur")
            inHaftarah && label.startsWith("בשלוש רגלים ובשבת חוהמ סוכות אומרים:") ->
                mark(index, index + 1, "festival_haftarah")
            inHaftarah && label.startsWith("בכל שבתות השנה חוץ משבת חול המועד וחגים אומרים זה:") ->
                mark(index, index + 2, "sat", "!festival_haftarah", "!rosh_hashana", "!yom_kippur")
            inHaftarah && label.startsWith("בג רגלים וגם בשבת חוהמ אומרים זה:") ->
                markUntil(index, { it.startsWith("בראש השנה") }, "festival_haftarah")
            inHaftarah && label.startsWith("בראש השנה:") -> mark(index, index + 1, "rosh_hashana")
            inHaftarah && label.startsWith("בראש השנה אומרים") -> mark(index, index + 2, "rosh_hashana")
            inHaftarah && label.startsWith("ביום הכיפורים אומרים") -> mark(index, index + 2, "yom_kippur")
        }
        if (part?.festival == true || inHaftarah) festivalLineFlag(label)?.let { mark(index, index + 1, it) }
    }
}

private val festivalLabel = Regex("""^([^:]+?)(?:\s+-\s+|:)""")
private fun festivalLineFlag(label: String): String? =
    when (festivalLabel.find(label)?.groupValues?.get(1)) {
        "בפסח", "לפסח" -> "pesach"
        "בשבועות", "לשבועות" -> "shavuot"
        "בסוכות", "לסוכות", "סוכות", "לסכות" -> "sukkot"
        "בשמיני עצרת", "בשמ ע ושת", "בשמע ושת", "בשמע ובשת", "בש ע", "בשע", "לשמע ולשת" -> "shmini_atzeret"
        else -> null
    }

private val teshuvaAddition =
    Regex("^(?:בעשית|בעשת|בשת|בעשרת ימי תשובה):? (?:זכרנו לחיים|מי כמוך אב|וכתב לחיים|בספר חיים|המלך הקדוש).*$")

private fun avinuVariantEnd(
    index: Int,
    label: String,
    labels: List<String>,
): Int =
    when {
        "אבינו מלכנו" in label -> index + 1
        labels.getOrNull(index + 1)?.startsWith("אבינו מלכנו כתבנו בספר") == true ||
            labels.getOrNull(index + 1)?.startsWith("אבינו מלכנו זכרנו לחיים") == true -> index + 6
        else -> index + 2
    }

private val nightLabel = Regex("""^\s*<small>\s*(?:ליל|יום)\s+(?:[א-ז]['׳]?|שבת)\s*</small>\s*""")
private val anaBekoachVerse = Regex("^(?:ליל|יום) (א|ב|ג|ד|ה|ו|ז|שבת) ")

internal fun List<SiddurLine>.bedtimePassages(
    date: LocalDate,
    flags: Set<String>,
    showAll: Boolean,
    afterMidnight: Boolean,
): List<SiddurLine> {
    val labels = map { prayerText(plain(it.html)) }
    val vidui = labels.indexOfFirst { it.startsWith("יעמוד ויאמר וידוי") }
    val omitVidui = "calendar_tachanun" !in flags || !afterMidnight && ("havdala" in flags || "after_rosh_chodesh" in flags)
    val introduction = labels.indexOfFirst { it.startsWith("אחר כך יאמר") && "כולו בכל לילה" in it }
    val first = introduction + 1
    val last = first + 7
    val verses = if (introduction >= 0 && last <= size) {
        labels.subList(first, last).mapNotNull { anaBekoachVerse.find(it)?.groupValues?.get(1)?.replace("ז", "שבת") }
    } else {
        emptyList()
    }
    val verseIndex = verses.indexOf(listOf("א", "ב", "ג", "ד", "ה", "ו", "שבת")[(date.dayOfWeek.ordinal + 1) % 7])
    val selected = (first + verseIndex).takeIf { verses.toSet().size == 7 && verseIndex >= 0 }
    val repeat = selected?.let {
        val verse = this[it].html.replaceFirst(nightLabel, "").substringBefore("<small>").trim()
        this[it].copy(html = List(3) { verse }.joinToString("<br>"), special = true)
    }
    return buildList {
        for ((index, line) in this@bedtimePassages.withIndex()) {
            val inVidui = vidui >= 0 && index in vidui..vidui + 1
            if (inVidui && omitVidui) {
                if (showAll) add(line.copy(skipped = true))
            } else if (repeat != null && !showAll && index in first until last) {
                add(line.copy(html = line.html.replaceFirst(nightLabel, "")))
            } else {
                add(line)
            }
            if (repeat != null && index == last - 1) add(repeat)
        }
    }
}
