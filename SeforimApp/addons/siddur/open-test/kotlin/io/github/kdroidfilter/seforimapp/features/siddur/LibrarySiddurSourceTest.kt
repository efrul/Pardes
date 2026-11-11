package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.siddur.Nusach
import io.github.kdroidfilter.seforimapp.siddur.ReadingKind
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart
import io.github.kdroidfilter.seforimapp.siddur.SiddurPrefs
import io.github.kdroidfilter.seforimapp.siddur.buildDayBook
import io.github.kdroidfilter.seforimapp.siddur.readingsFor
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibrarySiddurSourceTest {
    @Test
    fun selectsSeasonalAndFastSelichotFromTheInstalledCorpus() =
        runBlocking {
            val file = System.getenv("SEFORIM_DB")?.let(::File)
            assumeTrue("Set SEFORIM_DB to test with the library", file?.isFile == true)
            checkNotNull(file)
            PersistentSqliteDriver("jdbc:sqlite:${file.toURI()}?mode=ro&immutable=1").use { driver ->
                val source = LibrarySiddurSource(SeforimRepository(file.absolutePath, driver))
                for (nusach in Nusach.entries) {
                    val prefs = SiddurPrefs(nusach, inIsrael = true, minyan = true, beitAvel = false)
                    for (date in listOf(LocalDate(2026, 9, 6), LocalDate(2026, 9, 14), LocalDate(2026, 9, 15))) {
                        val sections = buildDayBook(date, prefs, emptySet(), false, source)
                        assertEquals(SiddurPart.SELICHOT, sections.first().part, "$nusach $date")
                        assertTrue(sections.first().lines.size > 25, "$nusach $date")
                    }
                    val fast =
                        listOf(
                            LocalDate(2026, 7, 2) to if (nusach == Nusach.EDOT_HAMIZRACH) "תמוז" else "שבעה עשר בתמוז",
                            LocalDate(2026, 12, 20) to "עשרה בטבת",
                            LocalDate(2027, 3, 22) to "תענית אסתר",
                        )
                    for ((date, heading) in fast) {
                        val morning = buildDayBook(date, prefs, emptySet(), false, source).first { it.part == SiddurPart.SHACHARIT }
                        assertTrue(
                            morning.lines.any { it.special && heading in HebrewTextUtils.removeAllDiacritics(it.html) },
                            "$nusach $date",
                        )
                    }
                }
            }
        }

    @Test
    fun festivalEvesOmitTachanunAndErevYomKippurUsesItsMachzor() =
        runBlocking {
            val file = System.getenv("SEFORIM_DB")?.let(::File)
            assumeTrue("Set SEFORIM_DB to test with the library", file?.isFile == true)
            checkNotNull(file)
            PersistentSqliteDriver("jdbc:sqlite:${file.toURI()}?mode=ro&immutable=1").use { driver ->
                val source = LibrarySiddurSource(SeforimRepository(file.absolutePath, driver))
                for (nusach in Nusach.entries) {
                    val prefs = SiddurPrefs(nusach, inIsrael = true, minyan = true, beitAvel = false)
                    for (date in listOf(LocalDate(2027, 4, 21), LocalDate(2026, 9, 11), LocalDate(2026, 9, 20))) {
                        val mincha = buildDayBook(date, prefs, emptySet(), false, source).first { it.part == SiddurPart.MINCHA }
                        val text = mincha.lines.map { HebrewTextUtils.removeAllDiacritics(it.html.replace(Regex("<[^>]+>"), "")).trim() }
                        assertFalse("tachanun" in mincha.flags, "$nusach $date")
                        assertFalse(text.any { it == "תחנון" || it == "נפילת אפים" || it == "שומר ישראל" }, "$nusach $date")
                        assertEquals(date == LocalDate(2026, 9, 20), text.any { "על חטא שחטאנו" in it }, "$nusach $date")
                    }
                }
            }
        }

    @Test
    fun resolvesHaftarahAndSpecialReadingsFromTheInstalledCorpus() =
        runBlocking {
            val file = System.getenv("SEFORIM_DB")?.let(::File)
            assumeTrue("Set SEFORIM_DB to test with the library", file?.isFile == true)
            checkNotNull(file)
            PersistentSqliteDriver("jdbc:sqlite:${file.toURI()}?mode=ro&immutable=1").use { driver ->
                val source = LibrarySiddurSource(SeforimRepository(file.absolutePath, driver))
                val prefs = SiddurPrefs(Nusach.EDOT_HAMIZRACH, inIsrael = true, minyan = true, beitAvel = false)
                val zachor = LocalDate(2027, 3, 20)
                val book = buildDayBook(zachor, prefs, emptySet(), false, source)
                val rendered = book.first { it.part == SiddurPart.SHACHARIT_SHABBAT }.lines.mapNotNull { it.reading }
                assertEquals(listOf(ReadingKind.TORAH, ReadingKind.HAFTARAH), rendered.map { it.kind })
                val haftarah = rendered.last().passages.single()
                assertEquals(34, source.passage(haftarah)?.size)
                val dates = listOf(LocalDate(2026, 12, 11), LocalDate(2026, 12, 12), LocalDate(2026, 9, 21), zachor)
                val passages =
                    dates.flatMap { date ->
                        listOf(SiddurPart.SHACHARIT, SiddurPart.MINCHA).flatMap { part ->
                            readingsFor(date, part, prefs).flatMap { it.passages }
                        }
                    }
                assertTrue(passages.size >= 30)
                for (passage in passages) {
                    assertTrue(source.passage(passage).orEmpty().isNotEmpty(), passage.toString())
                }
            }
        }

    @Test
    fun opensCorePrayersFromTheInstalledCorpus() =
        runBlocking {
            val file = System.getenv("SEFORIM_DB")?.let(::File)
            assumeTrue("Set SEFORIM_DB to test with the library", file?.isFile == true)
            checkNotNull(file)
            PersistentSqliteDriver("jdbc:sqlite:${file.toURI()}?mode=ro&immutable=1").use { driver ->
                val source = LibrarySiddurSource(SeforimRepository(file.absolutePath, driver))
                for (nusach in Nusach.entries) {
                    val prefs = SiddurPrefs(nusach, inIsrael = true, minyan = true, beitAvel = false)
                    val weekday = buildDayBook(LocalDate(2026, 11, 3), prefs, emptySet(), false, source)
                    val friday = buildDayBook(LocalDate(2026, 11, 6), prefs, emptySet(), false, source)
                    val roshHashana = buildDayBook(LocalDate(2026, 9, 12), prefs, emptySet(), false, source)
                    assertTrue(weekday.any { it.part == SiddurPart.SHACHARIT && it.lines.size > 50 }, nusach.name)
                    assertTrue(weekday.any { it.part == SiddurPart.MINCHA && it.lines.size > 20 }, nusach.name)
                    assertTrue(friday.any { it.part == SiddurPart.MAARIV_SHABBAT && it.lines.size > 20 }, nusach.name)
                    assertTrue(roshHashana.any { it.part == SiddurPart.SHACHARIT_YOM_TOV && it.lines.size > 50 }, nusach.name)

                    suspend fun morning(date: LocalDate): String =
                        buildDayBook(date, prefs, emptySet(), false, source)
                            .first { it.part == SiddurPart.SHACHARIT_SHABBAT }
                            .lines
                            .joinToString("\n") { HebrewTextUtils.removeAllDiacritics(it.html.replace(Regex("<[^>]+>"), "")) }

                    val roshChodesh = morning(LocalDate(2027, 1, 9))
                    val ordinary = morning(LocalDate(2027, 1, 16))
                    assertTrue("אתה יצרת עולמך מקדם" in roshChodesh, nusach.name)
                    assertFalse("תכנת שבת רצית קרבנותיה" in roshChodesh, nusach.name)
                    assertTrue("תכנת שבת" in ordinary, nusach.name)
                    assertFalse("אתה יצרת עולמך מקדם" in ordinary, nusach.name)
                }
            }
        }
}
