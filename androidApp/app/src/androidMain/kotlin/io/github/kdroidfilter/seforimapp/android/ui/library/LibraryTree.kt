package io.github.kdroidfilter.seforimapp.android.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.components.ZIconButton
import io.github.kdroidfilter.seforimapp.android.ui.reader.PaneSearchField
import io.github.kdroidfilter.seforimapp.android.ui.shell.ShellTexts
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.framework.search.MIN_BOOK_QUERY_LENGTH
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desktop NavigationUseCase.BOOK_SEARCH_LIMIT. */
private const val BOOK_SEARCH_LIMIT = 120

sealed interface TreeNode {
    val depth: Int

    data class CategoryNode(val category: Category, override val depth: Int, val expanded: Boolean) : TreeNode

    data class BookNode(val book: Book, override val depth: Int) : TreeNode
}

private val TreeNode.key: String get() = when (this) {
    is TreeNode.CategoryNode -> "c${category.id}"
    is TreeNode.BookNode -> "b${book.id}"
}

/** A row the tree scrolls to once (a revealed book or category), [stamp] telling repeated requests apart. */
data class TreeReveal(val key: String, val stamp: Long = System.nanoTime())

data class LibraryState(
    val roots: List<Category> = emptyList(),
    val expanded: Set<Long> = emptySet(),
    val children: Map<Long, List<Category>> = emptyMap(),
    val books: Map<Long, List<Book>> = emptyMap(),
    val selectedCategoryId: Long? = null,
    val reveal: TreeReveal? = null,
) {
    val visible: List<TreeNode> get() = flattenTree(roots, expanded, children, books)
}

/** Desktop CategoryBookTreeView order: a category's own books first, then its subcategories. */
private fun flattenTree(roots: List<Category>, expanded: Set<Long>, children: Map<Long, List<Category>>, books: Map<Long, List<Book>>): List<TreeNode> {
    val out = ArrayList<TreeNode>()
    fun walk(cats: List<Category>, depth: Int) {
        for (cat in cats) {
            val open = cat.id in expanded
            out += TreeNode.CategoryNode(cat, depth, open)
            if (open) {
                books[cat.id].orEmpty().forEach { out += TreeNode.BookNode(it, depth + 1) }
                walk(children[cat.id].orEmpty(), depth + 1)
            }
        }
    }
    walk(roots, 0)
    return out
}

/**
 * The tree narrowed to a book search (desktop usecases/BookTreeSearch.kt buildBookFilter): the hits and the categories
 * leading to them in catalog order, all open; [matchIds] in display order, [bestMatchId] the lookup's best hit.
 */
data class BookTreeFilter(
    val query: String,
    val roots: List<Category> = emptyList(),
    val children: Map<Long, List<Category>> = emptyMap(),
    val books: Map<Long, List<Book>> = emptyMap(),
    val matchIds: List<Long> = emptyList(),
    val bestMatchId: Long? = null,
) {
    val nodes: List<TreeNode> get() = flattenTree(roots, (roots + children.values.flatten()).mapTo(HashSet()) { it.id }, children, books)
}

/**
 * The "רשימת הספרים" tree (desktop features/bookcontent/ui/panels/categorytree/CategoryTreePanel.kt and
 * CategoryBookTreeView.kt): categories expand lazily, books open in the reader; [reveal] and [revealCategory] open the
 * path down to a book or a category and scroll it into view.
 */
