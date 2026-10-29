package io.github.kdroidfilter.seforimapp.android.ui.settings

import android.content.Context
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.android.R
import io.github.kdroidfilter.seforimapp.android.ui.onboarding.PARDES_LICENSE_URL
import io.github.kdroidfilter.seforimapp.android.ui.shell.DialogSystemBarsOver
import io.github.kdroidfilter.seforimapp.android.ui.shell.PhoneHeader
import io.github.kdroidfilter.seforimapp.android.ui.shell.WindowBackScope
import io.github.kdroidfilter.seforimapp.android.ui.theme.Zayit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Settings › רישיונות (Android only: desktop has no such page). Pardes's own licence, the repository's LICENSE with
 * Zayit's §7b terms, and the credits and licence texts of everything else the APK carries: libraries, icons, fonts
 * and data. The texts are in assets/licenses (build.gradle.kts copies LICENSE and Lucene's two files there) and open
 * offline, word for word; only the Creative Commons licences open online, as their attribution terms allow.
 */

/** A licence text: bundled (an asset under assets/licenses, or a raw resource), or online when [url] is set. */
internal class LicenceDoc(val title: String, val asset: String? = null, @param:RawRes val raw: Int = 0, val url: String? = null) {
    fun read(context: Context): String =
        (if (asset != null) context.assets.open(asset) else context.resources.openRawResource(raw)).bufferedReader().use { it.readText() }
}

private fun bundled(title: String, file: String) = LicenceDoc(title, asset = "licenses/$file")

private object Docs {
    val pardes = bundled("GNU AGPL v3 ותנאי 7ב", "Pardes-LICENSE.txt")
    val apache = bundled("Apache License 2.0", "Apache-2.0.txt")
    val lgpl = bundled("GNU LGPL v2.1", "LGPL-2.1.txt")
    val gpl = bundled("GNU GPL v2", "GPL-2.0.txt")
    val fontNotices = bundled("הודעות הגופנים", "Fonts-NOTICE.txt")
}

/** One credit: [name], its copyright line(s) [holder] (Latin, shown isolated), an optional Hebrew [note] and its texts. */
private class Credit(val name: String, val holder: String, val docs: List<LicenceDoc>, val note: String? = null)

private val libraryCredits = listOf(
    Credit(
        "Apache Lucene 10.5.2",
        "Copyright 2001-2025 The Apache Software Foundation",
        listOf(bundled("Apache License 2.0 (Lucene)", "Lucene-LICENSE.txt"), bundled("NOTICE (Lucene)", "Lucene-NOTICE.txt")),
        "מנוע החיפוש: lucene-core ושלוש מחלקות של lucene-analysis-common, ששונו כדי לפעול מאנדרואיד 8.",
    ),
    Credit(
        "Apache Commons Compress, IO, Lang, Codec",
        "Copyright 2001-2025 The Apache Software Foundation",
        listOf(Docs.apache, bundled("NOTICE (Apache Commons)", "Apache-Commons-NOTICE.txt")),
    ),
    Credit(
        "zstd-jni, Zstandard",
        "Copyright (c) 2015-present, Luben Karavelov; Copyright (c) Meta Platforms, Inc. and affiliates",
        listOf(bundled("BSD 2-Clause (zstd-jni)", "zstd-jni-LICENSE.txt"), bundled("BSD (Zstandard)", "Zstandard-LICENSE.txt")),
    ),
    Credit("jsoup", "Copyright (c) 2009-2025 Jonathan Hedley", listOf(bundled("MIT (jsoup)", "jsoup-LICENSE.txt"))),
    Credit(
        "ONNX Runtime 1.22.0",
        "Copyright (c) Microsoft Corporation",
        listOf(
            bundled("MIT (ONNX Runtime)", "ONNX-Runtime-LICENSE.txt"),
            bundled("ThirdPartyNotices (ONNX Runtime)", "ONNX-Runtime-ThirdPartyNotices.txt"),
        ),
        "הרצת המודל של החיפוש לפי משמעות, במכשירי 64 ביט. הרכיבים של אחרים שהוא כולל רשומים ב־ThirdPartyNotices.",
    ),
    Credit(
        "requery sqlite-android, SQLite",
        "Copyright (C) 2017-2025 requery.io; The Android Open Source Project. SQLite and its multiplex VFS: public domain",
        listOf(Docs.apache),
        "מנוע SQLite נוסף, לספרייה שנשמרת בחלקים בכרטיס זיכרון FAT32 (עם רכיב ה-multiplex של SQLite).",
    ),
    Credit("KosherJava Zmanim", "Copyright © Eliyahu Hershfeld", listOf(Docs.lgpl)),
    Credit("KosherKotlin", "kdroidFilter, Sternbach-Software, after KosherJava by Eliyahu Hershfeld", listOf(Docs.lgpl)),
    Credit("HebrewNumerals", "kdroidFilter", listOf(Docs.apache)),
    Credit(
        "Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime",
        "Copyright JetBrains s.r.o. and contributors",
        listOf(Docs.apache),
    ),
    Credit("Jetpack Compose, AndroidX", "Copyright The Android Open Source Project", listOf(Docs.apache)),
    Credit(
        "SQLDelight, Kermit, JSpecify, Guava ListenableFuture",
        "Copyright Square, Inc.; Touchlab; The JSpecify Authors; The Guava Authors",
        listOf(Docs.apache),
    ),
)

private val iconCredits = listOf(
    Credit("Bootstrap Icons", "Copyright (c) 2019-2024 The Bootstrap Authors", listOf(bundled("MIT (Bootstrap Icons)", "Bootstrap-Icons-LICENSE.txt"))),
    Credit("Lucide", "Lucide Contributors; Cole Bemis (Feather)", listOf(bundled("ISC (Lucide)", "Lucide-LICENSE.txt"))),
    Credit("Radix Icons", "Copyright (c) 2022 WorkOS", listOf(bundled("MIT (Radix Icons)", "Radix-Icons-LICENSE.txt"))),
    Credit("Tabler Icons", "Copyright (c) 2020-2025 Paweł Kuna", listOf(bundled("MIT (Tabler Icons)", "Tabler-Icons-LICENSE.txt"))),
    Credit("Material Symbols, Material Icons", "Copyright Google LLC", listOf(Docs.apache)),
    Credit(
        "Emoji One",
        "Emoji One",
        listOf(LicenceDoc("CC BY 4.0", url = "https://creativecommons.org/licenses/by/4.0/")),
        "אייקון היד הכותבת (Writing hand, Monotone).",
    ),
)

private val fontCredits = listOf(
    Credit(
        "Culmus: Aharoni, David, Drugulin, Ellinia, Frank Ruehl, Hadasim, Keter YG, Miriam, Miriam Mono, Nachlieli, " +
            "Shofar, Simple, Stam Ashkenaz, Stam Sefarad, Yehuda",
        "Copyright Maxim Iorsh, Yoram Gnat; Latin glyphs (URW)++ Design & Development, Bitstream Inc.",
        listOf(Docs.gpl, bundled("רישיון הקולמוס", "Culmus-LICENSE.txt"), bundled("Bitstream Charter", "Bitstream-Charter-LICENSE.txt"), Docs.fontNotices),
        "גופני פרויקט הקולמוס, ברישיון GPL v2 עם חריג הגופנים.",
    ),
    Credit(
        "Taamey Ashkenaz, Taamey David CLM, Taamey Frank CLM, Keter Aram Tsova",
        "Copyright Yoram Gnat; Hebrew OpenType Layout logic (c) 2003 & 2007, Ralph Hancock & John Hudson",
        listOf(Docs.gpl, Docs.fontNotices),
        "ברישיון GPL v2 עם חריג הגופנים; לוגיקת הפריסה של הטעמים ברישיון MIT (בהודעות הגופנים).",
    ),
    Credit("Frank Ruhl Libre", "Copyright 2015 The Frank Ruhl Libre Project Authors", listOf(bundled("SIL OFL 1.1 (Frank Ruhl Libre)", "OFL-Frank-Ruhl-Libre.txt"))),
    Credit("Miriam Libre", "Copyright 2023 The Miriam Libre Project Authors", listOf(bundled("SIL OFL 1.1 (Miriam Libre)", "OFL-Miriam-Libre.txt"))),
    Credit("Noto Rashi Hebrew, Noto Serif Hebrew", "Copyright 2022 The Noto Project Authors", listOf(bundled("SIL OFL 1.1 (Noto)", "OFL-Noto-Hebrew.txt"))),
    Credit("Libertinus Serif", "Copyright © 2012-2024 The Libertinus Project Authors", listOf(bundled("SIL OFL 1.1 (Libertinus)", "OFL-Libertinus-Serif.txt"))),
    Credit("Cardo", "Copyright (c) 2002-2011, David J. Perry", listOf(bundled("SIL OFL 1.1 (Cardo)", "OFL-Cardo.txt"))),
    Credit("Bona Nova SC", "Copyright 2017 The Bona Nova Project Authors", listOf(bundled("SIL OFL 1.1 (Bona Nova)", "OFL-Bona-Nova-SC.txt"))),
    Credit("Tinos", "Digitized data copyright (c) 2010-2012 Google Corporation.", listOf(Docs.apache)),
)

private val modelCredits = listOf(
    Credit(
        "neodictabert-bilingual-embed",
        "Dicta: The Israel Center for Text Analysis",
        listOf(
            LicenceDoc("CC BY 4.0", url = "https://creativecommons.org/licenses/by/4.0/"),
            LicenceDoc("המודל", url = "https://huggingface.co/dicta-il/neodictabert-bilingual-embed"),
            bundled("NOTICE (המודל של פרדס)", "Pardes-model-NOTICE.md"),
        ),
        "המודל של החיפוש לפי משמעות, של דיקטה: המרכז הישראלי לניתוח טקסטים. הוא אינו בקובץ ההתקנה, ומגיע עם קובצי " +
            "החיפוש לפי משמעות (הגדרות › ניהול נתונים). פרדס המיר אותו ל־ONNX (עם ה־pooling בגרף) וכימת אותו ל־int8. " +
            "במודל שמסומן כנגזר, כמו זה שבקבצים של היום, פרדס גם המשיך לאמן אותו על הטקסטים של הספרייה ועל הקישורים שבה, " +
            "וזיקק אותו למודל קטן יותר. דיקטה אינם תומכים בפרדס או בשינויים האלה, והמודל ניתן כמו שהוא, בלי אחריות.",
    ),
)

private val dataCredits = listOf(
    Credit(
        "HYG Database v4.1",
        "David Nash (astronexus)",
        listOf(
            LicenceDoc("CC BY-SA 4.0", url = "https://creativecommons.org/licenses/by-sa/4.0/"),
            LicenceDoc("המאגר", url = "https://github.com/astronexus/HYG-Database"),
        ),
        "הכוכבים של \"השמיים\" עד בהירות 4 (מיקום, בהירות וצבע) הם קטע מהמאגר, ומופצים גם הם ברישיון CC BY-SA 4.0.",
    ),
    Credit(
        "David Roberts, The Siege and Destruction of Jerusalem by the Romans Under the Command of Titus, A.D. 70 (1850)",
        "Public domain",
        listOf(
            LicenceDoc(
                "נחלת הכלל",
                url = "https://commons.wikimedia.org/wiki/File:David_Roberts_-_The_Siege_and_Destruction_of_Jerusalem_by_the_Romans_Under_the_Command_of_Titus,_A.D._70.jpg",
            ),
        ),
        "התמונה של ווידג'ט בית המקדש היא חיתוך מהציור, עם הבהוב של האש.",
    ),
    Credit(
        "hebcal-learning, hebcal-leyning",
        "Copyright (c) 2026, hebcal",
        listOf(LicenceDoc("BSD 2-Clause (hebcal)", raw = R.raw.limud_notice)),
        "לוחות \"לימוד יומי\" וחלוקת העליות של שניים מקרא.",
    ),
)

/**
 * Latin text inside the RTL pages, isolated (LRI…PDI) so that its punctuation stays where it belongs. A title with
 * Hebrew in it ("GNU AGPL v3 ותנאי 7ב") is left to the RTL paragraph, which orders its words right.
 */
private fun isolated(text: String) = if (text.any { it in '֐'..'׿' }) text else "⁦$text⁩"

@Composable
internal fun LicencesSettings(wide: Boolean) {
    var open by remember { mutableStateOf<LicenceDoc?>(null) }
    val uriHandler = LocalUriHandler.current
    val onDoc: (LicenceDoc) -> Unit = { doc ->
        if (doc.url != null) uriHandler.openSafely(doc.url) else open = doc
    }
    val c = Zayit.colors
    SettingsColumn(wide) {
        // Not justified like MarkdownText: on a phone the Latin runs (the §7b line) would leave wide gaps
        Text(LICENCES_INTRO, fontSize = 15.sp, lineHeight = 23.sp, color = c.text)
        SettingsCard(title = "פרדס") {
            PARDES_LICENCE.forEach { Text(it, fontSize = 15.sp, lineHeight = 23.sp, color = c.text) }
            DocLinks(listOf(Docs.pardes, LicenceDoc("קוד המקור", url = "https://github.com/efrul/Pardes")), onDoc)
        }
        CreditsCard("ספריות", libraryCredits, onDoc, LIBRARIES_NOTE)
        CreditsCard("המודל של החיפוש לפי משמעות", modelCredits, onDoc)
        CreditsCard("אייקונים", iconCredits, onDoc)
        CreditsCard("גופנים", fontCredits, onDoc)
        CreditsCard("נתונים ותמונות", dataCredits, onDoc, DATA_NOTE)
    }
    open?.let { doc -> LicenceTextDialog(doc) { open = null } }
}

@Composable
private fun CreditsCard(title: String, credits: List<Credit>, onDoc: (LicenceDoc) -> Unit, footer: String? = null) {
    val c = Zayit.colors
    SettingsCard(title = title) {
        credits.forEachIndexed { i, credit ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(isolated(credit.name), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text(isolated(credit.holder), fontSize = 13.sp, lineHeight = 18.sp, color = c.textSecondary)
                credit.note?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = c.textSecondary) }
                DocLinks(credit.docs, onDoc)
            }
        }
        footer?.let {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = c.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocLinks(docs: List<LicenceDoc>, onDoc: (LicenceDoc) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        docs.forEach { doc -> LinkText(isolated(doc.title), onClick = { onDoc(doc) }) }
    }
}

/**
 * A licence text, full-screen over the settings (like "ההערות שלי"), left to right as written. The files are wrapped at
 * about 78 columns, too wide for a phone, so their lines are joined again into paragraphs (see [reflow]).
 */
@Composable
internal fun LicenceTextDialog(doc: LicenceDoc, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogSystemBarsOver(status = Zayit.colors.titleBarTone, navigation = Zayit.colors.ground)
        WindowBackScope {
            val c = Zayit.colors
            val context = LocalContext.current
            val blocks by produceState<List<String>?>(null, doc) {
                value = withContext(Dispatchers.IO) { runCatching { reflow(doc.read(context)) }.getOrDefault(emptyList()) }
            }
            Column(Modifier.fillMaxSize().background(c.ground)) {
                PhoneHeader(doc.title, onBack = onDismiss)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(blocks.orEmpty()) { block ->
                                Text(
                                    block,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    color = c.text,
                                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(bottom = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Opens the AGPL link of תנאים and of the onboarding licence step, [PARDES_LICENSE_URL], as the bundled LICENSE, so it
 * reads offline and always matches this build; other links open as usual.
 */
@Composable
internal fun BundledLicenceLinks(content: @Composable () -> Unit) {
    val base = LocalUriHandler.current
    var showLicence by rememberSaveable { mutableStateOf(false) }
    val handler = remember(base) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (uri == PARDES_LICENSE_URL) showLicence = true else base.openUri(uri)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides handler, content = content)
    if (showLicence) LicenceTextDialog(Docs.pardes) { showLicence = false }
}

private val listItem = Regex("""^(\(?([0-9]{1,2}|[a-z]|[ivx]{1,4})[.)]|[-*•])\s""")
private val rule = Regex("""^[-=_*]{3,}$""")

/**
 * Paragraphs (blank-line separated) with their hard-wrapped lines joined. A line stays on its own after a short line
 * (a title, an address) or a long one (the fonts' notices are not wrapped), and list items and rules keep theirs.
 * Only the display changes: the files themselves stay word for word.
 */
private fun reflow(text: String): List<String> =
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

private const val LICENCES_INTRO =
    "פרדס מופץ תחת רישיון GNU AGPL v3 עם תנאי ייחוס נוסף, וכולל רכיבים של אחרים, כל אחד ברישיון שלו. כאן רשומים " +
        "כולם, ונוסח כל רישיון נפתח בלחיצה על שמו."

private val PARDES_LICENCE = listOf(
    "פרדס מבוסס על זית מאת אליהו גמבש ואינו מטעמו: הוא לא יצר אותו, אינו אחראי לו ואינו נותן לו תמיכה.",
    "זית ו־SeforimLibrary: ⁦Copyright (C) 2025 Elie Gambache⁩, ברישיון GNU AGPL v3 עם תנאי ייחוס נוסף לפי סעיף 7ב:",
    "⁦Powered by the technologies that drive Zayit — https://zayitapp.com/⁩",
    "גם פרדס מופץ תחת רישיון זה, וקוד המקור שלו זמין תחת אותו רישיון. הוא מסופק כמות שהוא, ללא כל אחריות.",
)

private const val LIBRARIES_NOTE =
    "הספריות שברישיון LGPL אפשר להחליף: קוד המקור המלא של פרדס מאפשר לבנות אותו מחדש עם גרסה משונה שלהן."

private const val DATA_NOTE = "הספרים עצמם מגיעים מ־Sefaria ומאוצריא דרך SeforimLibrary, כל ספר ברישיון שלו (ראו תנאים)."
