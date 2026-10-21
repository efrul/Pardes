package io.github.kdroidfilter.seforimapp.android.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.FavoriteEntry
import io.github.kdroidfilter.seforimapp.android.data.FavoriteFolder
import io.github.kdroidfilter.seforimapp.android.data.VisitEntry
import io.github.kdroidfilter.seforimapp.android.data.VisitKind
import io.github.kdroidfilter.seforimapp.android.ui.AppUiState
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Tab
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import kotlinx.coroutines.launch

/*
 * The title bar's two lists: desktop 0.6.1 core/presentation/components/TabSearchPopup.kt (the clock: open tabs and the
 * 12 latest history entries, searchable, with "הצג את כל ההיסטוריה") and FavoritesPopup.kt (the star). A tablet shows
 * them as desktop's 340dp popup under the button ([TitleBarPopup]); a phone as a bottom sheet ([ShellSheet]) from the
 * overflow menu.
 */

private const val POPUP_HISTORY_LIMIT = 12

/** Desktop BelowAnchorEndPositionProvider: under the anchor, its far edge on the anchor's, kept on screen. */
private object BelowAnchorEndPositionProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.right - popupContentSize.width
        val y = anchorBounds.bottom
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** Tablet: a desktop-style popup under its title-bar button. */
@Composable
fun TitleBarPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Zayit.colors
    val shape = RoundedCornerShape(10.dp)
    Popup(popupPositionProvider = BelowAnchorEndPositionProvider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .padding(4.dp)
                .width(340.dp)
                .shadow(8.dp, shape)
                .background(c.panel, shape)
                .border(1.dp, c.panelBorder, shape)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** Phone: the same content in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var contentHeight by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Zayit.colors.panel,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Lists read after the sheet opened (history, favorites) change its height, but the sheet kept the
                // height it opened at, with the rows past it out of reach: it is expanded again to the new height
                .onSizeChanged { size ->
                    val resized = contentHeight != 0 && size.height != contentHeight
                    contentHeight = size.height
                    if (resized && sheetState.targetValue == SheetValue.Expanded) scope.launch { sheetState.expand() }
                }
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/**
 * Phone: [TabSearchContent] in a sheet, opened once the history is read, so that the height the sheet opens at already
 * holds the history and the footer under it.
 */
@Composable
fun TabSearchSheet(state: AppUiState, appVm: AppViewModel, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val history by produceState<List<VisitEntry>?>(null) {
        value = runCatching { app.userData.history.query("", POPUP_HISTORY_LIMIT) }.getOrDefault(emptyList())
    }
    history?.let { first ->
        ShellSheet(onDismiss = onDismiss) { TabSearchContent(state, appVm, tablet = false, onDismiss = onDismiss, initialHistory = first) }
    }
}

/**
 * Chrome's Tab Search equivalent: searches the open tabs and the visit history, with a footer leading to the full
 * history page. Opening a history entry opens it in a new tab next to the current one.
 */
@Composable
fun ColumnScope.TabSearchContent(
    state: AppUiState,
    appVm: AppViewModel,
    tablet: Boolean,
    onDismiss: () -> Unit,
    initialHistory: List<VisitEntry> = emptyList(),
) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val store = app.userData.history
    var query by remember { mutableStateOf("") }
    val revision by store.revision.collectAsState()
    var history by remember { mutableStateOf(initialHistory) }
    LaunchedEffect(query, revision) { history = runCatching { store.query(query, POPUP_HISTORY_LIMIT) }.getOrDefault(emptyList()) }
    val focus = remember { FocusRequester() }
    if (tablet) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.trim()
    val tabs = state.tabs.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }

    PageSearchField(query, { query = it }, "חיפוש…", focus)
    // The list gives way to the footer on a short screen (Jelly Star), rather than pushing it out of the sheet
    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = if (tablet) 380.dp else 460.dp)) {
        if (tabs.isNotEmpty()) {
            item(key = "header-tabs") { PopupSectionHeader("לשוניות פתוחות") }
            items(tabs, key = { "tab-" + it.id }) { tab ->
                PopupRow(
                    label = tab.title.ifBlank { AppUiState.HOME_TITLE },
                    icon = tabIcon(tab),
                    onClick = {
                        appVm.selectTabById(tab.id)
                        onDismiss()
                    },
                    onClose = { appVm.state.value.tabs.indexOfFirst { it.id == tab.id }.takeIf { it >= 0 }?.let(appVm::closeTab) },
                )
            }
        }
        if (history.isNotEmpty()) {
            item(key = "header-history") { PopupSectionHeader("היסטוריה") }
            items(history, key = { "hist-" + it.key }) { entry ->
                PopupRow(
                    label = entry.title,
                    icon = if (entry.kind == VisitKind.BOOK) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Search,
                    onClick = {
                        entry.destination()?.let { appVm.openInNewTab(it, entry.title) }
                        onDismiss()
                    },
                )
            }
        }
        if (tabs.isEmpty() && history.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(ShellTexts.NO_RESULTS, fontSize = 13.sp, color = Zayit.colors.textSecondary)
                }
            }
        }
    }
    PopupFooterRow(ShellTexts.SHOW_ALL_HISTORY, Icons.Outlined.History, if (tablet) "Ctrl+H" else null) {
        appVm.showPage(Destination.History)
        onDismiss()
    }
}

