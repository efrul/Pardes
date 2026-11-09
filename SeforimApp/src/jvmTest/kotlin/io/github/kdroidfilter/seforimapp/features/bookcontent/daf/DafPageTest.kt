package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.background
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Berakhot 35b, a page whose every line the alignment is sure of, drawn from its layout: its lines in runs of a
 * column, each run a text whose words a click finds. The drawing is written to build/reports/daf/ to look at.
 */
class DafPageTest {
    private val fonts = File("src/commonMain/composeResources/font")
    private val gemara =
        FontFamily(
            Font(File(fonts, "FrankRuehlCLM-Medium.otf"), FontWeight.Normal),
            Font(File(fonts, "FrankRuehlCLM-Bold.otf"), FontWeight.Bold),
        )

    // A variable font: its bold is its weight axis at 700
    private val rashi =
        FontFamily(
            Font(File(fonts, "notorashihebrew.ttf"), FontWeight.Normal),
            Font(File(fonts, "notorashihebrew.ttf"), FontWeight.Bold),
        )

    private fun amud(): DafAmud {
        val dir = File(assertNotNull(javaClass.classLoader.getResource("daf/berakhot-35b.json")).toURI()).parentFile
        val file = assertNotNull(DafLayouts(dir).tractate("Berakhot"))
        assertEquals("ברכות", file.tractateHe)
        return file.amudim.single { it.amud == "35b" }
    }

