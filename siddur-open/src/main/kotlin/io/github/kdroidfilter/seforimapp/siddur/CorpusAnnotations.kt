package io.github.kdroidfilter.seforimapp.siddur

private data class Rubric(
    val start: Regex,
    val end: Regex,
    val flag: String,
    val maxRows: Int,
)

// Match both anchors in the same prayer. A changed edition leaves an unmatched block intact.
private val rubrics =
    listOf(
        Rubric(Regex("^בראש חו?דש ובחול המועד אומרים"), Regex("^(ותחזינה|ואתה ברחמיך)"), "yaale_veyavo", 10),
        Rubric(Regex("^בליל ראש חו?דש יש הנוהגים לומר$"), Regex("^לשם יחוד"), "rosh_chodesh", 3),
        Rubric(Regex("^בחנוכה ופורים אומרים"), Regex("^(ועל ?כלם|על הכל יהוה אלהינו אנחנו מודים)"), "al_hanisim", 12),
        Rubric(
            Regex("^(מודים דרבנן|כשיגיע שליח צבור למודים)"),
            Regex("^(בחנוכה ופורים|על הני?סים|ועל ?כלם)"),
            "repetition",
            6,
        ),
        Rubric(Regex("^קדושה(?: בחזרת השץ.*)?$"), Regex("^(אתה קדוש|קדושת השם$|דעת$)"), "repetition", 24),
        Rubric(Regex("^ברכת כו?הנים$"), Regex("^(שים שלום|שלום רב|שלום$)"), "repetition", 24),
        Rubric(Regex("^בתענית ציבור השליח ציבור אומר בחזרה"), Regex("^רפאנו"), "fast_repetition", 6),
        Rubric(Regex("^בשבת יאמר$"), Regex("^ואם חל ראש חודש בשבת יאמר"), "!rosh_chodesh", 7),
        Rubric(Regex("^ואם חל ראש חודש בשבת יאמר"), Regex("^ישמחו במלכותך"), "rosh_chodesh", 12),
        Rubric(Regex("^כשחל ראש חודש בשבת אומרים"), Regex("^תכנת שבת רצית"), "rosh_chodesh", 14),
        Rubric(Regex("^וממשיכים רצה"), Regex("^רצה יהוה"), "!rosh_chodesh", 8),
        Rubric(
            Regex("^(אם המסובים שלושה או יותר אומרים|שלושה שאכלו כאחד חייבין בזימון)"),
            Regex("^ברוך אתה .{1,10} אלהינו מלך העולם"),
            "zimun",
            60,
        ),
    )

private val teshuvaInline = Regex("""<small>\s*<small>([^<>]+)</small>([^<>]*)</small>""")
private val variant = Regex("^([א-ת ]+):")
private val variants =
    mapOf(
        "בראש חדש" to "rosh_chodesh",
        "בראש חודש" to "rosh_chodesh",
        "לרח" to "rosh_chodesh",
        "בחוהמ פסח" to "pesach",
        "לפסח" to "pesach",
        "בחוהמ סוכות" to "sukkot",
        "לסכות" to "sukkot",
        "בחנוכה" to "chanuka",
        "בחנוכה אומרים" to "chanuka",
        "בפורים" to "purim",
        "בפורים אומרים" to "purim",
    )

private val tenRitual = Regex("""\(\s*(?:<small>)?בעשרה(?: או יותר| ויותר)?[: ]*(?:</small>)?\s*(<b>[^<]+</b>|[^<>()]+)\)""")
private val tenSmall = Regex("""<small>\(בעשרה(?: או יותר| ויותר)?[: ]*(<b>[^<]+</b>)\)</small>""")
private val birkatHamazonYaale = Rubric(
    Regex("^בראש חו?דש (ביום טוב )?ובחול המועד אומרים"),
    Regex("^(ות?בנה ירושלים|ובנה ירושלים|שכח לומר רצה)"),
    "yaale_veyavo",
    14,
)

