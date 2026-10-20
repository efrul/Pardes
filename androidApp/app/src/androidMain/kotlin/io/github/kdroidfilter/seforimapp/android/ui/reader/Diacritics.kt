// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.ui.reader

// Text helpers of the reader with no Compose, ported from desktop 0.6.1 core/presentation/text: DiacriticsMode.kt and
// HebrewSearch.kt. The rest of that folder is in ReaderText.kt.

/** Which Hebrew diacritics the reader displays; the diacritics button cycles through them (desktop DiacriticsMode). */
enum class DiacriticsMode {
    /** Nikud and teamim. */
    All,

    /** Nikud only: teamim are hidden, and so is the meteg, which mostly marks the silluq. */
    NikudOnly,

    /** Neither nikud nor teamim. */
    None,
    ;

    /** The mode the button moves to; [NikudOnly] is skipped when [hasTeamim] is false, as it would show no change. */
    fun next(hasTeamim: Boolean = true): DiacriticsMode {
        val next = entries[(ordinal + 1) % entries.size]
        return if (next == NikudOnly && !hasTeamim) next.next() else next
    }

    /** True when this mode drops [c] from the displayed text. */
    fun hides(c: Char): Boolean = when (this) {
        All -> false
        NikudOnly -> isTeamim(c) || c == METEG
        None -> isNikudOrTeamim(c)
    }

    /** [text] as displayed in this mode. */
    fun apply(text: String): String = if (this == All) text else text.filterNot(::hides)

    /** As desktop's category_display_settings stores it: 0 none, 1 all, 2 nikud only. */
    val stored: Long
        get() = when (this) {
            All -> 1L
            NikudOnly -> 2L
            None -> 0L
        }

    /** Diacritic layers shown, for the button's dots: nikud and teamim, nikud only, none. */
    val shownLevel: Int
        get() = when (this) {
            All -> 2
            NikudOnly -> 1
            None -> 0
        }

    companion object {
        fun fromStored(value: Long?): DiacriticsMode = when (value) {
            0L -> None
            2L -> NikudOnly
            else -> All
        }
    }
}

/** What the diacritics button does next, as desktop's tooltip says it (DiacriticsButton). */
fun DiacriticsMode.nextActionLabel(hasTeamim: Boolean): String = when (next(hasTeamim)) {
    DiacriticsMode.All -> "הצג ניקוד וטעמים"
    DiacriticsMode.NikudOnly -> "הסתר טעמים"
    DiacriticsMode.None -> if (this == DiacriticsMode.NikudOnly) "הסתר ניקוד" else "הסתר ניקוד וטעמים"
}

private const val METEG = 'ֽ'

private fun isTeamim(c: Char): Boolean = c.code in 0x0591..0x05AF

/** True for nikud (vowel points, meteg included) and ta'amim (cantillation) characters. */
internal fun isNikudOrTeamim(c: Char): Boolean =
    isTeamim(c) || (c.code in 0x05B0..0x05BD) || c == 'ׁ' || c == 'ׂ' || c == 'ׇ'

/** True when [text] carries nikud or teamim (desktop HebrewTextUtils.containsNikud/containsTeamim). */
fun hasDiacritics(text: String): Boolean = text.any(::isNikudOrTeamim)

// ---- Hebrew-aware matching (HebrewSearch.kt) ----

/** [src] without nikud, teamim, geresh and gershayim, and the map from plain index to original index. */
fun stripDiacriticsWithMap(src: String): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    for (i in src.indices) {
        val ch = src[i]
        if (!isNikudOrTeamim(ch) && ch != '״' && ch != '׳') {
            out.append(ch)
            map[count++] = i
        }
    }
    return out.toString() to (if (count == map.size) map else map.copyOf(count))
}

/** Strips what [mode] hides (keeping ׳ ״), as [DiacriticsMode.apply], with the map stripped index -> original index. */
fun stripNikudTeamimWithMap(src: String, mode: DiacriticsMode = DiacriticsMode.None): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    for (i in src.indices) {
        val ch = src[i]
        if (!mode.hides(ch)) {
            out.append(ch)
            map[count++] = i
        }
    }
    return out.toString() to (if (count == map.size) map else map.copyOf(count))
}

internal fun replaceFinalsWithBase(text: String): String =
    text.replace('ך', 'כ').replace('ם', 'מ').replace('ן', 'נ').replace('ף', 'פ').replace('ץ', 'צ')

private val TEAMIM_REGEX = Regex("[֑-֯]")
private val NIKUD_REGEX = Regex("[ְֱֲֳִֵֶַָׇֹֻּֽׁׂ]")

internal fun normalizeQueryForHebrew(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw.trim()
    s = s.replace(TEAMIM_REGEX, "")
    s = s.replace(NIKUD_REGEX, "")
    s = s.replace('־', ' ')
    s = s.replace("״", "").replace("׳", "")
    s = replaceFinalsWithBase(s)
    return s.lowercase()
}

internal fun mapToOrigIndex(mapToOrig: IntArray, plainIndex: Int): Int {
    if (mapToOrig.isEmpty()) return plainIndex
    return mapToOrig[plainIndex.coerceIn(0, mapToOrig.size - 1)]
}

/** Original index -> displayed index in [mode], -1 where the character is hidden. */
fun createOriginalToStrippedMap(src: String, mode: DiacriticsMode = DiacriticsMode.None): IntArray {
    val result = IntArray(src.length) { -1 }
    var strippedIndex = 0
    for (i in src.indices) {
        if (!mode.hides(src[i])) result[i] = strippedIndex++
    }
    return result
}

/** Maps an offset of the text with diacritics to the displayed text (the next shown character when it is hidden). */
fun mapOriginalToStripped(originalOffset: Int, originalToStrippedMap: IntArray): Int {
    if (originalToStrippedMap.isEmpty()) return originalOffset
    val strippedLength = originalToStrippedMap.count { it >= 0 }
    if (originalOffset >= originalToStrippedMap.size) return strippedLength
    val safe = originalOffset.coerceAtLeast(0)
    if (originalToStrippedMap[safe] >= 0) return originalToStrippedMap[safe]
    for (i in safe until originalToStrippedMap.size) if (originalToStrippedMap[i] >= 0) return originalToStrippedMap[i]
    return strippedLength
}

/** Diacritic- and final-letter-insensitive matches of [query] in [text], as ranges of [text]. */
internal fun findAllMatchesOriginal(text: String, query: String): List<IntRange> = findAllMatchesNormalized(text, normalizeQueryForHebrew(query))

/** [findAllMatchesOriginal] for a query already through [normalizeQueryForHebrew]. */
internal fun findAllMatchesNormalized(text: String, q: String): List<IntRange> {
    if (q.length < 2) return emptyList()
    val (plain, map) = stripDiacriticsWithMap(text)
    val plainSearch = replaceFinalsWithBase(plain).lowercase()
    val out = mutableListOf<IntRange>()
    var from = 0
    while (from <= plainSearch.length - q.length) {
        val idx = plainSearch.indexOf(q, startIndex = from)
        if (idx == -1) break
        val startOrig = mapToOrigIndex(map, idx)
        val endOrig = mapToOrigIndex(map, idx + q.length - 1) + 1
        if (endOrig > startOrig) out += (startOrig until endOrig)
        from = idx + q.length
    }
    return out
}
