// From Pardes desktop's SeforimApp/src/jvmMain/.../features/settings/semantic/SemanticPackCard.kt, in its package, so
// that desktop's SemanticPackReleasesTest runs on the port unchanged. Android's card is ui/settings/SemanticPackSection.
package io.github.kdroidfilter.seforimapp.features.settings.semantic

import io.github.kdroidfilter.seforimapp.features.search.semantic.pack.SemanticPack

/** A library release (`20261012193827`, `v3-20261012193827`) as its date, 12.10.2026; as written when it is not one. */
internal fun libraryDate(release: String): String {
    val key = SemanticPack.libraryKey(release)
    if (key.length < 8 || !key.take(8).all(Char::isDigit)) return release
    return "${key.substring(6, 8)}.${key.substring(4, 6)}.${key.substring(0, 4)}"
}
