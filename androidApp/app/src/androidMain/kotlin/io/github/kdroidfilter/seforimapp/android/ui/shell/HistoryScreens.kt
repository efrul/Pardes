package io.github.kdroidfilter.seforimapp.android.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.FavoriteEntry
import io.github.kdroidfilter.seforimapp.android.data.FavoriteFolder
import io.github.kdroidfilter.seforimapp.android.data.VisitEntry
import io.github.kdroidfilter.seforimapp.android.data.VisitKind
import io.github.kdroidfilter.seforimapp.android.data.guardedWrite
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The History and Favorites tab pages: ports of desktop 0.6.1 features/history/HistoryScreen.kt and
 * features/favorites/FavoritesScreen.kt, drawn with the primitives of core/presentation/components/ListPageComponents.kt
 * (centred 720dp column, cards, section headers, rows). Touch: the × that desktop shows on hover is always visible,
 * a long press stands for the right click, and the popups become dialogs.
 */

private const val HISTORY_PAGE_LIMIT = 500

/** Where a history entry reopens: the book at its line, or the search (desktop core/history/VisitEntryDestination.kt). */
fun VisitEntry.destination(): Destination? = when (kind) {
    VisitKind.BOOK -> bookId?.let { Destination.Book(bookId = it, lineId = lineId, title = title.substringBefore(" - ")) }
    VisitKind.SEARCH -> searchQuery?.let { Destination.Search(it, scope = searchScope, globalExtended = searchGlobalExtended) }
}

fun FavoriteEntry.destination(): Destination = Destination.Book(bookId = bookId, lineId = lineId, title = title.substringBefore(" - "))

/**
 * Full visit-history page (the chrome://history equivalent): searchable, grouped by day, with per-entry deletion and
 * clear-all. Opening an entry navigates in this tab, as desktop. On a phone the title and the trash sit in the top
 * bar ([topBar]); on a tablet in the page header.
 */
