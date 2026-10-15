package io.github.kdroidfilter.seforimapp.android.ui.home

import android.content.res.Configuration
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.ui.AppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchBarActions
import io.github.kdroidfilter.seforimapp.android.ui.search.SearchBarViewModel
import io.github.kdroidfilter.seforimapp.android.ui.search.UnifiedSearchBar
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimlibrary.core.models.Book

/**
 * The home page's search bar: desktop 0.6.1 HomeView's UnifiedSearchBar with its SearchHomeViewModel (one per Home tab,
 * as the results tab's bar is one per tab). Everything it opens replaces the Home in this tab, as desktop's
 * replaceCurrentTabDestination: a book or a place ([onOpenBook], which also reveals it in the book tree), an author's
 * page, a text search (in the base books, or all of them from "בכל הספרים" / Ctrl+Enter, scoped to the picked book or
 * TOC entry) or a pasted zayit:// link.
 *
 * Desktop focuses the field whenever the Home shows. On a touch screen that brings the soft keyboard up over the page,
 * and the field, the page's first text field, is also where a focus Android or Compose hands out by itself lands
 * (Compose's focus search on entering the view): there it takes the focus only from a touch on the bar, and HomeScreen's
 * own focus target takes the rest. With a hardware keyboard it keeps desktop's behaviour (the system shows no soft
 * keyboard then), and with any accessibility service on (TalkBack, Switch Access, Voice Access), whose click or focus
 * action is no touch on the bar, the field still takes the focus.
 *
 * [liftKey] changes when the bar moves on the page (see UnifiedSearchBar): a bar lifted over the keyboard is lifted again.
 */
@Composable
fun HomeSearchBar(
    wide: Boolean,
    onOpenBook: (Book, Long?) -> Unit,
    modifier: Modifier = Modifier,
    liftKey: Any? = null,
    resetKey: Int = 0,
) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val appVm: AppViewModel = LocalAppViewModel.current ?: viewModel()
    val vm: SearchBarViewModel = viewModel(key = "home-search-bar", factory = SearchBarViewModel.factory(app.libraryStore.repository(), app.search))
    val actions = remember(appVm, onOpenBook) {
        SearchBarActions(
            openBook = onOpenBook,
            openAuthor = { author ->
                val name = AuthorNames.display(author.name)
                appVm.openInCurrentTab(Destination.Author(author.id, name), name)
            },
            textSearch = { query, scope, extended ->
                appVm.openInCurrentTab(Destination.Search(query, scope = scope, globalExtended = extended), query)
            },
            openDestination = { destination ->
                when (destination) {
                    is Destination.Book -> appVm.openInCurrentTab(destination, destination.title)
                    is Destination.Search -> appVm.openInCurrentTab(destination, destination.query)
                    else -> Unit
                }
            },
        )
    }
    val configuration = LocalConfiguration.current
    val hardKeyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    val accessibility = remember(app) { app.getSystemService(AccessibilityManager::class.java) }
    // Set by a finger on the bar before the field asks for the focus (its tap acts on release), dropped with the focus
    var touched by remember { mutableStateOf(false) }
    UnifiedSearchBar(
        vm = vm,
        actions = actions,
        wide = wide,
        liftKey = liftKey,
        resetKey = resetKey,
        modifier =
            modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touched = true
                    }
                }.onFocusChanged { if (!it.hasFocus) touched = false }
                // Applies to the field inside: no focus target lies between this node and it
                .focusProperties {
                    if (!touched && !hardKeyboard && accessibility?.isEnabled != true) canFocus = false
                },
    )
}
