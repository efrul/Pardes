package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager

/**
 * Pardes's copy of the library's word dictionary (lexical.db), which the regular search reads in its place
 * ([LuceneSearchEngine]'s `dictionaryPath`). It holds the same words and forms, with two fixes and one addition:
 *
 * 1. **No base without forms.** lexical.db (release 20261012193827) has 4,711 bases that no surface points to, and
 *    2,479 of them are spelled like a form of another base (ברכות, הלכות, לאכול, דברים, תפלין). MagicDictionaryIndex
 *    prefers the base spelled like the query word, so such a word got that empty base: no other form at all.
 * 2. **The commonest forms first.** The search keeps a word's first 32 forms, in the order of the surface ids. Here each
 *    base's surfaces are numbered by how many lines of the library's index hold them, so the 32 are the common ones
 *    (שלוש keeps שלש and שלשה, docs/SEARCH_ORDER_ISSUES.md #4). The variants still come after the surfaces.
 * 3. **The defective spellings (כתיב חסר).** A form written with a ו or a י that the library's older texts leave out
 *    (עשרונים for עשרנים) didn't reach them, and a word typed as they write it had no forms at all. Each form's
 *    defective spellings ([defectiveForms]) that some line holds and that the dictionary doesn't have are forms of its
 *    base too, numbered with the others. A spelling the dictionary has, it keeps under its own base: a defective
 *    spelling is often another word (בשורי, my ox, is בשרי, of meat), and joined to another base (an extra base, listed by
 *    its own number) it took over that base's first forms or the word's whole paradigm.
 *
 * Measured on eval-100 and eval-real (lexical side, docs/LEXICON.md): source in the first ten 13 → 16, a good line in the
 * first three 42 → 47 of 153, about 10 ms more per search (real paradigms instead of nothing). The defective spellings,
 * with the hybrid search (docs/fusion-compare/SPELLING.md): 157 source searches, source first / in 3 / in 10
 * 114/145/154 → 115/146/155; eval-real 4 searches better, none worse; the feedback's graded searches, one query worse.
 *
 * Built from lexical.db and the text index when they change (a library install or update), in the background, into
 * [FILE] next to lexical.db; until it is there the search reads lexical.db itself.
 */
object PardesLexicon {
    const val FILE = "pardes-lexical.db"

    /** Bump when the derivation changes: older files are rebuilt. */
    private const val VERSION = 2
    private const val META = "pardes_meta"
    private const val BATCH = 5_000

    @Volatile
    private var building: Thread? = null

    /**
     * The dictionary the search should read: [FILE] when it is up to date with [lexical] and [indexDir], else
     * [lexical], with [FILE] then rebuilt in the background. A stale [FILE] is deleted first, so the search never
     * reads one of another library. The search opens its dictionary on its first query and keeps it: a rebuild that
     * ends after that serves from the next start.
     */
    fun dictionaryFor(
        lexical: Path,
        indexDir: Path,
    ): Path {
        val target = lexical.resolveSibling(FILE)
        if (!Files.isRegularFile(lexical)) return lexical
        val fingerprint = runCatching { fingerprint(lexical, indexDir) }.getOrElse { return lexical }
        if (storedFingerprint(target) == fingerprint) return target
        runCatching { Files.deleteIfExists(target) }
        building =
            Thread({
                runCatching { build(lexical, indexDir, target, fingerprint) }
                    .onFailure { warnln(it) { "[PardesLexicon] could not build $target; the search keeps lexical.db" } }
            }, "pardes-lexicon").apply {
                isDaemon = true
                start()
            }
        return target
    }

    /**
     * Waits (up to [timeoutMs]) for a rebuild [dictionaryFor] started: the search warm-up calls it before its first
     * query, which opens the dictionary for the whole run.
     */
    fun awaitBuild(timeoutMs: Long) {
        building?.join(timeoutMs)
    }

    /** lexical.db's size and time, and the index's commit: what the file is built from. */
    internal fun fingerprint(
        lexical: Path,
        indexDir: Path,
    ): String {
        val segments =
            Files.list(indexDir).use { s ->
                s
                    .map { it.fileName.toString() }
                    .filter { it.startsWith("segments_") }
                    .sorted()
                    .toList()
            }
        return "v$VERSION|${Files.size(lexical)}|${Files.getLastModifiedTime(lexical).toMillis()}|${segments.joinToString(",")}"
    }

