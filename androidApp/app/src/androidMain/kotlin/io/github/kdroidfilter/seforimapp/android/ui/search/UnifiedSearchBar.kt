package io.github.kdroidfilter.seforimapp.android.ui.search

import android.animation.ValueAnimator
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.data.SearchScope
import io.github.kdroidfilter.seforimapp.android.search.searchKey
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalTabSession
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ResolvedReference
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimapp.icons.JournalBookmark
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Desktop 0.6.1 strings (composeResources/values/strings.xml).
private const val SEARCH_TEXT_ROW = "חיפוש במאגר: %1\$s"
private const val SEARCH_TEXT_ROW_IN_BOOK = "חיפוש בתוך %2\$s: %1\$s"
private const val SEARCH_TEXT_ROW_IN_AUTHOR = "חיפוש בספרי %2\$s: %1\$s"
private const val SEARCH_IN_BOOK_PLACEHOLDER = "מה לחפש בספר זה? פרק, סימן או כל מילה"
private const val ROW_ACTION_OPEN = "פתיחה"
private const val ROW_ACTION_SEARCH_INSIDE = "חיפוש בתוך"
private const val ROW_ACTION_SEARCH = "חיפוש"
private const val ROW_ACTION_SEARCH_BASE = "בספרי יסוד"
private const val ROW_ACTION_SEARCH_ALL = "בכל הספרים"
private const val AUTOCOMPLETE_NO_RESULTS = "לא נמצאו תוצאות"
private const val AUTOCOMPLETE_LOADING = "טוען…"
private const val AUTHOR_BOOKS_COUNT = "%1\$d ספרים"
private const val SEARCH_CLEAR = "ניקוי"
private const val SEARCH_ICON_DESCRIPTION = "חיפוש"
private const val REMOVE_SELECTED_BOOK = "הסר ספר שנבחר"
internal const val BREADCRUMB_SEPARATOR = " > "

private val REFERENCE_HINTS = listOf(
    "שו\"ע אורח חיים סימן רסח",
    "רמב\"ם הלכות שבת פרק כ",
    "משנה ברורה סימן רלה סעיף קטן ד",
    "ברכות דף ב עמוד א",
    "תהילים פרק כג פסוק ד",
)
private val TEXT_HINTS = listOf(
    "הכל בידי שמים חוץ מיראת שמים",
    "ואהבת לרעך כמוך",
    "שמע ישראל ה׳ אלקינו ה׳ אחד",
    "מי שטרח בערב שבת יאכל בשבת",
    "אין עוד מלבדו",
)

// The one bar takes references and texts alike: both hints, in turn
private val HINTS = REFERENCE_HINTS.zip(TEXT_HINTS).flatMap { (reference, text) -> listOf(reference, text) }

/** Desktop's placeholder grey (HomeView SearchBar, both themes). */
private val PlaceholderGrey = Color(0xFF9AA0A6)

/**
 * The room above the soft keyboard (under the status bar) below which the bar searches in [SearchSheet]: the title bar
 * (52 dp), the lifted bar (13 + 48 dp) and three rows of suggestions (3 × 50 dp). A 411 dp phone has ~550 dp over
 * Gboard, the Jelly Star ~120 dp.
 */
private val SHORT_SCREEN_ROOM = 280.dp

/** The search sheet's field: lower than the bar's 48 dp, the sheet's few rows want the room. */
private val SHEET_FIELD_HEIGHT = 40.dp

/**
 * What the bar's rows do (desktop SearchHomeNavigationEvent, carried out by TabsContent.navigate, and HomeSearchCallbacks).
 * Books, places, authors and links open in the tab the bar is in.
 */
@Stable
class SearchBarActions(
    /** A book, at a line when one is given (a reference, a TOC entry). */
    val openBook: (book: Book, lineId: Long?) -> Unit,
    val openAuthor: (AuthorHit) -> Unit,
    /** A text search in [scope] (the bar's book or TOC entry, else everywhere); [extended] for all the books. */
    val textSearch: (query: String, scope: SearchScope, extended: Boolean) -> Unit,
    /** A zayit:// link pasted into the bar. */
    val openDestination: (Destination) -> Unit,
)

/** A row of the book-stage list; see [catBookRows] for their order. */
private sealed interface CatBookRow {
    data class Jump(val reference: ResolvedReference) : CatBookRow

    data object TextSearch : CatBookRow

    data class Author(val hit: AuthorHit) : CatBookRow

    data class BookRow(val suggestion: BookSuggestionDto) : CatBookRow
}

/**
 * The book-stage rows in order: the references, then the books and authors named exactly as typed (`חולין` opens
 * Chullin on Enter; `שוע` puts the שולחן ערוך parts first), the text search, then the other authors and books.
 * A title being typed past its first word (`בבא ק`) is no text search: with no book named exactly,
 * the first book whose title it begins comes before the text search too (desktop 0.6.2 fix 51463d23).
 */
private fun catBookRows(
    query: String,
    jumps: List<ResolvedReference>,
    textSearch: Boolean,
    authors: List<AuthorHit>,
    books: List<BookSuggestionDto>,
): List<CatBookRow> {
    val key = query.searchKey()
    val (exactAuthors, otherAuthors) = authors.partition { author ->
        listOfNotNull(author.name, AuthorNames.display(author.name), author.alias).any { it.searchKey() == key }
    }
    val (namedBooks, otherBooks) = books.partition { it.exactAcronym || it.book.title.searchKey() == key }
    val typedTitle =
        otherBooks.firstOrNull()?.takeIf { book ->
            val title =
                book.book.title
                    .searchKey()
                    .replace(",", "")
            namedBooks.isEmpty() && ' ' in key && title.startsWith(key)
        }
    val exactBooks = namedBooks + listOfNotNull(typedTitle)
    return buildList {
        jumps.mapTo(this) { CatBookRow.Jump(it) }
        exactBooks.mapTo(this) { CatBookRow.BookRow(it) }
        exactAuthors.mapTo(this) { CatBookRow.Author(it) }
        if (textSearch) add(CatBookRow.TextSearch)
        otherAuthors.mapTo(this) { CatBookRow.Author(it) }
        otherBooks.filter { it !== typedTitle }.mapTo(this) { CatBookRow.BookRow(it) }
    }
}

/**
 * The one smart search bar (references, books, authors, texts) with its suggestions: the home page's, reused by the
 * search results page. Port of desktop 0.6.1 HomeView.kt UnifiedSearchBar + SearchBar (+ SuggestionsPanel,
 * TocSuggestionsPanel, SuggestionRow, RowActionChip, SelectedBookChip) and texteffects/TypewriterPlaceholder.kt,
 * with the 0.6.2 fix 51463d23 in [catBookRows] (a title being typed opens the book before the text search) and the
 * 0.6.5 commits 1723de06 and 9e3d19a8 (once an author is picked, the text row searches all their books: one action,
 * no base books to choose, Ctrl+Enter as Enter).
 *
 * Touch adaptation: a tap on a row is desktop's click (Enter); IME "Search" is Enter (it runs the highlighted row,
 * the first one by default); the text row always shows its "בספרי יסוד" / "בכל הספרים" buttons ("חיפוש" alone in an
 * author's books) and every book and author row its "חיפוש בתוך" (desktop Tab), the highlighted row all of its actions; key hints show only with a
 * hardware keyboard, where Up/Down/Tab/Ctrl+Enter/Esc/Backspace work as on desktop. The list is a popup under the
 * field (above it when the keyboard leaves no room), as tall as the space left: up to 320 dp on a phone, desktop's
 * 220 dp from [wide]. On a short phone with the soft keyboard up (the Jelly Star) the field and the list take the whole
 * screen above the keyboard instead ([SearchSheet]). [initialText] fills the field without suggesting (the results
 * page's query).
 */
