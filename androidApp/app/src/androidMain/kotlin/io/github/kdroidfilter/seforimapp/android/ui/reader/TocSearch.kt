package io.github.kdroidfilter.seforimapp.android.ui.reader

import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

// Verbatim port of desktop 0.6.1 features/bookcontent/usecases/TocSearch.kt: the TOC pane's title search.

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
private val WORD = Regex("[\\p{L}\\p{N}]+")

// ASCII stand-ins for gershayim/geresh (קכ"א, ס'): part of a word when between two letters
private fun isInWordQuote(text: CharSequence, i: Int): Boolean =
    (text[i] == '"' || text[i] == '\'') && i > 0 && i < text.length - 1 && text[i - 1].isLetter() && text[i + 1].isLetter()

/** The TOC narrowed to the entries matching the query plus their ancestors; the alt TOC's under negated ids. */
data class TocFilterResult(
    val roots: List<TocEntry> = emptyList(),
    val altRoots: List<TocEntry> = emptyList(),
    val children: Map<Long, List<TocEntry>> = emptyMap(),
    val matchIds: List<Long> = emptyList(),
    /** Ranges of the title to highlight, per matching entry. */
    val highlights: Map<Long, List<IntRange>> = emptyMap(),
    val bestMatchId: Long? = null,
)

/** Splits a normalized query into its words, dropping punctuation. */
internal fun tocQueryTokens(query: String): List<String> {
    val normalized = normalizeQueryForHebrew(query)
    val joined = buildString { normalized.forEachIndexed { i, c -> if (!isInWordQuote(normalized, i)) append(c) } }
    return joined.split(NON_WORD).filter { it.isNotEmpty() }
}

internal data class TocTextMatch(val ranges: List<IntRange>, val exactWords: Int)

/** Matches [text] against [tokens]: each token the start of its own word of the title, whole words first. */
internal fun matchTocText(text: String, tokens: List<String>): TocTextMatch? {
    if (tokens.isEmpty()) return null
    val (stripped, strippedMap) = stripDiacriticsWithMap(text)
    val plain = StringBuilder(stripped.length)
    val toOriginal = IntArray(stripped.length)
    stripped.forEachIndexed { i, c ->
        if (!isInWordQuote(stripped, i)) {
            toOriginal[plain.length] = strippedMap[i]
            plain.append(c)
        }
    }
    val map = toOriginal.copyOf(plain.length)
    val words = WORD.findAll(replaceFinalsWithBase(plain.toString()).lowercase()).toList()
    val used = BooleanArray(words.size)
    val starts = IntArray(tokens.size) { -1 }
    var exactWords = 0
    tokens.forEachIndexed { t, token ->
        val w = words.indices.firstOrNull { !used[it] && words[it].value == token } ?: return@forEachIndexed
        used[w] = true
        starts[t] = words[w].range.first
        exactWords++
    }
    tokens.indices.filter { starts[it] < 0 }.sortedByDescending { tokens[it].length }.forEach { t ->
        val w = words.indices.firstOrNull { !used[it] && words[it].value.startsWith(tokens[t]) } ?: return null
        used[w] = true
        starts[t] = words[w].range.first
    }
    val ranges = tokens.indices.map { t -> mapToOrigIndex(map, starts[t])..mapToOrigIndex(map, starts[t] + tokens[t].length - 1) }
    return TocTextMatch(ranges, exactWords)
}

/** Filters the full TOC of a book ([entries], in book order) to the entries matching [query] and their ancestors. */
internal fun buildTocFilter(entries: List<TocEntry>, query: String): TocFilterResult {
    val tokens = tocQueryTokens(query)
    if (tokens.isEmpty()) return TocFilterResult()
    val byId = entries.associateBy { it.id }
    val highlights = HashMap<Long, List<IntRange>>()
    val exactWords = HashMap<Long, Int>()
    val visible = HashSet<Long>()
    for (entry in entries) {
        val match = matchTocText(entry.text, tokens) ?: continue
        highlights[entry.id] = match.ranges
        exactWords[entry.id] = match.exactWords
        var current: TocEntry? = entry
        while (current != null && visible.add(current.id)) current = current.parentId?.let(byId::get)
    }
    if (highlights.isEmpty()) return TocFilterResult()
    val childrenByParent = entries.filter { it.id in visible }.groupBy { it.parentId }
    val children = HashMap<Long, List<TocEntry>>()
    val ordered = ArrayList<Long>(highlights.size)

    fun walk(parentId: Long?): List<TocEntry> {
        val siblings = childrenByParent[parentId].orEmpty()
        return siblings.mapIndexed { index, entry ->
            if (entry.id in highlights) ordered += entry.id
            val kids = walk(entry.id)
            if (kids.isNotEmpty()) children[entry.id] = kids
            entry.copy(hasChildren = kids.isNotEmpty(), isLastChild = index == siblings.lastIndex)
        }
    }

    var roots = walk(null)
    val soleRoot = roots.singleOrNull()
    if (soleRoot != null && soleRoot.id !in highlights) roots = children[soleRoot.id].orEmpty()
    return TocFilterResult(roots = roots, children = children, matchIds = ordered, highlights = highlights, bestMatchId = ordered.maxByOrNull { exactWords.getValue(it) })
}

/** This filter (over the TOC) extended with [alt] (over the alt TOC). */
internal fun TocFilterResult.withAlt(alt: TocFilterResult): TocFilterResult = copy(
    altRoots = alt.roots,
    children = children + alt.children,
    matchIds = matchIds + alt.matchIds,
    highlights = highlights + alt.highlights,
    bestMatchId = bestMatchId ?: alt.bestMatchId,
)

/** The alt TOC entry a TOC search match id stands for, if it is one. */
internal fun altTocIdOfSearchMatch(matchId: Long): Long? = if (matchId < 0) -matchId else null

/** An alt TOC entry as a TOC search entry, under its negated id. */
internal fun AltTocEntry.toSearchTocEntry(bookId: Long): TocEntry = toTocEntry(bookId).copy(id = -id, parentId = parentId?.let { -it })

internal fun AltTocEntry.toTocEntry(bookId: Long): TocEntry =
    TocEntry(id = id, bookId = bookId, parentId = parentId, textId = textId, text = text, level = level, lineId = lineId, isLastChild = isLastChild, hasChildren = hasChildren)
