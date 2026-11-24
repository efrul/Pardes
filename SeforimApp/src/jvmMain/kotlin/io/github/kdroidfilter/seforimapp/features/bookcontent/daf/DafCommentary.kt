package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Pardes (request 82 of docs/DESKTOP_STAGE3_PLAN.md: "גם מרש"י או הפירוש המקביל אליו כאין רשי כמו כרשבם בבא בתרא או רן
// בנדרים למשל ותוס יוכלו לעבור לצורת הדף"; request 86: "פתיחה של צורת הדף מרשי תפתח את כל האפשרויות כמו גמרא, אין אפשרות
// היום להתמקד על הגמרא ככה"): a commentary printed on a tractate's daf, in Rashi's column or Tosafot's, opens that
// daf, the tractate's own, at its comment, selected there as a click on it selects it, every option of the daf at hand.

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

/**
 * The comment each tab's daf is to select once it shows it, as a click on it selects it: asked by a commentary's tab
 * as it opens its tractate's daf at the comment (request 86), taken once by the daf of that tractate.
 */
@Inject
@SingleIn(AppScope::class)
class DafFocusRequests {
    // By the tab's id: the tractate's book, and the comment
    private val pending = ConcurrentHashMap<String, Pair<Long, Long>>()

    /** The daf of [tractateId] in the tab [tabId] is to select [commentId] when it next shows its places. */
    internal fun request(
        tabId: String,
        tractateId: Long,
        commentId: Long,
    ) {
        pending[tabId] = tractateId to commentId
    }

    /** The comment the daf of [tractateId] in the tab [tabId] is to select, once; null when none is asked of it. */
    internal fun take(
        tabId: String,
        tractateId: Long,
    ): Long? = pending.remove(tabId)?.takeIf { it.first == tractateId }?.second
}

/**
 * What the daf's button does in the tab [tabId] of a commentary printed on a tractate's daf: opens the tractate at the
 * Gemara line the commentary's comment is on ([onOpen]; at the tractate's start without one), the comment to be selected
 * on the daf ([DafFocusRequests]), every option of the tractate's daf then at hand, as from the tractate itself.
 */
@Composable
internal fun rememberOpenTractateDaf(
    tabId: String,
    onOpen: (bookId: Long, lineId: Long?) -> Unit,
): (tractate: Book, commentId: Long?) -> Unit {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val currentOnOpen by rememberUpdatedState(onOpen)
    return remember(tabId, graph, scope) {
        { tractate, commentId ->
            scope.launch {
                val gemara = commentId?.let { runSuspendCatching { gemaraLineOf(graph.repository, tractate, it) }.getOrNull() }
                commentId?.let { graph.dafFocusRequests.request(tabId, tractate.id, it) }
                currentOnOpen(tractate.id, gemara)
            }
        }
    }
}

/**
 * The Gemara line of [tractate] that a commentary's line [lineId] is on: the one the library links it to, else its
 * amud's first, by the two books' tables of contents; null when neither is found (a line of no amud).
 */
internal suspend fun gemaraLineOf(
    repository: SeforimRepository,
    tractate: Book,
    lineId: Long,
): Long? {
    baseOf(repository, tractate.id, lineId) { true }?.let { return it.first }
    val amud = repository.getTocEntryForLine(lineId)?.text?.let(::amudOf) ?: return null
    return firstLines(repository.getTocEntriesForBook(tractate.id))[amud]
}