@Composable
fun UnifiedSearchBar(
    vm: SearchBarViewModel,
    actions: SearchBarActions,
    wide: Boolean,
    modifier: Modifier = Modifier,
    initialText: String = "",
    autoFocus: Boolean = true,
    // The Home bar sits mid-page: lifted to the top so the soft keyboard and the suggestions fit under it
    liftOnFocus: Boolean = autoFocus,
    // Changes when the bar moves on its page (what is above it changed height): a lifted bar is lifted again
    liftKey: Any? = null,
    // A change empties the bar as a new one starts (the Home's, once a book opened in its place)
    resetKey: Int = 0,
    onTextChange: (String) -> Unit = {},
) {
    val ui by vm.uiState.collectAsState()
    val c = Zayit.colors
    val scope = rememberCoroutineScope()
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentActions by rememberUpdatedState(actions)

    var reference by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var toc by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var tocEditedSinceBook by rememberSaveable { mutableStateOf(false) }
    var seededQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    var resetDone by rememberSaveable { mutableStateOf(resetKey) }
    if (resetKey != resetDone) {
        SideEffect {
            resetDone = resetKey
            reference = TextFieldValue()
            toc = TextFieldValue()
            tocEditedSinceBook = false
            seededQuery = null
            vm.reset()
        }
    }

    val selectedBook = ui.selectedScopeBook
    val isTocMode = selectedBook != null
    val field = if (isTocMode) toc else reference

    // Edits as the user makes them: forwarded to the ViewModel (desktop's snapshotFlow on the field states)
    fun editReference(v: TextFieldValue, notify: Boolean = true) {
        val changed = v.text != reference.text
        reference = v
        if (changed) {
            currentOnTextChange(v.text)
            if (notify) vm.onReferenceQueryChanged(v.text)
        }
    }

    fun editToc(v: TextFieldValue, notify: Boolean = true) {
        val changed = v.text != toc.text
        toc = v
        if (changed) {
            // In a book, the typed text is also the search's query
            if (vm.uiState.value.selectedScopeBook != null) currentOnTextChange(v.text)
            tocEditedSinceBook = v.text.isNotBlank()
            if (notify) vm.onTocQueryChanged(v.text)
        }
    }

    fun textValue(text: String) = TextFieldValue(text, TextRange(text.length))

    // The results page shows its query in the field: the book's field once the bar is in a book (its search's scope),
    // else the main one. Seeded once per query, and again only when a restore puts the bar in its book
    val inBook = selectedBook != null && ui.selectedScopeToc == null
    LaunchedEffect(initialText, inBook) {
        if (initialText.isEmpty()) return@LaunchedEffect
        val restoredBook = inBook && !vm.uiState.value.bookPickedByUser
        if (seededQuery == initialText && !restoredBook) return@LaunchedEffect
        seededQuery = initialText
        if (inBook) {
            if (toc.text != initialText) editToc(textValue(initialText))
        } else if (reference.text != initialText) {
            editReference(textValue(initialText))
        }
    }

    // Back to the bar once a book is picked, to type in its TOC
    LaunchedEffect(selectedBook?.id) {
        if (selectedBook != null && vm.uiState.value.bookPickedByUser) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // A tab in the background, or the tab under an overlay (settings, tools), shows no popup: it is a window of its own
    val front = LocalTabSession.current?.isFront ?: true
    val appVm = LocalAppViewModel.current
    val overlayShown by remember(appVm) { appVm?.state?.map { it.overlays.isNotEmpty() }?.distinctUntilChanged() ?: flowOf(false) }
        .collectAsState(false)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val configuration = LocalConfiguration.current
    val hardKeyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

    // A picked TOC entry fills the field with its path: nothing to search then
    val textSearchEnabled = ui.selectedScopeToc == null
    // Once an author is picked, the text is searched in all their books
    val textSearchInAuthor = ui.selectedScopeAuthor?.name?.let(AuthorNames::display)
    val query = field.text.trim()
    val hasUserText = field.text.isNotBlank()
    val queryLength = field.text.length
    val rows = remember(query, ui.jumpSuggestions, textSearchEnabled, ui.authorSuggestions, ui.bookSuggestions, isTocMode) {
        if (isTocMode) emptyList() else catBookRows(query, ui.jumpSuggestions, textSearchEnabled && query.isNotEmpty(), ui.authorSuggestions, ui.bookSuggestions)
    }
    val totalCatBook = rows.size
    // Keyboard navigation operates on the exact list the TOC panel renders, so the highlighted row is the one opened
    val visibleToc = remember(ui.tocSuggestions, selectedBook) { if (isTocMode) tocSuggestionsForDisplay(selectedBook, ui.tocSuggestions) else emptyList() }
    val totalToc = visibleToc.size
    val textRow = textSearchEnabled && hasUserText
    val totalTocRows = totalToc + if (textRow) 1 else 0
    val showBookStage = ui.suggestionsVisible && totalCatBook > 0 && !isTocMode
    val showToc = (ui.tocSuggestionsVisible || textRow) && totalTocRows > 0 && isTocMode
    val showBookLoading = !isTocMode && ui.isReferenceLoading && hasUserText && queryLength >= 2
    val showTocLoading = isTocMode && ui.isTocLoading && hasUserText && queryLength >= 1
    val showBookEmpty = !isTocMode && ui.suggestionsVisible && totalCatBook == 0 && hasUserText && queryLength >= 2 && !showBookLoading
    val showTocEmpty = isTocMode && ui.tocSuggestionsVisible && totalToc == 0 && hasUserText && queryLength >= 1 && !showTocLoading

    var focusedIndex by remember { mutableIntStateOf(-1) }
    var popupVisible by remember { mutableStateOf(false) }
    // Bumped when the field gets the focus or a touch: the suggestions open (again, after a touch outside closed them)
    var openRequests by remember { mutableIntStateOf(0) }
    var handledOpenRequests by remember { mutableIntStateOf(0) }
    var fieldFocused by remember { mutableStateOf(false) }
    val lift = remember { BringIntoViewRequester() }
    val liftDensity = LocalDensity.current
    // From the field: past the bar's own padding and border, plus a gap under the title bar
    val liftMargin = with(liftDensity) { 28.dp.toPx() }
    val imeInsets = WindowInsets.ime
    // Whether a soft keyboard is really up: some devices (the emulator among them) report a QWERTY keyboard and still
    // show one, so the configuration can't tell
    val imeShown by remember(imeInsets, liftDensity) { derivedStateOf { imeInsets.getBottom(liftDensity) > 0 } }
    // [liftKey] too: on a cold start the Home's catalog row arrives after the lift and pushes the bar ~50 dp down. A
    // scroll leaves the key alone (the bar keeps its place on the page), so the lift's own scroll can't restart it
    LaunchedEffect(fieldFocused, imeShown, liftKey) {
        if (!fieldFocused || !liftOnFocus || !imeShown) return@LaunchedEffect
        // With edge-to-edge the window is not resized for the keyboard, so the bar would stay under it. A rect taller
        // than any viewport makes the page scroll the bar's top edge to the top of the screen.
        lift.bringIntoView(Rect(0f, -liftMargin, 1f, 100_000f))
    }
    // A short phone with the keyboard up: Gboard leaves the Jelly Star ~120 dp of its 427, where the title bar and the
    // bar leave the suggestions a few dp under the bar, or one cut row over the title bar. Below this room (what the
    // title bar, the lifted bar and three rows take) the field and its list take the screen above the keyboard
    val hostView = LocalView.current
    val statusInsets = WindowInsets.statusBars
    val shortScreen by remember(hostView, imeInsets, statusInsets, liftDensity) {
        derivedStateOf {
            val ime = imeInsets.getBottom(liftDensity)
            ime > 0 && hostView.rootView.height - ime - statusInsets.getTop(liftDensity) < with(liftDensity) { SHORT_SCREEN_ROOM.toPx() }
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current

    // Compose's controller hides the keyboard through the field's input session, the window's whatever holds it
    fun hideKeyboard() {
        keyboard?.hide()
        hostView.windowInsetsController?.hide(android.view.WindowInsets.Type.ime())
    }
    LaunchedEffect(
        ui.suggestionsVisible, ui.tocSuggestionsVisible, ui.bookSuggestions, ui.jumpSuggestions, ui.authorSuggestions, textRow,
        ui.tocSuggestions, isTocMode, showBookEmpty, showTocEmpty, showBookLoading, showTocLoading, openRequests,
    ) {
        val shouldOpen = showToc || showBookStage || showBookEmpty || showTocEmpty || showBookLoading || showTocLoading
        // A reopen request alone keeps the highlighted row; new suggestions start from the first
        val keepRow = openRequests != handledOpenRequests && shouldOpen && focusedIndex >= 0
        handledOpenRequests = openRequests
        popupVisible = shouldOpen
        if (!keepRow) focusedIndex = if (shouldOpen && (showToc || showBookStage)) 0 else -1
    }

    // The list closes as its bar goes out of sight (another tab in front, an overlay such as a tool over it), as a touch
    // outside it closes it: the Home shown again (Back from a tool opened by a widget, a results tab closed) shows its
    // bar as it was left, without a list the user did not ask for; a tap in the field or a letter typed brings it back
    LaunchedEffect(front, overlayShown) {
        if (!front || overlayShown) popupVisible = false
    }

    fun dismissPopup() {
        popupVisible = false
        vm.dismissSuggestions()
    }

    fun focusLater() {
        scope.launch {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // A search over the whole library covers the base books, or [extended] all of them; a pasted link opens instead
    fun handleTextSearch(extended: Boolean = false) {
        val q = (if (vm.uiState.value.selectedScopeBook != null) toc else reference).text.trim()
        if (q.isEmpty()) return
        val barScope = vm.barScope()
        vm.onGlobalExtendedChange(extended)
        dismissPopup()
        // The results page keeps its bar, focused: the search sheet would stay over the results, so the keyboard goes
        if (!wide && shortScreen) hideKeyboard()
        scope.launch {
            val link = vm.deepLinkTarget(q)
            if (link != null) currentActions.openDestination(link) else currentActions.textSearch(q, barScope, extended)
        }
    }

    fun openReference() {
        scope.launch { vm.resolvePickedReference()?.let { (book, line) -> currentActions.openBook(book, line) } }
    }

    fun pickBook(book: Book) {
        vm.onPickBook(book)
        editReference(TextFieldValue(), notify = false)
        // Not skipped, as desktop (its skip flag is reset before the change is seen): the book's TOC starts unfiltered
        editToc(TextFieldValue())
        tocEditedSinceBook = false
        dismissPopup()
        focusLater()
    }

    fun pickToc(entry: Pair<TocSuggestionDto, List<String>>) {
        vm.onPickToc(entry.first.toc)
        val display = entry.second.joinToString(BREADCRUMB_SEPARATOR)
        editToc(textValue(display), notify = false)
        tocEditedSinceBook = true
        dismissPopup()
    }

    // Opens a TOC entry as Enter does: picks it, then opens the book there
    fun openToc(entry: Pair<TocSuggestionDto, List<String>>) {
        pickToc(entry)
        scope.launch {
            withFrameNanos { }
            openReference()
        }
    }

    fun clearBook() {
        vm.onReferenceQueryChanged("")
        vm.onTocQueryChanged("")
        editReference(TextFieldValue(), notify = false)
        editToc(TextFieldValue(), notify = false)
        tocEditedSinceBook = false
    }

    // Runs a book-stage row: its main action ([open]: open, search) or its Tab one (search inside)
    fun runCatBookRow(row: CatBookRow, open: Boolean) {
        when (row) {
            is CatBookRow.Jump -> {
                vm.onOpened()
                dismissPopup()
                currentActions.openBook(row.reference.book, row.reference.lineId)
            }
            CatBookRow.TextSearch -> handleTextSearch()
            is CatBookRow.Author -> if (open) {
                vm.onOpened()
                dismissPopup()
                currentActions.openAuthor(row.hit)
            } else {
                vm.onPickAuthor(row.hit)
                editReference(TextFieldValue(), notify = false)
            }
            is CatBookRow.BookRow -> if (open) {
                vm.onOpened()
                dismissPopup()
                currentActions.openBook(row.suggestion.book, null)
            } else {
                pickBook(row.suggestion.book)
            }
        }
    }

    // Enter (and the soft keyboard's Search): runs the highlighted row. A list closed by a touch outside it (the
    // keyboard stays up) has none: Enter then searches, as on desktop once Esc closed it
    fun runEnter() {
        val row = if (popupVisible) focusedIndex else -1
        when {
            isTocMode && row in 0 until totalToc -> openToc(visibleToc[row])
            isTocMode && textRow && row == totalToc -> handleTextSearch()
            !isTocMode && row in 0 until totalCatBook -> runCatBookRow(rows[row], open = true)
            selectedBook != null -> {
                dismissPopup()
                openReference()
            }
            textSearchEnabled -> handleTextSearch()
        }
    }

    // Tab: picks the highlighted book or author to search inside it (a TOC entry: opens it)
    fun runTab() {
        when {
            isTocMode && focusedIndex in 0 until totalToc -> openToc(visibleToc[focusedIndex])
            !isTocMode && focusedIndex in 0 until totalCatBook -> runCatBookRow(rows[focusedIndex], open = false)
        }
    }

    // Only under a focused bar, as a phone's search field shows its suggestions: the Home takes the focus back from the
    // bar when it shows again (HomeScreen's focus target), and the suggestions arriving for a query typed just before
    // reopen [popupVisible]; a list under a grey, unfocused bar is no search in progress
    val showOverlay = front && !overlayShown && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && openRequests > 0 && popupVisible &&
        fieldFocused && (showToc || showBookStage || showBookEmpty || showTocEmpty || showBookLoading || showTocLoading)
    // Back closes the list as a touch outside it does, so a tap in the field brings it back: dropping the suggestions
    // (desktop's Esc, still the hardware Esc's) left a results tab's bar with none for its query until it was edited
    BackHandler(enabled = showOverlay) { popupVisible = false }

    var backspaceStartedEmpty by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    // Read where the bar is placed: [anchor] changes on every frame the page scrolls, this only when the bar leaves or
    // comes back, so a scroll does not recompose the bar
    val barOnScreen by remember { derivedStateOf { anchor?.let { it.width > 0f && it.height > 0f } == true } }
    // The bar's sides, which the lift's scroll leaves alone: the list's width and the search sheet's line
    val barSides by remember { derivedStateOf { anchor?.let { it.left to it.right } } }
    val sheetShown = !wide && shortScreen && fieldFocused && front && !overlayShown && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    // Under the sheet the real field (where the keyboard types) is told its window has no focus. Its caret's handle,
    // shown from a tap in the bar until a letter is typed, is a popup window of its own, which no window under it can
    // cover: on the Jelly Star it stood over the sheet's list, a gold half-circle just above Gboard (the bar's place
    // before the sheet), beside the sheet's own caret. A text field draws its caret, its handles, its magnifier and its
    // toolbar only in a focused window; its focus and its input connection don't depend on it, so the keyboard goes on
    // typing in it, and its caret and handle are back when the sheet goes. So too while another page covers the bar (a
    // tool's overlay, another tab in front): a handle left from a tap in the bar stood over the tools list
    val windowInfo = LocalWindowInfo.current
    val fieldCovered = rememberUpdatedState(sheetShown || !front || overlayShown)
    val fieldWindowInfo = remember(windowInfo) { CoveredFieldWindowInfo(windowInfo, fieldCovered) }
    val fieldHeight = if (wide) 40.dp else 48.dp
    val fontSize = if (wide) 14.sp else 16.sp
    val pill = RoundedCornerShape(50)
    val borderColor by animateColorAsState(if (fieldFocused) c.accent else c.panelBorder, tween(150), label = "barBorder")

    Box(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .clip(pill)
                .background(c.panel)
                .border(if (fieldFocused) 2.dp else 1.dp, borderColor, pill)
                .onGloballyPositioned { anchor = it.boundsInWindow() }
                .padding(horizontal = 14.dp),
        ) {
            val chipMax = maxWidth * 0.4f
            Row(Modifier.fillMaxWidth().height(fieldHeight), verticalAlignment = Alignment.CenterVertically) {
                // A sign that it's a search field, not a button: Enter and the rows search
                Icon(Icons.Outlined.Search, SEARCH_ICON_DESCRIPTION, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                if (selectedBook != null) {
                    SelectedChip(selectedBook.title, Modifier.widthIn(max = chipMax)) {
                        clearBook()
                        runCatching { focusRequester.requestFocus() }
                    }
                    Spacer(Modifier.width(8.dp))
                } else if (ui.selectedScopeAuthor != null) {
                    SelectedChip(AuthorNames.display(ui.selectedScopeAuthor!!.name), Modifier.widthIn(max = chipMax)) {
                        vm.onClearAuthor()
                        runCatching { focusRequester.requestFocus() }
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(2.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        val placeholderStyle = TextStyle(fontSize = fontSize, color = PlaceholderGrey, fontFamily = ZayitFonts.ui)
                        if (isTocMode) {
                            Text(SEARCH_IN_BOOK_PLACEHOLDER, style = placeholderStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            val windowFocused = LocalWindowInfo.current.isWindowFocused
                            // Each letter typed is a frame of the whole window: only while it can be seen (the bar
                            // scrolled into view, nothing over it) and no soft keyboard types in it; a hardware
                            // keyboard's focused, empty bar types its hints on, as desktop's, and a paused one shows
                            // its hint whole
                            val focusPause = fieldFocused && !hardKeyboard
                            androidx.compose.runtime.key(selectedBook?.id) {
                                TypewriterPlaceholder(
                                    hints = HINTS,
                                    textStyle = placeholderStyle,
                                    typingDelayMs = 120L,
                                    deletingDelayMs = 55L,
                                    holdDelayMs = 1600L,
                                    preTypePauseMs = 500L,
                                    postDeletePauseMs = 450L,
                                    punctuationExtraDelayMs = 180L,
                                    enabled = windowFocused && front && !overlayShown && !focusPause && barOnScreen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                                    showWhenPaused = focusPause,
                                )
                            }
                        }
                    }
                    CompositionLocalProvider(LocalWindowInfo provides fieldWindowInfo) {
                        BasicTextField(
                            value = field,
                            onValueChange = { if (isTocMode) editToc(it) else editReference(it) },
                            singleLine = true,
                            textStyle = TextStyle(color = c.text, fontSize = fontSize, fontFamily = ZayitFonts.ui),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runEnter() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .bringIntoViewRequester(lift)
                                .onFocusChanged {
                                    // The focus gone (the Home's own focus target took it, a tool opened): the list closes
                                    // as on a touch outside it, keeping the suggestions; the next focus reopens it
                                    if (fieldFocused && !it.isFocused) popupVisible = false
                                    fieldFocused = it.isFocused
                                    if (it.isFocused) openRequests++
                                }
                                // A touch in the field (to move the caret) counts as outside the popup: it reopens it
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        openRequests++
                                    }
                                }
                                .onPreviewKeyEvent { ev ->
                                    val enter = ev.key == Key.Enter || ev.key == Key.NumPadEnter
                                    when {
                                        ev.key == Key.Backspace && !isTocMode && ui.selectedScopeAuthor != null -> when (ev.type) {
                                            KeyEventType.KeyDown -> {
                                                backspaceStartedEmpty = field.text.isEmpty()
                                                false
                                            }
                                            KeyEventType.KeyUp -> {
                                                val clear = backspaceStartedEmpty && field.text.isEmpty()
                                                backspaceStartedEmpty = false
                                                if (clear) vm.onClearAuthor()
                                                clear
                                            }
                                            else -> false
                                        }
                                        ev.key == Key.Backspace && isTocMode -> when (ev.type) {
                                            KeyEventType.KeyDown -> {
                                                backspaceStartedEmpty = field.text.isEmpty()
                                                false
                                            }
                                            KeyEventType.KeyUp -> {
                                                val clear = backspaceStartedEmpty && field.text.isEmpty() && !tocEditedSinceBook
                                                backspaceStartedEmpty = false
                                                if (clear) clearBook()
                                                clear
                                            }
                                            else -> false
                                        }
                                        // Enter acts on release, as desktop; its press must not reach the field's IME action
                                        enter && ev.type == KeyEventType.KeyDown -> true
                                        enter && ev.isCtrlPressed && ev.type == KeyEventType.KeyUp -> {
                                            // An author's books are all searched already: Ctrl+Enter is Enter there
                                            if (textSearchEnabled) handleTextSearch(extended = textSearchInAuthor == null)
                                            true
                                        }
                                        enter && ev.type == KeyEventType.KeyUp -> {
                                            runEnter()
                                            true
                                        }
                                        // Arrows move through the open list as soon as they are pressed, repeating while held
                                        (ev.key == Key.DirectionDown || ev.key == Key.DirectionUp) && popupVisible -> {
                                            if (ev.type == KeyEventType.KeyDown) {
                                                val total = if (isTocMode) totalTocRows else totalCatBook
                                                val step = if (ev.key == Key.DirectionDown) 1 else -1
                                                if (total > 0) focusedIndex = (focusedIndex + step).coerceIn(0, total - 1)
                                            }
                                            true
                                        }
                                        ev.key == Key.Escape && ev.type == KeyEventType.KeyUp && popupVisible -> {
                                            dismissPopup()
                                            true
                                        }
                                        ev.key == Key.Tab && ev.type == KeyEventType.KeyDown && popupVisible -> true
                                        ev.key == Key.Tab && ev.type == KeyEventType.KeyUp && popupVisible -> {
                                            runTab()
                                            true
                                        }
                                        else -> false
                                    }
                                },
                        )
                    }
                }
                // Clears the typed text, keeping the picked book
                if (field.text.isNotEmpty()) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) {
                            if (isTocMode) editToc(TextFieldValue()) else editReference(TextFieldValue())
                            runCatching { focusRequester.requestFocus() }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, SEARCH_CLEAR, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Read only while a list or the sheet shows: a tab sliding in moves the sides on every frame
        val sides = if (showOverlay || sheetShown) barSides else null
        // A list narrower than 320 dp (a small phone's, and a tablet's results-page bar beside the logo) gives the titles
        // the room: see [SuggestionRow]'s [narrow]
        val narrowList = sides?.let { (left, right) -> with(LocalDensity.current) { (right - left).toDp() } < 320.dp } == true
        // The list, in the popup or in the search sheet ([dense]; [onDrag]: a drag on it)
        val suggestions: @Composable (Dp, Boolean, (() -> Unit)?) -> Unit = { maxHeight, dense, onDrag ->
            if (isTocMode) {
                SuggestionList(
                    count = totalTocRows,
                    focusedIndex = focusedIndex,
                    maxHeight = maxHeight,
                    emptyMessage = if (showTocEmpty) AUTOCOMPLETE_NO_RESULTS else null,
                    loading = showTocLoading && totalTocRows == 0,
                    dense = dense,
                    onDrag = onDrag,
                ) { index ->
                    if (index < totalToc) {
                        val entry = visibleToc[index]
                        val openAction = RowAction("↵", ROW_ACTION_OPEN) { openToc(entry) }
                        SuggestionRow(
                            parts = entry.second,
                            kind = SuggestionKind.PLACE,
                            highlighted = index == focusedIndex,
                            wide = wide,
                            keys = hardKeyboard,
                            actions = listOf(openAction),
                            alwaysShown = emptyList(),
                            onClick = openAction.onClick,
                            narrow = narrowList,
                            dense = dense,
                        )
                    } else {
                        val q = field.text.trim()
                        val searchAction = RowAction("↵", ROW_ACTION_SEARCH) { handleTextSearch() }
                        SuggestionRow(
                            parts = listOf(SEARCH_TEXT_ROW_IN_BOOK.replace("%2\$s", selectedBook.title).replace("%1\$s", q)),
                            kind = SuggestionKind.TEXT_SEARCH,
                            emphasis = q,
                            highlighted = index == focusedIndex,
                            wide = wide,
                            keys = hardKeyboard,
                            actions = listOf(searchAction),
                            alwaysShown = listOf(searchAction),
                            onClick = searchAction.onClick,
                            narrow = narrowList,
                            dense = dense,
                        )
                    }
                }
            } else {
                SuggestionList(
                    count = totalCatBook,
                    focusedIndex = focusedIndex,
                    maxHeight = maxHeight,
                    emptyMessage = if (showBookEmpty) AUTOCOMPLETE_NO_RESULTS else null,
                    loading = showBookLoading && totalCatBook == 0,
                    dense = dense,
                    onDrag = onDrag,
                ) { index ->
                    val row = rows[index]
                    val focused = index == focusedIndex
                    val open = RowAction("↵", ROW_ACTION_OPEN) { runCatBookRow(row, true) }
                    val searchInside = RowAction("Tab", ROW_ACTION_SEARCH_INSIDE) { runCatBookRow(row, false) }
                    when (row) {
                        is CatBookRow.Jump -> SuggestionRow(
                            parts = listOf(row.reference.book.title, row.reference.label),
                            kind = SuggestionKind.PLACE,
                            highlighted = focused,
                            wide = wide,
                            keys = hardKeyboard,
                            actions = listOf(open),
                            alwaysShown = emptyList(),
                            onClick = open.onClick,
                            narrow = narrowList,
                            dense = dense,
                        )
                        CatBookRow.TextSearch -> {
                            val q = field.text.trim()
                            // An author's books are all searched: no base books to choose
                            val actions = if (textSearchInAuthor != null) {
                                listOf(RowAction("↵", ROW_ACTION_SEARCH) { runCatBookRow(row, true) })
                            } else {
                                listOf(
                                    RowAction("↵", ROW_ACTION_SEARCH_BASE) { runCatBookRow(row, true) },
                                    RowAction("Ctrl+↵", ROW_ACTION_SEARCH_ALL) { handleTextSearch(extended = true) },
                                )
                            }
                            SuggestionRow(
                                parts = listOf(
                                    if (textSearchInAuthor != null) {
                                        SEARCH_TEXT_ROW_IN_AUTHOR.replace("%2\$s", textSearchInAuthor).replace("%1\$s", q)
                                    } else {
                                        SEARCH_TEXT_ROW.replace("%1\$s", q)
                                    },
                                ),
                                kind = SuggestionKind.TEXT_SEARCH,
                                emphasis = q,
                                highlighted = focused,
                                wide = wide,
                                keys = hardKeyboard,
                                actions = actions,
                                alwaysShown = actions,
                                onClick = actions.first().onClick,
                                narrow = narrowList,
                                dense = dense,
                            )
                        }
                        is CatBookRow.Author -> SuggestionRow(
                            parts = listOf(AuthorNames.display(row.hit.name) + row.hit.alias?.let { " ($it)" }.orEmpty()),
                            kind = SuggestionKind.AUTHOR,
                            highlighted = focused,
                            wide = wide,
                            keys = hardKeyboard,
                            detail = AUTHOR_BOOKS_COUNT.replace("%1\$d", row.hit.bookCount.toString()),
                            actions = listOf(open, searchInside),
                            alwaysShown = listOf(searchInside),
                            onClick = open.onClick,
                            narrow = narrowList,
                            dense = dense,
                        )
                        is CatBookRow.BookRow -> SuggestionRow(
                            parts = dedupAdjacent(row.suggestion.path),
                            kind = SuggestionKind.BOOK,
                            highlighted = focused,
                            wide = wide,
                            keys = hardKeyboard,
                            actions = listOf(open, searchInside),
                            alwaysShown = listOf(searchInside),
                            onClick = open.onClick,
                            narrow = narrowList,
                            dense = dense,
                        )
                    }
                }
            }
        }

        if (sheetShown) {
            SearchSheet(
                barSides = sides,
                field = {
                    SheetField(
                        value = field,
                        placeholder = if (isTocMode) SEARCH_IN_BOOK_PLACEHOLDER else HINTS.first(),
                        chip = selectedBook?.title ?: ui.selectedScopeAuthor?.let { AuthorNames.display(it.name) },
                        onClearChip = {
                            if (selectedBook != null) clearBook() else vm.onClearAuthor()
                            runCatching { focusRequester.requestFocus() }
                        },
                        // The real field's selection, read when it lands: the keyboard may have typed since
                        onSelect = { range -> if (isTocMode) editToc(toc.copy(selection = range)) else editReference(reference.copy(selection = range)) },
                        onClear = {
                            if (isTocMode) editToc(TextFieldValue()) else editReference(TextFieldValue())
                            runCatching { focusRequester.requestFocus() }
                        },
                    )
                },
            ) { maxHeight ->
                // A drag on the list puts the keyboard away: the sheet then gives way to the list under the bar, as
                // tall as the screen
                if (showOverlay) suggestions(maxHeight, true) { hideKeyboard() }
            }
        } else {
            // Only while the list shows: read always, each scroll frame would recompose the whole bar
            val a = if (showOverlay) anchor else null
            if (a != null) {
                SuggestionsPopup(anchor = a, wide = wide, onDismiss = { popupVisible = false }) { maxHeight -> suggestions(maxHeight, false, null) }
            }
        }
    }

    // Desktop focuses the Home bar at once; here only with a hardware keyboard (a soft one would cover the page). Again
    // when a Home TabHost kept parked behind a book comes back in its own tab (Back, "בית"), as a new one would; a tab
    // switch moves no focus
    val parked = LocalTabSession.current?.parked == true
    LaunchedEffect(parked) {
        if (parked) return@LaunchedEffect
        delay(200)
        if (autoFocus && hardKeyboard) runCatching { focusRequester.requestFocus() }
    }
}

/**
 * The suggestions in a popup window under the field, its width, or above the field when the keyboard leaves too
 * little room below (desktop: "prefer below, otherwise above"); [content] gets the height it may take.
 */
@Composable
private fun SuggestionsPopup(anchor: Rect, wide: Boolean, onDismiss: () -> Unit, content: @Composable (maxHeight: androidx.compose.ui.unit.Dp) -> Unit) {
    val density = LocalDensity.current
    val view = LocalView.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val statusTop = WindowInsets.statusBars.getTop(density)
    val windowHeight = view.rootView.height
    val gap = with(density) { 6.dp.roundToPx() }
    val cap = with(density) { (if (wide) 220.dp else 320.dp).roundToPx() }
    val below = windowHeight - imeBottom - anchor.bottom.toInt() - gap * 2
    val above = anchor.top.toInt() - statusTop - gap * 2
    val placeBelow = below >= min(cap, with(density) { 160.dp.roundToPx() }) || below >= above
    val maxPx = max(min(cap, if (placeBelow) below else above), with(density) { 48.dp.roundToPx() })
    val provider = remember(anchor, placeBelow) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = anchorBounds.left
                val y = if (placeBelow) anchorBounds.bottom + gap else anchorBounds.top - gap - popupContentSize.height
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false, dismissOnClickOutside = true)) {
        val widthDp = with(density) { anchor.width.toDp() }
        Box(Modifier.width(widthDp)) { content(with(density) { maxPx.toDp() }) }
    }
}

/**
 * The search sheet of a short phone with the keyboard up: everything between the status bar and the keyboard, over the
 * title bar and the page, as a search page: [field] at the top, lined up with the bar ([barSides], its left and right
 * in window pixels), and [list] under it with the height left. The real field stays under the sheet, focused, so the
 * keyboard goes on typing in it; the sheet is a window that takes no focus, as the suggestions popup.
 */
@Composable
private fun SearchSheet(barSides: Pair<Float, Float>?, field: @Composable () -> Unit, list: @Composable (maxHeight: Dp) -> Unit) {
    val density = LocalDensity.current
    val view = LocalView.current
    val c = Zayit.colors
    val top = WindowInsets.statusBars.getTop(density)
    val windowWidth = view.rootView.width
    val sheetHeight = (view.rootView.height - WindowInsets.ime.getBottom(density) - top).coerceAtLeast(0)
    // The bar's sides, or a phone page's margins while the bar is out of sight (a results page's folded header)
    val minBar = with(density) { 48.dp.toPx() }
    val margin = with(density) { 10.dp.toPx() }
    val (left, right) = barSides?.takeIf { (l, r) -> r - l >= minBar } ?: (margin to windowWidth - margin)
    val provider = remember(top) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset(0, top)
        }
    }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        with(density) {
            Column(
                Modifier
                    .size(windowWidth.toDp(), sheetHeight.toDp())
                    .background(c.panel)
                    .absolutePadding(left = left.toDp(), right = (windowWidth - right).coerceAtLeast(0f).toDp())
                    .padding(top = 6.dp),
            ) {
                field()
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) { list(maxHeight) }
            }
        }
    }
}

/**
 * [window] as the bar's field sees it: unfocused while [covered] (the search sheet over it). A legacy BasicTextField
 * reads the window's focus only to draw its caret, its selection and caret handles, its magnifier and its toolbar
 * (CoreTextField's showHandleAndMagnifier, foundation 1.10 to 1.12); its focus, its input connection and its key events
 * don't depend on it. Everything else is the window's.
 */
@OptIn(ExperimentalComposeUiApi::class)
private class CoveredFieldWindowInfo(private val window: WindowInfo, private val covered: State<Boolean>) : WindowInfo {
    override val isWindowFocused: Boolean get() = window.isWindowFocused && !covered.value
    override val keyboardModifiers: PointerKeyboardModifiers get() = window.keyboardModifiers
    override val containerSize: IntSize get() = window.containerSize
    override val containerDpSize: DpSize get() = window.containerDpSize
}

/**
 * The bar at the search sheet's top. The field itself is under the sheet, where the keyboard types, so this one draws
 * its text ([SheetText]: its caret where the field's is, a tap there moves it), its picked book or author and its ×.
 */
@Composable
private fun SheetField(
    value: TextFieldValue,
    placeholder: String,
    chip: String?,
    onClearChip: () -> Unit,
    onSelect: (TextRange) -> Unit,
    onClear: () -> Unit,
) {
    val text = value.text
    val c = Zayit.colors
    val pill = RoundedCornerShape(50)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(SHEET_FIELD_HEIGHT)
            .clip(pill)
            .background(c.panel)
            .border(2.dp, c.accent, pill)
            .padding(horizontal = 14.dp),
    ) {
        val chipMax = maxWidth * 0.4f
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, SEARCH_ICON_DESCRIPTION, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            if (chip != null) {
                SelectedChip(chip, Modifier.widthIn(max = chipMax), onClearChip)
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(2.dp))
            }
            if (text.isEmpty()) {
                // RTL: the caret first, on the right, where Hebrew starts
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Caret(text)
                    Text(placeholder, color = PlaceholderGrey, fontSize = 16.sp, fontFamily = ZayitFonts.ui, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            } else {
                // The pill's whole height: a tap a little above or below the line still places the caret
                SheetText(value, onSelect, Modifier.weight(1f).fillMaxHeight())
            }
            if (text.isNotEmpty()) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClear), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, SEARCH_CLEAR, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A caret's blink, shown again from each change of [key] (an edit, a move) on. */
@Composable
private fun rememberCaretBlink(key: Any): State<Boolean> {
    val on = remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        on.value = true
        while (true) {
            delay(500)
            on.value = !on.value
        }
    }
    return on
}

