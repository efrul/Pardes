package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.commentator_detach
import kotlin.math.abs

// Grid capacity thresholds at the reference commentary font size ([REFERENCE_TEXT_SIZE]).
// At runtime the effective minimums scale with the user's chosen font size so the grid
// widens when the text is enlarged and tightens when it shrinks.
internal val MIN_CELL_WIDTH_AT_REF = 320.dp
private val MIN_CELL_HEIGHT_AT_REF = 150.dp
private const val MAX_ROWS_PER_PAGE = 2

// Default commentTextSize as produced by [rememberAnimatedTextSettings]: the global
// DEFAULT_TEXT_SIZE (16) multiplied by the 0.875 commentary ratio.
private const val REFERENCE_TEXT_SIZE = 14f

// Floor for the downward scaling — prevents absurdly narrow cells at very small fonts.
private const val MIN_TEXT_SCALE = 0.55f

// Amplifies the deviation below the reference size so the user feels a meaningful shift
// when zooming out (the raw commentTextSize range is narrow — ~12.25 to 28 — so a
// direct proportional scale barely moves). Only applied when rawScale < 1.
private const val DOWNWARD_AMPLIFIER = 3f

// Below this pane width the grid is allowed to collapse to 1×1 (pane truly too narrow
// to split). Anything wider keeps at least 2 cells per page so the layout never feels
// wasted.
private val NARROW_PANE_WIDTH = 420.dp

internal data class CommentariesGridCapacity(
    val cols: Int,
    val rows: Int,
    val perPage: Int,
)

internal data class CommentariesPageLayout(
    val rows: Int,
    val colsPerRow: Int,
)

/**
 * Derive the commentaries grid capacity from the pane size and the user's commentary
 * font size. Pure helper — extracted so it can be unit-tested without Compose.
 *
 * [maxPerPage] is the user-configured ceiling on commentators per page: 0 means automatic
 * (no cap). A positive value only ever lowers the computed capacity — when the pane has room
 * for fewer than the cap, the smaller fit-based count wins.
 */
internal fun computeCommentariesGridCapacity(
    paneWidthDp: Float,
    paneHeightDp: Float,
    commentTextSize: Float,
    maxPerPage: Int = 0,
): CommentariesGridCapacity {
    val rawScale = commentTextSize / REFERENCE_TEXT_SIZE
    val textScale =
        if (rawScale >= 1f) {
            1f
        } else {
            (1f - (1f - rawScale) * DOWNWARD_AMPLIFIER).coerceAtLeast(MIN_TEXT_SCALE)
        }
    val minWidth = MIN_CELL_WIDTH_AT_REF.value * textScale
    val minHeight = MIN_CELL_HEIGHT_AT_REF.value * textScale
    var cols = maxOf(1, (paneWidthDp / minWidth).toInt())
    var rows = maxOf(1, (paneHeightDp / minHeight).toInt()).coerceAtMost(MAX_ROWS_PER_PAGE)
    if (cols * rows < 2 && paneWidthDp >= NARROW_PANE_WIDTH.value) {
        if (paneWidthDp >= paneHeightDp) cols = 2 else rows = 2
    }
    var perPage = cols * rows
    // Apply the user ceiling, shrinking cols/rows so the layout stays consistent with the
    // capped count. Capping never raises capacity, so a narrow pane that only fits 1 still
    // shows 1 even when the cap is higher.
    if (maxPerPage in 1 until perPage) {
        perPage = maxPerPage
        cols = cols.coerceAtMost(perPage)
        rows = ((perPage + cols - 1) / cols).coerceAtLeast(1)
    }
    return CommentariesGridCapacity(cols = cols, rows = rows, perPage = perPage)
}

/**
 * Spread [itemCount] commentators across the minimal number of rows bounded by [cols].
 * Partial pages are rebalanced so the actual items fill the row width instead of
 * leaving empty slots (e.g. 4 items in a 5-cap page → 2 rows of 2).
 */
