package io.github.kdroidfilter.seforimapp.android.ui.home

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.CatalogAccess
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.ToolKind
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.FullWidthSection
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsGrid
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.HomeWidgetsOverlay
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.decodeLayout
import io.github.kdroidfilter.seforimapp.android.ui.home.widgets.rememberHomeWidgetsState
import io.github.kdroidfilter.seforimapp.android.ui.library.LibraryTree
import io.github.kdroidfilter.seforimapp.android.ui.library.LibraryViewModel
import io.github.kdroidfilter.seforimapp.android.ui.reader.PaneHeader
import io.github.kdroidfilter.seforimapp.android.ui.reader.ReaderRailButton
import io.github.kdroidfilter.seforimapp.android.ui.reader.SplitHandle
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalKeyShortcuts
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalTabSession
import io.github.kdroidfilter.seforimapp.android.ui.shell.isTabInFront
import io.github.kdroidfilter.seforimapp.android.ui.theme.AccentColor
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.tools.ToolPanelHost
import io.github.kdroidfilter.seforimapp.android.ui.tools.opensAsPanel
import io.github.kdroidfilter.seforimapp.icons.Library as LibraryIcon
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The home page, desktop 0.6.10 features/bookcontent/ui/panels/bookcontent/views/HomeView.kt: the wallpaper under its
 * two blending layers, then the page: the catalog row (components/CatalogRow.kt, its dropdowns from catalog.pb as
 * core/catalog/CatalogAccess.kt shapes them), the logo, the search bar ([HomeSearchBar]) and the editable widget grid
 * (features/home/widgets): one column (two on a phone on its side) below 670 dp, desktop's 20-column grid above.
 */
@Composable
fun HomeScreen(
    onOpenBook: (Book) -> Unit,
    onSearch: (String) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onOpenBookAt: (Book, Long) -> Unit = { book, _ -> onOpenBook(book) },
) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val repo = remember { app.libraryStore.repository() }
    val showWallpaper by app.prefs.showWallpaper.collectAsState()
    val c = Zayit.colors
    // On a tablet the small tools open over the Home as a panel at their desktop window's size
    var panel by rememberSaveable { mutableStateOf<ToolKind?>(null) }
    val state = rememberHomeWidgetsState(openTool = { tool -> opensAsPanel(tool, wide).also { if (it) panel = tool } })
    val layoutRaw by state.layout.store.homeWidgetsLayoutFlow.collectAsState()
    val widgets = remember(layoutRaw) { decodeLayout(layoutRaw) }
    val scrollState = rememberScrollState()
    // Where the search bar stands on the page, however far it is scrolled (its place in the window plus the scroll):
    // only what is above it moves it, as the catalog row does when it loads after the bar was lifted over the keyboard
    var barOnPage by remember { mutableIntStateOf(0) }
    val barMoveSlop = with(LocalDensity.current) { 4.dp.roundToPx() }
    // Parked (a book opened in its place, TabHost keeps it): back at its top, its bar empty, out of the edit mode, as
    // the new Home Back or "בית" used to build, while it keeps its widgets built and their values
    val parked = LocalTabSession.current?.parked == true
    var searchResets by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(parked) {
        if (!parked) return@LaunchedEffect
        scrollState.scrollTo(0)
        state.editingWidgets = false
        state.selectedWidget = null
        searchResets++
    }
    // Back leaves the edit mode first (desktop: Escape)
    BackHandler(enabled = state.editingWidgets) {
        state.editingWidgets = false
        state.selectedWidget = null
    }

    // Desktop BookContentPanel's home card: the panel's colour under the wallpaper. A focus target of its own: a focus
    // Android or Compose hands out by itself (no touch asked for it) lands on the page, not on its first text field (the
    // search bar's, a widget's), whose soft keyboard would cover a phone's page
    BoxWithConstraints(modifier.fillMaxSize().background(c.panel).focusTarget()) {
        if (showWallpaper) Wallpaper()
        val narrow = maxWidth < 360.dp
        val pageWidth = maxWidth
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            HomeWidgetsGrid(
                state = state,
                widgets = widgets,
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                // 14 dp on a tablet too: room for the grid's "−" badges and resize handles, which stick out of a widget
                contentPadding = PaddingValues(horizontal = if (narrow) 10.dp else 14.dp),
            ) {
                // Scrolls with the page instead of floating over the widgets
                FullWidthSection(gapAfter = 4.dp) { CatalogRow(app, repo, onOpenBook, onOpenBookAt) }
                FullWidthSection(gapAfter = 4.dp) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // Desktop: half the page, 320 to 450 dp; a phone keeps its sides
                        val logoWidth = (pageWidth * 0.5f).coerceIn(minOf(320.dp, pageWidth * 0.78f), 450.dp)
                        LogoImage(Modifier.width(logoWidth).aspectRatio(1556f / 715f))
                    }
                }
                FullWidthSection(gapAfter = 4.dp) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        HomeSearchBar(
                            wide,
                            { book, line -> if (line == null) onOpenBook(book) else onOpenBookAt(book, line) },
                            Modifier.widthIn(max = 600.dp).fillMaxWidth().onGloballyPositioned { coordinates ->
                                val y = coordinates.positionInWindow().y.roundToInt() + scrollState.value
                                if (abs(y - barOnPage) > barMoveSlop) barOnPage = y
                            },
                            liftKey = barOnPage,
                            resetKey = searchResets,
                        )
                    }
                }
            }
            HomeWidgetsOverlay(state, widgets)
        }
    }
    ToolPanelHost(panel) { panel = null }
}

