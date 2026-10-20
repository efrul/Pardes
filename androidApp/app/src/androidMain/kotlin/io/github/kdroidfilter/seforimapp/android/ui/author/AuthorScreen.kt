package io.github.kdroidfilter.seforimapp.android.ui.author

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.search.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.android.search.authorYears
import io.github.kdroidfilter.seforimapp.android.search.plainPersonLinks
import io.github.kdroidfilter.seforimapp.android.ui.Destination
import io.github.kdroidfilter.seforimapp.android.ui.components.EmptyHint
import io.github.kdroidfilter.seforimapp.android.ui.components.InfoBanner
import io.github.kdroidfilter.seforimapp.android.ui.components.Island
import io.github.kdroidfilter.seforimapp.android.ui.components.LoadingBox
import io.github.kdroidfilter.seforimapp.android.ui.reader.PlainCopySelectionContainer
import io.github.kdroidfilter.seforimapp.android.ui.reader.rememberJustifiedText
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalAppViewModel
import io.github.kdroidfilter.seforimapp.android.ui.shell.LocalTabSession
import io.github.kdroidfilter.seforimapp.android.ui.shell.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import io.github.kdroidfilter.seforimapp.android.ui.theme.ZayitFonts
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Desktop 0.6.7 strings (composeResources/values/strings.xml).
private const val AUTHOR_BOOKS = "ספרים במאגר"
private const val AUTHOR_INFO_ERA = "תקופה"
private const val AUTHOR_INFO_YEARS = "שנים"
private const val AUTHOR_INFO_ALIASES = "כינויים"
private const val AUTHOR_SOURCES = "מקורות"
private const val AUTHOR_BOOKS_PANE = "ספרי המחבר"
private const val AUTHOR_INFO_PANE = "פרטי המחבר"
private const val AUTHOR_BIO_AI_NOTICE =
    "שימו לב: הביוגרפיה נכתבה באופן אוטומטי בעזרת בינה מלאכותית על פי המקורות המצוינים בה, ולא עברה בדיקה אנושית. " +
        "ייתכנו טעויות בתאריכים, בשמות ובפרטים; אמתו מול מקור מהימן לפני הסתמכות."
private const val AUTHOR_BIO_LICENSE = "הביוגרפיה מתפרסמת ברישיון CC BY-SA 4.0, על פי המקורות המצוינים בה."
private const val AUTHOR_NOT_FOUND = "המחבר לא נמצא"

/** From this width the page has both side panes, as desktop opens it (WIDE_LAYOUT). */
private val WideLayout = 1100.dp
private val SidePane = 280.dp
private const val NAME_SCALE = 1.75f
private const val LINE_SPACING = 1.75f

/**
 * An author's page, port of desktop 0.6.7 features/author/AuthorScreen.kt: the name, the era and years, the
 * biography (its summary as the lead, its sections, the AI notice) at the app's text size, the author's books by
 * category, and the aliases, the biography's sources and its license. Laid out like a book's page on desktop: from
 * [WideLayout] the books pane at the start, the text, the info pane at the end; on a tablet below that, the books
 * beside the text with the info under it; on a phone one column (text, books, info). Books open in a new tab
 * ([onOpenBook]); a book link in the biography (zayit://) too, other links in the browser.
 */
@Composable
fun AuthorScreen(authorId: Long, wide: Boolean, onOpenBook: (Book) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val appVm = LocalAppViewModel.current
    val session = LocalTabSession.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val page by produceState<AuthorPage>(AuthorPage.Loading, authorId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val repo = app.libraryStore.repository()
                repo.getAuthorDetails(authorId)?.withHebrewQuotes()?.let { details ->
                    val categories = details.books.map { it.categoryId }.distinct().associateWith { id -> repo.getCategory(id)?.title?.hebrewQuotes().orEmpty() }
                    AuthorPage.Loaded(details, categories, BioText.of(details.bio?.bodyMd))
                } ?: AuthorPage.Missing
            }.getOrDefault(AuthorPage.Missing)
        }
    }
    // The tab is named after the author (desktop updateTabTitle)
    val displayName = (page as? AuthorPage.Loaded)?.let { AuthorNames.display(it.details.name).hebrewQuotes() }
    LaunchedEffect(session?.tabId, displayName) {
        val tabId = session?.tabId
        if (displayName != null && tabId != null) appVm?.updateTabTitle(tabId, displayName)
    }

    fun openLink(url: String) {
        when (val destination = parseZayitDeepLink(url)) {
            is Destination.Book -> scope.launch {
                val title = withContext(Dispatchers.IO) { runCatching { app.libraryStore.repository().getBookCore(destination.bookId)?.title }.getOrNull() } ?: return@launch
                appVm?.openInNewTab(destination.copy(title = title), title)
            }
            is Destination.Search -> appVm?.openInNewTab(destination, destination.query)
            null -> runCatching { uriHandler.openUri(url) }
            else -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        when (val current = page) {
            AuthorPage.Loading -> LoadingBox(Modifier.fillMaxSize())
            AuthorPage.Missing -> EmptyHint(AUTHOR_NOT_FOUND, Modifier.fillMaxSize())
            is AuthorPage.Loaded -> AuthorPageLayout(current, wide, onLink = ::openLink, onBook = onOpenBook)
        }
    }
}

