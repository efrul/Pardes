package io.github.kdroidfilter.seforimapp.legacy.data

import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimlibrary.dao.repository.BookEdition
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** A part of a book and the line it starts at. */
data class BookPart(val title: String, val lineId: Long)

/**
 * What the reader's פרטי הספר pane shows (the main app's BookDetailsLoader, desktop's BookDetails): the categories from
 * the root, the authors, the book's main parts, its editions with their licence, and whether its text came through
 * Otzaria (every source but Sefaria), to credit it.
 */
data class BookDetails(
    val title: String,
    val categories: List<String>,
    val authors: List<String>,
    val parts: List<BookPart>,
    val editions: List<BookEdition>,
    val fromOtzaria: Boolean,
) {
    companion object {
        private const val MAX_DEPTH = 12
        private const val MAX_PARTS = 12
        private const val MAX_ALT_PARTS = 20
        private const val SEFARIA_SOURCE = "Sefaria"

        suspend fun load(repository: SeforimRepository, bookId: Long): BookDetails? {
            val book = repository.getBook(bookId) ?: return null
            val categories = ArrayList<String>()
            var categoryId: Long? = book.categoryId
            while (categoryId != null && categories.size < MAX_DEPTH) {
                val category = repository.getCategory(categoryId) ?: break
                categories.add(0, category.title)
                categoryId = category.parentId
            }
            return BookDetails(
                title = book.title,
                categories = categories,
                authors = book.authors.map { AuthorNames.display(it.name) },
                parts = mainParts(repository, bookId),
                editions = runCatching { repository.getBookEditions(bookId) }.getOrDefault(emptyList()),
                fromOtzaria = runCatching { repository.getSourceById(book.sourceId)?.name }.getOrNull().let { it != null && it != SEFARIA_SOURCE },
            )
        }

        /**
         * The book's main parts: its top entries below a root that only repeats the title, when few enough to mean
         * something, else the first alternative contents that has few (a tractate's chapters rather than its folios).
         */
        private suspend fun mainParts(repository: SeforimRepository, bookId: Long): List<BookPart> {
            var level = repository.getBookRootToc(bookId)
            while (level.size == 1 && level[0].hasChildren) level = repository.getTocChildren(level[0].id)
            if (level.size <= MAX_PARTS) return level.mapNotNull { e -> e.lineId?.let { BookPart(e.text, it) } }
            for (structure in repository.getAltTocStructuresForBook(bookId)) {
                var alt = repository.getAltRootToc(structure.id)
                while (alt.size == 1 && alt[0].hasChildren) alt = repository.getAltTocChildren(alt[0].id)
                if (alt.size in 2..MAX_ALT_PARTS) return alt.mapNotNull { e -> e.lineId?.let { BookPart(e.text, it) } }
            }
            return emptyList()
        }

        /** A licence code in plain Hebrew where it has a name, else as Creative Commons writes it (desktop licenseLabel). */
        fun licenseLabel(code: String): String = when (code) {
            "Public Domain" -> "נחלת הכלל"
            "unknown" -> "רישיון לא ידוע"
            else -> if (code.startsWith("CC-")) "CC " + code.removePrefix("CC-") else code
        }

        const val OTZARIA_CREDIT = "התכנים שלפניכם נאספו, מוינו, סוננו ונערכו בעמל רב ובמסירות על ידי צוות פרויקט 'אוצריא' (otzaria.org)."
        const val OTZARIA_URL = "https://otzaria.org"
    }
}
