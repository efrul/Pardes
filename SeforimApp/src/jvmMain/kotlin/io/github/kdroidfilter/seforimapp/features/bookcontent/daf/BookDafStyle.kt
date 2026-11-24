package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.serialization.Serializable

// Pardes (request 21 of docs/DESKTOP_STAGE2.md, taken up again on 2026-11-23 with the user's rules for setting a page of
// text and commentaries, docs/BOOK_DAF_RULES.md): how a book's daf sets its pages, as the reader chose it. The rules'
// numbers are the defaults here, in the rules' own unit (a page whose block of text is 616 wide), and every one of them
// may be changed, for all the books or for one.

/** The ways a book's daf sets its pages. */
@Serializable
enum class DafStyle {
    /**
     * As the Mikraot Gedolot: pages of one size, the text at the top with its targum beside it, each commentator in a
     * band under it, in balanced columns that go on from page to page (BookDafBands, the rules' own).
     */
    Bands,

    /** The text in the middle and the commentators around it, as the Vilna Shas sets the Gemara (DafAroundSetter). */
    Around,
}

/**
 * How a book's daf sets its pages: its [layout]; the letters of its text and of its commentaries ([textSize],
 * [commentarySize], in the rules' unit: a block of text [RULES_WIDTH] wide); a commentator's [columns]; the share of
 * the width the targum beside the text takes ([sideShare], none at 0); the page's height to its width ([aspect]);
 * whether the text's lines run on in one paragraph, as verses do ([runOn]; null: the Tanakh's alone); and whether the
 * commentaries are in the text's square letters rather than Rashi script ([squareCommentary]). On a page wider than
 * tall, the view's own where the pages follow its width, a commentator has [wideColumns] (request 99 of
 * docs/DESKTOP_STAGE3_PLAN.md). [together]: the text waits for its commentaries, a page of them alone going on where
 * they are longer than a page, and a paragraph is cut between two pages where the comments on it end (request 98);
 * without it every page takes a new line of the text, as the rules' third has it, and a long commentary lags behind.
 */
@Serializable
data class BookDafStyle(
    val layout: DafStyle = DafStyle.Bands,
    val textSize: Float = DEFAULT_TEXT_SIZE,
    val commentarySize: Float = DEFAULT_COMMENTARY_SIZE,
    val columns: Int = DEFAULT_COLUMNS,
    val sideShare: Float = DEFAULT_SIDE_SHARE,
    val aspect: Float = DEFAULT_ASPECT,
    val runOn: Boolean? = null,
    val squareCommentary: Boolean = false,
    val wideColumns: Int = DEFAULT_WIDE_COLUMNS,
    val together: Boolean = true,
) {
    /** The style with every value inside what a page can be set with. */
    fun sane() =
        copy(
            textSize = textSize.coerceIn(TEXT_SIZES),
            commentarySize = commentarySize.coerceIn(COMMENTARY_SIZES),
            columns = columns.coerceIn(COLUMNS),
            sideShare = sideShare.coerceIn(SIDE_SHARES),
            aspect = aspect.coerceIn(ASPECTS),
            wideColumns = wideColumns.coerceIn(BAND_COLUMNS),
        )

    companion object {
        /** The width of the block of text the rules' numbers are for. */
        const val RULES_WIDTH = 616f

        const val DEFAULT_TEXT_SIZE = 22f
        const val DEFAULT_COMMENTARY_SIZE = 12.5f
        const val DEFAULT_COLUMNS = 2
        const val DEFAULT_WIDE_COLUMNS = 3
        const val DEFAULT_SIDE_SHARE = 0.38f
        const val DEFAULT_ASPECT = 848f / RULES_WIDTH

        val TEXT_SIZES = 14f..30f
        val COMMENTARY_SIZES = 9f..18f
        val COLUMNS = 1..3

        /** The columns a commentator may have: on a wide page, or its own. */
        val BAND_COLUMNS = 1..5
        val SIDE_SHARES = 0f..0.5f
        val ASPECTS = 1f..1.8f
    }
}

/**
 * A commentator's own letters on a book's pages, where the reader set them apart from the others' (request 93 of
 * docs/DESKTOP_STAGE3_PLAN.md): their [size] (the rules' unit), its [columns], and whether they are the text's [square]
 * letters; each the style's where null.
 */
@Serializable
data class BandStyle(
    val size: Float? = null,
    val columns: Int? = null,
    val square: Boolean? = null,
)

/**
 * What a book's pages show of its commentators, as the reader chose on them (requests 92 and 93): those [shown], in the
 * order of their bands (null: none chosen yet, and the pages take the book's usual ones); the one set [beside] the text,
 * in its targum's place; and each one's own letters ([bands]), by its book's id.
 */
@Serializable
data class BookDafCommentators(
    val shown: List<Long>? = null,
    val beside: Long? = null,
    val bands: Map<Long, BandStyle> = emptyMap(),
)

/**
 * A book's daf's settings (Settings › צורת הדף): whether a book that is no tractate is offered pages of its own, an
 * experiment that is off until turned on there; the [style] of every book's pages, and a book's own by its id
 * ([books]); a book's commentators on its pages ([commentators]); and whether the pages follow the view's width, set
 * again for it rather than made larger ([wide], request 89).
 */
object BookDafSettings : SettingsGroup("book_daf") {
    val enabled by boolean(false)

    val style by json(BookDafStyle())

    val books by json<Map<Long, BookDafStyle>>(emptyMap())

    val commentators by json<Map<Long, BookDafCommentators>>(emptyMap())

    val wide by boolean(false)
}

/** Whether [this] is a book of the Tanakh, whose verses run on in one paragraph. */
internal val Book.isTanakh: Boolean
    get() = with(CatalogPresets.Ids.Categories) { categoryId in TORAH..KETUVIM }

/**
 * Whether [book], no tractate, is offered pages of its own (BookDafView): the experiment is on, and it has commentaries
 * to set under its text. Not a commentary printed on a tractate's daf, whose button opens that daf (requests 82 and 86
 * of stage 3).
 */
@Composable
internal fun hasBookDaf(book: Book): Boolean {
    val enabled by LocalAppGraph.current.appSettings
        .flow(BookDafSettings.enabled)
        .collectAsState()
    val printed = rememberDafTractate(book) != null
    return enabled && !printed && book.hasCommentaryConnection
}

/** Whether the tab of [uiState] shows a book's own pages. */
@Composable
internal fun showsBookDaf(uiState: BookContentState): Boolean {
    val book = uiState.navigation.selectedBook ?: return false
    return uiState.content.dafView && hasBookDaf(book)
}

/** The style [book]'s pages are set in: its own when it has one, else every book's. */
@Composable
internal fun rememberBookDafStyle(book: Book): BookDafStyle {
    val settings = LocalAppGraph.current.appSettings
    val all by settings.flow(BookDafSettings.style).collectAsState()
    val own by settings.flow(BookDafSettings.books).collectAsState()
    return (own[book.id] ?: all).sane()
}
