package io.github.kdroidfilter.seforimapp.legacy.data

/** A row of a catalog dropdown: a section's title (no book), or a book, at a line for a link into its contents. */
data class CatalogItem(val label: String, val bookId: Long? = null, val lineId: Long? = null)

/** A dropdown of the Home's catalog row: its label and its rows. */
data class CatalogMenu(val label: String, val items: List<CatalogItem>)

/**
 * The Home's catalog row as the main app builds it (HomeScreen `catalogs`, a port of desktop 0.6.1 CatalogPresets,
 * with CatalogAccess's display rules), read from the database rather than catalog.pb, which holds the same tree:
 *  - "תלמוד" before the titles of the Bavli and the Yerushalmi;
 *  - no "מפרשים" in the Mishneh Torah's lists, no "הקדמה" or "פרי מגדים" in the Shulchan Aruch's;
 *  - a book's title without its categories' names ("משנה ברכות" under משנה is ברכות).
 * Each section lists its own books, not its subcategories'; a dropdown the library has no book of is left out.
 */
object Catalog {
    private sealed interface Preset {
        data class Categories(val labelId: Long, val sectionIds: List<Long>) : Preset

        data class TocLinks(val bookId: Long, val links: List<Pair<String, Long>>) : Preset
    }

    private val presets = listOf(
        Preset.Categories(1, listOf(2, 3, 4)),
        Preset.Categories(5, listOf(6, 7, 8, 9, 10, 11)),
        Preset.Categories(13, listOf(14, 15, 16, 17, 18, 19)),
        Preset.Categories(20, listOf(21, 22, 23, 24, 25)),
        Preset.Categories(45, listOf(46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 59, 56, 57, 58, 60)),
        Preset.TocLinks(380, listOf("אורח חיים" to 252_607, "יורה דעה" to 254_014, "אבן העזר" to 254_828, "חושן משפט" to 255_198)),
        Preset.Categories(62, emptyList()),
    )

    private const val BAVLI = 13L
    private const val YERUSHALMI = 20L
    private const val MISHNE_TORAH = 45L
    private const val SHULCHAN_ARUCH = 62L
    private const val MEFARSHIM_PREFIX = "מפרשים"
    private const val TALMUD_FALLBACK = "תלמוד"
    private val SHULCHAN_ARUCH_EXCLUDED_PREFIXES = listOf("הקדמה", "פרי מגדים")

    @Volatile
    private var cached: Pair<Library.Opened, List<CatalogMenu>>? = null

    /** The dropdowns of [opened], built once per library (blocking: call it off the main thread). */
    fun menus(opened: Library.Opened): List<CatalogMenu> {
        cached?.takeIf { it.first === opened }?.let { return it.second }
        val categories = opened.categories().associateBy { it.id }
        fun title(id: Long): String? {
            val row = categories[id] ?: return null
            if (id != BAVLI && id != YERUSHALMI) return row.title
            val parent = row.parentId?.let { categories[it]?.title }?.takeIf { it.isNotBlank() } ?: TALMUD_FALLBACK
            return "$parent ${row.title}"
        }

        fun books(categoryId: Long): List<CatalogItem> {
            if (categoryId !in categories) return emptyList()
            val labels = ArrayList<String>()
            var id: Long? = categoryId
            while (id != null && labels.size < 50) {
                val row = categories[id] ?: break
                if (row.title.isNotBlank()) labels += row.title
                id = row.parentId
            }
            val parent = categories[categoryId]?.parentId
            val excluded = when {
                categoryId == MISHNE_TORAH || parent == MISHNE_TORAH -> listOf(MEFARSHIM_PREFIX)
                categoryId == SHULCHAN_ARUCH || parent == SHULCHAN_ARUCH -> SHULCHAN_ARUCH_EXCLUDED_PREFIXES
                else -> emptyList()
            }
            return opened.booksIn(categoryId).mapNotNull { book ->
                val shown = labels.distinct().fold(book.title) { t, label -> stripLabelPrefix(label, t) }
                if (excluded.any { shown.trimStart().startsWith(it) }) null else CatalogItem(shown, book.id)
            }
        }

        val menus = presets.mapNotNull { preset ->
            when (preset) {
                is Preset.TocLinks -> opened.bookTitle(preset.bookId)?.let { t ->
                    CatalogMenu(t, preset.links.map { (label, line) -> CatalogItem(label, preset.bookId, line) })
                }
                is Preset.Categories -> {
                    val label = title(preset.labelId)
                    if (preset.sectionIds.isEmpty()) {
                        val list = books(preset.labelId)
                        if (label == null || list.isEmpty()) null else CatalogMenu(label, list)
                    } else {
                        val sections = preset.sectionIds.mapNotNull { id ->
                            val section = title(id) ?: return@mapNotNull null
                            val list = books(id)
                            if (list.isEmpty()) null else listOf(CatalogItem(section)) + list
                        }
                        if (label == null || sections.isEmpty()) null else CatalogMenu(label, sections.flatten())
                    }
                }
            }
        }
        cached = opened to menus
        return menus
    }

    /** CatalogAccess.stripLabelPrefix: [title] without [label] and what follows it (a comma, a dash, a plus, spaces). */
    private fun stripLabelPrefix(label: String, title: String): String {
        if (label.isBlank()) return title
        val prefix = Regex.escape(label)
        val patterns = listOf(
            Regex("^$prefix\\s*,\\s*"),
            Regex("^$prefix,\\s*"),
            Regex("^$prefix\\s*[:–—-]\\s*"),
            Regex("^$prefix\\s*\\+\\s*"),
            Regex("^$prefix\\s+"),
        )
        for (p in patterns) {
            val match = p.find(title) ?: continue
            return title.substring(match.range.last + 1).trimStart()
        }
        return title
    }
}
