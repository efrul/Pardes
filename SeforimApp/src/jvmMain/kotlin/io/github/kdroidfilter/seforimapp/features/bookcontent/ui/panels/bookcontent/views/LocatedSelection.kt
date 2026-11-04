package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor

/**
 * Pardes: a selection its content locates in the library itself, the daf view's, whose printed words are not the
 * book's lines' text (the text's menu would search the book's lines for them): each library line's part of it, in
 * that line's own book ([ranges]); the note it anchors, on the main book's first line of it (null when it has none);
 * and the line a link to it goes to ([lineId]).
 */
data class LocatedSelection(
    val ranges: List<LocatedRange>,
    val note: NoteDraftAnchor?,
    val lineId: Long?,
)

/** The characters [start] until [end] of the line [lineId] of book [bookId], in its plain text with diacritics. */
data class LocatedRange(
    val bookId: Long,
    val lineId: Long,
    val start: Int,
    val end: Int,
)

/**
 * Pardes: the content's located selection, which the book's text menu (BookTextMenus) reads when it opens, for its
 * highlights, its note and its link, in place of a search of the selected text; null for the book's own text.
 */
val LocalLocatedSelection = staticCompositionLocalOf<(() -> LocatedSelection?)?> { null }