/**
 * The favorites menu: root entries first, then each folder as a section, with a search field and a footer leading to
 * the full favorites page. Opening a favorite opens it in a new tab.
 */
@Composable
fun ColumnScope.FavoritesMenuContent(appVm: AppViewModel, tablet: Boolean, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val store = app.userData.favorites
    var query by remember { mutableStateOf("") }
    val revision by store.revision.collectAsState()
    var entries by remember { mutableStateOf<List<FavoriteEntry>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    LaunchedEffect(query, revision) {
        runCatching {
            entries = store.query(query)
            folders = store.folders()
        }
    }
    val focus = remember { FocusRequester() }
    if (tablet) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val open: (FavoriteEntry) -> Unit = {
        appVm.openInNewTab(it.destination(), it.title)
        onDismiss()
    }

    PageSearchField(query, { query = it }, "חפש במועדפים...", focus)
    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = if (tablet) 380.dp else 460.dp)) {
        items(entries.filter { it.folderId == null }, key = { "fav-" + it.key }) { entry ->
            PopupRow(entry.title, Icons.AutoMirrored.Outlined.MenuBook, onClick = { open(entry) })
        }
        folders.forEach { folder ->
            val folderEntries = entries.filter { it.folderId == folder.id }
            if (folderEntries.isNotEmpty()) {
                item(key = "folder-" + folder.id) { PopupSectionHeader(folder.name) }
                items(folderEntries, key = { "fav-" + it.key }) { entry ->
                    PopupRow(entry.title, Icons.AutoMirrored.Outlined.MenuBook, onClick = { open(entry) })
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(ShellTexts.FAVORITES_EMPTY, fontSize = 13.sp, color = Zayit.colors.textSecondary)
                }
            }
        }
    }
    PopupFooterRow(ShellTexts.SHOW_ALL_FAVORITES, Icons.Outlined.StarOutline, if (tablet) "Ctrl+Shift+O" else null) {
        appVm.showPage(Destination.Favorites)
        onDismiss()
    }
}

/** The icon a tab shows in the strip, the tab list and the popups (desktop TabsView). */
fun tabIcon(tab: Tab): ImageVector = when (tab.destination) {
    Destination.Home -> Icons.Outlined.Home
    is Destination.Search -> Icons.Outlined.Search
    is Destination.Book -> Icons.AutoMirrored.Outlined.MenuBook
    Destination.History -> Icons.Outlined.History
    Destination.Favorites -> Icons.Outlined.Star
    is Destination.Author -> Icons.Outlined.Person
}

@Composable
private fun PopupSectionHeader(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Zayit.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PopupRow(label: String, icon: ImageVector, onClick: () -> Unit, onClose: (() -> Unit)? = null) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, color = c.text, fontFamily = ZayitFonts.ui, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onClose != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "סגור לשונית", tint = c.textSecondary, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun PopupFooterRow(label: String, icon: ImageVector, shortcutHint: String?, onClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 13.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (shortcutHint != null) Text(shortcutHint, fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.widthIn(max = 120.dp))
    }
}