@Composable
fun HistoryPage(wide: Boolean, onOpen: (Destination, String) -> Unit, topBar: (@Composable (actions: @Composable () -> Unit) -> Unit)?) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val store = app.userData.history
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val revision by store.revision.collectAsState()
    var entries by remember { mutableStateOf<List<VisitEntry>>(emptyList()) }
    LaunchedEffect(query, revision) { entries = runCatching { store.query(query, HISTORY_PAGE_LIMIT) }.getOrDefault(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("he")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val grouped = remember(entries) { entries.groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() } }
    var collapsed by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    LaunchedEffect(grouped.keys) { collapsed = emptySet() }
    fun dayLabel(day: LocalDate): String = when (day) {
        today -> "היום"
        today.minusDays(1) -> "אתמול"
        else -> day.format(dateFormatter)
    }

    val clearButton: @Composable () -> Unit = { ZIconButton(Icons.Outlined.Delete, ShellTexts.CLEAR_HISTORY, { confirmClear = true }) }
    Column(Modifier.fillMaxSize()) {
        topBar?.invoke(clearButton)
        ListPageColumn(wide) {
            if (topBar == null) PageHeader("היסטוריה") { clearButton() }
            PageSearchField(query, { query = it }, "חיפוש…")
            if (entries.isEmpty()) {
                EmptyState(Icons.Outlined.History, ShellTexts.HISTORY_EMPTY)
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    grouped.forEach { (day, dayEntries) ->
                        val expanded = day !in collapsed
                        item(key = "day-$day") {
                            CardSurface {
                                SectionHeader(
                                    title = dayLabel(day),
                                    count = dayEntries.size,
                                    expanded = expanded,
                                    onToggle = { collapsed = if (expanded) collapsed + day else collapsed - day },
                                )
                                if (expanded) {
                                    dayEntries.forEachIndexed { index, entry ->
                                        val time = remember(entry.visitedAt) { Instant.ofEpochMilli(entry.visitedAt).atZone(zone).toLocalTime().format(timeFormatter) }
                                        ListRow(
                                            title = entry.title,
                                            icon = if (entry.kind == VisitKind.BOOK) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Search,
                                            tint = Zayit.colors.text,
                                            onOpen = { entry.destination()?.let { onOpen(it, entry.title) } },
                                            onDelete = { scope.launch { guardedWrite("History removal") { store.delete(entry.key) } } },
                                            showDivider = index < dayEntries.lastIndex,
                                            leading = { Text(time, fontSize = 12.sp, color = Zayit.colors.textSecondary) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear && isTabInFront()) {
        ConfirmDialog(
            title = ShellTexts.CLEAR_HISTORY_TITLE,
            message = ShellTexts.CLEAR_HISTORY_MESSAGE,
            confirmText = ShellTexts.CLEAR_HISTORY_CONFIRM,
            onConfirm = {
                confirmClear = false
                scope.launch { guardedWrite("History clearing") { store.clearAll() } }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/**
 * Favorites page: a bookmark manager. Folders as collapsible cards (two per row on a tablet), then the favorites
 * filed in no folder ("ללא תיקייה"); a long press on a row moves it to a folder. Opening navigates in this tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesPage(wide: Boolean, onOpen: (Destination, String) -> Unit, topBar: (@Composable (actions: @Composable () -> Unit) -> Unit)?) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val store = app.userData.favorites
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val revision by store.revision.collectAsState()
    var entries by remember { mutableStateOf<List<FavoriteEntry>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    LaunchedEffect(query, revision) {
        runCatching {
            entries = store.query(query)
            folders = store.folders()
        }
    }
    var newFolder by remember { mutableStateOf(false) }
    val byFolder = remember(entries) { entries.groupBy { it.folderId } }
    val unfiled = byFolder[null].orEmpty()
    val open: (FavoriteEntry) -> Unit = { onOpen(it.destination(), it.title) }
    val delete: (FavoriteEntry) -> Unit = { scope.launch { guardedWrite("Favorite removal") { store.remove(it.key) } } }
    val move: (FavoriteEntry, Long?) -> Unit = { e, id -> scope.launch { guardedWrite("Favorite move") { store.setFolder(e.key, id) } } }

    val addButton: @Composable () -> Unit = { ZIconButton(Icons.Outlined.CreateNewFolder, "תיקייה חדשה", { newFolder = true }) }
    Column(Modifier.fillMaxSize()) {
        topBar?.invoke(addButton)
        ListPageColumn(wide) {
            if (topBar == null) PageHeader("מועדפים") { addButton() }
            PageSearchField(query, { query = it }, "חפש במועדפים...")
            if (entries.isEmpty() && folders.isEmpty()) {
                EmptyState(Icons.Outlined.StarOutline, ShellTexts.FAVORITES_EMPTY)
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shownFolders = folders.filter { query.isBlank() || byFolder[it.id].orEmpty().isNotEmpty() }
                    if (shownFolders.isNotEmpty()) {
                        item(key = "folders") {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = if (wide) 2 else 1,
                            ) {
                                shownFolders.forEach { folder ->
                                    FolderCard(
                                        folder = folder,
                                        entries = byFolder[folder.id].orEmpty(),
                                        folders = folders,
                                        onOpen = open,
                                        onDelete = delete,
                                        onMove = move,
                                        onDeleteFolder = { scope.launch { guardedWrite("Folder removal") { store.deleteFolder(folder.id) } } },
                                        modifier = if (wide) Modifier.weight(1f).widthIn(min = 220.dp) else Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                    if (unfiled.isNotEmpty()) {
                        item(key = "unfiled") {
                            Column {
                                Text(
                                    "ללא תיקייה",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Zayit.colors.textSecondary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
                                )
                                CardSurface {
                                    unfiled.forEachIndexed { index, entry ->
                                        FavoriteRow(entry, folders, open, delete, move, showDivider = index < unfiled.lastIndex)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (newFolder && isTabInFront()) {
        InputDialog(
            title = "תיקייה חדשה",
            placeholder = "שם התיקייה",
            confirmText = "צור",
            onConfirm = { name ->
                newFolder = false
                if (name.isNotBlank()) scope.launch { guardedWrite("Folder creation") { store.createFolder(name.trim(), System.currentTimeMillis()) } }
            },
            onDismiss = { newFolder = false },
        )
    }
}

@Composable
private fun FolderCard(
    folder: FavoriteFolder,
    entries: List<FavoriteEntry>,
    folders: List<FavoriteFolder>,
    onOpen: (FavoriteEntry) -> Unit,
    onDelete: (FavoriteEntry) -> Unit,
    onMove: (FavoriteEntry, Long?) -> Unit,
    onDeleteFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(folder.id) { mutableStateOf(true) }
    CardSurface(modifier) {
        SectionHeader(
            title = folder.name,
            count = entries.size,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            icon = Icons.Outlined.Folder,
            onDelete = onDeleteFolder,
        )
        if (expanded && entries.isNotEmpty()) {
            // Desktop FolderCard insets the entries, so they read as the folder's
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
                entries.forEachIndexed { index, entry ->
                    FavoriteRow(entry, folders, onOpen, onDelete, onMove, showDivider = index < entries.lastIndex, compact = true)
                }
            }
        }
    }
}

/** A favorite: tap opens it, × removes it, a long press offers the folders to move it to (desktop FolderMovePopup). */
@Composable
private fun FavoriteRow(
    entry: FavoriteEntry,
    folders: List<FavoriteFolder>,
    onOpen: (FavoriteEntry) -> Unit,
    onDelete: (FavoriteEntry) -> Unit,
    onMove: (FavoriteEntry, Long?) -> Unit,
    showDivider: Boolean,
    compact: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListRow(
            title = entry.title,
            icon = Icons.Outlined.Star,
            tint = Zayit.colors.accent,
            onOpen = { onOpen(entry) },
            onDelete = { onDelete(entry) },
            onLongPress = { menu = true },
            showDivider = showDivider,
            compact = compact,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Zayit.colors.panel) {
            FolderMenuItem("ללא תיקייה", entry.folderId == null) {
                menu = false
                onMove(entry, null)
            }
            folders.forEach { folder ->
                FolderMenuItem(folder.name, entry.folderId == folder.id) {
                    menu = false
                    onMove(entry, folder.id)
                }
            }
        }
    }
}

@Composable
private fun FolderMenuItem(text: String, current: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(if (current) Icons.Outlined.Check else Icons.Outlined.Folder, null, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

/**
 * The reader star's folder chooser (desktop BreadcrumbActionsView secondary click, FavoritePopupContent): the
 * favorite's title ([showTitle], for a popup with no title of its own), "ללא תיקייה", the folders (the current one
 * checked) and a new-folder field. For the reader to show in a popup or a bottom sheet.
 */
@Composable
fun FavoriteFolderChooser(favoriteKey: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = false) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val store = app.userData.favorites
    // Writes go to the app's scope: the chooser closes (a sheet leaves the composition at the next frame) as they start,
    // and a new folder's two steps must both run
    val scope = app.appScope
    val revision by store.revision.collectAsState()
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    var current by remember { mutableStateOf<Long?>(null) }
    // Desktop gets the title from the breadcrumb; the stored favorite holds the same one
    var title by remember { mutableStateOf("") }
    LaunchedEffect(revision) {
        runCatching {
            folders = store.folders()
            val entry = store.get(favoriteKey)
            current = entry?.folderId
            title = entry?.title.orEmpty()
        }
    }
    var name by remember { mutableStateOf("") }
    val c = Zayit.colors
    Column(modifier.padding(8.dp)) {
        if (showTitle && title.isNotBlank()) {
            Text(
                title,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        FolderChoice("ללא תיקייה", current == null) { scope.launch { guardedWrite("Favorite move") { store.setFolder(favoriteKey, null) } }; onDismiss() }
        folders.forEach { folder ->
            FolderChoice(folder.name, current == folder.id) { scope.launch { guardedWrite("Favorite move") { store.setFolder(favoriteKey, folder.id) } }; onDismiss() }
        }
        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CreateNewFolder, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) Text("תיקייה חדשה", color = c.textSecondary, fontSize = 14.sp)
                BasicTextField(
                    name,
                    { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = ZayitFonts.ui),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (name.isNotBlank()) {
                ZIconButton(Icons.Outlined.Add, "צור", {
                    val folderName = name.trim()
                    name = ""
                    scope.launch {
                        guardedWrite("Folder creation") {
                            val id = store.createFolder(folderName, System.currentTimeMillis())
                            store.setFolder(favoriteKey, id)
                        }
                    }
                    onDismiss()
                })
            }
        }
    }
}

@Composable
private fun FolderChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Folder, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Text(text, color = c.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
}

// ---- List page primitives (desktop ListPageComponents.kt) ----

/** Desktop ListPageContainer: a column of at most 720dp, centred. */
@Composable
private fun ListPageColumn(wide: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = if (wide) 24.dp else 10.dp, vertical = if (wide) 20.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun PageHeader(title: String, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.text, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth().clip(shape).border(1.dp, c.panelBorder, shape).background(c.panel).padding(6.dp)) { content() }
}

/** Desktop PageSearchField (also used by the title-bar popups). */
@Composable
internal fun PageSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, focusRequester: FocusRequester? = null) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(shape).border(1.dp, c.panelBorder, shape).background(c.panel).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, fontSize = 15.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                query,
                onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = c.text, fontFamily = ZayitFonts.ui),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        if (query.isNotEmpty()) ZIconButton(Icons.Outlined.Close, "ניקוי", { onQueryChange("") }, tint = c.textSecondary)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector? = null,
    onDelete: (() -> Unit)? = null,
) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Desktop's chevron points to the reading direction when collapsed and down when open.
        Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronLeft, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        if (icon != null) Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), fontSize = 12.sp, color = c.textSecondary)
        if (onDelete != null) ZIconButton(Icons.Outlined.Close, ShellTexts.DELETE, onDelete, tint = c.textSecondary) else Box(Modifier.size(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean,
    compact: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Zayit.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 44.dp else 48.dp)
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke(this)
            Icon(icon, null, tint = tint, modifier = Modifier.size(if (compact) 15.dp else 17.dp))
            Text(
                title,
                fontSize = if (compact) 13.sp else 15.sp,
                color = c.text,
                fontFamily = ZayitFonts.ui,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ZIconButton(Icons.Outlined.Close, ShellTexts.DELETE, onDelete, tint = c.textSecondary)
        }
        if (showDivider) HorizontalDivider(color = c.divider.copy(alpha = 0.5f), modifier = Modifier.padding(start = 28.dp))
    }
}

@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = Zayit.colors.textSecondary, modifier = Modifier.size(40.dp))
        Text(message, color = Zayit.colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** Desktop ConfirmPopup, as a dialog. */
@Composable
private fun ConfirmDialog(title: String, message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Zayit.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = { Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text(message, color = c.textSecondary, fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = c.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול", color = c.text) } },
    )
}

/** Desktop InputPopup, as a dialog: a single-line value. */
@Composable
private fun InputDialog(title: String, placeholder: String, confirmText: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Zayit.colors
    var value by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(8.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = { Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Box(Modifier.fillMaxWidth().height(44.dp).clip(shape).border(1.dp, c.panelBorder, shape).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = c.textSecondary, fontSize = 15.sp)
                BasicTextField(
                    value,
                    { value = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = ZayitFonts.ui),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        // Desktop InputPopup greys its button until a name is typed; a colour set on the label would hide that
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = c.accent, disabledContentColor = c.textDisabled),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול", color = c.text) } },
    )
}