internal fun computeCommentariesPageLayout(
    itemCount: Int,
    cols: Int,
): CommentariesPageLayout {
    if (itemCount <= 0 || cols <= 0) return CommentariesPageLayout(rows = 0, colsPerRow = 0)
    val rowsNeeded = ((itemCount + cols - 1) / cols).coerceAtLeast(1)
    val colsPerRow = ((itemCount + rowsNeeded - 1) / rowsNeeded).coerceAtLeast(1)
    return CommentariesPageLayout(rows = rowsNeeded, colsPerRow = colsPerRow)
}

/**
 * Shared grid scaffolding for single-line and multi-line commentaries. The [column] slot
 * renders one commentator column given its bookId.
 *
 * The grid capacity (cols × rows) is derived from the available space using
 * [MIN_CELL_WIDTH_AT_REF] and [MIN_CELL_HEIGHT_AT_REF]; any overflow spills into additional
 * vertical pager pages. The anchor commentator (top-left of the current page) is preserved
 * across resizes so the user keeps their reading context when the pager re-paginates.
 */
@Composable
internal fun CommentatorsGridScaffold(
    config: CommentariesLayoutConfig,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onFlushPersist: () -> Unit,
    // The commentators of the page shown (empty once the grid goes), for the text to offer their passages only
    onVisibleChange: (List<String>) -> Unit = {},
    // The commentator of the passage selected in the text: its page is the one shown, whatever repaginates
    focusCommentator: String? = null,
    column: @Composable (commentatorId: Long) -> Unit,
) {
    val selected = config.selectedCommentators
    if (selected.isEmpty()) return

    // Page-swipe events are coalesced via save = false upstream so rapid navigation
    // doesn't hammer the disk. The final in-memory state is flushed here when the
    // scaffold leaves composition (tab close, book change, pane hidden).
    val currentOnFlushPersist by rememberUpdatedState(onFlushPersist)
    DisposableEffect(Unit) {
        onDispose { currentOnFlushPersist() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val capacity =
            computeCommentariesGridCapacity(
                paneWidthDp = maxWidth.value,
                paneHeightDp = maxHeight.value,
                commentTextSize = config.textSizes.commentTextSize,
                maxPerPage = config.maxCommentatorsPerPage,
            )
        val cols = capacity.cols
        val perPage = capacity.perPage
        val pages =
            remember(selected, perPage) {
                selected.chunked(perPage)
            }
        val pagerState =
            rememberPagerState(
                initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                pageCount = { pages.size },
            )

        // Guard against out-of-bounds [currentPage] when the page count shrinks (the user
        // deselects enough commentators to collapse pages, or the pane widens and packs
        // the remaining items into fewer pages). Runs before the anchor effect so the
        // pager is always in a valid state even when the anchor no longer exists.
        LaunchedEffect(pages.size) {
            val maxValid = (pages.size - 1).coerceAtLeast(0)
            if (pagerState.currentPage > maxValid) {
                pagerState.scrollToPage(maxValid)
            }
        }

        val currentOnPageChange by rememberUpdatedState(onPageChange)
        val currentOnVisibleChange by rememberUpdatedState(onVisibleChange)
        LaunchedEffect(pagerState.settledPage, pages) { currentOnVisibleChange(pages.getOrNull(pagerState.settledPage).orEmpty()) }
        DisposableEffect(Unit) { onDispose { currentOnVisibleChange(emptyList()) } }

        // Anchor = commentator the user is focused on. Used to follow:
        //   - page navigation (anchor tracks the first item of the current page)
        //   - selection additions (anchor jumps to the newly added commentator)
        //   - repagination from resize (anchor stays put so the visible commentator remains)
        val anchorName = remember { mutableStateOf(selected.firstOrNull()) }

        // Keyed on [settledPage] (not [currentPage]) so that mid-animation updates do not
        // overwrite the target anchor and abort an in-flight scroll. Notifies the caller
        // and re-anchors to the first commentator of the new page in a single effect.
        LaunchedEffect(pagerState.settledPage) {
            val settled = pagerState.settledPage
            currentOnPageChange(settled)
            pages.getOrNull(settled)?.firstOrNull()?.let { anchorName.value = it }
        }

        // Detect newly added commentators and promote the last one as the anchor so the
        // pager scrolls to the page containing it. Skips the first composition so the
        // restored page from saved state is preserved. The same name also flags a
        // short-lived highlight on the new cell so the user can locate it visually.
        val previousSelected = remember { mutableStateOf<List<String>?>(null) }
        val recentlyAdded = remember { mutableStateOf<String?>(null) }
        LaunchedEffect(selected) {
            val prev = previousSelected.value
            if (prev != null) {
                val addedLast = selected.lastOrNull { it !in prev }
                if (addedLast != null) {
                    anchorName.value = addedLast
                    recentlyAdded.value = addedLast
                }
            }
            previousSelected.value = selected.toList()
        }
        // The selected passage's commentator anchors the pager, so its commentary stays on the page shown even when a
        // new line (or a resize) repaginates
        LaunchedEffect(focusCommentator, selected) {
            if (focusCommentator != null && focusCommentator in selected) anchorName.value = focusCommentator
        }
        LaunchedEffect(recentlyAdded.value) {
            if (recentlyAdded.value != null) {
                // Slightly longer than the underline draw + hold + fade-out cycle so the
                // header animation completes before we clear the flag.
                delay(2000)
                recentlyAdded.value = null
            }
        }

        // Scroll the pager to the anchor's page whenever pagination changes (resize,
        // selection update) or the anchor itself moves (new commentator picked).
        LaunchedEffect(perPage, pages, anchorName.value) {
            val anchor = anchorName.value ?: return@LaunchedEffect
            val idx = selected.indexOf(anchor)
            if (idx < 0) return@LaunchedEffect
            val target = (idx / perPage).coerceAtMost((pages.size - 1).coerceAtLeast(0))
            if (target != pagerState.currentPage) {
                pagerState.animateScrollToPage(target)
            }
        }

        // The pages side by side, their dots in a row above them. The pager's own gestures stay off, so a
        // commentator's vertical scroll never turns a page: a sideways swipe does (pageSwipe).
        val multiPage = pages.size > 1
        Column(modifier = Modifier.fillMaxSize()) {
            if (multiPage) {
                HorizontalPagerIndicator(pagerState = pagerState, pages = pages)
            }
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (multiPage) Modifier.pageSwipe(pagerState) else Modifier),
                beyondViewportPageCount = 1,
                userScrollEnabled = false,
            ) { pageIdx ->
                CommentatorPageGrid(
                    names = pages[pageIdx],
                    cols = cols,
                    config = config,
                    recentlyAdded = recentlyAdded.value,
                    column = column,
                )
            }
        }
    }
}

