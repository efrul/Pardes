package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookFilterResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchButton
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchLayout
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.TypeToSearchTarget
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.rememberPaneSearchFocus
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.type
import io.github.kdroidfilter.seforimapp.features.pdf.MyPdfBooks
import io.github.kdroidfilter.seforimapp.features.pdf.myPdfBooksIn
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory
import io.github.kdroidfilter.seforimapp.features.search.narrowedTo
import io.github.kdroidfilter.seforimapp.framework.search.MIN_BOOK_QUERY_LENGTH
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_list
import seforimapp.seforimapp.generated.resources.book_search
import seforimapp.seforimapp.generated.resources.book_search_no_results

@Composable
fun CategoryTreePanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneHoverSource = remember { MutableInteractionSource() }
    // Pardes: the user's PDF books found by the search too (request 51)
    val search = myPdfBooksIn(uiState.navigation.search)
    val searchFocus = rememberPaneSearchFocus()
    // Pardes: a letter typed with the pointer over the book tree opens its search with it (TypeToSearch)
    TypeToSearchTarget(paneHoverSource) { text ->
        searchFocus.type(text, isOpen = search != null) { onEvent(BookContentEvent.ToggleBookTreeSearch) }
    }
    Column(modifier = modifier.hoverable(paneHoverSource)) {
        PaneHeader(
            label = stringResource(Res.string.book_list),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleBookTree) },
            actions = {
                PaneSearchButton(
                    isOpen = search != null,
                    focus = searchFocus,
                    onClick = { onEvent(BookContentEvent.ToggleBookTreeSearch) },
                    contentDescription = stringResource(Res.string.book_search),
                )
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            PaneSearchLayout(
                search = search,
                focus = searchFocus,
                placeholder = stringResource(Res.string.book_search),
                noResults = stringResource(Res.string.book_search_no_results),
                onQueryChange = { onEvent(BookContentEvent.BookTreeSearchQueryChanged(it)) },
                onSelect = { onEvent(BookContentEvent.BookTreeSearchBookSelected(it)) },
                onClose = { onEvent(BookContentEvent.CloseBookTreeSearch) },
                minQueryLength = MIN_BOOK_QUERY_LENGTH,
                regular = { NavigationTree(uiState = uiState, onEvent = onEvent) },
            ) { result, activeId ->
                CategoryBookTreeView(
                    navigationState =
                        uiState.navigation.copy(
                            rootCategories = result.roots,
                            categoryChildren = result.children,
                            booksInCategory = result.books,
                            expandedCategories = result.categoryIds,
                            selectedCategory = null,
                            selectedBook = null,
                            scrollIndex = 0,
                            scrollOffset = 0,
                            categoryReveal = null,
                        ),
                    onCategoryClick = {},
                    onBookClick = bookClick(onEvent) { onEvent(BookContentEvent.BookTreeSearchBookSelected(it.id)) },
                    onScroll = { _, _ -> },
                    selectedBookIdOverride = activeId,
                    // Pardes: the focus back to the text (request 47 of stage 3)
                    onLeave = { onEvent(BookContentEvent.FocusText) },
                )
            }
        }
    }
}

@Composable
private fun NavigationTree(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    // Pardes: the user's PDF books after the library's categories (request 28)
    MyPdfBooks(uiState.navigation, bookClick(onEvent) { onEvent(BookContentEvent.BookSelected(it)) }) { navigation, onBookClick ->
        CategoryBookTreeView(
            navigationState = navigation,
            onCategoryClick = { onEvent(BookContentEvent.CategorySelected(it)) },
            onBookClick = onBookClick,
            onScroll = { index, offset -> onEvent(BookContentEvent.BookTreeScrolled(index, offset)) },
            onCategoryRevealComplete = { onEvent(BookContentEvent.CategoryRevealed) },
            // Pardes: the focus back to the text (request 47 of stage 3)
            onLeave = { onEvent(BookContentEvent.FocusText) },
        )
    }
}

/** A book click: Ctrl/Cmd opens it in a new tab, a plain click runs [onOpen]. */
@Composable
private fun bookClick(
    onEvent: (BookContentEvent) -> Unit,
    onOpen: (Book) -> Unit,
): (Book) -> Unit {
    val windowInfo = LocalWindowInfo.current
    return { book ->
        val mods = windowInfo.keyboardModifiers
        if (mods.isCtrlPressed || mods.isMetaPressed) onEvent(BookContentEvent.BookSelectedInNewTab(book)) else onOpen(book)
    }
}

// Search mode variant: lambdas-only for events (no onEvent)

