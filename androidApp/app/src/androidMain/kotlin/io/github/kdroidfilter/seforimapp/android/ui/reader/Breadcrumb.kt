package io.github.kdroidfilter.seforimapp.android.ui.reader

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.guardedWrite
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.shell.FavoriteFolderChooser
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.shell.bookShareLink
import io.github.kdroidfilter.seforimapp.android.ui.shell.copyLink
import io.github.kdroidfilter.seforimapp.android.ui.shell.shareLink
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A segment of the breadcrumb (desktop BreadcrumbNode). */
sealed interface CrumbNode {
    val key: String
    val title: String

    data class CategoryNode(val category: Category) : CrumbNode {
        override val key get() = "c${category.id}"
        override val title get() = category.title
    }

    data class BookNode(val book: Book) : CrumbNode {
        override val key get() = "b${book.id}"
        override val title get() = book.title
    }

    data class TocNode(val entry: TocEntry) : CrumbNode {
        override val key get() = "t${entry.id}"
        override val title get() = entry.text
    }
}

/**
 * Categories from the root, the book, its TOC path to the place on screen (the selected line while it shows, see
 * [ReaderUiState.placeLineId]), a first heading repeating the title dropped.
 */
fun crumbPath(state: ReaderUiState): List<CrumbNode> {
    val book = state.book ?: return emptyList()
    return buildList {
        state.categories.forEach { add(CrumbNode.CategoryNode(it)) }
        add(CrumbNode.BookNode(book))
        val path = state.placeBreadcrumb
        val toc = if (path.firstOrNull()?.text == book.title) path.drop(1) else path
        toc.forEach { add(CrumbNode.TocNode(it)) }
    }
}

/** What the breadcrumb navigates to: a category shown in the tree, another book, a heading's line. */
class CrumbNavigation(val revealCategory: (Category) -> Unit, val openBook: (Book) -> Unit, val openLine: (Long) -> Unit)

/** Desktop CatalogBreadcrumbChildrenProvider: a category's books then subcategories, a book's TOC, an entry's children. */
suspend fun crumbChildren(app: ZayitApplication, vm: ReaderViewModel, node: CrumbNode): List<CrumbNode> = runCatching {
    val repo = app.libraryStore.repository()
    when (node) {
        is CrumbNode.CategoryNode ->
            repo.getBooksByCategory(node.category.id).sortedBy { it.order }.map { CrumbNode.BookNode(it) } +
                repo.getCategoryChildren(node.category.id).sortedBy { it.order }.map { CrumbNode.CategoryNode(it) }
        is CrumbNode.BookNode -> {
            val roots = repo.getBookRootToc(node.book.id)
            val titleRoot = roots.firstOrNull()?.takeIf { it.text == node.book.title && it.hasChildren }
            (if (titleRoot != null) vm.tocChildrenOf(titleRoot.id) + roots.drop(1) else roots).map { CrumbNode.TocNode(it) }
        }
        is CrumbNode.TocNode -> if (node.entry.hasChildren) vm.tocChildrenOf(node.entry.id).map { CrumbNode.TocNode(it) } else emptyList()
    }
}.getOrElse { emptyList() }

private fun go(node: CrumbNode, nav: CrumbNavigation) {
    when (node) {
        is CrumbNode.CategoryNode -> nav.revealCategory(node.category)
        is CrumbNode.BookNode -> nav.openBook(node.book)
        is CrumbNode.TocNode -> node.entry.lineId?.let(nav.openLine)
    }
}

/**
 * Tablet: desktop BreadcrumbView + BreadcrumbActionsView under the text. A tap on a segment opens a menu of its
 * children (its own row first: tapping it goes there, desktop's double click); the actions copy the place's link,
 * share it, and star it (a long press files it in a folder).
 */
@Composable
fun BreadcrumbBar(vm: ReaderViewModel, state: ReaderUiState, nav: CrumbNavigation, modifier: Modifier = Modifier) {
    val c = Zayit.colors
    val path = remember(state.book, state.categories, state.placeBreadcrumb) { crumbPath(state) }
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            path.forEachIndexed { i, node ->
                if (i > 0) Text(" > ", fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.padding(horizontal = 2.dp))
                CrumbSegment(vm, node, last = i == path.lastIndex, nav)
            }
        }
        BreadcrumbActions(state)
    }
}

