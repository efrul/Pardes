// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.data

// From HomeScreen.kt: the catalog row's dropdowns (desktop CatalogPresets), with no Compose.

/** Desktop CatalogPresets.Dropdowns of the catalog row, in its order. */
sealed interface CatalogPreset {
    /** A category's books under the category of [labelId]'s title, by section ([sectionIds]) or alone. */
    data class Categories(val labelId: Long, val sectionIds: List<Long>) : CatalogPreset

    /** A book's TOC quick links (the Tur's four parts): label and the line each opens on. */
    data class TocLinks(val bookId: Long, val links: List<Pair<String, Long>>) : CatalogPreset
}

val catalogPresets =
    listOf(
        CatalogPreset.Categories(1, listOf(2, 3, 4)),
        CatalogPreset.Categories(5, listOf(6, 7, 8, 9, 10, 11)),
        CatalogPreset.Categories(13, listOf(14, 15, 16, 17, 18, 19)),
        CatalogPreset.Categories(20, listOf(21, 22, 23, 24, 25)),
        CatalogPreset.Categories(45, listOf(46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 59, 56, 57, 58, 60)),
        CatalogPreset.TocLinks(380, listOf("אורח חיים" to 252_607, "יורה דעה" to 254_014, "אבן העזר" to 254_828, "חושן משפט" to 255_198)),
        CatalogPreset.Categories(62, emptyList()),
    )

/** A dropdown entry: a section header, or a book (at a line for a quick link). */
data class CatalogItem(val label: String, val bookId: Long? = null, val lineId: Long? = null)

data class CatalogMenu(val label: String, val items: List<CatalogItem>)

/** A dropdown as desktop's CatalogDropdown fills it; null when the library has none of its books. */
fun catalogMenu(
    access: CatalogAccess,
    catalog: CatalogPreset,
): CatalogMenu? =
    when (catalog) {
        is CatalogPreset.TocLinks -> {
            val title = access.bookTitle(catalog.bookId)
            title?.let { CatalogMenu(it, catalog.links.map { (label, lineId) -> CatalogItem(label, catalog.bookId, lineId) }) }
        }
        is CatalogPreset.Categories -> {
            val label = access.categoryTitle(catalog.labelId)
            if (catalog.sectionIds.isEmpty()) {
                val books = access.booksFor(catalog.labelId)
                if (label == null || books.isEmpty()) null else CatalogMenu(label, books.map { CatalogItem(it.title, it.id) })
            } else {
                val sections =
                    catalog.sectionIds.mapNotNull { id ->
                        val title = access.categoryTitle(id) ?: return@mapNotNull null
                        val books = access.booksFor(id)
                        if (books.isEmpty()) null else listOf(CatalogItem(title)) + books.map { CatalogItem(it.title, it.id) }
                    }
                if (label == null || sections.isEmpty()) null else CatalogMenu(label, sections.flatten())
            }
        }
    }
