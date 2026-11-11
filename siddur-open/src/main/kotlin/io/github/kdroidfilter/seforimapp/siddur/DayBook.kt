package io.github.kdroidfilter.seforimapp.siddur

import io.github.kdroidfilter.seforimapp.siddur.SiddurLine.Kind
import io.github.kdroidfilter.seforimapp.siddur.SiddurPart.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** The civil day's morning and afternoon followed by the Jewish date that begins at sunset. */
suspend fun buildDayBook(
    civil: LocalDate,
    prefs: SiddurPrefs,
    options: Set<String>,
    showAll: Boolean,
    source: SiddurSource,
    afterHalachicMidnight: Boolean = false,
): List<BookSection> {
    val outlines = mutableMapOf<String, Outline?>()
    suspend fun outline(title: String): Outline? {
        if (title !in outlines) outlines[title] = source.book(title)?.let(::Outline)
        return outlines[title]
    }

    val nusach = prefs.nusach
    val sourceBook = source.book(nusach.siddurTitle()) ?: return emptyList()
    val amud = AmudAnnotations(nusach, sourceBook.lines)
    val base = Outline(sourceBook)
    val tomorrow = civil.plus(1, DateTimeUnit.DAY)
    val dayFlags = flagsFor(civil, SHACHARIT, prefs)
    val nightFlags = flagsFor(tomorrow, MAARIV, prefs)
    val fast = "yom_kippur" in dayFlags
    val fastTonight = "yom_kippur" in nightFlags
    val season = seasonalSelichot(civil, prefs)
    val dayParts = buildList {
        if (season != null) add(SELICHOT)
        add(dayPartFor(civil, SHACHARIT, prefs))
        if (!fast && ("sat" in dayFlags || "festival" in dayFlags)) {
            add(if ("festival" in dayFlags) SEUDAT_YOM_TOV else SEUDAT_SHABBAT)
        }
        add(dayPartFor(civil, MINCHA, prefs))
        if (fast) add(NEILA)
    }
    val nightParts = buildList {
        if (nightFlags.any { it.startsWith("chanuka_") } || showAll) add(NEROT_CHANUKA)
        if ("sat" in nightFlags && !fastTonight) add(KABBALAT_SHABBAT)
        add(dayPartFor(tomorrow, MAARIV, prefs))
        if ("havdala" in nightFlags) add(HAVDALA)
        if ("omer" in nightFlags || showAll) add(SFIRAT_HAOMER)
        if (!fastTonight && ("sat" in nightFlags || "festival" in nightFlags)) {
            add(if ("festival" in nightFlags) LEIL_YOM_TOV else LEIL_SHABBAT)
        }
        if ("levana" in nightFlags || showAll) add(KIDDUSH_LEVANA)
        add(KRIAT_SHEMA_AL_HAMITA)
    }

    suspend fun section(part: SiddurPart, date: LocalDate, night: Boolean): BookSection? {
        val family = part.familyOf()
        val selected = options.filter { it.startsWith("${part.name}:") }.map { it.substringAfter(':') }.toSet()
        val readings = readingsFor(date, part, prefs)
        val standaloneHaftarah = readings.singleOrNull()?.takeIf { it.kind == ReadingKind.HAFTARAH && !it.withBlessings }
        val flags = flagsFor(date, part, prefs) +
            readings.mapNotNull {
                when (it.kind) {
                    ReadingKind.TORAH -> "torah_reading"
                    ReadingKind.HAFTARAH -> if (it.withBlessings) "haftarah" else null
                    ReadingKind.MEGILLAH -> null
                }
            } +
            (if ("zimun_10" in selected) selected + "zimun" else selected)
        val machzor = if (part.festival || part == MINCHA) machzorTitle(flags, nusach) else null
        var lines = when {
            part == SELICHOT && season != null ->
                outline(season.title)?.read(listOf(season.key), flags + "selichot", showAll).orEmpty()
            machzor != null -> {
                val keys = machzorKeys(part, flags).filter { holidayReading(it, flags) }
                outline(machzor)?.read(keys, flags, showAll).orEmpty()
            }
            part == LEIL_YOM_TOV -> {
                val koren = if (nusach == Nusach.ASHKENAZ) outline("סידור קורן השלם; אשכנז") else null
                festivalKiddush(base, koren, flags, nusach, showAll)
            }
            part.festival && part.familyOf() != part -> festivalPrayer(base, part, flags, nusach, showAll)
            else -> base.read(baseKeys(part, nusach), flags, showAll)
        }
        val occasion = israeliOccasion(date)
        if (occasion?.hasThanksgivingPrayer == true) {
            if ("israeli_hallel" in flags) {
                val hallel = hallelLines(base, flags, nusach, showAll)
                val haftarah = standaloneHaftarah?.let { SiddurLine(it.kind.name, Kind.SLOT, reading = it) }
                val publicPrayers = if (prefs.israeliPrayerCustom.festive && family == SHACHARIT) {
                    outline("סידור קורן השלם; אשכנז")?.read(
                        listOf("תפילה לשלום מדינת ישראל", "מי שברך לחיילי צה\"ל"),
                        flags,
                        showAll,
                    )?.takeWhile { plain(it.html) != "מי שברך לשבויים" }.orEmpty().map { it.copy(special = true) }
                } else {
                    emptyList()
                }
                lines = lines.withIsraeliHallel(hallel + publicPrayers + listOfNotNull(haftarah), part)
            }
            if (prefs.israeliPrayerCustom.festive) {
                when (family) {
                    MAARIV -> {
                        val independence = occasion == IsraeliOccasion.YOM_HAATZMAUT
                        val piyyut = if (independence) {
                            (if (nusach == Nusach.ASHKENAZ) base else outline(Nusach.ASHKENAZ.siddurTitle()))
                                ?.read(listOf("לכה דודי"), flags, showAll).orEmpty()
                        } else {
                            emptyList()
                        }
                        lines = lines.withIsraeliMaariv(outline("תהילים"), piyyut, independence, prefs, flags, showAll)
                    }
                    SHACHARIT -> {
                        lines = lines.withIsraeliShacharit(
                            outline("תהילים"),
                            occasion == IsraeliOccasion.YOM_YERUSHALAYIM,
                            nusach,
                            flags,
                            showAll,
                        )
                    }
                    else -> Unit
                }
            }
        }
        if (family == SHACHARIT && machzor == null) {
            if (nusach == Nusach.EDOT_HAMIZRACH) lines = lines.splitHoshienu(showAll)
            val sharedLulav = if (nusach == Nusach.EDOT_HAMIZRACH && "sukkot" in flags && "sat" !in flags) {
                outline(Nusach.ASHKENAZ.siddurTitle())
            } else {
                null
            }
            lines = morningAdditions(lines, base, flags, date, prefs, sharedLulav, showAll)
            fastSelichot(date, prefs)?.let { fast ->
                val fastLines = base.read(listOf(fast.key), flags + "selichot", showAll)
                lines = lines.withFastSelichot(fastLines)
            }
        }
        if (part == SFIRAT_HAOMER && !showAll) lines = selectOmer(lines, flags, nusach)
        if (part == MAARIV) lines = lines.withMaarivHeadings().motzaeiShabbatMaariv(flags, nusach, showAll)
        if (part == KABBALAT_SHABBAT) lines = lines.kabbalatShabbat(flags, nusach, showAll)
        if (part == HAVDALA) lines = lines.havdalaFor(flags)
        if (part == MEEIN_SHALOSH) {
            lines = selectFood(lines, flags).splitMealAdditions()
        }
        if (part == NEROT_CHANUKA && lines.isNotEmpty()) lines = lines.withSlot("chanuka_candles")
        if (part == KIDDUSH_LEVANA && lines.isNotEmpty()) lines = lines.withSlot("kiddush_levana")
        val skipped = when (part) {
            NEROT_CHANUKA -> "chanuka" !in flags
            SFIRAT_HAOMER -> "omer" !in flags
            KIDDUSH_LEVANA -> "levana" !in flags
            else -> false
        }
        if (skipped) lines = lines.map { it.copy(skipped = true) }
        if (part == BIRKAT_HAMAZON && !showAll) lines = lines.selectMigdol(flags, night)
        val annotated = annotateCorpus(lines, flags, showAll, amud.takeIf { machzor == null && part != SELICHOT }, part, nusach)
            .let { if (part == KRIAT_SHEMA_AL_HAMITA) it.bedtimePassages(date, flags, showAll, afterHalachicMidnight) else it }
            .withoutRepeatedHeadingText()
            .fillShabbatAnnouncements(date, prefs, flags)
            .withReadings(readings.filter { it != standaloneHaftarah && (machzor == null || it.kind == ReadingKind.MEGILLAH) }, part)
        if (annotated.none { it.kind == Kind.TEXT }) return null
        val display = if (part == MEEIN_SHALOSH && selected.none(part.options::contains)) emptyList() else annotated
        return BookSection(part, date, night, flags, display)
    }

    return buildList {
        addAll(dayParts.mapNotNull { section(it, civil, false) })
        addAll(nightParts.mapNotNull { section(it, tomorrow, true) })
        for (part in listOf(BIRKAT_HAMAZON, MEEIN_SHALOSH)) {
            val day = if (fast) null else section(part, civil, false)
            if (day != null) add(day)
            val relevant = if (part == BIRKAT_HAMAZON) mealDayFlags + birkatHamazonDayFlags else mealDayFlags
            if (!fastTonight && (day == null || dayFlags.intersect(relevant) != nightFlags.intersect(relevant))) {
                section(part, tomorrow, true)?.let(::add)
            }
        }
        if (!fast) section(TEFILAT_HADERECH, civil, false)?.let(::add)
    }
}

