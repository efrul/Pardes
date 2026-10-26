package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.data.Dictionary
import io.github.kdroidfilter.seforimapp.android.data.DictionaryEntry
import io.github.kdroidfilter.seforimapp.android.data.DictionaryIndex
import io.github.kdroidfilter.seforimapp.android.data.lookup
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of the widget part of desktop 0.6.10 features/home/widgets/library/DictionaryWidget.kt: the library's
// dictionaries in one card, a word looked up in one of them or in all, its entry read in the card, and opened in its
// book from there. The tool page shows the same card full size.

private const val HOME_DICTIONARY_ALL = "כל המילונים"
private const val HOME_DICTIONARY_NOT_FOUND = "המילה לא נמצאה"
private const val HOME_DICTIONARY_LOADING = "טוען את המילונים…"
private const val HOME_DICTIONARY_RANDOM = "מילה אקראית"
private const val HOME_DICTIONARY_BACK = "חזרה לחיפוש"
private const val HOME_DICTIONARY_FAILED = "חלק מהמילונים לא נטענו"
private const val HOME_DICTIONARY_RETRY = "לנסות שוב"
private const val HOME_DICTIONARY_OPTIONS_TITLE = "המילונים לחיפוש"
private const val HOME_DICTIONARY_NONE = "אין בספרייה את %1\$s"

internal object DictionarySettings : WidgetSettings(DictionaryWidget.id) {
    /** The dictionaries searched, ticked in the options page. */
    val shown by enumSet(Dictionary.entries.toSet())

    /** The one of them its chip picked; null: all of them. */
    val picked by enumOrNull<Dictionary>()
}

/** The dictionaries searched, in their order, and the one of them its chip [picked] (null: all of them). */
private data class DictionaryOptions(
    val shown: List<Dictionary>,
    val picked: Dictionary?,
)

@Composable
private fun dictionaryOptions(state: HomeWidgetsState): DictionaryOptions {
    val ticked = state[DictionarySettings.shown]
    // None ticked can't be: every one, as at first
    val shown = Dictionary.entries.filter { it in ticked }.ifEmpty { Dictionary.entries }
    return DictionaryOptions(shown, state[DictionarySettings.picked]?.takeIf { it in shown })
}

/** A word typed, its entries in the dictionary picked (or in all) below; one tapped reads in the card. */
internal object DictionaryWidget : HomeWidget {
    override val id = "dictionary"
    override val title = "מילון"
    override val defaultSpan = CellSpan(6, 5)
    override val minSpan = CellSpan(4, 4)
    override val maxSpan = CellSpan(10, 10)
    override val settings get() = DictionarySettings
    override val tool = ToolKind.Dictionary

    @Composable
    override fun menuItems(state: HomeWidgetsState) = listOf(WidgetMenuItem(HOME_WIDGETS_OPTIONS) { state.optionsOpen = this })

    @Composable
    override fun Options(state: HomeWidgetsState) = DictionaryOptionsPage(state)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = DictionaryCard(state, modifier)
}

