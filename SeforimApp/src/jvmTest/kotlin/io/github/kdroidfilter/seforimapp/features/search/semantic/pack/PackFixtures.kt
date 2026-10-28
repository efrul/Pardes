package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import com.github.luben.zstd.ZstdOutputStream
import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelCleaner
import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelSpec
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.lucene.document.Document
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small packs as semantic-model/package_pack.py and semantic-release.yml make them, and a library to put them beside. */
internal object PackFixtures {
    const val MODEL = "student@test/pardes-clean-1"
    const val DIMENSION = 4
    const val LIBRARY = "20261012193827"
    const val LIBRARY_TAG = "v3-$LIBRARY"

    /** A library folder: its database and its release. */
    fun library(
        dir: File,
        release: String = LIBRARY,
    ): File =
        dir.apply {
            mkdirs()
            File(this, "seforim.db").writeText("db")
            File(this, "release_info.txt").writeText(release)
        }

    /** The pack's two folders in [dir]; [tag] tells packs apart (it is in the model's files). */
    fun folders(
        dir: File,
        tag: String = "new",
        model: String = MODEL,
        indexModel: String = model,
        library: String? = LIBRARY,
    ): File {
        val modelDir = File(dir, SemanticPack.MODEL_DIR).apply { mkdirs() }
        val onnx = File(modelDir, "model.int8.onnx").apply { writeText("onnx $tag") }
        val tokenizer = File(modelDir, "tokenizer.json").apply { writeText("{\"tokenizer\": \"$tag\"}") }
        File(modelDir, "NOTICE.md").writeText("notice")
        File(modelDir, OpenModelSpec.FILE_NAME).writeText(
            JSONObject()
                .put("format", 1)
                .put("id", model)
                .put("onnx", onnx.name)
                .put("tokenizer", tokenizer.name)
                .put("dimension", DIMENSION)
                .put("cleaner", OpenModelCleaner.ID)
                .put("onnxSha256", sha256(onnx.readBytes()))
                .put("tokenizerSha256", sha256(tokenizer.readBytes()))
                .toString(2),
        )
        val indexDir = File(dir, SemanticPack.INDEX_DIR)
        FSDirectory.open(indexDir.toPath()).use { directory ->
            IndexWriter(directory, IndexWriterConfig()).use { writer ->
                repeat(2) { writer.addDocument(Document().apply { add(StoredField("line_id", it.toLong())) }) }
                writer.setLiveCommitData(
                    buildMap {
                        put(VectorIndexBuilder.KEY_MODEL, indexModel)
                        put(VectorIndexBuilder.KEY_DIMENSION, DIMENSION.toString())
                        put(VectorIndexBuilder.KEY_LINES, "2")
                        library?.let { put(VectorIndexBuilder.KEY_DB_RELEASE, it) }
                    }.entries,
                )
                writer.commit()
            }
        }
        File(indexDir, "write.lock").delete()
        return dir
    }

    /** Every file of [dir] by its path in it, with its content. */
    fun contents(dir: File): Map<String, String> =
        dir
            .walk()
            .filter { it.isFile }
            .associate { it.relativeTo(dir).invariantSeparatorsPath to sha256(it.readBytes()) }

    /**
     * The bundle of the pack in [source] (its two folders) in [out], cut in [parts] files with a manifest, as
     * package_pack.py writes it; [extra] adds entries with these names.
     */
    fun bundle(
        source: File,
        out: File,
        parts: Int = 2,
        pack: Int = 2,
        library: String = LIBRARY_TAG,
        extra: List<String> = emptyList(),
    ): File {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(ZstdOutputStream(bytes)).use { tar ->
            val files =
                SemanticPack.FOLDERS.flatMap { name ->
                    File(source, name)
                        .walk()
                        .filter { it.isFile }
                        .sorted()
                        .toList()
                }
            for (file in files) {
                val entry = TarArchiveEntry(file, file.relativeTo(source).invariantSeparatorsPath)
                tar.putArchiveEntry(entry)
                file.inputStream().use { it.copyTo(tar) }
                tar.closeArchiveEntry()
            }
            for (name in extra) {
                val data = "extra".toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        val whole = bytes.toByteArray()
        out.mkdirs()
        // All parts of one size but a strictly smaller last one, as SeforimLibrary splits
        val size = whole.size / parts + 1
        val listed =
            if (parts == 1) {
                File(out, SemanticPack.BUNDLE_NAME).writeBytes(whole)
                listOf(Triple(SemanticPack.BUNDLE_NAME, whole.size.toLong(), sha256(whole)))
            } else {
                whole.toList().chunked(size).mapIndexed { i, chunk ->
                    val data = chunk.toByteArray()
                    val name = "%s.part%02d".format(SemanticPack.BUNDLE_NAME, i + 1)
                    File(out, name).writeBytes(data)
                    Triple(name, data.size.toLong(), sha256(data))
                }
            }
        File(out, SemanticPack.MANIFEST_NAME).writeText(
            JSONObject()
                .put("formatVersion", 1)
                .put("file", SemanticPack.BUNDLE_NAME)
                .put("size", whole.size.toLong())
                .put("sha256", sha256(whole))
                .put("parts", JSONArray(listed.map { (name, length, hash) -> part(name, length, hash) }))
                .put("pack", pack)
                .put("model", MODEL)
                .put("dimension", DIMENSION)
                .put("library", library)
                .put("lines", 2)
                .toString(2),
        )
        return out
    }

    private fun part(
        name: String,
        size: Long,
        sha256: String,
    ): JSONObject = JSONObject().put("file", name).put("size", size).put("sha256", sha256)

    /** One of a pack's two zips: [folder] of [source], as `zip -r` writes it, named [name] in [out]. */
    fun zip(
        source: File,
        folder: String,
        out: File,
        name: String,
    ): File =
        File(out.apply { mkdirs() }, name).also { zip ->
            ZipOutputStream(zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("$folder/"))
                output.closeEntry()
                File(source, folder).walk().filter { it.isFile }.sorted().forEach { file ->
                    output.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }

    fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
