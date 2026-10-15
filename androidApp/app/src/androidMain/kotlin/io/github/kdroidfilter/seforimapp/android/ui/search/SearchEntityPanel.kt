package io.github.kdroidfilter.seforimapp.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.search.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.android.search.SearchEntity
import io.github.kdroidfilter.seforimapp.android.search.authorYears
import io.github.kdroidfilter.seforimapp.android.search.plainPersonLinks
import io.github.kdroidfilter.seforimapp.android.ui.components.AccentButton
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames

// Desktop 0.6.1 strings.
private const val AUTHOR_BOOKS_COUNT = "%1\$d ספרים"
private const val SEARCH_PANEL_AUTHOR = "מחבר"
private const val SEARCH_PANEL_PARTS = "חלקי הספר"
private const val SEARCH_PANEL_SOURCE = "מקור ורישיון"
private const val SEARCH_PANEL_AUTHOR_PAGE = "לדף המחבר"
private const val LICENSE_PUBLIC_DOMAIN = "נחלת הכלל"
private const val LICENSE_UNKNOWN = "רישיון לא ידוע"
private const val OTZARIA_CREDIT = "התכנים שלפניכם נאספו, מוינו, סוננו ונערכו בעמל רב ובמסירות על ידי צוות פרויקט 'אוצריא' (otzaria.org)."
private const val OTZARIA_URL = "https://otzaria.org"

/**
 * The book or author a query names, as Google's knowledge panel: what it is, and buttons to go there. Desktop 0.6.1
 * SearchResultScreen.kt EntityPanel / EntityDetails, with features/bookcontent/ui/panels/bookdetails/BookDetails.kt for
 * a book. Beside the preview on wide screens (as desktop); on narrower ones (where desktop has none) the first card
 * of the list.
 */
@Composable
internal fun EntityPanel(
    entity: SearchEntity,
    onOpenBookAt: (bookId: Long, lineId: Long) -> Unit,
    onOpenAuthor: (id: Long, name: String) -> Unit,
    modifier: Modifier = Modifier,
    // In the list (narrow screens) it sits on the results' card: outlined rather than a card of its own
    outlined: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val frame = if (outlined) modifier.padding(vertical = 4.dp).clip(shape).border(1.dp, Zayit.colors.panelBorder, shape) else modifier.card()
    Column(frame.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EntityDetails(entity, onOpenBookAt, onOpenAuthor)
    }
}

/**
 * The body of the tablets' book-details pane on a search tab: the selected result's book, as the knowledge panel shows
 * a book (desktop BookDetailsPane, under its header).
 */
@Composable
internal fun SearchBookDetails(
    entity: SearchEntity.BookEntity,
    onOpenBookAt: (bookId: Long, lineId: Long) -> Unit,
    onOpenAuthor: (id: Long, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BookDetails(entity, onOpenBookAt, onOpenAuthor)
    }
}

/** What the panel shows of a book (categories, author, parts) or an author (era, bio, books). */
@Composable
private fun ColumnScope.EntityDetails(
    entity: SearchEntity,
    onOpenBookAt: (bookId: Long, lineId: Long) -> Unit,
    onOpenAuthor: (id: Long, name: String) -> Unit,
) {
    val grey = readingSecondary()
    when (entity) {
        is SearchEntity.BookEntity -> BookDetails(entity, onOpenBookAt, onOpenAuthor)
        is SearchEntity.AuthorEntity -> {
            val details = entity.details
            val name = AuthorNames.display(details.name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(WritingHand, contentDescription = null, tint = grey, modifier = Modifier.size(20.dp))
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Zayit.colors.text)
            }
            val facts = listOfNotNull(details.era?.let { AUTHOR_ERAS[it] }, authorYears(details))
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = grey)
            details.bio?.summary?.let {
                Text(plainPersonLinks(it).replace("**", ""), fontSize = 13.sp, lineHeight = 21.sp, color = grey, maxLines = 7, overflow = TextOverflow.Ellipsis)
            }
            Text(AUTHOR_BOOKS_COUNT.replace("%1\$d", details.books.size.toString()), fontSize = 12.sp, color = grey)
            AccentButton(SEARCH_PANEL_AUTHOR_PAGE, onClick = { onOpenAuthor(details.id, name) }, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** A book's details: title, categories, authors (to their page), parts (to open) and editions with their license. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BookDetails(
    entity: SearchEntity.BookEntity,
    onOpenBookAt: (bookId: Long, lineId: Long) -> Unit,
    onOpenAuthor: (id: Long, name: String) -> Unit,
) {
    val c = Zayit.colors
    val grey = readingSecondary()
    val book = entity.book
    val uriHandler = LocalUriHandler.current
    Text(book.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.text)
    if (entity.categories.isNotEmpty()) Text(entity.categories.joinToString(" › "), fontSize = 12.sp, color = grey)
    entity.authors.forEach { author ->
        PanelSection(SEARCH_PANEL_AUTHOR) {
            val name = AuthorNames.display(author.name)
            ResultLink(name, c.accent, 14.sp, minHeight = 32.dp) { onOpenAuthor(author.id, name) }
            val facts = listOfNotNull(author.era?.let { AUTHOR_ERAS[it] }, authorYears(author))
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = grey)
        }
    }
    if (entity.parts.isNotEmpty()) {
        PanelSection(SEARCH_PANEL_PARTS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entity.parts.forEach { part -> ResultLink(part.title, c.accent, 13.sp, minHeight = 32.dp) { onOpenBookAt(book.id, part.lineId) } }
            }
        }
    }
    val editions = entity.editions.distinctBy { (it.heTitle ?: it.title) to it.licenseCode }
    if (editions.isNotEmpty() || entity.fromOtzaria) {
        PanelSection(SEARCH_PANEL_SOURCE) {
            editions.forEach { edition ->
                val title = edition.heTitle ?: edition.title
                val url = edition.sourceUrl
                if (url != null) {
                    ResultLink(title, c.accent, 13.sp, minHeight = 32.dp) { runCatching { uriHandler.openUri(url) } }
                } else {
                    Text(title, fontSize = 13.sp, color = c.text)
                }
                Text(licenseLabel(edition.licenseCode), fontSize = 12.sp, color = grey)
            }
            if (entity.fromOtzaria) {
                Text(OTZARIA_CREDIT, fontSize = 12.sp, color = grey, modifier = Modifier.clickable { runCatching { uriHandler.openUri(OTZARIA_URL) } })
            }
        }
    }
}

/** A license code in plain Hebrew where it has a name (נחלת הכלל), else as Creative Commons writes it. */
private fun licenseLabel(code: String): String = when (code) {
    "Public Domain" -> LICENSE_PUBLIC_DOMAIN
    "unknown" -> LICENSE_UNKNOWN
    else -> if (code.startsWith("CC-")) "CC " + code.removePrefix("CC-") else code
}

/** A titled part of the panel, under a thin divider. */
@Composable
private fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).height(1.dp).background(Zayit.colors.divider))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = readingSecondary())
        content()
    }
}