@Composable
private fun AuthorPageLayout(page: AuthorPage.Loaded, wide: Boolean, onLink: (String) -> Unit, onBook: (Book) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (!wide) {
            // A phone: one column, the text, then the books and the facts
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 20.dp)) {
                TextContent(page, showFacts = true, onLink = onLink)
                Spacer(Modifier.height(24.dp))
                SectionTitle(AUTHOR_BOOKS_PANE)
                BooksContent(page, onBook)
                Spacer(Modifier.height(24.dp))
                SectionTitle(AUTHOR_INFO_PANE)
                InfoContent(page, onLink)
            }
            return@BoxWithConstraints
        }
        val threePanes = maxWidth >= WideLayout
        // RTL: the books pane first, at the start (right), as desktop
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Island(Modifier.width(SidePane).fillMaxHeight(), title = AUTHOR_BOOKS_PANE) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) { BooksContent(page, onBook) }
            }
            Island(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp)) {
                    // Its era and years in the text when the info pane is not there (desktop showFacts)
                    TextContent(page, showFacts = !threePanes, onLink = onLink)
                    if (!threePanes) {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle(AUTHOR_INFO_PANE)
                        InfoContent(page, onLink)
                    }
                }
            }
            if (threePanes) {
                Island(Modifier.width(SidePane).fillMaxHeight(), title = AUTHOR_INFO_PANE) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) { InfoContent(page, onLink) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Zayit.colors.text, modifier = Modifier.padding(bottom = 6.dp))
}

/** The name, then the biography at the app's text size; its era and years too when the info pane is closed. */
@Composable
private fun TextContent(page: AuthorPage.Loaded, showFacts: Boolean, onLink: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as ZayitApplication
    val textSize by app.prefs.textSize.collectAsState()
    val c = Zayit.colors
    PlainCopySelectionContainer {
        Column(Modifier.fillMaxWidth()) {
            Text(
                AuthorNames.display(page.details.name).hebrewQuotes(),
                fontSize = (textSize * NAME_SCALE).sp,
                lineHeight = (textSize * NAME_SCALE * 1.3f).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ZayitFonts.book,
                color = c.text,
            )
            val facts = listOfNotNull(page.details.era?.let { AUTHOR_ERAS[it] }, authorYears(page.details))
            if (showFacts && facts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(facts.joinToString(" · "), fontSize = 13.sp, color = c.textSecondary)
            }
            if (page.details.bio != null) {
                Spacer(Modifier.height(12.dp))
                InfoBanner(AUTHOR_BIO_AI_NOTICE)
            }
            Spacer(Modifier.height(20.dp))
            page.details.bio?.summary?.let {
                AuthorMarkdown(plainPersonLinks(it), onLink, fontSize = (textSize + 2).sp, lineHeight = ((textSize + 2) * LINE_SPACING).sp)
            }
            if (page.bio.sections.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                AuthorMarkdown(page.bio.sections, onLink, fontSize = textSize.sp, lineHeight = (textSize * LINE_SPACING).sp)
            }
        }
    }
}

/** The author's books by category, opened in a tab; categories fold when there are many. */
@Composable
private fun BooksContent(page: AuthorPage.Loaded, onBook: (Book) -> Unit) {
    val groups = remember(page) { page.details.books.groupBy { it.categoryId } }
    val openByDefault = groups.size <= 2
    Column(Modifier.fillMaxWidth()) {
        groups.forEach { (categoryId, books) -> CategoryBooks(categoryId, page.categories[categoryId].orEmpty(), books, openByDefault, onBook) }
    }
}

