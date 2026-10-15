package io.github.kdroidfilter.seforimapp.android.data

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.TefilaRules
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Siddur books of each nusach in the library. */
enum class SiddurNusach(val label: String, val books: List<String>) {
    Ashkenaz("נוסח אשכנז", listOf("סידור אשכנז")),
    Sefard("נוסח ספרד", listOf("סידור ספרד לשבת", "סידור ספרד", "סידור ספרד לימות החול")),
    Edot("עדות המזרח", listOf("סידור נוסח עדות המזרח", "סידור עדות המזרח")),
}

/** Prayers of the desktop smart siddur, each with the TOC headings it is found under ("parent/child"). */
enum class SiddurPart(val label: String, val candidates: List<String>) {
    Shacharit("שחרית", listOf("ימי חול/תפילת שחרית", "שחרית לימות החול", "שחרית לימי החול", "תפילת שחרית")),
    Mincha("מנחה", listOf("ימי חול/תפילת מנחה", "מנחה לימות החול", "מנחה לימי החול", "תפילת מנחה")),
    Maariv("ערבית", listOf("ימי חול/מעריב", "ערבית לימות החול", "ערבית לימי החול", "מעריב", "ערבית")),
    KabbalatShabbat("קבלת שבת", listOf("קבלת שבת")),
    MaarivShabbat("ערבית לשבת", listOf("שבת/מעריב", "ערבית לשבת", "ערבית של שבת", "ערבית לליל שבת")),
    LeilShabbat("שולחן ליל שבת", listOf("שבת/ליל שבת", "סדר ליל שבת", "ליל שבת", "שלום עליכם")),
    ShacharitShabbat("שחרית ומוסף לשבת", listOf("שבת/שחרית", "שחרית לשבת", "שחרית של שבת", "שחרית ליום השבת")),
    SeudatShabbat("סעודת שבת", listOf("שבת/סעודת היום", "סעודת שבת", "סדר סעודת שבת", "קידושא רבא", "קידושא רבה")),
    MinchaShabbat("מנחה לשבת", listOf("מנחה לשבת קודש", "מנחה לשבת ויום טוב", "מנחה של שבת", "שבת/מנחה", "מנחה לשבת")),
    Havdala("הבדלה", listOf("הבדלה")),
    ShacharitYomTov("שחרית ומוסף", listOf("תפילות לשלוש רגלים", "שלוש רגלים", "שלש רגלים", "ליום טוב", "יום טוב")),
    SeudatYomTov("קידושא רבא", listOf("קידושא רבה", "קידושא רבא", "קידוש ליום טוב")),
    LeilYomTov("קידוש וסעודת הלילה", listOf("קידוש לליל יום טוב", "קידוש ליל יום טוב", "קידוש ליום טוב", "ליל יום טוב")),
    Neila("נעילה", listOf("נעילה", "תפלת נעילה", "תפילת נעילה")),
    Hallel("הלל", listOf("ראש חודש/הלל", "הלל")),
    BirkatHamazon("ברכת המזון", listOf("ברכת המזון")),
    MeeinShalosh("ברכה מעין שלוש", listOf("ברכה אחרונה מעין שלוש", "ברכת מעין שלוש", "ברכה מעין שלש", "מעין שלוש", "מעין שלש")),
    KriatShemaAlHamita("קריאת שמע על המיטה", listOf("קריאת שמע על המיטה", "קריאת שמע שעל המיטה", "קריאת שמע שעל המטה")),
    TefilatHaderech("תפילת הדרך", listOf("תפילת הדרך", "תפלת הדרך")),
    KiddushLevana("קידוש לבנה", listOf("ברכת הלבנה", "קידוש לבנה", "קידוש הלבנה")),
    SfiratHaomer("ספירת העומר", listOf("ספירת העומר")),
    NerotChanuka("נרות חנוכה", listOf("הדלקת נרות חנוכה", "נרות חנוכה", "חנוכה")),
}

/** A TOC section of a siddur with its line range and sub-headings. */
class SiddurSection(val book: Book, val entry: TocEntry, val path: List<String>, val start: Int, val end: Int, val children: List<Pair<String, Int>>) {
    internal val normText: String = HebrewText.normalize(entry.text)
    internal val normPath: List<String> = path.map { HebrewText.normalize(it) }
}

