package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LibraryLinks
import io.github.kdroidfilter.seforimapp.features.sefaria.Mention
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaSages
import io.github.kdroidfilter.seforimapp.features.sefaria.SefariaWindows
import io.github.kdroidfilter.seforimlibrary.core.models.Line

/**
 * The names of the sages the Mishnah and the Talmud mention as links to their card, from Sefaria's mentions
 * (SefariaSages): where Sefaria found a person named in a line, the name as the line writes it is made a link
 * (`seforim://sage/<id>`, [withSageLinks]), which the reader's link handler opens in the persons' window.
 */
internal class SageLinks(
    private val library: LibraryLinks,
) : QuoteLinksFeature {
    // The Mishnah's and the Talmud's books, read once from the library
    @Volatile
    private var books: Set<Long>? = null

    override fun covers(bookId: Long): Boolean {
        val known = books ?: library.booksOf(BOOKS).also { if (it.isNotEmpty()) books = it }
        return bookId in known
    }

    override suspend fun decorate(
        bookId: Long,
        lines: List<Line>,
        linesByIds: suspend (Collection<Long>) -> List<Line>,
    ): List<Line> {
        val sages = SefariaSages.shipped
        return lines.map { line ->
            val mentions = line.heRef?.let(sages::at).orEmpty()
            if (mentions.isEmpty()) return@map line
            val html = withSageLinks(line.content, mentions)
            if (html === line.content) line else line.copy(content = html)
        }
    }

    private companion object {
        val BOOKS =
            with(CatalogPresets.Ids.Categories) {
                (MISHNA_ZERAIM..MISHNA_TAHAROT) + (BAVLI_ZERAIM..BAVLI_TAHAROT) + (YERUSHALMI_ZERAIM..YERUSHALMI_TAHAROT)
            }
    }
}

/**
 * [html], a line of the Mishnah or the Talmud, with each name [mentions] give made a link to its person: every run of
 * the line's words that writes the name letter by letter (its nikud aside), outside the line's links (a verse quoted).
 * A name in short (ר"מ, רשב"ג) is two words of the line, parted by its gershayim, and one link. The longer names first,
 * so that a name inside another (הורקנוס in רבי אליעזר בן הורקנוס) is not a link of its own. The same [html] when no
 * name is found.
 */
internal fun withSageLinks(
    html: String,
    mentions: List<Mention>,
): String {
    val words = wordsOfLine(html)
    if (words.isEmpty()) return html
    val letters = words.map { word -> html.substring(word.start, word.end).filter(Char::isHebrewLetter) }
    val free = BooleanArray(words.size) { true }
    val links = ArrayList<Pair<IntRange, Int>>()
    val names =
        mentions
            .map { mention ->
                mention to
                    mention.text
                        .split(NAME_BREAKS)
                        .map { it.filter(Char::isHebrewLetter) }
                        .filter(String::isNotEmpty)
            }.filter { it.second.isNotEmpty() }
            .distinctBy { (mention, name) -> mention.sageId to name }
            .sortedByDescending { it.second.size }
    for ((mention, name) in names) {
        var at = 0
        while (at + name.size <= words.size) {
            val run = at until at + name.size
            if (run.all { free[it] && letters[it] == name[it - at] }) {
                run.forEach { free[it] = false }
                links += run to mention.sageId
                at += name.size
            } else {
                at++
            }
        }
    }
    if (links.isEmpty()) return html
    return buildString(html.length + links.size * LINK_CHARS) {
        var at = 0
        for ((run, sageId) in links.sortedBy { it.first.first }) {
            val start = words[run.first].start
            val end = words[run.last].end
            append(html, at, start)
            val href = SefariaWindows.SAGE_LINK + sageId
            // A name in short keeps its gershayim in its link; a tag inside is crossed as a verse's link crosses it
            if (html.indexOf('<', start) !in start until end) {
                append("<a class=\"ref\" href=\"")
                    .append(href)
                    .append("\">")
                    .append(html, start, end)
                    .append("</a>")
            } else {
                appendLinked(html, start, end, href)
            }
            at = end
        }
        append(html, at, html.length)
    }
}

private fun Char.isHebrewLetter(): Boolean = this in 'א'..'ת'

// What parts a name's words as the line's words are parted: spaces, the maqaf, and the gershayim of a name in short
private val NAME_BREAKS = Regex("[\\s\u05BE\"\u05F4\u201C\u201D]+")

private const val LINK_CHARS = 48
