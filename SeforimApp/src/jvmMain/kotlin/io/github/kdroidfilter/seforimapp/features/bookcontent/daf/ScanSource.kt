package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.pdf.PdfOutlineEntry
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import java.io.File

// Pardes (request 52 of docs/DESKTOP_STAGE3_PLAN.md): "בתיקייה אישית של גמרות, לקבוע לכל מסכת באיזה עמוד בקובץ נמצא
// דף ב", in a file of the tractate with pages before its daf ב., or of several tractates joined (request 43).

/**
 * A tractate's scan: its [file], how the file's name fits the tractate ([fit]), whether it is in the folder the user
 * chose ([personal]), the page of the file (from 0) of the tractate's daf ב. ([first]), and how many of the file's pages
 * are the tractate's ([count]; null for all those after [first]). [firstSet] when the user set [first]; else it is
 * guessed, by the file's bookmarks ([firstByBookmarks]) or the tractates named before it in the file's name
 * ([firstByName]), else 0.
 */
data class ScanSource(
    val file: File,
    val fit: ScanFit = ScanFit.EXACT,
    val personal: Boolean = false,
    val first: Int = 0,
    val count: Int? = null,
    val firstSet: Boolean = false,
) {
    /** The tractate's pages in a file of [pages] pages, from its daf ב. */
    fun pagesIn(pages: Int): Int = (pages - first).coerceAtLeast(0).let { rest -> count?.coerceAtMost(rest) ?: rest }
}

/** The key of the first page set for [tractate] in [file], by the file's name: a folder moved keeps it. */
internal fun firstPageKey(
    file: File,
    tractate: String,
): String = "${file.name}$KEY_SEPARATOR$tractate"

/** The file's name and the tractate of a first page's [key] ([firstPageKey]). */
internal fun firstPageOfKey(key: String): Pair<String, String> =
    key.substringBeforeLast(KEY_SEPARATOR) to key.substringAfterLast(KEY_SEPARATOR)

private const val KEY_SEPARATOR = "|"

/** The amudim a tractate has from ב., by its table of contents [toc]; null when it names none. */
internal fun amudCount(toc: List<TocEntry>): Int? =
    toc
        .mapNotNull { entry -> amudOf(entry.text)?.let(::amudIndex) }
        .filter { it != Int.MAX_VALUE }
        .maxOrNull()
        ?.let { it - amudIndex(FIRST_AMUD) + 1 }
        ?.takeIf { it > 0 }

/**
 * The page of [tractate]'s daf ב. by the file's bookmarks [outline]: under the bookmark named after the tractate, at
 * any depth, its bookmark of daf ב. (`ב`, `דף ב.`), else that bookmark's own page; in a file of the tractate alone
 * ([alone]), a bookmark of daf ב. anywhere. Null when there is none.
 */
internal fun firstByBookmarks(
    tractate: String,
    outline: List<PdfOutlineEntry>,
    alone: Boolean,
): Int? {
    val named = outline.flattened().firstOrNull { ScanNames.indexIn(ScanNames.words(it.title), tractate) >= 0 }
    val within = named?.children ?: outline.takeIf { alone } ?: return null
    return within.flattened().firstOrNull { isDafBet(it.title) && it.page != null }?.page ?: named?.page
}

/**
 * The page of [tractate]'s daf ב. in a file of the tractates [inName], in their order in its name: after those before
 * it ([amuds], each one's amudim from ב.), from the nearest of them whose first page the user set ([set]), else from the
 * file's start; null when one of them is not known.
 */
internal fun firstByName(
    tractate: String,
    inName: List<String>,
    amuds: (String) -> Int?,
    set: (String) -> Int? = { null },
): Int? {
    val index = inName.indexOf(tractate).takeIf { it >= 0 } ?: return null
    val from = (index - 1 downTo 0).firstOrNull { set(inName[it]) != null }
    val start = from?.let { set(inName[it]) } ?: 0
    return inName.subList(from ?: 0, index).fold(start as Int?) { sum, before -> sum?.let { total -> amuds(before)?.let { total + it } } }
}

// A bookmark of daf ב. (`ב`, `ב.`, `דף ב`, `דף ב עמוד א`, `ב ע"א`)
private fun isDafBet(title: String): Boolean = ScanNames.words(title).filter { it != "דף" && it != "עמוד" } in DAF_BET

private val DAF_BET = setOf(listOf("ב"), listOf("ב", "א"), listOf("ב", "עא"))

private const val FIRST_AMUD = "2a"

private fun List<PdfOutlineEntry>.flattened(): List<PdfOutlineEntry> = flatMap { listOf(it) + it.children.flattened() }