class LibraryViewModel(private val repository: SeforimRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(roots = repository.getRootCategories().sortedBy { c -> c.order }) }
        }
    }

    fun toggle(category: Category) {
        val s = _state.value
        if (category.id in s.expanded) {
            _state.update { it.copy(expanded = it.expanded - category.id) }
            return
        }
        viewModelScope.launch {
            expand(category.id)
        }
    }

    private suspend fun expand(id: Long) {
        val s = _state.value
        val kids = s.children[id] ?: repository.getCategoryChildren(id).sortedBy { it.order }
        val books = s.books[id] ?: repository.getBooksByCategory(id).sortedBy { it.order }
        _state.update { it.copy(expanded = it.expanded + id, children = it.children + (id to kids), books = it.books + (id to books)) }
    }

    private suspend fun chainOf(categoryId: Long?): List<Category> {
        val chain = ArrayList<Category>()
        var id: Long? = categoryId
        while (id != null) {
            val c = repository.getCategory(id) ?: break
            chain += c
            id = c.parentId
        }
        return chain.asReversed()
    }

    /** Opens the path down to a book so the tree highlights it, as desktop does when a book opens. */
    fun reveal(book: Book) {
        viewModelScope.launch {
            for (c in chainOf(book.categoryId)) if (c.id !in _state.value.expanded) expand(c.id)
            _state.update { it.copy(selectedCategoryId = null, reveal = TreeReveal("b${book.id}")) }
        }
    }

    /** A breadcrumb category: its path opens, the category itself too, and it is selected and scrolled to. */
    fun revealCategory(category: Category) {
        viewModelScope.launch {
            for (c in chainOf(category.id)) if (c.id !in _state.value.expanded) expand(c.id)
            _state.update { it.copy(selectedCategoryId = category.id, reveal = TreeReveal("c${category.id}")) }
        }
    }

    /** Desktop buildBookFilter over the catalog: hits ([hitIds], best first) and the categories leading to them. */
    internal suspend fun filterFor(query: String, hitIds: List<Long>): BookTreeFilter = withContext(Dispatchers.IO) {
        if (hitIds.isEmpty()) return@withContext BookTreeFilter(query)
        val hits = hitIds.distinct().mapNotNull { id -> runCatching { repository.getBookCore(id) }.getOrNull() }
        val categories = HashMap<Long, Category>()
        for (book in hits) for (c in chainOf(book.categoryId)) categories[c.id] = c
        val inTree = hits.filter { it.categoryId in categories }
        val roots = categories.values.filter { it.parentId == null }.sortedBy { it.order }
        val children = categories.values.filter { it.parentId != null }.groupBy { it.parentId!! }.mapValues { (_, l) -> l.sortedBy { it.order } }
        val books = inTree.groupBy { it.categoryId }.mapValues { (_, l) -> l.sortedBy { it.order } }
        val ordered = ArrayList<Long>(inTree.size)
        fun walk(cats: List<Category>) {
            for (cat in cats) {
                books[cat.id]?.forEach { ordered += it.id }
                walk(children[cat.id].orEmpty())
            }
        }
        walk(roots)
        val shown = ordered.toHashSet()
        BookTreeFilter(query, roots, children, books, ordered, hitIds.firstOrNull { it in shown })
    }

    companion object {
        fun factory(repository: SeforimRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(repository) as T
        }
    }
}

/**
 * The book tree. With a [header] (a desktop pane header; it gets the search button to place among its actions) the
 * book search ("חיפוש ספר") opens from that button, as on desktop; without one the search field stays above the tree
 * without raising the keyboard. Two letters narrow the tree to the books found ("לא נמצאו ספרים" when none); Enter
 * opens the active match, ↑/↓ move it. A long press opens a book in a new tab (desktop Ctrl+click).
 */
