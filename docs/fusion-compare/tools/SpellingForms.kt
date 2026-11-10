// The כתיב מלא/חסר forms of every word of the given searches, as an extra-forms table for ExtraForms.kt (EXPANSIONS.md):
//
//   SpellingFormsKt OUT.tsv searches.tsv [searches.tsv ...]
//
// A word is read as LuceneSearchEngine reads it (HebrewTextUtils.normalizeHebrew, then StandardAnalyzer), and gets the
// forms of Otzaria's rules (OtzariaLexical.kt's spellingVariations, up to 32: an inner ו or י left out, a ו or י added),
// in the index's letters (final letters as their base form). ExtraForms.kt keeps those some line holds, the most common
// first.

import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import java.io.File

fun main(args: Array<String>) {
    val analyzer = StandardAnalyzer()
    val words = sortedSetOf<String>()
    for (file in args.drop(1)) {
        for (line in File(file).readLines().drop(1)) {
            val query = line.split('\t').getOrNull(1) ?: continue
            analyzer.tokenStream("text", HebrewTextUtils.normalizeHebrew(query)).use { ts ->
                val term = ts.addAttribute(CharTermAttribute::class.java)
                ts.reset()
                while (ts.incrementToken()) words += term.toString()
                ts.end()
            }
        }
    }
    val finalOf = mapOf('כ' to 'ך', 'מ' to 'ם', 'נ' to 'ן', 'פ' to 'ף', 'צ' to 'ץ')
    val baseOf = finalOf.entries.associate { (b, f) -> f to b }
    var n = 0
    File(args[0]).bufferedWriter().use { out ->
        for (w in words) {
            if (w.isEmpty()) continue
            val typed = w.dropLast(1) + (finalOf[w.last()] ?: w.last())
            val forms = spellingVariations(typed, 32).map { f -> f.map { baseOf[it] ?: it }.joinToString("") }
                .filter { it != w }.distinct()
            if (forms.isNotEmpty()) {
                out.write(w + "\t" + forms.joinToString("\t") + "\n")
                n++
            }
        }
    }
    println("${words.size} words, $n with forms")
}