private val mealDayFlags = setOf(
    "sat", "festival", "rosh_chodesh", "rosh_hashana", "pesach", "shvii_shel_pesach", "shavuot", "sukkot",
    "shmini_atzeret", "simchat_torah",
)
private val birkatHamazonDayFlags = setOf("chanuka", "purim")

private fun List<SiddurLine>.withMaarivHeadings(): List<SiddurLine> =
    mapNotNull { line ->
        when (plain(line.html)) {
            "ברכו" -> line.takeUnless { it.kind == Kind.HEADING }
            "תפילת ערבית לימי החול", "תפילת עמידה ליום חול" -> null
            else -> line
        }
    }

private fun List<SiddurLine>.withoutRepeatedHeadingText(): List<SiddurLine> = filterIndexed { index, line ->
    val previous = getOrNull(index - 1)
    line.kind != Kind.TEXT || previous?.kind != Kind.HEADING || plain(line.html) != plain(previous.html)
}

private fun List<SiddurLine>.withFastSelichot(selichot: List<SiddurLine>): List<SiddurLine> {
    if (selichot.isEmpty()) return this
    val anchor = indexOfFirst {
        it.kind == Kind.HEADING &&
            plain(it.html) in setOf("אחר העמידה", "תחנון", "וידוי", "קריאת התורה", "סדר קריאת התורה")
    }.takeIf { it >= 0 } ?: size
    return take(anchor) + selichot.map { it.copy(special = true) } + drop(anchor)
}