@Composable
fun LibraryTree(
    vm: LibraryViewModel,
    selectedBookId: Long?,
    onOpenBook: (Book) -> Unit,
    onOpenBookInNewTab: (Book) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable (searchButton: @Composable () -> Unit) -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val app = LocalContext.current.applicationContext as ZayitApplication
    var searchOpen by rememberSaveable { mutableStateOf(header == null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<BookTreeFilter?>(null) }
    var activeId by remember(filter) { mutableStateOf(filter?.bestMatchId) }
    val searching = searchOpen && query.trim().length >= MIN_BOOK_QUERY_LENGTH

    LaunchedEffect(query, searchOpen) {
        val q = query.trim()
        if (!searchOpen || q.length < MIN_BOOK_QUERY_LENGTH) {
            filter = null
            return@LaunchedEffect
        }
        delay(120)
        val hits = withContext(Dispatchers.IO) {
            runCatching { app.search.lookup()?.suggestBooks(q, BOOK_SEARCH_LIMIT) }.getOrNull().orEmpty()
        }
        filter = vm.filterFor(q, hits.map { it.id })
    }

    fun closeSearch() {
        query = ""
        if (header != null) searchOpen = false
    }

    fun openMatch(id: Long?) {
        val book = filter?.books?.values?.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } } ?: return
        closeSearch()
        vm.reveal(book)
        onOpenBook(book)
    }

    Column(modifier) {
        header?.invoke {
            ZIconButton(Icons.Outlined.Search, "חיפוש ספר", { if (searchOpen) closeSearch() else searchOpen = true }, tint = Zayit.colors.textSecondary, selected = searchOpen)
        }
        if (searchOpen) {
            Column(
                Modifier.onPreviewKeyEvent { e ->
                    val ids = filter?.matchIds.orEmpty()
                    if (e.type != KeyEventType.KeyDown || ids.isEmpty()) return@onPreviewKeyEvent false
                    val delta = when (e.key) {
                        Key.DirectionDown -> 1
                        Key.DirectionUp -> -1
                        else -> return@onPreviewKeyEvent false
                    }
                    activeId = ids[(ids.indexOf(activeId) + delta).coerceIn(0, ids.lastIndex)]
                    true
                },
            ) {
                PaneSearchField(
                    query,
                    "חיפוש ספר",
                    onQuery = { query = it },
                    onClose = if (header != null) ({ closeSearch() }) else null,
                    onEnter = { if (filter?.query == query.trim()) openMatch(activeId) },
                    autoFocus = header != null,
                )
            }
        }
        val result = filter
        when {
            searching && result == null -> Unit
            searching && result!!.matchIds.isEmpty() -> Text(ShellTexts.NO_BOOKS_FOUND, color = Zayit.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            searching -> TreeList(
                nodes = result!!.nodes,
                selectedBookId = activeId,
                selectedCategoryId = null,
                reveal = activeId?.let { TreeReveal("b$it", 0L) },
                onCategory = {},
                onOpenBook = { openMatch(it.id) },
                onOpenBookInNewTab = { closeSearch(); onOpenBookInNewTab(it) },
                modifier = Modifier.weight(1f),
            )
            else -> TreeList(
                nodes = state.visible,
                selectedBookId = selectedBookId,
                selectedCategoryId = state.selectedCategoryId,
                reveal = state.reveal,
                onCategory = { vm.toggle(it) },
                onOpenBook = onOpenBook,
                onOpenBookInNewTab = onOpenBookInNewTab,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TreeList(
    nodes: List<TreeNode>,
    selectedBookId: Long?,
    selectedCategoryId: Long?,
    reveal: TreeReveal?,
    onCategory: (Category) -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenBookInNewTab: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    // Scrolls a revealed row into view once its path is open (desktop revealItem)
    var revealed by remember { mutableStateOf<TreeReveal?>(null) }
    LaunchedEffect(reveal, nodes.size) {
        if (reveal == null || reveal == revealed) return@LaunchedEffect
        val idx = nodes.indexOfFirst { it.key == reveal.key }
        if (idx < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo
        val shown = visible.any { it.index == idx } && visible.firstOrNull()?.index != idx && visible.lastOrNull()?.index != idx
        if (!shown) list.scrollToItem((idx - 3).coerceAtLeast(0))
        revealed = reveal
    }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
        items(nodes, key = { it.key }) { node ->
            when (node) {
                is TreeNode.CategoryNode -> TreeRow(
                    depth = node.depth,
                    text = node.category.title,
                    folder = true,
                    expanded = node.expanded,
                    selected = node.category.id == selectedCategoryId,
                    onClick = { onCategory(node.category) },
                    onLongClick = { onCategory(node.category) },
                )
                is TreeNode.BookNode -> TreeRow(
                    depth = node.depth,
                    text = node.book.title,
                    folder = false,
                    expanded = false,
                    selected = node.book.id == selectedBookId,
                    onClick = { onOpenBook(node.book) },
                    onLongClick = { onOpenBookInNewTab(node.book) },
                )
            }
        }
    }
}

@Composable
private fun TreeRow(depth: Int, text: String, folder: Boolean, expanded: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) c.selectedRow else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = (8 + depth * 16).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (folder) {
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowLeft,
                null,
                tint = c.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Icon(Icons.Outlined.Folder, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.width(16.dp))
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Text(
            text,
            fontSize = 15.sp,
            color = c.text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
