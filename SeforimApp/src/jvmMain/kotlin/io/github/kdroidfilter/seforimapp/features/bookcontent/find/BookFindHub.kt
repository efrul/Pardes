package io.github.kdroidfilter.seforimapp.features.bookcontent.find

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Pardes (request 55 of docs/DESKTOP_STAGE3_PLAN.md): "חיפוש בתוך ספר פתוח יפתח חלונית בצד, שמציגה את כל התוצאות
// מסודרות". The text keeps its find as Zayit's code holds it, and tells it here, where the results pane reads it.

/** Whether a book's find lists its results in a pane beside the text ([FindResultsPane]): one setting, every tab. */
object FindResultsSettings : SettingsGroup("find_results") {
    val shown by boolean(false)
}

/** A tab's find in its book [bookId]: its [query], whether by meaning ([smart]), its [matches] and the [current] one. */
@Immutable
data class FindListing(
    val bookId: Long,
    val query: String,
    val smart: Boolean,
    val matches: BookFindMatches,
    val current: BookFindMatches.Match?,
)

/** A line the find matched: its place in the book, its id, and how many times it matched. */
@Immutable
data class FoundLine(
    val lineIndex: Int,
    val lineId: Long,
    val count: Int,
)

/** The lines of these matches, in reading order, each once with its count. */
fun BookFindMatches.foundLines(): List<FoundLine> {
    var match = nextAfterLine(-1, forward = true) ?: return emptyList()
    val lines = ArrayList<FoundLine>()
    repeat(occurrences) {
        val last = lines.lastOrNull()
        if (last != null && last.lineIndex == match.lineIndex) {
            lines[lines.lastIndex] = last.copy(count = last.count + 1)
        } else {
            lines += FoundLine(match.lineIndex, match.lineId, 1)
        }
        match = next(match, forward = true) ?: return lines
    }
    return lines
}

/**
 * The tabs' finds in their books, which their texts tell ([publish]) and the results pane lists ([found]), and the
 * lines the pane asks a tab's text to go to ([goTo]), as a PDF book's reader and its contents pane talk through
 * PdfBookStore.
 */
@Inject
@SingleIn(AppScope::class)
class BookFindHub {
    private val _found = MutableStateFlow<Map<String, FindListing>>(emptyMap())

    /** Each tab's find, by its id, while the find bar shows its matches. */
    val found: StateFlow<Map<String, FindListing>> = _found.asStateFlow()

    private val _goTo = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = GO_TO_BUFFER)

    /** The lines the pane asks for: the tab's id and the line's place in its book. */
    val goTo: SharedFlow<Pair<String, Int>> = _goTo.asSharedFlow()

    /** Tells the find of the tab [tabId], or that it has none ([listing] null). */
    fun publish(
        tabId: String,
        listing: FindListing?,
    ) {
        _found.update { if (listing == null) it - tabId else it + (tabId to listing) }
    }

    /** Asks the text of the tab [tabId] to go to the first match of the line [lineIndex] of its book. */
    fun goTo(
        tabId: String,
        lineIndex: Int,
    ) {
        _goTo.tryEmit(tabId to lineIndex)
    }

    private companion object {
        const val GO_TO_BUFFER = 8
    }
}
