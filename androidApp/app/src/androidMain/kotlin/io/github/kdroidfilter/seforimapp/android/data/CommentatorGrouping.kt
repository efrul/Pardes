package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.PubDate
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Commentator grouping and ordering, ported from the desktop CommentariesUseCase so the checklist reads the same:
 * buckets such as "ראשונים על התנ״ך" in editorial order, commentators in chronological order inside each bucket.
 */
class CommentatorGrouping(private val repository: SeforimRepository) {
    private val categoryCache = mutableMapOf<Long, Category?>()

    /** Groups [books] (commentary books linked from the open book) into ordered (label, books) buckets. */
    suspend fun group(books: List<Pair<Book?, String>>): List<Pair<String, List<Int>>> {
        val byLabel = LinkedHashMap<String, MutableList<Int>>()
        books.forEachIndexed { i, (book, _) -> byLabel.getOrPut(resolveGroupLabel(book, categoryCache)) { mutableListOf() } += i }
        fun year(i: Int): Int {
            val (book, name) = books[i]
            canonicalRank(book, name)?.let { return it }
            book?.pubDates?.let { extractEarliestYear(it) }?.let { return it }
            return Int.MAX_VALUE
        }
        return byLabel.map { (label, idx) ->
            val sorted = idx.sortedWith(
                compareBy(
                    { if (categoryCache[books[it].first?.categoryId]?.title?.startsWith("הערות על") == true) 1 else 0 },
                    { year(it) },
                    { books[it].second },
                ),
            )
            Triple(label, sorted, sorted.minOfOrNull { year(it) } ?: Int.MAX_VALUE)
        }.sortedWith(compareBy<Triple<String, List<Int>, Int>> { groupRank(it.first) }.thenBy { it.third }.thenBy { it.first })
            .map { it.first to it.second }
    }

    private suspend fun runSuspendCatching(block: suspend () -> Category?): Result<Category?> =
        try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun resolveGroupLabel(
        book: Book?,
        cache: MutableMap<Long, Category?>,
    ): String {
        if (book == null) return ""

        suspend fun loadCategory(id: Long): Category? {
            cache[id]?.let { return it }
            val loaded = runSuspendCatching { repository.getCategory(id) }.getOrNull()
            cache[id] = loaded
            return loaded
        }

        // Collect the full ancestor chain (book.categoryId → root). Cap depth as
        // a safety net against accidental cycles in the closure table.
        val chain = mutableListOf<Category>()
        var currentId: Long? = book.categoryId
        var depth = 0
        while (currentId != null && depth < 32) {
            currentCoroutineContext().ensureActive()
            val cat = loadCategory(currentId) ?: break
            chain.add(cat)
            currentId = cat.parentId
            depth++
        }
        if (chain.isEmpty()) return ""

        // Pass 1: the strongest, most-informative bucket — "X על Y" labels
        // anchored on a primary corpus (Tanakh / Talmud / Mishna / Shas) win
        // over everything else, scanned across the whole ancestor chain. This
        // is important because a sub-commentary on Rif (chain:
        // `מפרשים < רי״ף < ראשונים על התלמוד`) must surface as
        // `ראשונים על התלמוד`, NOT as `מפרשים על רי״ף`.
        for (category in chain) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            if (
                title.contains("על התנ״ך") ||
                title.contains("על התלמוד") ||
                title.contains("על המשנה") ||
                title.contains("על המשניות") ||
                title.contains("על הש\"ס") ||
                title.contains("על השס")
            ) {
                return title
            }
        }