    private fun layout(amud: DafAmud): AmudLayout {
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Rtl)
        return layoutAmud(amud, 1200f / (amud.block[2] - amud.block[0]), measurer, density, gemara, rashi)
    }

    @Test
    fun everyLineIsInARunOfItsColumnEvenlySpaced() {
        val amud = amud()
        val laidOut = layout(amud)
        val placed = laidOut.runs.flatMap { run -> run.lines.filterNotNull().map { it.index } }
        assertEquals(
            amud.lines.indices
                .filter { amud.lines[it].t.isNotBlank() }
                .sorted(),
            placed.sorted(),
            "every line once",
        )
        for (run in laidOut.runs) {
            val column =
                amud.lines[
                    run.lines
                        .filterNotNull()
                        .first()
                        .index,
                ].c
            assertTrue(run.lines.filterNotNull().all { amud.lines[it.index].c == column }, "a run is of one column")
            for (line in run.lines.filterNotNull()) {
                assertTrue(line.indent >= -0.5f, "a line starts inside its run")
                assertTrue(line.size <= run.size + 0.01f, "a line's letters are its column's, or smaller to fit")
            }
        }
        // The Gemara of this amud widens twice, as Tosafot and then Rashi end beside it: three boxes, no other column in
        // any of them
        val gemara = laidOut.runs.filter { it.isGemara && !it.isBig }
        assertEquals(3, gemara.size)
        for (run in laidOut.runs) {
            for (other in laidOut.runs) {
                if (other === run || other.isBig || run.isBig) continue
                val across = minOf(run.left + run.width, other.left + other.width) - maxOf(run.left, other.left)
                val bottom = minOf(run.top + run.pitch * run.lines.size, other.top + other.pitch * other.lines.size)
                val down = bottom - maxOf(run.top, other.top)
                assertTrue(across <= 1f || down <= run.pitch * 0.5f, "two runs' boxes overlap")
            }
        }
    }

    @Test
    fun aRunsTextHasItsLinesWordsInOrderAndFindsThem() {
        val amud = amud()
        val run = layout(amud).runs.first { it.lines.filterNotNull().size > 3 }
        val words = run.lines.filterNotNull().flatMap { it.words }
        assertEquals(
            words.joinToString(" "),
            run.text
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" "),
        )
        run.lines.forEachIndexed { slot, line ->
            if (line == null) return@forEachIndexed
            for (i in line.words.indices) {
                val range = run.rangeOf(slot, i)
                assertEquals(line.words[i], run.text.substring(range.first, range.last + 1))
                assertEquals(DafWord(line.index, i), run.wordAt(range.first))
                assertEquals(DafWord(line.index, i), run.wordAt(range.last))
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aClickOnAWordReachesItsCallback() {
        val amud = amud()
        val laidOut = layout(amud)
        val width = laidOut.width.toInt() + 1
        val height = laidOut.height.toInt() + 1
        val clicked = mutableListOf<DafWord>()
        val scene =
            ImageComposeScene(width, height, Density(1f), LayoutDirection.Rtl) {
                DafAmudText(laidOut, 1f, gemara, rashi, Color.Black, onWordClick = { word, _ -> clicked += word })
            }
        try {
            scene.render()
            // The first word of a Gemara line: at its line's right end, in the line's middle
            val run = laidOut.runs.first { it.isGemara && !it.isBig }
            val slot = run.lines.indexOfFirst { it != null && it.words.size > 2 }
            val line = run.lines[slot]!!
            val at = Offset(run.left + run.width - line.indent - 3f, run.top + run.pitch * (slot + 0.5f))
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, at)
            scene.render()
            assertEquals(listOf(DafWord(line.index, 0)), clicked)
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aRightClickOpensTheMenuOnItsWordAndSelectsNothing() {
        val laidOut = layout(amud())
        val ranges = mutableListOf<DafRange?>()
        val context = mutableListOf<DafWord>()
        val menus = mutableListOf<Offset>()
        val scene =
            ImageComposeScene(laidOut.width.toInt() + 1, laidOut.height.toInt() + 1, Density(1f), LayoutDirection.Rtl) {
                DafAmudText(
                    laidOut,
                    1f,
                    gemara,
                    rashi,
                    Color.Black,
                    onRange = { ranges += it },
                    onContextWord = { context += it },
                    onMenu = { menus += it },
                )
            }
        try {
            scene.render()
            val run = laidOut.runs.first { it.isGemara && !it.isBig }
            val slot = run.lines.indexOfFirst { it != null && it.words.size > 2 }
            val line = run.lines[slot]!!
            val at = Offset(run.left + run.width - line.indent - 3f, run.top + run.pitch * (slot + 0.5f))
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isSecondaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, at)
            scene.render()
            assertEquals(listOf(DafWord(line.index, 0)), context, "the menu's word")
            assertEquals(listOf(at), menus, "the menu opens where the pointer is")
            assertTrue(ranges.isEmpty(), "the selection stays as it was")
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aDragGoesDownAColumnFromOneOfItsBoxesToTheNext() {
        val amud = amud()
        val laidOut = layout(amud)
        // A column of two boxes or more, one above the other (as Rashi above the Gemara, then beside it)
        val column =
            laidOut.runs
                .filter { !it.isBig }
                .groupBy { it.column }
                .values
                .first { it.size >= 2 }
        val (upper, lower) = column.sortedBy { it.top }.let { it[0] to it[1] }
        val ranges = mutableListOf<DafRange?>()
        val scene =
            ImageComposeScene(laidOut.width.toInt() + 1, laidOut.height.toInt() + 1, Density(1f), LayoutDirection.Rtl) {
                DafAmudText(laidOut, 1f, gemara, rashi, Color.Black, onRange = { ranges += it })
            }
        try {
            scene.render()
            val from = upper.lines.indexOfFirst { it != null && it.words.size > 2 }
            val first = upper.lines[from]!!
            val start = Offset(upper.left + upper.width - first.indent - 3f, upper.top + upper.pitch * (from + 0.5f))
            val to = lower.lines.indexOfFirst { it != null && it.words.size > 2 }
            val second = lower.lines[to]!!
            val end = Offset(lower.left + lower.width - second.indent - 3f, lower.top + lower.pitch * (to + 0.5f))
            scene.sendPointerEvent(PointerEventType.Press, start, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Move, start + Offset(-20f, 0f), buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Move, end, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, end)
            scene.render()
            val range = assertNotNull(ranges.lastOrNull())
            assertEquals(DafWord(first.index, 0), range.first, "from the word the drag began on")
            assertEquals(DafWord(second.index, 0), range.last, "to the word in the column's next box")
            // All the lines between, of that column only
            val text = amud.textOf(range)
            assertTrue(text.startsWith(first.words.first()) && text.endsWith(second.words.first()))
        } finally {
            scene.close()
        }
    }

    @Test
    fun aRangeIsReadInTheAmudsOrderAndCopiedAsPrinted() {
        val amud = amud()
        val line = amud.lines.indexOfFirst { it.c == "R" }
        val range = DafRange(DafWord(line + 1, 1), DafWord(line, 2))
        assertEquals(DafWord(line, 2), range.first, "a drag back up is read from its top")
        assertTrue(DafWord(line, 5) in range && DafWord(line + 1, 0) in range && DafWord(line + 1, 2) !in range)
        val words =
            amud.lines[line]
                .t
                .split(' ')
                .filter { it.isNotEmpty() }
        val next =
            amud.lines[line + 1]
                .t
                .split(' ')
                .filter { it.isNotEmpty() }
        assertEquals((words.drop(2) + next.take(2)).joinToString(" "), amud.textOf(range))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun drawsThePage() {
        val amud = amud()
        val laidOut = layout(amud)
        val scene =
            ImageComposeScene(laidOut.width.toInt() + 1, laidOut.height.toInt() + 1, Density(1f), LayoutDirection.Rtl) {
                DafAmudText(laidOut, 1f, gemara, rashi, Color.Black, Modifier.background(Color.White))
            }
        try {
            val png = assertNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)).bytes
            val out = File("build/reports/daf/berakhot-35b.png")
            out.parentFile.mkdirs()
            out.writeBytes(png)
            assertTrue(png.size > DRAWN_PNG_BYTES, "the page drew nearly nothing")
        } finally {
            scene.close()
        }
    }

    private companion object {
        /** A white page of this size is some 12 KB of PNG, a drawn one some 880 KB. */
        const val DRAWN_PNG_BYTES = 100_000
    }
}