/** The smart-siddur logic: which prayers today, where they are in each siddur, which lines apply. */
object SiddurData {
    fun partsFor(jc: JewishCalendar, tomorrow: JewishCalendar): List<SiddurPart> {
        val out = mutableListOf<SiddurPart>()
        val shabbat = jc.weekday == 7
        val nightIsHoly = tomorrow.isAssurBemelacha
        when {
            jc.isYomKippur -> out += listOf(SiddurPart.ShacharitYomTov, SiddurPart.Mincha, SiddurPart.Neila, SiddurPart.Maariv, SiddurPart.Havdala)
            shabbat -> {
                out += listOf(SiddurPart.ShacharitShabbat, SiddurPart.SeudatShabbat, SiddurPart.MinchaShabbat)
                if (!nightIsHoly) out += listOf(SiddurPart.Maariv, SiddurPart.Havdala)
            }
            jc.isYomTovAssurBemelacha -> {
                out += listOf(SiddurPart.ShacharitYomTov, SiddurPart.SeudatYomTov, SiddurPart.Mincha)
                out += if (nightIsHoly) listOf(SiddurPart.Maariv, SiddurPart.LeilYomTov) else listOf(SiddurPart.Maariv, SiddurPart.Havdala)
            }
            jc.weekday == 6 -> out += listOf(SiddurPart.Shacharit, SiddurPart.Mincha, SiddurPart.KabbalatShabbat, SiddurPart.MaarivShabbat, SiddurPart.LeilShabbat)
            nightIsHoly -> out += listOf(SiddurPart.Shacharit, SiddurPart.Mincha, SiddurPart.Maariv, SiddurPart.LeilYomTov)
            else -> out += listOf(SiddurPart.Shacharit, SiddurPart.Mincha, SiddurPart.Maariv)
        }
        if (TefilaRules().isHallelRecited(jc) && !shabbat && !jc.isYomTovAssurBemelacha) out.add(1, SiddurPart.Hallel)
        if (JewishDay.omerTonight(jc) != null) out += SiddurPart.SfiratHaomer
        if (JewishDay.chanukaTonight(jc) != null) out += SiddurPart.NerotChanuka
        if (jc.jewishDayOfMonth in 3..15 && !shabbat) out += SiddurPart.KiddushLevana
        out += listOf(SiddurPart.BirkatHamazon, SiddurPart.MeeinShalosh, SiddurPart.KriatShemaAlHamita)
        if (!jc.isAssurBemelacha) out += SiddurPart.TefilatHaderech
        return out.distinct()
    }

    /**
     * For each line, whether it is said on [jc] (null = always shown). A line opened by a small
     * condition ("בעשי״ת:", "בחורף:", "בחנוכה:"…) is judged by it; when the condition stands alone
     * on its line ("<small>בקיץ:</small>"), it governs the following line too.
     */
    fun applicability(contents: List<String>, jc: JewishCalendar): List<Boolean?> {
        val out = MutableList<Boolean?>(contents.size) { null }
        var i = 0
        while (i < contents.size) {
            val split = splitCondition(contents[i])
            if (split == null) {
                i++
                continue
            }
            val (cond, body) = split
            if (body.contains("<small>")) {
                // Several conditions on one line ("בקיץ: … בחורף: …"): keep the whole line.
                i++
                continue
            }
            if (body.isBlank() && i + 1 < contents.size && splitCondition(contents[i + 1]) == null) {
                val v = evaluate(cond, plain(contents[i + 1]), jc)
                out[i] = v
                out[i + 1] = v
                i += 2
            } else {
                out[i] = evaluate(cond, plain(body), jc)
                i++
            }
        }
        return out
    }

    /** Condition text and the rest of the line, when the line starts with a short "<small>…</small>". */
    private fun splitCondition(content: String): Pair<String, String>? {
        val t = content.trimStart()
        if (!t.startsWith("<small>")) return null
        val close = t.indexOf("</small>")
        if (close < 0 || close > 80) return null
        val cond = HebrewText.normalize(t.substring(7, close).replace(Regex("<[^>]+>"), ""))
        return cond to t.substring(close + 8)
    }

    private fun plain(html: String): String = HebrewText.normalize(HebrewTextUtils.removeAllDiacritics(html.replace(Regex("<[^>]+>"), " ")))

