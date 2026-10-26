package io.github.kdroidfilter.seforimapp.android.delta

import android.database.sqlite.SQLiteDatabase
import io.github.kdroidfilter.seforimlibrary.deltaupdater.LuceneUpdater
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.core.LowerCaseFilter
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
import org.apache.lucene.index.DocValuesType
import org.apache.lucene.index.FieldInfos
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.SerialMergeScheduler
import org.apache.lucene.index.TieredMergePolicy
import org.apache.lucene.store.NIOFSDirectory
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.io.File

/**
 * Where a delta's line changes go into the full-text index (`seforim.db.lucene`): desktop 0.6.10
 * `framework/update/DbDeltaUpdateService.kt` defaultLuceneSinksProvider (one IndexWriter per delta, book metadata looked
 * up in the live seforim.db after the COMMIT and cached per book, delete-then-add per line id), with two changes:
 *
 *  - The documents are those of the index's own writer, SeforimLibrary (7ddf045) generator
 *    `searchindex/lucene/LuceneTextIndexWriter.kt` addLine, analysed and normalised as `BuildLuceneIndex.kt` does
 *    (StandardAnalyzer, plus the 4-gram analyzer on `text_ng4`; Jsoup.clean + normalizePostHtmlForIndex). Desktop's
 *    sink writes a reduced document (no `text_ng4`, no `ancestor_category_ids`, `line_index`/`order_index`/
 *    `is_base_book` stored only): the search would miss those lines in substring and category searches, and Lucene
 *    rejects a document whose fields drop the points the index has for them. Fields the index carries without points
 *    or doc values (an older release) are written the same way, like desktop's book_id doc-values check.
 *  - The index opens with [NIOFSDirectory] (no MMapDirectory on Android), appends only, merges on the calling thread
 *    and leaves segments over 512 MB alone (a phone cannot rewrite the 5 GB main segment), and is committed only by
 *    [LuceneUpdater.SinkSession.commit]; a session closed without it rolls the writer back.
 *
 * No index (a library without search): a no-op session, as desktop without a luceneIndexDir.
 */
object LuceneDeltaSinks {
    // Field names of LuceneTextIndexWriter.
    private const val FIELD_TYPE = "type"
    private const val TYPE_LINE = "line"
    private const val FIELD_BOOK_ID = "book_id"
    private const val FIELD_CATEGORY_ID = "category_id"
    private const val FIELD_ANCESTOR_CATEGORY_IDS = "ancestor_category_ids"
    private const val FIELD_BOOK_TITLE = "book_title"
    private const val FIELD_LINE_ID = "line_id"
    private const val FIELD_LINE_INDEX = "line_index"
    private const val FIELD_TEXT = "text"
    private const val FIELD_TEXT_NG4 = "text_ng4"
    private const val FIELD_ORDER_INDEX = "order_index"
    private const val FIELD_IS_BASE_BOOK = "is_base_book"

    fun provider(indexDir: File, seforimDb: File, onLine: () -> Unit = {}): () -> LuceneUpdater.SinkSession = {
        if (indexDir.list()?.any { it.startsWith("segments_") } != true) {
            LuceneUpdater.SinkSession(delete = { }, upsert = { })
        } else {
            open(indexDir, seforimDb, onLine)
        }
    }

