package io.github.kdroidfilter.seforimapp.features.settings.fonts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.typography.UserFont
import io.github.kdroidfilter.seforimapp.core.presentation.typography.UserFonts
import io.github.kdroidfilter.seforimapp.features.settings.library.isolatedPath
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_user_font_missing
import seforimapp.seforimapp.generated.resources.settings_user_fonts_add
import seforimapp.seforimapp.generated.resources.settings_user_fonts_added
import seforimapp.seforimapp.generated.resources.settings_user_fonts_failed
import seforimapp.seforimapp.generated.resources.settings_user_fonts_hint
import seforimapp.seforimapp.generated.resources.settings_user_fonts_no_hebrew
import seforimapp.seforimapp.generated.resources.settings_user_fonts_none
import seforimapp.seforimapp.generated.resources.settings_user_fonts_remove
import seforimapp.seforimapp.generated.resources.settings_user_fonts_title
import java.io.File

/**
 * Settings › גופנים: the fonts the user added ([UserFonts]), each with a link that takes it out, and a button that adds
 * one from a TTF or OTF file; it says when a font added has no Hebrew letters.
 */
@Composable
fun UserFontsCard() {
    val userFonts = remember { UserFonts.sharedOrNull() } ?: return
    val fonts by userFonts.fonts.collectAsState()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<FontAddResult?>(null) }
    UserFontsCardContent(
        fonts = fonts,
        result = result,
        onAdd = {
            scope.launch {
                // Off the GTK event-loop thread, as the data screen's pickers (see pickDatabaseParts)
                val picked =
                    withContext(Dispatchers.IO) { FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("ttf", "otf"))) }
                if (picked != null) {
                    result =
                        withContext(Dispatchers.IO) {
                            runCatching { userFonts.add(File(picked.path)) }
                                .fold(
                                    { FontAddResult.Added(it.font.name, it.hebrew) },
                                    { FontAddResult.Failed(it.message ?: it.toString()) },
                                )
                        }
                }
            }
        },
        onRemove = { code ->
            result = null
            userFonts.remove(code)
        },
    )
}

@Composable
internal fun UserFontsCardContent(
    fonts: List<UserFont>,
    result: FontAddResult?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.settings_user_fonts_title), fontSize = 15.sp)
        Text(
            text = stringResource(Res.string.settings_user_fonts_hint),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (fonts.isEmpty()) {
            Text(text = stringResource(Res.string.settings_user_fonts_none), fontSize = 13.sp)
        }
        fonts.forEach { font ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = font.name, fontSize = 13.sp)
                Link(text = stringResource(Res.string.settings_user_fonts_remove), onClick = { onRemove(font.code) })
            }
        }
        val wide = Modifier.fillMaxWidth()
        when (result) {
            is FontAddResult.Added ->
                if (result.hebrew) {
                    InlineInformationBanner(
                        text = stringResource(Res.string.settings_user_fonts_added, isolatedPath(result.name)),
                        modifier = wide,
                    )
                } else {
                    InlineWarningBanner(
                        text = stringResource(Res.string.settings_user_fonts_no_hebrew, isolatedPath(result.name)),
                        modifier = wide,
                    )
                }
            is FontAddResult.Failed ->
                InlineErrorBanner(
                    text = stringResource(Res.string.settings_user_fonts_failed, isolatedPath(result.reason)),
                    modifier = wide,
                )
            null -> Unit
        }
        OutlinedButton(onClick = onAdd) { Text(stringResource(Res.string.settings_user_fonts_add)) }
    }
}

/**
 * The fonts a list of fonts offers, [codes] with their [labels], and the user font of [fontCode] whose file is gone when
 * a setting names it: listed as missing (its text is shown in the default font).
 */
@Composable
fun withMissingFont(
    fontCode: String,
    codes: ImmutableList<String>,
    labels: ImmutableList<String>,
): Pair<ImmutableList<String>, ImmutableList<String>> {
    val missing = UserFonts.sharedOrNull()?.fontOf(fontCode)?.takeIf { !it.present } ?: return codes to labels
    return (codes + fontCode).toImmutableList() to
        (labels + stringResource(Res.string.settings_user_font_missing, missing.name)).toImmutableList()
}
