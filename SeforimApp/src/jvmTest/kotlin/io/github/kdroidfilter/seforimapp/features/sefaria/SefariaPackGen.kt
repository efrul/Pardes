package io.github.kdroidfilter.seforimapp.features.sefaria

import io.github.kdroidfilter.seforimapp.features.bookcontent.daf.DafDownloads
import io.github.kdroidfilter.seforimapp.features.sefaria.pack.SefariaManifest
import io.github.kdroidfilter.seforimapp.features.sefaria.pack.SefariaPack
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The pack's release files (.github/workflows/sefaria-release.yml), built from sefaria-data/ when SEFARIA_PACK_OUT names
 * where they go: the data's file gzipped ([SefariaPack.FILE_NAME]) and its manifest ([SefariaPack.MANIFEST_NAME]), of
 * the pack SEFARIA_PACK_NUMBER, on Sefaria's export of SEFARIA_PACK_DUMP. Nothing otherwise.
 */
class SefariaPackGen {
    @Test
    fun `the pack's release files`() {
        val out = System.getenv("SEFARIA_PACK_OUT") ?: return
        val number = System.getenv("SEFARIA_PACK_NUMBER")?.toIntOrNull() ?: error("SEFARIA_PACK_NUMBER")
        val dump = System.getenv("SEFARIA_PACK_DUMP").orEmpty()
        val dir = File(out).apply { mkdirs() }
        val db = File(dir, "${SefariaPack.DB_NAME}.build")
        SefariaPackBuilder.buildFrom(SefariaPackBuilder.repositoryData, db, number, dump)
        assertEquals(number, assertNotNull(SefariaDb.open(db)).use { it.pack })
        val gz = File(dir, SefariaPack.FILE_NAME)
        gz.outputStream().use { file -> best(file).use { zip -> db.inputStream().use { it.copyTo(zip) } } }
        val manifest =
            SefariaManifest(
                format = SefariaDb.FORMAT,
                pack = number,
                file = gz.name,
                size = gz.length(),
                sha256 = DafDownloads.sha256(gz),
                dbSize = db.length(),
                dbSha256 = DafDownloads.sha256(db),
                dump = dump,
            )
        File(dir, SefariaPack.MANIFEST_NAME).writeText(Json.encodeToString(SefariaManifest.serializer(), manifest))
        db.delete()
        println("Sefaria pack $number: ${gz.length()} bytes gzipped, ${manifest.dbSize} bytes of data")
    }

    // The data gzipped at its tightest: it is downloaded by every user
    private fun best(out: OutputStream): GZIPOutputStream =
        object : GZIPOutputStream(out) {
            init {
                def.setLevel(Deflater.BEST_COMPRESSION)
            }
        }
}