/** Where [SheetText] last laid out its text: kept from one layout to the next, as a field's scroll. */
private class SheetTextPlacement {
    var layout: TextLayoutResult? = null

    /** The text's left edge in the box, in pixels from the box's left (absolute, whatever the layout direction). */
    var left = Float.NaN
}

/**
 * [SheetField]'s text, laid out as the real field's: one line, scrolled only as far as keeps the caret (the selection's
 * end, where the keyboard types) in sight, so a caret moved by the keyboard (Gboard's space-bar swipe, its arrows)
 * shows where the next letter goes; a selection's background. A tap places the caret in the real field ([onSelect]),
 * which the sheet covers; a long press selects the word, for the keyboard's paste or cut to replace.
 */
@Composable
private fun SheetText(value: TextFieldValue, onSelect: (TextRange) -> Unit, modifier: Modifier) {
    val c = Zayit.colors
    val placement = remember { SheetTextPlacement() }
    val blink = rememberCaretBlink(value)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val caretWidth = with(LocalDensity.current) { 2.dp.toPx() }
    val selection = value.selection
    BasicText(
        text = value.text,
        style = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = ZayitFonts.ui),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { placement.layout = it },
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                fun offsetAt(x: Float): Int? {
                    val layout = placement.layout ?: return null
                    val left = placement.left.takeUnless { it.isNaN() } ?: return null
                    return layout.getOffsetForPosition(Offset(x - left, layout.size.height / 2f))
                }
                detectTapGestures(
                    onTap = { pos -> offsetAt(pos.x)?.let { currentOnSelect(TextRange(it)) } },
                    onLongPress = { pos ->
                        offsetAt(pos.x)?.let { offset ->
                            val word = placement.layout?.getWordBoundary(offset)
                            currentOnSelect(if (word == null || word.collapsed) TextRange(offset) else word)
                        }
                    },
                )
            }
            .layout { measurable, constraints ->
                // The line whole, however long: the box shows the part around the caret; centred in the box's height
                val text = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0))
                val width = if (constraints.hasBoundedWidth) constraints.maxWidth else text.width
                val height = text.height.coerceIn(constraints.minHeight, constraints.maxHeight)
                val pad = caretWidth
                val ltr = placement.layout?.let { it.layoutInput.text.isNotEmpty() && it.getParagraphDirection(0) == ResolvedTextDirection.Ltr } == true
                val left = if (text.width <= width - 2 * pad) {
                    // It fits: at its start, as the field aligns it (Hebrew on the right)
                    if (ltr) pad else width - pad - text.width
                } else {
                    val caret = placement.layout?.let { it.getCursorRect(selection.end.coerceIn(0, it.layoutInput.text.length)).left } ?: 0f
                    var l = placement.left.takeUnless { it.isNaN() } ?: if (ltr) pad else width - pad - text.width
                    if (caret + l < pad) l = pad - caret
                    if (caret + l > width - pad) l = width - pad - caret
                    l.coerceIn(width - pad - text.width, pad)
                }
                placement.left = left
                layout(width, height) { text.place(left.roundToInt(), (height - text.height) / 2) }
            }
            .drawWithContent {
                val layout = placement.layout
                if (layout != null && !selection.collapsed && selection.max <= layout.layoutInput.text.length) {
                    // The real field's selection colour (Material's: the accent at 40 %)
                    drawPath(layout.getPathForRange(selection.min, selection.max), c.accent.copy(alpha = 0.4f))
                }
                drawContent()
                // The placement keeps the caret a caret's width inside the box: drawn on the glyphs' edge, as the field's
                if (layout != null && selection.collapsed && blink.value) {
                    val rect = layout.getCursorRect(selection.end.coerceIn(0, layout.layoutInput.text.length))
                    drawLine(c.accent, Offset(rect.left, rect.top), Offset(rect.left, rect.bottom), strokeWidth = caretWidth)
                }
            },
    )
}