@Composable
private fun CrumbSegment(vm: ReaderViewModel, node: CrumbNode, last: Boolean, nav: CrumbNavigation) {
    val c = Zayit.colors
    val app = LocalContext.current.applicationContext as ZayitApplication
    var open by remember { mutableStateOf(false) }
    val children by produceState<List<CrumbNode>?>(null, open, node.key) { if (open) value = crumbChildren(app, vm, node) }
    Box {
        Text(
            node.title,
            fontSize = 12.sp,
            fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
            color = c.text,
            maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).combinedClickable(onClick = { open = true }, onLongClick = { go(node, nav) }).heightIn(min = 32.dp).padding(horizontal = 6.dp, vertical = 8.dp),
        )
        DropdownMenu(open, { open = false }, containerColor = c.panel, modifier = Modifier.heightIn(max = 420.dp)) {
            DropdownMenuItem(text = { Text(node.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = {
                open = false
                go(node, nav)
            })
            children?.forEach { child ->
                DropdownMenuItem(text = { Text(child.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = {
                    open = false
                    go(child, nav)
                })
            }
        }
    }
}

/**
 * The place the breadcrumb names: the line on screen (the primary line while it shows), else its TOC entry's; titled
 * "<book> - <heading>". The links and the star beside the path name what the path names.
 */
private data class Place(val bookId: Long, val tocId: Long?, val lineId: Long?, val title: String)

private fun placeOf(state: ReaderUiState): Place? {
    val book = state.book ?: return null
    val toc = state.placeBreadcrumb.lastOrNull()
    val lineId = state.placeLineId ?: state.primaryLineId ?: toc?.lineId
    val title = toc?.text?.takeIf { it.isNotBlank() && it != book.title }?.let { "${book.title} - $it" } ?: book.title
    return Place(book.id, toc?.id, lineId, title)
}

/** Desktop BreadcrumbActionsView: copy link (✓ for a moment), share, the favourite star with its folders. */
@Composable
fun BreadcrumbActions(state: ReaderUiState) {
    val c = Zayit.colors
    val context = LocalContext.current
    val place = placeOf(state) ?: return
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ZIconButton(if (copied) Icons.Outlined.Check else Icons.Outlined.Link, "העתק קישור", {
            copyLink(context, bookShareLink(place.bookId, place.lineId))
            copied = true
        }, tint = c.textSecondary)
        ZIconButton(Icons.Outlined.Share, "שתף", { shareLink(context, place.title, bookShareLink(place.bookId, place.lineId)) }, tint = c.textSecondary)
        FavoriteStar(place)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteStar(place: Place) {
    val c = Zayit.colors
    val app = LocalContext.current.applicationContext as ZayitApplication
    val store = app.userData.favorites
    val scope = rememberCoroutineScope()
    val revision by store.revision.collectAsState()
    val key = store.keyFor(place.bookId, place.tocId)
    val favorite by produceState(false, key, revision) { value = runCatching { store.isFavorite(key) }.getOrDefault(false) }
    var folders by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).combinedClickable(
                onClickLabel = if (favorite) "עריכת מועדף" else "הוסף למועדפים",
                onClick = {
                    scope.launch {
                        guardedWrite("Favorite") { if (favorite) store.remove(key) else store.add(place.bookId, place.title, System.currentTimeMillis(), place.tocId, place.lineId) }
                    }
                },
                onLongClick = {
                    // Desktop's secondary click: starred first, then its folder chooser
                    scope.launch {
                        if (favorite || guardedWrite("Favorite") { store.add(place.bookId, place.title, System.currentTimeMillis(), place.tocId, place.lineId) }) folders = true
                    }
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, if (favorite) "עריכת מועדף" else "הוסף למועדפים", tint = if (favorite) c.accent else c.textSecondary, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(folders, { folders = false }, containerColor = c.panel) {
            FavoriteFolderChooser(key, onDismiss = { folders = false }, showTitle = true)
        }
    }
}

/**
 * Phone: the breadcrumb as a sheet (the top bar's subtitle opens it): the path as an indented list, each level
 * opening its children, and the three actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadcrumbSheet(vm: ReaderViewModel, state: ReaderUiState, nav: CrumbNavigation, onDismiss: () -> Unit) {
    val c = Zayit.colors
    val app = LocalContext.current.applicationContext as ZayitApplication
    val path = remember(state.book, state.categories, state.placeBreadcrumb) { crumbPath(state) }
    var expanded by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.panel) {
        WindowBackScope {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) { BreadcrumbActions(state) }
                path.forEachIndexed { i, node ->
                    val children by produceState<List<CrumbNode>?>(null, expanded == node.key) { if (expanded == node.key) value = crumbChildren(app, vm, node) }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).combinedClickableCompat(
                            onClick = { expanded = if (expanded == node.key) null else node.key },
                            onLongClick = { onDismiss(); go(node, nav) },
                        ).padding(start = (16 + i * 12).dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(node.title, fontSize = 15.sp, fontWeight = if (i == path.lastIndex) FontWeight.Bold else FontWeight.Normal, color = c.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        // An icon, not a "‹" glyph: alone in an RTL paragraph the glyph is mirrored and pointed back.
                        // Drilling in goes to the left in this RTL app, as the desktop tree's chevrons and the separators.
                        androidx.compose.material3.Icon(
                            if (expanded == node.key) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronLeft,
                            null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (expanded == node.key) {
                        val list = children
                        Column(Modifier.padding(start = (28 + i * 12).dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).combinedClickableCompat(onClick = { onDismiss(); go(node, nav) }), verticalAlignment = Alignment.CenterVertically) {
                                Text("עבור אל " + node.title, color = c.link, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            list?.forEach { child ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).combinedClickableCompat(onClick = { onDismiss(); go(child, nav) }), verticalAlignment = Alignment.CenterVertically) {
                                    Text(child.title, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phone: the favourite of the place ([favoriteKey], titled [title]) edited from the reader's "עוד" menu, desktop's
 * "עריכת מועדף": its folder (desktop's star popup) or its removal, said aloud as the phone shows no star.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteEditSheet(favoriteKey: String, title: String, onDismiss: () -> Unit) {
    val c = Zayit.colors
    val context = LocalContext.current
    val app = context.applicationContext as ZayitApplication
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panel) {
        WindowBackScope {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.Star, null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FavoriteFolderChooser(favoriteKey, onDismiss = onDismiss, modifier = Modifier.padding(horizontal = 10.dp))
                SheetRow(Icons.Outlined.StarOutline, "הסר מהמועדפים") {
                    // The app's scope: the sheet leaves the composition at the next frame, its own scope with it, and
                    // the toast is the phone's only sign of the removal
                    app.appScope.launch {
                        if (guardedWrite("Favorite removal") { app.userData.favorites.remove(favoriteKey) }) {
                            withContext(Dispatchers.Main) { Toast.makeText(app, "הוסר מהמועדפים", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    onDismiss()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** Copies [text] to the clipboard (Android 13 shows its own confirmation). */
fun copyText(context: Context, text: String) {
    copyLink(context, text)
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "הועתק", Toast.LENGTH_SHORT).show()
}
