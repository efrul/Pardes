package io.github.kdroidfilter.seforimapp.features.search.area

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The area's rule: which faces are one entity, and the order the entities are shown in. */
class SearchFacetsTest {
    private fun kinds(entities: List<AreaEntity>) = entities.map { entity -> entity.facets.map { it.kind } }

    @Test
    fun `a tractate comes before the topic of the same name, apart from it`() {
        val book = Facet(FacetKind.BOOK, 9, listOf("שבת"))
        val topic = Facet(FacetKind.TOPIC, 3391, listOf("שבת", "השבת"))
        assertEquals(listOf(listOf(FacetKind.BOOK), listOf(FacetKind.TOPIC)), kinds(SearchFacets.entities(listOf(topic, book))))
    }

    @Test
    fun `an author and a topic that share two names are one person, the author first`() {
        val author = Facet(FacetKind.AUTHOR, 11, listOf("רמב\"ם", "רמב\"ם", "משה בן מימון", "רמבם"))
        val topic = Facet(FacetKind.TOPIC, 4646, listOf("רמב\"ם", "משה בן מימון", "רמבם"))
        val entities = SearchFacets.entities(listOf(topic, author))
        assertEquals(listOf(listOf(FacetKind.AUTHOR, FacetKind.TOPIC)), kinds(entities))
        assertEquals(author, entities.single().main)
    }

    @Test
    fun `one name shared does not make one person`() {
        val author = Facet(FacetKind.AUTHOR, 86, listOf("רא\"ש", "אשר בן יחיאל"))
        val topic = Facet(FacetKind.TOPIC, 4736, listOf("ראש"))
        assertEquals(listOf(listOf(FacetKind.AUTHOR), listOf(FacetKind.TOPIC)), kinds(SearchFacets.entities(listOf(author, topic))))
    }

    @Test
    fun `a name written twice counts once`() {
        val author = Facet(FacetKind.AUTHOR, 133, listOf("מרדכי", "מרדכי"))
        val sage = Facet(FacetKind.SAGE, 1330, listOf("מרדכי", "מָרְדֳּכַי"))
        assertEquals(2, SearchFacets.entities(listOf(author, sage)).size)
    }

    @Test
    fun `a person and a topic of Sefaria's are one by their id alone`() {
        val sage = Facet(FacetKind.SAGE, 2376, listOf("רבא"))
        val topic = Facet(FacetKind.TOPIC, 2376, listOf("רבא"))
        val other = Facet(FacetKind.TOPIC, 2377, listOf("רבא"))
        assertEquals(listOf(listOf(FacetKind.SAGE, FacetKind.TOPIC)), kinds(SearchFacets.entities(listOf(topic, sage))))
        assertEquals(2, SearchFacets.entities(listOf(sage, other)).size)
    }

    @Test
    fun `an author, a person, a book named by its acronym, then a topic`() {
        val acronym = Facet(FacetKind.BOOK, 5, listOf("שולחן ערוך"), byTitle = false)
        val topic = Facet(FacetKind.TOPIC, 7, listOf("שוע"))
        val sage = Facet(FacetKind.SAGE, 8, listOf("שוע"))
        val author = Facet(FacetKind.AUTHOR, 6, listOf("שוע"))
        val order = SearchFacets.entities(listOf(topic, acronym, sage, author)).map { it.main.kind }
        assertEquals(listOf(FacetKind.AUTHOR, FacetKind.SAGE, FacetKind.BOOK, FacetKind.TOPIC), order)
    }

    @Test
    fun `a book never joins a person`() {
        val book = Facet(FacetKind.BOOK, 1, listOf("חפץ חיים", "שמירת הלשון"))
        val author = Facet(FacetKind.AUTHOR, 2, listOf("חפץ חיים", "שמירת הלשון"))
        assertEquals(2, SearchFacets.entities(listOf(book, author)).size)
    }

    @Test
    fun `nothing named, no entity`() {
        assertTrue(SearchFacets.entities(emptyList()).isEmpty())
    }
}
