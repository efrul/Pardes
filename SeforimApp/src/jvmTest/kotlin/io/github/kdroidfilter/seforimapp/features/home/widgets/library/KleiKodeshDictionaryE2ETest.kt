package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Klei Kodesh's index over the installed library (`SEFORIMAPP_DATABASE_PATH`); skipped when it is absent. On the
 * October 2026 library (v3-20261012193827) every one of the index's entries is found on its line.
 */
class KleiKodeshDictionaryE2ETest {
    private val dbPath =
        System.getenv("SEFORIMAPP_DATABASE_PATH")
            ?: (System.getProperty("user.home") + "/Library/Application Support/io.github.kdroidfilter.seforimapp/databases/seforim.db")

    private val repository by lazy { SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath")) }

    @Test
    fun `every indexed dictionary is read from the library, its entries on their lines`() =
        runBlocking {
            assumeTrue("No database at $dbPath", File(dbPath).exists())
            val expected =
                mapOf(
                    Dictionary.METZUDAT to 17_994,
                    Dictionary.MALBIM to 6_169,
                    Dictionary.MIKROPEDIA to 1_305,
                    Dictionary.MENACHEM to 1_605,
                )
            for ((dictionary, count) in expected) {
                val entries = readIndexed(repository, dictionary)
                assertEquals(count, entries.size, dictionary.name)
                assertTrue(entries.all { it.word.isNotBlank() }, dictionary.name)
                // The explanation read: of a word, its line; of a heading, the lines after it
                assertTrue(entries.count { it.gloss.isNotBlank() } > count * 0.95, dictionary.name)
                println(
                    "$dictionary: " +
                        entries
                            .shuffled(
                                kotlin.random.Random(1),
                            ).take(3)
                            .map { "${it.word} — ${it.gloss.take(60)} ${it.first}..${it.last}" },
                )
            }
            val menachem = readIndexed(repository, Dictionary.MENACHEM).first { it.word == "אב" }
            assertEquals(264..270, menachem.first..menachem.last)
        }
}
