package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.SqlVariableLimit
import io.github.kdroidfilter.seforimapp.android.search.BookDetailsLoader
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocStructure
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Panes of the reader (desktop features/bookcontent/ui/panes/ReaderPanes.kt), with desktop's titles. */
enum class ReaderPane(val title: String) {
    Commentaries("מפרשים"),
    Targum("תרגומים"),
    Sources("מקורות"),
    Notes("הערות"),
    BookInfo("פרטי הספר"),
}

/** A request to bring a line into view; [top] puts it at the top (a TOC jump), else only when hidden. */
data class ScrollRequest(val index: Int, val lineId: Long, val top: Boolean, val stamp: Long = System.nanoTime())

/** A pending note (desktop NoteDraftAnchor): anchored, not saved until typed. */
data class NoteDraftAnchor(val lineId: Long, val startOffset: Int, val endOffset: Int, val quote: String)

/** Desktop AltTocState: the alternative structure under the TOC (פרשות, דפים…) and its headings in the text. */
data class AltTocUi(
    val structures: List<AltTocStructure> = emptyList(),
    val selectedStructureId: Long? = null,
    val entries: List<AltTocEntry> = emptyList(),
    val expanded: Set<Long> = emptySet(),
    val children: Map<Long, List<AltTocEntry>> = emptyMap(),
    val selectedEntryId: Long? = null,
    val lineHeadings: Map<Long, List<AltTocEntry>> = emptyMap(),
    val entriesById: Map<Long, AltTocEntry> = emptyMap(),
)

/**
 * The find-in-page session (desktop AppSettings find bar of the tab + BookContentView findResults): [results] are
 * those of [resultsQuery] (null while computing), [current] the match the arrows reached.
 *
 * [smart]: the bar holds the query of the search result that opened the book (desktop seeds the bar in smart mode):
 * it finds the lines the search finds, lit with the search's highlighting, one match each ([findSearchMatchesInBook]),
 * not the literal phrase. Typing another query is the literal find again. [resultsSmart] is the mode of [results].
 */
data class FindState(
    val open: Boolean = false,
    val query: String = "",
    val results: BookFindMatches? = null,
    val resultsQuery: String = "",
    val current: BookFindMatches.Match? = null,
    val focusRequest: Int = 0,
    val smart: Boolean = false,
    val resultsSmart: Boolean = false,
) {
    /** The query highlighted in the text and the panes: the typed one, from 2 letters, while the bar is open. */
    val activeQuery: String get() = if (open && query.trim().length >= 2) query else ""

    /** The smart find's lines once known for the query shown: only they are lit (null while they are computed). */
    val smartLines: BookFindMatches? get() = results?.takeIf { smart && resultsSmart && resultsQuery == query }
}

/** Which lines a pane shows the links of: the primary line (its section when it heads one), or a manual multi-selection. */
sealed interface LinkSelection {
    data class Single(val lineId: Long) : LinkSelection

    data class Multi(val lineIds: List<Long>) : LinkSelection
}

data class ReaderUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    /** Categories from the root down to the book's. */
    val categories: List<Category> = emptyList(),
    val totalLines: Int = 0,
    val selectedLineIds: Set<Long> = emptySet(),
    val primaryLineId: Long? = null,
    val primaryLineIndex: Int? = null,
    /** The selection is a TOC heading's section (thin bar for its lines, targum only by default). */
    val isTocEntrySelection: Boolean = false,
    /** Touch stand-in for Ctrl+click: taps add or remove lines. */
    val multiSelect: Boolean = false,
    val scrollRequest: ScrollRequest? = null,
    val tocRoots: List<TocEntry> = emptyList(),
    val tocExpanded: Set<Long> = emptySet(),
    val tocChildren: Map<Long, List<TocEntry>> = emptyMap(),
    val selectedTocId: Long? = null,
    /** TOC path of the primary line, root first (desktop toc.breadcrumbPath). */
    val breadcrumb: List<TocEntry> = emptyList(),
    /**
     * Where the text stands: the line the breadcrumb, the phone's subtitle, the tab title and the links name, and its
     * TOC path (null until known). The primary line while it is on screen, else the line the reader scrolled or
     * jumped to (see [ReaderViewModel.followPlace]): desktop's breadcrumb follows the selection only, and a fast-scroller
     * drop, which selects nothing, left the phone's subtitle on the chapter the reader had left.
     */
    val placeLineId: Long? = null,
    val placePath: List<TocEntry>? = null,
    /** The TOC search bar's query, null while it is hidden. */
    val tocSearch: String? = null,
    val tocSearchResult: TocFilterResult? = null,
    val altToc: AltTocUi = AltTocUi(),
    /** Line panes open on a tablet (desktop's independent toggles). */
    val panes: Set<ReaderPane> = emptySet(),
    /** The pane a phone shows under the text (one at a time). */
    val phonePane: ReaderPane? = null,
    val showToc: Boolean = true,
    val showTree: Boolean = false,
    val shnayimMikra: Boolean = false,
    /** Lines (by index) of a passage to read, tinted, with an end mark (desktop MarkedRange). */
    val markedRange: IntRange? = null,
    val diacritics: DiacriticsMode = DiacriticsMode.All,
    /** Commentators remembered for the book (desktop selectedCommentatorsByBook, sticky). */
    val stickyCommentators: Set<Long> = emptySet(),
    val commentatorsByLine: Map<Long, Set<Long>> = emptyMap(),
    val noteDraft: NoteDraftAnchor? = null,
    val find: FindState = FindState(),
    /** The search a result was opened from: its words stay highlighted (the engine's rule) while the bar shows it. */
    val searchQuery: String? = null,
) {
    val rootCategory: Category? get() = categories.firstOrNull()

    /** The selection the panes follow (desktop: manual multi-selection = all lines, TOC selection = its primary line). */
    val linkSelection: LinkSelection?
        get() {
            val primary = primaryLineId ?: return null
            return if (selectedLineIds.size > 1 && !isTocEntrySelection) LinkSelection.Multi(selectedLineIds.toList()) else LinkSelection.Single(primary)
        }

    /** The desktop TOC skips a lone root wrapping the book (its title): its children are the first level. */
    val tocDisplayRoots: List<TocEntry>
        get() = tocRoots.singleOrNull()?.takeIf { it.hasChildren }?.let { tocChildren[it.id] } ?: tocRoots

    /** The primary line's chapter: its TOC entry, unless it repeats the book title (the history visit's heading). */
    val heading: String get() = headingOf(breadcrumb)

    /** The TOC path of the place on screen ([placePath]), the primary line's until that is known. */
    val placeBreadcrumb: List<TocEntry> get() = placePath ?: breadcrumb

    /** The chapter on screen: the subtitle's and the tab title's. */
    val placeHeading: String get() = headingOf(placeBreadcrumb)

    private fun headingOf(path: List<TocEntry>): String = path.lastOrNull()?.text?.takeIf { it.isNotBlank() && it != book?.title }.orEmpty()
}

