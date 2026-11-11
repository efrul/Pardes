package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.seforimapp.siddur.Nusach.*
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart.*

private val marks = Regex("[\u0591-\u05BD\u05BF-\u05C7'׳״\"]")
private val tags = Regex("<[^>]+>")
private val spaces = Regex("[\\s\\u00a0]+")

internal fun plain(html: String): String =
    html.replace(tags, "").replace(marks, "").replace('־', ' ').replace(spaces, " ").trim()

/** An outline key matches complete headings. A slash separates ancestors, counted from the end of the path. */
internal class Outline(
    private val book: SourceBook,
) {
    private data class Node(
        val heading: SourceHeading,
        val path: List<String>,
        val end: Int,
    )

    private val nodes: List<Node> =
        buildList {
            val stack = mutableListOf<SourceHeading>()
            val headings = book.headings.sortedWith(compareBy(SourceHeading::index, SourceHeading::level))
            for ((index, heading) in headings.withIndex()) {
                while (stack.lastOrNull()?.let { it.level >= heading.level } == true) stack.removeLast()
                stack.add(heading)
                val end = headings.drop(index + 1).firstOrNull { it.level <= heading.level }?.index ?: book.lines.size
                add(Node(heading, stack.map { plain(it.title) }, end))
            }
        }

    fun read(
        keys: List<String>,
        flags: Set<String>,
        showAll: Boolean,
    ): List<SiddurLine> =
        keys.flatMap { key ->
            val path = key.split('/').map(::plain)
            val root = nodes.firstOrNull { it.path.takeLast(path.size) == path } ?: return@flatMap emptyList()
            val stack = mutableListOf<Pair<Node, String?>>()
            val headings = nodes.filter { it.heading.index in root.heading.index until root.end }.groupBy { it.heading.index }
            (root.heading.index until root.end).flatMap row@{ index ->
                val row = book.lines[index]
                val nodesAtRow = headings[index].orEmpty()
                for (node in nodesAtRow) {
                    while (stack.lastOrNull()?.first?.heading?.level?.let { it >= node.heading.level } == true) stack.removeLast()
                    val label = node.path.last()
                    val rule = when {
                        node == root -> null
                        root.path.last() == "מוסף לשבת" && label == "לשבת" -> "!rosh_chodesh"
                        label == "לשבת ראש חודש" -> "rosh_chodesh"
                        label == "ברכי נפשי" && root.path == listOf("סידור אשכנז", "שבת", "מנחה") -> "winter_psalms"
                        label == "וידוי" && "erev_yom_kippur" in flags -> null
                        "selichot" in flags && condition(label) == "tachanun" -> null
                        else -> condition(label)
                    }
                    stack.add(node to rule)
                }
                val label = plain(row)
                val mincha = root.path.last() in setOf("מנחה לימות החול", "מנחה לימי החול")
                if (mincha && (label.startsWith("השץ אומר קדיש שלם") || label.startsWith("ואומר החזן קדיש תתקבל"))) {
                    stack.removeAll { it.second == "tachanun" }
                }
                if (label.startsWith("בבית האבל בבוקר אחר שיר של יום ואחר מנחה")) {
                    stack.removeAll { it.second == "ledavid_ori" }
                }
                if (label.startsWith("אשרי יושבי ביתך")) {
                    stack.removeAll { it.second == "av_harachamim" }
                }
                val conditions = stack.mapNotNull { it.second }
                val text = label.substringBefore(':')
                val lineCondition = if (nodesAtRow.isEmpty() && (text != "וידוי" || "erev_yom_kippur" !in flags)) {
                    condition(text)?.takeUnless { it == "tachanun" && "selichot" in flags }
                } else {
                    null
                }
                val applies = (conditions + listOfNotNull(lineCondition)).all { flag ->
                    if (flag.startsWith("!")) flag.drop(1) !in flags else flag in flags
                }
                // These are their own sections in the day book.
                if ("separate" in conditions) return@row emptyList()
                // Keep skipped rows until annotateCorpus has matched any rubric that uses them as anchors.
                val node = nodesAtRow.lastOrNull()
                if (node != null) {
                    val heading = SiddurLine(
                        html = node.heading.title,
                        kind = SiddurLine.Kind.HEADING,
                        headingLevel = (node.heading.level - root.heading.level + 2).coerceIn(2, 6),
                        special = applies && conditions.any { it in specialFlags },
                        skipped = !applies,
                    )
                    if (row.trimStart().startsWith("<h") || plain(row) == plain(node.heading.title)) return@row listOf(heading)
                    return@row listOf(heading, SiddurLine(row, skipped = !applies)).filter { it.html.isNotBlank() }
                }
                if (applies && showAll) gevurotAlternatives(row, flags)?.let { return@row it }
                val content = if (applies) seasonalLine(row, flags, showAll) else row
                if (plain(content).isEmpty()) return@row emptyList()
                listOf(SiddurLine(content, special = applies && (content != row || lineCondition in specialFlags), skipped = !applies))
            }
        }
}