private fun holidayReading(key: String, flags: Set<String>): Boolean =
    when (key) {
        "קריאה ליום ראשון", "הפטרה ליום א" -> "rh_second_day" !in flags
        "קריאה ליום שני", "הפטרה ליום שני" -> "rh_second_day" in flags
        else -> true
    }

private fun List<SiddurLine>.withSlot(name: String): List<SiddurLine> =
    take(1) + SiddurLine(name, Kind.SLOT) + drop(1)

private fun List<SiddurLine>.withReadings(readings: List<SiddurReading>, part: SiddurPart): List<SiddurLine> {
    if (readings.isEmpty() || isEmpty()) return this
    val labels = map { plain(it.html) }
    val torahHeadings = setOf("קריאת התורה", "סדר קריאת התורה בשבת", "קריאת התורה לתענית ציבור", "קריאה לתענית ציבור")
    val torahStart = indices.lastOrNull { this[it].kind == Kind.HEADING && labels[it] in torahHeadings } ?: -1
    val haftaraStart = indices.firstOrNull {
        it > torahStart && this[it].kind == Kind.HEADING && labels[it] in setOf("הפטרה", "ברכות ההפטרה")
    }
    val musaf = indices.firstOrNull { this[it].kind == Kind.HEADING && labels[it].startsWith("מוסף") } ?: size
    val torahEnd = minOf(haftaraStart ?: size, musaf)
    val torahInsert = (torahStart + 1 until torahEnd).firstOrNull {
        labels[it].removePrefix("ו").startsWith("אחר קריאת התורה") ||
            labels[it].removePrefix("ו").startsWith("אחרי קריאת התורה")
    } ?: (torahStart + 1 until torahEnd).firstOrNull {
        labels[it] in setOf("ברכת הגומל", "הגבהה", "החזרת ספר תורה")
    } ?: when {
        torahStart >= 0 -> torahStart + 1
        part.familyOf() == MINCHA -> indices.firstOrNull {
            this[it].kind == Kind.HEADING && labels[it] in amidahHeadings
        } ?: size
        else -> musaf
    }
    val haftaraInsert = haftaraStart?.let { start ->
        (start + 1 until size).firstOrNull {
            labels[it].removePrefix("ו").startsWith("אחרי קריאת ההפטרה") ||
                labels[it].removePrefix("ו").startsWith("אחר קריאת ההפטרה")
        } ?: (start + 1 until size).firstOrNull {
            "אשר בחר בנביאים טובים" in labels[it] && "הבוחר בתורה ובמשה" in labels[it]
        }?.plus(1) ?: start + 1
    } ?: torahInsert
    val inserts = readings.groupBy { reading ->
        when (reading.kind) {
            ReadingKind.TORAH -> torahInsert
            ReadingKind.HAFTARAH -> haftaraInsert
            ReadingKind.MEGILLAH -> size
        }
    }
    return buildList {
        for (index in 0..this@withReadings.size) {
            for (reading in inserts[index].orEmpty()) {
                add(SiddurLine(reading.kind.name, Kind.SLOT, reading = reading))
            }
            this@withReadings.getOrNull(index)?.let(::add)
        }
    }
}

