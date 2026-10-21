package io.github.kdroidfilter.seforimapp.android.ui.shell

import android.net.Uri
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Public, shareable deep link scheme for Zayit. Links reference the stable book / line database identifiers, so a link
 * resolves to the same content on any device running the same database version:
 *  - zayit://book/<bookId>                 -> open the book
 *  - zayit://book/<bookId>/line/<lineId>   -> open the book scrolled to a precise line
 *  - zayit://search/<url-encoded-query>    -> open a search
 *
 * Verbatim port of desktop 0.6.7 core/deeplink/ZayitDeepLink.kt, mapped to the Android [Destination]. Also reads
 * `zayit://search?q=…`, the form this app produced before.
 */
const val ZAYIT_SCHEME = "zayit"

private const val PREFIX = "$ZAYIT_SCHEME://"
private const val HOST_BOOK = "book"
private const val HOST_SEARCH = "search"
private const val SEGMENT_LINE = "line"

/** Builds a shareable link to a book, optionally pinned to a precise line. */
fun bookShareLink(bookId: Long, lineId: Long? = null): String = buildString {
    append(PREFIX).append(HOST_BOOK).append('/').append(bookId)
    if (lineId != null && lineId > 0) append('/').append(SEGMENT_LINE).append('/').append(lineId)
}

/** Builds a shareable link to a search query. */
fun searchShareLink(query: String): String = PREFIX + HOST_SEARCH + "/" + URLEncoder.encode(query, "UTF-8")

/**
 * A shareable link for this destination, or null when there is nothing worth sharing (Home, History, Favorites).
 * [anchorLineId] is where the book tab is being read (desktop shares the tab's own line).
 */
fun Destination.toShareLink(anchorLineId: Long? = null): String? = when (this) {
    is Destination.Book -> if (bookId > 0) bookShareLink(bookId, anchorLineId?.takeIf { it > 0 } ?: lineId) else null
    is Destination.Search -> query.takeIf { it.isNotBlank() }?.let(::searchShareLink)
    Destination.Home, Destination.History, Destination.Favorites, is Destination.Author -> null
}

/**
 * Parses a zayit:// deep link into a destination, or null when the URI does not match a known content scheme.
 * Checking that a book exists is the caller's job.
 */
fun parseZayitDeepLink(uri: String): Destination? {
    if (!uri.startsWith(PREFIX)) return null
    // This app's earlier search links: zayit://search?q=…
    if (uri.startsWith("$PREFIX$HOST_SEARCH?")) {
        return runCatching { Uri.parse(uri).getQueryParameter("q") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { Destination.Search(it) }
    }
    val path = uri.removePrefix(PREFIX)
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    return when (segments[0]) {
        HOST_BOOK -> {
            val bookId = segments.getOrNull(1)?.toLongOrNull() ?: return null
            val lineId = if (segments.getOrNull(2) == SEGMENT_LINE) segments.getOrNull(3)?.toLongOrNull() ?: return null else null
            Destination.Book(bookId = bookId, lineId = lineId)
        }
        HOST_SEARCH -> {
            val encoded = path.substringAfter("$HOST_SEARCH/", "")
            if (encoded.isEmpty()) return null
            runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { Destination.Search(it) }
        }
        else -> null
    }
}
