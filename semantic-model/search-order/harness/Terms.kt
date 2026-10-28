import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.search.MagicDictionaryIndex
import java.io.File
import java.nio.file.Path

// The forms a query word expands to in the lexical search, and the 32 LuceneSearchEngine keeps of them
// (buildLimitedTermsForToken: the word, its bases, then the dictionary's surface forms and variants, in that order).
//
// args: lexical.db, a UTF-8 file of words, one a line, each optionally followed by a tab and forms (space-separated)
// to look for, out file
fun main(args: Array<String>) {
    val dict = MagicDictionaryIndex.load(HebrewTextUtils::normalizeHebrew, Path.of(args[0])) ?: error("no dictionary at ${args[0]}")
    File(args[2]).bufferedWriter().use { out ->
        for (line in File(args[1]).readLines().filter { it.isNotBlank() }) {
            val word = line.substringBefore('\t').trim()
            val forms = line.substringAfter('\t', "").split(' ').filter { it.isNotBlank() }.map(HebrewTextUtils::normalizeHebrew)
            val token = HebrewTextUtils.normalizeHebrew(word)
            val e = dict.expansionFor(token)
            if (e == null) {
                out.write("$word: no expansion\n")
                continue
            }
            val ordered = LinkedHashSet<String>().apply { add(token); addAll(e.base); addAll(e.surface); addAll(e.variants) }.toList()
            val kept = ordered.take(MAX_TERMS)
            out.write("$word: ${ordered.size} forms; kept: ${kept.joinToString(" ")}\n")
            for (f in forms) {
                val at = ordered.indexOf(f)
                out.write("   $f: ${if (at < 0) "not a form" else if (at < MAX_TERMS) "kept" else "cut (#${at + 1})"}\n")
            }
        }
    }
}

/** LuceneSearchEngine's MAX_SYNONYM_TERMS_PER_TOKEN. */
private const val MAX_TERMS = 32