private val fastAmidahRubrics = listOf(
    Rubric(
        Regex("^(?:במנחת תשעה באב:|בתשעה באב אומרים: נחם)"),
        Regex("^ברוך אתה .*בונה ירושלים"),
        "nacham",
        4,
    ),
    Rubric(
        Regex("^(?:בתענית ציבור אומרים כאן עננו בתפילת הלחש|בתענית אומר היחיד עננו|בתשעה באב יש אומרים עננו)"),
        Regex("^כי אתה שומע תפלת"),
        "aneinu",
        4,
    ),
    Rubric(
        Regex("^בתענית ציבור אומר כאן השץ עננו"),
        Regex("^(רפואה|רפאנו)"),
        "fast_repetition",
        5,
    ),
)
private val minchaKohanimRubric = Rubric(
    Regex("^בתענית ציבור (?:אומר כאן השץ ברכת כהנים|כשמתפללים מנחה קטנה)"),
    Regex("^(?:שים שלום|שלום רב|שלום$)"),
    "fast_repetition",
    16,
)

internal fun annotateCorpus(
    lines: List<SiddurLine>,
    flags: Set<String>,
    showAll: Boolean,
    amud: AmudAnnotations? = null,
    part: SiddurPart? = null,
    nusach: Nusach? = null,
): List<SiddurLine> {
    val text = lines.map { plain(it.html) }
    val conditions = MutableList(lines.size) { mutableSetOf<String>() }
    calendarPassages(lines, text, part, nusach, conditions)
    val family = part?.familyOf()
    val rules = when (family) {
        SiddurPart.BIRKAT_HAMAZON -> rubrics + birkatHamazonYaale
        SiddurPart.MINCHA -> rubrics + fastAmidahRubrics + minchaKohanimRubric
        SiddurPart.SHACHARIT, SiddurPart.MAARIV -> rubrics + fastAmidahRubrics
        else -> rubrics
    }
    for (index in lines.indices) {
        if (text[index].trimEnd(':') == "ואומר החזן קדיש תתקבל" &&
            text.getOrNull(index + 1)?.startsWith("יתגדל ויתקדש") == true
        ) {
            conditions[index].add("minyan")
            for (offset in 1..4) {
                val label = text.getOrNull(index + offset) ?: break
                if (!kaddishTiskabelLines[offset - 1].containsMatchIn(label)) break
                conditions[index + offset].add("minyan")
            }
        }
        for (rule in rules) {
            if (!rule.start.containsMatchIn(text[index])) continue
            val end = (index + 1..minOf(index + rule.maxRows, lines.lastIndex))
                .firstOrNull { rule.end.containsMatchIn(text[it]) } ?: continue
            val previousIsKohanimTitle = text.getOrNull(index - 1) in kohanimTitles
            val start = if (rule == minchaKohanimRubric && previousIsKohanimTitle) index - 1 else index
            (start until end).forEach { conditions[it].add(rule.flag) }
            if (rule.flag == "nacham") conditions[end].add("!nacham")
        }
        if (text[index].startsWith("בחזרת השץ אומרים כאן קדושה")) conditions[index].add("repetition")
        if (text[index].startsWith("נוסח עננו בג") && "סנסן ליעיר" in text[index]) conditions[index].add("alternate_aneinu")
        if (text[index].startsWith("(בתענית ציבור אומר כאן השץ עננו")) conditions[index].add("fast_repetition")
        if (family == SiddurPart.MINCHA && text[index].startsWith("לתענית ציבור: שים שלום")) {
            conditions[index].add("taanit")
            if (text.getOrNull(index - 1)?.startsWith("שלום רב") == true) conditions[index - 1].add("!taanit")
        }
        val flag = variant.find(text[index])?.groupValues?.get(1)?.let(variants::get)
        if (flag != null && conditions[index].any { it == "yaale_veyavo" || it == "al_hanisim" }) {
            conditions[index].add(flag)
            if (text[index].endsWith(':') && index < lines.lastIndex) conditions[index + 1].add(flag)
        }
        val season = text[index].trimEnd(':', '׃')
        val next = text.getOrNull(index + 1).orEmpty()
        val label = leadingMealLabel(lines[index].html)
        if (part == SiddurPart.MEEIN_SHALOSH || part == SiddurPart.BIRKAT_HAMAZON && "yaale_veyavo" in conditions[index]) {
            mealCalendarFlag(label)?.let { conditions[index].add(it) }
        }
        if (part == SiddurPart.BIRKAT_HAMAZON) {
            if (text[index].startsWith("רצה והחליצנו") || season in retzehIntroductions && next.startsWith("רצה והחליצנו")) {
                conditions[index].add("sat")
            }
        }
        val blessing = text[index].trimStart('(')
        when {
            blessing.startsWith("אתה חוננתנו") || blessing == "במוצאי שבת ויום טוב אומרים" ->
                conditions[index].add("havdala")
            blessing.startsWith("ותודיענו") || "במוצאי שבת אומרים ותודיענו" in blessing ||
                blessing == "במוצאי שבת אומרים" && next.startsWith("ותודיענו") ->
                conditions[index].add("vatodieinu")
        }
        val seasonFlag = when {
            season == "בקיץ" && next.startsWith("מוריד הטל") -> "morid_hatal"
            season == "בחורף" && next.startsWith("משיב הרוח") -> "mashiv_haruach"
            season == "בקיץ" && next.startsWith("ברכנו ") -> "!tal_umatar"
            season == "בחורף" && next.startsWith("ברך עלינו ") -> "tal_umatar"
            else -> null
        }
        if (seasonFlag != null) {
            conditions[index].add(seasonFlag)
            conditions[index + 1].add(seasonFlag)
        }
    }
    return lines.flatMapIndexed { index, line ->
        val conditionsOnLine = conditions[index]
        val tagged = amud?.condition(line.html, flags)
        val applies = !line.skipped && tagged != false && conditionsOnLine.all { flag ->
            when (flag) {
                "pesach" -> "pesach" in flags || "shvii_shel_pesach" in flags
                "shmini_atzeret" -> "shmini_atzeret" in flags || "simchat_torah" in flags
                else -> if (flag.startsWith('!')) flag.drop(1) !in flags else flag in flags
            }
        }
        if (!showAll && !applies) return@flatMapIndexed emptyList()
        val selected = if (applies) amud?.applyInline(line.html, flags, showAll) ?: line.html else line.html
        var html = if (!applies || showAll && selected != line.html) selected else teshuvaInline.replace(selected) { match ->
            val instruction = plain(match.groupValues[1])
            val active = when {
                instruction.startsWith("בעשרת ימי תשובה") -> "aseret_yemei_teshuva" in flags
                instruction.startsWith("בשבת תשובה") -> "aseret_yemei_teshuva" in flags && "sat" in flags
                else -> return@replace match.value
            }
            when {
                active && showAll -> match.value
                active -> MARK_START + match.groupValues[2].trimStart() + MARK_END
                showAll -> SKIP_START + match.value + SKIP_END
                else -> ""
            }
        }
        if (!showAll && plain(html).isEmpty() && line.lulavInstruction == null) return@flatMapIndexed emptyList()
        if (!showAll && "zimun" in conditionsOnLine) {
            for (pattern in listOf(tenSmall, tenRitual)) {
                html = pattern.replace(html) { if ("zimun_10" in flags) it.groupValues[1] else "" }
            }
        }
        val kind = when {
            "repetition" !in conditionsOnLine || line.kind != SiddurLine.Kind.TEXT -> line.kind
            text[index].trimEnd(':') in repetitionTitles -> SiddurLine.Kind.HEADING
            text[index].trimEnd(':') in repetitionInstructions -> SiddurLine.Kind.NOTE
            else -> line.kind
        }
        val annotated = line.copy(
            html = html,
            kind = kind,
            headingLevel = if (kind == SiddurLine.Kind.HEADING && kind != line.kind) 5 else line.headingLevel,
            special = applies && (line.special || MARK_START in html || conditionsOnLine.any { it != "minyan" }),
            skipped = !applies,
        )
        if ("repetition" in conditionsOnLine) annotated.splitRepetitionTitle() else listOf(annotated)
    }
}