    private fun evaluate(cond: String, body: String, jc: JewishCalendar): Boolean? {
        val rules = TefilaRules()
        // "ותן ברכה / ותן טל ומטר" follow the rain request; "מוריד הטל / משיב הרוח" follow the rain praise.
        val rainRequest = body.contains("טל ומטר") || body == "ברכה" || body.contains("ותן ברכה") ||
            body.startsWith("ברכנו") || body.startsWith("ברך עלינו")
        val rainSeason = if (rainRequest) rules.isVeseinTalUmatarRecited(jc) else rules.isMashivHaruachRecited(jc)
        return when {
            cond.contains("מדלגין") -> null
            cond.startsWith("בעשית") || cond.startsWith("בעשרת ימי תשובה") -> jc.isAseresYemeiTeshuva
            cond.startsWith("בקיץ") || cond.startsWith("בימות החמה") -> !rainSeason
            cond.startsWith("בחורף") || cond.startsWith("בימות הגשמים") -> rainSeason
            cond.startsWith("בראש חודש ובחול המועד") || cond.startsWith("בראש חודש ובחוהמ") -> jc.isRoshChodesh || jc.isCholHamoed
            cond.startsWith("בראש חודש") || cond.startsWith("ברח") -> jc.isRoshChodesh
            cond.startsWith("בחנוכה") -> jc.isChanukah || (cond.contains("פורים") && jc.isPurim)
            cond.startsWith("בפורים") -> jc.isPurim
            cond.startsWith("בתענית ציבור") -> jc.isTaanis
            cond.startsWith("בראש השנה") -> jc.isRoshHashana
            cond.startsWith("בשבת") -> jc.weekday == 7
            cond.startsWith("ביוט") -> jc.isYomTov
            cond.startsWith("בחול המועד") -> jc.isCholHamoed
            else -> null
        }
    }

    suspend fun loadSections(repo: SeforimRepository, titles: List<String>): List<SiddurSection> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SiddurSection>()
        for (title in titles) {
            val book = LibraryLookup.findBook(repo, listOf(title)) ?: continue
            if (out.any { it.book.id == book.id }) continue
            val toc = runCatching { repo.getBookToc(book.id) }.getOrDefault(emptyList())
            if (toc.isEmpty()) continue
            val indexById = HashMap<Long, Int>()
            toc.mapNotNull { it.lineId }.chunked(500).forEach { ids -> repo.getLinesByIds(ids).forEach { indexById[it.id] = it.lineIndex } }
            val byId = toc.associateBy { it.id }
            val ordered = toc.mapNotNull { e -> e.lineId?.let { indexById[it] }?.let { e to it } }.sortedWith(compareBy({ it.second }, { it.first.level }))
            val lastLine = if (book.totalLines > 0) book.totalLines - 1 else Int.MAX_VALUE
            ordered.forEachIndexed { i, (e, start) ->
                val end = ordered.drop(i + 1).firstOrNull { it.first.level <= e.level }?.second?.minus(1) ?: lastLine
                val path = generateSequence(e.parentId?.let { byId[it] }) { p -> p.parentId?.let { byId[it] } }.map { it.text }.toList().reversed()
                val children = ordered.filter { it.first.parentId == e.id }.map { it.first.text to it.second }
                out += SiddurSection(book, e, path, start, maxOf(start, end), children)
            }
        }
        out
    }

    /** An exact heading match of any candidate (in order) wins over a partial one. */
    fun findSection(sections: List<SiddurSection>, candidates: List<String>): SiddurSection? {
        for (exact in listOf(true, false)) {
            for (cand in candidates) {
                val segs = cand.split('/').map { HebrewText.normalize(it) }
                val last = segs.last()
                val parents = segs.dropLast(1)
                val hit = sections.filter { s ->
                    val t = s.normText
                    (if (exact) t == last else t.contains(last)) && parentsMatch(s.normPath, parents)
                }.minWithOrNull(compareBy({ it.entry.level }, { it.start }))
                if (hit != null) return hit
            }
        }
        return null
    }

    private fun parentsMatch(path: List<String>, parents: List<String>): Boolean {
        var i = 0
        for (p in path) {
            if (i < parents.size && p.contains(parents[i])) i++
        }
        return i == parents.size
    }
}
