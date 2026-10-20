// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.android.ui.settings

import io.github.kdroidfilter.seforimapp.android.ui.onboarding.PARDES_BASED_ON

// From LicencesSettings.kt (no Compose): Settings › רישיונות's texts, and the licence files' paragraphs.

private val listItem = Regex("""^(\(?([0-9]{1,2}|[a-z]|[ivx]{1,4})[.)]|[-*•])\s""")
private val rule = Regex("""^[-=_*]{3,}$""")

/**
 * Paragraphs (blank-line separated) with their hard-wrapped lines joined. A line stays on its own after a short line
 * (a title, an address) or a long one (the fonts' notices are not wrapped), and list items and rules keep theirs.
 * Only the display changes: the files themselves stay word for word.
 */
internal fun reflow(text: String): List<String> =
    text.replace("\r\n", "\n").split(Regex("""\n[ \t]*\n""")).mapNotNull { block ->
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@mapNotNull null
        buildString {
            lines.forEachIndexed { i, line ->
                if (i > 0) {
                    val previous = lines[i - 1]
                    val wrapped = previous.length in 40..90 && !rule.matches(previous) && !rule.matches(line) && !listItem.containsMatchIn(line)
                    append(if (wrapped) ' ' else '\n')
                }
                // An 80-column rule would wrap into three lines on a phone
                append(if (rule.matches(line)) line.take(24) else line)
            }
        }
    }

internal const val LICENCES_INTRO =
    "פרדס מופץ תחת רישיון GNU AGPL v3 עם תנאי ייחוס נוסף, וכולל רכיבים של אחרים, כל אחד ברישיון שלו. כאן רשומים " +
        "כולם, ונוסח כל רישיון נפתח בלחיצה על שמו."

internal val PARDES_LICENCE = listOf(
    PARDES_BASED_ON,
    "זית ו־SeforimLibrary: ⁦Copyright (C) 2025 Elie Gambache⁩, ברישיון GNU AGPL v3 עם תנאי ייחוס נוסף לפי סעיף 7ב:",
    "⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩",
    "גם פרדס מופץ תחת רישיון זה, וקוד המקור שלו זמין תחת אותו רישיון. הוא מסופק כמות שהוא, ללא כל אחריות.",
)

internal const val LIBRARIES_NOTE =
    "הספריות שברישיון LGPL אפשר להחליף: קוד המקור המלא של פרדס מאפשר לבנות אותו מחדש עם גרסה משונה שלהן."

internal const val DATA_NOTE = "הספרים עצמם מגיעים מ־Sefaria ומאוצריא דרך SeforimLibrary, כל ספר ברישיון שלו (ראו תנאים)."