private val kaddishTiskabelLines = listOf(
    Regex("^יתגדל ויתקדש"), Regex("^תתקבל צלותנא"), Regex("^יהא שלמא רבא"),
    Regex("^(?:יפסע שלש פסיעות לאחור )?עושה שלום"),
)

private val kohanimTitles = setOf("ברכת כהנים", "ברכת כוהנים")
private val repetitionTitles = kohanimTitles + setOf("קדושה", "מודים דרבנן")
private val repetitionInstructions = setOf(
    "לחזרת השץ", "בחזרת השץ אומרים", "בחזרת השץ אומרים כאן קדושה", "בחזרת השץ כשהחזן אומר מודים, הקהל אומרים",
)
private val inlineRepetitionTitle = Regex("""^\s*(קדושה|מודים דרבנן):?\s*(?:<br\s*/?>\s*)?(?=<small>)""")

private fun SiddurLine.splitRepetitionTitle(): List<SiddurLine> {
    if (kind != SiddurLine.Kind.TEXT) return listOf(this)
    val title = inlineRepetitionTitle.find(html) ?: return listOf(this)
    val body = html.drop(title.range.last + 1)
    val bodyKind = if (plain(body).trimEnd(':') in repetitionInstructions) SiddurLine.Kind.NOTE else kind
    return listOf(
        copy(html = title.groupValues[1], kind = SiddurLine.Kind.HEADING, headingLevel = 5),
        copy(html = body, kind = bodyKind),
    )
}

