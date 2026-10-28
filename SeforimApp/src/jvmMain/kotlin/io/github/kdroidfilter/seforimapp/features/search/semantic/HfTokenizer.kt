package io.github.kdroidfilter.seforimapp.features.search.semantic

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

/**
 * A HuggingFace `tokenizer.json` read and applied in Kotlin, no native library: the normalizers, pre-tokenizers and
 * post-processor of the open model (dicta-il/neodictabert-bilingual-embed), plus the plain ones next to them.
 * A component it does not know makes [fromFile] throw rather than tokenize differently from the model: a query
 * tokenized unlike the corpus finds the wrong lines without any error.
 *
 * `HfTokenizerParityTest` checks the ids against those of the `tokenizers` library on the corpus
 * (semantic-model/make_parity_fixtures.py).
 */
class HfTokenizer private constructor(
    private val normalizers: List<(String) -> String>,
    private val preTokenizer: (String) -> List<String>,
    private val vocab: Map<String, Int>,
    private val unkId: Int,
    private val subwordPrefix: String,
    private val maxInputCharsPerWord: Int,
    private val prefixIds: IntArray,
    private val suffixIds: IntArray,
    private val addedTokens: Map<String, Int>,
) {
    /** The id that pads a batch's shorter sequences (masked out): `[PAD]`'s, or 0. */
    val padId: Int get() = addedTokens["[PAD]"] ?: 0

    /** Token ids of [text], with the special tokens, truncated as the tokenizers library does to [maxTokens]. */
    fun encode(
        text: String,
        maxTokens: Int,
    ): LongArray {
        val room = maxTokens - prefixIds.size - suffixIds.size
        require(room > 0) { "maxTokens $maxTokens leaves no room for the text" }
        val ids = ArrayList<Int>()
        for ((piece, addedId) in splitOnAddedTokens(text)) {
            if (addedId != null) {
                ids.add(addedId)
            } else {
                var normalized = piece
                for (n in normalizers) normalized = n(normalized)
                for (word in preTokenizer(normalized)) {
                    wordPiece(word, ids)
                    if (ids.size >= room) break
                }
            }
            if (ids.size >= room) break
        }
        val n = minOf(ids.size, room)
        val out = LongArray(prefixIds.size + n + suffixIds.size)
        var i = 0
        for (id in prefixIds) out[i++] = id.toLong()
        for (k in 0 until n) out[i++] = ids[k].toLong()
        for (id in suffixIds) out[i++] = id.toLong()
        return out
    }

    /**
     * [text] cut around the added tokens it holds (`[CLS]`, `[MASK]`...), as the tokenizers library does before it
     * normalizes: at each place the longest token written there, kept whole with its id; null for the text between.
     */
    private fun splitOnAddedTokens(text: String): List<Pair<String, Int?>> {
        if (addedTokens.isEmpty()) return listOf(text to null)
        val out = ArrayList<Pair<String, Int?>>()
        var last = 0
        var i = 0
        while (i < text.length) {
            val token = addedTokens.keys.filter { text.startsWith(it, i) }.maxByOrNull { it.length }
            if (token == null) {
                i++
                continue
            }
            if (i > last) out.add(text.substring(last, i) to null)
            out.add(token to addedTokens.getValue(token))
            i += token.length
            last = i
        }
        if (last < text.length) out.add(text.substring(last) to null)
        return out
    }

    private fun wordPiece(
        word: String,
        into: MutableList<Int>,
    ) {
        if (word.isEmpty()) return
        // Char boundaries are code points, as in Rust
        val bounds = IntArray(word.codePointCount(0, word.length) + 1)
        var b = 0
        var offset = 0
        while (offset < word.length) {
            bounds[b++] = offset
            offset += Character.charCount(word.codePointAt(offset))
        }
        bounds[b] = word.length
        val chars = bounds.size - 1
        if (chars > maxInputCharsPerWord) {
            into.add(unkId)
            return
        }
        val pieces = ArrayList<Int>()
        var start = 0
        while (start < chars) {
            var end = chars
            var found = -1
            while (start < end) {
                val sub = word.substring(bounds[start], bounds[end])
                val id = vocab[if (start > 0) subwordPrefix + sub else sub]
                if (id != null) {
                    found = id
                    break
                }
                end--
            }
            if (found < 0) {
                into.add(unkId)
                return
            }
            pieces.add(found)
            start = end
        }
        into.addAll(pieces)
    }

    companion object {
        /** Reads a `tokenizer.json`, or a gzipped one (`.gz`). */
        fun fromFile(path: Path): HfTokenizer {
            val file = File(path.toUri())
            val text =
                if (file.name.endsWith(".gz")) {
                    GZIPInputStream(file.inputStream()).use { it.readBytes().toString(StandardCharsets.UTF_8) }
                } else {
                    file.readText(StandardCharsets.UTF_8)
                }
            return fromJson(JSONObject(text))
        }

        fun fromJson(root: JSONObject): HfTokenizer {
            val model = root.getJSONObject("model")
            require(model.getString("type") == "WordPiece") { "Unsupported tokenizer model: ${model.getString("type")}" }
            val vocabObj = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length() * 2)
            for (key in vocabObj.keySet()) vocab[key] = vocabObj.getInt(key)
            val unk = model.optString("unk_token", "[UNK]")
            val unkId = requireNotNull(vocab[unk]) { "No id for the unknown token $unk" }

            val (prefix, suffix) = postProcessor(root.opt("post_processor"))
            return HfTokenizer(
                normalizers = normalizers(root.opt("normalizer")),
                preTokenizer = preTokenizer(root.opt("pre_tokenizer")),
                vocab = vocab,
                unkId = unkId,
                subwordPrefix = model.optString("continuing_subword_prefix", "##"),
                maxInputCharsPerWord = model.optInt("max_input_chars_per_word", 100),
                prefixIds = prefix,
                suffixIds = suffix,
                addedTokens = addedTokens(root.optJSONArray("added_tokens")),
            )
        }

        /**
         * The added tokens, content -> id. Only those matched on the text as written are supported: a token matched
         * after normalization, or only as a whole word or with its spaces, makes [fromJson] throw.
         */
        private fun addedTokens(array: JSONArray?): Map<String, Int> {
            if (array == null) return emptyMap()
            return array.objects().associate { token ->
                val content = token.getString("content")
                val options = listOf("normalized", "single_word", "lstrip", "rstrip")
                require(options.none { token.optBoolean(it, false) } && content.isNotEmpty()) { "Unsupported added token: $token" }
                content to token.getInt("id")
            }
        }

        private fun normalizers(node: Any?): List<(String) -> String> {
            if (node == null || node == JSONObject.NULL) return emptyList()
            val obj = node as JSONObject
            return when (val type = obj.getString("type")) {
                "Sequence" -> obj.getJSONArray("normalizers").objects().flatMap { normalizers(it) }
                "NFC" -> listOf { s -> Normalizer.normalize(s, Normalizer.Form.NFC) }
                "NFD" -> listOf { s -> Normalizer.normalize(s, Normalizer.Form.NFD) }
                "NFKC" -> listOf { s -> Normalizer.normalize(s, Normalizer.Form.NFKC) }
                "NFKD" -> listOf { s -> Normalizer.normalize(s, Normalizer.Form.NFKD) }
                "Lowercase" -> listOf(::lowercase)
                "StripAccents" -> listOf(::stripMarks)
                "Replace" -> {
                    val content = obj.getString("content")
                    val pattern = obj.getJSONObject("pattern")
                    if (pattern.has("String")) {
                        val literal = pattern.getString("String")
                        listOf { s -> s.replace(literal, content) }
                    } else {
                        val regex = compile(pattern.getString("Regex"))
                        val replacement =
                            java.util.regex.Matcher
                                .quoteReplacement(content)
                        listOf { s -> regex.matcher(s).replaceAll(replacement) }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported normalizer: $type")
            }
        }

        private fun preTokenizer(node: Any?): (String) -> List<String> {
            if (node == null || node == JSONObject.NULL) return { s -> listOf(s) }
            val obj = node as JSONObject
            return when (val type = obj.getString("type")) {
                "Sequence" -> {
                    val steps = obj.getJSONArray("pretokenizers").objects().map { preTokenizer(it) }
                    return { s -> steps.fold(listOf(s)) { pieces, step -> pieces.flatMap(step) } }
                }
                "Whitespace" -> split(compile("\\w+|[^\\w\\s]+"), "Removed", invert = true)
                "WhitespaceSplit" -> split(compile("\\s+"), "Removed", invert = false)
                "Split" -> {
                    val pattern = obj.getJSONObject("pattern")
                    val regex =
                        if (pattern.has("String")) {
                            Pattern.compile(Pattern.quote(pattern.getString("String")))
                        } else {
                            compile(pattern.getString("Regex"))
                        }
                    split(regex, obj.getString("behavior"), obj.optBoolean("invert", false))
                }
                else -> throw IllegalArgumentException("Unsupported pre-tokenizer: $type")
            }
        }

        /**
         * The tokenizers library's Split: the matches are the delimiters, or with [invert] the pieces kept. Only the
         * behaviours where a delimiter is removed or stands alone are needed (and supported).
         */
        private fun split(
            regex: Pattern,
            behavior: String,
            invert: Boolean,
        ): (String) -> List<String> {
            require(behavior == "Removed" || behavior == "Isolated") { "Unsupported Split behavior: $behavior" }
            return { s ->
                val out = ArrayList<String>()
                val m = regex.matcher(s)
                var last = 0
                while (m.find()) {
                    if (m.start() == m.end()) continue
                    if (m.start() > last && (!invert || behavior == "Isolated")) out.add(s.substring(last, m.start()))
                    if (invert || behavior == "Isolated") out.add(s.substring(m.start(), m.end()))
                    last = m.end()
                }
                if (last < s.length && (!invert || behavior == "Isolated")) out.add(s.substring(last))
                out
            }
        }

        private fun postProcessor(node: Any?): Pair<IntArray, IntArray> {
            if (node == null || node == JSONObject.NULL) return IntArray(0) to IntArray(0)
            val obj = node as JSONObject
            require(obj.getString("type") == "TemplateProcessing") {
                "Unsupported post-processor: ${obj.getString("type")}"
            }
            val special = obj.getJSONObject("special_tokens")
            val prefix = ArrayList<Int>()
            val suffix = ArrayList<Int>()
            var seenSequence = false
            for (item in obj.getJSONArray("single").objects()) {
                when {
                    item.has("Sequence") -> seenSequence = true
                    item.has("SpecialToken") -> {
                        val id = item.getJSONObject("SpecialToken").getString("id")
                        val ids = special.getJSONObject(id).getJSONArray("ids")
                        val target = if (seenSequence) suffix else prefix
                        for (k in 0 until ids.length()) target.add(ids.getInt(k))
                    }
                }
            }
            return prefix.toIntArray() to suffix.toIntArray()
        }

        // Oniguruma (the tokenizers library's regex engine) matches \d and \s on Unicode, as Java does with
        // UNICODE_CHARACTER_CLASS; its \w is Letter|Mark|Decimal_Number|Connector_Punctuation, where Java's also takes
        // the joiners (U+200C, U+200D: "וי" + ZWJ + "ראו" would be one unknown word instead of three pieces)
        private fun compile(regex: String): Pattern = Pattern.compile(onigWordClass(regex), Pattern.UNICODE_CHARACTER_CLASS)

        private const val ONIG_WORD = "\\p{L}\\p{M}\\p{Nd}\\p{Pc}"

        private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)

        /** [regex] with each `\w` spelled as Oniguruma's: bare inside a character class, bracketed outside. */
        internal fun onigWordClass(regex: String): String {
            val sb = StringBuilder(regex.length)
            var inClass = 0
            var i = 0
            while (i < regex.length) {
                val c = regex[i]
                when {
                    c == '\\' && i + 1 < regex.length -> {
                        val next = regex[i + 1]
                        if (next == 'w') sb.append(if (inClass > 0) ONIG_WORD else "[$ONIG_WORD]") else sb.append(c).append(next)
                        i += 2
                        continue
                    }
                    c == '[' -> inClass++
                    c == ']' && inClass > 0 -> inClass--
                }
                sb.append(c)
                i++
            }
            return sb.toString()
        }

        /** Rust's char::to_lowercase, one code point at a time: no context rules (Java's final sigma). */
        private fun lowercase(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val str = String(Character.toChars(cp))
                sb.append(if (Character.isUpperCase(cp) || Character.isTitleCase(cp)) str.lowercase(Locale.ROOT) else str)
                i += Character.charCount(cp)
            }
            return sb.toString()
        }

        /**
         * The tokenizers library's StripAccents: drops every mark (General_Category M: niqqud and teamim, but also the
         * spacing and enclosing ones, Mc and Me, that it drops too).
         */
        private fun stripMarks(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                if (Character.getType(cp).toByte() !in MARKS) sb.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            return sb.toString()
        }

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    }
}