/** A text field's caret, blinking from each edit ([text]) on; the blink only redraws it. */
@Composable
private fun Caret(text: String) {
    val color = Zayit.colors.accent
    val on by rememberCaretBlink(text)
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .size(2.dp, 20.dp)
            .graphicsLayer { alpha = if (on) 1f else 0f }
            .background(color),
    )
}

/**
 * The panel: menu-styled, rows 6 dp apart, the highlighted one kept in view; its loading and empty states. [dense] (the
 * search sheet's): no frame, the sheet is one, and rows 2 dp apart; [onDrag] is called when a finger starts dragging
 * the list.
 */
@Composable
private fun SuggestionList(
    count: Int,
    focusedIndex: Int,
    maxHeight: Dp,
    emptyMessage: String?,
    loading: Boolean,
    dense: Boolean = false,
    onDrag: (() -> Unit)? = null,
    row: @Composable (Int) -> Unit,
) {
    val c = Zayit.colors
    val listState = rememberLazyListState()
    val currentOnDrag by rememberUpdatedState(onDrag)
    // The finger's drags only: the highlighted row is kept in view by scrollToItem, which must not count
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) currentOnDrag?.invoke() }
    }
    LaunchedEffect(focusedIndex, count) {
        if (focusedIndex >= 0 && count > 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index
            val lastVisible = visible.lastOrNull()?.index
            if (lastVisible != null && focusedIndex >= lastVisible) {
                listState.scrollToItem((focusedIndex + 1).coerceAtMost(count - 1))
            } else if (firstVisible != null && focusedIndex <= firstVisible) {
                listState.scrollToItem((focusedIndex - 1).coerceAtLeast(0))
            }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (dense) Modifier else Modifier.clip(shape).border(1.dp, c.panelBorder, shape).background(c.panel))
            .heightIn(max = maxHeight)
            .padding(if (dense) 0.dp else 6.dp),
    ) {
        when {
            loading -> Message(AUTOCOMPLETE_LOADING)
            count == 0 && emptyMessage != null -> Message(emptyMessage)
            else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 6.dp), modifier = Modifier.fillMaxWidth()) {
                items(count) { row(it) }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Zayit.colors.textDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

/** What a suggestion row leads to, shown by its icon. */
private enum class SuggestionKind { PLACE, TEXT_SEARCH, AUTHOR, BOOK }

/** One thing a suggestion row does: its keys (`↵`, `Tab`) and what it does. */
private class RowAction(val keys: String, val label: String, val onClick: () -> Unit)

@Composable
private fun SuggestionIcon(kind: SuggestionKind, size: androidx.compose.ui.unit.Dp) {
    val tint = Zayit.colors.textSecondary
    val m = Modifier.size(size)
    when (kind) {
        SuggestionKind.PLACE -> Image(rememberVectorPainter(JournalBookmark), null, m, colorFilter = ColorFilter.tint(tint))
        SuggestionKind.BOOK -> Image(rememberVectorPainter(bookOpenTabs(tint)), null, m, colorFilter = ColorFilter.tint(tint))
        SuggestionKind.TEXT_SEARCH -> Icon(Icons.Outlined.Search, null, m, tint = tint)
        SuggestionKind.AUTHOR -> Image(rememberVectorPainter(WritingHand), null, m, colorFilter = ColorFilter.tint(tint))
    }
}

/**
 * A suggestion: its icon, its parts joined by " > " (in grey) on one line that shows its end (the book) when too long,
 * the [detail] after it, then its action buttons: [actions] on the highlighted row (desktop shows them on the active
 * row), [alwaysShown] on the others (no hover on a touch screen).
 *
 * [narrow] (a list under 320 dp): a phone's (the Jelly Star's) highlighted row too shows only [alwaysShown], as
 * "פתיחה" and "חיפוש בתוך" side by side left its title no room at all; the highlight still says what a tap and Enter
 * run. A tablet's results-page list is as narrow, beside the logo, but keeps desktop's active-row buttons: there a
 * hardware keyboard reads its Enter on them. A row with two buttons even when not highlighted (the text search) puts
 * them under the text. [dense] (the search sheet, a few rows high): shorter rows, and no button for what a tap on the
 * row does already ([onClick]), so every row keeps one line.
 */
@Composable
private fun SuggestionRow(
    parts: List<String>,
    kind: SuggestionKind,
    highlighted: Boolean,
    wide: Boolean,
    keys: Boolean,
    actions: List<RowAction>,
    alwaysShown: List<RowAction>,
    onClick: () -> Unit,
    emphasis: String? = null,
    detail: String? = null,
    narrow: Boolean = false,
    dense: Boolean = false,
) {
    val c = Zayit.colors
    val background by animateColorAsState(if (highlighted) c.accent.copy(alpha = 0.12f) else Color.Transparent, tween(150), label = "rowBg")
    // A narrow list (the Jelly Star's) leaves a book's row room for its title alone: cut at the start, as the other rows,
    // every ברכות read "… ברכות" (בבלי, משנה, ירושלמי, תוספתא alike). There the title comes first and its categories follow
    // in grey, cut at their end, so the top one, which tells them apart, stays
    val titleFirst = narrow && !wide && kind == SuggestionKind.BOOK && parts.size > 1
    val label = remember(parts, emphasis, detail, c.textDisabled, titleFirst) {
        buildAnnotatedString {
            if (titleFirst) {
                append(parts.last().withBold(emphasis))
                withStyle(SpanStyle(color = c.textDisabled)) { append("  " + parts.dropLast(1).joinToString(BREADCRUMB_SEPARATOR)) }
                return@buildAnnotatedString
            }
            parts.forEachIndexed { i, part ->
                if (i > 0) withStyle(SpanStyle(color = c.textDisabled)) { append(BREADCRUMB_SEPARATOR) }
                append(part.withBold(emphasis))
            }
            if (detail != null) withStyle(SpanStyle(color = c.textDisabled, fontSize = 11.sp)) { append("  $detail") }
        }
    }
    val shown = when {
        parts.isEmpty() -> emptyList<RowAction>()
        highlighted && !(narrow && !wide) -> actions
        else -> alwaysShown
    }.let { buttons -> if (dense) buttons.filter { it.onClick !== onClick } else buttons }
    // A narrow list puts two buttons under the text, which would leave it no room beside them; only on a row that has
    // them unhighlighted too: one that grew a line as it was highlighted would make the list jump under the arrows
    val chipsBelow = narrow && shown.size >= 2 && alwaysShown.size >= 2
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (wide || dense) 34.dp else 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (dense) 2.dp else 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (wide) 26.dp else if (dense) 30.dp else 36.dp), verticalAlignment = Alignment.CenterVertically) {
            SuggestionIcon(kind, if (wide) 14.dp else 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = c.text,
                fontSize = if (wide) 13.sp else if (dense) 14.sp else 15.sp,
                maxLines = 1,
                softWrap = false,
                overflow = if (titleFirst) TextOverflow.Ellipsis else TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f),
            )
            if (shown.isNotEmpty() && !chipsBelow) {
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    shown.forEach { RowActionChip(it, wide || dense, keys) }
                }
            }
        }
        if (chipsBelow) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                shown.forEach { RowActionChip(it, wide || dense, keys) }
            }
        }
    }
}

