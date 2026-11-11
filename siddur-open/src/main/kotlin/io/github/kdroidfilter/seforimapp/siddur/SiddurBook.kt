package io.github.kdroidfilter.seforimapp.siddur

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Instant

enum class Nusach {
    ASHKENAZ,
    SEFARD,
    EDOT_HAMIZRACH,
}

enum class SiddurPart(
    val night: Boolean = false,
    val festival: Boolean = false,
    val options: List<String> = emptyList(),
) {
    SHACHARIT(options = listOf("first_lulav")),
    MINCHA,
    MAARIV(night = true),
    SFIRAT_HAOMER(night = true),
    BIRKAT_HAMAZON(options = listOf("zimun", "zimun_10", "seudat_mila")),
    MEEIN_SHALOSH(options = listOf("mezonot", "wine", "fruit")),
    TEFILAT_HADERECH,
    KRIAT_SHEMA_AL_HAMITA(night = true),
    KIDDUSH_LEVANA(night = true),
    NEROT_CHANUKA(night = true),
    KABBALAT_SHABBAT(night = true),
    MAARIV_SHABBAT(night = true),
    LEIL_SHABBAT(night = true),
    SHACHARIT_SHABBAT,
    SEUDAT_SHABBAT,
    MINCHA_SHABBAT,
    HAVDALA(night = true),
    MAARIV_YOM_TOV(night = true, festival = true),
    LEIL_YOM_TOV(night = true, festival = true),
    SHACHARIT_YOM_TOV(festival = true, options = listOf("first_lulav")),
    SEUDAT_YOM_TOV(festival = true),
    MINCHA_YOM_TOV(festival = true),
    NEILA(festival = true),
    SELICHOT,
    ;

    fun familyOf(): SiddurPart =
        when (this) {
            SHACHARIT_SHABBAT, SHACHARIT_YOM_TOV -> SHACHARIT
            MINCHA_SHABBAT, MINCHA_YOM_TOV -> MINCHA
            MAARIV_SHABBAT, MAARIV_YOM_TOV -> MAARIV
            else -> this
        }
}

data class SiddurPrefs(
    val nusach: Nusach,
    val inIsrael: Boolean,
    val minyan: Boolean,
    val beitAvel: Boolean,
    val mukafChoma: Boolean = false,
    val israeliPrayerCustom: IsraeliPrayerCustom = IsraeliPrayerCustom.NO_TACHANUN,
)

data class SiddurLine(
    val html: String,
    val kind: Kind = Kind.TEXT,
    val headingLevel: Int = 3,
    val special: Boolean = false,
    val skipped: Boolean = false,
    val reading: SiddurReading? = null,
    val moladAnnouncement: MoladAnnouncement? = null,
    val lulavInstruction: LulavInstruction? = null,
) {
    enum class Kind { TEXT, HEADING, NOTE, SLOT }
}

enum class LulavInstruction { ASHKENAZ_DIRECTIONS, ARI_DIRECTIONS, SHAKE, SHAKE_TWICE, CONGREGATION, LEADER }

data class MoladAnnouncement(
    val day: String,
    val hour: Int,
    val minute: Int,
    val parts: Int,
)

data class BookSection(
    val part: SiddurPart,
    val date: LocalDate,
    val night: Boolean,
    val flags: Set<String>,
    val lines: List<SiddurLine>,
)

/** A linear reading with its original outline. Each heading starts at a zero-based row index. */
data class SourceBook(
    val lines: List<String>,
    val headings: List<SourceHeading>,
)

data class SourceHeading(
    val index: Int,
    val level: Int,
    val title: String,
)

fun interface SiddurSource {
    suspend fun book(title: String): SourceBook?
}

const val MARK_START = '\uE000'
const val MARK_END = '\uE001'
const val SKIP_START = '\uE004'
const val SKIP_END = '\uE005'

fun civilDayOf(date: LocalDate, part: SiddurPart): LocalDate =
    if (part.night) date.minus(1, DateTimeUnit.DAY) else date

fun siddurOfTheHour(
    now: Instant,
    today: LocalDate,
    dawn: Instant?,
    noon: Instant?,
    sunset: Instant?,
): Pair<LocalDate, SiddurPart> =
    when {
        sunset != null && now >= sunset -> kotlinx.datetime.LocalDate.fromEpochDays(today.toEpochDays() + 1) to SiddurPart.MAARIV
        dawn != null && now < dawn -> today to SiddurPart.MAARIV
        noon != null && now >= noon -> today to SiddurPart.MINCHA
        else -> today to SiddurPart.SHACHARIT
    }

fun Nusach.siddurTitle(): String =
    when (this) {
        Nusach.ASHKENAZ -> "סידור אשכנז"
        Nusach.SEFARD -> "סידור ספרד"
        Nusach.EDOT_HAMIZRACH -> "סידור נוסח עדות המזרח"
    }
