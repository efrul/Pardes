package io.github.kdroidfilter.seforimapp.android.ui.search

import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml

/** The text size the preview's highlight ranges are computed at (ranges do not depend on it). */
private const val PREVIEW_LAYOUT_SIZE = 16f

/** A line's HTML as the preview lays its text out: SearchViewModel's highlight ranges are offsets in it. */
internal fun previewText(html: String): String = buildAnnotatedFromHtml(html, PREVIEW_LAYOUT_SIZE).text