/** A row action as a quiet button: its label, then (with a hardware keyboard) its key in grey; [wide]: desktop's size. */
@Composable
private fun RowActionChip(action: RowAction, wide: Boolean, keys: Boolean) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .heightIn(min = if (wide) 26.dp else 32.dp)
            .clip(shape)
            .background(c.text.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(action.label, fontSize = if (wide) 11.sp else 12.sp, color = c.text, maxLines = 1, softWrap = false)
        // Keys read left to right (Ctrl+↵), whatever the layout
        if (keys) Text(action.keys, fontSize = 10.sp, color = c.textSecondary, maxLines = 1, softWrap = false, style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

/** The picked book or author in the field, with its ×. */
@Composable
private fun SelectedChip(title: String, modifier: Modifier = Modifier, onClear: () -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(c.panel).border(1.dp, c.panelBorder, shape).padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = c.text, modifier = Modifier.weight(1f, fill = false))
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, REMOVE_SELECTED_BOOK, tint = c.textDisabled, modifier = Modifier.size(12.dp))
        }
    }
}

private fun String.withBold(part: String?): AnnotatedString {
    val start = part?.takeIf { it.isNotEmpty() }?.let { indexOf(it) } ?: -1
    if (start < 0) return AnnotatedString(this)
    return buildAnnotatedString {
        append(this@withBold)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + part!!.length)
    }
}