private val mealFoods = setOf("mezonot", "wine", "fruit")
private val lineBreak = Regex("<br\\s*/?>")
private val retzehIntroductions = setOf("בשבת אומרים", "בשבת מוסיפים")
private val leadingMealSpan = Regex("""^(?:(?:<small>|<em>)\s*)*(?:<small>|<em>)([^<>]+)</(?:small|em)>""")
private val israeliFoodEnding = Regex("""\s*<small>\s*\(?\s*<small>([^<>]+)</small>\s*([^<>()]+?)\s*\)?\s*</small>:?\s*$""")
private val grapeEnding = hebrewPhrase("פרי הגפן")
private val fruitEnding = Regex("${hebrewPhrase("הפרות").pattern}|${hebrewPhrase("הפירות").pattern}")
private val migdolAlternative = Regex("""(\S+(?:\s+\S+\s+\S+)?)\s+\(<small>([^<>]+)</small>\s*([^()]+)\)""")

internal fun List<SiddurLine>.selectMigdol(flags: Set<String>, night: Boolean): List<SiddurLine> = map { line ->
    val html = migdolAlternative.replace(line.html) { match ->
        val (regular, instruction, alternative) = match.destructured
        if (!plain(regular).startsWith("מגדיל") || !plain(alternative).startsWith("מגדול")) return@replace match.value
        val useMigdol = if (plain(instruction).startsWith("ביום שמתפללים מוסף")) {
            "musaf" in flags || "purim" in flags || "seudat_mila" in flags || night && "motzaei_shabbat" in flags
        } else {
            "sat" in flags || "festival" in flags
        }
        if (useMigdol) MARK_START + alternative + MARK_END else regular
    }
    if (html == line.html) line else line.copy(html = html)
}

private fun leadingMealLabel(html: String): String =
    leadingMealSpan.find(html.trimStart())?.let { plain(it.groupValues[1]).trim('(', ')', ':', '׃') }.orEmpty()

