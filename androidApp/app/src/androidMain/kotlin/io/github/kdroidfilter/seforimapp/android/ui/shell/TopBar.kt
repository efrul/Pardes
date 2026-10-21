package io.github.kdroidfilter.seforimapp.android.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ui.AppUiState
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.Tab
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.hasFindInPage
import io.github.kdroidfilter.seforimapp.android.ui.theme.ThemeMode
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlin.math.abs

/** What the title bars' buttons do. */
data class TopBarActions(
    val onHome: () -> Unit,
    val onFind: () -> Unit,
    val onTabs: () -> Unit,
    val onNewTab: () -> Unit,
    val onSettings: () -> Unit,
    val onTools: () -> Unit,
    val onCycleTheme: () -> Unit,
    val onHistory: () -> Unit,
    val onFavorites: () -> Unit,
    /** Phone title bar swipe: the next (+1) or previous (-1) tab. */
    val onSwipeTab: (Int) -> Unit,
    /** Desktop's update badge (update/AppUpdater): set while a newer Pardes is known, opens the update dialog. */
    val onAppUpdate: (() -> Unit)? = null,
)

/**
 * Phone top bar: the desktop title bar (core/presentation/components/MainTitleBar.kt) folded into one row. The current
 * tab is shown by its title, with the open-tabs counter (tab switcher) and an overflow with what desktop's title bar
 * buttons do. A horizontal swipe on it moves to the next or previous tab (the touch Ctrl+Tab). [findInMenu] puts
 * desktop's find-in-page button in the overflow (very narrow phones, where [extra] has no room for it).
 */
@Composable
fun PhoneTopBar(
    title: String,
    subtitle: String?,
    tabCount: Int,
    themeMode: ThemeMode,
    actions: TopBarActions,
    leading: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    findInMenu: Boolean = false,
) {
    val c = Zayit.colors
    var menu by remember { mutableStateOf(false) }
    val swipe by rememberUpdatedState(actions.onSwipeTab)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.titleBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        // RTL: the next tab stands on the left, so a swipe to the left brings it.
                        if (abs(total) > threshold) swipe(if (total < 0) 1 else -1)
                    },
                ) { _, dx -> total += dx }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            extra?.invoke()
            TabCounter(tabCount, actions.onTabs)
            Box {
                ZIconButton(Icons.Outlined.MoreVert, if (actions.onAppUpdate != null) "אפשרויות נוספות (עדכון זמין)" else "אפשרויות נוספות", { menu = true })
                // Desktop's update badge, folded into the overflow: a dot on its button, and its first item
                if (actions.onAppUpdate != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(8.dp).clip(CircleShape).background(c.accent))
                // Desktop's title bar order: the update badge, "+" at the end of the tabs, then home, find, favorites,
                // history, tools, theme and settings
                ShellMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.onAppUpdate?.let { open -> MenuItem("עדכון זמין", Icons.Outlined.SystemUpdate) { menu = false; open() } }
                    MenuItem("לשונית חדשה", Icons.Outlined.Add) { menu = false; actions.onNewTab() }
                    MenuItem("דף הבית", Icons.Outlined.Home) { menu = false; actions.onHome() }
                    if (findInMenu) MenuItem("חפש בתוך העמוד", Icons.Outlined.FindInPage) { menu = false; actions.onFind() }
                    MenuItem("מועדפים", Icons.Outlined.StarOutline) { menu = false; actions.onFavorites() }
                    MenuItem("היסטוריה", Icons.Outlined.History) { menu = false; actions.onHistory() }
                    MenuItem("כלים", Icons.Outlined.Construction) { menu = false; actions.onTools() }
                    MenuItem(themeLabel(themeMode), themeIcon(themeMode)) { menu = false; actions.onCycleTheme() }
                    MenuItem("הגדרות", Icons.Outlined.Settings) { menu = false; actions.onSettings() }
                }
            }
        }
    }
}

/**
 * A DropdownMenu that goes at once when it closes. M3 fades it out in a popup window above every screen, so an item
 * that opens another screen (tools, settings, a tab) showed that screen under the fading menu for half a second.
 */