/** Era, years, aliases and number of books; the biography's sources and license under them. */
@Composable
private fun InfoContent(page: AuthorPage.Loaded, onLink: (String) -> Unit) {
    val details = page.details
    PlainCopySelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            details.era?.let { AUTHOR_ERAS[it] }?.let { InfoLine(AUTHOR_INFO_ERA, it) }
            authorYears(details)?.let { InfoLine(AUTHOR_INFO_YEARS, it) }
            if (details.aliases.isNotEmpty()) InfoLine(AUTHOR_INFO_ALIASES, details.aliases.joinToString(" · ") { it.withoutNikud() })
            InfoLine(AUTHOR_BOOKS, details.books.size.toString())
            if (page.bio.sources.isNotBlank()) Sources(page.bio.sources, onLink)
            if (details.bio != null) Text(AUTHOR_BIO_LICENSE, fontSize = 11.sp, color = Zayit.colors.textDisabled)
        }
    }
}

@Composable
private fun Sources(markdown: String, onLink: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Disclosure(AUTHOR_SOURCES, expanded) { expanded = !expanded }
        if (expanded) AuthorMarkdown(markdown, onLink, fontSize = 12.sp, lineHeight = 20.sp, color = Zayit.colors.textSecondary)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = Zayit.colors.textDisabled)
        Text(value, fontSize = 13.sp, color = Zayit.colors.text)
    }
}

@Composable
private fun CategoryBooks(categoryId: Long, category: String, books: List<Book>, openByDefault: Boolean, onOpen: (Book) -> Unit) {
    var expanded by rememberSaveable(categoryId) { mutableStateOf(openByDefault) }
    Column {
        Disclosure("$category (${books.size})", expanded) { expanded = !expanded }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, top = 2.dp)) { books.forEach { book -> BookRow(book, onOpen) } }
        }
    }
}

@Composable
private fun Disclosure(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onToggle).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, Modifier.size(16.dp), tint = c.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 13.sp, color = c.textSecondary)
    }
}

@Composable
private fun BookRow(book: Book, onOpen: (Book) -> Unit) {
    val c = Zayit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(book) }.padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = c.textSecondary
        Image(rememberVectorPainter(bookOpenTabs(tint)), null, Modifier.size(13.dp), colorFilter = ColorFilter.tint(tint))
        Spacer(Modifier.width(8.dp))
        Text(book.title, fontSize = 13.sp, color = c.text)
    }
}

private sealed interface AuthorPage {
    data object Loading : AuthorPage

    data object Missing : AuthorPage

    data class Loaded(val details: AuthorDetails, val categories: Map<Long, String>, val bio: BioText) : AuthorPage
}

/**
 * A biography split for the page: its sections (without the summary, shown as the lead, nor its own book list, which
 * dates from its writing) and its sources, folded at the bottom.
 */
private data class BioText(val sections: String, val sources: String) {
    companion object {
        private const val SUMMARY = "תקציר"
        private const val BOOKS = "במאגר"
        private const val SOURCES = "מקורות"

        fun of(markdown: String?): BioText {
            if (markdown == null) return BioText("", "")
            val sections = mutableListOf<String>()
            var sources = ""
            markdown.split(Regex("(?m)^(?=## )")).forEach { section ->
                when (section.lineSequence().first().removePrefix("## ").trim()) {
                    SUMMARY, BOOKS -> Unit
                    SOURCES -> sources = section.substringAfter('\n').trim()
                    else -> sections += section.trim()
                }
            }
            return BioText(plainPersonLinks(sections.joinToString("\n\n")), sources)
        }
    }
}

/** The page's texts with Hebrew geresh and gershayim (רמב״ם, ר׳) instead of ASCII quotes and guillemets; the name stays raw. */
private fun AuthorDetails.withHebrewQuotes(): AuthorDetails =
    copy(
        aliases = aliases.map { it.hebrewQuotes() },
        bio = bio?.let { it.copy(summary = it.summary?.markdownHebrewQuotes(), bodyMd = it.bodyMd.markdownHebrewQuotes()) },
        books = books.map { it.copy(title = it.title.hebrewQuotes()) },
    )

private val GERSHAYIM = Regex("""(?<=[֐-׿])["“”](?=[֐-׿])""")
private val GERESH = Regex("""(?<=[֐-׿])['‘’]""")
private val LINK_TARGET = Regex("""]\([^)]*\)""")
private val GUILLEMETS = Regex("[«»]")

private fun String.hebrewQuotes(): String = replace(GUILLEMETS, "״").replace(GERSHAYIM, "״").replace(GERESH, "׳")

