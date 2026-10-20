package io.github.kdroidfilter.seforimapp.android.ui.search

import io.github.kdroidfilter.seforimapp.android.ui.reader.DiacriticsMode
import io.github.kdroidfilter.seforimapp.legacy.ui.LineText

/**
 * This edition's previewText (the main app's is in its ui/search/PreviewText.kt): a line's HTML as this edition shows
 * it, LineText.render with every mark kept. SearchViewModel's highlight ranges are offsets in this text, so a preview
 * must show the line so for them to fall on the words found.
 */
internal fun previewText(html: String): String = LineText.render(html, DiacriticsMode.All).toString()