    internal fun storedFingerprint(target: Path): String? {
        if (!Files.isRegularFile(target)) return null
        return runCatching {
            DriverManager.getConnection("jdbc:sqlite:${target.toAbsolutePath()}").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT value FROM $META WHERE key = 'fingerprint'").use { rs ->
                        if (rs.next()) rs.getString(1) else null
                    }
                }
            }
        }.getOrNull()
    }

    /** Writes the fixed copy of [lexical] to [target] (through a temporary file, moved in place when complete). */
    internal fun build(
        lexical: Path,
        indexDir: Path,
        target: Path,
        fingerprint: String,
    ) {
        val started = System.nanoTime()
        val tmp = target.resolveSibling("$FILE.tmp")
        Files.deleteIfExists(tmp)
        try {
            DriverManager.getConnection("jdbc:sqlite:${tmp.toAbsolutePath()}").use { c ->
                c.autoCommit = false
                c.exec("ATTACH DATABASE '${lexical.toAbsolutePath().toString().replace("'", "''")}' AS src")
                val hasExtra = c.hasTable("src", "surface_extra_base")
                c.exec(SCHEMA)
                // Surface ids, each base's surfaces by their lines in the index, with the defective spellings it lacks
                val surfaces = mutableListOf<LongArray>() // id, base id, document frequency
                val spelled = HashMap<Long, Pair<String, Long>>() // the added surfaces: id -> value, base id
                DirectoryReader.open(FSDirectory.open(indexDir)).use { reader ->
                    val rows = mutableListOf<Triple<Long, Long, String>>()
                    c.createStatement().use { st ->
                        st.executeQuery("SELECT id, base_id, value FROM src.surface").use { rs ->
                            while (rs.next()) rows += Triple(rs.getLong(1), rs.getLong(2), rs.getString(3) ?: "")
                        }
                    }
                    val known = HashSet<String>(rows.size * 2) // the terms the dictionary has (or now has)
                    for ((id, base, value) in rows) {
                        val term = HebrewTextUtils.normalizeHebrew(value)
                        val df = if (term.isEmpty() || term.contains(' ')) 0 else reader.docFreq(Term("text", term))
                        surfaces += longArrayOf(id, base, df.toLong())
                        known += term
                    }
                    val absent = HashSet<String>()
                    var next = (rows.maxOfOrNull { it.first } ?: 0L) + 1
                    for ((_, base, value) in rows) {
                        for (form in defectiveForms(value)) {
                            val term = HebrewTextUtils.normalizeHebrew(form)
                            if (term.isEmpty() || term.contains(' ') || term in known || term in absent) continue
                            val df = reader.docFreq(Term("text", term))
                            if (df == 0) {
                                absent += term
                                continue
                            }
                            surfaces += longArrayOf(next, base, df.toLong())
                            spelled[next] = form to base
                            known += term
                            next++
                        }
                    }
                }
                surfaces.sortWith(compareBy<LongArray> { it[1] }.thenByDescending { it[2] }.thenBy { it[0] })
                val renumbered = HashMap<Long, Long>(surfaces.size * 2)
                surfaces.forEachIndexed { i, sf -> renumbered[sf[0]] = i + 1L }
                c.exec("CREATE TEMP TABLE smap (old INTEGER PRIMARY KEY, new INTEGER NOT NULL)")
                c.prepareStatement("INSERT INTO smap VALUES (?, ?)").use { ps ->
                    surfaces.forEachIndexed { i, s ->
                        ps.setLong(1, s[0])
                        ps.setLong(2, i + 1L)
                        ps.addBatch()
                        if (i % BATCH == 0) ps.executeBatch()
                    }
                    ps.executeBatch()
                }
                val extraBases = if (hasExtra) " OR EXISTS (SELECT 1 FROM src.surface_extra_base e WHERE e.base_id = b.id)" else ""
                c.exec(
                    "INSERT INTO base SELECT b.id, b.value FROM src.base b " +
                        "WHERE EXISTS (SELECT 1 FROM src.surface s WHERE s.base_id = b.id)$extraBases",
                )
                c.exec(
                    "INSERT INTO surface SELECT m.new, s.value, s.base_id, NULL FROM src.surface s JOIN smap m ON m.old = s.id ORDER BY m.new",
                )
                c.prepareStatement("INSERT INTO surface VALUES (?, ?, ?, NULL)").use { ps ->
                    spelled.entries.sortedBy { renumbered.getValue(it.key) }.forEachIndexed { i, (id, form) ->
                        ps.setLong(1, renumbered.getValue(id))
                        ps.setString(2, form.first)
                        ps.setLong(3, form.second)
                        ps.addBatch()
                        if (i % BATCH == 0) ps.executeBatch()
                    }
                    ps.executeBatch()
                }
                c.exec("INSERT INTO variant SELECT id, value FROM src.variant")
                c.exec(
                    "INSERT INTO surface_variant SELECT m.new, sv.variant_id FROM src.surface_variant sv " +
                        "JOIN smap m ON m.old = sv.surface_id ORDER BY m.new, sv.variant_id",
                )
                if (hasExtra) {
                    c.exec(
                        "INSERT INTO surface_extra_base SELECT m.new, e.base_id FROM src.surface_extra_base e " +
                            "JOIN smap m ON m.old = e.surface_id ORDER BY m.new, e.base_id",
                    )
                }
                c.exec(INDEXES)
                c.prepareStatement("INSERT INTO $META VALUES ('fingerprint', ?)").use {
                    it.setString(1, fingerprint)
                    it.executeUpdate()
                }
                c.commit()
                c.autoCommit = true
                c.exec("DETACH DATABASE src")
                c.exec("ANALYZE")
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            infoln { "[PardesLexicon] built $target in ${(System.nanoTime() - started) / 1_000_000} ms" }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** At most this many ו and as many י left out of one word ([defectiveForms]). */
    private const val MAX_LEFT_OUT = 3

    /** The ו and י a word may lose are looked for in its first this many runs of them ([defectiveForms]). */
    private const val MAX_RUNS = 10

    /**
     * The defective spellings of [word]: an inner ו or י left out, one from each run of them, never the first letter nor
     * the last nor one right before a final ה (מצוה keeps its ו), up to [MAX_LEFT_OUT] ו and as many י at once, and at
     * least three letters left. The deletions of Otzaria's generate_spelling_variations (otzaria_search_engine, MIT).
     */
    internal fun defectiveForms(word: String): List<String> {
        val n = word.length
        val at = ArrayList<Int>()
        var i = 0
        while (i < n && at.size < MAX_RUNS) {
            val c = word[i]
            if (c != 'ו' && c != 'י') {
                i++
                continue
            }
            var j = i
            while (j < n && word[j] == c) j++
            val k = (i until j).firstOrNull { it > 0 && it + 1 < n }
            if (k != null && !(k + 2 == n && word[n - 1] == 'ה')) at += k
            i = j
        }
        if (at.isEmpty()) return emptyList()
        val letters = word.count { it in 'א'..'ת' }
        val out = LinkedHashSet<String>()
        for (mask in 1 until (1 shl at.size)) {
            val left = at.filterIndexed { p, _ -> mask and (1 shl p) != 0 }
            if (left.count { word[it] == 'ו' } > MAX_LEFT_OUT || left.count { word[it] == 'י' } > MAX_LEFT_OUT) continue
            if (letters - left.size < 3) continue
            out += word.filterIndexed { idx, _ -> idx !in left }
        }
        return out.toList()
    }

    private fun Connection.exec(sql: String) {
        createStatement().use { st ->
            sql
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { st.execute(it) }
        }
    }

    private fun Connection.hasTable(
        schema: String,
        name: String,
    ): Boolean =
        prepareStatement("SELECT 1 FROM $schema.sqlite_master WHERE type = 'table' AND name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { it.next() }
        }

    // The tables and indexes MagicDictionaryIndex reads, as lexical.db declares them
    private const val SCHEMA = """
        CREATE TABLE base (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE);
        CREATE TABLE surface (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE,
            base_id INTEGER NOT NULL REFERENCES base(id), notes TEXT);
        CREATE TABLE variant (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE);
        CREATE TABLE surface_variant (surface_id INTEGER NOT NULL, variant_id INTEGER NOT NULL,
            PRIMARY KEY (surface_id, variant_id));
        CREATE TABLE surface_extra_base (surface_id INTEGER NOT NULL, base_id INTEGER NOT NULL,
            PRIMARY KEY (surface_id, base_id));
        CREATE TABLE $META (key TEXT PRIMARY KEY, value TEXT NOT NULL)
    """

    private const val INDEXES = """
        CREATE INDEX surface_base_id_index ON surface(base_id);
        CREATE INDEX surface_variant_variant_id_index ON surface_variant(variant_id);
        CREATE INDEX surface_extra_base_base_id_index ON surface_extra_base(base_id)
    """
}