/**
 * A Home tab on a tablet, as desktop 0.6.10 core/presentation/window/WindowBody.kt lays one out: the start bar with its
 * "ספרים" button (features/bookcontent/ui/components/VerticalBars.kt StartVerticalBar), the book tree
 * (panels/categorytree/CategoryTreePanel.kt: hidden from its header, resized by its divider) and the home card. The
 * tree's visibility is the tab's (desktop BookContentPersistedState.isBookTreeVisible), kept with the session; Ctrl+B
 * toggles it (desktop handleBookShortcut).
 */
@Composable
fun WideHome(
    tabId: String,
    appVm: AppViewModel,
    library: LibraryViewModel,
    onOpenBook: (Book) -> Unit,
    onOpenBookInNewTab: (Book) -> Unit,
    content: @Composable () -> Unit,
) {
    var treeVisible by rememberSaveable(tabId) { mutableStateOf(appVm.isBookTreeVisible(tabId)) }
    // Back from a book opened in its place (TabHost kept this Home), the tab's state starts anew: it is told again
    val front = isTabInFront()
    LaunchedEffect(front) { if (front && treeVisible != appVm.isBookTreeVisible(tabId)) appVm.setBookTreeVisible(tabId, treeVisible) }
    var treeW by rememberSaveable { mutableFloatStateOf(280f) }
    val toggleTree = {
        treeVisible = !treeVisible
        appVm.setBookTreeVisible(tabId, treeVisible)
    }
    val treeKey: (KeyEvent) -> Boolean = { e ->
        val ctrl = e.isCtrlPressed || e.isMetaPressed
        (e.type == KeyEventType.KeyDown && ctrl && !e.isShiftPressed && e.key == Key.B).also { if (it) toggleTree() }
    }
    // Ctrl+B works when nothing has the focus too, in the front tab: the keys no view used reach it (desktop 0.6.5
    // f96e98f3, the window's readerShortcuts, which a Home tab sets as a book tab does)
    val shortcuts = LocalKeyShortcuts.current
    val currentTreeKey by rememberUpdatedState(treeKey)
    DisposableEffect(shortcuts, front) {
        val fallback: (android.view.KeyEvent) -> Boolean = { native -> currentTreeKey(KeyEvent(native)) }
        if (shortcuts != null && front) shortcuts.fallback = fallback
        onDispose { if (shortcuts?.fallback === fallback) shortcuts.fallback = null }
    }
    // RTL row: the bar and the book list come first so they sit on the right, as on desktop
    Row(
        Modifier.fillMaxSize().padding(6.dp).onPreviewKeyEvent(treeKey),
    ) {
        Column(Modifier.fillMaxHeight().padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ReaderRailButton(LibraryIcon, "ספרים", selected = treeVisible, onClick = toggleTree)
        }
        if (treeVisible) {
            Island(Modifier.width(treeW.dp).fillMaxHeight()) {
                LibraryTree(
                    library,
                    null,
                    onOpenBook = onOpenBook,
                    onOpenBookInNewTab = onOpenBookInNewTab,
                    modifier = Modifier.fillMaxSize(),
                    header = { search -> PaneHeader("רשימת הספרים", toggleTree) { search() } },
                )
            }
            SplitHandle(vertical = false) { d -> treeW = (treeW - d).coerceIn(160f, 420f) }
        }
        Island(Modifier.weight(1f).fillMaxSize()) { content() }
    }
}

/**
 * Desktop HomeView's wallpaper layers: the picture (small, medium or large by the page's width in pixels), a
 * horizontal vignette of the panel's colour, and a radial gradient of it centred at 65% of the height, so the widgets
 * read on a clean centre.
 */
