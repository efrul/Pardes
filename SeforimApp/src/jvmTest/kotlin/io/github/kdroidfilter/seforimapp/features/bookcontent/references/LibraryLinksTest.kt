package io.github.kdroidfilter.seforimapp.features.bookcontent.references

import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryLinksTest {
    private val file = Files.createTempFile("library-links", ".db")
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$file")

    init {
        connection.createStatement().use { statement ->
            // The library's tables, with the columns the links are read by
            statement.executeUpdate("CREATE TABLE connection_type (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
            statement.executeUpdate("CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT, categoryId INTEGER, orderIndex INTEGER)")
            statement.executeUpdate(
                "CREATE TABLE line (id INTEGER PRIMARY KEY, bookId INTEGER, lineIndex INTEGER, content TEXT, heRef TEXT)",
            )
            statement.executeUpdate(
                "CREATE TABLE link (id INTEGER PRIMARY KEY, sourceBookId INTEGER, targetBookId INTEGER, " +
                    "sourceLineId INTEGER, targetLineId INTEGER, connectionTypeId INTEGER)",
            )
            ConnectionType.entries.forEach {
                statement.executeUpdate("INSERT INTO connection_type VALUES (${it.ordinal + 1}, '${it.name}')")
            }
            statement.executeUpdate("INSERT INTO book VALUES (1, 'ברכות', 10, 5), (2, 'דברים', 20, 1), (3, 'בית יוסף', 30, 9)")
            statement.executeUpdate(
                "INSERT INTO line VALUES (11, 1, 0, 'מאימתי', 'ברכות ב., א'), (12, 1, 1, 'עד חצות', 'ברכות ב., ב'), " +
                    "(21, 2, 7, 'ובשכבך', 'דברים ו, ז'), (31, 3, 3, 'ומאימתי', 'בית יוסף, רלה'), (32, 3, 4, 'אחר', 'בית יוסף, רלו')",
            )
        }
    }

    @AfterTest
    fun close() {
        connection.close()
        Files.deleteIfExists(file)
    }

    private fun link(
        from: Long,
        to: Long,
        type: ConnectionType,
    ) = connection.createStatement().use {
        // A line's book is its id's tens here
        it.executeUpdate(
            "INSERT INTO link (sourceBookId, targetBookId, sourceLineId, targetLineId, connectionTypeId) " +
                "VALUES (${from / 10}, ${to / 10}, $from, $to, ${type.ordinal + 1})",
        )
    }

    @Test
    fun aLinesLinksAreThoseThatStartFromItAndThoseThatReachIt() {
        link(11, 21, ConnectionType.OTHER)
        link(31, 11, ConnectionType.RELATED)
        val found = LibraryLinks.linked(connection, listOf(11L))
        assertEquals(listOf(21L, 31L), found.map { it.line.id })
        assertEquals(listOf(ConnectionType.OTHER, ConnectionType.RELATED), found.map { it.connectionType })
        assertTrue(found.all { it.fromLineId == 11L })
        val verse = found.first()
        assertEquals("דברים", verse.bookTitle)
        assertEquals(20L, verse.categoryId)
        assertEquals(1, verse.bookOrderIndex)
        assertEquals("ובשכבך", verse.line.content)
        assertEquals("דברים ו, ז", verse.line.heRef)
        assertEquals(7, verse.line.lineIndex)
        assertTrue(LibraryLinks.anyLinked(connection, listOf(11L)))
    }

    @Test
    fun theKindsThePanesAlreadyListAreLeftToThem() {
        link(11, 21, ConnectionType.COMMENTARY)
        link(11, 31, ConnectionType.REFERENCE)
        link(11, 32, ConnectionType.EIN_MISHPAT)
        link(32, 11, ConnectionType.MESORAT_HASHAS)
        assertTrue(LibraryLinks.linked(connection, listOf(11L)).isEmpty())
        assertFalse(LibraryLinks.anyLinked(connection, listOf(11L)))
        link(11, 21, ConnectionType.QUOTATION)
        link(11, 31, ConnectionType.MISHNAH_IN_TALMUD)
        assertEquals(listOf(21L, 31L), LibraryLinks.linked(connection, listOf(11L)).map { it.line.id })
    }

    @Test
    fun aLineIsListedOnceAndTheSelectedLinesNotAtAll() {
        link(11, 21, ConnectionType.OTHER)
        link(21, 11, ConnectionType.QUOTATION)
        link(12, 21, ConnectionType.OTHER)
        link(11, 12, ConnectionType.RELATED)
        val found = LibraryLinks.linked(connection, listOf(11L, 12L))
        assertEquals(listOf(21L), found.map { it.line.id })
        // Lines linked to one another only have nothing to list
        connection.createStatement().use { it.executeUpdate("DELETE FROM link WHERE targetLineId = 21 OR sourceLineId = 21") }
        assertTrue(LibraryLinks.linked(connection, listOf(11L, 12L)).isEmpty())
        assertFalse(LibraryLinks.anyLinked(connection, listOf(11L, 12L)))
        assertTrue(LibraryLinks.anyLinked(connection, listOf(11L)))
    }

    @Test
    fun theLinesOfSomeBooksALineIsLinkedTo() {
        link(11, 21, ConnectionType.OTHER)
        link(21, 11, ConnectionType.QUOTATION)
        link(12, 21, ConnectionType.REFERENCE)
        link(11, 31, ConnectionType.OTHER)
        link(11, 21, ConnectionType.COMMENTARY)
        // דברים is under category 20, בית יוסף under 30
        val verses = LibraryLinks.linkedIn(connection, listOf(20L), listOf(11L, 12L))
        assertEquals(setOf(11L, 12L), verses.keys)
        assertEquals(listOf(21L), verses.getValue(11).map { it.id })
        assertEquals("ובשכבך", verses.getValue(12).single().content)
        assertEquals(listOf(31L), LibraryLinks.linkedIn(connection, listOf(30L), listOf(11L)).getValue(11).map { it.id })
        assertTrue(LibraryLinks.linkedIn(connection, listOf(40L), listOf(11L)).isEmpty())
        assertTrue(LibraryLinks.linkedIn(connection, emptyList(), listOf(11L)).isEmpty())
        // A commentary's link alone is none
        assertTrue(LibraryLinks.linkedIn(connection, listOf(20L), listOf(32L)).isEmpty())
        assertEquals(setOf(1L, 3L), LibraryLinks.booksIn(connection, listOf(10L, 30L)))
        assertTrue(LibraryLinks.booksIn(connection, emptyList()).isEmpty())
    }

    @Test
    fun theLinesBeforeALineInItsBook() {
        val second = Line(id = 32, bookId = 3, lineIndex = 4, content = "", heRef = null)
        assertEquals(listOf(31L), LibraryLinks.idsBefore(connection, second, 10))
        assertTrue(LibraryLinks.idsBefore(connection, second.copy(lineIndex = 0), 10).isEmpty())
        assertTrue(LibraryLinks.idsBefore(connection, second, 0).isEmpty())
        // ברכות's two lines, at 0 and 1
        assertEquals(listOf(11L), LibraryLinks.idsBefore(connection, Line(id = 12, bookId = 1, lineIndex = 1, content = ""), 1))
    }

    @Test
    fun aBooksNotesAndTheLinesTheyStartWith() {
        connection.createStatement().use {
            it.executeUpdate("INSERT INTO book VALUES (4, 'הערות על בית יוסף', 30, 10)")
            it.executeUpdate(
                "INSERT INTO line VALUES (41, 4, 0, '<b>(1)</b> הערה ראשונה ארוכה', null), (42, 4, 1, '<b>(2)</b> שנייה', null)",
            )
        }
        assertEquals(mapOf(3L to 4L), LibraryLinks.booksWithNotes(connection))
        link(31, 41, ConnectionType.COMMENTARY)
        link(31, 42, ConnectionType.COMMENTARY)
        link(31, 11, ConnectionType.OTHER)
        assertEquals(
            mapOf(31L to listOf(41L to "<b>(1)</b> הער", 42L to "<b>(2)</b> שני")),
            LibraryLinks.linkedStarts(connection, 4, listOf(31L, 32L), 14),
        )
        assertTrue(LibraryLinks.linkedStarts(connection, 4, emptyList(), 14).isEmpty())
    }

    @Test
    fun noLinesNoLinks() {
        assertTrue(LibraryLinks.linked(connection, emptyList()).isEmpty())
        assertFalse(LibraryLinks.anyLinked(connection, emptyList()))
    }

    @Test
    fun theOtherKindsAreATabOfTheirOwnAfterTheReferences() {
        link(11, 21, ConnectionType.OTHER)
        link(11, 31, ConnectionType.RELATED)
        link(11, 32, ConnectionType.OTHER)
        val more = LibraryLinks.linked(connection, listOf(11L))
        val reference = more.first { it.line.id == 31L }.copy(connectionType = ConnectionType.REFERENCE)
        val references =
            groupReferences(
                listOf(reference) + more,
                mapOf(20L to listOf(Category(id = 20, title = "תנ״ך")), 30L to listOf(Category(id = 30, title = "הלכה"))),
            ) { null }
        // בית יוסף twice: as a reference, and with its two other links
        assertEquals(listOf(ReferenceKind.Reference, ReferenceKind.More, ReferenceKind.More), references.books.map { it.kind })
        assertEquals(listOf(3L, 3L, 2L), references.books.map { it.bookId })
        assertEquals(listOf(ReferenceKind.More), references.kindTabs)
        assertEquals(listOf(30L, ReferenceKind.More.tabId, ReferenceKind.More.tabId), references.books.map { it.tabId })
        assertEquals(LibraryLinks.KINDS, ConnectionType.entries.filterTo(HashSet()) { ReferenceKind.of(it) == ReferenceKind.More })
    }
}