@Composable
fun ShellMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Composed only while open: it still grows in, and leaves with nothing to fade
    if (expanded) DropdownMenu(expanded = true, onDismissRequest = onDismissRequest, containerColor = Zayit.colors.panel, content = content)
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** Desktop light_theme / dark_theme / system_theme: the theme in use. */
fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> ShellTexts.THEME_SYSTEM
    ThemeMode.Light -> ShellTexts.THEME_LIGHT
    ThemeMode.Dark -> ShellTexts.THEME_DARK
}

/** Desktop switch_to_*_theme: what the button does next (Light → Dark → System → Light). */
fun themeSwitchLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.Light -> "עבור לערכת נושא כהה"
    ThemeMode.Dark -> "עבור לערכת נושא מערכת"
    ThemeMode.System -> "עבור לערכת נושא בהירה"
}

fun themeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> Icons.Outlined.BrightnessMedium
    ThemeMode.Light -> Icons.Outlined.LightMode
    ThemeMode.Dark -> Icons.Outlined.DarkMode
}

@Composable
fun TabCounter(count: Int, onClick: () -> Unit) {
    val c = Zayit.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "לשוניות פתוחות", onClick = onClick)
            .semantics { contentDescription = "לשוניות פתוחות: $count" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).border(1.8.dp, c.text, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(if (count > 99) ":)" else count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
        }
    }
}

/** What a tab reads in the strip: the Home tab is "דף הבית - פרדס" there (desktop home_tab_with_app). */
private fun stripLabel(tab: Tab): String = if (tab.destination == Destination.Home) "דף הבית - פרדס" else tab.title

/**
 * Tablet title bar, like the desktop custom title bar (MainTitleBar + TabsView + TitleBarActionsButtonsView): the tab
 * strip (scrolling, the shown tab kept in view, "+" after the last tab), the workspace switcher, then home, find in
 * page, favorites, tab search & history, tools, theme and settings. A long press on a tab opens its menu, on "+" the
 * reopen-closed-tab item, and on any button its tooltip.
 */
