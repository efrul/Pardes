package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.search.MagicDictionaryIndex
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PardesLexiconTest {
    private val dir: Path = Files.createTempDirectory("pardes-lexicon")
    private val lexical: Path = dir.resolve("lexical.db")
    private val index: Path = dir.resolve("seforim.db.lucene")

    /** ברכה with three forms, and ברכות also as a base without forms, as lexical.db has it. */
    private fun library() {
        DriverManager.getConnection("jdbc:sqlite:$lexical").use { c ->
            c.createStatement().use { st ->
                listOf(
                    "CREATE TABLE base (id INTEGER PRIMARY KEY, value TEXT NOT NULL UNIQUE)",
                    "CREATE TABLE surface (id INTEGER PRIMARY KEY, value TEXT NOT NULL UNIQUE, base_id INTEGER NOT NULL, notes TEXT)",
                    "CREATE TABLE variant (id INTEGER PRIMARY KEY, value TEXT NOT NULL UNIQUE)",
                    "CREATE TABLE surface_variant (surface_id INTEGER NOT NULL, variant_id INTEGER NOT NULL, PRIMARY KEY (surface_id, variant_id))",
                    "CREATE TABLE surface_extra_base (surface_id INTEGER NOT NULL, base_id INTEGER NOT NULL, PRIMARY KEY (surface_id, base_id))",
                    "CREATE INDEX surface_base_id_index ON surface(base_id)",
                    "INSERT INTO base VALUES (1, 'ברכה'), (2, 'ברכות')",
                    "INSERT INTO surface VALUES (1, 'ברכות', 1, NULL), (2, 'בברכה', 1, NULL), (3, 'ברכה', 1, NULL)",
                    "INSERT INTO variant VALUES (1, 'וברכות')",
                    "INSERT INTO surface_variant VALUES (1, 1)",
                ).forEach { st.execute(it) }
            }
        }
        // ברכה in three lines, בברכה in two, ברכות in one
        IndexWriter(FSDirectory.open(index), IndexWriterConfig(StandardAnalyzer())).use { w ->
            listOf("ברכה", "ברכה", "ברכה בברכה", "בברכה", "ברכות").forEach { text ->
                w.addDocument(Document().apply { add(TextField("text", text, Field.Store.NO)) })
            }
        }
    }

    private fun forms(
        dictionary: Path,
        word: String,
    ): List<String> = MagicDictionaryIndex.load(HebrewTextUtils::normalizeHebrew, dictionary)!!.expansionFor(word)!!.surface

    @Test
    fun emptyBaseDroppedAndCommonestFormsFirst() {
        library()
        // lexical.db: the empty base ברכות wins, and the word has no other form
        assertEquals(listOf("ברכות"), forms(lexical, "ברכות"))

        val fixed = PardesLexicon.dictionaryFor(lexical, index)
        PardesLexicon.awaitBuild(60_000)
        assertEquals(dir.resolve(PardesLexicon.FILE), fixed)
        assertEquals(listOf("ברכה", "בברכה", "ברכות", "וברכות"), forms(fixed, "ברכות"))
        // Up to date: kept
        assertEquals(fixed, PardesLexicon.dictionaryFor(lexical, index))
    }

    @Test
    fun rebuiltWhenTheLibraryChanges() {
        library()
        PardesLexicon.dictionaryFor(lexical, index)
        PardesLexicon.awaitBuild(60_000)
        val before = PardesLexicon.storedFingerprint(dir.resolve(PardesLexicon.FILE))
        DriverManager.getConnection("jdbc:sqlite:$lexical").use { c ->
            c.createStatement().use { it.execute("INSERT INTO surface VALUES (4, 'לברכה', 1, NULL)") }
        }
        Files.setLastModifiedTime(
            lexical,
            java.nio.file.attribute.FileTime
                .fromMillis(System.currentTimeMillis() + 5_000),
        )
        PardesLexicon.dictionaryFor(lexical, index)
        PardesLexicon.awaitBuild(60_000)
        assertNotEquals(before, PardesLexicon.storedFingerprint(dir.resolve(PardesLexicon.FILE)))
        assertEquals("ברכה", forms(dir.resolve(PardesLexicon.FILE), "לברכה").first())
    }
}
