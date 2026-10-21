// Vendored from desktop Zayit 0.6.1 htmlparser/src/commonMain (HtmlCompose.kt's plainTextFromHtml), moved out of
// HtmlCompose.kt unchanged so that it compiles without Compose.
// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforim.htmlparser

private val htmlParser = HtmlParser()

/**
 * The text [buildAnnotatedFromHtml] produces with its defaults (footnote markers hidden, their
 * content shown, no images), without building any style: for matching text, not showing it.
 */
fun plainTextFromHtml(html: String): String =
    buildString {
        for (e in htmlParser.parse(html)) {
            when {
                e.isLineBreak -> append("\n")
                e.isImage || e.text.isBlank() -> Unit
                // A hidden marker leaves a space so its neighbours don't collapse
                e.isFootnoteMarker -> if (isNotEmpty() && !endsWith(" ")) append(" ")
                else -> append(e.text)
            }
        }
    }
