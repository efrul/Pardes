package io.github.kdroidfilter.seforimapp.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

private data class TocRow(val entry: TocEntry, val level: Int, val expanded: Boolean)

private fun flatten(roots: List<TocEntry>, expanded: Set<Long>, children: Map<Long, List<TocEntry>>): List<TocRow> {
    val out = ArrayList<TocRow>()
    fun walk(entries: List<TocEntry>, level: Int) {
        for (e in entries) {
            val open = e.id in expanded
            out += TocRow(e, level, open)
            if (open) walk(children[e.id].orEmpty(), level + 1)
        }
    }
    walk(roots, 0)
    return out
}

/**
 * The TOC pane (desktop features/bookcontent/ui/panels/booktoc/BookTocPanel.kt + BookTocView.kt): the TOC without
 * a lone root, parents that only open, leaves that select their line, the selected line's entry in bold and scrolled
 * into view; the alt TOC under it when the book has one; the title search ("חיפוש כותרת") in place of both while
 * typing. [onPicked] closes a drawer after a jump.
 */
@Composable
fun TocPane(vm: ReaderViewModel, state: ReaderUiState, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    val search = state.tocSearch
    val searching = search != null && search.isNotBlank()
    val showAlt = state.book?.hasAltStructures == true && state.altToc.structures.isNotEmpty() && !searching
    Column(modifier) {
        if (search != null) TocSearchField(search, onQuery = vm::searchToc, onClose = vm::closeTocSearch, onEnter = { state.tocSearchResult?.bestMatchId?.let { vm.openTocSearchMatch(it); onPicked() } })
        if (searching) {
            val result = state.tocSearchResult
            when {
                result == null -> Unit
                result.matchIds.isEmpty() -> Text("לא נמצאו כותרות", color = Zayit.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                else -> {
                    val pick: (TocEntry) -> Unit = { vm.openTocSearchMatch(it.id); onPicked() }
                    if (result.roots.isNotEmpty()) TocTree(flatten(result.roots, result.children.keys, result.children), null, result.highlights, pick, pick, Modifier.weight(1f))
                    if (result.roots.isNotEmpty() && result.altRoots.isNotEmpty()) HorizontalDivider(color = Zayit.colors.divider, modifier = Modifier.padding(vertical = 6.dp))
                    if (result.altRoots.isNotEmpty()) TocTree(flatten(result.altRoots, result.children.keys, result.children), null, result.highlights, pick, pick, Modifier.weight(1f))
                }
            }
        } else {
            val rows = remember(state.tocDisplayRoots, state.tocExpanded, state.tocChildren) { flatten(state.tocDisplayRoots, state.tocExpanded, state.tocChildren) }
            TocTree(rows, state.selectedTocId, null, onOpen = { vm.openTocEntry(it); onPicked() }, onExpand = { vm.toggleTocEntry(it) }, modifier = Modifier.weight(1f))
            if (showAlt) {
                HorizontalDivider(color = Zayit.colors.divider, modifier = Modifier.padding(vertical = 6.dp))
                val alt = state.altToc
                val bookId = state.book.id
                val roots = remember(alt.entries, bookId) { alt.entries.map { it.toTocEntry(bookId) } }
                val children = remember(alt.children, bookId) { alt.children.mapValues { (_, list) -> list.map { it.toTocEntry(bookId) } } }
                val rowsAlt = remember(roots, alt.expanded, children) { flatten(roots, alt.expanded, children) }
                TocTree(
                    rowsAlt,
                    alt.selectedEntryId,
                    null,
                    onOpen = { e -> alt.entriesById[e.id]?.let { vm.selectAltEntry(it) }; onPicked() },
                    onExpand = { e -> alt.entriesById[e.id]?.let { vm.toggleAltEntry(it) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TocTree(rows: List<TocRow>, selectedId: Long?, highlights: Map<Long, List<IntRange>>?, onOpen: (TocEntry) -> Unit, onExpand: (TocEntry) -> Unit, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val list = rememberLazyListState()
    // The selected entry comes into view once per selection (desktop BookTocView auto-center)
    var centeredFor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId, rows.size) {
        if (selectedId == null || centeredFor == selectedId) return@LaunchedEffect
        val idx = rows.indexOfFirst { it.entry.id == selectedId }
        if (idx >= 0) {
            val visible = list.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == idx } || visible.firstOrNull()?.index == idx) list.scrollToItem((idx - 2).coerceAtLeast(0))
            centeredFor = selectedId
        }
    }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
        items(rows, key = { it.entry.id }) { row ->
            val e = row.entry
            val selected = e.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) c.selectedRow else Color.Transparent)
                    .clickable(role = Role.Button) { if (e.hasChildren) onExpand(e) else onOpen(e) }
                    .padding(start = (6 + row.level * 16).dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (e.hasChildren) {
                    Icon(if (row.expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowLeft, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                } else {
                    Spacer(Modifier.width(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                val ranges = highlights?.get(e.id)
                val text = if (highlights == null) {
                    androidx.compose.ui.text.AnnotatedString(e.text)
                } else {
                    remember(e.text, ranges) {
                        buildAnnotatedString {
                            append(e.text)
                            ranges?.forEach { r ->
                                val end = (r.last + 1).coerceAtMost(length)
                                if (end > r.first) addStyle(SpanStyle(background = Color(0x66FFC107)), r.first, end)
                            }
                        }
                    }
                }
                Text(
                    text,
                    fontSize = 15.sp,
                    color = if (highlights != null && ranges == null) c.textDisabled else c.text,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Desktop PaneSearchLayout's field: "חיפוש כותרת", Enter opens the best match. */
@Composable
private fun TocSearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, onEnter: () -> Unit) {
    PaneSearchField(query, "חיפוש כותרת", onQuery, onClose, onEnter)
}

/**
 * A pane's search field (desktop ui/components/PaneSearch.kt), focused when it opens unless [autoFocus] is off (an
 * always-shown field must not raise the keyboard); ✕ runs [onClose], or clears the text when there is none.
 */
@Composable
fun PaneSearchField(query: String, placeholder: String, onQuery: (String) -> Unit, onClose: (() -> Unit)?, onEnter: () -> Unit, autoFocus: Boolean = true) {
    val c = Zayit.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(c.ground).heightIn(min = 44.dp).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, color = c.textDisabled, fontSize = 14.sp)
            BasicTextField(
                query,
                onQuery,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = ZayitFonts.ui),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEnter() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (onClose != null) {
            ZIconButton(Icons.Outlined.Close, "סגור", onClose, tint = c.textSecondary)
        } else if (query.isNotEmpty()) {
            ZIconButton(Icons.Outlined.Close, "נקה", { onQuery("") }, tint = c.textSecondary)
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** The TOC pane's header action: the title search (desktop PaneSearchButton). */
@Composable
fun TocSearchButton(open: Boolean, onClick: () -> Unit) {
    ZIconButton(Icons.Outlined.Search, "חיפוש כותרת", onClick, tint = Zayit.colors.textSecondary, selected = open)
}