private val specialFlags =
    setOf(
        "yaale_veyavo", "al_hanisim", "avinu_malkenu", "hallel", "taanit", "rosh_chodesh", "ledavid_ori",
        "birkat_hachodesh", "fast_announcement",
    )

private fun condition(label: String): String? =
    when {
        label in setOf("ספירת העומר", "קריאת שמע על המיטה", "ברכת הלבנה", "קידוש לבנה", "קדוש לבנה") -> "separate"
        label.startsWith("קדיש ") || label in setOf("קדיש", "ברכו", "קדושה", "חזרה", "חזרת השץ", "מודים דרבנן") -> "minyan"
        label in setOf("תחנון", "וידוי", "וידוי ויג המידות", "נפילת אפים", "נפילת אפיים", "שומר ישראל") -> "tachanun"
        label == "אל ארך אפים" -> "lamenatzeach"
        label == "אבינו מלכנו" -> "avinu_malkenu"
        label in setOf("לשני וחמישי", "לימי שני וחמישי") -> "tachanun"
        label == "מזמור לתודה" -> "mizmor_letoda"
        label == "למנצח" -> "lamenatzeach"
        label.startsWith("יעלה ויבא") || label.startsWith("יעלה ויבוא") -> "yaale_veyavo"
        label in setOf("על הנסים", "על הניסים") -> "al_hanisim"
        label.startsWith("קריאה לתענית ציבור") -> "taanit"
        label in setOf("קריאת התורה", "סדר קריאת התורה בשבת", "קריאת התורה לראש חדש") -> "torah_reading"
        label in setOf("הפטרה", "ברכות ההפטרה", "ברכות לאחר ההפטרה") -> "haftarah"
        label.startsWith("תוספות למוצאי שבת") || label == "ערבית למוצאי שבת" -> "motzaei_shabbat"
        label == "ברכי נפשי" -> "rosh_chodesh"
        label in setOf("לדוד", "לדוד ה") -> "ledavid_ori"
        label in setOf("ברכת החודש", "ברכת החדש", "הכרזת ראש חדש", "הכרזת ראש חודש") -> "birkat_hachodesh"
        label == "הכרזת תענית" -> "fast_announcement"
        label == "אב הרחמים" -> "av_harachamim"
        label in setOf("צדקתך", "צדקתך צדק") -> "tzidkatcha"
        label == "פרקי אבות" -> "pirkei_avot"
        label == "מברכין בהב" -> "behab"
        label in occasionalBlessingHeadings ->
            "occasional_blessings"
        label in setOf("יום ראשון של ראש השנה", "ליום ראשון של ראש השנה", "קריאה ליום ראשון של ראש השנה",
            "הפטרה ליום ראשון של ראש השנה", "קריאה ליום ראשון", "הפטרה ליום א") -> "!rh_second_day"
        label in setOf("יום שני של ראש השנה", "ליום שני של ראש השנה", "קריאה ליום שני של ראש השנה",
            "הפטרה ליום שני של ראש השנה", "קריאה ליום שני", "הפטרה ליום שני") -> "rh_second_day"
        label in setOf("שבת", "לשבת", "ויכולו", "ויכלו", "מגן אבות", "סדר קבלת שבת", "קבלת שבת") -> "sat"
        label in setOf("זימון", "ברכת הזימון") -> "zimun"
        label == "הרחמן לברית מילה" -> "seudat_mila"
        else -> null
    }

