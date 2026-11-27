package io.github.kdroidfilter.seforimapp.features.search.area

import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey

/** What a query can name, as the area beside a search's results shows it. */
enum class FacetKind {
    BOOK,
    AUTHOR,
    SAGE,
    TOPIC,
}

/**
 * One thing a query names exactly: a book (by its title, or by its whole acronym when [byTitle] is false), an author,
 * a person of Sefaria's or a topic of Sefaria's, with its [id] and every name it goes by.
 */
data class Facet(
    val kind: FacetKind,
    val id: Long,
    val names: List<String>,
    val byTitle: Boolean = true,
)

/** One entity of the area: its [facets], the one it is shown as first (an author before its topic). */
data class AreaEntity(
    val facets: List<Facet>,
) {
    val main: Facet get() = facets.first()

    fun facet(kind: FacetKind): Facet? = facets.firstOrNull { it.kind == kind }
}

/**
 * Pardes: what the area beside a search's results makes of the things its query names (the design's ד with ב): the
 * faces of one person together, and the entities in the order they are shown, the first in full and the others on its
 * "also" line. A person of Sefaria's and a topic of Sefaria's are one when they share Sefaria's id (רבא); an author and
 * either of them when they share two names at least (the רמב"ם and his topic share רמב"ם, משה בן מימון and רמבם), for
 * one name alone joins persons apart (the רא"ש and the topic ראש, the Rishon מרדכי and Esther's). A book is always an
 * entity of its own.
 */
object SearchFacets {
    /** The names an author must share with a person or a topic of Sefaria's to be one with it. */
    const val SHARED_NAMES = 2

    /** The entities of [facets], in the order the area shows them. */
    fun entities(facets: List<Facet>): List<AreaEntity> {
        val groups = ArrayList<MutableList<Facet>>()
        for (facet in facets.sortedBy(::rank)) {
            val group = groups.firstOrNull { group -> group.any { same(it, facet) } }
            if (group != null) group += facet else groups += mutableListOf(facet)
        }
        return groups.map(::AreaEntity).sortedBy { rank(it.main) }
    }

    private fun same(
        a: Facet,
        b: Facet,
    ): Boolean =
        when {
            a.kind == FacetKind.BOOK || b.kind == FacetKind.BOOK || a.kind == b.kind -> false
            a.kind in SEFARIA && b.kind in SEFARIA -> a.id == b.id
            else -> sharedNames(a, b) >= SHARED_NAMES
        }

    private fun sharedNames(
        a: Facet,
        b: Facet,
    ): Int {
        val keys = a.names.mapTo(HashSet()) { it.searchKey() }
        return b.names
            .map { it.searchKey() }
            .distinct()
            .count { it.isNotEmpty() && it in keys }
    }

    // A book named by its title first, then an author, a person, a book named by its acronym, a topic
    private fun rank(facet: Facet): Int =
        when (facet.kind) {
            FacetKind.BOOK -> if (facet.byTitle) 0 else 3
            FacetKind.AUTHOR -> 1
            FacetKind.SAGE -> 2
            FacetKind.TOPIC -> 4
        }

    private val SEFARIA = setOf(FacetKind.SAGE, FacetKind.TOPIC)
}
