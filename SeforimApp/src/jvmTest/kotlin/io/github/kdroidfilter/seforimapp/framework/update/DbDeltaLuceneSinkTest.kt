package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.seforimlibrary.deltaupdater.LuceneUpdater
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.ngram.NGramTokenFilter
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A line a delta writes into the full-text index must read as the release's own lines do, which SeforimLibrary's
 * LuceneTextIndexWriter.addLine writes with BuildLuceneIndex's analyzer: otherwise the search misses it.
 */
class DbDeltaLuceneSinkTest {
    private val root = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a line a delta writes is found as the release's own lines are`() {
        val index = root.toPath().resolve("seforim.db.lucene")
        writeReleaseIndex(index)

        DbDeltaUpdateService.defaultLuceneSinksProvider(index, database())().use { session ->
            val content = "<b>וַיֹּאמֶר</b> אֱלֹהִים יְהִי אוֹר"
            session.upsert.upsertLine(LuceneUpdater.PatchLine(id = 2, bookId = 10, lineIndex = 7, content = content, heRef = null))
        }

        DirectoryReader.open(FSDirectory.open(index)).use { reader ->
            val searcher = IndexSearcher(reader)

            fun lines(query: Query): List<Long> =
                searcher.search(query, 10).scoreDocs.map { hit ->
                    searcher
                        .storedFields()
                        .document(hit.doc)
                        .getField("line_id")
                        .numericValue()
                        .toLong()
                }

            // The words without nikud and HTML, as the search asks for them
            assertEquals(listOf(2L), lines(TermQuery(Term("text", "ויאמר"))))
            // Inside a word
            assertEquals(listOf(2L), lines(TermQuery(Term("text_ng4", "יאמר"))))
            // Under the category above its own
            assertEquals(listOf(2L), lines(IntPoint.newExactQuery("ancestor_category_ids", 3)))
            // Its place in the book, and a base book's order brought forward as the release's
            val doc =
                searcher
                    .search(IntPoint.newExactQuery("line_index", 7), 1)
                    .scoreDocs
                    .single()
                    .doc
            assertEquals(
                5,
                searcher
                    .storedFields()
                    .document(doc)
                    .getField("order_index")
                    .numericValue()
                    .toInt(),
            )
        }
    }

    /** The release's index with one line of book 20, written as LuceneTextIndexWriter.addLine writes its lines. */
    private fun writeReleaseIndex(index: Path) {
        FSDirectory.open(index).use { dir ->
            IndexWriter(dir, IndexWriterConfig(releaseAnalyzer())).use { writer ->
                writer.addDocument(
                    Document().apply {
                        add(StringField("type", "line", Field.Store.NO))
                        add(StoredField("book_id", 20L))
                        add(IntPoint("book_id", 20))
                        add(NumericDocValuesField("book_id", 20L))
                        add(StoredField("category_id", 9L))
                        add(IntPoint("category_id", 9))
                        add(StoredField("book_title", "ספר אחר"))
                        add(IntPoint("ancestor_category_ids", 9))
                        add(StoredField("ancestor_category_ids", "9"))
                        add(StoredField("line_id", 1L))
                        add(IntPoint("line_id", 1))
                        add(StoredField("line_index", 0))
                        add(IntPoint("line_index", 0))
                        add(StoredField("order_index", 40))
                        add(IntPoint("order_index", 40))
                        add(StoredField("is_base_book", 0))
                        add(IntPoint("is_base_book", 0))
                        add(TextField("text", "בראשית ברא", Field.Store.NO))
                        add(TextField("text_ng4", "בראשית ברא", Field.Store.NO))
                    },
                )
            }
        }
    }

    /** BuildLuceneIndex's analyzer: StandardAnalyzer, and on text_ng4 StandardTokenizer + LowerCaseFilter + 4-grams. */
    private fun releaseAnalyzer(): Analyzer {
        val ngrams =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source = StandardTokenizer()
                    val stream: TokenStream = NGramTokenFilter(LowerCaseFilter(source), 4, 4, false)
                    return TokenStreamComponents(source, stream)
                }
            }
        return PerFieldAnalyzerWrapper(StandardAnalyzer(), mapOf("text_ng4" to ngrams))
    }

    /** Book 10, a base book of order 10, in category 5, which is under category 3. */
    private fun database(): Path {
        val db = root.toPath().resolve("seforim.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { con ->
            con.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT, categoryId INTEGER, orderIndex INTEGER, isBaseBook INTEGER)",
                )
                st.executeUpdate("CREATE TABLE category_closure (ancestorId INTEGER, descendantId INTEGER)")
                st.executeUpdate("INSERT INTO book VALUES (10, 'בראשית', 5, 10, 1)")
                st.executeUpdate("INSERT INTO category_closure VALUES (3, 5), (5, 5)")
            }
        }
        return db
    }
}
