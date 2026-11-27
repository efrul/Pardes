package io.github.kdroidfilter.seforimapp.features.sefaria.pack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.sefaria_pack_available
import seforimapp.seforimapp.generated.resources.sefaria_pack_cancel
import seforimapp.seforimapp.generated.resources.sefaria_pack_checking
import seforimapp.seforimapp.generated.resources.sefaria_pack_current
import seforimapp.seforimapp.generated.resources.sefaria_pack_description
import seforimapp.seforimapp.generated.resources.sefaria_pack_download
import seforimapp.seforimapp.generated.resources.sefaria_pack_installed
import seforimapp.seforimapp.generated.resources.sefaria_pack_missing
import seforimapp.seforimapp.generated.resources.sefaria_pack_needed
import seforimapp.seforimapp.generated.resources.sefaria_pack_none
import seforimapp.seforimapp.generated.resources.sefaria_pack_problem
import seforimapp.seforimapp.generated.resources.sefaria_pack_progress
import seforimapp.seforimapp.generated.resources.sefaria_pack_prompt
import seforimapp.seforimapp.generated.resources.sefaria_pack_remove
import seforimapp.seforimapp.generated.resources.sefaria_pack_title
import seforimapp.seforimapp.generated.resources.sefaria_pack_update
import java.util.Locale

/**
 * Settings › ניהול נתונים: Sefaria's data, its pack beside the library (SefariaPack): what the folder has, the newest pack
 * and its size, its download or its update, its removal, and whether a start offers it while it is missing.
 */
@Composable
fun SefariaPackCard() {
    val downloads = SefariaDownloads.shared
    val state by downloads.state.collectAsState()
    val appSettings = LocalAppGraph.current.appSettings
    val prompt by appSettings.flow(SefariaPackSettings.prompt).collectAsState()
    LaunchedEffect(Unit) { downloads.refresh() }
    val marked = remember { SefariaPackPrompt.takeFocus() }
    val inView = remember { BringIntoViewRequester() }
    if (marked) {
        // Once the screen is laid out, and again once the check has settled the card's lines
        LaunchedEffect(state.checked) {
            withFrameNanos {}
            inView.bringIntoView()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val colors = JewelTheme.globalColors
    Column(
        modifier =
            Modifier
                .bringIntoViewRequester(inView)
                .fillMaxWidth()
                .clip(shape)
                .border(if (marked) 2.dp else 1.dp, if (marked) colors.outlines.focused else colors.borders.normal, shape)
                .background(colors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.sefaria_pack_title), fontSize = 15.sp)
        Text(text = stringResource(Res.string.sefaria_pack_description), fontSize = 12.sp, color = colors.text.info)
        Text(
            text =
                state.installed?.let { stringResource(Res.string.sefaria_pack_installed, it) }
                    ?: stringResource(Res.string.sefaria_pack_missing),
            fontSize = 13.sp,
        )
        Text(text = availableText(state), fontSize = 12.sp, color = colors.text.info)
        PackWork(state)
        PackButtons(state, downloads)
        if (state.installed == null) {
            CheckboxRow(
                text = stringResource(Res.string.sefaria_pack_prompt),
                checked = prompt,
                onCheckedChange = { appSettings[SefariaPackSettings.prompt] = it },
            )
        }
    }
}

/**
 * Where a feature of Sefaria's data shows it is missing ([SefariaPack]): what it is, and its download in place, with
 * its progress, without opening the settings.
 */
@Composable
fun SefariaPackHint(modifier: Modifier = Modifier) {
    val downloads = SefariaDownloads.shared
    val state by downloads.state.collectAsState()
    LaunchedEffect(Unit) { downloads.refresh() }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sefaria_pack_needed), fontSize = 13.sp, color = JewelTheme.globalColors.text.info)
        PackWork(state)
        val manifest = state.manifest
        when {
            state.work != null -> OutlinedButton(onClick = downloads::cancel) { Text(stringResource(Res.string.sefaria_pack_cancel)) }
            manifest != null && state.installed == null ->
                DefaultButton(onClick = downloads::download) {
                    Text(stringResource(Res.string.sefaria_pack_download, megabytes(manifest.size)))
                }
            state.checking ->
                Text(
                    stringResource(Res.string.sefaria_pack_checking),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
        }
    }
}

// The download or the update while one is out, its cancel while it goes on, and the removal of the folder's pack
@Composable
private fun PackButtons(
    state: SefariaDownloads.State,
    downloads: SefariaDownloads,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val manifest = state.manifest
        when {
            state.work != null ->
                OutlinedButton(onClick = downloads::cancel) { Text(stringResource(Res.string.sefaria_pack_cancel)) }
            manifest != null && state.newer ->
                DefaultButton(onClick = downloads::download) {
                    Text(
                        if (state.installed == null) {
                            stringResource(Res.string.sefaria_pack_download, megabytes(manifest.size))
                        } else {
                            stringResource(Res.string.sefaria_pack_update, manifest.pack, megabytes(manifest.size))
                        },
                    )
                }
        }
        if (state.installed != null && state.work == null) {
            OutlinedButton(onClick = downloads::remove) { Text(stringResource(Res.string.sefaria_pack_remove)) }
        }
    }
}

// The download's progress, or why the last check or download stopped
@Composable
private fun ColumnScope.PackWork(state: SefariaDownloads.State) {
    val work = state.work
    if (work != null) {
        AnimatedHorizontalProgressBar(value = work.done.toFloat() / work.total.coerceAtLeast(1L), modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(Res.string.sefaria_pack_progress, megabytes(work.done), megabytes(work.total)),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
    }
    state.problem?.let { InlineErrorBanner(text = stringResource(Res.string.sefaria_pack_problem, it), modifier = Modifier.fillMaxWidth()) }
}

// What is out: checking, nothing yet, the newest pack and its size, or that the folder has it
@Composable
private fun availableText(state: SefariaDownloads.State): String {
    val manifest = state.manifest
    return when {
        state.checking || !state.checked -> stringResource(Res.string.sefaria_pack_checking)
        manifest == null -> stringResource(Res.string.sefaria_pack_none)
        !state.newer -> stringResource(Res.string.sefaria_pack_current)
        else -> stringResource(Res.string.sefaria_pack_available, manifest.pack, megabytes(manifest.size))
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f", bytes / BYTES_PER_MB)

private const val BYTES_PER_MB = 1_048_576.0
