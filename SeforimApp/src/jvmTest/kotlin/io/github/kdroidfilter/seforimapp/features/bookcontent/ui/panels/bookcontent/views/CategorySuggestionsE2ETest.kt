package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.features.search.domain.CategoryHit
import io.github.kdroidfilter.seforimapp.features.search.domain.CategorySuggestions
import io.github.kdroidfilter.seforimapp.features.search.domain.CommentaryLinks
import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimapp.framework.search.titles.appLookup
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bar's categories over the installed library (its catalog and database); skipped when it is absent. */
class CategorySuggestionsE2ETest {
    private val dbPath =
        System.getenv("SEFORIMAPP_DATABASE_PATH")
            ?: (System.getProperty("user.home") + "/Library/Application Support/io.github.kdroidfilter.seforimapp/databases/seforim.db")

    private val provider by lazy {
        FileKit.init("io.github.kdroidfilter.seforimapp")
        DatabasePathProvider(testAppSettings().apply { setDatabasePath(dbPath) })
    }
    private val repository by lazy { SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath")) }
    private val categories by lazy { LibraryCategories(CatalogCache(provider), repository, CommentaryLinks(provider)) }

    @Test
    fun `the category typed by its name comes first, then its base books alone`() =
        runBlocking {
            assumeTrue("No database at $dbPath", File(dbPath).exists())
            val lookup = appLookup(dbPath)
            for (name in listOf("משנה תורה", "שולחן ערוך", "תלמוד")) {
                val books = lookup.suggestBooks(name, limit = 120).map { BookSuggestion(it.toBook(), listOf(it.title), it.exactAcronym) }
                val found = CategorySuggestions.match(name, categories.categories(), limit = 4)
                val rows = catBookRows(name, emptyList(), textSearch = true, authors = emptyList(), books = books, categories = found)
                val first = rows.take(2).map { (it as? CatBookRow.Category)?.hit }
                assertEquals(listOf(name, name), first.map { it?.title }, rows.take(3).toString())
                assertEquals(listOf(false, true), first.map { it?.baseBooksOnly })
            }
        }

    @Test
    fun `a category's base books are its texts, without their commentaries`() =
        runBlocking {
            assumeTrue("No database at $dbPath", File(dbPath).exists())
            val talmud = categories.categories().first { it.path == listOf("תלמוד") }
            val all = categories.bookIds(talmud.id)
            val base = categories.bookIds(talmud.id, baseBooksOnly = true)
            assertTrue(base.isNotEmpty() && base.size < all.size && all.containsAll(base))
            assertEquals(all.toSet(), repository.getBooksUnderCategoryTree(talmud.id).map { it.id }.toSet())
            // The tractates: base books all of them, in the database too
            assertTrue(base.all { repository.getBookCore(it)?.isBaseBook == true })
            // Rashi on the Torah: commentaries only, offered whole
            val rashiTorah = categories.categories().first { it.path.takeLast(2) == listOf("רש״י", "תורה") }
            assertTrue(!rashiTorah.hasBaseBooksAndOthers)
        }

    @Test
    fun `a category without curated base books is searched without the commentaries on its own books`() =
        runBlocking {
            assumeTrue("No database at $dbPath", File(dbPath).exists())

            suspend fun category(vararg path: String) = categories.categories().first { it.path.takeLast(path.size) == path.toList() }

            suspend fun titles(
                hit: CategoryHit,
                base: Boolean,
            ) = categories.bookIds(hit.id, baseBooksOnly = base).map { repository.getBookCore(it)?.title.orEmpty() }

            // The מהר״ל's nine books, without הרטמן's notes and "הערות ומקורות על תפארת ישראל"
            val maharal = category("מהר״ל מפראג")
            assertTrue(maharal.hasBaseBooksAndOthers)
            val books = titles(maharal, base = true)
            assertEquals(9, books.size, books.toString())
            assertTrue("גבורות השם" in books && "נצח ישראל" in books, books.toString())
            assertTrue(books.none { "הערות" in it }, books.toString())
            assertEquals(18, titles(maharal, base = false).size)
            // The זהר comments on the Torah, outside: in קבלה › זהר it is a base book
            val zohar = category("קבלה", "זהר")
            assertTrue(zohar.hasBaseBooksAndOthers)
            val zoharBooks = titles(zohar, base = true)
            assertTrue("ספר הזהר" in zoharBooks && "הסולם על ספר הזהר" !in zoharBooks, zoharBooks.toString())
            // The הגדה and its commentaries
            assertTrue(category("סדר התפילה", "הגדה").hasBaseBooksAndOthers)
            // The curated corpora as before: תלמוד's 76 tractates
            assertEquals(76, categories.bookIds(category("תלמוד").id, baseBooksOnly = true).size)
            // One note among שו״ת's 405 books: not offered
            assertTrue(!category("שו״ת").hasBaseBooksAndOthers)
        }

    @Test
    fun `a search in the מהר״ל's books alone finds no line of the notes on them`() =
        runBlocking {
            assumeTrue("No full-text index beside $dbPath", File("$dbPath.lucene").isDirectory)
            val maharal = categories.categories().first { it.path.last() == "מהר״ל מפראג" }
            val base = categories.bookIds(maharal.id, baseBooksOnly = true).toSet()
            val all = categories.bookIds(maharal.id)
            val engine =
                LuceneSearchEngine(
                    Paths.get("$dbPath.lucene"),
                    RepositorySnippetSourceProvider(repository),
                    dictionaryPath = Paths.get(dbPath).resolveSibling("lexical.db"),
                )
            engine.use {
                fun books(ids: Collection<Long>) =
                    runBlocking {
                        engine.openSession("גבורות השם", near = 5, bookIds = ids)!!.use { session ->
                            generateSequence { runBlocking { session.nextPage(200, snippets = false) } }
                                .takeWhile { it.hits.isNotEmpty() }
                                .take(20)
                                .flatMap { it.hits }
                                .map { it.bookId }
                                .toSet()
                        }
                    }
                val inAll = books(all)
                // The notes do quote it
                assertTrue((inAll - base).isNotEmpty(), inAll.toString())
                val inBase = books(base)
                assertTrue(inBase.isNotEmpty() && inBase.all { it in base }, inBase.toString())
            }
        }
}
