package io.github.kdroidfilter.seforimapp.android.data

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Port of the data part of desktop 0.6.10 features/home/widgets/measures/MeasuresWidget.kt, verbatim: the Torah's
// lengths, the opinions of what they are today and how long a mil takes to walk, each with the line of the library it
// comes from, and the number formatting.

/** A line of the library, [ref] as shown: [lineIndex] in the book [bookTitle]. */
internal data class Source(
    val ref: String,
    val bookTitle: String,
    val lineIndex: Int,
)

/** What says how many [from] are in [to] or back: each unit's source from the smaller one's next to the larger. */
internal fun conversionSources(
    from: LengthUnit,
    to: LengthUnit,
): List<Source> {
    val range = minOf(from, to)..maxOf(from, to)
    return LengthUnit.entries
        .filter { it in range && it != range.start }
        .map { it.source }
        .distinct()
}

// Every figure below is in the library, on the line its source points to.
// ponytail: lines by index in their book, as the library numbers them; a book re-cut would need them found again

private val RAMBAM_SHABBOS_17_36 = Source("רמב״ם שבת יז, לו", "משנה תורה, הלכות שבת", 370)
private val RAMBAM_TEFILLAH_4_2 = Source("רמב״ם תפילה ד, ב", "משנה תורה, הלכות תפילה וברכת כהנים", 47)
private val MISHNAH_BERURAH_110_31 = Source("משנה ברורה קי, לא", "משנה ברורה", 2603)

internal const val ETZBAOS_IN_AMMA = 24.0
internal const val AMOS_IN_MIL = 2000.0

/** The lengths, in etzbaos, with what says so. */
internal enum class LengthUnit(
    val title: String,
    val plural: String,
    val etzbaos: Double,
    val source: Source,
) {
    // "האצבע… רחב הגודל של יד. והטפח ארבע אצבעות… אמה בת ששה טפחים"
    ETZBA("אצבע", "אצבעות", 1.0, RAMBAM_SHABBOS_17_36),
    TEFACH("טפח", "טפחים", 4.0, RAMBAM_SHABBOS_17_36),
    AMMA("אמה", "אמות", ETZBAOS_IN_AMMA, RAMBAM_SHABBOS_17_36),

    // "ארבעה מילין שהם שמונת אלפים אמה"
    MIL("מיל", "מילין", ETZBAOS_IN_AMMA * AMOS_IN_MIL, RAMBAM_TEFILLAH_4_2),

    // "מיל הוא אלפים אמה ופרסה הוא ד' מילין"
    PARSAH("פרסה", "פרסאות", ETZBAOS_IN_AMMA * AMOS_IN_MIL * 4, MISHNAH_BERURAH_110_31),
}

internal class AmmaOpinion(
    val id: String,
    val label: String,
    val cm: Double,
    val source: Source,
)

internal val AMMA_OPINIONS =
    listOf(
        // "ומדת האמה 58 ס"מ" (קונטרס השיעורים)
        AmmaOpinion("chazon_ish", "חזון איש", 58.0, Source("חזו״א, קונטרס השיעורים לט, ט", "חזון איש, אורח חיים מועד", 1533)),
        // "אמה של תורה שהוא כ"ד אצבעות מדת כ"א אינטשעס ורביע"
        AmmaOpinion("igros_moshe", "אגרות משה", 21.25 * 2.54, Source("אגרות משה או״ח א, קלו", "אגרות משה אורח חיים א", 1256)),
    )

internal class MilOpinion(
    val id: String,
    val label: String,
    val minutes: Double,
    val source: Source,
)

private val BIUR_HALACHA_459_2 = Source("ביאור הלכה תנט, ב", "ביאור הלכה", 4452)

internal val MIL_OPINIONS =
    listOf(
        // "ושיעור מיל הוי רביעית שעה וחלק מעשרים מן השעה"
        MilOpinion("mil_18", "מיל 18 דק׳", 18.0, Source("שו״ע או״ח תנט, ב", "שולחן ערוך, אורח חיים", 3402)),
        // "שחושבין שיעור מיל לחשבון כ"ב מינוטין וחצי"
        MilOpinion("mil_22_5", "מיל 22.5 דק׳", 22.5, BIUR_HALACHA_459_2),
        // "שליש שעה וחלק ט"ו מן השעה"
        MilOpinion("mil_24", "מיל 24 דק׳", 24.0, BIUR_HALACHA_459_2),
    )

/** The ids of every opinion: shown until the user picks (desktop MeasuresSettings.shown's default). */
internal val ALL_MEASURE_OPINIONS: Set<String> = (AMMA_OPINIONS.map { it.id } + MIL_OPINIONS.map { it.id }).toSet()

internal const val MEASURES_TODAY_TITLE = "בימינו"
internal const val MEASURES_WALK_TITLE = "זמן הליכה"

/** [count] [unit]s, its name in the singular for one. */
internal fun quantity(
    count: Double,
    unit: LengthUnit,
): String = "${amount(count)} ${if (count == 1.0) unit.title else unit.plural}"

/** [cm] in centimetres, metres or kilometres. */
internal fun length(cm: Double): String =
    when {
        cm < 100 -> "${amount(cm)} ס״מ"
        cm < 100_000 -> "${amount(cm / 100)} מ׳"
        else -> "${amount(cm / 100_000)} ק״מ"
    }

/** [seconds] in seconds, minutes or hours. */
internal fun duration(seconds: Double): String =
    when {
        seconds < 60 -> "${amount(seconds)} שניות"
        seconds < 3600 -> "${amount(seconds / 60)} דקות"
        else -> "${amount(seconds / 3600)} שעות"
    }

// The same "8,000" whatever the system's language
private val whole = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ROOT))

/** Whole from 100, else three significant digits: 58, 1.74, 0.54. */
internal fun amount(x: Double): String =
    when {
        x >= 100 -> whole.format(x)
        x == 0.0 -> "0"
        else -> BigDecimal(x).round(MathContext(3)).stripTrailingZeros().toPlainString()
    }