@Composable
fun SearchCategoryTreePanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    searchTree: ImmutableList<SearchTreeCategory>,
    isFiltering: Boolean,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
    onCategoryCheckedChange: (Long, Boolean) -> Unit,
    onBookCheckedChange: (Long, Boolean) -> Unit,
    onEnsureScopeBookForToc: (Long) -> Unit,
    modifier: Modifier = Modifier,
    // Pardes: a checkbox turned by the pointer, after its change (request 97 of stage 3)
    onPointerCheck: () -> Unit = {},
) {
    val paneHoverSource = remember { MutableInteractionSource() }
    // Pardes: the book tree's own search, its button, its field and the letters typed over the pane, the books it finds
    // shown in this tree, Enter checking the one marked (the reader's word on build 75: "למה אין בעץ את החיפוש? זה אמור
    // להיות אותו עץ של כל התוכנה")
    val search = uiState.navigation.search
    val searchFocus = rememberPaneSearchFocus()
    TypeToSearchTarget(paneHoverSource) { text ->
        searchFocus.type(text, isOpen = search != null) { onEvent(BookContentEvent.ToggleBookTreeSearch) }
    }
    val tree: @Composable (List<SearchTreeCategory>, BookFilterResult?, Long?) -> Unit = { shown, found, activeId ->
        SearchTreeList(
            uiState,
            onEvent,
            SearchTreeShown(shown, found, activeId),
            isFiltering,
            SearchMarksShown(selectedCategoryIds, selectedBookIds),
            SearchTreeChecks(onCategoryCheckedChange, onBookCheckedChange, onEnsureScopeBookForToc, onPointerCheck),
        )
    }
    Column(modifier = modifier.hoverable(paneHoverSource)) {
        PaneHeader(
            label = stringResource(Res.string.book_list),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleBookTree) },
            actions = {
                PaneSearchButton(
                    search != null,
                    searchFocus,
                    { onEvent(BookContentEvent.ToggleBookTreeSearch) },
                    stringResource(Res.string.book_search),
                )
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            PaneSearchLayout(
                search = search,
                focus = searchFocus,
                placeholder = stringResource(Res.string.book_search),
                noResults = stringResource(Res.string.book_search_no_results),
                onQueryChange = { onEvent(BookContentEvent.BookTreeSearchQueryChanged(it)) },
                onSelect = { id ->
                    val checked =
                        id in selectedBookIds ||
                            search
                                ?.result
                                ?.books
                                ?.firstOrNull { it.id == id }
                                ?.categoryId in selectedCategoryIds
                    onBookCheckedChange(id, !checked)
                    if (!checked) onEnsureScopeBookForToc(id)
                },
                onClose = { onEvent(BookContentEvent.CloseBookTreeSearch) },
                minQueryLength = MIN_BOOK_QUERY_LENGTH,
                regular = { tree(searchTree, null, null) },
            ) { result, activeId -> tree(remember(searchTree, result) { searchTree.narrowedTo(result) }, result, activeId) }
        }
    }
}

// Pardes: what a search's tree shows: [shown], the whole library's tree, or what the pane's search [found] (its
// categories open, its book [activeId] marked)
private class SearchTreeShown(
    val shown: List<SearchTreeCategory>,
    val found: BookFilterResult?,
    val activeId: Long?,
)

// Pardes: the search's marks, its categories and books checked
private class SearchMarksShown(
    val categories: Set<Long>,
    val books: Set<Long>,
)

// Pardes: what a check of the search's tree does: Zayit's changes of a category and a book, the book's contents shown,
// and the bar's keys after a check by the pointer
private class SearchTreeChecks(
    val onCategory: (Long, Boolean) -> Unit,
    val onBook: (Long, Boolean) -> Unit,
    val onEnsureScopeBookForToc: (Long) -> Unit,
    val onPointerCheck: () -> Unit,
)

// Pardes: the search's tree, the whole library's or what the pane's search found (SearchCategoryTreePanel)
@Composable
private fun SearchTreeList(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    tree: SearchTreeShown,
    isFiltering: Boolean,
    marks: SearchMarksShown,
    checks: SearchTreeChecks,
) {
    val whole = tree.found == null
    SearchResultCategoryTreeView(
        expandedCategoryIds = tree.found?.categoryIds ?: uiState.navigation.expandedCategories,
        scrollIndex = if (whole) uiState.navigation.scrollIndex else 0,
        scrollOffset = if (whole) uiState.navigation.scrollOffset else 0,
        searchTree = tree.shown,
        isFiltering = isFiltering,
        selectedCategoryIds = marks.categories,
        selectedBookIds = marks.books,
        onCategoryRowClick = { if (whole) onEvent(BookContentEvent.CategorySelected(it)) },
        onPersistScroll = { index, offset -> if (whole) onEvent(BookContentEvent.BookTreeScrolled(index, offset)) },
        onCategoryCheckedChange = checks.onCategory,
        onBookCheckedChange = checks.onBook,
        onEnsureScopeBookForToc = checks.onEnsureScopeBookForToc,
        onPointerCheck = checks.onPointerCheck,
        activeBookId = tree.activeId,
    )
}