/**
 * The TOC suggestions to show (and let the keyboard select), each with its display breadcrumb. Entries whose
 * breadcrumb collapses to nothing once the book prefix is stripped are dropped. Desktop tocSuggestionsForDisplay.
 */
private fun tocSuggestionsForDisplay(selectedBook: Book?, tocSuggestions: List<TocSuggestionDto>): List<Pair<TocSuggestionDto, List<String>>> =
    tocSuggestions.mapNotNull { ts ->
        val parts = stripBookPrefixFromTocPath(selectedBook, dedupAdjacent(ts.path))
        if (parts.isNotEmpty()) ts to parts else null
    }

/** Strips the selected book's title if it redundantly opens a TOC path. Desktop stripBookPrefixFromTocPath. */
private fun stripBookPrefixFromTocPath(selectedBook: Book?, parts: List<String>): List<String> {
    if (selectedBook == null || parts.isEmpty()) return parts
    val bookTitle = selectedBook.title.trim()
    val first = parts.first().trim()
    if (first == bookTitle) return parts.drop(1)
    if (first.length > bookTitle.length && first.startsWith(bookTitle)) {
        val ch = first[bookTitle.length]
        if (ch == ',' || ch == ' ' || ch == ':' || ch == '-' || ch == '—') {
            val remainder = first.substring(bookTitle.length + 1).trim().trimStart(',', ' ', ':', '-', '—').trim()
            if (remainder.isNotEmpty()) return listOf(remainder) + parts.drop(1)
        }
    }
    return parts
}

