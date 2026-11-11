package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.util.zip.GZIPInputStream

enum class ReadingKind { TORAH, HAFTARAH, MEGILLAH }

data class VerseAddress(
    val chapter: Int,
    val verse: Int,
) : Comparable<VerseAddress> {
    override fun compareTo(other: VerseAddress): Int =
        compareValuesBy(this, other, VerseAddress::chapter, VerseAddress::verse)
}

data class ReadingPassage(
    val bookTitle: String,
    val first: VerseAddress,
    val last: VerseAddress,
    /** 1–7 for aliyot, M for maftir, null for the unnumbered passages of a haftarah or megillah. */
    val aliyah: String?,
)

data class SiddurReading(
    val kind: ReadingKind,
    val occasion: String,
    val passages: List<ReadingPassage>,
    val withBlessings: Boolean = true,
)

private data class DayKey(
    val yearType: String,
    val inIsrael: Boolean,
    val offset: Int,
    val service: String,
)

private data class Schedule(
    val occasion: String,
    val torah: List<ReadingPassage>,
    val haftarah: List<ReadingPassage>,
    val sephardicHaftarah: List<ReadingPassage>,
    val megillah: List<ReadingPassage>,
)

/** Traditional full readings for the Hebrew date of the service. The evening already uses the next Hebrew day. */
fun readingsFor(
    date: LocalDate,
    part: SiddurPart,
    prefs: SiddurPrefs,
): List<SiddurReading> {
    val service = when (part.familyOf()) {
        SiddurPart.SHACHARIT -> "morning"
        SiddurPart.MINCHA -> "mincha"
        SiddurPart.MAARIV -> "night"
        else -> return emptyList()
    }
    val day = JewishCalendar(date, prefs.inIsrael)
    val start = JewishCalendar(day.jewishYear, HebrewMonth.TISHREI, 1).gregorianLocalDate
    val yearType = "${(start.dayOfWeek.ordinal + 1) % 7}-${day.daysInJewishYear}"
    val offset = (date.toEpochDays() - start.toEpochDays()).toInt()
    val key = DayKey(yearType, prefs.inIsrael, offset, service)
    val ordinary = schedules[key]
    val candidate =
        if (prefs.mukafChoma && day.yomTovIndex == JewishCalendar.PURIM) {
            schedules[key.copy(service = "walled_$service")]
        } else if (prefs.mukafChoma) {
            schedules[key.copy(service = "walled_$service")] ?: ordinary
        } else {
            ordinary
        }
    val thanksgivingHaftarah = prefs.minyan && prefs.israeliPrayerCustom.festive && service == "morning" &&
        israeliOccasion(day) == IsraeliOccasion.YOM_HAATZMAUT
    val additions =
        if (thanksgivingHaftarah) {
            listOf(
                SiddurReading(
                    kind = ReadingKind.HAFTARAH,
                    occasion = "",
                    passages = listOf(ReadingPassage("ישעיהו", VerseAddress(10, 32), VerseAddress(12, 6), null)),
                    withBlessings = false,
                ),
            )
        } else {
            emptyList()
        }
    val schedule = candidate ?: return additions
    val sephardic = prefs.nusach == Nusach.EDOT_HAMIZRACH
    val minorFastMincha = service == "mincha" && day.isTaanis &&
        day.yomTovIndex !in setOf(JewishCalendar.YOM_KIPPUR, JewishCalendar.TISHA_BEAV)
    val haftarah = when {
        // The minor-fast Mincha haftarah follows the custom in Rema, Orach Chayim 566:1.
        sephardic && minorFastMincha -> emptyList()
        sephardic && schedule.sephardicHaftarah.isNotEmpty() -> schedule.sephardicHaftarah
        else -> schedule.haftarah
    }
    val megillah = schedule.megillah.takeUnless {
        sephardic && it.firstOrNull()?.bookTitle in setOf("שיר השירים", "רות", "קהלת")
    }.orEmpty()
    val torah = if (service == "morning" &&
        day.yomTovIndex in setOf(JewishCalendar.CHOL_HAMOED_SUCCOS, JewishCalendar.HOSHANA_RABBA)
    ) {
        sukkotTorahReading(schedule.torah, day.jewishDayOfMonth - 14, date.dayOfWeek == DayOfWeek.SATURDAY, prefs)
    } else {
        schedule.torah
    }
    return listOf(
        ReadingKind.TORAH to torah,
        ReadingKind.HAFTARAH to haftarah,
        ReadingKind.MEGILLAH to megillah,
    ).mapNotNull { (kind, passages) ->
        passages.takeIf { it.isNotEmpty() && (prefs.minyan || kind == ReadingKind.MEGILLAH) }
            ?.let { SiddurReading(kind, schedule.occasion, it) }
    } + additions
}

/** Orach Chayim 663. The generated schedule uses the Diaspora aliyot even in Israel. */
private fun sukkotTorahReading(
    scheduled: List<ReadingPassage>,
    festivalDay: Int,
    shabbat: Boolean,
    prefs: SiddurPrefs,
): List<ReadingPassage> {
    fun offering(firstDay: Int, lastDay: Int, aliyah: String): ReadingPassage =
        ReadingPassage("במדבר", VerseAddress(29, 11 + firstDay * 3), VerseAddress(29, 13 + lastDay * 3), aliyah)

    if (prefs.inIsrael) {
        if (shabbat) return scheduled.map { if (it.aliyah == "M") offering(festivalDay, festivalDay, "M") else it }
        return (1..4).map { offering(festivalDay, festivalDay, it.toString()) }
    }
    if (shabbat || prefs.nusach != Nusach.EDOT_HAMIZRACH) return scheduled
    return listOf(
        offering(festivalDay - 1, festivalDay - 1, "1"),
        offering(festivalDay, festivalDay, "2"),
        offering(festivalDay, festivalDay, "3"),
        offering(festivalDay - 1, festivalDay, "4"),
    )
}

private val schedules by lazy {
    val stream = checkNotNull(DayKey::class.java.getResourceAsStream("/open-siddur/readings.tsv.gz"))
    GZIPInputStream(stream).bufferedReader().useLines { rows ->
        rows.associate { row ->
            val fields = row.split('\t')
            DayKey(fields[0], fields[1] == "1", fields[2].toInt(), fields[3]) to
                Schedule(fields[4], passages(fields[5]), passages(fields[6]), passages(fields[7]), passages(fields[8]))
        }
    }
}

private fun passages(field: String): List<ReadingPassage> =
    if (field.isEmpty()) emptyList() else field.split('|').map { passage ->
        val (aliyah, title, first, last) = passage.split(';')
        ReadingPassage(title, address(first), address(last), aliyah.ifEmpty { null })
    }

private fun address(value: String): VerseAddress {
    val (chapter, verse) = value.split(':')
    return VerseAddress(chapter.toInt(), verse.toInt())
}
