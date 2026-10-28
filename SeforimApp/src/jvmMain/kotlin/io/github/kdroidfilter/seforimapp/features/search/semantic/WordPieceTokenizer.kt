package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.text.Normalizer

class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkToken: String = "[UNK]",
) {
    private val unkId: Long = vocab[unkToken]?.toLong() ?: 1L

    fun encode(
        text: String,
        maxTokens: Int = 512,
    ): LongArray {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val tokens = ArrayList<Long>()

        val words = normalized.split(WHITESPACE_REGEX)
        for (rawWord in words) {
            val word = rawWord.trim()
            if (word.isEmpty()) continue

            var start = 0
            val wlen = word.length
            val wordTokens = ArrayList<Long>()
            var isBad = false

            while (start < wlen) {
                var end = wlen
                var curId: Long? = null
                while (start < end) {
                    val sub = if (start > 0) "##" + word.substring(start, end) else word.substring(start, end)
                    val id = vocab[sub]
                    if (id != null) {
                        curId = id.toLong()
                        break
                    }
                    end--
                }
                if (curId == null) {
                    isBad = true
                    break
                }
                wordTokens.add(curId)
                start = end
            }

            if (isBad) {
                tokens.add(unkId)
            } else {
                tokens.addAll(wordTokens)
            }

            if (tokens.size >= maxTokens) break
        }

        if (tokens.isEmpty()) {
            return longArrayOf(0L)
        }

        val limit = minOf(tokens.size, maxTokens)
        val result = LongArray(limit)
        for (i in 0 until limit) {
            result[i] = tokens[i]
        }
        return result
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")

        fun fromFile(path: Path): WordPieceTokenizer {
            val content = File(path.toUri()).readText(StandardCharsets.UTF_8)
            val root = JSONObject(content)
            val model = root.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")
            val unkToken = model.optString("unk_token", "[UNK]")

            val vocab = HashMap<String, Int>(vocabObj.length())
            for (key in vocabObj.keySet()) {
                vocab[key] = vocabObj.getInt(key)
            }

            return WordPieceTokenizer(vocab, unkToken)
        }
    }
}
