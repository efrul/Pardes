package io.github.kdroidfilter.seforimapp.features.sefaria

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.GZIPInputStream

/**
 * Sefaria's data file (SefariaDb.SCHEMA) built from the rows scripts/sefaria_content.py writes under sefaria-data/ (their
 * rows are described there): for the tests, and for the pack's release (SefariaPackGen). The app only reads the file.
 */
internal object SefariaPackBuilder {
    /** The repository's data: sefaria-data/ beside the app's module, where Gradle runs the tests. */
    val repositoryData: File = File(System.getProperty("user.dir")).resolve("../sefaria-data").normalize()

    // The repository's data built once, for the tests whose screens show it
    private val repositoryPack: File by lazy {
        File.createTempFile("sefaria-pack", ".db").also {
            it.deleteOnExit()
            buildFrom(repositoryData, it, pack = 1)
        }
    }

    /** The app's screens shown the repository's data (SefariaData), until [hideData]. */
    fun showRepositoryData() {
        SefariaData.close()
        SefariaData.locate = { repositoryPack }
    }

    /** The app's screens back to the data beside the library, which the tests have none of. */
    fun hideData() {
        SefariaData.close()
        SefariaData.locate = { null }
    }

    /** [file] built from the repository's files in [dir]. */
    fun buildFrom(
        dir: File,
        file: File,
        pack: Int,
        dump: String = "",
    ) = build(
        file,
        pack = pack,
        dump = dump,
        topics = read(File(dir, "topics.tsv.gz")),
        sages = read(File(dir, "sages.tsv.gz")),
        sugyot = read(File(dir, "sugyot.tsv.gz")),
        krupnik = read(File(dir, "krupnik.tsv.gz")),
    )

    /** [file] made anew from the rows given. */
    fun build(
        file: File,
        pack: Int = 1,
        dump: String = "",
        topics: Sequence<String> = emptySequence(),
        sages: Sequence<String> = emptySequence(),
        sugyot: Sequence<String> = emptySequence(),
        krupnik: Sequence<String> = emptySequence(),
    ) {
        file.delete()
        file.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement -> SefariaDb.SCHEMA.forEach(statement::execute) }
            val writer = Writer(connection)
            writer.meta(SefariaDb.META_FORMAT, SefariaDb.FORMAT.toString())
            writer.meta(SefariaDb.META_PACK, pack.toString())
            if (dump.isNotEmpty()) writer.meta("dump", dump)
            writer.topics(topics)
            writer.sages(sages)
            writer.sugyot(sugyot)
            writer.krupnik(krupnik)
            writer.close()
            connection.commit()
            connection.autoCommit = true
            connection.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun read(file: File): Sequence<String> = GZIPInputStream(file.inputStream()).bufferedReader().readLines().asSequence()