private fun festivalPrayer(
    base: Outline,
    part: SiddurPart,
    flags: Set<String>,
    nusach: Nusach,
    showAll: Boolean,
): List<SiddurLine> {
    val fallback =
        when (part) {
            SHACHARIT_YOM_TOV -> SHACHARIT_SHABBAT
            MINCHA_YOM_TOV -> MINCHA_SHABBAT
            else -> MAARIV_SHABBAT
        }
    val ordinary = base.read(baseKeys(fallback, nusach), flags, showAll)
    val amidah =
        base.read(
            when (nusach) {
                Nusach.ASHKENAZ -> listOf("תפילות לשלוש רגלים/עמידה לערבית, שחרית, מנחה")
                Nusach.SEFARD -> listOf("לשלש רגלים/תפילת עמידה לשחרית, מנחה וערבית של ג רגלים")
                Nusach.EDOT_HAMIZRACH -> listOf("תפילה לשלש רגלים/עמידה")
            },
            flags,
            showAll,
        )
    val start = ordinary.indexOfFirst { it.kind == Kind.HEADING && plain(it.html) in amidahHeadings }
    if (start < 0 || amidah.isEmpty()) return amidah
    val level = ordinary[start].headingLevel
    val end = (start + 1 until ordinary.size).firstOrNull {
        ordinary[it].kind == Kind.HEADING && ordinary[it].headingLevel <= level
    } ?: ordinary.size
    return ordinary.take(start) + amidah.map { it.copy(special = true) } + ordinary.drop(end)
}

private val amidahHeadings = setOf("עמידה", "תפילת עמידה", "תפילת שבע", "תפלת שבע")

