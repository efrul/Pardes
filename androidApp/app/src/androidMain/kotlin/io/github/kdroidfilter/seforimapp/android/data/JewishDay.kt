package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.TefilaRules
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toKotlinLocalDate
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Day of the week, 1 (Sunday) to 7 (Shabbat), as KosherJava numbered it. */
internal val JewishCalendar.weekday: Int get() = gregorianLocalDate.dayOfWeek.isoDayNumber % 7 + 1

/** The next day (KosherKotlin's copy() is ambiguous with no arguments). */
internal fun JewishCalendar.nextDay(): JewishCalendar = JewishCalendar(gregorianLocalDate, inIsrael).apply { forward(DateTimeUnit.DAY, 1) }

/**
 * Calendar facts and prayer rules of a day for the siddur fallback (the desktop smart siddur is a private package),
 * on KosherKotlin 3.0.5 as desktop: TefilaRules and the desktop wordings.
 */
object JewishDay {
    private val rules = TefilaRules()
    private val sefirot = listOf("חסד", "גבורה", "תפארת", "נצח", "הוד", "יסוד", "מלכות")

    fun calendar(date: LocalDate, inIsrael: Boolean): JewishCalendar = JewishCalendar(date.toKotlinLocalDate(), inIsrael)

    private fun omerName(day: Int): String = "${sefirot[(day - 1) % 7]} שב${sefirot[(day - 1) / 7]}"

    /** "יום 8 לעומר · חסד שבגבורה" (the count said tonight). */
    fun omerTonight(jc: JewishCalendar): String? {
        val n = jc.nextDay().dayOfOmer
        return if (n in 1..49) "יום $n לעומר · ${omerName(n)}" else null
    }

    fun chanukaTonight(jc: JewishCalendar): String? {
        val n = jc.nextDay().dayOfChanukah
        return when {
            n == 1 -> "הלילה מדליקים נר אחד"
            n in 2..8 -> "הלילה מדליקים $n נרות"
            else -> null
        }
    }

    private fun isLedavidSeason(jc: JewishCalendar): Boolean =
        jc.jewishMonth == HebrewMonth.ELUL || (jc.jewishMonth == HebrewMonth.TISHREI && jc.jewishDayOfMonth <= 21)

    private fun torahReading(jc: JewishCalendar): Boolean =
        jc.weekday == 2 || jc.weekday == 5 || jc.weekday == 7 || jc.isRoshChodesh || jc.isTaanis || jc.isChanukah ||
            jc.isPurim || jc.isCholHamoed || jc.isYomTov

    private fun noLamenatzeach(jc: JewishCalendar): Boolean =
        jc.isRoshChodesh || jc.isChanukah || jc.isPurim || jc.isCholHamoed || jc.isTishaBav ||
            jc.yomTovIndex == JewishCalendar.EREV_PESACH || jc.yomTovIndex == JewishCalendar.EREV_YOM_KIPPUR ||
            jc.yomTovIndex == JewishCalendar.ISRU_CHAG

    private fun noMizmorLetoda(jc: JewishCalendar): Boolean =
        jc.yomTovIndex == JewishCalendar.EREV_PESACH || jc.isCholHamoedPesach || jc.yomTovIndex == JewishCalendar.EREV_YOM_KIPPUR

    /** The short labels the siddur highlights for the day (Android's strip; desktop's comes from its private package). */
    fun highlights(jc: JewishCalendar): List<String> {
        val out = mutableListOf<String>()
        out += if (rules.isMashivHaruachRecited(jc)) "משיב הרוח" else "מוריד הטל"
        if (!jc.isAssurBemelacha) out += if (rules.isVeseinTalUmatarRecited(jc)) "ותן טל ומטר" else "ותן ברכה"
        out += if (rules.isTachanunRecitedShacharis(jc)) "תחנון" else "אין תחנון"
        if (rules.isYaalehVeyavoRecited(jc)) out += "יעלה ויבוא"
        if (rules.isAlHanissimRecited(jc)) out += "על הניסים"
        if (rules.isHallelRecited(jc)) out += if (rules.isHallelShalemRecited(jc)) "הלל שלם" else "חצי הלל"
        if (jc.isRoshChodesh || jc.isCholHamoed || jc.isYomTovAssurBemelacha || jc.weekday == 7) out += "מוסף"
        if (jc.isAseresYemeiTeshuva) out += "עשרת ימי תשובה"
        if (jc.isTaanis && !jc.isYomKippur) out += "עננו"
        if ((jc.isAseresYemeiTeshuva || (jc.isTaanis && !jc.isTishaBav)) && jc.weekday != 7) out += "אבינו מלכנו"
        if (isLedavidSeason(jc)) out += "לדוד ה׳ אורי"
        if (torahReading(jc)) out += "קריאת התורה"
        if (noLamenatzeach(jc) && jc.weekday != 7) out += "אין למנצח"
        if (noMizmorLetoda(jc)) out += "אין מזמור לתודה"
        if (jc.weekday == 7) out += "רצה והחליצנו"
        val tomorrow = jc.nextDay()
        if (jc.isAssurBemelacha && !tomorrow.isAssurBemelacha) out += "אתה חוננתנו"
        if (jc.weekday == 7 && !jc.isCholHamoed && !jc.isYomTov && !yomTovInComingWeek(jc)) out += "ויהי נועם"
        if (omerTonight(jc) != null) out += "ספירת העומר"
        return out.distinct()
    }

    /** Whether a festival falls on a weekday of the week after this Shabbat (then ויהי נועם is skipped). */
    private fun yomTovInComingWeek(jc: JewishCalendar): Boolean {
        val probe = JewishCalendar(jc.gregorianLocalDate, jc.inIsrael)
        repeat(6) {
            probe.forward(DateTimeUnit.DAY, 1)
            if (probe.isYomTovAssurBemelacha) return true
        }
        return false
    }

