package io.github.kdroidfilter.seforimapp.siddur

private val holidayLechaDodi = Regex("""<small>\s*<small>\(ביו"ט מוסיפים: (<b>[^<]+</b>)\)</small>\s*</small>""")

internal fun List<SiddurLine>.kabbalatShabbat(flags: Set<String>, nusach: Nusach, showAll: Boolean): List<SiddurLine> {
    val short = "short_kabbalat_shabbat" in flags
    val labels = map { plain(it.html) }
    val weekdayStart = labels.indexOfFirst { it == "ידיד נפש" || it.startsWith("לכו נרננה") }
    val shortStart = labels.indexOfFirst { it == "תהילים כט" || it.startsWith("מזמור לדוד הבו") }
    val middleStart = labels.indexOfFirst { it.startsWith("ביום טוב ובשבת חול המועד מדלגים ל") }
    val middleEnd = labels.indexOfFirst { it.startsWith("ימין ושמאל תפרוצי") }
    val bamehStart = labels.indexOfFirst { it == "במה מדליקין" || it.startsWith("אין אומרים במה מדליקין") }
    val bamehEnd = if (nusach == Nusach.EDOT_HAMIZRACH) bamehStart + 2 else size
    return mapIndexed { index, line ->
        val inactive = short && nusach != Nusach.EDOT_HAMIZRACH && weekdayStart >= 0 && index in weekdayStart until shortStart ||
            short && nusach == Nusach.SEFARD && middleStart >= 0 && index in middleStart until middleEnd ||
            "bameh_madlikin" !in flags && bamehStart >= 0 && index in bamehStart until bamehEnd
        val html = if (nusach == Nusach.SEFARD && !showAll) {
            holidayLechaDodi.replace(line.html) { if (short) it.groupValues[1] else "" }
        } else {
            line.html
        }
        line.copy(html = html, skipped = line.skipped || inactive)
    }
}

internal fun List<SiddurLine>.motzaeiShabbatMaariv(
    flags: Set<String>,
    nusach: Nusach,
    showAll: Boolean,
): List<SiddurLine> {
    val noam = "vayehi_noam" in flags
    val labels = map { plain(it.html) }
    val tishaBeav = "shabbat_into_tisha_beav" in flags
    if (nusach == Nusach.EDOT_HAMIZRACH) {
        val start = labels.indexOfFirst { it.startsWith("בכל לילות החול מלבד מוצאי שבת") }
        val end = (start + 1 until size).firstOrNull {
            labels[it].startsWith(if (tishaBeav) "ואתה קדוש" else "ואומר החזן קדיש תתקבל")
        } ?: -1
        return markInactive(start, end, !noam)
    }
    val weekdayEnd = labels.indexOfFirst { it.startsWith("בחול אומר החזן קדיש שלם") }
    val shabbatEnd = labels.indexOfFirst { it == "תוספות למוצאי שבת" || it == "ערבית למוצאי שבת" }
    if (weekdayEnd < 0 || shabbatEnd <= weekdayEnd) return this
    val noamStart = labels.indexOfFirst { it == "ויהי נעם" || it.startsWith("ויהי נעם אדני") }
    val kedushaStart = labels.indexOfFirst { it.startsWith("ואתה קדוש יושב") }
    val prayers = markInactive(noamStart, kedushaStart, tishaBeav)
    if (nusach == Nusach.SEFARD) {
        val next = labels.indexOfFirst { it == "ספירת העומר" }.takeIf { it > shabbatEnd } ?: size
        return prayers.markInactive(weekdayEnd, shabbatEnd, noam || tishaBeav).markInactive(shabbatEnd, next, !noam && !tishaBeav)
    }
    val veyiten = labels.indexOfFirst { it == "ויתן לך" }
    if (!noam && !tishaBeav) return markInactive(shabbatEnd, veyiten, true)
    val titkabal = (weekdayEnd until shabbatEnd).firstOrNull { labels[it].startsWith("תתקבל צלותהון") }
    if (titkabal == null || veyiten <= shabbatEnd) return this
    val veyitenEnd = (veyiten + 1 until size).firstOrNull { this[it].kind == SiddurLine.Kind.HEADING } ?: size
    // The edition prints one Kaddish. Take its first half before the psalm and repeat it in full afterwards.
    val fullKaddish = subList(weekdayEnd + 1, shabbatEnd)
    return take(titkabal) +
        (if (showAll) subList(titkabal, shabbatEnd).map { it.copy(skipped = true) } else emptyList()) +
        prayers.subList(shabbatEnd, veyiten) + fullKaddish + prayers.markInactive(veyiten, veyitenEnd, tishaBeav).drop(veyiten)
}

internal fun List<SiddurLine>.havdalaFor(flags: Set<String>): List<SiddurLine> =
    map { line ->
        val label = plain(line.html)
        val candle = "בורא מאורי האש" in label
        val spices = "בשמים" in label && "ברוך אתה" in label
        val inactive = line.kind != SiddurLine.Kind.HEADING && when {
            "shabbat_into_tisha_beav" in flags -> !candle
            "deferred_havdala" in flags -> "בורא פרי הגפן" !in label && "המבדיל בין קדש לחול" !in label
            "motzaei_shabbat" !in flags -> spices ||
                label.startsWith("יקח הכוס בידו הימנית והבשמים") ||
                "after_yom_kippur" !in flags && (candle || label.startsWith("כשיברך על הנר"))
            else -> false
        }
        if (inactive) line.copy(skipped = true) else line
    }