/** Like [hebrewQuotes], leaving link targets untouched. */
private fun String.markdownHebrewQuotes(): String {
    val out = StringBuilder()
    var last = 0
    LINK_TARGET.findAll(this).forEach { link ->
        out.append(substring(last, link.range.first).hebrewQuotes()).append(link.value)
        last = link.range.last + 1
    }
    return out.append(substring(last).hebrewQuotes()).toString()
}

private fun String.withoutNikud(): String = replace(Regex("[֑-ׇ]"), "").replace(Regex("\\s+"), " ").trim()

// ---- Markdown, as desktop AccentMarkdown lays the biographies out: headings, paragraphs (justified), lists, bold,
// italics and links in the accent colour ----

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    data class Item(val marker: String, val text: String) : MdBlock
}

private val LIST_ITEM = Regex("""^\s*([-*•]|\d+[.)])\s+(.*)$""")

private fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
        para.clear()
    }
    for (raw in src.lines()) {
        val line = raw.trimEnd()
        val item = LIST_ITEM.find(line)
        when {
            line.isBlank() -> flush()
            line.trimStart().startsWith("#") -> {
                flush()
                val t = line.trimStart()
                val level = t.takeWhile { it == '#' }.length
                out += MdBlock.Heading(level, t.drop(level).trim())
            }
            item != null -> {
                flush()
                val marker = item.groupValues[1].let { if (it.first().isDigit()) it else "•" }
                out += MdBlock.Item(marker, item.groupValues[2])
            }
            else -> para.append(line.trim()).append(' ')
        }
    }
    flush()
    return out
}

private val MD_LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

private fun inlineMarkdown(text: String, link: SpanStyle, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var bold = false
    var italic = false
    var i = 0
    fun style() = SpanStyle(fontWeight = if (bold) FontWeight.Bold else null, fontStyle = if (italic) FontStyle.Italic else null)
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            bold = !bold
            i += 2
            continue
        }
        if (text[i] == '*' && (i + 1 < text.length && !text[i + 1].isWhitespace() || italic)) {
            italic = !italic
            i++
            continue
        }
        if (text[i] == '[') {
            val m = MD_LINK.find(text, i)?.takeIf { it.range.first == i }
            if (m != null) {
                val url = m.groupValues[2]
                withLink(LinkAnnotation.Clickable(url, TextLinkStyles(style = link)) { onLink(url) }) {
                    withStyle(style()) { append(m.groupValues[1].replace("**", "")) }
                }
                i = m.range.last + 1
                continue
            }
        }
        var end = i + 1
        while (end < text.length && !text.startsWith("**", end) && text[end] != '[' && text[end] != '*') end++
        withStyle(style()) { append(text.substring(i, end)) }
        i = end
    }
}

/** Desktop AccentMarkdown: markdown laid out in place, in the serif at [fontSize], links in the accent colour. */
@Composable
private fun AuthorMarkdown(markdown: String, onLink: (String) -> Unit, fontSize: TextUnit, lineHeight: TextUnit, color: Color = Color.Unspecified) {
    val c = Zayit.colors
    val ink = if (color == Color.Unspecified) c.text else color
    val link = SpanStyle(color = c.accent)
    val blocks = remember(markdown, c.accent) { parseMarkdown(markdown).map { it to inlineMarkdown(it.textOf(), link, onLink) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { (block, text) ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text,
                    fontFamily = ZayitFonts.book,
                    fontSize = if (block.level <= 2) 20.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.padding(top = if (block.level <= 2) 20.dp else 14.dp, bottom = if (block.level <= 2) 6.dp else 4.dp),
                )
                // Selectable, so justified by the app: a long-pressed word is highlighted where it is drawn (Justify.kt)
                is MdBlock.Paragraph -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val style = LocalTextStyle.current.merge(TextStyle(fontFamily = ZayitFonts.book, fontSize = fontSize, lineHeight = lineHeight, color = ink, textAlign = TextAlign.Justify))
                    val justified = rememberJustifiedText(text, style, constraints.maxWidth)
                    Text(justified.text, modifier = Modifier.fillMaxWidth(), onTextLayout = justified.onLayout, style = style.copy(textAlign = justified.textAlign))
                }
                is MdBlock.Item -> Row(Modifier.fillMaxWidth()) {
                    Text(block.marker, fontFamily = ZayitFonts.book, fontSize = fontSize, lineHeight = lineHeight, color = ink, modifier = Modifier.padding(end = 8.dp))
                    Text(text, fontFamily = ZayitFonts.book, fontSize = fontSize, lineHeight = lineHeight, color = ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun MdBlock.textOf(): String = when (this) {
    is MdBlock.Heading -> text
    is MdBlock.Paragraph -> text
    is MdBlock.Item -> text
}
