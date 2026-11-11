package io.github.kdroidfilter.seforimapp.siddur

internal fun israeliHallel(
    lines: List<SiddurLine>,
    flags: Set<String>,
    showAll: Boolean,
): List<SiddurLine> {
    val texts = lines.map { it.html.replaceFirst(fullHallelRubric, "") }
    val labels = texts.map(::plain)
    val firstPsalm = labels.indexOfFirst { it.startsWith("הללויה") }
    val closingBlessing = labels.indexOfFirst { it.startsWith("יהללוך") }
    if (firstPsalm < 0 || closingBlessing <= firstPsalm) return emptyList()
    val afterBlessing = closingBlessing + 1
    val end = if (labels.getOrNull(afterBlessing)?.startsWith("ברוך אתה") == true) afterBlessing + 1 else afterBlessing
    val unblessed = "hallel_without_blessings" in flags
    return lines.take(end).mapIndexedNotNull { index, line ->
        val label = labels[index]
        val rubric = label.isBlank() || label.startsWith("אלו ימים") || label.startsWith("בימים שגומרים") ||
            label.startsWith("בימים שאין גומרים") || label.startsWith("והחזן מברך") ||
            label.startsWith("ברח ובחוהמ פסח")
        if (rubric || label.startsWith("ברשות מורי") && "minyan" !in flags) return@mapIndexedNotNull null
        val blessing = index < firstPsalm && (label.startsWith("ברוך אתה") || label.startsWith("ברכת הלל")) ||
            index >= closingBlessing || label == "ברכה בסוף" || label.startsWith("ברשות מורי")
        val skipped = unblessed && blessing
        if (skipped && !showAll) return@mapIndexedNotNull null
        line.copy(html = texts[index], special = true, skipped = line.skipped || skipped)
    }
}

// Some editions keep the instruction to skip Psalm 115 or 116 on the same row as the psalm.
private val fullHallelRubric = Regex("""^\s*<small>[^<]*מדלגין[^<]*</small>(?:\s*<br\s*/?>)?\s*""")
private val afterAmidah =
    setOf("אחר העמידה", "תחנון", "וידוי", "קדיש שלם", "קדיש תתקבל", "קריאת התורה", "אשרי", "עלינו", "עלינו לשבח")

internal fun List<SiddurLine>.withIsraeliHallel(
    addition: List<SiddurLine>,
    part: SiddurPart,
): List<SiddurLine> {
    if (addition.isEmpty() || isEmpty()) return this
    val amidah = indexOfFirst {
        it.kind == SiddurLine.Kind.HEADING && plain(it.html) in setOf("עמידה", "תפילת עמידה")
    }
    if (amidah < 0) return this + addition
    val evening = part.familyOf() == SiddurPart.MAARIV
    val index = (amidah + 1 until size).firstOrNull {
        val line = this[it]
        val label = plain(line.html)
        line.kind == SiddurLine.Kind.HEADING && label in afterAmidah ||
            label.startsWith("יתגדל") ||
            evening && (
                label.startsWith("ואומר החזן קדיש") ||
                    label.startsWith("בחול אומר החזן קדיש") ||
                    label.startsWith("בכל לילות החול")
            )
    } ?: size
    return take(index) + addition + drop(index)
}

internal fun List<SiddurLine>.withIsraeliMaariv(
    psalms: Outline?,
    piyyut: List<SiddurLine>,
    independence: Boolean,
    prefs: SiddurPrefs,
    flags: Set<String>,
    showAll: Boolean,
): List<SiddurLine> {
    if (isEmpty()) return this
    val opening = if (independence) {
        psalms.chapters(flags, showAll, "קז", "צז", "צח")
    } else {
        psalms.chapters(flags, showAll, "צז", "צח", "קכב")
    }
    val stanzas = piyyut.filter { line -> festiveStanzas.any { plain(line.html).startsWith(it) } }
    val song = if (stanzas.isEmpty()) {
        emptyList()
    } else {
        val refrain = SiddurLine(zeHayom, special = true)
        piyyut.take(1).map { it.copy(special = true) } + refrain +
            stanzas.flatMap { listOf(it.copy(special = true), refrain) }
    }
    val blessing =
        if (independence && prefs.israeliPrayerCustom == IsraeliPrayerCustom.DAY_AND_NIGHT_HALLEL) {
            listOf(SiddurLine(shehecheyanu, special = true))
        } else {
            emptyList()
        }
    return take(1) + opening + song + blessing + drop(1)
}

internal fun List<SiddurLine>.withIsraeliShacharit(
    psalms: Outline?,
    jerusalemDay: Boolean,
    nusach: Nusach,
    flags: Set<String>,
    showAll: Boolean,
): List<SiddurLine> {
    // Use the extra psalms of Shabbat and keep the weekday blessings and Amidah.
    val ashkenaz = nusach == Nusach.ASHKENAZ
    val index = indexOfFirst {
        val label = plain(it.html)
        if (ashkenaz) label == "יהי כבוד" else label.startsWith("הושיענו") && "וקבצנו" in label
    }
    val additional = if (ashkenaz) {
        psalms.chapters(flags, showAll, "יט", "לד", "צ", "צא", "קלה", "קלו", "לג")
    } else {
        psalms.chapters(flags, showAll, "יט", "לג", "לד", "צ", "צא", "צח", "קכא", "קכב", "קכג", "קכד", "קלה", "קלו")
    }
    val insertion = if (ashkenaz) index else index + 1
    val morning = if (index < 0) this else take(insertion) + additional + drop(insertion)
    return if (jerusalemDay && isNotEmpty()) morning + psalms.chapters(flags, showAll, "קז") else morning
}

private fun Outline?.chapters(
    flags: Set<String>,
    showAll: Boolean,
    vararg names: String,
): List<SiddurLine> =
    this?.read(names.map { "פרק $it" }, flags, showAll).orEmpty().map { it.copy(special = true) }

private val festiveStanzas = setOf("מקדש מלך", "התנערי", "התעוררי", "לא תבושי", "ימין ושמאל")
private const val zeHayom = "זֶה הַיּוֹם עָשָׂה יְהֹוָה, נָגִילָה וְנִשְׂמְחָה בוֹ."
private const val shehecheyanu =
    "בָּרוּךְ אַתָּה יְהֹוָה אֱלֹהֵינוּ מֶלֶךְ הָעוֹלָם, שֶׁהֶחֱיָנוּ וְקִיְּמָנוּ וְהִגִּיעָנוּ לַזְּמַן הַזֶּה."
