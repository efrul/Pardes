package io.github.kdroidfilter.seforimapp.features.bookcontent.references

import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
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
                "CREATE TABLE link (id INTEGER PRIMARY KEY, sourceLineId INTEGER, targetLineId INTEGER, connectionTypeId INTEGER)",
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
        it.executeUpdate("INSERT INTO link (sourceLineId, targetLineId, connectionTypeId) VALUES ($from, $to, ${type.ordinal + 1})")
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
