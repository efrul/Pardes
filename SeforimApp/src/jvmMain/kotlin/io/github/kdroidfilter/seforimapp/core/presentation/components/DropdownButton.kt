package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.menuStyle

/**
 * DropdownButton is a convenience composable inspired by Jewel's split buttons.
 * It renders a regular button that includes a chevron indicator, and clicking anywhere
 * on the button toggles a popup with custom content.
 *
 * Unlike a traditional split button, the primary action also opens the popup.
 */
@Composable
fun DropdownButton(
    popupContent: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    maxPopupHeight: Dp = 360.dp,
    minPopupHeight: Dp = Dp.Unspecified,
    popupWidthMatchButton: Boolean = true,
    popupWidthMultiplier: Float = 1f,
    showScrollbar: Boolean = true,
    alignToEndOfAnchor: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    var popupOpen by remember { mutableStateOf(false) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier =
            modifier
                .onGloballyPositioned { coords ->
                    anchorOffset = coords.positionInWindow().round()
                    anchorSize = coords.size
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        OutlinedButton(onClick = { popupOpen = !popupOpen }, enabled = enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                Icon(
                    key = AllIconsKeys.General.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (popupOpen && enabled) {
            val provider =
                remember(anchorOffset, anchorSize, alignToEndOfAnchor) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset {
                            var x =
                                if (alignToEndOfAnchor) {
                                    anchorOffset.x + anchorSize.width - popupContentSize.width
                                } else {
                                    anchorOffset.x
                                }
                            var y = anchorOffset.y + anchorSize.height + 4
                            // Clamp horizontally inside window
                            if (x + popupContentSize.width > windowSize.width) {
                                x = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                            }
                            if (x < 0) x = 0
                            if (y + popupContentSize.height > windowSize.height) {
                                val aboveY = anchorOffset.y - popupContentSize.height - 4
                                y = aboveY.coerceAtLeast(0)
                            }
                            return IntOffset(x, y)
                        }
                    }
                }
            Popup(
                popupPositionProvider = provider,
                properties = PopupProperties(focusable = true),
                onDismissRequest = { popupOpen = false },
            ) {
                val widthDp = with(LocalDensity.current) { (anchorSize.width * popupWidthMultiplier).toDp() }
                val menuStyle = JewelTheme.menuStyle
                val shape = RoundedCornerShape(menuStyle.metrics.cornerSize)
                val scrollState = rememberScrollState()
                // Pardes: up and down move between the rows, Escape closes, the popup taking the keys as it opens
                // (request 69 of stage 3)
                val focusManager = LocalFocusManager.current
                val keys = remember { FocusRequester() }
                LaunchedEffect(Unit) { keys.requestFocus() }
                // One row marked at a time, by the keys or the pointer, as in a menu (2026-11-20)
                val marking = remember { DropdownMarking() }
                Box(
                    Modifier
                        .dropdownKeys(focusManager, marking) { popupOpen = false }
                        .pointerInput(marking) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Move) marking.pointerAt(event.changes.first().position)
                                }
                            }
                        }.focusRequester(keys)
                        .focusable()
                        .then(if (popupWidthMatchButton) Modifier.width(widthDp) else Modifier)
                        .requiredHeightIn(min = minPopupHeight, max = maxPopupHeight)
                        .shadow(menuStyle.metrics.shadowSize, shape)
                        .clip(shape)
                        .background(menuStyle.colors.background)
                        .border(menuStyle.metrics.borderWidth, menuStyle.colors.border, shape),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            // Pardes: the rows' marking centred, as much room on the start side as the scrollbar's (2026-11-20)
                            .padding(start = if (showScrollbar) 12.dp else 0.dp)
                            // Reserve space so content doesn't shift when scrollbar appears
                            .padding(end = if (showScrollbar) 12.dp else 0.dp)
                            // Pardes: the row the keys move to comes into view at once (request 69 of stage 3)
                            .revealAtOnce(scrollState, marking),
                    ) {
                        // Pardes: the rows' marking (request 69 of stage 3)
                        CompositionLocalProvider(LocalDropdownMarking provides marking) { popupContent { popupOpen = false } }
                    }
                    if (showScrollbar) {
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            adapter = rememberScrollbarAdapter(scrollState),
                        )
                    }
                }
            }
        }
    }
}
