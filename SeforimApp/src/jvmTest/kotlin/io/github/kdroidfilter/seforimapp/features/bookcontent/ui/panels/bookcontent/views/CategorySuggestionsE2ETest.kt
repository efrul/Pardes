package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.features.search.domain.CategorySuggestions
import io.github.kdroidfilter.seforimapp.features.search.domain.LibraryCategories
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.search.titles.appLookup
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
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
    private val categories by lazy { LibraryCategories(CatalogCache(provider), repository) }

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
}