private fun morningAdditions(
    lines: List<SiddurLine>,
    base: Outline,
    flags: Set<String>,
    date: LocalDate,
    prefs: SiddurPrefs,
    sharedLulav: Outline?,
    showAll: Boolean,
): List<SiddurLine> {
    val nusach = prefs.nusach
    val hallel = if ("israeli_hallel" in flags) emptyList() else hallelLines(base, flags, nusach, showAll)
    val hoshanot = base.hoshanot(date, prefs, flags)
    val musafKeys = when {
        "festival" in flags || "chol_hamoed" in flags -> when (nusach) {
            Nusach.ASHKENAZ -> listOf("תפילות לשלוש רגלים/מוסף")
            Nusach.SEFARD -> listOf("לשלש רגלים/מוסף לג רגלים")
            Nusach.EDOT_HAMIZRACH -> listOf("תפילה לשלש רגלים/מוסף")
        }
        "sat" in flags -> listOf("מוסף לשבת", "מוסף של שבת")
        "rosh_chodesh" in flags -> listOf("מוסף עמידה לראש חודש", "מוסף לראש חודש", "סדר ראש חודש/מוסף")
        else -> emptyList()
    }
    val musaf = base.read(musafKeys, flags, showAll)
        .takeWhile { plain(it.html) != "קידוש לילי ראש השנה" }
        .map { it.copy(special = true) }
    val roshChodeshPsalm = when {
        "rosh_chodesh" !in flags && !showAll -> emptyList()
        nusach == Nusach.ASHKENAZ && "sat" in flags -> base.read(listOf("סיום תפילה/ברכי נפשי"), flags, showAll)
        nusach == Nusach.EDOT_HAMIZRACH -> base.read(listOf("סדר ראש חודש/ברכי נפשי"), flags, showAll)
        else -> emptyList()
    }.map { it.copy(special = "rosh_chodesh" in flags, skipped = it.skipped || "rosh_chodesh" !in flags) }
    val psalmPosition = if (nusach == Nusach.ASHKENAZ) {
        musaf.indexOfFirst { it.kind == Kind.HEADING && plain(it.html) in setOf("לדוד", "שיר הכבוד") }
    } else {
        musaf.indexOfFirst { it.kind == Kind.HEADING && plain(it.html) == "עלינו" }
    }.takeIf { it >= 0 } ?: musaf.size
    val afterMusaf = if (nusach == Nusach.ASHKENAZ) hoshanot else emptyList()
    val musafWithPsalm = musaf.take(psalmPosition) + roshChodeshPsalm + afterMusaf + musaf.drop(psalmPosition)
    val reading = lines.indexOfFirst {
        it.kind == Kind.HEADING && (plain(it.html).startsWith("קריאת התורה") || plain(it.html).startsWith("סדר קריאת התורה"))
    }.takeIf { it >= 0 } ?: lines.size
    val endPrayers = if ("sat" in flags || "festival" in flags) {
        lines.size
    } else {
        (reading until lines.size).firstOrNull {
            lines[it].kind == Kind.HEADING && plain(lines[it].html) in setOf("עלינו", "עלינו לשבח")
        } ?: lines.size
    }
    val hallelMarker = lines.indexOfFirst {
        val label = plain(it.html)
        label in setOf("בראש חודש אומרים כאן הלל", "בראש חודש אומרים כאן הלל.") ||
            label.startsWith("בשבת רח או חול המועד או חנוכה אומרים הלל לפני הקדיש")
    }.takeIf { it in 0 until reading }
    val hasKaddishAfterMarker = hallelMarker?.let { marker ->
        lines.getOrNull(marker + 1)?.let { plain(it.html).startsWith("ואומר החזן קדיש תתקבל") }
    } == true
    val kaddish = if (nusach != Nusach.ASHKENAZ && !hasKaddishAfterMarker &&
        hallel.any { it.kind == Kind.TEXT && !it.skipped } && "minyan" in flags
    ) {
        hallelKaddish(base, flags, nusach, showAll)
    } else {
        emptyList()
    }
    val afterHallel = if (nusach == Nusach.SEFARD) hoshanot else emptyList()
    val additions = base.lulavBlessings(date, prefs, flags, sharedLulav) +
        hallel.withLulavCues(prefs, flags) + afterHallel + kaddish
    val beforeMusaf = lines.take(hallelMarker ?: reading) + additions +
        lines.subList(hallelMarker?.plus(1) ?: reading, endPrayers)
    return beforeMusaf + musafWithPsalm + lines.drop(endPrayers)
}

