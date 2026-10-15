package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One verse of an aliya: the Hebrew line and its Onkelos line (HTML as stored). */
data class VerseText(
    val chapter: Int,
    val verse: Int,
    val hebrew: String,
    val targum: String?,
)

/**
 * The verses of an aliya with Targum Onkelos, for the Shnayim Mikra tool's reader (Android's: desktop opens the Chumash
 * in its Shnayim Mikra mode, a reader feature). The aliyos are desktop's ([aliyosOf], combined for a double parsha).
 */
internal object TorahText {
    /** [parsha]'s aliya [aliya] (0–6), its verses in order, from the library's Chumash and "תרגום אונקלוס על …". */
    suspend fun aliya(
        repo: SeforimRepository,
        parsha: Parsha,
        aliya: Int,
    ): List<VerseText> =
        withContext(Dispatchers.IO) {
            val title = parshaPlace(parsha)?.bookTitle ?: return@withContext emptyList()
            val (from, to) = aliyosOf(parsha)[aliya]
            val torah = LibraryLookup.findBook(repo, listOf(title)) ?: return@withContext emptyList()
            val onkelos = LibraryLookup.findBook(repo, listOf("תרגום אונקלוס על $title"))
            val hebrew = versesOf(repo, torah, from, to)
            val targum = onkelos?.let { runCatching { versesOf(repo, it, from, to) }.getOrDefault(emptyMap()) } ?: emptyMap()
            hebrew.entries.sortedBy { it.key }.map { (k, html) -> VerseText(k / 1000, k % 1000, html, targum[k]) }
        }

    private suspend fun versesOf(
        repo: SeforimRepository,
        book: Book,
        from: Verse,
        to: Verse,
    ): Map<Int, String> {
        val toc = repo.getBookToc(book.id)
        val entry = LibraryLookup.findTocEntry(toc, listOf("פרק ${hebrewNumeral(from.chapter)}"))
        val start = entry?.lineId?.let { repo.getLine(it)?.lineIndex } ?: 0
        val out = LinkedHashMap<Int, String>()
        val startKey = from.chapter * 1000 + from.verse
        val endKey = to.chapter * 1000 + to.verse
        var index = start
        val last = if (book.totalLines > 0) book.totalLines else start + 4000
        while (index <= last) {
            val lines = repo.getLines(book.id, index, index + 199)
            if (lines.isEmpty()) break
            for (l in lines.sortedBy { it.lineIndex }) {
                val ref = l.heRef ?: continue
                val (ch, v) = parseRef(ref) ?: continue
                val k = ch * 1000 + v
                if (k > endKey) return out
                if (k >= startKey) out[k] = l.content
            }
            index += 200
        }
        return out
    }

    /** "בראשית ו, ט" → (6, 9). */
    fun parseRef(ref: String): Pair<Int, Int>? {
        val m = Regex("(\\S+),\\s*(\\S+)\\s*$").find(ref.trim()) ?: return null
        val ch = HebrewText.value(m.groupValues[1])
        val v = HebrewText.value(m.groupValues[2])
        return if (ch > 0 && v > 0) ch to v else null
    }
}