/**
 * The reader of one tab: desktop 0.6.7 BookContentViewModel with its use cases (ContentUseCase selection,
 * TocUseCase, AltTocUseCase, CommentariesUseCase selections, NotesUseCase visibility, CategoryDisplaySettingsUseCase),
 * and the book view's find-in-page (BookContentView). Lines are paged by index in chunks of [CHUNK].
 */
class ReaderViewModel(private val app: ZayitApplication, private val destination: Destination.Book) : ViewModel() {
    private val repository: SeforimRepository = app.libraryStore.repository()
    val bookId: Long = destination.bookId

    /** The title its tab was opened with, shown until the book is read. */
    val openedTitle: String = destination.title

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Lines by index, filled chunk by chunk as the list scrolls. */
    val lines = mutableStateMapOf<Int, Line>()
    private val loadedChunks = HashSet<Int>()
    private val indexById = HashMap<Long, Int>()

    // Chunks asked for and not yet read, read one at a time by [chunkLoader], the nearest to the latest ask first (all
    // on the main thread, between the reads)
    private val pendingChunks = HashSet<Int>()
    private var loadTarget = 0
    private val loadSignal = Channel<Unit>(Channel.CONFLATED)
    private var chunkLoader: Job? = null

    /** What each line links to, prefetched for the visible lines (desktop BookTabUi connections cache). */
    val connections = mutableStateMapOf<Long, LineConnectionsSnapshot>()
    private val lineConnections = LineConnections(repository) { _state.value.book?.title.orEmpty() }
    private val inFlight = HashSet<Long>()

    private val _charCounts = MutableStateFlow<IntArray?>(null)

    /** Per-line character counts, for the fast scroller's proportions (desktop bookCharCounts). */
    val charCounts: StateFlow<IntArray?> = _charCounts.asStateFlow()

    private val _details = MutableStateFlow<SearchEntity.BookEntity?>(null)

    /** The book-details pane's data (desktop bookDetailsFlow), loaded when first asked. */
    val details: StateFlow<SearchEntity.BookEntity?> = _details.asStateFlow()
    private var detailsRequested = false

    private var findJob: Job? = null
    private var tocSearchJob: Job? = null
    private var bookToc: Deferred<List<TocEntry>?>? = null
    private var noteDraftBaseline: Long? = null
    private val sectionCache = HashMap<Long, List<Long>>()

    // The place on screen ([followPlace]): a scroll settles on line after line of the same chapter, so the TOC entry of
    // each line and the path of each entry are read once (the selection fills them too)
    private var placeJob: Job? = null
    private val tocIdByLine = HashMap<Long, Long?>()
    private val tocPathById = HashMap<Long, List<TocEntry>>()

    /** The draft whose editor already took the cursor (see [claimNoteDraftFocus]); compared by identity. */
    private var focusedNoteDraft: NoteDraftAnchor? = null

