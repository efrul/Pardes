package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Berakhot 35b, a page whose every line the alignment is sure of, drawn from its layout: each line's words fill its
 * box from right to left, none past its edges. The drawing is written to build/reports/daf/ to look at.
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

    @Test
    fun everyLineFillsItsBoxWithoutPassingIt() {
        val amud = amud()
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Rtl)
        val scale = 1200f / (amud.block[2] - amud.block[0])
        val placed = layoutAmud(amud, scale, measurer, density, gemara, rashi, Color.Black)
        assertEquals(133, placed.size)
        for (line in placed) {
            val right = line.xs[0] + line.words[0].size.width
            val left = line.xs.last()
            assertTrue(right <= line.boxRight + 0.5f, "a line ends past its box's right edge")
            assertTrue(left >= line.boxLeft - 0.5f, "a line ends past its box's left edge")
            for (i in 1 until line.words.size) {
                // the words come from the right edge to the left one, none on another
                assertTrue(line.xs[i] + line.words[i].size.width <= line.xs[i - 1] + 0.5f, "two words overlap")
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun drawsThePage() {
        val amud = amud()
        val width = 1200
        val height = ((amud.block[3] - amud.block[1]) * width / (amud.block[2] - amud.block[0])).toInt() + 1
        val scene =
            ImageComposeScene(width, height, Density(1f), LayoutDirection.Rtl) {
                DafPage(amud, gemara, rashi, Color.Black, Modifier.fillMaxWidth().background(Color.White))
            }
        try {
            val png = assertNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)).bytes
            val out = File("build/reports/daf/berakhot-35b.png")
            out.parentFile.mkdirs()
            out.writeBytes(png)
            assertTrue(png.size > 10_000, "the page drew nearly nothing")
        } finally {
            scene.close()
        }
    }
}