/** Desktop texteffects CachedRectShape: a horizontal slice of the hint, revealed as it is typed. */
private class SliceShape(private val left: Float, private val right: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(left, 0f, right, size.height))
}

/**
 * Desktop texteffects/TypewriterPlaceholder.kt: types each hint, holds it, deletes it, then the next; the text is laid
 * out once per hint and revealed by clipping, so a frame only moves the clip.
 *
 * On a phone a frame a letter is the battery's: it goes through the hints once, then rests on the first one typed out,
 * and draws nothing more. Paused ([enabled] false: the field focused, the bar out of sight) a hint half typed shows
 * whole until it goes on; with the system's animations off it is shown whole, still.
 */
@Composable
private fun TypewriterPlaceholder(
    hints: List<String>,
    textStyle: TextStyle,
    typingDelayMs: Long,
    deletingDelayMs: Long,
    holdDelayMs: Long,
    preTypePauseMs: Long,
    postDeletePauseMs: Long,
    punctuationExtraDelayMs: Long,
    enabled: Boolean,
    showWhenPaused: Boolean = false,
) {
    var index by remember(hints) { mutableIntStateOf(0) }
    // The slices of the hint laid out last, with that hint
    var laidOut by remember { mutableStateOf<Pair<String, Array<Shape>>?>(null) }
    var current by remember { mutableStateOf<Shape?>(null) }
    // Hints still to be typed: all of them, then the first again, which stays
    var toType by remember(hints) { mutableIntStateOf(hints.size + 1) }
    // System animations off ("Remove animations"): the hint shown still, typed out
    val still = !ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(hints, enabled, still, showWhenPaused) {
        suspend fun shapesOf(full: String) = snapshotFlow { laidOut }.first { it?.first == full }!!.second
        if (!enabled || still || toType <= 0) {
            // Paused: the hint whole (a field focused before the first letter or between two hints included); blank
            // only while nothing was shown and the bar is not placed yet
            if (current != null || still || toType <= 0 || showWhenPaused) current = shapesOf(hints[index]).lastOrNull()
            return@LaunchedEffect
        }
        while (true) {
            val full = hints[index]
            delay(preTypePauseMs)
            val shapes = shapesOf(full)
            for (len in 1..full.length) {
                current = shapes.getOrNull(len - 1)
                delay(typingDelayMs + if (full[len - 1] in ".!?…,;:") punctuationExtraDelayMs else 0L)
            }
            if (--toType <= 0) return@LaunchedEffect
            delay(holdDelayMs)
            for (len in full.length - 1 downTo 0) {
                current = if (len > 0) shapes.getOrNull(len - 1) else null
                delay(deletingDelayMs)
            }
            delay(postDeletePauseMs)
            index = (index + 1) % hints.size
        }
    }
    BasicText(
        text = hints[index],
        onTextLayout = { result: TextLayoutResult ->
            val text = result.layoutInput.text.text
            val origin = result.getHorizontalPosition(0, usePrimaryDirection = true)
            laidOut = text to Array(text.length) { i ->
                val edge = result.getHorizontalPosition(i + 1, usePrimaryDirection = true)
                SliceShape(min(origin, edge), max(origin, edge))
            }
        },
        style = textStyle,
        maxLines = 1,
        modifier = Modifier.graphicsLayer {
            val s = current
            if (s == null) {
                alpha = 0f
            } else {
                alpha = 1f
                clip = true
                shape = s
            }
        },
    )
}

/** Desktop LogoBranch's place: this app's icon (פר / דס on olive green), beside the results page's bar. */
@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.pardes_app_icon), contentDescription = null, modifier = modifier.clip(RoundedCornerShape(22)))
}