    init {
        viewModelScope.launch {
            runCatching { load() }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "Book $bookId failed to load", e)
                _state.update { it.copy(loading = false) }
            }
        }
        viewModelScope.launch {
            app.userData.diacriticsChanges.collect { id -> if (id == _state.value.rootCategory?.id) refreshDiacritics() }
        }
    }

    private suspend fun load() {
        val book = repository.getBookCore(bookId) ?: run {
            _state.update { it.copy(loading = false) }
            return
        }
        val chain = ArrayList<Category>()
        var catId: Long? = book.categoryId
        while (catId != null && chain.size < 64) {
            val c = repository.getCategory(catId) ?: break
            chain.add(0, c)
            catId = c.parentId
        }
        val diacritics = chain.firstOrNull()?.let { DiacriticsMode.fromStored(app.userData.categoryDiacritics(it.id)) } ?: DiacriticsMode.All
        val sticky = app.prefs.selectedCommentators(bookId) ?: runCatching { repository.getDefaultCommentatorIdsForBook(bookId).toSet() }.getOrDefault(emptySet())
        val requested = destination.lineId?.let { repository.getLine(it) }?.takeIf { it.bookId == bookId }
        // Opened plainly: the first TOC leaf's first line is selected (desktop loadBookData), its section when it heads one.
        val initial = requested ?: firstTocLine(book)
        val marked = destination.endLineId?.let { end ->
            val last = repository.getLine(end)?.lineIndex
            val first = requested?.lineIndex
            if (first != null && last != null && last >= first) first..last else null
        }
        val seeded = destination.findQuery?.takeIf { q -> q.trim().length >= 2 }
        // The lines it opens on, read before the text shows: its first frame is the text there, not empty rows that
        // fill in and change height a moment later
        loadChunksNow(initial?.lineIndex ?: 0, book.totalLines)
        _state.update {
            it.copy(
                loading = false,
                book = book,
                categories = chain,
                totalLines = book.totalLines,
                diacritics = diacritics,
                stickyCommentators = sticky,
                shnayimMikra = destination.shnayimMikra && book.isChumash,
                markedRange = marked,
                searchQuery = seeded,
                // A search result's find bar is there with the first frame, so the passage put at the top below lands
                // under the bar rather than behind it (openFind below then counts its matches)
                find = if (seeded != null) it.find.copy(open = true, query = seeded, smart = true) else it.find,
                panes = if (destination.openNotes) setOf(ReaderPane.Notes) else emptySet(),
                phonePane = if (destination.openNotes) ReaderPane.Notes else null,
                // With the text's first frame: the selection's queries below would otherwise leave the book's start
                // on screen meanwhile, and the tab's anchor and history there
                scrollRequest = requested?.let { line -> ScrollRequest(line.lineIndex, line.id, top = true) },
            )
        }
        initial?.let { line ->
            ensureLoaded(line.lineIndex)
            selectLineInternal(line, toggle = false)
        } ?: ensureLoaded(0)
        seeded?.let { openFind(it, focus = false) }
        launchSafely("TOC of ${book.id}") { loadRootToc(book, initial?.id) }
        launchSafely("alt TOC of ${book.id}") { loadAltStructures(book) }
        viewModelScope.launch { _charCounts.value = runCatching { repository.getBookCharCounts(bookId) }.getOrNull() }
    }

    private suspend fun firstTocLine(book: Book): Line? = runCatching {
        val first = repository.getBookRootToc(book.id).firstOrNull()
        val target = first?.let { repository.getFirstLeafTocId(it.id) ?: it.id }
        val id = target?.let { repository.getLineIdsForTocEntry(it).firstOrNull() } ?: repository.getLineByIndex(book.id, 0)?.id
        id?.let { repository.getLine(it) }
    }.getOrNull()

    /** A coroutine of the reader whose DB failure is logged rather than crashing the app (desktop runSuspendCatching). */
    private fun launchSafely(what: String, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
        }
    }

    // ---- Lines ----

    /**
     * The lines around [index] (its chunk and the two beside it), read soon. A fast-scroller drag asks for a new place
     * every frame: the asks are conflated, one chunk read at a time, the latest place first, and those a drag only
     * passed on its way (far from the latest ask) are dropped; still on screen, their lines ask again.
     */
    fun ensureLoaded(index: Int) {
        val chunk = index / CHUNK
        val total = max(1, _state.value.totalLines)
        loadTarget = chunk
        for (c in chunk - 1..chunk + 1) {
            if (c < 0 || c * CHUNK >= total || c in loadedChunks) continue
            pendingChunks.add(c)
        }
        pendingChunks.removeAll { abs(it - chunk) > LOAD_WINDOW_CHUNKS }
        if (pendingChunks.isEmpty()) return
        if (chunkLoader == null) {
            chunkLoader = viewModelScope.launch {
                while (true) {
                    loadSignal.receive()
                    loadPendingChunks()
                }
            }
        }
        loadSignal.trySend(Unit)
    }

    private suspend fun loadPendingChunks() {
        while (true) {
            val target = loadTarget
            val c = pendingChunks.minByOrNull { abs(it - target) } ?: return
            pendingChunks.remove(c)
            if (!loadedChunks.add(c)) continue
            val start = c * CHUNK
            val loaded = try {
                repository.getLines(bookId, start, start + CHUNK - 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lines $start.. of $bookId failed to load", e)
                loadedChunks.remove(c)
                emptyList()
            }
            for (l in loaded) {
                lines[l.lineIndex] = l
                indexById[l.id] = l.lineIndex
            }
        }
    }

    /** [index]'s chunk read now, and the next one when [index] is in its second half (the screen goes on into it). */
    private suspend fun loadChunksNow(index: Int, totalLines: Int) {
        val chunk = index / CHUNK
        val chunks = if (index % CHUNK >= CHUNK / 2) listOf(chunk, chunk + 1) else listOf(chunk)
        for (c in chunks) {
            if (c * CHUNK >= max(1, totalLines) || !loadedChunks.add(c)) continue
            val start = c * CHUNK
            val loaded = try {
                repository.getLines(bookId, start, start + CHUNK - 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lines $start.. of $bookId failed to load", e)
                loadedChunks.remove(c)
                continue
            }
            for (l in loaded) {
                lines[l.lineIndex] = l
                indexById[l.id] = l.lineIndex
            }
        }
    }

    /**
     * Every line from [from] to [to] read now: a text selection may reach over lines a jump passed without loading
     * them (the fast scroller, Ctrl+End, the TOC), and its actions read them all.
     */
    suspend fun loadRange(from: Int, to: Int) {
        val last = min(to, _state.value.totalLines - 1)
        var c = max(0, from) / CHUNK
        while (c * CHUNK <= last) {
            val start = c * CHUNK
            if ((max(start, from)..min(start + CHUNK - 1, last)).any { lines[it] == null }) {
                val loaded = try {
                    repository.getLines(bookId, start, start + CHUNK - 1)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Lines $start.. of $bookId failed to load", e)
                    null
                }
                // Marked loaded only once read: a chunk that failed (or whose read was cancelled) is asked for again
                if (loaded != null) {
                    for (l in loaded) {
                        lines[l.lineIndex] = l
                        indexById[l.id] = l.lineIndex
                    }
                    loadedChunks.add(c)
                }
            }
            c++
        }
    }

    suspend fun lineIndexOf(lineId: Long): Int? = indexById[lineId] ?: repository.getLine(lineId)?.takeIf { it.bookId == bookId }?.lineIndex

    fun lineById(lineId: Long): Line? = indexById[lineId]?.let { lines[it] }

    /** The plain text of a line WITH diacritics: what highlights and notes count their offsets on (desktop). */
    fun plainText(line: Line): String = buildAnnotatedFromHtml(line.content, 16f).text

    // ---- Selection (desktop ContentUseCase.selectLine / loadAndSelectLine) ----

    /** A tap on a line: selects it, its section when it is a heading; in multi-select mode, adds or removes it. */
    fun onLineTap(line: Line) {
        viewModelScope.launch { selectLineInternal(line, toggle = _state.value.multiSelect) }
    }

    /** Ctrl+click (a keyboard) or the multi-select mode: adds or removes [line]. */
    fun toggleLine(line: Line) {
        viewModelScope.launch { selectLineInternal(line, toggle = true) }
    }

    fun setMultiSelect(on: Boolean) = _state.update { it.copy(multiSelect = on) }

    /** TOC, breadcrumb, alt TOC and parts: select the line and put it at the top. */
    fun loadAndSelectLine(lineId: Long, syncAltToc: Boolean = true) {
        launchSafely("line $lineId") {
            val line = repository.getLine(lineId)?.takeIf { it.bookId == bookId } ?: return@launchSafely
            ensureLoaded(line.lineIndex)
            selectLineInternal(line, toggle = false, syncAltToc = syncAltToc)
            _state.update { it.copy(scrollRequest = ScrollRequest(line.lineIndex, line.id, top = true)) }
        }
    }

    /** ↑ / ↓ keys: the previous or next line. */
    fun selectAdjacent(forward: Boolean) {
        launchSafely("adjacent line") {
            val index = _state.value.primaryLineIndex ?: return@launchSafely
            val line = (if (forward) repository.getNextLine(bookId, index) else repository.getPreviousLine(bookId, index)) ?: return@launchSafely
            ensureLoaded(line.lineIndex)
            selectLineInternal(line, toggle = false)
            _state.update { it.copy(scrollRequest = ScrollRequest(line.lineIndex, line.id, top = false)) }
        }
    }

    private suspend fun selectLineInternal(line: Line, toggle: Boolean, syncAltToc: Boolean = true) {
        indexById[line.id] = line.lineIndex
        val s = _state.value
        if (toggle) {
            val already = line.id in s.selectedLineIds
            val next = if (already) s.selectedLineIds - line.id else s.selectedLineIds + line.id
            val primary = if (already) next.firstOrNull() else line.id
            _state.update {
                it.copy(
                    selectedLineIds = next,
                    primaryLineId = primary,
                    primaryLineIndex = primary?.let { id -> indexById[id] },
                    isTocEntrySelection = false,
                    multiSelect = it.multiSelect && next.isNotEmpty(),
                )
            }
        } else {
            val heading = runCatching { repository.getHeadingTocEntryByLineId(line.id) }.getOrNull()
            val ids = if (heading != null) sectionWindow(heading.id, line.id).toSet() + line.id else setOf(line.id)
            _state.update { it.copy(selectedLineIds = ids, primaryLineId = line.id, primaryLineIndex = line.lineIndex, isTocEntrySelection = heading != null) }
        }
        val primary = _state.value.primaryLineId
        if (primary != null && primary != noteDraftBaseline && _state.value.noteDraft != null) _state.update { it.copy(noteDraft = null) }
        val tocLine = primary ?: line.id
        val tocId = runCatching { repository.getTocEntryIdForLine(tocLine) }.getOrNull()
        val path = tocId?.let { runCatching { repository.getAncestorPath(it) }.getOrNull() }.orEmpty()
        // The selection is usually the place next (tapped on screen, or brought there by the TOC, the keys, a link):
        // [followPlace] finds its chapter here without asking the DB again (only answers read: a failed read is null too)
        if (tocId != null && path.isNotEmpty()) {
            tocIdByLine[tocLine] = tocId
            tocPathById[tocId] = path
        }
        _state.update { it.copy(selectedTocId = tocId, breadcrumb = path) }
        if (tocId != null) expandTocPath(path)
        prefetchConnections(listOf(tocLine))
        if (syncAltToc) selectAltEntryForLine(tocLine)
    }

    /** Up to 128 lines of a section around [lineId] (desktop's sliding window). */
    private suspend fun sectionWindow(tocId: Long, lineId: Long): List<Long> {
        val all = sectionCache[tocId] ?: runCatching { repository.getLineIdsForTocEntry(tocId) }.getOrElse { emptyList() }.also { sectionCache[tocId] = it }
        if (all.size <= MAX_SECTION) return all
        val idx = all.indexOf(lineId).coerceAtLeast(0)
        val start = (idx - MAX_SECTION / 2).coerceAtLeast(0)
        return all.subList(start, min(all.size, start + MAX_SECTION))
    }

    fun consumeScrollRequest(request: ScrollRequest) = _state.update { if (it.scrollRequest == request) it.copy(scrollRequest = null) else it }

    /**
     * The fast scroller's bubble for the line at [index], loaded or not: its reference ("בראשית לז, ט"), else the last
     * two levels of its TOC path below the book's title (the bubble is one line), else the title.
     */
    suspend fun placeLabel(index: Int): String? {
        val title = _state.value.book?.title
        val line = lines[index] ?: runCatching { repository.getLineByIndex(bookId, index) }.getOrNull() ?: return title
        line.heRef?.takeIf { it.isNotBlank() }?.let { return it }
        val path = runCatching { repository.getTocEntryIdForLine(line.id)?.let { repository.getAncestorPath(it) } }.getOrNull().orEmpty()
        return path.map { it.text }.filter { it.isNotBlank() && it != title }.takeLast(2).joinToString(" › ").ifEmpty { title }
    }

    /**
     * The screen settled on [lineId] (the primary line while it shows, else the line the text scrolled or jumped to):
     * the breadcrumb, the phone's subtitle and the tab title follow it, whatever moved the text (a scroll, a fast-scroller
     * drop, a find match, a jump), with no selection change. The latest call wins.
     */
    fun followPlace(lineId: Long) {
        placeJob?.cancel()
        if (lineId == _state.value.placeLineId) return
        placeJob = launchSafely("place of $lineId") {
            val tocId = if (lineId in tocIdByLine) tocIdByLine[lineId] else repository.getTocEntryIdForLine(lineId).also { tocIdByLine[lineId] = it }
            val path = tocId?.let { id -> tocPathById[id] ?: repository.getAncestorPath(id).also { tocPathById[id] = it } }.orEmpty()
            _state.update { it.copy(placeLineId = lineId, placePath = path) }
        }
    }

    /** The fast scroller dropped on [index]: no selection, just the place. */
    fun scrollToIndex(index: Int) {
        val i = index.coerceIn(0, max(0, _state.value.totalLines - 1))
        ensureLoaded(i)
        _state.update { it.copy(scrollRequest = ScrollRequest(i, -1, top = true)) }
    }

    // ---- Connections and pane selections (desktop CommentariesUseCase) ----

    fun prefetchConnections(ids: List<Long>) {
        val missing = ids.filterNot { it in connections || it in inFlight }.distinct()
        if (missing.isEmpty()) return
        inFlight += missing
        viewModelScope.launch {
            runCatching { lineConnections.load(missing) }.onSuccess { connections.putAll(it) }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "connections of $missing", e)
            }
            inFlight -= missing.toSet()
        }
    }

    /** The connections the panes show for [selection]: the line's, or the union of a multi-selection's. */
    suspend fun connectionsOf(selection: LinkSelection): LineConnectionsSnapshot = try {
        when (selection) {
            is LinkSelection.Single -> connections[selection.lineId] ?: lineConnections.of(selection.lineId).also { connections[selection.lineId] = it }
            is LinkSelection.Multi -> lineConnections.union(selection.lineIds)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A library read that fails (a truncated or corrupt seforim.db): the pane shows none instead of closing the app
        Log.w(TAG, "Connections of $selection failed", e)
        LineConnectionsSnapshot()
    }

    /** The commentators shown for [lineId] among [available]: those picked for the line, else the book's sticky ones. */
    fun selectedCommentators(lineId: Long, available: Collection<Long>): Set<Long> {
        val s = _state.value
        val perLine = s.commentatorsByLine[lineId]
        if (!perLine.isNullOrEmpty()) return perLine
        val desired = available.filter { it in s.stickyCommentators }.toSet()
        return desired.ifEmpty { s.stickyCommentators }
    }

    /** The commentators list changed for [lineId]: the change also applies to the book's sticky set (desktop). */
    fun updateSelectedCommentators(lineId: Long, selected: Set<Long>, available: Collection<Long>) {
        _state.update { s ->
            val previous = selectedCommentators(lineId, available)
            val sticky = s.stickyCommentators + (selected - previous) - (previous - selected)
            app.prefs.setSelectedCommentators(bookId, sticky)
            s.copy(
                stickyCommentators = sticky,
                commentatorsByLine = s.commentatorsByLine.toMutableMap().apply { if (selected.isEmpty()) remove(lineId) else put(lineId, selected) },
            )
        }
    }

    /**
     * One page of the links of [selection] to [targetBookId] of [type] (desktop CommentsForLineOrTocPagingSource,
     * LineTargumPagingSource and their multi-line variants): a heading stands for its section.
     */
    suspend fun linksPage(selection: LinkSelection, type: ConnectionType, targetBookId: Long, offset: Int, limit: Int): List<CommentaryWithText> {
        val ids = when (selection) {
            is LinkSelection.Single -> sectionOrLine(selection.lineId, windowed = type == ConnectionType.COMMENTARY)
            is LinkSelection.Multi -> selection.lineIds
        }
        val distinct = type != ConnectionType.COMMENTARY && ids.size > 1
        return runCatching {
            // A whole section (a heading's targum and sources) can pass the 999 bound parameters of SQLite below 3.32
            SqlVariableLimit.linksPage(ids, type, distinct, offset, limit) { lineIds, o, l ->
                repository.getCommentariesForLineRange(
                    lineIds = lineIds,
                    activeCommentatorIds = setOf(targetBookId),
                    connectionTypes = setOf(type),
                    offset = o,
                    limit = l,
                    distinctByTargetLine = distinct,
                )
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun sectionOrLine(lineId: Long, windowed: Boolean): List<Long> {
        val heading = runCatching { repository.getHeadingTocEntryByLineId(lineId) }.getOrNull() ?: return listOf(lineId)
        val lines = (sectionCache[heading.id] ?: repository.getLineIdsForTocEntry(heading.id).also { sectionCache[heading.id] = it }).filter { it != lineId }
        if (!windowed || lines.size <= MAX_COMMENT_WINDOW) return lines.ifEmpty { listOf(lineId) }
        return lines.take(MAX_COMMENT_WINDOW)
    }

    /** The line of [commentatorId]'s book its first commentary on [selection] is (desktop resolveCommentaryTargetLine). */
    suspend fun commentaryTargetLine(selection: LinkSelection, commentatorId: Long): Long? = try {
        linksPage(selection, ConnectionType.COMMENTARY, commentatorId, 0, 1).firstOrNull()?.link?.targetLineId
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "commentary target of $commentatorId", e)
        null
    }

    // ---- Panes ----

    /** A rail or toolbar toggle (desktop ToggleCommentaries / ToggleTargum / ToggleSources / ToggleNotes). */
    fun togglePane(pane: ReaderPane) = _state.update { s ->
        if (pane in s.panes) {
            val rest = s.panes - pane
            s.copy(panes = rest, phonePane = if (s.phonePane == pane) null else s.phonePane)
        } else {
            s.copy(panes = s.panes + pane, phonePane = pane)
        }
    }

    /** Phone: shows [pane] under the text (its tab), or hides it when it is the one shown. */
    fun togglePhonePane(pane: ReaderPane) = _state.update { s ->
        if (s.phonePane == pane) s.copy(phonePane = null, panes = s.panes - pane) else s.copy(phonePane = pane, panes = if (pane == ReaderPane.BookInfo) s.panes else s.panes + pane)
    }

    fun showPane(pane: ReaderPane) = _state.update { s -> s.copy(panes = if (pane == ReaderPane.BookInfo) s.panes else s.panes + pane, phonePane = pane) }

    fun closePhonePane() = _state.update { s -> s.copy(phonePane = null, panes = s.phonePane?.let { s.panes - it } ?: s.panes) }

    fun closePane(pane: ReaderPane) = _state.update { s -> s.copy(panes = s.panes - pane, phonePane = if (s.phonePane == pane) null else s.phonePane) }

    fun toggleToc() = _state.update { it.copy(showToc = !it.showToc) }

    fun toggleTree() = _state.update { it.copy(showTree = !it.showTree) }

    fun setTree(visible: Boolean) = _state.update { it.copy(showTree = visible) }

    fun toggleShnayimMikra() = _state.update { it.copy(shnayimMikra = !it.shnayimMikra && it.book?.isChumash == true) }

    /** Loads the book-details pane's data once (desktop BookDetailsLoader). */
    fun requestDetails() {
        if (detailsRequested) return
        detailsRequested = true
        viewModelScope.launch { _details.value = withContext(Dispatchers.IO) { BookDetailsLoader(repository).load(bookId) } }
    }

    // ---- Notes ----

    /** "הוסף הערה" on a selection: a draft anchored to it, in the notes pane (desktop BookTextMenus add note). */
    fun startNoteDraft(draft: NoteDraftAnchor) {
        noteDraftBaseline = _state.value.primaryLineId
        _state.update { it.copy(noteDraft = draft) }
        showPane(ReaderPane.Notes)
    }

    fun consumeNoteDraft() = _state.update { it.copy(noteDraft = null) }

    /**
     * True the first time only for [draft] (this very "הוסף הערה"): its editor takes the cursor once. The pane's list
     * composes the editor again when it scrolls back into view (the phone's pane shrinks as the find bar takes the
     * keyboard), or the pane reopens; asking again then pulled the focus and the keyboard back from the find bar.
     */
    fun claimNoteDraftFocus(draft: NoteDraftAnchor): Boolean {
        if (focusedNoteDraft === draft) return false
        focusedNoteDraft = draft
        return true
    }

    // ---- Diacritics (desktop CategoryDisplaySettingsUseCase, per root category) ----

    fun cycleDiacritics() {
        val s = _state.value
        val root = s.rootCategory ?: return
        val next = s.diacritics.next(s.book?.hasTeamim == true)
        _state.update { it.copy(diacritics = next) }
        launchSafely("Diacritics of ${root.id}") { app.userData.setCategoryDiacritics(root.id, next.stored) }
    }

    private suspend fun refreshDiacritics() {
        val root = _state.value.rootCategory ?: return
        _state.update { it.copy(diacritics = DiacriticsMode.fromStored(app.userData.categoryDiacritics(root.id))) }
    }

    // ---- TOC (desktop TocUseCase) ----

    private suspend fun loadRootToc(book: Book, initialLineId: Long?) {
        val roots = runCatching { repository.getBookRootToc(book.id) }.getOrElse { emptyList() }
        val first = roots.firstOrNull()?.takeIf { it.hasChildren }
        val children = first?.let { mapOf(it.id to repository.getTocChildren(it.id)) }.orEmpty()
        _state.update { it.copy(tocRoots = roots, tocChildren = it.tocChildren + children, tocExpanded = it.tocExpanded + setOfNotNull(first?.id)) }
        initialLineId?.let { id -> runCatching { repository.getTocEntryIdForLine(id) }.getOrNull() }?.let { tocId ->
            expandTocPath(runCatching { repository.getAncestorPath(tocId) }.getOrElse { emptyList() })
        }
    }

    fun toggleTocEntry(entry: TocEntry) {
        val s = _state.value
        if (entry.id in s.tocExpanded) {
            // Collapsing takes the open descendants along (desktop getAllDescendantIds)
            val descendants = HashSet<Long>()
            fun collect(id: Long) {
                s.tocChildren[id]?.forEach { c -> if (descendants.add(c.id)) collect(c.id) }
            }
            collect(entry.id)
            _state.update { it.copy(tocExpanded = it.tocExpanded - entry.id - descendants) }
            return
        }
        viewModelScope.launch {
            val children = s.tocChildren[entry.id] ?: runCatching { repository.getTocChildren(entry.id) }.getOrElse { emptyList() }
            _state.update { it.copy(tocExpanded = it.tocExpanded + entry.id, tocChildren = it.tocChildren + (entry.id to children)) }
        }
    }

    private suspend fun expandTocPath(path: List<TocEntry>) {
        for (e in path) {
            if (e.hasChildren && e.id !in _state.value.tocChildren) {
                val kids = runCatching { repository.getTocChildren(e.id) }.getOrElse { emptyList() }
                _state.update { it.copy(tocChildren = it.tocChildren + (e.id to kids)) }
            }
            if (e.hasChildren) _state.update { it.copy(tocExpanded = it.tocExpanded + e.id) }
        }
    }

    /** A TOC leaf: its line, selected at the top. */
    fun openTocEntry(entry: TocEntry) {
        viewModelScope.launch {
            val lineId = entry.lineId ?: runCatching { repository.getLineIdsForTocEntry(entry.id).firstOrNull() }.getOrNull() ?: return@launch
            loadAndSelectLine(lineId)
        }
    }

    /** Children of a TOC entry, for the breadcrumb menus (desktop TocUseCase.tocChildren). */
    suspend fun tocChildrenOf(entryId: Long): List<TocEntry> =
        _state.value.tocChildren[entryId] ?: runCatching { repository.getTocChildren(entryId) }.getOrElse { emptyList() }

    fun toggleTocSearch() {
        tocSearchJob?.cancel()
        _state.update { if (it.tocSearch == null) it.copy(tocSearch = "", tocSearchResult = null) else it.copy(tocSearch = null, tocSearchResult = null) }
    }

    fun closeTocSearch() {
        tocSearchJob?.cancel()
        _state.update { it.copy(tocSearch = null, tocSearchResult = null) }
    }

    /** Filters the TOC and the alt TOC by title (desktop TocUseCase.search). */
    fun searchToc(query: String) {
        if (_state.value.tocSearch == null) return
        _state.update { it.copy(tocSearch = query) }
        tocSearchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(tocSearchResult = null) }
            return
        }
        tocSearchJob = viewModelScope.launch {
            val toc = (bookToc ?: viewModelScope.async { runCatching { repository.getBookToc(bookId) }.getOrNull() }.also { bookToc = it }).await()
            if (toc == null) {
                bookToc = null
                return@launch
            }
            val alt = _state.value.altToc.entriesById.values.toList()
            val result = withContext(Dispatchers.Default) { buildTocFilter(toc, query).withAlt(buildTocFilter(alt.map { it.toSearchTocEntry(bookId) }, query)) }
            _state.update { if (it.tocSearch == query) it.copy(tocSearchResult = result) else it }
        }
    }

    /** A TOC search match: closes the search and jumps to its line, a TOC or an alt TOC entry. */
    fun openTocSearchMatch(matchId: Long) {
        if (_state.value.tocSearch == null) return
        closeTocSearch()
        viewModelScope.launch {
            val altId = altTocIdOfSearchMatch(matchId)
            if (altId == null) {
                val entry = runCatching { repository.getTocEntry(matchId) }.getOrNull() ?: return@launch
                expandTocPath(runCatching { repository.getAncestorPath(entry.id) }.getOrElse { emptyList() })
                val lineId = entry.lineId ?: runCatching { repository.getLineIdsForTocEntry(entry.id).firstOrNull() }.getOrNull() ?: return@launch
                loadAndSelectLine(lineId)
            } else {
                val entry = _state.value.altToc.entriesById[altId] ?: return@launch
                expandAltPath(altId)
                selectAltEntry(entry)
            }
        }
    }

    // ---- Alt TOC (desktop AltTocUseCase) ----

    private suspend fun loadAltStructures(book: Book) {
        if (!book.hasAltStructures) return
        val structures = runCatching { repository.getAltTocStructuresForBook(book.id) }.getOrElse { emptyList() }
        if (structures.isEmpty()) return
        val preferred = structures.minByOrNull { priority(it.key) } ?: structures.first()
        _state.update { it.copy(altToc = AltTocUi(structures = structures, selectedStructureId = preferred.id)) }
        loadAltRoot(preferred.id)
        _state.value.primaryLineId?.let { selectAltEntryForLine(it) }
        _state.value.tocSearch?.takeIf { it.isNotBlank() }?.let { searchToc(it) }
    }

    private fun priority(key: String): Int = when (key.lowercase()) {
        "daf" -> 0
        "parasha" -> 1
        "chapters" -> 2
        "topic" -> 3
        "section" -> 4
        else -> 100
    }

    private suspend fun loadAltRoot(structureId: Long) {
        val (root, all) = withContext(Dispatchers.IO) { repository.getAltRootToc(structureId) to repository.getAltTocEntriesForStructure(structureId) }
        val headings = all.filter { it.lineId != null }.groupBy { it.lineId!! }
        val firstExpanded = root.firstOrNull()?.takeIf { it.hasChildren }?.id
        _state.update {
            it.copy(
                altToc = it.altToc.copy(
                    selectedStructureId = structureId,
                    entries = root,
                    expanded = setOfNotNull(firstExpanded),
                    children = emptyMap(),
                    selectedEntryId = null,
                    lineHeadings = headings,
                    entriesById = all.associateBy { e -> e.id },
                ),
            )
        }
        firstExpanded?.let { loadAltChildren(it) }
    }

    fun selectAltStructure(structure: AltTocStructure) {
        launchSafely("alt structure ${structure.id}") { loadAltRoot(structure.id) }
    }

    private suspend fun loadAltChildren(parentId: Long) {
        if (parentId in _state.value.altToc.children) return
        val kids = withContext(Dispatchers.IO) { runCatching { repository.getAltTocChildren(parentId) }.getOrElse { emptyList() } }
        _state.update { it.copy(altToc = it.altToc.copy(children = it.altToc.children + (parentId to kids), entriesById = it.altToc.entriesById + kids.associateBy { e -> e.id })) }
    }

    fun toggleAltEntry(entry: AltTocEntry) {
        val alt = _state.value.altToc
        if (entry.id in alt.expanded) {
            _state.update { it.copy(altToc = it.altToc.copy(expanded = it.altToc.expanded - entry.id)) }
        } else {
            _state.update { it.copy(altToc = it.altToc.copy(expanded = it.altToc.expanded + entry.id)) }
            if (entry.hasChildren) viewModelScope.launch { loadAltChildren(entry.id) }
        }
    }

    /** An alt TOC entry or inline alt heading: its line, selected at the top. */
    fun selectAltEntry(entry: AltTocEntry) {
        _state.update { it.copy(altToc = it.altToc.copy(selectedEntryId = entry.id)) }
        viewModelScope.launch {
            val lineId = entry.lineId ?: withContext(Dispatchers.IO) { runCatching { repository.getLineIdsForAltTocEntry(entry.id).firstOrNull() }.getOrNull() } ?: return@launch
            loadAndSelectLine(lineId, syncAltToc = false)
        }
    }

    private suspend fun selectAltEntryForLine(lineId: Long) {
        val alt = _state.value.altToc
        val structureId = alt.selectedStructureId ?: return
        val id = alt.lineHeadings[lineId]?.firstOrNull()?.id
            ?: withContext(Dispatchers.IO) { runCatching { repository.getAltTocEntryIdForLine(lineId, structureId) }.getOrNull() }
            ?: return
        _state.update { it.copy(altToc = it.altToc.copy(selectedEntryId = id)) }
        expandAltPath(id)
    }

    private suspend fun expandAltPath(entryId: Long) {
        val path = ArrayList<AltTocEntry>()
        var current: Long? = entryId
        var guard = 0
        while (current != null && guard++ < 512) {
            val e = _state.value.altToc.entriesById[current] ?: withContext(Dispatchers.IO) { runCatching { repository.getAltTocEntry(current) }.getOrNull() } ?: break
            path += e
            current = e.parentId
        }
        for (e in path.asReversed()) {
            if (e.hasChildren) loadAltChildren(e.id)
            if (e.id != entryId || e.hasChildren) _state.update { it.copy(altToc = it.altToc.copy(expanded = it.altToc.expanded + e.id)) }
        }
    }

    // ---- Find in page (desktop BookContentView findResults / navigateToMatch) ----

    /**
     * Opens the find bar, with [query] when given ("חפש בעמוד" on a selection, a search result). [focus] puts the
     * cursor in it; a search result's seeded bar does not, or the phone's keyboard would cover the passage opened.
     */
    fun openFind(query: String? = null, focus: Boolean = true) {
        _state.update { it.copy(find = it.find.copy(open = true, query = query ?: it.find.query, focusRequest = it.find.focusRequest + if (focus) 1 else 0)) }
        recomputeFind()
    }

    fun closeFind() {
        findJob?.cancel()
        _state.update { it.copy(find = it.find.copy(open = false, current = null)) }
    }

    fun setFindQuery(query: String) {
        if (query == _state.value.find.query) return
        _state.update { it.copy(find = it.find.copy(query = query, current = null)) }
        recomputeFind()
    }

    private fun recomputeFind() {
        val searchQuery = _state.value.searchQuery
        // Smart while the bar holds the search's query as it came (desktop: smart mode set with the seeded query)
        _state.update { it.copy(find = it.find.copy(smart = searchQuery != null && it.find.query == searchQuery)) }
        val f = _state.value.find
        val query = f.query
        val smart = f.smart
        findJob?.cancel()
        if (!f.open || query.trim().length < 2) {
            _state.update { it.copy(find = it.find.copy(results = null, resultsQuery = "")) }
            return
        }
        // Results of the other mode don't answer this one, nor narrow it
        val previous = f.results?.takeIf { f.resultsQuery.isNotEmpty() && f.resultsSmart == smart }
        if (previous != null && (f.resultsQuery == query || !smart && normalizeQueryForHebrew(f.resultsQuery) == normalizeQueryForHebrew(query))) {
            _state.update { it.copy(find = it.find.copy(resultsQuery = query)) }
            return
        }
        val narrowing = previous?.takeIf { !smart && queryNarrows(query, f.resultsQuery) }
        _state.update { it.copy(find = it.find.copy(results = null)) }
        findJob = viewModelScope.launch {
            if (!smart) delay(FIND_DEBOUNCE_MS) // let the typing settle before querying the index
            val engine = runCatching { app.search.engine() }.getOrNull()
            val matches = runCatching {
                if (smart) {
                    engine?.let { findSearchMatchesInBook(it, repository::getLinesByIds, bookId, query) } ?: BookFindMatches.Empty
                } else {
                    findInBook(engine, repository::getLinesByIds, { a, b -> repository.getLines(bookId, a, b) }, bookId, query, narrowing)
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "find in book", e)
                BookFindMatches.Empty
            }
            _state.update {
                if (it.find.query == query && it.find.smart == smart) it.copy(find = it.find.copy(results = matches, resultsQuery = query, resultsSmart = smart)) else it
            }
        }
    }

    /**
     * ▲ / ▼ / Enter: the next match from the current one, else from the line at the top of the screen
     * ([topLineIndex]); the screen scrolls it into view. Waits for the results of the query as typed.
     */
    fun navigateFind(forward: Boolean, topLineIndex: Int) {
        viewModelScope.launch {
            var f = _state.value.find
            var waited = 0
            while ((f.results == null || f.resultsQuery != f.query) && f.open && waited < FIND_WAIT_MS) {
                delay(50)
                waited += 50
                f = _state.value.find
            }
            val matches = f.results?.takeIf { f.resultsQuery == f.query } ?: return@launch
            val match = f.current?.takeIf { it in matches }?.let { matches.next(it, forward) }
                ?: matches.nextAfterLine(if (forward) topLineIndex - 1 else topLineIndex, forward)
                ?: return@launch
            ensureLoaded(match.lineIndex)
            _state.update { it.copy(find = it.find.copy(current = match), scrollRequest = ScrollRequest(match.lineIndex, match.lineId, top = false)) }
        }
    }

    /** The search's words in [text] (the engine's rule, as the results highlight them), off the main thread. */
    suspend fun searchRanges(text: String, query: String): List<IntRange> = withContext(Dispatchers.IO) {
        runCatching { app.search.engine()?.highlightRanges(text, query) }.getOrNull().orEmpty()
    }

    /** Lines read from the DB, for selections spanning lines the list did not compose (copy with source, report). */
    suspend fun linesByIds(ids: Collection<Long>): List<Line> =
        runCatching { SqlVariableLimit.readInChunks(ids) { repository.getLinesByIds(it) } }.getOrElse { emptyList() }

    companion object {
        const val CHUNK = 60

        /** Chunks asked for further than this from the latest ask are dropped before they are read. */
        private const val LOAD_WINDOW_CHUNKS = 2
        private const val TAG = "Zayit"
        private const val MAX_SECTION = 128
        private const val MAX_COMMENT_WINDOW = 256
        private const val FIND_DEBOUNCE_MS = 150L
        private const val FIND_WAIT_MS = 30_000

        fun factory(app: ZayitApplication, destination: Destination.Book) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(app, destination) as T
        }
    }
}

private val CHUMASH_TITLES = setOf("בראשית", "שמות", "ויקרא", "במדבר", "דברים")

/** The five books of the Chumash: the only ones shnayim mikra reads (desktop ShnayimMikraTargum.isChumash). */
val Book.isChumash: Boolean get() = title in CHUMASH_TITLES
