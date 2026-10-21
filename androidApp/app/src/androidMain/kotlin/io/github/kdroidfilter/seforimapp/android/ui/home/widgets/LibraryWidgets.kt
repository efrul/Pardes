package io.github.kdroidfilter.seforimapp.android.ui.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforimapp.android.data.FavoriteEntry
import io.github.kdroidfilter.seforimapp.android.data.FavoriteFolder
import io.github.kdroidfilter.seforimapp.android.data.UserNote
import io.github.kdroidfilter.seforimapp.android.data.VisitEntry
import io.github.kdroidfilter.seforimapp.android.data.VisitKind
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.notes.NotesPage
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.ShellTexts
import io.github.kdroidfilter.seforimapp.android.ui.shell.destination
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Port of desktop 0.6.1 features/home/widgets/library/LibraryWidgets.kt: the app's own lists on the Home, the
// history, the favorites and the user's notes, each opening its entry in a new tab. They follow their stores' writes,
// and show as many whole rows as the card holds.

private const val LIST_LIMIT = 40

/** The books opened and searches run lately; tapping one opens it again. */
internal object HistoryWidget : HomeWidget {
    override val id = "history"
    override val title = "היסטוריה"
    override val defaultSpan = CellSpan(5, 4)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val store = state.app.userData.history
        val revision by store.revision.collectAsState()
        val entries by produceState(emptyList<VisitEntry>(), revision) { value = runCatching { store.query("", LIST_LIMIT) }.getOrDefault(emptyList()) }
        val open = rememberOpenBookAt(state)
        ListCard(
            title = "היסטוריה",
            onShowAll = { state.openPage(Destination.History) },
            showAllLabel = ShellTexts.SHOW_ALL_HISTORY,
            empty = ShellTexts.HISTORY_EMPTY.takeIf { entries.isEmpty() },
            modifier = modifier,
        ) {
            entries.forEach { entry ->
                EntryRow(
                    title = entry.title,
                    trailing = entry.visitedAt.whenLabel("אתמול"),
                    icon = if (entry.kind == VisitKind.SEARCH) Icons.Outlined.Search else Icons.AutoMirrored.Outlined.MenuBook,
                    onClick = {
                        when {
                            entry.kind == VisitKind.SEARCH -> (entry.destination() as? Destination.Search)?.let(state.openSearch)
                            entry.bookId != null -> open(entry.bookId, entry.lineId)
                        }
                    },
                )
            }
        }
    }
}

/** The user's favorites, by folder when there are folders; tapping one opens it where it was starred. */
internal object FavoritesWidget : HomeWidget {
    override val id = "favorites"
    override val title = "מועדפים"
    override val defaultSpan = CellSpan(5, 4)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val store = state.app.userData.favorites
        val revision by store.revision.collectAsState()
        val entries by produceState(emptyList<FavoriteEntry>(), revision) { value = runCatching { store.query() }.getOrDefault(emptyList()) }
        val folders by produceState(emptyList<FavoriteFolder>(), revision) { value = runCatching { store.folders() }.getOrDefault(emptyList()) }
        // null: every favorite
        var folder by remember { mutableStateOf<Long?>(null) }
        val shown = entries.filter { folder == null || it.folderId == folder }
        val open = rememberOpenBookAt(state)
        ListCard(
            title = "מועדפים",
            onShowAll = { state.openPage(Destination.Favorites) },
            showAllLabel = ShellTexts.SHOW_ALL_FAVORITES,
            empty = ShellTexts.FAVORITES_EMPTY.takeIf { entries.isEmpty() },
            header = { if (folders.isNotEmpty()) FolderChips(folders, folder) { folder = it } },
            modifier = modifier,
        ) {
            shown.take(LIST_LIMIT).forEach { entry ->
                EntryRow(title = entry.title, icon = Icons.Filled.Bookmark, iconTint = Zayit.colors.accent, onClick = { open(entry.bookId, entry.lineId) })
            }
        }
    }
}