@Composable
private fun Wallpaper() {
    val c = Zayit.colors
    val panelBackground = c.panel
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val wallpaper =
            if (c.isDark) {
                when {
                    widthPx <= 960 -> R.drawable.homepage_wallpaper_dark_small
                    widthPx <= 1440 -> R.drawable.homepage_wallpaper_dark_medium
                    else -> R.drawable.homepage_wallpaper_dark_large
                }
            } else {
                when {
                    widthPx <= 960 -> R.drawable.homepage_wallpaper_small
                    widthPx <= 1440 -> R.drawable.homepage_wallpaper_medium
                    else -> R.drawable.homepage_wallpaper_large
                }
            }
        // Layer 1: Wallpaper background, decoded off the UI thread (a new tab's first frame is its opening animation's)
        rememberDecodedImage(wallpaper)?.let { image ->
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // Layer 2: Smooth horizontal vignette — image blends into background atmosphere
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops =
                        arrayOf(
                            0.00f to panelBackground.copy(alpha = 0.10f),
                            0.04f to panelBackground.copy(alpha = 0.20f),
                            0.08f to panelBackground.copy(alpha = 0.35f),
                            0.13f to panelBackground.copy(alpha = 0.50f),
                            0.20f to panelBackground.copy(alpha = 0.65f),
                            0.28f to panelBackground.copy(alpha = 0.78f),
                            0.38f to panelBackground.copy(alpha = 0.85f),
                            0.50f to panelBackground.copy(alpha = 0.78f),
                            0.62f to panelBackground.copy(alpha = 0.85f),
                            0.72f to panelBackground.copy(alpha = 0.78f),
                            0.80f to panelBackground.copy(alpha = 0.65f),
                            0.87f to panelBackground.copy(alpha = 0.50f),
                            0.92f to panelBackground.copy(alpha = 0.35f),
                            0.96f to panelBackground.copy(alpha = 0.20f),
                            1.00f to panelBackground.copy(alpha = 0.10f),
                        ),
                ),
            ),
        )
        // Layer 3: Radial gradient — bottom-center opaque, edges visible all around
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colorStops =
                        arrayOf(
                            0.00f to panelBackground,
                            0.35f to panelBackground,
                            0.55f to panelBackground.copy(alpha = 0.75f),
                            0.75f to panelBackground.copy(alpha = 0.40f),
                            1.00f to Color.Transparent,
                        ),
                    center = Offset(x = constraints.maxWidth / 2f, y = constraints.maxHeight * 0.65f),
                    radius = constraints.maxWidth * 0.55f,
                ),
            ),
        )
    }
}

/** Desktop rememberDecodedImage: [res] decoded on the IO dispatcher, once for the app ([cache]); null until then. */
@Composable
private fun rememberDecodedImage(
    @DrawableRes res: Int,
    cache: LruCache<Int, ImageBitmap> = decodedImages,
): ImageBitmap? {
    val resources = LocalContext.current.resources
    return produceState(cache[res], res) {
        value = cache[res]
            ?: withContext(Dispatchers.IO) { BitmapFactory.decodeResource(resources, res)?.asImageBitmap() }
                ?.also { cache.put(res, it) }
    }.value
}

/** Desktop keeps every wallpaper it decoded; a phone keeps the last two (a theme switch and back). */
private val decodedImages = LruCache<Int, ImageBitmap>(2)

/** The name's lettering, as on the launcher icon: Frank Ruhl Libre Black (its wght axis at 900). */
private val LogoFont = FontFamily(Font(R.font.frankruhllibre, FontWeight.Black))

/**
 * Desktop LogoImage's place and colours with this app's own logo: Zayit's author asked forks not to use Zayit's name
 * or its olive branch, so the Home shows the name פרדס, in the launcher icon's letters, painted light gold (or the
 * accent, in dark mode) as desktop paints its lettering. Text: nothing to decode, at any size.
 */
