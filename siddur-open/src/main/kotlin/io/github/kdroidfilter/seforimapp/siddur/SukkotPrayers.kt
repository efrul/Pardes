package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.siddur.SiddurLine.Kind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal fun Outline.lulavBlessings(
    date: LocalDate,
    prefs: SiddurPrefs,
    flags: Set<String>,
    sharedBlessings: Outline?,
): List<SiddurLine> {
    if ("sukkot" !in flags || "sat" in flags) return emptyList()
    val day = JewishCalendar(date, prefs.inIsrael).jewishDayOfMonth
    val first = day == 15 || day == 16 && date.dayOfWeek == DayOfWeek.SUNDAY || "first_lulav" in flags
    val section = when (prefs.nusach) {
        Nusach.ASHKENAZ -> read(listOf("סוכות/נטילת לולב"), flags, false)
        Nusach.SEFARD -> read(listOf("סדר נטילת לולב"), flags, false)
        Nusach.EDOT_HAMIZRACH -> sharedBlessings?.read(listOf("סוכות/נטילת לולב"), flags, false).orEmpty()
    }
    val directions = if (prefs.nusach == Nusach.ASHKENAZ) {
        LulavInstruction.ASHKENAZ_DIRECTIONS
    } else {
        LulavInstruction.ARI_DIRECTIONS
    }
    val text = if (first) section else section.takeWhile { !plain(it.html).startsWith("בפעם הראשו") }
    return text.map { it.copy(special = true) } + lulavNote(directions)
}

private val ashkenazHoshanot = listOf(
    "יום טוב ראשון של סוכות", "יום שני של סוכות", "יום שלישי של סוכות",
    "יום רביעי של סוכות", "יום חמישי של סוכות", "יום שישי של סוכות", "הושענא רבה",
)

// The order table printed in Siddur Sefard starts on 15 Tishrei.
private val sefardHoshanot = mapOf(
    DayOfWeek.MONDAY to listOf("למען אמתך", "אבן שתיה", "אערוך שועי", "אום אני חומה", "אל למושעות"),
    DayOfWeek.TUESDAY to listOf("למען אמתך", "אבן שתיה", "אערוך שועי", "אל למושעות", "", "אדון המושיע"),
    DayOfWeek.THURSDAY to listOf("למען אמתך", "אבן שתיה", "", "אערוך שועי", "אל למושעות", "אדון המושיע"),
    DayOfWeek.SATURDAY to listOf("", "למען אמתך", "אערוך שועי", "אבן שתיה", "אל למושעות", "אדון המושיע"),
)

internal fun Outline.hoshanot(date: LocalDate, prefs: SiddurPrefs, flags: Set<String>): List<SiddurLine> {
    if ("sukkot" !in flags) return emptyList()
    val day = JewishCalendar(date, prefs.inIsrael).jewishDayOfMonth - 15
    val section = when (prefs.nusach) {
        Nusach.ASHKENAZ -> {
            val key = if ("sat" in flags) "כל יום בסוכות שחל בשבת" else ashkenazHoshanot.getOrNull(day)
            if (key == null) return emptyList()
            read(listOf("סוכות/הושענות/$key"), flags, false).selectAshkenazHoshanah(date.dayOfWeek)
        }
        Nusach.SEFARD -> when {
            day == 6 -> read(listOf("סוכות/הושענות להושענא רבא"), flags, false)
            "sat" in flags -> read(listOf("סוכות/הושענות לשבת"), flags, false)
            else -> {
                val firstDay = date.minus(day, DateTimeUnit.DAY).dayOfWeek
                val poem = sefardHoshanot[firstDay]?.getOrNull(day)?.takeIf(String::isNotBlank) ?: return emptyList()
                val lines = read(listOf("סוכות/הושענות ליו\"ט ראשון ולחוה\"מ"), flags, false)
                val start = lines.indexOfFirst { plain(it.html).startsWith(poem) }
                if (start < 0) return emptyList()
                lines.takeWhile { !plain(it.html).startsWith("ליוט ראשון למעט") } + lines.drop(start).take(4)
            }
        }
        Nusach.EDOT_HAMIZRACH -> return emptyList()
    }
    return section.takeWhile {
        val label = plain(it.html)
        !label.startsWith("קדיש שלם") && !label.startsWith("שיר של יום,")
    }.filterNot { !prefs.minyan && plain(it.html).startsWith("השץ אומר קדיש") }
        .mapIndexed { index, line ->
            if (index == 0 && prefs.nusach == Nusach.ASHKENAZ && line.kind == Kind.HEADING) {
                line.copy(html = "הושענות — ${line.html}", special = true)
            } else {
                line.copy(special = true)
            }
        }
}

private val ashkenazWeekdays = "אבגדהוש"

private fun List<SiddurLine>.selectAshkenazHoshanah(weekday: DayOfWeek): List<SiddurLine> = buildList {
    var include = true
    val letter = ashkenazWeekdays[(weekday.ordinal + 1) % 7]
    for (line in this@selectAshkenazHoshanah) {
        val label = plain(line.html)
        when {
            label.startsWith("כשחל ביום ") -> include = letter in label.removePrefix("כשחל ביום ").replace("או", "")
            label.startsWith("לאחר אמירת ההושענא") -> include = true
            include -> add(line)
        }
    }
}

private fun lulavNote(instruction: LulavInstruction) =
    SiddurLine("", Kind.NOTE, special = true, lulavInstruction = instruction)

private val hodu = Regex("ה\\p{M}*ו\\p{M}*ד\\p{M}*ו\\p{M}*\\s+ל\\p{M}*[יה]")
private val hoshiAna = Regex("אנא (?:יהוה|יי|ה) הושיעה נא")

internal fun List<SiddurLine>.withLulavCues(prefs: SiddurPrefs, flags: Set<String>): List<SiddurLine> {
    if ("sukkot" !in flags || "sat" in flags) return this
    var firstHodu = true
    return flatMap { line ->
        val label = prayerText(plain(line.html))
        val closingHodu = if (label.startsWith("ברוך הבא")) hodu.find(line.html)?.range?.first else null
        when {
            line.kind != Kind.TEXT || line.skipped -> listOf(line)
            closingHodu != null -> listOf(
                line.copy(html = line.html.substring(0, closingHodu).trimEnd()),
                lulavNote(
                    if (prefs.nusach == Nusach.ASHKENAZ && prefs.minyan) LulavInstruction.SHAKE_TWICE else LulavInstruction.SHAKE,
                ),
                line.copy(html = line.html.substring(closingHodu)),
            )
            label.startsWith("הודו ") && firstHodu -> {
                firstHodu = false
                listOf(lulavNote(LulavInstruction.SHAKE), line)
            }
            hoshiAna.matchAt(label, 0) != null -> listOf(
                lulavNote(
                    if (hoshiAna.findAll(label).count() > 1) LulavInstruction.SHAKE_TWICE else LulavInstruction.SHAKE,
                ),
                line,
            )
            prefs.nusach == Nusach.ASHKENAZ && prefs.minyan && label.startsWith("יאמר נא ישראל") ->
                listOf(lulavNote(LulavInstruction.LEADER), line)
            prefs.nusach == Nusach.ASHKENAZ && prefs.minyan && label.startsWith("הודו ") ->
                listOf(lulavNote(LulavInstruction.CONGREGATION), line)
            else -> listOf(line)
        }
    }
}