internal fun festivalKiddush(
    base: Outline,
    koren: Outline?,
    flags: Set<String>,
    nusach: Nusach,
    showAll: Boolean,
): List<SiddurLine> {
    val shabbat = "sat" in flags
    val pesach = "pesach" in flags || "shvii_shel_pesach" in flags
    val secondSukkot = "sukkot_second_night" in flags
    val lines = when (nusach) {
        Nusach.ASHKENAZ -> koren?.read(listOf("קידוש לליל יום טוב"), flags, showAll).orEmpty()
        Nusach.SEFARD -> base.read(listOf("קידוש לשלש רגלים"), flags, showAll)
        Nusach.EDOT_HAMIZRACH -> {
            val kiddush = base.read(listOf("תפילה לשלש רגלים/מוסף"), flags, showAll)
                .dropWhile { plain(it.html) != "קידוש לליל שלש רגלים" }
                .takeWhile { plain(it.html) != "זוהר לסעודת סוכה" }
            val pesachText = base.read(listOf("תפילה לשלש רגלים/עמידה"), flags, showAll)
                .firstOrNull { plain(it.html).startsWith("בפסח: חג המצות הזה") }
            val vayechulu = base.read(listOf("קידוש ליל שבת"), flags, showAll)
                .firstOrNull { "יום הששי" in plain(it.html) && "ויכלו השמים" in plain(it.html) }
            buildList {
                for (line in kiddush) {
                    val label = plain(line.html)
                    if (label.startsWith("בשבת אומרים כל הקידוש") && shabbat && vayechulu != null) add(vayechulu)
                    if (label.startsWith("חג השבועות הזה") && pesachText != null) add(pesachText.copy(skipped = !pesach))
                    add(line)
                }
            }
        }
    }
    val labels = lines.map { plain(it.html) }
    val opening = labels.indexOfFirst { it.startsWith("סברי מרנן") || it.startsWith("ואומר המקדש סברי מרנן") }
    val rendered = lines.mapIndexed { index, line ->
        val label = labels[index]
        val inactive = when {
            index in 1 until opening && nusach != Nusach.EDOT_HAMIZRACH && !shabbat -> true
            label.startsWith("כשחל יוט בחול מתחילין כאן") -> shabbat
            label.startsWith("בשבת אומרים כל הקידוש") -> !shabbat
            label.startsWith("חג השבועות הזה") -> "shavuot" !in flags
            label.startsWith("חג הסכות הזה") -> "sukkot" !in flags
            label.startsWith("שמיני חג עצרת הזה") -> "shmini_atzeret" !in flags && "simchat_torah" !in flags
            "בורא מאורי האש" in label || "המבדיל בין קדש לקדש" in label ||
                label.startsWith("כשחל יוט במוצאי שבת מקדשין") || label == "במוצאי שבת מוסיפים" -> "vatodieinu" !in flags
            label.startsWith("בליל שני של סוכות") -> !secondSukkot
            label.startsWith("בליל ראשון של סוכות") -> "sukkot" !in flags || secondSukkot
            "וצונו לישב בסכה" in label -> "sukkot" !in flags
            "שהחינו וקימנו" in label -> "shvii_shel_pesach" in flags
            else -> false
        }
        val html = if (showAll) line.html else line.html
            .let { if (nusach == Nusach.ASHKENAZ) selectKorenFestival(it, flags) else it }
            .let { if ("אשר בחר בנו" in label || "מקדש" in label) selectShabbatKiddushWords(it, shabbat) else it }
        line.copy(html = html, skipped = line.skipped || inactive)
    }
    val times = rendered.indices.filter { "שהחינו וקימנו" in plain(rendered[it].html) }
    val time = times.firstOrNull() ?: return rendered
    val sukkah = rendered.indexOfFirst { "וצונו לישב בסכה" in plain(it.html) }
    val havdala = rendered.indexOfFirst { "המבדיל בין קדש לקדש" in plain(it.html) }
    if (sukkah < 0) return rendered
    val after = if ("sukkot" in flags && !secondSukkot) maxOf(sukkah, havdala) else maxOf(havdala, sukkah - 1)
    return buildList {
        for ((index, line) in rendered.withIndex()) {
            if (index !in times) add(line)
            if (index == after) add(rendered[time])
        }
    }
}

private val korenFestival = Regex("""<i class="instruction">([^<]+)</i>\s*([^<]+?)(?=<i| \(בְּאַהֲבָה\))""")
private fun selectKorenFestival(html: String, flags: Set<String>): String =
    korenFestival.replace(html) { match ->
        val active = when (plain(match.groupValues[1])) {
            "בפסח:" -> "pesach" in flags || "shvii_shel_pesach" in flags
            "בשבועות:" -> "shavuot" in flags
            "בסוכות:" -> "sukkot" in flags
            "בשמעצ ובשת:" -> "shmini_atzeret" in flags || "simchat_torah" in flags
            else -> return@replace match.value
        }
        if (active) match.groupValues[2] else ""
    }

private val shabbatKiddushWords = Regex("""(?:<small>)?\((?:לשבת )?([^()]+)\)(?:</small>)?""")
private fun selectShabbatKiddushWords(html: String, shabbat: Boolean): String =
    shabbatKiddushWords.replace(html) { match ->
        if (shabbat) match.groupValues[1] else ""
    }

private fun List<SiddurLine>.markInactive(start: Int, end: Int, inactive: Boolean): List<SiddurLine> =
    if (!inactive || start < 0 || end <= start) this else mapIndexed { index, line ->
        if (index in start until end) line.copy(skipped = true) else line
    }