private fun mealCalendarFlag(label: String): String? =
    when (label) {
        "בשבת" -> "sat"
        "בראש חדש", "בראש חודש", "ברח", "לרח" -> "rosh_chodesh"
        "בראש השנה", "ברה", "לרה" -> "rosh_hashana"
        "בפסח", "לפסח" -> "pesach"
        "בשבועות" -> "shavuot"
        "בסוכות", "בסכות", "לסכות" -> "sukkot"
        "בשמיני עצרת", "לשע ושת" -> "shmini_atzeret"
        "ביוט" -> "festival"
        "ילדים האוכלים ביוכ" -> "yom_kippur"
        else -> null
    }

internal fun List<SiddurLine>.splitMealAdditions(): List<SiddurLine> = flatMap { line ->
    val html = line.html.trim()
    if (!html.startsWith("<small><em>") || !html.endsWith("</small>")) return@flatMap listOf(line)
    val parts = html.removePrefix("<small>").removeSuffix("</small>").split(lineBreak)
    if (parts.size < 2 || parts.any { mealCalendarFlag(leadingMealLabel(it)) == null }) return@flatMap listOf(line)
    parts.map { line.copy(html = "<small>$it</small>") }
}

internal fun selectFood(
    lines: List<SiddurLine>,
    flags: Set<String>,
): List<SiddurLine> {
    val picked = flags.intersect(mealFoods)
    if (picked.isEmpty()) return lines
    val inIsrael = "israel" in flags
    return lines.mapNotNull { line ->
        val parts = line.html.split(lineBreak)
        val known = parts.map(::foodVariant)
        if (known.all { it.isEmpty() }) return@mapNotNull line
        val local = parts.map { leadingMealLabel(it).endsWith("של ארץ ישראל") }
        val paired = known.indices.groupBy(known::get).filterValues { indices -> indices.map(local::get).toSet().size > 1 }.keys
        val text = parts.filterIndexed { index, _ ->
            val foods = known[index]
            val selected = foods.isEmpty() || foods.size == 1 && picked.containsAll(foods) || foods == picked
            selected && (foods !in paired || local[index] == inIsrael)
        }.joinToString("<br>") { selectInlineFoodOrigin(it, inIsrael) }
        if (text.isBlank()) null else line.copy(html = text)
    }
}

private fun selectInlineFoodOrigin(html: String, inIsrael: Boolean): String {
    val match = israeliFoodEnding.find(html) ?: return html
    val (label, alternate) = match.destructured
    val base = html.substring(0, match.range.first)
    val rubric = leadingMealSpan.find(base.trimStart()) ?: return html
    return when (plain(label)) {
        "של ארץ ישראל" -> if (inIsrael) rubric.value + " " + alternate else base
        "באי" -> {
            if (!inIsrael) return base
            val ending = alternate.trim().substringAfter(' ').trimEnd(':', '.')
            val pattern = if (plain(ending).startsWith("פרי גפנה")) grapeEnding else fruitEnding
            val start = base.indexOf(rubric.value) + rubric.value.length
            base.take(start) + pattern.replace(base.drop(start)) { ending }
        }
        else -> html
    }
}

private fun hebrewPhrase(text: String): Regex = Regex(
    text.map { if (it == ' ') "\\s+" else "$it[\\u0591-\\u05BD\\u05BF-\\u05C7]*" }.joinToString(""),
)

private fun foodVariant(html: String): Set<String> {
    val label = leadingMealLabel(html).ifEmpty {
        plain(html).trimStart('(').substringBefore(':').substringBefore(" יאמר").trimEnd(')')
    }.substringBefore(" יאמר")
    return when (label) {
        "מזונות", "על מזונות", "על מזונות של ארץ ישראל", "אם אכל מזונות" -> setOf("mezonot")
        "יין", "על יין", "על היין", "על יין של ארץ ישראל", "אם שתה יין" -> setOf("wine")
        "פירות", "על הפירות", "על שבעת המינים", "על שבעת המינים של ארץ ישראל",
        "על פירות משבעת המינים", "אם אכל פירות משבעת מינים",
        "אם אכל פרות משבעת המינים",
        -> setOf("fruit")
        "על מזונות ויין ביחד" -> mealFoods - "fruit"
        else -> emptySet()
    }
}