@Composable
private fun LogoImage(modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val tint = if (c.isDark) c.accent else AccentColor.Gold.light
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = "פרדס" }, contentAlignment = Alignment.Center) {
        Text(
            "פרדס",
            color = tint,
            fontFamily = LogoFont,
            fontSize = with(LocalDensity.current) { (maxHeight * 0.62f).toSp() },
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Desktop CatalogPresets.Dropdowns of the catalog row, in its order. */
private sealed interface Catalog {
    /** A category's books under the category of [labelId]'s title, by section ([sectionIds]) or alone. */
    data class Categories(val labelId: Long, val sectionIds: List<Long>) : Catalog

    /** A book's TOC quick links (the Tur's four parts): label and the line each opens on. */
    data class TocLinks(val bookId: Long, val links: List<Pair<String, Long>>) : Catalog
}

private val catalogs =
    listOf(
        Catalog.Categories(1, listOf(2, 3, 4)),
        Catalog.Categories(5, listOf(6, 7, 8, 9, 10, 11)),
        Catalog.Categories(13, listOf(14, 15, 16, 17, 18, 19)),
        Catalog.Categories(20, listOf(21, 22, 23, 24, 25)),
        Catalog.Categories(45, listOf(46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 59, 56, 57, 58, 60)),
        Catalog.TocLinks(380, listOf("אורח חיים" to 252_607, "יורה דעה" to 254_014, "אבן העזר" to 254_828, "חושן משפט" to 255_198)),
        Catalog.Categories(62, emptyList()),
    )

/** A dropdown entry: a section header, or a book (at a line for a quick link). */
private data class CatalogItem(val label: String, val bookId: Long? = null, val lineId: Long? = null)

private data class CatalogMenu(val label: String, val items: List<CatalogItem>)

/** The catalog row's dropdowns as last built, for the catalog they were built from ([CatalogAccess.load] keeps it). */
private class BuiltCatalog(val access: CatalogAccess, val menus: List<CatalogMenu>)

@Volatile
private var builtCatalog: BuiltCatalog? = null

@Composable
private fun CatalogRow(
    app: ZayitApplication,
    repo: SeforimRepository,
    onOpenBook: (Book) -> Unit,
    onOpenBookAt: (Book, Long) -> Unit,
) {
    val c = Zayit.colors
    val scope = rememberCoroutineScope()
    // The dropdowns built last, shown at once by a Home built again and checked against the catalog meanwhile; null
    // until known (a cold start)
    val loaded by produceState(builtCatalog?.menus) {
        value =
            runCatching {
                // The library folder and the catalog's stamp are file system calls, and the dropdowns' few hundred
                // entries are built with them: none of it on the UI thread, busy with the Home's first frames
                withContext(Dispatchers.IO) {
                    val access = CatalogAccess.load(app.libraryStore)
                    builtCatalog?.takeIf { it.access === access }?.menus
                        ?: catalogs.mapNotNull { catalogMenu(access, it) }.also { builtCatalog = BuiltCatalog(access, it) }
                }
            }.onFailure { Log.w("Zayit", "catalog", it) }.getOrNull() ?: value.orEmpty()
    }
    val menus = loaded.orEmpty()
    // Until a cold start's dropdowns load, the row keeps the height it had when last laid out at this width, so what is
    // under it doesn't jump down then; a library with none of the catalog's books keeps no room for it
    val reserved = remember { app.prefs.catalogRow }
    if (loaded?.isEmpty() == true) SideEffect { app.prefs.catalogRow = null }
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .layout { measurable, constraints ->
                val min = reserved?.takeIf { loaded == null && it.first == constraints.maxWidth }?.second?.coerceAtMost(constraints.maxHeight) ?: 0
                val placeable = measurable.measure(constraints.copy(minHeight = maxOf(constraints.minHeight, min)))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }.onSizeChanged { if (menus.isNotEmpty()) app.prefs.catalogRow = it.width to it.height },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (menu in menus) {
            var open by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier
                        .widthIn(max = 130.dp)
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.panel.copy(alpha = 0.95f))
                        .border(1.dp, c.panelBorder, RoundedCornerShape(8.dp))
                        .clickable(role = Role.DropdownList) { open = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(menu.label, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(open && isTabInFront(), { open = false }, containerColor = c.panel, modifier = Modifier.heightIn(max = 420.dp)) {
                    menu.items.forEachIndexed { i, item ->
                        if (item.bookId == null) {
                            if (i > 0) HorizontalDivider(color = c.divider)
                            Text(item.label, color = c.textDisabled, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        } else {
                            DropdownMenuItem(
                                text = { Text(item.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    open = false
                                    scope.launch {
                                        val book = runCatching { repo.getBookCore(item.bookId) }.getOrNull() ?: return@launch
                                        if (item.lineId != null) onOpenBookAt(book, item.lineId) else onOpenBook(book)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A dropdown as desktop's CatalogDropdown fills it; null when the library has none of its books. */
private fun catalogMenu(
    access: CatalogAccess,
    catalog: Catalog,
): CatalogMenu? =
    when (catalog) {
        is Catalog.TocLinks -> {
            val title = access.bookTitle(catalog.bookId)
            title?.let { CatalogMenu(it, catalog.links.map { (label, lineId) -> CatalogItem(label, catalog.bookId, lineId) }) }
        }
        is Catalog.Categories -> {
            val label = access.categoryTitle(catalog.labelId)
            if (catalog.sectionIds.isEmpty()) {
                val books = access.booksFor(catalog.labelId)
                if (label == null || books.isEmpty()) null else CatalogMenu(label, books.map { CatalogItem(it.title, it.id) })
            } else {
                val sections =
                    catalog.sectionIds.mapNotNull { id ->
                        val title = access.categoryTitle(id) ?: return@mapNotNull null
                        val books = access.booksFor(id)
                        if (books.isEmpty()) null else listOf(CatalogItem(title)) + books.map { CatalogItem(it.title, it.id) }
                    }
                if (label == null || sections.isEmpty()) null else CatalogMenu(label, sections.flatten())
            }
        }
    }