/**
 * Turns the commentators' pages by a sideways swipe: a mouse drag (outside the texts, where a drag
 * selects), a touchpad's sideways swipe with two fingers (a pan on Windows, a scroll elsewhere), a tilt wheel or
 * Shift + wheel. One page per gesture.
 */
@Composable
internal fun Modifier.pageSwipe(pagerState: PagerState): Modifier {
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragStartPage by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val dragState =
        rememberDraggableState { delta ->
            // The delta comes reversed in RTL (reverseDirection): negative moves on to the next page
            pagerState.dispatchRawDelta(-delta)
        }
    // A page change cut short (the selection changed again meanwhile) leaves the pager between two pages, and its
    // own gestures, which would snap it, are off: snap it to the nearest page once it rests.
    LaunchedEffect(pagerState) {
        snapshotFlow { !dragging && !pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) > SNAP_EPSILON }
            .collect { offPage -> if (offPage) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage) } }
    }
    return this
        .pointerInput(pagerState, isRtl) {
            // Initial pass: ahead of the columns, which would take Shift + wheel as their own scroll.
            awaitPointerEventScope {
                val panPerTurn = SWIPE_PAN_DISTANCE.toPx()
                var accumulated = 0f
                var lastScrollAt = 0L
                var turned = false
                // The fingers' move so far in a touchpad's swipe
                var panX = 0f
                var panY = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val now = change.uptimeMillis
                    // A pause, or a new touchpad swipe, ends the gesture: the next one may turn another page
                    if (event.type == PointerEventType.PanStart || now - lastScrollAt > SWIPE_GESTURE_GAP_MS) {
                        accumulated = 0f
                        turned = false
                        panX = 0f
                        panY = 0f
                    }
                    val sideways =
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val delta = change.scrollDelta
                                when {
                                    abs(delta.x) > abs(delta.y) -> delta.x
                                    event.keyboardModifiers.isShiftPressed && delta.y != 0f -> delta.y
                                    else -> continue
                                }
                            }
                            // Two fingers on a touchpad come as a pan (Windows), in px, the way a scroll goes (tried on a
                            // Windows touchpad); an up-and-down one is the column's scroll
                            PointerEventType.PanMove -> {
                                panX += change.panOffset.x
                                panY += change.panOffset.y
                                if (abs(panX) <= abs(panY)) continue
                                change.panOffset.x / panPerTurn
                            }
                            else -> continue
                        }
                    change.consume()
                    lastScrollAt = now
                    if (turned) continue
                    accumulated += sideways
                    if (abs(accumulated) >= SWIPE_SCROLL_THRESHOLD) {
                        turned = true
                        // Scrolling right shows what is on the right: the next page in LTR, the previous in RTL
                        val forward = (accumulated > 0) != isRtl
                        val target = pagerState.currentPage + if (forward) 1 else -1
                        if (target in 0 until pagerState.pageCount) {
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    }
                }
            }
        }.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            reverseDirection = isRtl,
            onDragStarted = {
                dragging = true
                dragStartPage = pagerState.currentPage
            },
            onDragStopped = { velocity ->
                val moved = pagerState.currentPage + pagerState.currentPageOffsetFraction - dragStartPage
                val target =
                    when {
                        velocity < -SWIPE_FLING_VELOCITY || moved > SWIPE_PAGE_FRACTION -> dragStartPage + 1
                        velocity > SWIPE_FLING_VELOCITY || moved < -SWIPE_PAGE_FRACTION -> dragStartPage - 1
                        else -> dragStartPage
                    }.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                scope.launch {
                    try {
                        pagerState.animateScrollToPage(target)
                    } finally {
                        dragging = false
                    }
                }
            },
        )
}