@Composable
fun TabletTitleBar(
    state: AppUiState,
    appVm: AppViewModel,
    themeMode: ThemeMode,
    actions: TopBarActions,
    tabSearchVisible: Boolean,
    onTabSearchVisible: (Boolean) -> Unit,
    favoritesVisible: Boolean,
    onFavoritesVisible: (Boolean) -> Unit,
) {
    val c = Zayit.colors
    val findEnabled = state.selectedTab.destination.hasFindInPage()
    Row(
        Modifier.fillMaxWidth().background(c.titleBar).windowInsetsPadding(WindowInsets.statusBars).height(48.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabStrip(state, appVm, Modifier.weight(1f).fillMaxHeight())
        WorkspaceSwitcher(state, appVm)
        // Desktop's update badge (TitleBarActionsButtonsView): the first button while a newer version is known
        actions.onAppUpdate?.let { TitleBarButton(Icons.Outlined.SystemUpdate, "עדכון זמין", "עדכון זמין", it, tint = c.accent) }
        TitleBarButton(Icons.Outlined.Home, "דף הבית", "עבור לדף הבית  (Alt+Home)", actions.onHome)
        TitleBarButton(
            Icons.Outlined.Search,
            "חפש בתוך העמוד",
            if (findEnabled) "חפש בתוך העמוד  (Ctrl+F)" else "חיפוש זמין במסך תוצאות או כשספר נבחר",
            actions.onFind,
            enabled = findEnabled,
        )
        Box {
            TitleBarButton(Icons.Outlined.StarOutline, "מועדפים", "מועדפים  (Ctrl+Shift+O)", { onFavoritesVisible(!favoritesVisible) })
            if (favoritesVisible) {
                TitleBarPopup(onDismiss = { onFavoritesVisible(false) }) { FavoritesMenuContent(appVm, tablet = true, onDismiss = { onFavoritesVisible(false) }) }
            }
        }
        Box {
            TitleBarButton(Icons.Outlined.History, "חיפוש בלשוניות ובהיסטוריה", "חיפוש בלשוניות ובהיסטוריה  (Ctrl+Shift+A)", { onTabSearchVisible(!tabSearchVisible) })
            if (tabSearchVisible) {
                TitleBarPopup(onDismiss = { onTabSearchVisible(false) }) { TabSearchContent(state, appVm, tablet = true, onDismiss = { onTabSearchVisible(false) }) }
            }
        }
        TitleBarButton(Icons.Outlined.Construction, "כלים", "כלים", actions.onTools)
        TitleBarButton(themeIcon(themeMode), themeLabel(themeMode), themeSwitchLabel(themeMode), actions.onCycleTheme)
        TitleBarButton(Icons.Outlined.Settings, "הגדרות", "פתח הגדרות  (Ctrl+,)", actions.onSettings)
    }
}

/** A title-bar button with desktop's tooltip (shown on a long press, or on hover with a mouse). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleBarButton(icon: ImageVector, description: String, tooltip: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = Zayit.colors.text) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        ZIconButton(icon, description, { if (enabled) onClick() }, modifier = Modifier.alpha(if (enabled) 1f else 0.38f), tint = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStrip(state: AppUiState, appVm: AppViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // Keep the shown tab in view (a tab opened from a link, Ctrl+Tab, a restore…).
    LaunchedEffect(state.selectedIndex, state.tabs.size) {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == state.selectedIndex }
        val fullyVisible = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(state.selectedIndex)
    }
    LazyRow(
        modifier,
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(state.tabs, key = { _, t -> t.id }) { i, tab ->
            TabChip(
                tab = tab,
                selected = i == state.selectedIndex,
                onClick = { appVm.selectTab(i) },
                onClose = { appVm.closeTab(i) },
                menuItems = { tabMenuItems(context, appVm, i, vertical = false) },
            )
        }
        item(key = "+") {
            var menu by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .combinedClickable(role = Role.Button, onClickLabel = "הוסף לשונית", onLongClick = { menu = true }, onClick = appVm::newHomeTab)
                        .semantics { contentDescription = "הוסף לשונית" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, null, tint = Zayit.colors.text, modifier = Modifier.size(20.dp)) }
                ShellMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("פתח מחדש לשונית שנסגרה") },
                        leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                        enabled = state.canReopenClosedTab,
                        onClick = {
                            menu = false
                            appVm.reopenClosedTab()
                        },
                    )
                }
            }
        }
    }
}

// The pin marking a pinned tab of the strip, beside its icon or acronym (desktop 0.6.5 TabsView PinMarkSize/Gap).
private val PinMarkSize = 12.dp
private val PinMarkGap = 4.dp
private val PinMarkWidth = PinMarkSize + PinMarkGap

/**
 * One tab of the strip. A pinned tab is compact (desktop: 40dp with its icon, or its acronym up to 80dp) and has no ×;
 * a pin marks it, beside its icon or acronym (desktop 0.6.5 1d50541d: 12dp, 4dp apart, the tab that much wider).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(tab: Tab, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit, menuItems: () -> List<TabMenuItem>) {
    val c = Zayit.colors
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val label = stripLabel(tab)
    Box {
        Row(
            Modifier
                .then(if (tab.pinned) Modifier.widthIn(min = 40.dp + PinMarkWidth, max = 96.dp + PinMarkWidth) else Modifier.widthIn(min = 96.dp, max = 220.dp))
                .height(36.dp)
                .clip(shape)
                .background(if (selected) c.accentTint else Color.Transparent)
                .border(1.dp, if (selected) c.accent.copy(alpha = 0.5f) else Color.Transparent, shape)
                .combinedClickable(role = Role.Tab, onClickLabel = label, onLongClickLabel = "אפשרויות לשונית", onLongClick = { menu = true }, onClick = onClick)
                .semantics { contentDescription = label }
                .padding(start = 10.dp, end = if (tab.pinned) 10.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (tab.pinned) PinMarkGap else 6.dp, Alignment.CenterHorizontally),
        ) {
            if (tab.pinned) Icon(Icons.Outlined.PushPin, "לשונית מוצמדת", tint = c.textSecondary, modifier = Modifier.size(PinMarkSize))
            if (!tab.pinned || tab.shortTitle.isBlank()) Icon(tabIcon(tab), null, tint = c.text, modifier = Modifier.size(16.dp))
            if (tab.pinned) {
                if (tab.shortTitle.isNotBlank()) Text(tab.shortTitle, fontSize = 14.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(label, fontSize = 14.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClickLabel = "סגור לשונית", onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "סגור לשונית", tint = c.textSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }
        TabContextMenu(menu, { menu = false }, menuItems)
    }
}