    /** BuildLuceneIndex's analyzer: StandardAnalyzer, and on text_ng4 StandardTokenizer + LowerCaseFilter + 4-grams. */
    fun analyzer(): Analyzer {
        val ngram4Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val src = StandardTokenizer()
                var ts: TokenStream = src
                ts = LowerCaseFilter(ts)
                ts = NGramTokenFilter(ts, 4, 4, false)
                return TokenStreamComponents(src, ts)
            }
        }
        return PerFieldAnalyzerWrapper(StandardAnalyzer(), mapOf(FIELD_TEXT_NG4 to ngram4Analyzer))
    }

    private fun open(indexDir: File, seforimDb: File, onLine: () -> Unit): LuceneUpdater.SinkSession {
        val dir = NIOFSDirectory(indexDir.toPath())
        val config = IndexWriterConfig(analyzer()).apply {
            openMode = IndexWriterConfig.OpenMode.APPEND
            commitOnClose = false
            mergeScheduler = SerialMergeScheduler()
            mergePolicy = TieredMergePolicy().apply {
                maxMergedSegmentMB = 1024.0
                deletesPctAllowed = 50.0
            }
        }
        val writer = try {
            IndexWriter(dir, config)
        } catch (t: Throwable) {
            runCatching { dir.close() }
            throw t
        }
        val db: SQLiteDatabase
        val fields: FieldInfos
        try {
            fields = DirectoryReader.open(writer).use { reader -> FieldInfos.getMergedFieldInfos(reader) }
            db = openLibraryDatabase(seforimDb)
        } catch (t: Throwable) {
            runCatching { writer.rollback() }
            runCatching { dir.close() }
            throw t
        }
        val schema = IndexSchema(fields)
        val books = HashMap<Long, BookMeta>()
        val ancestorsByCategory = HashMap<Long, List<Long>>()

        fun bookMeta(bookId: Long): BookMeta = books.getOrPut(bookId) {
            db.rawQuery("SELECT title, categoryId, orderIndex, isBaseBook FROM book WHERE id = ?", arrayOf(bookId.toString())).use { c ->
                if (c.moveToFirst()) BookMeta(c.getString(0).orEmpty(), c.getLong(1), c.getLong(2), c.getInt(3) != 0) else BookMeta.EMPTY
            }
        }

        // SeforimRepository.getAncestorCategoryIds (CategoryClosureQueries.selectAncestors), cached per category.
        fun ancestors(categoryId: Long): List<Long> = ancestorsByCategory.getOrPut(categoryId) {
            db.rawQuery("SELECT ancestorId FROM category_closure WHERE descendantId = ?", arrayOf(categoryId.toString())).use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0)) }
            }
        }

        fun deleteLine(id: Long) {
            writer.deleteDocuments(IntPoint.newExactQuery(FIELD_LINE_ID, id.toInt()))
        }

        return LuceneUpdater.SinkSession(
            delete = { id ->
                deleteLine(id)
                onLine()
            },
            upsert = { line ->
                val meta = bookMeta(line.bookId)
                deleteLine(line.id)
                writer.addDocument(lineDocument(schema, line, meta, ancestors(meta.categoryId)))
                onLine()
            },
            onCommit = { writer.commit() },
            onClose = { committed ->
                try {
                    if (committed) writer.close() else writer.rollback()
                } finally {
                    runCatching { dir.close() }
                    runCatching { db.close() }
                }
            },
        )
    }

    /** LuceneTextIndexWriter.addLine for one patch line (BuildLuceneIndex: base books get orderIndex - 5, at least 1). */
    private fun lineDocument(schema: IndexSchema, line: LuceneUpdater.PatchLine, meta: BookMeta, ancestors: List<Long>): Document {
        val orderIndex = (if (meta.isBaseBook) (meta.orderIndex - 5).coerceAtLeast(1) else meta.orderIndex).toInt()
        val isBase = if (meta.isBaseBook) 1 else 0
        val normalized = normalizeForIndexDefault(line.content)
        return Document().apply {
            if (schema.indexed(FIELD_TYPE)) add(StringField(FIELD_TYPE, TYPE_LINE, Field.Store.NO))

            add(StoredField(FIELD_BOOK_ID, line.bookId))
            if (schema.points(FIELD_BOOK_ID)) add(IntPoint(FIELD_BOOK_ID, line.bookId.toInt()))
            if (schema.docValues(FIELD_BOOK_ID)) add(NumericDocValuesField(FIELD_BOOK_ID, line.bookId))
            add(StoredField(FIELD_CATEGORY_ID, meta.categoryId))
            if (schema.points(FIELD_CATEGORY_ID)) add(IntPoint(FIELD_CATEGORY_ID, meta.categoryId.toInt()))
            add(StoredField(FIELD_BOOK_TITLE, meta.title))

            if (schema.points(FIELD_ANCESTOR_CATEGORY_IDS)) ancestors.forEach { add(IntPoint(FIELD_ANCESTOR_CATEGORY_IDS, it.toInt())) }
            add(StoredField(FIELD_ANCESTOR_CATEGORY_IDS, ancestors.joinToString(",")))

            add(StoredField(FIELD_LINE_ID, line.id))
            if (schema.points(FIELD_LINE_ID)) add(IntPoint(FIELD_LINE_ID, line.id.toInt()))
            add(StoredField(FIELD_LINE_INDEX, line.lineIndex))
            if (schema.points(FIELD_LINE_INDEX)) add(IntPoint(FIELD_LINE_INDEX, line.lineIndex))

            add(StoredField(FIELD_ORDER_INDEX, orderIndex))
            if (schema.points(FIELD_ORDER_INDEX)) add(IntPoint(FIELD_ORDER_INDEX, orderIndex))
            add(StoredField(FIELD_IS_BASE_BOOK, isBase))
            if (schema.points(FIELD_IS_BASE_BOOK)) add(IntPoint(FIELD_IS_BASE_BOOK, isBase))

            if (schema.indexed(FIELD_TEXT)) add(TextField(FIELD_TEXT, normalized, Field.Store.NO))
            if (schema.indexed(FIELD_TEXT_NG4)) add(TextField(FIELD_TEXT_NG4, normalized, Field.Store.NO))
        }
    }

    /** What the index already has per field; a field it lacks gets the writer's full set (new fields are free). */
    private class IndexSchema(private val infos: FieldInfos) {
        fun points(field: String): Boolean = infos.fieldInfo(field)?.let { it.pointDimensionCount > 0 } ?: true

        fun docValues(field: String): Boolean = infos.fieldInfo(field)?.let { it.docValuesType == DocValuesType.NUMERIC } ?: true

        fun indexed(field: String): Boolean = infos.fieldInfo(field)?.let { it.indexOptions != IndexOptions.NONE } ?: true
    }

    private data class BookMeta(val title: String, val categoryId: Long, val orderIndex: Long, val isBaseBook: Boolean) {
        companion object {
            // Desktop's BookMeta.EMPTY.
            val EMPTY = BookMeta("", 0L, 999L, false)
        }
    }

    /** BuildLuceneIndex.normalizeForIndexDefault: strip HTML, then the single-pass Hebrew normalisation. */
    internal fun normalizeForIndexDefault(html: String): String = normalizePostHtmlForIndex(Jsoup.clean(html, Safelist.none()))

    /**
     * BuildLuceneIndex.normalizePostHtmlForIndex, verbatim: drops nikud, teamim and gershayim/geresh, maqaf and
     * whitespace runs become one space, final letters become their regular form.
     */
    private fun normalizePostHtmlForIndex(text: String): String {
        val sb = StringBuilder(text.length)
        var prevWasSpace = true
        for (c in text) {
            val code = c.code
            if ((code in 0x05B0..0x05BD) || code == 0x05C1 || code == 0x05C2 || code == 0x05C7) continue
            if (code in 0x0591..0x05AF) continue
            if (code == 0x05F4 || code == 0x05F3) continue
            if (code == 0x05BE || c.isWhitespace()) {
                if (!prevWasSpace) {
                    sb.append(' ')
                    prevWasSpace = true
                }
                continue
            }
            val mapped = when (code) {
                0x05DA -> 'כ' // ך -> כ
                0x05DD -> 'מ' // ם -> מ
                0x05DF -> 'נ' // ן -> נ
                0x05E3 -> 'פ' // ף -> פ
                0x05E5 -> 'צ' // ץ -> צ
                else -> c
            }
            sb.append(mapped)
            prevWasSpace = false
        }
        if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }
}