/** "All" and the user's folders, the one shown lit; a row that scrolls when they're many. */
@Composable
private fun FolderChips(
    folders: List<FavoriteFolder>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    val c = Zayit.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf<FavoriteFolder?>(null) + folders).forEach { folder ->
            val lit = folder?.id == selected
            Text(
                text = folder?.name ?: "הכל",
                fontSize = 11.sp,
                fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
                color = if (lit) c.accent else c.textSecondary,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (lit) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelect(folder?.id) }
                        .heightIn(min = 28.dp)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/** The user's latest notes, with the passage and the book; tapping one opens the passage. */
internal object NotesWidget : HomeWidget {
    override val id = "notes"
    override val title = "ההערות שלי"
    override val defaultSpan = CellSpan(6, 4)
    override val minSpan = CellSpan(5, 3)

    // Wider, a one-line note would leave half its row empty
    override val maxSpan = CellSpan(7, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val c = Zayit.colors
        val written by state.app.userData.revision.collectAsState()
        val notes by produceState(emptyList<Pair<UserNote, String>>(), written) {
            // One hop off the UI thread for the notes and all their books, not one per book
            value =
                withContext(Dispatchers.IO) {
                    val recent = runCatching { state.app.userData.recentNotes(LIST_LIMIT) }.getOrDefault(emptyList())
                    // The book's name is a nicety: a note still shows, and opens, without the books DB
                    val titles =
                        recent.map { it.bookId }.distinct().associateWith { id ->
                            runCatching { state.app.libraryStore.repository().getBookCore(id)?.title }.getOrNull().orEmpty()
                        }
                    recent.map { it to titles[it.bookId].orEmpty() }
                }
        }
        // Desktop: a note opens its book on its line with the notes pane, "הצג את כל ההערות" opens the notes page
        val appVm: AppViewModel = LocalAppViewModel.current ?: viewModel()
        val open: (Long, Long, String) -> Unit = { bookId, lineId, title -> appVm.openInNewTab(Destination.Book(bookId, lineId, title, openNotes = true), title) }
        var showAll by remember { mutableStateOf(false) }
        if (showAll && isTabInFront()) NotesPage(onOpen = open, onDismiss = { showAll = false })
        ListCard(title = "ההערות שלי", onShowAll = { showAll = true }, showAllLabel = "הצג את כל ההערות", empty = "אין הערות".takeIf { notes.isEmpty() }, modifier = modifier) {
            notes.forEach { (note, bookTitle) ->
                HoverBox(onClick = { open(note.bookId, note.lineId, bookTitle) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.EditNote, null, tint = c.accent, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(note.note, fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(note.quote, bookTitle).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 11.sp,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A titled card of rows, or its [empty] message when there is nothing to list. */
@Composable
private fun ListCard(
    title: String,
    empty: String?,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
    showAllLabel: String = "",
    header: @Composable () -> Unit = {},
    rows: @Composable () -> Unit,
) {
    val c = Zayit.colors
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WidgetTitle(title) {
                if (onShowAll != null) TitleIconButton(Icons.AutoMirrored.Outlined.OpenInNew, showAllLabel, onShowAll)
            }
            header()
            if (empty != null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(empty, fontSize = 13.sp, color = c.textSecondary)
                }
            } else {
                FitColumn(Modifier.fillMaxWidth().weight(1f)) { rows() }
            }
        }
    }
}

/** A row of a list: its icon, its title, and what goes at the end. */
@Composable
private fun EntryRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: String? = null,
    iconTint: Color = Zayit.colors.text,
) {
    val c = Zayit.colors
    HoverBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, fontSize = 11.sp, color = c.textSecondary, maxLines = 1)
        }
    }
}

/** The time today, "yesterday", or the date before: as the history page labels its days. */
private fun Long.whenLabel(yesterday: String): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(this).atZone(zone)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> "⁦%02d:%02d⁩".format(at.hour, at.minute)
        today.minusDays(1) -> yesterday
        else -> "⁦${at.dayOfMonth}.${at.monthValue}⁩"
    }
}
