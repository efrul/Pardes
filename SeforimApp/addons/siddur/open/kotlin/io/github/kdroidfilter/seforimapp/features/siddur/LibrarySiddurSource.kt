package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.seforimapp.siddur.ReadingPassage
import io.github.kdroidfilter.seforimapp.siddur.SiddurSource
import io.github.kdroidfilter.seforimapp.siddur.SourceBook
import io.github.kdroidfilter.seforimapp.siddur.SourceHeading
import io.github.kdroidfilter.seforimapp.siddur.VerseAddress
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.util.NavigableMap
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

/** Resolves titles and heading anchors against the library selected in the current app session. */
internal class LibrarySiddurSource(
    private val repository: SeforimRepository,
) : SiddurSource {
    private val books = ConcurrentHashMap<String, SourceBook>()
    private val verses = ConcurrentHashMap<String, NavigableMap<VerseAddress, String>>()

    suspend fun passage(passage: ReadingPassage): List<String>? {
        val title = passage.bookTitle
        val bookVerses =
            verses[title] ?: run {
                val book = repository.getBookByTitle(title) ?: return null
                val lines = repository.getLines(book.id, 0, book.totalLines - 1)
                val byAddress =
                    lines
                        .mapNotNull { line ->
                            line.heRef?.let { verseAddress(title, it) }?.let { address -> address to line.content }
                        }.toMap()
                TreeMap(byAddress).also { verses[title] = it }
            }
        if (passage.first !in bookVerses || passage.last !in bookVerses) return null
        return bookVerses.subMap(passage.first, true, passage.last, true).values.toList()
    }

    override suspend fun book(title: String): SourceBook? {
        books[title]?.let { return it }
        val book = repository.getBookByTitle(title) ?: return null
        val lines = repository.getLines(book.id, 0, book.totalLines - 1)
        if (lines.isEmpty()) return null
        val indices = lines.withIndex().associate { (index, line) -> line.id to index }
        val headings =
            repository.getTocEntriesForBook(book.id).mapNotNull { entry ->
                indices[entry.lineId]?.let { index -> SourceHeading(index, entry.level, entry.text) }
            }
        return SourceBook(lines.map { it.content }, headings).also { books[title] = it }
    }
}

private val verseReference = Regex("""\s+([א-ת׳״'"]+),\s*([א-ת׳״'"]+)\s*""")
private val gematria =
    "אבגדהוזחטיכלמנסעפצקרשת"
        .toList()
        .zip(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 200, 300, 400),
        ).toMap()

private fun verseAddress(
    title: String,
    reference: String,
): VerseAddress? {
    if (!reference.startsWith(title)) return null
    val (chapter, verse) = verseReference.matchEntire(reference.removePrefix(title))?.destructured ?: return null
    return VerseAddress(chapter.sumOf { gematria[it] ?: 0 }, verse.sumOf { gematria[it] ?: 0 })
}