private fun hallelLines(
    base: Outline,
    flags: Set<String>,
    nusach: Nusach,
    showAll: Boolean,
): List<SiddurLine> {
    val fullHallel = "hallel_shalem" in flags
    val halfHallel = "chatzi_hallel" in flags
    return if (fullHallel || halfHallel || showAll) {
        val keys = when (nusach) {
            Nusach.ASHKENAZ -> listOf("ראש חודש/הלל")
            Nusach.SEFARD -> listOf("לראש חודש/סדר הלל")
            Nusach.EDOT_HAMIZRACH -> listOf("סדר ראש חודש/הלל לראש חודש ולמועדים")
        }
        val corpus = base.read(keys, flags, showAll).takeWhile {
            val label = plain(it.html)
            !label.startsWith(edotHallelKaddishRubric) && !label.startsWith(sefardHallelKaddishRubric)
        }.let { lines ->
            if (nusach == Nusach.SEFARD && "rosh_chodesh" !in flags) {
                lines.takeWhile { !plain(it.html).startsWith("יש נוהגין לומר ברח אחר הלל") }
            } else {
                lines
            }
        }
        if ("israeli_hallel" in flags) return israeliHallel(corpus, flags, showAll)
        corpus.mapNotNull { line ->
            val label = prayerText(plain(line.html))
            val halfOnlyRubric = label.startsWith("בימים שאין גומרים את ההלל")
            val skipsPsalm = label.startsWith("לא לנו") || label.startsWith("אהבתי") || "מדלגין" in label
            val omitted = (halfHallel || showAll && !fullHallel) &&
                (skipsPsalm || halfOnlyRubric || nusach == Nusach.EDOT_HAMIZRACH && edotFullHallelLabels.any(label::startsWith))
            val html = if (showAll) line.html else line.html.replace(hallelSkipInstruction, "")
            if (!showAll && (omitted || halfOnlyRubric || html.isBlank())) {
                null
            } else {
                line.copy(html = html, special = true, skipped = line.skipped || omitted || !halfHallel && !fullHallel)
            }
        }
    } else {
        emptyList()
    }
}

private val edotHallelKaddishRubric = "ואומר החזן קדיש תתקבל, ובחנוכה אומר רק חצי קדיש"
private val sefardHallelKaddishRubric = "בראש חודש וביום טוב ובחול המועד אומר החזן קדיש שלם"
private val edotFullHallelLabels = listOf(
    "ברוך אתה", "יהללוך", "בימים שגומרים את ההלל אומר החזן", "ברשות מורי ורבותי", "והחזן מברך",
)
private val hallelSkipInstruction = Regex("""<small>[^<>]*מדלגין[^<>]*</small>""")

private fun hallelKaddish(base: Outline, flags: Set<String>, nusach: Nusach, showAll: Boolean): List<SiddurLine> {
    val full = "musaf" in flags
    if (nusach == Nusach.SEFARD) {
        val source = base.read(listOf("לראש חודש/סדר הלל"), flags, showAll)
        val start = source.indexOfFirst { plain(it.html).startsWith(sefardHallelKaddishRubric) }
        if (start < 0) return emptyList()
        val body = source.drop(start + 1).takeWhile { plain(it.html) != "שיר של יום" }
        return if (full) {
            body.filterNot { plain(it.html).startsWith("עד כאן אומרים בחנוכה") }
        } else {
            body.takeWhile { !plain(it.html).startsWith("עד כאן אומרים בחנוכה") }
                .filterNot { plain(it.html) == "קדיש שלם" }
        }
    }
    val source = base.read(listOf("סדר ראש חודש/הלל לראש חודש ולמועדים"), flags, showAll)
    val start = source.indexOfFirst { plain(it.html).startsWith(edotHallelKaddishRubric) }
    if (start < 0) return emptyList()
    val text = source[start].html
    val instruction = if (full) text.substringBefore(',') + ":</small>" else "<small>" + text.substringAfter("ובחנוכה אומר רק ")
    return listOf(source[start].copy(html = instruction)) + source.drop(start + 1).take(if (full) 4 else 1)
}

private val numberedOmer = Regex("^.*?([0-9]{1,2})[.]\\s*היום")
private val datedOmer = Regex("^[א-ת]{1,3} (ניסן|אייר|סיון|סיוון)$")

private fun selectOmer(
    lines: List<SiddurLine>,
    flags: Set<String>,
    nusach: Nusach,
): List<SiddurLine> {
    val count = flags.firstNotNullOfOrNull { it.removePrefix("omer_").toIntOrNull() } ?: return emptyList()
    if (nusach == Nusach.EDOT_HAMIZRACH) {
        val dates = lines.indices.filter { datedOmer.matches(plain(lines[it].html)) }.take(49)
        if (dates.size != 49) return lines
        val start = dates[count - 1]
        val end = dates.getOrNull(count) ?: start + 3
        return lines.take(dates.first()) + lines.subList(start, end).map { it.copy(special = true) } + lines.drop(dates.last() + 3)
    }
    return lines.mapNotNull { line ->
        val day = numberedOmer.find(plain(line.html))?.groupValues?.get(1)?.toIntOrNull()
        when (day) {
            null -> line
            count -> line.copy(special = true)
            else -> null
        }
    }
}
