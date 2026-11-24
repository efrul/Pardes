package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforimapp.core.presentation.components.ChevronIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.CountBadge
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableRow
import io.github.kdroidfilter.seforimapp.core.presentation.components.SharedIcon
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.TreeKeyRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.TreeNode
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.rememberTreeKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.revealItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.treeKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.treeSelectedKey
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.icons.Book_2
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@Stable
private data class TreeItem(
    val id: String,
    val level: Int,
    val content: @Composable () -> Unit,
    // Pardes: what the tree's keys need of the row (request 47 of stage 3)
    val folder: Boolean = false,
    val open: Boolean = false,
    val click: () -> Unit = {},
)

@OptIn(FlowPreview::class)
@Composable
fun CategoryBookTreeView(
    navigationState: NavigationState,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit,
    onScroll: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    // Optional search integration: counts + selection override
    categoryCounts: Map<Long, Int> = emptyMap(),
    bookCounts: Map<Long, Int> = emptyMap(),
    selectedCategoryIdOverride: Long? = null,
    selectedBookIdOverride: Long? = null,
    showCounts: Boolean = false,
    booksForCategoryOverride: Map<Long, List<Book>> = emptyMap(),
    onCategoryRevealComplete: () -> Unit = {},
    // Pardes: Escape, and Enter on a book, give the focus back to the text (request 47 of stage 3)
    onLeave: (() -> Unit)? = null,
) {
    /* ---------------------------------------------------------------------
     * Build the flat hierarchical list to display.
     * -------------------------------------------------------------------- */

    // Indexed once per catalog: each expanded category used to scan every book of the library
    val booksByCategory = remember(navigationState.booksInCategory) { navigationState.booksInCategory.groupBy { it.categoryId } }
    // Pardes: the tree's keys, as code editors have them, their row shown as selected while it has the focus (request 47
    // of stage 3)
    val keys = rememberTreeKeys(navigationState.treeSelectedKey(selectedBookIdOverride, selectedCategoryIdOverride))
    val focusedId = keys.shown as? String
    val treeItems =
        remember(
            navigationState.rootCategories,
            navigationState.expandedCategories,
            navigationState.categoryChildren,
            navigationState.booksInCategory,
            navigationState.selectedCategory,
            navigationState.selectedBook,
            // Rebuild when search-related inputs change
            showCounts,
            categoryCounts,
            bookCounts,
            selectedCategoryIdOverride,
            selectedBookIdOverride,
            booksForCategoryOverride,
            focusedId,
        ) {
            buildTreeItems(
                rootCategories = navigationState.rootCategories,
                expandedCategories = navigationState.expandedCategories,
                categoryChildren = navigationState.categoryChildren,
                booksByCategory = booksByCategory,
                selectedCategory = navigationState.selectedCategory,
                selectedBook = navigationState.selectedBook,
                onCategoryClick = onCategoryClick,
                onBookClick = onBookClick,
                categoryCounts = categoryCounts,
                bookCounts = bookCounts,
                selectedCategoryIdOverride = selectedCategoryIdOverride,
                selectedBookIdOverride = selectedBookIdOverride,
                showCounts = showCounts,
                booksForCategoryOverride = booksForCategoryOverride,
                focusedId = focusedId,
            )
        }

    /* ---------------------------------------------------------------------
     * Restore the LazyListState.
     * -------------------------------------------------------------------- */
    val listState: LazyListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = navigationState.scrollIndex,
            initialFirstVisibleItemScrollOffset = navigationState.scrollOffset,
        )

    var hasRestored by remember { mutableStateOf(false) }
    val currentOnScroll by rememberUpdatedState(onScroll)

    // ---------------- 1. Restore when the list is truly ready --------
    LaunchedEffect(treeItems.size) {
        if (treeItems.isNotEmpty() && !hasRestored) {
            val safeIndex =
                navigationState.scrollIndex
                    .coerceIn(0, treeItems.lastIndex)
            listState.scrollToItem(safeIndex, navigationState.scrollOffset)
            hasRestored = true // ← wait until finished before listening to scrolls
        }
    }

    // ---------------- 2. Propagate scrolls *after* restoration ----------
    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.distinctUntilChanged()
                .debounce(250)
                .collect { (i, o) -> currentOnScroll(i, o) }
        }
    }

    // 3) A category revealed from elsewhere (the breadcrumb) is scrolled into view, then the request consumed
    val currentOnCategoryRevealComplete by rememberUpdatedState(onCategoryRevealComplete)
    LaunchedEffect(navigationState.categoryReveal, treeItems, hasRestored) {
        val reveal = navigationState.categoryReveal ?: return@LaunchedEffect
        if (!hasRestored) return@LaunchedEffect
        val idx = treeItems.indexOfFirst { it.id == "category_$reveal" }
        if (idx >= 0) listState.revealItem(idx)
        // Consumed even when missing, so that it never fires later out of the blue
        currentOnCategoryRevealComplete()
    }

    // Pardes: the open book's row brought into view as the tree opens, and as another book opens, as the contents keeps
    // its entry in view (request 61 of stage 3); a row in view stays where it is (a book clicked in the tree)
    val openBookId = navigationState.selectedBook?.id?.takeIf { selectedBookIdOverride == null }
    val openBookRow = openBookId?.let { id -> treeItems.indexOfFirst { it.id == "book_$id" } } ?: -1
    LaunchedEffect(openBookId, openBookRow >= 0, hasRestored) {
        if (hasRestored && openBookRow >= 0) listState.revealItem(openBookRow)
    }

    /* ---------------------------------------------------------------------
     * UI.
     * -------------------------------------------------------------------- */

    val keyRows = remember(treeItems) { treeItems.map { TreeKeyRow(it.id, TreeNode(it.level, it.folder, it.open), it.click) } }
    Box(modifier = modifier.fillMaxSize().padding(bottom = 8.dp).treeKeys(keys, listState, keyRows, onLeave)) {
        VerticallyScrollableContainer(
            scrollState = listState as ScrollableState,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 16.dp),
            ) {
                items(
                    items = treeItems,
                    key = { it.id },
                ) { item ->
                    Box(modifier = Modifier.padding(start = (item.level * 16).dp)) {
                        item.content()
                    }
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun SearchResultCategoryTreeView(
    expandedCategoryIds: Set<Long>,
    scrollIndex: Int,
    scrollOffset: Int,
    searchTree: List<SearchResultViewModel.SearchTreeCategory>,
    isFiltering: Boolean,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
    onCategoryRowClick: (Category) -> Unit,
    onPersistScroll: (Int, Int) -> Unit,
    onCategoryCheckedChange: (Long, Boolean) -> Unit,
    onBookCheckedChange: (Long, Boolean) -> Unit,
    onEnsureScopeBookForToc: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Restore/track scroll position using the same keys as the classic tree
    val listState: LazyListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = scrollIndex,
            initialFirstVisibleItemScrollOffset = scrollOffset,
        )
    var hasRestored by remember { mutableStateOf(false) }
    val currentOnPersistScroll by rememberUpdatedState(onPersistScroll)

    // Flatten the search tree with current expansion state
    val expanded = expandedCategoryIds
    val items =
        remember(searchTree, expanded, selectedCategoryIds, selectedBookIds) {
            buildList {
                fun addNode(
                    node: SearchResultViewModel.SearchTreeCategory,
                    level: Int,
                    ancestorSelected: Boolean,
                ) {
                    val isThisSelected = selectedCategoryIds.contains(node.category.id)
                    val cascadedSelected = ancestorSelected || isThisSelected
                    add(
                        TreeItem(
                            id = "category_${node.category.id}",
                            level = level,
                            content = {
                                CategoryItem(
                                    category = node.category,
                                    isExpanded = expanded.contains(node.category.id),
                                    // In search mode, highlight remains aligned with checkbox selection
                                    isSelected = isThisSelected,
                                    onClick = { onCategoryRowClick(node.category) },
                                    count = node.count,
                                    showCount = true,
                                    checkboxChecked = isThisSelected,
                                    onCheckboxToggle = { checked ->
                                        // Checkbox exclusively controls selection
                                        onCategoryCheckedChange(node.category.id, checked)
                                    },
                                )
                            },
                        ),
                    )

                    if (expanded.contains(node.category.id)) {
                        // Books under this category (only those with results)
                        node.books.forEach { sb ->
                            add(
                                TreeItem(
                                    id = "book_${sb.book.id}",
                                    level = level + 1,
                                    content = {
                                        val checkedByBook = selectedBookIds.contains(sb.book.id)
                                        val checkedByCategory = cascadedSelected
                                        val isChecked = checkedByBook || checkedByCategory
                                        BookItem(
                                            book = sb.book,
                                            // Align highlight with effective checkbox (book OR cascaded category)
                                            isSelected = isChecked,
                                            onClick = {
                                                // Do not toggle selection when clicking the row in search mode
                                                // Intentionally no-op to avoid checking the checkbox
                                            },
                                            count = sb.count,
                                            showCount = true,
                                            checkboxChecked = isChecked,
                                            onCheckboxToggle = { checked ->
                                                onBookCheckedChange(sb.book.id, checked)
                                                if (checked) {
                                                    // Ensure TOC panel appears for this book
                                                    onEnsureScopeBookForToc(sb.book.id)
                                                }
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                        // Child categories
                        node.children.forEach { child -> addNode(child, level + 1, cascadedSelected) }
                    }
                }
                searchTree.forEach { addNode(it, 0, false) }
            }
        }

    // 1) Restore scroll once items are ready
    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && !hasRestored) {
            val safeIndex = scrollIndex.coerceIn(0, items.lastIndex)
            listState.scrollToItem(safeIndex, scrollOffset)
            hasRestored = true
        }
    }

    // 2) Persist scroll only after restoration to avoid thrashing
    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(150)
                .collect { (index, offset) -> currentOnPersistScroll(index, offset) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        VerticallyScrollableContainer(scrollState = listState as ScrollableState) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items) { node ->
                    Box(modifier = Modifier.fillMaxWidth().padding(start = (node.level * 12).dp)) {
                        node.content()
                    }
                }
            }
        }

        // Loader overlay while applying filters, with fast fade
        AnimatedVisibility(
            visible = isFiltering,
            enter = fadeIn(tween(durationMillis = 120, easing = LinearEasing)),
            exit = fadeOut(tween(durationMillis = 120, easing = LinearEasing)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.4f))
                        .zIndex(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/* -------------------------------------------------------------------------
 * Helpers
 * ---------------------------------------------------------------------- */
private fun buildTreeItems(
    rootCategories: List<Category>,
    expandedCategories: Set<Long>,
    categoryChildren: Map<Long, List<Category>>,
    booksByCategory: Map<Long, List<Book>>,
    selectedCategory: Category?,
    selectedBook: Book?,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit,
    categoryCounts: Map<Long, Int>,
    bookCounts: Map<Long, Int>,
    selectedCategoryIdOverride: Long?,
    selectedBookIdOverride: Long?,
    showCounts: Boolean,
    booksForCategoryOverride: Map<Long, List<Book>>,
    // Pardes: the keys' row, shown as selected in place of the selection (request 47 of stage 3)
    focusedId: String? = null,
): List<TreeItem> =
    buildList {
        // Pardes: the books of an open category, Zayit's, in a function of their own for the 60 lines of detekt.yml that
        // the tree's keys passed (request 47 of stage 3)
        fun addBooks(
            category: Category,
            level: Int,
        ) {
            val booksSeq: Sequence<Book> =
                if (showCounts) {
                    booksForCategoryOverride[category.id].orEmpty().asSequence()
                } else {
                    booksByCategory[category.id].orEmpty().asSequence()
                }
            booksSeq
                .distinctBy { it.id }
                .forEach { book ->
                    // In search mode, skip books with zero results
                    if (showCounts && (bookCounts[book.id] ?: 0) <= 0) return@forEach
                    add(
                        TreeItem(
                            id = "book_${book.id}",
                            level = level + 1,
                            content = {
                                BookItem(
                                    book = book,
                                    isSelected =
                                        focusedId?.let { it == "book_${book.id}" }
                                            ?: selectedBookIdOverride?.let { it == book.id }
                                            ?: (selectedBook?.id == book.id),
                                    onClick = { onBookClick(book) },
                                    count = bookCounts[book.id] ?: 0,
                                    showCount = showCounts,
                                )
                            },
                            click = { onBookClick(book) },
                        ),
                    )
                }
        }

        fun addCategory(
            category: Category,
            level: Int,
        ) {
            // In search mode, only render categories that contain results
            if (showCounts) {
                val catCount = categoryCounts[category.id] ?: 0
                if (catCount <= 0) return
            }
            add(
                TreeItem(
                    id = "category_${category.id}",
                    level = level,
                    content = {
                        // In search mode (showCounts == true), highlight category only when the category filter is active
                        // If a book filter is active (selectedBookIdOverride != null), do not highlight parent category
                        CategoryItem(
                            category = category,
                            isExpanded = expandedCategories.contains(category.id),
                            isSelected =
                                if (showCounts) {
                                    selectedBookIdOverride == null && (selectedCategoryIdOverride?.let { it == category.id } == true)
                                } else {
                                    selectedCategory?.id == category.id
                                },
                            onClick = { onCategoryClick(category) },
                            count = categoryCounts[category.id] ?: 0,
                            showCount = showCounts,
                            isFocused = focusedId == "category_${category.id}",
                        )
                    },
                    folder = true,
                    open = expandedCategories.contains(category.id),
                    click = { onCategoryClick(category) },
                ),
            )

            if (expandedCategories.contains(category.id)) {
                // Books in this category
                addBooks(category, level)

                // Subcategories
                categoryChildren[category.id]?.forEach { child ->
                    addCategory(child, level + 1)
                }
            }
        }

        rootCategories.forEach { category ->
            addCategory(category, 0)
        }
    }

@Composable
private fun CategoryItem(
    category: Category,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    count: Int,
    showCount: Boolean,
    checkboxChecked: Boolean? = null,
    onCheckboxToggle: ((Boolean) -> Unit)? = null,
    // Pardes: the keys' row, highlighted as a category selected in search mode (request 47 of stage 3)
    isFocused: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).background(
                    if (showCount && isSelected || isFocused) {
                        // highlight only in search mode (showCount signifies search mode here)
                        JewelTheme.iconButtonStyle.colors.backgroundFocused
                    } else {
                        Color.Transparent
                    },
                ).padding(vertical = 4.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (checkboxChecked != null && onCheckboxToggle != null) {
            Checkbox(
                checked = checkboxChecked,
                onCheckedChange = onCheckboxToggle,
            )
        }
        ChevronIcon(
            expanded = isExpanded,
            tint = JewelTheme.globalColors.text.normal,
            contentDescription = "",
        )
        SharedIcon(
            key = AllIconsKeys.Nodes.Folder,
            contentDescription = null,
        )
        Text(text = category.title)
        Spacer(Modifier.weight(1f))
        if (showCount && count > 0) CountBadge(count)
    }
}

@Composable
private fun BookItem(
    book: Book,
    isSelected: Boolean,
    onClick: () -> Unit,
    count: Int,
    showCount: Boolean,
    checkboxChecked: Boolean? = null,
    onCheckboxToggle: ((Boolean) -> Unit)? = null,
) {
    SelectableRow(isSelected = isSelected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (checkboxChecked != null && onCheckboxToggle != null) {
                    Checkbox(
                        checked = checkboxChecked,
                        onCheckedChange = onCheckboxToggle,
                    )
                }
                Icon(
                    imageVector = Book_2,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (isSelected) {
                            JewelTheme.globalColors.text.selected
                        } else {
                            JewelTheme.globalColors.text.normal
                                .copy(alpha = 0.7f)
                        },
                )
                Text(text = book.title, fontWeight = if (isSelected) Bold else Normal)
            }
            if (showCount && count > 0) CountBadge(count)
        }
    }
}