        // Pass 2: hard-coded multi-book families (chevruta, dictionaries,
        // contemporary authors).
        for (category in chain) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            if (title == "ביאור חברותא" || title == "הערות על ביאור חברותא") return "חברותא"
            if (title.contains("מילונים")) return title
            if (title == "מחברי זמננו") return title
        }

        // Pass 3: "מפרשים" — only when no higher-level "X על Y" anchor exists
        //   (e.g. Mishneh Torah's super-commentaries). Check if the parent's
        //   ancestors include a corpus reference. If so, use that. Otherwise use
        //   immediate parent.
        for ((idx, category) in chain.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (category.title == "מפרשים") {
                // Check if "ראשונים" appears further up the chain; if so, use its corpus parent.
                for (ancestorIdx in (idx + 1) until chain.size) {
                    val ancestor = chain[ancestorIdx]
                    if (ancestor.title == "ראשונים") {
                        // Found "ראשונים" — use its parent corpus
                        val corpus = chain.getOrNull(ancestorIdx + 1)
                        if (corpus != null) {
                            return "ראשונים על ${when (corpus.title) {
                                "בבלי", "בבל" -> "התלמוד"
                                "תנ״ך", "תנך" -> "התנ״ך"
                                "משנה" -> "המשנה"
                                "ש\"ס", "ש״ס", "שס" -> "הש\"ס"
                                else -> corpus.title
                            }}"
                        }
                        break
                    }
                }
                // Fallback: use immediate parent
                val parent = chain.getOrNull(idx + 1)
                if (parent != null && parent.title.isNotBlank()) {
                    return "מפרשים על ${parent.title}"
                }
                return "מפרשים"
            }
        }

        // Pass 4: bare "ראשונים" / "אחרונים" anywhere in the chain. Look for corpus
        //   references further up; if found, use those. Otherwise use immediate parent.
        for ((idx, category) in chain.withIndex()) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            if (title == "ראשונים" || title == "אחרונים") {
                // Check ancestors for corpus references (בבלי, תנ״ך, משנה, etc.)
                for (ancestorIdx in (idx + 1) until chain.size) {
                    val ancestor = chain[ancestorIdx]
                    val corpusLabel =
                        when (ancestor.title) {
                            "בבלי", "בבל" -> "התלמוד"
                            "תנ״ך", "תנך" -> "התנ״ך"
                            "משנה" -> "המשנה"
                            "ש\"ס", "ש״ס", "שס" -> "הש\"ס"
                            else -> null
                        }
                    if (corpusLabel != null) {
                        return "$title על $corpusLabel"
                    }
                }
                // Fallback: use immediate parent
                val parent = chain.getOrNull(idx + 1)
                if (parent != null && parent.title.isNotBlank()) {
                    val parentLabel =
                        when (parent.title) {
                            "תנ״ך", "תנך" -> "התנ״ך"
                            "תלמוד" -> "התלמוד"
                            "משנה" -> "המשנה"
                            "ש\"ס", "ש״ס", "שס" -> "הש\"ס"
                            else -> parent.title
                        }
                    return "$title על $parentLabel"
                }
                return title
            }
        }

        // Second pass: collapse fragmented sub-trees (Targumim, Midrash, Kabbalah, Chasidut)
        // into a single top-level bucket so multi-volume editorial families don't
        // create one group per book.
        for (category in chain) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            // Targums: "תורה < תרגום אונקלוס < תרגומים < תנ״ך"; also bare "תרגום ירושלמי" etc.
            if (title == "תרגומים" || title.startsWith("תרגום ") || title.startsWith("תפסיר ")) {
                return "תרגומים"
            }
            // Midrash: "מדרש לקח טוב < אגדה < מדרש", "מדרש רבה < אגדה < מדרש".
            if (title == "מדרש") return "מדרש"
            // Kabbalah: "ספרי קבלה נוספים < קבלה", "זהר < קבלה".
            if (title == "קבלה") return "קבלה"
            // Chasidut sub-tree.
            if (title == "חסידות" || title.contains("חסידות")) return "חסידות"
        }

        // Fallback: use the deepest reasonable bucket from the root side
        // (avoid single-author categories like חזקוני/מהר״ל that produce singleton groups).
        val rootLevel = chain.lastOrNull()?.title
        return rootLevel ?: chain.first().title
    }

    private fun groupRank(label: String): Int {
        // Tanach buckets
        if (label == "תרגומים") return 10
        if (label.startsWith("ראשונים על המשנה")) return 20
        if (label.startsWith("ראשונים על התלמוד") || label.startsWith("ראשונים על הש")) return 25
        if (label.startsWith("ראשונים על התנ״ך")) return 30
        if (label.startsWith("אחרונים על המשנה")) return 40
        if (label.startsWith("אחרונים על התלמוד") || label.startsWith("אחרונים על הש")) return 45
        if (label.startsWith("אחרונים על התנ״ך")) return 50
        if (label.startsWith("מפרשים על")) return 55
        if (label == "ראשונים") return 60
        if (label == "אחרונים") return 65
        if (label.startsWith("ראשונים על ")) return 67
        if (label.startsWith("אחרונים על ")) return 68
        if (label == "מדרש") return 70
        if (label == "חסידות") return 80
        if (label == "קבלה") return 90
        if (label == "חברותא") return 100
        if (label.contains("מילונים")) return 110
        if (label == "מחברי זמננו") return 120
        return GROUP_RANK_DEFAULT
    }

    private fun canonicalRank(
        book: Book?,
        displayName: String,
    ): Int? {
        if (book == null) return null

        fun normalize(s: String): String = s.replace('"', '״').replace('\'', '׳').trim()

        val authorNames = book.authors.map { normalize(it.name) }
        for (author in authorNames) {
            CANONICAL_AUTHOR_YEAR[author]?.let { return it }
        }
        val title = normalize(book.title)
        for ((pattern, year) in CANONICAL_TITLE_YEAR) {
            if (title.contains(pattern)) return year
        }
        val display = normalize(displayName)
        for ((pattern, year) in CANONICAL_TITLE_YEAR) {
            if (display.contains(pattern)) return year
        }
        return null
    }

    private fun extractEarliestYear(pubDates: List<PubDate>): Int? {
        var best: Int? = null
        for (pub in pubDates) {
            val raw = pub.date
            val candidate =
                YEAR_REGEX.find(raw)?.value?.toIntOrNull()
                    ?: if (raw.contains("Ancient", ignoreCase = true)) Int.MIN_VALUE else null
            if (candidate != null) {
                best = if (best == null || candidate < best) candidate else best
            }
        }
        return best
    }

    private companion object {
        const val GROUP_RANK_DEFAULT = 1_000
private val YEAR_REGEX = Regex("""-?\d{3,4}""")

        val CANONICAL_AUTHOR_YEAR: Map<String, Int> =
            mapOf(
                // Geonim
                "ר' סעדיה גאון" to 882,
                "סעדיה גאון" to 882,
                "רב סעדיה גאון" to 882,
                // Rishonim – Ashkenaz / Tsarfat
                "רש״י" to 1040,
                "רשב״ם" to 1085,
                "ר' יוסף קרא" to 1065,
                "יוסף בכור שור" to 1140,
                "אבן עזרא" to 1089,
                "ר' אברהם אבן עזרא" to 1089,
                "רד״ק" to 1160,
                "רמב״ן" to 1194,
                "חזקוני" to 1240,
                "רא״ש" to 1250,
                "רבנו בחיי" to 1255,
                "בחיי בן אשר" to 1255,
                "יעקב בן אשר" to 1269,
                "רלב״ג" to 1288,
                "מנחם ריקנטי" to 1290,
                "אברבנאל" to 1437,
                "עובדיה מברטנורא" to 1445,
                "אליהו בן אברהם מזרחי" to 1455,
                "ספורנו" to 1475,
                // Acharonim
                "אלשיך" to 1508,
                "מהר״ל" to 1520,
                "שלמה אפרים מלונטשיץ" to 1550,
                "ש״ך" to 1622,
                "חיים בן עטר" to 1696,
                "אליהו בן שלמה זלמן מווילנה" to 1720,
                "משה סופר" to 1762,
                "נתן נטע שפירא" to 1585,
                "יעקב צבי מקלנבורג" to 1785,
                "מלבי״ם" to 1809,
                "נפתלי צבי יהודה ברלין" to 1816,
                "יוסף חיים" to 1834,
                "מאיר שמחה הכהן" to 1843,
                "רב יוסף דוב הלוי סולובייצ'ק" to 1903,
            )

        // Title fragments (normalized to gershayim) → canonical year. Acts as a
        // fallback when the author table is sparse for older works.
        val CANONICAL_TITLE_YEAR: List<Pair<String, Int>> =
            listOf(
                "רס״ג" to 882,
                "ר' סעדיה גאון" to 882,
                "רש״י" to 1040,
                "רשב״ם" to 1085,
                "אבן עזרא" to 1089,
                "אב״ע" to 1089,
                "ראב״ע" to 1089,
                "בכור שור" to 1140,
                "רד״ק" to 1160,
                "רמב״ן" to 1194,
                "חזקוני" to 1240,
                "רא״ש" to 1250,
                "רבנו בחיי" to 1255,
                "רבינו בחיי" to 1255,
                "בעל הטורים" to 1269,
                "הטור הארוך" to 1269,
                "רלב״ג" to 1288,
                "פענח רזא" to 1295,
                "ריקנטי" to 1290,
                "רקנאטי" to 1290,
                "דעת זקנים" to 1290,
                "הדר זקנים" to 1290,
                "אברבנאל" to 1437,
                "ברטנורא" to 1445,
                "מזרחי" to 1455,
                "ספורנו" to 1475,
                "צרור המור" to 1440,
                "תולדות יצחק" to 1458,
                "אלשיך" to 1508,
                "מהר״ל" to 1520,
                "גור אריה" to 1520,
                "כלי יקר" to 1550,
                "שפתי כהן" to 1622,
                "אור החיים" to 1696,
                "מנחת שי" to 1565,
                "שפתי חכמים" to 1641,
                "אבי עזר" to 1750,
                "אדרת אליהו" to 1720,
                "הגר״א" to 1720,
                "חתם סופר" to 1762,
                "הכתב והקבלה" to 1785,
                "תפארת יהונתן" to 1690,
                "אהבת יהונתן" to 1690,
                "מלבי״ם" to 1809,
                "העמק דבר" to 1816,
                "נצי״ב" to 1816,
                "רש״ר הירש" to 1808,
                "בן איש חי" to 1834,
                "תורה תמימה" to 1860,
                "פרדס יוסף" to 1880,
                "משך חכמה" to 1843,
                "בית הלוי" to 1820,
                "נתינה לגר" to 1875,
                "תרגום אונקלוס" to -100,
                "תרגום יונתן" to 100,
                "תרגום ירושלמי" to 200,
                "תפסיר רס״ג" to 882,
                "מדרש" to 500,
                "בראשית רבה" to 400,
                "שמות רבה" to 400,
                "ויקרא רבה" to 400,
                "מדרש תנחומא" to 500,
                "תנחומא בובר" to 500,
                "פסיקתא" to 600,
                "מכילתא" to 200,
                "ספרי" to 200,
            )
    }
}
