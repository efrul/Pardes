// Vendored unchanged from Zayit 0.6.1: SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/RepositorySnippetSourceProvider.kt
// (tag v0.6.1). Keep it identical; Android changes belong in a variant file.
// Also compiled into the Android 4.4 edition (legacy/build.gradle.kts, sharedAppSources): no Compose, no AndroidX
// lifecycle, and nothing above API 19 that is not behind an SDK_INT check (:legacy:lintDebug reports it).
package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineSnippetInfo
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import io.github.kdroidfilter.seforimlibrary.search.SnippetSource
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [SnippetProvider] over the app's database: the lines come from [repository]. */
class RepositorySnippetSourceProvider(
    private val repository: SeforimRepository,
) : SnippetProvider {
    override suspend fun getSnippetSources(lines: List<LineSnippetInfo>): Map<Long, SnippetSource> {
        if (lines.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            SnippetSources.build(lines) { bookId, from, to ->
                repository.getLines(bookId, from, to).associate { it.lineIndex to it.content }
            }
        }
    }
}
