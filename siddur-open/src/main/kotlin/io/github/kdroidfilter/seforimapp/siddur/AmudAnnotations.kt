package io.github.kdroidfilter.seforimapp.siddur

import java.security.MessageDigest
import java.util.HexFormat

private val nonHebrew = Regex("[^א-ת]")
private val markup = Regex("<[^>]+>")
private val hebrewEntity = Regex("&#(?:([0-9]+)|[xX]([a-fA-F0-9]+));")

/** Conditions from Amud, keyed by the normalized text of an unambiguous paragraph in the local edition. */
internal class AmudAnnotations(
    nusach: Nusach,
    lines: List<String>,
) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val rules = amudRules[nusach].orEmpty()
    private val inline = amudInline[nusach].orEmpty()
    private val matched =
        lines.mapNotNull { html -> fingerprint(html)?.takeIf(rules::containsKey)?.let { it to html } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, originals) -> originals.singleOrNull()?.let { it to rules.getValue(key) } }
            .toMap()
    private val matchedInline =
        lines.mapNotNull { html -> inline[hash(html)]?.let { html to it } }.toMap()

    fun condition(
        html: String,
        flags: Set<String>,
    ): Boolean? =
        matched[html]?.matches(flags)

    fun applyInline(html: String, flags: Set<String>, showAll: Boolean): String {
        val hidden = matchedInline[html].orEmpty().filterNot { it.condition.matches(flags) }
        if (hidden.isEmpty()) return html
        return buildString {
            var start = 0
            for (span in hidden) {
                append(html, start, span.start)
                if (showAll) {
                    append(SKIP_START)
                    append(html, span.start, span.end)
                    append(SKIP_END)
                }
                start = span.end
            }
            append(html, start, html.length)
        }
    }

    private fun fingerprint(html: String): String? {
        val text = hebrewEntity.replace(html.replace(markup, "")) { entity ->
            val decimal = entity.groupValues[1].toIntOrNull()
            val code = decimal ?: entity.groupValues[2].toIntOrNull(16)
            code?.takeIf { it in 0x05D0..0x05EA }?.toChar()?.toString().orEmpty()
        }.replace(nonHebrew, "")
        if (text.length < 24) return null
        return hash(text)
    }

    private fun hash(text: String): String = HexFormat.of().formatHex(digest.digest(text.toByteArray()))
}

private typealias AmudCondition = List<List<String>>

private fun String.condition(): AmudCondition = split('|').map { it.split('&') }

private fun AmudCondition.matches(flags: Set<String>): Boolean =
    any { terms -> terms.all { term -> if (term.startsWith('!')) term.drop(1) !in flags else term in flags } }

private data class InlineRule(val start: Int, val end: Int, val condition: AmudCondition)

private fun <T> loadAmud(name: String, parse: (String) -> T): Map<Nusach, Map<String, T>> {
    val stream = checkNotNull(AmudAnnotations::class.java.getResourceAsStream("/open-siddur/$name"))
    return stream.bufferedReader().useLines { rows ->
        rows.filter { it.isNotEmpty() }.map { row ->
            val (nusach, key, condition) = row.split('\t')
            Triple(Nusach.valueOf(nusach), key, parse(condition))
        }.groupBy { it.first }.mapValues { (_, rules) -> rules.associate { it.second to it.third } }
    }
}

private val amudRules by lazy { loadAmud("amud-annotations.tsv", String::condition) }

private val amudInline by lazy {
    loadAmud("amud-inline.tsv") { encoded ->
        encoded.split(';').map { range ->
            val (start, end, condition) = range.split(':')
            InlineRule(start.toInt(), end.toInt(), condition.condition())
        }
    }
}
