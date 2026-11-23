package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineSelectionRepository

// Pardes (request 45 of docs/DESKTOP_STAGE3_PLAN.md: "מעבר עמודים בpdf לא מעביר מקביל את תוכן העניינים"): a tractate's
// scan selects no line as it pages, and its table of contents follows the amud at its top all the same.

/**
 * The table of contents' entry of [lineId] selected, as a selected line's is ([repository]'s entry and the path to it,
 * as ContentUseCase reads them), the line itself not: the entry of the amud at a scan's top follows its pages.
 */
internal suspend fun BookContentStateManager.showInToc(
    repository: LineSelectionRepository,
    lineId: Long,
) {
    val tocId = runSuspendCatching { repository.getTocEntryIdForLine(lineId) }.getOrNull() ?: return
    if (tocId == state.value.toc.selectedEntryId) return
    val tocPath = repository.getAncestorPath(tocId)
    updateToc(save = false) {
        copy(
            selectedEntryId = tocId,
            breadcrumbPath = tocPath,
        )
    }
}
