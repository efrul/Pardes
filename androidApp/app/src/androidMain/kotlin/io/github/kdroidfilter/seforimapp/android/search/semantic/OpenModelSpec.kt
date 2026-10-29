// Android variant of Pardes desktop's SeforimApp/src/jvmMain/.../features/search/semantic/OpenModelSpec.kt, same
// package and class. One change, marked "Android:": the manifest is read with Files.readAllBytes, since
// Files.readString (Java 11) is not in Android 8's java.nio.
package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The open model as semantic-model/export_onnx.py describes it in `pardes-model.json`, next to the ONNX file.
 *
 * [id] names the weights and the cleaner, the two things a query must share with the corpus: the vector index
 * records the id it was embedded with ([VectorIndexBuilder]) and is only searched with a model of the same id.
 * The quantization of the query model is not part of it (the corpus is embedded by the unquantized model).
 */
data class OpenModelSpec(
    val dir: Path,
    val id: String,
    val onnx: Path,
    val tokenizer: Path,
    val dimension: Int,
    val output: String,
    val queryPrefix: String,
    val queryMaxTokens: Int,
    val cleaner: String,
    val attribution: String,
    /** The dense side's weight in the hybrid's RRF: measured per model (semantic-model/BENCHMARK.md), 1 when unset. */
    val hybridDenseWeight: Double = 1.0,
    /** What precedes a document's text when the model embeds it (the corpus, a found line's passages). */
    val documentPrefix: String = "",
) {
    companion object {
        const val FILE_NAME = "pardes-model.json"
        private const val FORMAT = 1

        /** The spec in [dir], or null when [dir] has none; throws when the manifest is unusable. */
        fun read(dir: Path): OpenModelSpec? {
            val file = dir.resolve(FILE_NAME)
            if (!Files.isRegularFile(file)) return null
            // Android: readAllBytes, not readString (API 33 and later only)
            val json = JSONObject(String(Files.readAllBytes(file), StandardCharsets.UTF_8))
            val format = json.optInt("format", -1)
            require(format == FORMAT) { "$file: format $format, this app reads $FORMAT" }
            val cleaner = json.getString("cleaner")
            require(cleaner == OpenModelCleaner.ID) { "$file: cleaner $cleaner, this app has ${OpenModelCleaner.ID}" }
            val spec =
                OpenModelSpec(
                    dir = dir,
                    id = json.getString("id"),
                    onnx = dir.resolve(json.getString("onnx")),
                    tokenizer = dir.resolve(json.getString("tokenizer")),
                    dimension = json.getInt("dimension"),
                    output = json.optString("output", "sentence_embedding"),
                    queryPrefix = json.optString("queryPrefix", ""),
                    queryMaxTokens = json.optInt("queryMaxTokens", 128),
                    cleaner = cleaner,
                    attribution = json.optString("attribution", ""),
                    hybridDenseWeight = json.optDouble("hybridDenseWeight", 1.0),
                    documentPrefix = json.optString("documentPrefix", ""),
                )
            require(Files.isRegularFile(spec.onnx)) { "$file: missing ${spec.onnx.fileName}" }
            require(Files.isRegularFile(spec.tokenizer)) { "$file: missing ${spec.tokenizer.fileName}" }
            return spec
        }
    }
}