private val occasionalBlessingHeadings = setOf(
    "לשבת חתן", "מי שברך ליולדת", "לנער בר מצוה", "סדר זבד הבת", "מי שברך לחולה", "ברכת הגומל",
    "לחולים", "ליולדות", "יולדת בן", "יולדת בת", "בר מצוה", "בת מצוה", "הזכרת נשמות",
)

/** A source line that explicitly puts both alternatives on the same line. */
private val smallSpan = Regex("""<small[^>]*>([^<>]*)</small>""")
private val gevurot = Regex("""^(<small>)?(<small>בקיץ:</small>\s*.+?)\s*(<small>בחורף:</small>\s*.+)$""")

private fun gevurotAlternatives(html: String, flags: Set<String>): List<SiddurLine>? {
    val (wrapper, summer, winter) = gevurot.matchEntire(html.trim())?.destructured ?: return null
    val winterWords = if (wrapper.isEmpty()) winter else winter.removeSuffix("</small>")
    if (!plain(summer).startsWith("בקיץ: מוריד הטל") || !plain(winterWords).startsWith("בחורף: משיב הרוח")) return null
    val summerApplies = "mashiv_haruach" !in flags && ("ashkenaz" !in flags || "israel" in flags)
    val winterApplies = "mashiv_haruach" in flags
    return listOf(
        SiddurLine(summer, special = summerApplies, skipped = !summerApplies),
        SiddurLine(winterWords, special = winterApplies, skipped = !winterApplies),
    )
}

private fun seasonalLine(
    html: String,
    flags: Set<String>,
    showAll: Boolean,
): String {
    if (showAll) return html
    val alternatives = smallSpan.findAll(html).filter { plain(it.groupValues[1]).trimEnd(':', '׃') in seasons }.toList()
    if (alternatives.size != 2) return html
    val summerFirst = plain(alternatives.first().groupValues[1]).startsWith("בקיץ")
    val winter = "mashiv_haruach" in flags
    val selected = if (winter == summerFirst) 1 else 0
    val start = alternatives[selected].range.last + 1
    val end = alternatives.getOrNull(selected + 1)?.range?.first ?: html.length
    val words = html.substring(start, end).trim()
    if (!winter && "ashkenaz" in flags && "israel" !in flags && plain(words).startsWith("מוריד הטל")) return ""
    return html.substring(0, alternatives.first().range.first) + MARK_START + words + MARK_END
}

private val seasons = setOf("בקיץ", "בחורף")

internal fun baseKeys(
    part: SiddurPart,
    nusach: Nusach,
): List<String> =
    when (part) {
        SHACHARIT -> when (nusach) {
            ASHKENAZ -> listOf("ימי חול/תפילת שחרית")
            SEFARD -> listOf("סדר השכמת הבוקר", "שחרית לימות החול")
            EDOT_HAMIZRACH -> listOf("סדר השכמת הבוקר", "שחרית לימי החול")
        }
        MINCHA -> when (nusach) {
            ASHKENAZ -> listOf("ימי חול/תפילת מנחה")
            SEFARD -> listOf("מנחה לימות החול")
            EDOT_HAMIZRACH -> listOf("מנחה לימי החול")
        }
        MAARIV -> listOf(when (nusach) {
            ASHKENAZ -> "ימי חול/מעריב"
            SEFARD -> "ערבית לימות החול"
            EDOT_HAMIZRACH -> "ערבית לימי החול"
        })
        KABBALAT_SHABBAT -> listOf("קבלת שבת")
        MAARIV_SHABBAT -> listOf(if (nusach == ASHKENAZ) "שבת/מעריב" else "ערבית של שבת")
        SHACHARIT_SHABBAT -> listOf(if (nusach == ASHKENAZ) "שבת/שחרית" else "שחרית של שבת")
        MINCHA_SHABBAT -> listOf(when (nusach) {
            ASHKENAZ -> "שבת/מנחה"
            SEFARD -> "מנחה לשבת קודש"
            EDOT_HAMIZRACH -> "מנחה של שבת"
        })
        LEIL_SHABBAT -> listOf(when (nusach) {
            ASHKENAZ -> "ליל שבת"
            SEFARD -> "סעודת ליל שבת"
            EDOT_HAMIZRACH -> "סדר ליל שבת"
        })
        SEUDAT_SHABBAT -> when (nusach) {
            ASHKENAZ -> listOf("סעודת היום", "סעודת שלישית")
            SEFARD -> listOf("סעודת שבת", "סעודה שלישית")
            EDOT_HAMIZRACH -> listOf("סדר סעודה שניה", "סעודה שלישית")
        }
        HAVDALA -> listOf(if (nusach == ASHKENAZ) "שבת/הבדלה" else "סדר הבדלה")
        BIRKAT_HAMAZON -> listOf(if (nusach == SEFARD) "סדר ברכת המזון" else "ברכת המזון")
        MEEIN_SHALOSH -> listOf(if (nusach == ASHKENAZ) "אכילה/על המחיה" else
            if (nusach == SEFARD) "ברכה אחרונה מעין שלוש" else "ברכת מעין שלוש")
        TEFILAT_HADERECH -> listOf("תפילת הדרך", "תפלת הדרך")
        SFIRAT_HAOMER -> listOf("ספירת העומר")
        KRIAT_SHEMA_AL_HAMITA -> listOf("קריאת שמע על המיטה", "קריאת שמע שעל המיטה")
        KIDDUSH_LEVANA -> listOf("ברכת הלבנה", "קידוש לבנה")
        NEROT_CHANUKA -> listOf("סדר הדלקת נרות חנוכה", "חנוכה/סדר ההדלקה")
        SEUDAT_YOM_TOV -> listOf("סדר קידושא רבא לג רגלים ולראש השנה")
        else -> emptyList()
    }

