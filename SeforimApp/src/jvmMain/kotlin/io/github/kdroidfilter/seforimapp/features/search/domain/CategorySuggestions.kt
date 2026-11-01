package io.github.kdroidfilter.seforimapp.features.search.domain

import androidx.compose.runtime.Immutable

/**
 * A category of the library the bar can search in, whole: its id and its path (from the root, itself last), or
 * [baseBooksOnly] its base books alone.
 */
@Immutable
data class CategoryHit(
    val id: Long,
    val path: List<String>,
    /** Searched in its base books alone: the texts without their commentaries (תלמוד's tractates, תורה's five books). */
    val baseBooksOnly: Boolean = false,
    /** It holds base books and others: searched in its base books alone, it is another search (see [baseBooksOnly]). */
    val hasBaseBooksAndOthers: Boolean = false,
) {
    val title: String get() = path.last()

    /** Its name where it is shown searched in (the bar's chip): with its parent, as רש״י › תורה, תלמוד › בבלי. */
    val label: String get() = path.takeLast(2).joinToString(" › ")
}

/**
 * The categories a text names, as typed in the bar: `תלמוד`, `שולחן ערוך`, `רש"י על התורה`
 * (תנ״ך › ראשונים › רש״י › תורה), `אחרונים על הש"ס` (תלמוד › בבלי › אחרונים).
 *
 * Every word typed must begin a word of the category's path, one at least of its own title; `על` and `של` join
 * words and are skipped, a word's leading ה may be dropped (`התורה`) and a few names stand for a category's
 * (`ש"ס`: בבלי, `חומש`: תורה).
 */
object CategorySuggestions {
    private val JOINING_WORDS = setOf("על", "של")

    // A name and the words of the category titles it stands for (as searchKey gives them)
    private val ALIASES =
        mapOf(
            "שס" to listOf("בבלי", "תלמוד"),
            "גמרא" to listOf("בבלי", "תלמוד"),
            "חומש" to listOf("תורה"),
            "שוע" to listOf("שולחן ערוך"),
            "משנב" to listOf("משנה ברורה"),
        )

    /**
     * The categories [query] names, best first: its exact title, then the most of it in the title, then the shallowest.
     * One that holds base books and others is followed by its base books alone ([CategoryHit.baseBooksOnly]): תלמוד,
     * then תלמוד's tractates without their commentaries.
     */
    fun match(
        query: String,
        categories: List<CategoryHit>,
        limit: Int,
    ): List<CategoryHit> {
        val key = query.searchKey()
        val words = key.split(' ').filter { it.isNotEmpty() && it !in JOINING_WORDS }
        if (words.isEmpty() || key.length < MIN_QUERY_LENGTH) return emptyList()
        val choices = words.map(::alternatives)
        return categories
            .asSequence()
            .mapNotNull { category -> rank(key, choices, category)?.let { category to it } }
            .sortedWith(compareBy<Pair<CategoryHit, Rank>> { it.second })
            .take(limit)
            .flatMap { (category, _) ->
                if (category.hasBaseBooksAndOthers) sequenceOf(category, category.copy(baseBooksOnly = true)) else sequenceOf(category)
            }.toList()
    }

    private data class Rank(
        val notExact: Boolean,
        val missingInTitle: Int,
        val depth: Int,
        val titleLength: Int,
    ) : Comparable<Rank> {
        override fun compareTo(other: Rank): Int =
            compareValuesBy(this, other, Rank::notExact, Rank::missingInTitle, Rank::depth, Rank::titleLength)
    }

    // Null when [category] isn't named by the words, each typed word given as its [choices]
    private fun rank(
        key: String,
        choices: List<List<String>>,
        category: CategoryHit,
    ): Rank? {
        val path = category.path.map { it.searchKey() }
        val title = path.last()
        val inTitle = choices.count { names -> names.any { title.hasWordStartingWith(it) } }
        if (inTitle == 0) return null
        if (!choices.all { names -> names.any { name -> path.any { it.hasWordStartingWith(name) } } }) return null
        return Rank(
            notExact = title != key,
            missingInTitle = choices.size - inTitle,
            depth = path.size,
            titleLength = title.length,
        )
    }

    // What a typed word may stand for: itself, without its leading ה, a name's category words
    private fun alternatives(word: String): List<String> =
        buildList {
            add(word)
            if (word.length > 2 && word.startsWith('ה')) add(word.drop(1))
            ALIASES[word]?.let(::addAll)
            ALIASES[word.drop(1)]?.takeIf { word.startsWith('ה') }?.let(::addAll)
        }

    // Whether [prefix] (one word or several) begins a word of this title
    private fun String.hasWordStartingWith(prefix: String): Boolean = startsWith(prefix) || contains(" $prefix")

    private const val MIN_QUERY_LENGTH = 2
}
