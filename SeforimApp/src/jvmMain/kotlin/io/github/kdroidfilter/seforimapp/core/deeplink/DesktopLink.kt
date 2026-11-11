package io.github.kdroidfilter.seforimapp.core.deeplink

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val PREFIX = "$ZAYIT_SCHEME://"
private const val DESKTOP = "desktop/"
private const val TAB = "t="
private const val SELECTED = "s="

/** The most tabs a desktop's link carries: its first ones, in their windows' order. */
const val MAX_LINKED_TABS = 40

// The longest name the desktops' menu shows (DesktopSwitcher's MAX_DESKTOP_NAME_LENGTH)
private const val MAX_NAME_LENGTH = 8

/**
 * A whole desktop as one link: its name and its tabs, each one's own link ([toShareLink]) without the scheme, and the
 * tab selected when it is not the first:
 *
 *  - zayit://desktop/<url-encoded name>?t=book/<bookId>/line/<lineId>&t=search/<url-encoded query>&s=1
 *
 * Pasted into the search bar, it opens a new desktop of that name with those tabs ([parseDesktopLink], [asWindow]).
 * The ids are the library's, as in a tab's link: the same content on any machine with the same library.
 */
data class DesktopLink(
    val name: String,
    val tabs: List<TabsDestination>,
    val selected: Int = 0,
)

/**
 * The link of a desktop named [name] holding [tabs], the tab at [selected] shown; null when none of them can be
 * linked to (the Home, the history, a book still loading). Tabs without a link are left out, and those past
 * [MAX_LINKED_TABS].
 */
fun desktopShareLink(
    name: String,
    tabs: List<TabsDestination>,
    selected: Int = 0,
): String? {
    val linked = tabs.withIndex().mapNotNull { (index, tab) -> tab.toShareLink()?.let { index to it } }.take(MAX_LINKED_TABS)
    if (linked.isEmpty()) return null
    val shown = linked.indexOfFirst { it.first == selected }
    return buildString {
        append(PREFIX).append(DESKTOP).append(URLEncoder.encode(name, StandardCharsets.UTF_8))
        linked.forEachIndexed { at, (_, link) -> append(if (at == 0) '?' else '&').append(TAB).append(link.removePrefix(PREFIX)) }
        if (shown > 0) append('&').append(SELECTED).append(shown)
    }
}

/**
 * The desktop [uri] links to, its tabs carrying fresh ids; null when it is no desktop's link, or one without a tab this
 * app knows how to open. Whether the library has the books is the caller's to check ([asWindow]).
 */
fun parseDesktopLink(uri: String): DesktopLink? {
    if (!uri.startsWith(PREFIX + DESKTOP)) return null
    val rest = uri.removePrefix(PREFIX + DESKTOP)
    val parts = rest.substringAfter('?', "").split('&')
    val tabs = parts.filter { it.startsWith(TAB) }.mapNotNull { parseZayitDeepLink(PREFIX + it.removePrefix(TAB)) }.take(MAX_LINKED_TABS)
    if (tabs.isEmpty()) return null
    val name = runCatching { URLDecoder.decode(rest.substringBefore('?'), StandardCharsets.UTF_8) }.getOrDefault("")
    val selected = parts.firstOrNull { it.startsWith(SELECTED) }?.removePrefix(SELECTED)?.toIntOrNull() ?: 0
    return DesktopLink(name.trim().take(MAX_NAME_LENGTH), tabs, selected.coerceIn(0, tabs.lastIndex))
}

/**
 * This tab as it stands now, from its kept [state], rather than as it was opened: the book it shows (a tab opens
 * other books), at the line at the top of the screen, else the line selected; a search, with the words it last ran.
 */
fun TabsDestination.asItStands(state: TabPersistedState?): TabsDestination =
    when (this) {
        is TabsDestination.BookContent -> {
            val reader = state?.bookContent
            val book = reader?.selectedBookId?.takeIf { it > 0 } ?: bookId
            val line = reader?.contentAnchorLineId?.takeIf { it > 0 } ?: reader?.primarySelectedLineId?.takeIf { it > 0 }
            // The line it was opened at says nothing of another book
            copy(bookId = book, lineId = line ?: lineId.takeIf { book == bookId })
        }
        is TabsDestination.Search -> copy(searchQuery = state?.search?.query?.takeIf { it.isNotBlank() } ?: searchQuery)
        else -> this
    }

/**
 * The link of the desktop named [name] that this snapshot is of: its windows' tabs in order, each as it stands
 * ([asItStands]), the first window's selected tab shown. Null when no tab can be linked to.
 */
fun DesktopTabsSnapshot.shareLink(name: String): String? {
    val windows = effectiveWindows()
    val tabs = windows.flatMap { it.destinations }.map { it.asItStands(tabStates[it.tabId]) }
    return desktopShareLink(name, tabs, windows.firstOrNull()?.selectedIndex ?: 0)
}

/**
 * The window a new desktop opens with for this link: the tabs whose books [books] (the library's, by id) has, titled
 * as their tabs will be once read, the others left out. Null when the library has none of them.
 */
fun DesktopLink.asWindow(books: Map<Long, Book>): WindowSnapshot? {
    val kept = tabs.withIndex().filter { (_, tab) -> tab !is TabsDestination.BookContent || tab.bookId in books }
    if (kept.isEmpty()) return null
    return WindowSnapshot(
        destinations = kept.map { it.value },
        selectedIndex = kept.indexOfFirst { it.index == selected }.coerceAtLeast(0),
        titles =
            kept.associate { (_, tab) ->
                val title = (tab as? TabsDestination.BookContent)?.let { books[it.bookId]?.title } ?: DesktopSession.titleFor(tab)
                tab.tabId to SerializableTabTitle(title, DesktopSession.tabTypeFor(tab))
            },
    )
}