/** The dictionary card; [readNow] reads the dictionaries at once (the tool page) rather than on first use. */
@Composable
internal fun DictionaryCard(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    readNow: Boolean = false,
) {
    val c = Zayit.colors
    val app = state.app
    val options = dictionaryOptions(state)
    // null: all those ticked; one the library no longer has, all of them too rather than none
    val picked = options.picked?.takeUnless { DictionaryIndex.isMissing(it) }
    val needed = picked?.let(::listOf) ?: options.shown
    // The one dictionary searched, picked or the only one ticked: its name is in the field, not on each word
    val searched = picked ?: options.shown.singleOrNull()
    // Read on first use (the field focused, a dictionary or a random word asked for), not as soon as the Home shows
    var wanted by remember { mutableStateOf(readNow) }
    var attempt by remember { mutableIntStateOf(0) }
    val read by produceState(DictionaryIndex.cached(needed), needed, wanted, attempt) {
        // Another choice's map would show this one's dictionaries as failed while they're read
        value = DictionaryIndex.cached(needed)
        if (wanted || value != null) {
            // The library folder and the database's stamp are file system calls: not on the UI thread
            value = withContext(Dispatchers.IO) { DictionaryIndex.read(app.libraryStore.repository(), app.cacheDir, app.libraryStore.databaseFile, needed) }
        }
    }
    val failed = needed.filter { read != null && it !in read.orEmpty() }
    val pool = remember(read, needed) { needed.flatMap { read?.get(it).orEmpty() } }
    var query by remember { mutableStateOf("") }
    val results = remember(pool, query) { lookup(pool, query) }
    var opened by remember { mutableStateOf<DictionaryEntry?>(null) }
    val open = rememberOpenBookAt(state)
    // Android's Back leaves an entry for the search, as its arrow does
    BackHandler(enabled = opened != null) { opened = null }

    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opened?.let { entry ->
                EntryPage(state, entry, onBack = { opened = null }) { open(entry.bookId, entry.lineId) }
                return@Column
            }
            WidgetTitle("מילון") {
                // Its options at hand, as in its menu
                TitleIconButton(Icons.Outlined.Settings, HOME_WIDGETS_OPTIONS) { state.optionsOpen = DictionaryWidget }
            }
            // Chips only to choose between two dictionaries at least
            val available = options.shown.filterNot { DictionaryIndex.isMissing(it) }
            if (available.size > 1) {
                DictionaryChips(available, picked) {
                    wanted = true
                    state[DictionarySettings.picked] = it
                }
            }
            SearchField(
                query = query,
                hint = "חיפוש ב${searched?.label ?: HOME_DICTIONARY_ALL}",
                onChange = { query = it },
                // Enter reads the first entry found
                onSubmit = { results.firstOrNull()?.let { opened = it } },
                onFocus = { wanted = true },
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    read == null && !wanted -> AskRandomWord { wanted = true }
                    read == null -> Hint(HOME_DICTIONARY_LOADING)
                    failed.isNotEmpty() && pool.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FailedNotice { attempt++ } }
                    // Read, and none of them is in the library: said, rather than an empty card (a random word has none to draw)
                    pool.isEmpty() -> Hint(HOME_DICTIONARY_NONE.replace("%1\$s", needed.joinToString(", ") { it.bookTitle }))
                    query.isBlank() -> RandomWord(pool, searched == null) { opened = it }
                    results.isEmpty() -> Hint(HOME_DICTIONARY_NOT_FOUND)
                    else ->
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(results, key = { it.lineId }) { entry -> EntryRow(entry, showDictionary = searched == null) { opened = entry } }
                        }
                }
            }
            // The ones read still answer; the others are named lost, to be tried again
            if (failed.isNotEmpty() && pool.isNotEmpty()) FailedNotice { attempt++ }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    hint: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocus: () -> Unit,
) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape).background(c.panel).border(1.dp, c.panelBorder, shape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(hint, color = c.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                query,
                onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "נקה", tint = c.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A box for each dictionary, with its book; each tick adds it to the search or takes it out at once, the last one kept. */
@Composable
private fun DictionaryOptionsPage(state: HomeWidgetsState) {
    val c = Zayit.colors
    val options = dictionaryOptions(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(HOME_DICTIONARY_OPTIONS_TITLE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        Dictionary.entries.forEach { dictionary ->
            val ticked = dictionary in options.shown
            // The search needs one dictionary at least
            OptionCheckRow(dictionary.label, dictionary.bookTitle, ticked, enabled = !ticked || options.shown.size > 1) { on ->
                state[DictionarySettings.shown] = (if (on) options.shown + dictionary else options.shown - dictionary).toSet()
            }
        }
    }
}

/** "All" and each dictionary the library has, the one picked lit; a row that scrolls when the card is narrow. */
@Composable
private fun DictionaryChips(
    available: List<Dictionary>,
    picked: Dictionary?,
    onPick: (Dictionary?) -> Unit,
) {
    val c = Zayit.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<Dictionary?>(null) + available).forEach { dictionary ->
            val lit = dictionary == picked
            Text(
                text = dictionary?.label ?: HOME_DICTIONARY_ALL,
                fontSize = 12.sp,
                fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
                color = if (lit) c.accent else c.textSecondary,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (lit) c.accent.copy(alpha = 0.16f) else c.text.copy(alpha = 0.05f))
                        .clickable(role = Role.Tab) { onPick(dictionary) }
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** Before the dictionaries are read: a random word on request, which reads them. */
@Composable
private fun AskRandomWord(onAsk: () -> Unit) {
    val c = Zayit.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HoverBox(onClick = onAsk) {
            Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Refresh, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Text(HOME_DICTIONARY_RANDOM, fontSize = 13.sp, color = c.textSecondary)
            }
        }
    }
}

/** A word of [pool] drawn at random while nothing is typed, drawn again with its button. */
@Composable
private fun RandomWord(
    pool: List<DictionaryEntry>,
    showDictionary: Boolean,
    onOpen: (DictionaryEntry) -> Unit,
) {
    val c = Zayit.colors
    var draw by remember { mutableIntStateOf(0) }
    val entry = remember(pool, draw) { pool.randomOrNull() } ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(HOME_DICTIONARY_RANDOM, fontSize = 11.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
            TitleIconButton(Icons.Outlined.Refresh, HOME_DICTIONARY_RANDOM) { draw++ }
        }
        HoverBox(onClick = { onOpen(entry) }, tinted = true, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.word, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (showDictionary) Tag(entry.dictionary.label)
                }
                Text(entry.gloss, fontSize = 13.sp, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Some dictionaries couldn't be read (the library DB missing, say), with a way to try again. */
@Composable
private fun FailedNotice(onRetry: () -> Unit) {
    val c = Zayit.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Text(HOME_DICTIONARY_FAILED, fontSize = 12.sp, color = c.textSecondary)
        Text(
            HOME_DICTIONARY_RETRY,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.accent,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onRetry).padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

/** A word found: the word, its dictionary when all are searched, and the start of its explanation. */
@Composable
private fun EntryRow(
    entry: DictionaryEntry,
    showDictionary: Boolean,
    onClick: () -> Unit,
) {
    val c = Zayit.colors
    HoverBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.word, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (showDictionary) Tag(entry.dictionary.label)
            }
            Text(entry.gloss, fontSize = 12.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An entry read in full, as its book has it, with the way back to the search and on to the book. */
@Composable
private fun EntryPage(
    state: HomeWidgetsState,
    entry: DictionaryEntry,
    onBack: () -> Unit,
    onOpenBook: () -> Unit,
) {
    val c = Zayit.colors
    val lines by produceState(emptyList<Line>(), entry) {
        value = runCatching { state.app.libraryStore.repository().getLines(entry.bookId, entry.first, entry.last) }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Back points to where reading came from: the start of the reading direction
            TitleIconButton(Icons.Outlined.ChevronRight, HOME_DICTIONARY_BACK, onBack)
            Text(entry.word, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1, modifier = Modifier.weight(1f))
            Tag(entry.dictionary.label)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { line ->
                Text(buildAnnotatedFromHtml(line.content, baseTextSize = 14f, boldColor = c.accent, linkColor = c.accent), fontSize = 14.sp, color = c.text, lineHeight = 22.sp)
            }
        }
        io.github.kdroidfilter.seforimapp.android.ui.components.OutlineButton("פתיחה ב${entry.dictionary.label}", onOpenBook, Modifier.align(Alignment.End))
    }
}

@Composable
private fun Tag(text: String) {
    val c = Zayit.colors
    Text(
        text,
        fontSize = 10.sp,
        color = c.textSecondary,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.textSecondary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = Zayit.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** Opens a book by id at a line in a new tab, the book read off the UI thread. */
@Composable
internal fun rememberOpenBookAt(state: HomeWidgetsState): (Long, Long?) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        { bookId, lineId ->
            scope.launch {
                runCatching { state.app.libraryStore.repository().getBookCore(bookId) }.getOrNull()?.let { state.openBook(it, lineId) }
            }
        }
    }
}