internal fun machzorTitle(flags: Set<String>, nusach: Nusach): String? =
    when {
        "rosh_hashana" in flags || "erev_rosh_hashana" in flags -> when (nusach) {
            ASHKENAZ -> "מחזור אשכנז לראש השנה"
            SEFARD -> "מחזור ראש השנה ספרד"
            EDOT_HAMIZRACH -> "מחזור ראש השנה עדות המזרח"
        }
        "yom_kippur" in flags || "erev_yom_kippur" in flags -> when (nusach) {
            ASHKENAZ -> "מחזור אשכנז ליום כיפור"
            SEFARD -> "מחזור ליום כפור ספרד"
            EDOT_HAMIZRACH -> "מחזור יום כיפור עדות המזרח"
        }
        else -> null
    }

internal fun machzorKeys(part: SiddurPart, flags: Set<String>): List<String> =
    when (part) {
        MINCHA -> when {
            "erev_yom_kippur" in flags -> listOf("תפלת מנחה לערב יום כפור", "מנחה לערב יום כפור", "מנחה לערב יום הכיפורים")
            "erev_rosh_hashana" in flags -> listOf("תפילת מנחה לערב ראש השנה", "מנחה לערב ראש השנה")
            else -> emptyList()
        }
        MAARIV_YOM_TOV -> listOf("כל נדרי", "תפלת ערבית לליל יום כפור", "תפילת ערבית", "תפלת ערבית", "ערבית")
        SHACHARIT_YOM_TOV -> listOf(
            "תפילת שחרית", "תפלת שחרית", "שחרית", "סדר פתיחת ההיכל", "ברכת התורה", "קריאת התורה",
            "קריאה ליום ראשון", "קריאה ליום שני", "מפטיר", "הפטרה ליום א", "הפטרה ליום שני", "ברכות לאחר ההפטרה",
        ) + (if ("rosh_hashana" in flags && "sat" !in flags) listOf("תקיעת שופר", "סדר תקיעת שופר") else emptyList()) +
            listOf("תפילת מוסף", "תפלת מוסף", "מוסף")
        MINCHA_YOM_TOV -> listOf("תפילת מנחה", "מנחה לראש השנה", "תפלת מנחה", "תפלת מנחה ליום כפור", "מנחה")
        NEILA -> listOf("תפלת נעילה", "נעילה")
        LEIL_YOM_TOV -> listOf("קידוש", "קידוש לראש השנה", "קידוש ליל ראש השנה", "סדר ליל ראש השנה", "מנהגי ליל ראש השנה")
        SEUDAT_YOM_TOV -> listOf("סדר קידוש היום")
        else -> emptyList()
    }
