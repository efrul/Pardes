package io.github.kdroidfilter.seforimlibrary.search

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Path

/**
 * For the measurement build only (build.sh, extra_forms_patch.py; EXPANSIONS.md): forms a query word gets beside its
 * dictionary's, as one more dictionary entry placed before the word's own, so that the 32 forms a word keeps hold them.
 * Like the dictionary's forms they count for the presence filter, the phrase boosts and the magic boost (as variants).
 *
 *   -DextraForms=FILE.tsv        word, then its forms, tab separated (the index's letters: no niqqud, finals as base)
 *   -DextraFormsIndex=DIR        the text index: only forms some line holds are kept, the most common first
 *   -DextraFormsKeep=N           how many forms a word keeps (4)
 *
 * With no extraForms the search is the app's own: no word gets any form.
 */
internal object ExtraForms {
    private val forms: Map<String, List<String>> by lazy { load() }

    fun of(token: String): List<String> = forms[token].orEmpty()

    private fun load(): Map<String, List<String>> {
        val file = System.getProperty("extraForms") ?: return emptyMap()
        val keep = System.getProperty("extraFormsKeep")?.toIntOrNull() ?: 4
        val reader = System.getProperty("extraFormsIndex")?.let { DirectoryReader.open(FSDirectory.open(Path.of(it))) }
        try {
            return File(file).readLines().filter { it.isNotBlank() }.associate { line ->
                val parts = line.split('\t')
                val ranked = parts.drop(1).distinct().filter { it != parts[0] }
                    .map { it to (reader?.docFreq(Term("text", it)) ?: 1) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                parts[0] to ranked.take(keep).map { it.first }
            }.filterValues { it.isNotEmpty() }
        } finally {
            reader?.close()
        }
    }
}