    private class Writer(
        private val connection: Connection,
    ) : AutoCloseable {
        private val lines = HashMap<String, Int>()
        private val insertLine = connection.prepareStatement("INSERT INTO line (id, heref) VALUES (?, ?)")

        fun meta(
            key: String,
            value: String,
        ) = insert("INSERT INTO meta (key, value) VALUES (?, ?)", listOf(listOf(key, value)))

        // The statements done with before the file is compacted: SQLite vacuums none in progress
        override fun close() = insertLine.close()

        // A line's id, the line written the first time it is met
        fun line(heRef: String): Int =
            lines.getOrPut(heRef) {
                val id = lines.size + 1
                insertLine.setInt(1, id)
                insertLine.setString(2, heRef)
                insertLine.executeUpdate()
                id
            }

        fun topics(rows: Sequence<String>) {
            val topics = ArrayList<List<Any>>()
            val roots = ArrayList<List<Any>>()
            val relations = ArrayList<List<Any>>()
            val topicLines = ArrayList<List<Any>>()
            val sources = HashMap<Int, Int>()
            for ((index, row) in rows.withIndex()) {
                val f = row.split('\t')
                when (f[0]) {
                    "T" -> topics += listOf(f[1].toInt(), f[2], f.getOrElse(3) { "" }, f.getOrElse(4) { "" })
                    "O" -> roots += listOf(f[1].toInt(), f[2].toInt())
                    "R" -> relations += listOf(f[1].toInt(), f[2], f[3].toInt(), index)
                    "A" -> {
                        val topic = f[1].toInt()
                        val source = if (f.getOrElse(4) { "" } == "1") 1 else 0
                        if (source == 1) sources.merge(topic, 1, Int::plus)
                        topicLines += listOf(line(f[2]), topic, source, index)
                    }
                }
            }
            insert(
                "INSERT INTO topic (id, name, others, description, sources) VALUES (?, ?, ?, ?, ?)",
                topics.map { it + (sources[it[0] as Int] ?: 0) },
            )
            insert("INSERT INTO topic_root (topic, ord) VALUES (?, ?)", roots)
            insert("INSERT INTO topic_relation (src, kind, dst, ord) VALUES (?, ?, ?, ?)", relations)
            insert("INSERT INTO topic_line (line, topic, source, ord) VALUES (?, ?, ?, ?)", topicLines)
        }

        fun sages(rows: Sequence<String>) {
            val sages = ArrayList<List<Any>>()
            val ambiguous = ArrayList<List<Any>>()
            val relations = ArrayList<List<Any>>()
            val mentions = ArrayList<List<Any>>()
            val places = HashMap<Int, MutableSet<Int>>()
            for ((index, row) in rows.withIndex()) {
                val f = row.split('\t')
                when (f[0]) {
                    "P" ->
                        sages +=
                            listOf(
                                f[1].toInt(),
                                f[2],
                                f.getOrElse(3) { "" },
                                f.getOrElse(4) { "" },
                                f.getOrElse(5) { "" },
                                f.getOrElse(6) { "" },
                            )
                    "W" -> ambiguous += listOf(f[1].toInt(), f[2], f.getOrElse(3) { "" })
                    "R" -> relations += listOf(f[1].toInt(), f[2], f[3].toInt(), index)
                    "M" -> {
                        val sage = f[2].toInt()
                        val line = line(f[1])
                        places.getOrPut(sage) { HashSet() } += line
                        mentions += listOf(line, sage, f.getOrElse(3) { "" }, index)
                    }
                }
            }
            insert(
                "INSERT INTO sage (id, name, others, description, generation, wiki, places) VALUES (?, ?, ?, ?, ?, ?, ?)",
                sages.map { it + (places[it[0] as Int]?.size ?: 0) },
            )
            insert("INSERT INTO ambiguous (id, name, options) VALUES (?, ?, ?)", ambiguous)
            insert("INSERT INTO sage_relation (src, kind, dst, ord) VALUES (?, ?, ?, ?)", relations)
            insert("INSERT INTO mention (line, sage, text, ord) VALUES (?, ?, ?, ?)", mentions)
        }

        fun sugyot(rows: Sequence<String>) {
            val sugyot = ArrayList<List<Any>>()
            val byLine = LinkedHashMap<Int, Int>()
            for (row in rows) {
                val f = row.split('\t')
                if (f[0] != "S" || f.size < 4) continue
                val id = sugyot.size + 1
                val kind = f[1].firstOrNull() ?: 'S'
                sugyot += listOf(id, kind.toString(), f[2], f[3])
                // A sugya over the Mishnah it shares lines with, as the reader's menu takes them
                f[3].split('|').forEach { heRef ->
                    val line = line(heRef)
                    if (kind == 'S' || line !in byLine) byLine[line] = id
                }
            }
            insert("INSERT INTO sugya (id, kind, source, lines) VALUES (?, ?, ?, ?)", sugyot)
            insert("INSERT INTO sugya_line (line, sugya) VALUES (?, ?)", byLine.map { (line, sugya) -> listOf(line, sugya) })
        }

        fun krupnik(rows: Sequence<String>) {
            val entries =
                rows
                    .map { it.split('\t') }
                    .filter { it[0] == "K" && it.size >= 4 }
                    .flatMap { f -> (listOf(f[1]) + f[2].split('|').filter { it.isNotEmpty() }).map { it to f[3] } }
                    .mapIndexed { index, (word, text) -> listOf(index, word, text) }
                    .toList()
            insert("INSERT INTO krupnik (ord, headword, text) VALUES (?, ?, ?)", entries)
        }

        private fun insert(
            sql: String,
            rows: List<List<Any>>,
        ) {
            connection.prepareStatement(sql).use { statement ->
                for (row in rows) {
                    row.forEachIndexed { index, value ->
                        when (value) {
                            is Int -> statement.setInt(index + 1, value)
                            else -> statement.setString(index + 1, value.toString())
                        }
                    }
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }
}
