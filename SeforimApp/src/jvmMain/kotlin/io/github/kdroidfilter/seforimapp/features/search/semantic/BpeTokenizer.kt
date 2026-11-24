package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.PriorityQueue
import java.util.zip.GZIPInputStream

/**
 * The reranker's `tokenizer.json` read and applied in Kotlin, no native library: a byte-pair-encoding model after a
 * Metaspace pre-tokenizer, as the tokenizers library applies them, and the template of a pair,
 * `<bos> query <eos> line <eos>` (pardes-semantic/reranker/build_reranker.py writes the file: the source model's tokenizer with
 * the tokens the library can use). A component it does not know makes [fromFile] throw rather than tokenize unlike
 * the model.
 *
 * The texts it is given are cleaned of their tags ([RerankText]), so no special token is ever written in one: the
 * file's added tokens are not looked for in a text. `BpeTokenizerParityTest` checks the ids against those of the
 * tokenizers library (pardes-semantic/reranker/make_fixtures.py).
 */
class BpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    // (left id, right id) of a merge -> its rank and the id of the token it makes
    private val merges: Map<Long, Long>,
    private val unkId: Int,
    private val fuseUnk: Boolean,
    // The token of each byte value (-1 where it has none), for a character the vocabulary does not hold; null when the
    // file asks for no such fallback
    private val byteIds: IntArray?,
    private val bosId: Int,
    private val eosId: Int,
) {
    /** The id that pads a batch's shorter sequences (masked out). */
    val padId: Int = vocab["<pad>"] ?: 0

    /**
     * `<bos> query <eos> line <eos>`, [maxTokens] at most: the line gives way first, as the tokenizers library
     * truncates the second of a pair, and the query is cut to [queryMaxTokens] before that.
     */
    fun encodePair(
        query: String,
        line: String,
        maxTokens: Int,
        queryMaxTokens: Int,
    ): LongArray {
        val first = encode(query).let { if (it.size > queryMaxTokens) it.copyOf(queryMaxTokens) else it }
        val room = maxTokens - first.size - SPECIAL_TOKENS
        require(room > 0) { "maxTokens $maxTokens leaves no room for the line" }
        val second = encode(line).let { if (it.size > room) it.copyOf(room) else it }
        val out = LongArray(first.size + second.size + SPECIAL_TOKENS)
        var i = 0
        out[i++] = bosId.toLong()
        for (id in first) out[i++] = id.toLong()
        out[i++] = eosId.toLong()
        for (id in second) out[i++] = id.toLong()
        out[i] = eosId.toLong()
        return out
    }

    /** The ids of [text]'s tokens, no special token around them. */
    fun encode(text: String): IntArray {
        val ids = ArrayList<Int>()
        for (word in words(text)) ids.addAll(merged(symbols(word)))
        return ids.toIntArray()
    }

    /**
     * [text] as the Metaspace pre-tokenizer cuts it: every space a `▁`, one before the text when it starts with none,
     * and a piece from each `▁` to the next.
     */
    private fun words(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val marked = text.replace(' ', META).let { if (it.startsWith(META)) it else META + it }
        val out = ArrayList<String>()
        var start = 0
        for (i in 1 until marked.length) {
            if (marked[i] == META) {
                out.add(marked.substring(start, i))
                start = i
            }
        }
        out.add(marked.substring(start))
        return out
    }

    /**
     * The id of each character of [word], before any merge: a character the vocabulary does not hold is the tokens of
     * its bytes, or the unknown token (one for a run of them, when the file says so) where a byte has none.
     */
    private fun symbols(word: String): IntArray {
        val out = ArrayList<Int>(word.length)
        var unknown = false
        var i = 0
        while (i < word.length) {
            val codePoint = word.codePointAt(i)
            val char = String(Character.toChars(codePoint))
            i += Character.charCount(codePoint)
            val id = vocab[char]
            val bytes = if (id == null) byteTokens(char) else null
            when {
                id != null -> out.add(id)
                bytes != null -> out.addAll(bytes)
                !unknown || !fuseUnk -> out.add(unkId)
            }
            unknown = id == null && bytes == null
        }
        return out.toIntArray()
    }

    private fun byteTokens(char: String): List<Int>? {
        val ids = byteIds ?: return null
        return char.toByteArray(StandardCharsets.UTF_8).map { ids[it.toInt() and BYTE_MASK] }.takeIf { tokens -> tokens.none { it < 0 } }
    }

    /**
     * [symbols] after the merges, as the tokenizers library applies them: the pair of the lowest rank first, the
     * leftmost of two of one rank, and a pair formed by a merge joins the others waiting.
     */
    private fun merged(symbols: IntArray): List<Int> {
        if (symbols.size < 2) return symbols.toList()
        val word = Word(symbols)
        val queue = PriorityQueue<Merge>(compareBy<Merge> { it.rank }.thenBy { it.position })
        for (i in 0 until symbols.size - 1) offer(queue, word, i)
        while (true) {
            val top = queue.poll() ?: break
            val right = word.next[top.position]
            // A pair waiting since before one of its two tokens was merged away or into another
            if (!word.alive[top.position] || right < 0 || newIdOf(word.ids[top.position], word.ids[right]) != top.newId) continue
            word.merge(top.position, right, top.newId)
            val previous = word.previous[top.position]
            if (previous >= 0) offer(queue, word, previous)
            if (word.next[top.position] >= 0) offer(queue, word, top.position)
        }
        return symbols.indices.filter { word.alive[it] }.map { word.ids[it] }
    }

    // The pair that starts at [position] joins [queue], when a merge makes a token of it
    private fun offer(
        queue: PriorityQueue<Merge>,
        word: Word,
        position: Int,
    ) {
        val merge = merges[pairKey(word.ids[position], word.ids[word.next[position]])] ?: return
        queue.add(Merge(position, (merge ushr Int.SIZE_BITS).toInt(), merge.toInt()))
    }

    // The token a merge makes of the pair, or -1 when none does
    private fun newIdOf(
        left: Int,
        right: Int,
    ): Int = merges[pairKey(left, right)]?.toInt() ?: -1

    private class Merge(
        val position: Int,
        val rank: Int,
        val newId: Int,
    )

    /** A word's tokens as a linked list over its first positions: a merge keeps the left one and drops the right. */
    private class Word(
        val ids: IntArray,
    ) {
        val alive = BooleanArray(ids.size) { true }
        val previous = IntArray(ids.size) { it - 1 }
        val next = IntArray(ids.size) { if (it + 1 < ids.size) it + 1 else -1 }

        fun merge(
            left: Int,
            right: Int,
            newId: Int,
        ) {
            ids[left] = newId
            alive[right] = false
            next[left] = next[right]
            if (next[right] >= 0) previous[next[right]] = left
        }
    }

    companion object {
        private const val META = '▁'
        private const val SPECIAL_TOKENS = 3
        private const val BYTE_VALUES = 256
        private const val BYTE_MASK = 0xFF

        private fun pairKey(
            left: Int,
            right: Int,
        ): Long = (left.toLong() shl Int.SIZE_BITS) or right.toLong()

        /** Reads a `tokenizer.json`, or a gzipped one (`.gz`). */
        fun fromFile(path: Path): BpeTokenizer {
            val file = File(path.toUri())
            val text =
                if (file.name.endsWith(".gz")) {
                    GZIPInputStream(file.inputStream()).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                } else {
                    file.readText(StandardCharsets.UTF_8)
                }
            return fromJson(JSONObject(text))
        }

        fun fromJson(root: JSONObject): BpeTokenizer {
            val model = root.getJSONObject("model")
            requireKnown(root, model)
            val vocabObj = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length() * 2)
            for (key in vocabObj.keySet()) vocab[key] = vocabObj.getInt(key)
            val unk = model.optString("unk_token", "<unk>")
            return BpeTokenizer(
                vocab = vocab,
                merges = merges(model, vocab),
                unkId = requireNotNull(vocab[unk]) { "No id for the unknown token $unk" },
                fuseUnk = model.optBoolean("fuse_unk", false),
                byteIds = if (model.optBoolean("byte_fallback", false)) byteIds(vocab) else null,
                bosId = requireNotNull(vocab["<bos>"]) { "No id for <bos>" },
                eosId = requireNotNull(vocab["<eos>"]) { "No id for <eos>" },
            )
        }

        // What this class applies, and nothing else: another model, normalizer, pre-tokenizer or template would
        // tokenize unlike the model without any error
        private fun requireKnown(
            root: JSONObject,
            model: JSONObject,
        ) {
            require(model.getString("type") == "BPE") { "Unsupported tokenizer model: ${model.getString("type")}" }
            require(model.isNull("continuing_subword_prefix") && model.isNull("end_of_word_suffix") && model.isNull("dropout")) {
                "Unsupported byte-pair-encoding options"
            }
            require(!model.optBoolean("ignore_merges", false)) { "Unsupported byte-pair-encoding option: ignore_merges" }
            val normalizer = root.getJSONObject("normalizer")
            val spaceToMeta =
                normalizer.getString("type") == "Replace" &&
                    normalizer.getJSONObject("pattern").optString("String") == " " &&
                    normalizer.getString("content") == META.toString()
            require(spaceToMeta) { "Unsupported normalizer: $normalizer" }
            val pre = root.getJSONObject("pre_tokenizer")
            val metaspace =
                pre.getString("type") == "Metaspace" &&
                    pre.getString("replacement") == META.toString() &&
                    pre.optString("prepend_scheme") == "always" &&
                    pre.optBoolean("split", true)
            require(metaspace) { "Unsupported pre-tokenizer: $pre" }
            val pair = root.getJSONObject("post_processor").getJSONArray("pair")
            val template =
                (0 until pair.length()).map { i ->
                    val part = pair.getJSONObject(i)
                    (part.optJSONObject("SpecialToken") ?: part.getJSONObject("Sequence")).getString("id")
                }
            require(template == listOf("<bos>", "A", "<eos>", "B", "<eos>")) { "Unsupported template of a pair: $template" }
        }

        private fun merges(
            model: JSONObject,
            vocab: Map<String, Int>,
        ): Map<Long, Long> {
            val list = model.getJSONArray("merges")
            val out = HashMap<Long, Long>(list.length() * 2)
            for (rank in 0 until list.length()) {
                val pair = list.getJSONArray(rank)
                val left = pair.getString(0)
                val right = pair.getString(1)
                val ids = listOf(left, right, left + right).map { token -> idOf(vocab, token) }
                out[pairKey(ids[0], ids[1])] = (rank.toLong() shl Int.SIZE_BITS) or ids[2].toLong()
            }
            return out
        }

        private fun idOf(
            vocab: Map<String, Int>,
            token: String,
        ): Int = requireNotNull(vocab[token]) { "A merge of a token out of the vocabulary: $token" }

        // The tokens `<0x00>` to `<0xFF>`, -1 for a byte the vocabulary has no token for
        private fun byteIds(vocab: Map<String, Int>): IntArray = IntArray(BYTE_VALUES) { vocab["<0x%02X>".format(it)] ?: -1 }
    }
}
