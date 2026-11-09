package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.seforimlibrary.deltaupdater.LuceneUpdater
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
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
import org.apache.lucene.index.DocValuesType
import org.apache.lucene.index.FieldInfos
import org.apache.lucene.index.IndexOptions
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * A line of the full-text index as SeforimLibrary writes the release's (its LuceneTextIndexWriter.addLine, with
 * BuildLuceneIndex's analyzer and normalization), for the lines a delta upserts: the search then finds them as it finds
 * the others, by their words without nikud, inside words (text_ng4) and under their categories. Each field gets only
 * the structures the index already has for it, as Lucene refuses a document whose field schema differs from the index's.
 */
internal object LuceneLineDocument {
    private const val FIELD_TYPE = "type"
    private const val TYPE_LINE = "line"
    private const val FIELD_BOOK_ID = "book_id"
    private const val FIELD_CATEGORY_ID = "category_id"
    private const val FIELD_ANCESTOR_CATEGORY_IDS = "ancestor_category_ids"
    private const val FIELD_BOOK_TITLE = "book_title"
    private const val FIELD_LINE_ID = "line_id"
    private const val FIELD_LINE_INDEX = "line_index"
    private const val FIELD_ORDER_INDEX = "order_index"
    private const val FIELD_IS_BASE_BOOK = "is_base_book"
    private const val FIELD_TEXT = "text"
    private const val FIELD_TEXT_NG4 = "text_ng4"

    // BuildLuceneIndex's normalization: nikud, teamim, gershayim and geresh go; a maqaf is a space; final letters as the others
    private val DROPPED: Set<Int> = (0x0591..0x05BD).toSet() + setOf(0x05C1, 0x05C2, 0x05C7, 0x05F3, 0x05F4)
    private const val MAQAF = 0x05BE
    private val FINAL_LETTERS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

    /** The book of a line, as its document records it. */
    data class Book(
        val title: String,
        val categoryId: Long,
        val ancestorCategoryIds: List<Long>,
        val orderIndex: Long,
        val isBaseBook: Boolean,
    )

    /** BuildLuceneIndex's analyzer: StandardAnalyzer, and on text_ng4 StandardTokenizer, LowerCaseFilter and 4-grams. */
    fun analyzer(): Analyzer {
        val ngrams =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source = StandardTokenizer()
                    return TokenStreamComponents(source, NGramTokenFilter(LowerCaseFilter(source), 4, 4, false))
                }
            }
        return PerFieldAnalyzerWrapper(StandardAnalyzer(), mapOf(FIELD_TEXT_NG4 to ngrams))
    }

    /** [line] of [book] for an index whose fields are [fields]. */
    fun create(
        fields: FieldInfos,
        line: LuceneUpdater.PatchLine,
        book: Book,
    ): Document {
        // BuildLuceneIndex brings base books a little forward
        val orderIndex = if (book.isBaseBook) (book.orderIndex - 5).coerceAtLeast(1) else book.orderIndex
        val text = normalize(Jsoup.clean(line.content, Safelist.none()))
        return Document().apply {
            if (fields.indexed(FIELD_TYPE)) add(StringField(FIELD_TYPE, TYPE_LINE, Field.Store.NO))
            addId(fields, FIELD_BOOK_ID, line.bookId)
            if (fields.hasNumericDocValues(FIELD_BOOK_ID)) add(NumericDocValuesField(FIELD_BOOK_ID, line.bookId))
            addId(fields, FIELD_CATEGORY_ID, book.categoryId)
            add(StoredField(FIELD_BOOK_TITLE, book.title))
            if (fields.hasPoints(FIELD_ANCESTOR_CATEGORY_IDS)) {
                book.ancestorCategoryIds.forEach { add(IntPoint(FIELD_ANCESTOR_CATEGORY_IDS, it.toInt())) }
            }
            add(StoredField(FIELD_ANCESTOR_CATEGORY_IDS, book.ancestorCategoryIds.joinToString(",")))
            addId(fields, FIELD_LINE_ID, line.id)
            addNumber(fields, FIELD_LINE_INDEX, line.lineIndex)
            addNumber(fields, FIELD_ORDER_INDEX, orderIndex.toInt())
            addNumber(fields, FIELD_IS_BASE_BOOK, if (book.isBaseBook) 1 else 0)
            addText(fields, FIELD_TEXT, text)
            addText(fields, FIELD_TEXT_NG4, text)
        }
    }

    // A field the index does not have yet takes the writer's structures: a new field is free
    private fun FieldInfos.hasPoints(field: String) = fieldInfo(field)?.let { it.pointDimensionCount > 0 } ?: true

    private fun FieldInfos.hasNumericDocValues(field: String) = fieldInfo(field)?.let { it.docValuesType == DocValuesType.NUMERIC } ?: true

    private fun FieldInfos.indexed(field: String) = fieldInfo(field)?.let { it.indexOptions != IndexOptions.NONE } ?: true

    /** An id as the writer stores it (a long), with its point. */
    private fun Document.addId(
        fields: FieldInfos,
        name: String,
        value: Long,
    ) {
        add(StoredField(name, value))
        if (fields.hasPoints(name)) add(IntPoint(name, value.toInt()))
    }

    /** A number as the writer stores it (an int), with its point. */
    private fun Document.addNumber(
        fields: FieldInfos,
        name: String,
        value: Int,
    ) {
        add(StoredField(name, value))
        if (fields.hasPoints(name)) add(IntPoint(name, value))
    }

    private fun Document.addText(
        fields: FieldInfos,
        name: String,
        text: String,
    ) {
        if (fields.indexed(name)) add(TextField(name, text, Field.Store.NO))
    }

    /** BuildLuceneIndex's normalizePostHtmlForIndex: the same characters dropped, spaced and replaced, in one pass. */
    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var prevWasSpace = true
        for (c in text) {
            when {
                c.code in DROPPED -> Unit
                c.code == MAQAF || c.isWhitespace() -> {
                    if (!prevWasSpace) sb.append(' ')
                    prevWasSpace = true
                }
                else -> {
                    sb.append(FINAL_LETTERS[c] ?: c)
                    prevWasSpace = false
                }
            }
        }
        if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }
}
