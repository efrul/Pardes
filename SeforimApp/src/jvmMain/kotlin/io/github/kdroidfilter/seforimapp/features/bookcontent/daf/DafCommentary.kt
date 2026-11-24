package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Pardes (request 82 of docs/DESKTOP_STAGE3_PLAN.md: "גם מרש"י או הפירוש המקביל אליו כאין רשי כמו כרשבם בבא בתרא או רן
// בנדרים למשל ותוס יוכלו לעבור לצורת הדף"): a commentary printed on a tractate's daf, in Rashi's column or Tosafot's,
// shows that daf in its tab as the tractate does, its comments what the daf selects, steps through and opens at.

/**
 * The tractate whose daf the tab of [book] shows: the book itself, a tractate of the Bavli; a commentary its layout
 * prints in a column of its comments (Rashi, Tosafot, the Rashbam in Bava Batra, the Ran in Nedarim:
 * [DafLayouts.tractateOf]), that tractate once read; else null.
 */
@Composable
internal fun rememberDafTractate(book: Book?): Book? {
    val layouts = rememberDafLayouts()
    val repository = LocalAppGraph.current.repository
    // The tractate of a commentary, with the commentary it is of: none other's while the next one is looked for
    val found by produceState<Pair<Long, Book?>?>(null, book?.id, layouts) {
        val commentary = book?.takeUnless { it.isBavli } ?: return@produceState
        value = commentary.id to
            runSuspendCatching {
                withContext(Dispatchers.IO) { layouts.tractateOf(commentary.id) }?.let { repository.getBookCore(it) }
            }.getOrNull()
    }
    return if (book?.isBavli == true) book else found?.takeIf { it.first == book?.id }?.second
}

/** The lines of the commentary [commentaryId], once read; null for none, and until they are. */
@Composable
internal fun rememberCommentaryLines(commentaryId: Long?): Set<Long>? {
    val repository = LocalAppGraph.current.repository
    val lines by produceState<Set<Long>?>(null, commentaryId) {
        value = null
        val id = commentaryId ?: return@produceState
        value = runSuspendCatching { repository.getLineTocMappingsForBook(id).mapTo(HashSet()) { it.lineId } }.getOrNull()
    }
    return lines
}

/**
 * The line of the commentary [commentaryId] that [word] stands for in the commentary's tab: its own comment's, or for a
 * Gemara word, the commentary's comment on the words there (the passage its dibbur quotes) or on the segment; null for
 * another commentary's word, and for a Gemara word it has no comment on.
 */
internal fun DafAmudLinks.commentaryLineAt(
    word: DafWord,
    commentaryId: Long,
): Long? {
    val id = lineIdOf(word) ?: return null
    if (!isGemara(word)) return id.takeIf { bookOf[it] == commentaryId }
    return passageAt(word).firstOrNull { bookOf[it] == commentaryId } ?: commentsOn[id].orEmpty().firstOrNull { bookOf[it] == commentaryId }
}

/**
 * The line of the tab that [word] stands for: its Gemara segment ([segmentOf]), or in a commentary's tab
 * ([commentaryId]) the commentary's comment ([commentaryLineAt]), for the text's menu and a link to it.
 */
internal fun DafAmudLinks.tabLineAt(
    word: DafWord,
    commentaryId: Long?,
): Long? = if (commentaryId == null) segmentOf(word) else commentaryLineAt(word, commentaryId)
