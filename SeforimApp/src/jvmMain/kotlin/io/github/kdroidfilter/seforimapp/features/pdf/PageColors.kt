package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.isBavli
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.ColorMode
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.page_colors_original
import seforimapp.seforimapp.generated.resources.page_colors_original_tooltip

// Pardes (request 42 of docs/DESKTOP_STAGE3_PLAN.md): pages drawn as images, a PDF book's and a tractate's scan, in the
// dark theme's colours, as the daf is drawn: black as the theme's text and white as its panel, with a button of the end
// bar that brings their original colours back.

/** The colours of pages drawn as images in the dark theme. */
object PageColorSettings : SettingsGroup("page_colors") {
    /** Whether pages keep their original colours in the dark theme, rather than its text on its panel; in every tab. */
    val original by boolean(false)
}

/**
 * The matrix that draws black as [ink] and white as [paper], each gray between them in proportion, a channel each: a
 * scan's ink and paper become the theme's text and background.
 */
internal fun inkOnPaper(
    ink: Color,
    paper: Color,
): ColorMatrix =
    ColorMatrix().apply {
        // From the identity: each channel scaled from the ink's to the paper's, the alpha as it is
        listOf(ink.red to paper.red, ink.green to paper.green, ink.blue to paper.blue).forEachIndexed { channel, (from, to) ->
            this[channel, channel] = to - from
            this[channel, OFFSETS] = from * CHANNEL
        }
    }

/** The column of a matrix's offsets, which are in a channel's 0 to 255. */
private const val OFFSETS = 4
private const val CHANNEL = 255f

/**
 * The colours pages drawn as images take now: in the dark theme its text on its panel ([inkOnPaper]), unless their
 * original colours are asked; null in the light theme, or with them.
 */
@Composable
fun rememberPageColors(): ColorFilter? {
    val original by LocalAppGraph.current.appSettings
        .flow(PageColorSettings.original)
        .collectAsState()
    if (!JewelTheme.isDark || original) return null
    val ink = JewelTheme.globalColors.text.normal
    val paper = JewelTheme.globalColors.panelBackground
    return remember(ink, paper) { ColorFilter.colorMatrix(inkOnPaper(ink, paper)) }
}

/** What this draws, through [colors] on a layer of its own: a page the binding draws, which takes no filter itself. */
fun Modifier.pageColors(colors: ColorFilter?): Modifier =
    if (colors == null) {
        this
    } else {
        drawWithContent {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { colorFilter = colors })
                drawContent()
                canvas.restore()
            }
        }
    }

/**
 * The end bar's button of the pages' original colours, in the diacritics' place: in the dark theme, while the tab of
 * [uiState] shows a tractate's scan or a PDF book; its setting holds in every tab.
 */
@Composable
fun PageColorsButton(
    uiState: BookContentState,
    modifier: Modifier = Modifier,
) {
    val book = uiState.navigation.selectedBook ?: return
    val images = pdfIdOf(book.id) != null || (book.isBavli && uiState.content.scanView)
    if (!images || !JewelTheme.isDark) return
    val appSettings = LocalAppGraph.current.appSettings
    val original by appSettings.flow(PageColorSettings.original).collectAsState()
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(Res.string.page_colors_original_tooltip),
        onClick = { appSettings[PageColorSettings.original] = !original },
        isSelected = original,
        icon = ColorMode,
        label = stringResource(Res.string.page_colors_original),
        modifier = modifier,
        iconDescription = stringResource(Res.string.page_colors_original),
    )
}