    /** The day's yom tov or Rosh Chodesh, as desktop's siddur DayNavigator names it after the date. */
    fun dayTag(jc: JewishCalendar): String =
        hebrewFormatter.formatYomTov(jc).ifBlank { if (jc.isRoshChodesh) hebrewFormatter.formatRoshChodesh(jc).ifBlank { "ראש חודש" } else "" }

    /** The occasion line of the siddur header ("שבת וחנוכה", "סוכות", "יום חול"). */
    fun occasion(jc: JewishCalendar): String {
        val holiday = when {
            jc.isPesach && (jc.jewishDayOfMonth == 21 || jc.jewishDayOfMonth == 22) -> "שביעי של פסח"
            jc.isRoshHashana -> "ראש השנה"
            jc.isYomKippur -> "יום הכיפורים"
            jc.isShminiAtzeres && jc.inIsrael -> "שמיני עצרת ושמחת תורה"
            jc.isShminiAtzeres -> "שמיני עצרת"
            jc.isSimchasTorah -> "שמחת תורה"
            jc.isSuccos -> "סוכות"
            jc.isPesach -> "פסח"
            jc.isShavuos -> "שבועות"
            else -> dayTag(jc)
        }
        return when {
            jc.weekday == 7 && holiday.isNotBlank() -> "שבת ו$holiday"
            jc.weekday == 7 -> "שבת"
            holiday.isNotBlank() -> holiday
            else -> "יום חול"
        }
    }

    /** "d.M · HH:mm" in the city's time zone (desktop MoladPanel's moment format). */
    fun shortDateTime(d: Date?, tz: TimeZone): String =
        d?.let { SimpleDateFormat("d.M · HH:mm", Locale.US).apply { timeZone = tz }.format(it) } ?: "--"

    fun timeFormat(city: City): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(city.tz) }

    fun today(city: City): LocalDate = city.today()
}

/** A row of the zmanim table. */
data class ZmanRow(val title: String, val subtitle: String?, val time: Date?, val emphasized: Boolean = false)

object ZmanimDay {
    /**
     * The day's zmanim in time order: the zmanim of desktop's "הזמן הבא" widget (LuachData.nextZmanim, same names), plus
     * the times its upcoming-events widget gives the day (LuachData.upcomingEvents): fast start and end, candle lighting
     * and havdala. Subtitles are the desktop zmanim cards' (home_widget_card_*_subtitle). Erev Pesach adds the GRA chametz
     * times (Android only). Computes the עתים לבינה sun: call it off the main thread.
     */
    fun rows(city: City, date: LocalDate, opinion: ZmanimOpinion): List<ZmanRow> {
        val israel = city.inIsrael
        val location = city.location
        val z = computeZmanimTimes(date, location, opinion, israel)
        val jc = JewishDay.calendar(date, israel)
        fun calendarOf(d: LocalDate) = JewishDay.calendar(d, israel)
        val rows = mutableListOf(
            ZmanRow("עלות השחר", "ראשית אור היום ההלכתי", z.alosHashachar),
            ZmanRow("הנץ החמה", "תחילת זמן קריאת שמע", z.sunrise),
            ZmanRow("סוף זמן ק״ש מג״א", null, z.sofZmanShmaMga),
            ZmanRow("סוף זמן ק״ש גר״א", null, z.sofZmanShmaGra),
            ZmanRow("סוף זמן תפילה מג״א", null, z.sofZmanTfilaMga),
            ZmanRow("סוף זמן תפילה גר״א", null, z.sofZmanTfilaGra),
            ZmanRow("חצות היום", "אמצע היום ההלכתי", z.chatzosHayom),
            ZmanRow("מנחה גדולה", null, z.minchaGedola),
            ZmanRow("מנחה קטנה", null, z.minchaKetana),
            ZmanRow("פלג המנחה", null, z.plagHamincha),
            ZmanRow("שקיעה", "סיום זמן היום", z.sunset),
            ZmanRow("צאת הכוכבים", "תחילת הלילה ההלכתי", z.tzais),
            ZmanRow("צאת הכוכבים ר״ת", null, z.tzaisRabbeinuTam),
            ZmanRow("חצות הלילה", null, z.chatzosLayla),
        )
        if (jc.yomTovIndex == JewishCalendar.EREV_PESACH) {
            val calendar = zmanimCalendar(date, location, opinion, israel)
            rows += ZmanRow("סוף זמן אכילת חמץ", null, calendar.sofZmanAchilasChametzGRA.toDate(), true)
            rows += ZmanRow("סוף זמן ביעור חמץ", null, calendar.sofZmanBiurChametzGRA.toDate(), true)
        }
        when {
            // A day off work: candles on the eve of the first one, havdala at the end of the last
            jc.isAssurBemelacha -> if (!calendarOf(date.plusDays(1)).isAssurBemelacha) {
                rows += ZmanRow("יציאה", null, havdalahTime(date, location, opinion, israel), true)
            }
            jc.isTaanis -> {
                val start = if (jc.isTishaBav) computeZmanimTimes(date.minusDays(1), location, opinion, israel).sunset else z.alosHashachar
                rows += ZmanRow("תחילת הצום", null, start, true)
                rows += ZmanRow("סוף הצום", null, z.tzais, true)
            }
        }
        if (!jc.isAssurBemelacha && calendarOf(date.plusDays(1)).isAssurBemelacha) {
            rows += ZmanRow("הדלקת נרות", null, candleLightingTime(date, location, opinion, city.name.ifBlank { null }, israel), true)
        }
        // Stable: rows with the same time keep the order above
        return rows.sortedBy { it.time?.time ?: Long.MAX_VALUE }
    }
}
