package io.github.kdroidfilter.seforimapp.features.search.semantic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop's LibraryLines.beside, for desktop's LevelPrecedenceTest: the database beside the text index, as the app names
 * them, read through a SqlDriver as on Android (LibraryStore's there, JDBC's here).
 */
internal fun LibraryLines.Companion.beside(indexDir: Path): LibraryLines? {
    val name = indexDir.fileName?.toString() ?: return null
    val db =
        when {
            name.endsWith(".lucene") -> indexDir.resolveSibling(name.removeSuffix(".lucene"))
            name.endsWith(".luceneindex") -> indexDir.resolveSibling(name.removeSuffix(".luceneindex"))
            else -> return null
        }
    if (!Files.isRegularFile(db)) return null
    val driver by lazy { JdbcSqliteDriver("jdbc:sqlite:$db") }
    return LibraryLines { driver }
}
