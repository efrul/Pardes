package io.github.kdroidfilter.seforimapp.features.search.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Embeds a query with an ONNX model into the int8 vector the index is searched with. [encode] turns the query into
 * token ids (cleaning, prefix and tokenizer of the model); [outputName] is the model's sentence-embedding output,
 * [batch, dimension], or the first output when null. [encodePassage] does the same for a passage of a found line, as the
 * model embeds documents (for the passage closest in meaning, [embedPassages]); [padId] pads a batch.
 */
class OnnxEmbeddingProvider(
    modelPath: Path,
    private val encode: (String) -> LongArray,
    override val dimension: Int,
    private val outputName: String? = null,
    private val encodePassage: ((String) -> LongArray)? = null,
    private val padId: Long = 0,
) : EmbeddingProvider {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val sessionOptions =
            OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
        session = env.createSession(modelPath.toString(), sessionOptions)
    }

    override fun embed(text: String): ByteArray = quantizeInt8(embedFloat(text))

    /** The model's (normalised) float vector, before the int8 quantization of [embed]. */
    fun embedFloat(text: String): FloatArray = run(listOf(encode(text))).single()

    override fun embedQuery(text: String): FloatArray = embedFloat(text)

    override fun embedPassages(texts: List<String>): List<FloatArray>? {
        val encodeOne = encodePassage ?: return null
        val tokens = texts.map(encodeOne)
        val out = arrayOfNulls<FloatArray>(texts.size)
        // Similar lengths together, at most PASSAGE_BATCH_TOKENS padded tokens a run
        val order = tokens.indices.sortedBy { tokens[it].size }
        var i = 0
        while (i < order.size) {
            var j = i + 1
            while (j < order.size && (j - i + 1) * tokens[order[j]].size <= PASSAGE_BATCH_TOKENS) j++
            val batch = order.subList(i, j)
            run(batch.map { tokens[it] }).forEachIndexed { k, vector -> out[batch[k]] = vector }
            i = j
        }
        return out.map { checkNotNull(it) }
    }

    // [sequences] in one run of the model, the shorter ones padded and masked out
    private fun run(sequences: List<LongArray>): List<FloatArray> {
        val length = sequences.maxOf { it.size }
        val ids = Array(sequences.size) { b -> LongArray(length) { p -> sequences[b].getOrElse(p) { padId } } }
        val mask = Array(sequences.size) { b -> LongArray(length) { p -> if (p < sequences[b].size) 1L else 0L } }
        val tensorIds = OnnxTensor.createTensor(env, ids)
        val tensorMask = OnnxTensor.createTensor(env, mask)
        try {
            session.run(mapOf("input_ids" to tensorIds, "attention_mask" to tensorMask)).use { result ->
                val value = outputName?.let { result.get(it).orElseThrow() } ?: result[0]

                @Suppress("UNCHECKED_CAST")
                val output = (value.value as Array<FloatArray>).toList()
                check(output.first().size == dimension) { "The model returns ${output.first().size} dimensions, expected $dimension" }
                return output
            }
        } finally {
            tensorIds.close()
            tensorMask.close()
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        /** The legacy model (384 dimensions, [HebrewNormalizer] + [WordPieceTokenizer]), as the app had it. */
        fun legacy(
            modelPath: Path,
            tokenizerPath: Path,
        ): OnnxEmbeddingProvider {
            val tokenizer = WordPieceTokenizer.fromFile(tokenizerPath)
            return OnnxEmbeddingProvider(modelPath, { tokenizer.encode(HebrewNormalizer.normalize(it)) }, 384)
        }

        /** A passage's tokens at most: a sentence or a clause ([Passages]), never a whole long line. */
        private const val PASSAGE_MAX_TOKENS = 128

        /** Padded tokens a run of [embedPassages] holds at most. */
        private const val PASSAGE_BATCH_TOKENS = 4096

        /**
         * The open model of [spec]: [OpenModelCleaner], the query prefix, then its tokenizer.json; passages with the
         * document prefix, as the corpus was embedded.
         */
        fun open(spec: OpenModelSpec): OnnxEmbeddingProvider {
            val tokenizer = HfTokenizer.fromFile(spec.tokenizer)
            return OnnxEmbeddingProvider(
                spec.onnx,
                { tokenizer.encode(spec.queryPrefix + OpenModelCleaner.clean(it), spec.queryMaxTokens) },
                spec.dimension,
                spec.output,
                { tokenizer.encode(spec.documentPrefix + OpenModelCleaner.clean(it), PASSAGE_MAX_TOKENS) },
                tokenizer.padId.toLong(),
            )
        }

        /**
         * Scales the vector so its largest component maps to 127, as the index's vectors were
         * (SeforimLibrary's LuceneTextIndexWriter, [VectorIndexBuilder]): COSINE ignores the scale.
         */
        fun quantizeInt8(floats: FloatArray): ByteArray {
            var maxVal = 0.0f
            for (v in floats) {
                val a = abs(v)
                if (a > maxVal) {
                    maxVal = a
                }
            }
            val out = ByteArray(floats.size)
            if (maxVal == 0.0f) {
                return out
            }
            for (i in floats.indices) {
                val q = (floats[i] * 127f / maxVal).roundToInt()
                out[i] = q.coerceIn(-128, 127).toByte()
            }
            return out
        }
    }
}