// Wheel units of sideways scroll that turn a page (a tilt-wheel notch or Shift + wheel notch is 1)
private const val SWIPE_SCROLL_THRESHOLD = 1f

// A touchpad's swipe this long sideways turns a page
private val SWIPE_PAN_DISTANCE = 48.dp

// Sideways scroll events closer than this belong to one gesture
private const val SWIPE_GESTURE_GAP_MS = 250L

// A drag let go faster than this (px/s) turns the page whatever its length
private const val SWIPE_FLING_VELOCITY = 400f

// Else a drag turns it once it has moved this fraction of a page
private const val SWIPE_PAGE_FRACTION = 0.2f

// A pager further than this fraction from a page is between two
private const val SNAP_EPSILON = 0.001f

/**
 * Lays out the commentators of a single pager page. The grid is rebalanced so that
 * partial pages fill the full area: instead of reserving empty slots for the unused
 * capacity, the actual commentators are spread evenly across as few rows as needed
 * (bounded by [cols]) and each cell expands via `weight(1f)` to consume the remaining
 * space.
 */
@Composable
private fun CommentatorPageGrid(
    names: List<String>,
    cols: Int,
    config: CommentariesLayoutConfig,
    recentlyAdded: String?,
    column: @Composable (commentatorId: Long) -> Unit,
) {
    if (names.isEmpty()) return
    val layout = computeCommentariesPageLayout(itemCount = names.size, cols = cols)
    val rowsChunks = names.chunked(layout.colsPerRow)
    Column(modifier = Modifier.fillMaxSize()) {
        rowsChunks.forEach { rowNames ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                rowNames.forEach { name ->
                    val id = config.titleToIdMap[name] ?: return@forEach
                    key(id) {
                        CommentatorCell(
                            name = name,
                            commentTextSize = config.textSizes.commentTextSize,
                            isRecentlyAdded = name == recentlyAdded,
                            onTitleClick = { config.onOpenCommentatorBook(id) },
                            onDetach = config.onDetachCommentator?.let { detach -> { detach(name) } },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
                        ) {
                            column(id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentatorCell(
    name: String,
    commentTextSize: Float,
    isRecentlyAdded: Boolean,
    onTitleClick: () -> Unit,
    onDetach: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        CommentatorHeader(
            commentator = name,
            commentTextSize = commentTextSize,
            isRecentlyAdded = isRecentlyAdded,
            onClick = onTitleClick,
            onDetach = onDetach,
        )
        content()
    }
}

/**
 * The pages' dots, in a row above them: in RTL the first page's dot is on the right, as its page. A dot's
 * tooltip names its commentators; the wheel over the row turns the pages too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HorizontalPagerIndicator(
    pagerState: PagerState,
    pages: List<List<String>>,
) {
    val activeColor = JewelTheme.globalColors.text.normal
    // A muted-but-readable dot: the border color is nearly invisible in light mode, so use a
    // semi-transparent text color that keeps enough contrast against the panel in both themes.
    val inactiveColor =
        JewelTheme.globalColors.text.normal
            .copy(alpha = 0.4f)
    val scope = rememberCoroutineScope()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(pagerState) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val delta = change.scrollDelta.y
                            if (delta == 0f) continue
                            change.consume()
                            // Wheel down: the next page
                            val target = pagerState.currentPage + if (delta > 0) 1 else -1
                            if (target in 0 until pagerState.pageCount && !pagerState.isScrollInProgress) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        }
                    }
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { i, names ->
            val isActive = pagerState.currentPage == i
            Tooltip({ Text(names.joinToString(" · ")) }) {
                // Clickable hit-box padded outwards so the dot itself stays small while the
                // click target remains comfortable.
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(20.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                if (!isActive) {
                                    scope.launch { pagerState.animateScrollToPage(i) }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(if (isActive) 10.dp else 7.dp)
                                .background(
                                    color = if (isActive) activeColor else inactiveColor,
                                    shape = CircleShape,
                                ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommentatorHeader(
    commentator: String,
    commentTextSize: Float,
    isRecentlyAdded: Boolean = false,
    onClick: () -> Unit = {},
    // Gives the commentator a pane of its own: a button at the header's end, shown on hover
    onDetach: (() -> Unit)? = null,
) {
    // Progressive underline: the stroke grows from the reading-side edge (LTR → left to
    // right, RTL → right to left) while fading in, holds briefly, then fades out.
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isRecentlyAdded) {
        if (isRecentlyAdded) {
            progress.snapTo(0f)
            alpha.snapTo(1f)
            progress.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            delay(700)
            alpha.animateTo(0f, tween(durationMillis = 650))
        } else if (alpha.value > 0f) {
            alpha.animateTo(0f, tween(durationMillis = 250))
        }
    }
    val highlightColor = JewelTheme.globalColors.outlines.focused
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = commentator,
            fontWeight = FontWeight.Bold,
            fontSize = (commentTextSize * 1.1f).sp,
            textAlign = TextAlign.Center,
            textDecoration = if (isHovered) TextDecoration.Underline else TextDecoration.None,
            modifier =
                Modifier.drawWithContent {
                    drawContent()
                    val a = alpha.value
                    val p = progress.value
                    if (a <= 0f || p <= 0f) return@drawWithContent
                    val y = size.height - 1.dp.toPx()
                    val thickness = 1.5.dp.toPx()
                    val span = size.width * p
                    val startX = if (isRtl) size.width - span else 0f
                    val endX = if (isRtl) size.width else span
                    drawLine(
                        color = highlightColor.copy(alpha = a),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                    )
                },
        )
        if (onDetach != null && isHovered) {
            val detachText = stringResource(Res.string.commentator_detach)
            Tooltip({ Text(detachText) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                IconActionButton(
                    key = AllIconsKeys.Actions.SplitVertically,
                    onClick = onDetach,
                    contentDescription = detachText,
                )
            }
        }
    }
}
