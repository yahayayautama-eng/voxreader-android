package com.voxleaf.reader.data.repository

/**
 * Pulls readable paragraphs out of HTML that no XML parser will accept.
 *
 * MOBI markup is 1990s HTML — unclosed `<p>`, bare `&`, attributes without quotes — so [EpubParser]'s
 * pull parser is not an option here. This scans for block boundaries rather than building a tree,
 * which is all that's needed to turn a chapter into speakable paragraphs.
 */
object HtmlTextExtractor {

    data class Paragraph(val text: String, val isHeading: Boolean)

    private val TAG = Regex("""<[^>]*>""")
    private val SKIPPED_ELEMENT = Regex(
        """<(script|style|head)\b[^>]*>.*?</\1\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val BLOCK_BOUNDARY = Regex(
        """</?(?:p|div|br|tr|li|blockquote|h[1-6]|section|article|table)\b[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val HEADING_OPEN = Regex("""<h([1-6])\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val HEADING_CLOSE = Regex("""</h([1-6])\s*>""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    /** Longest first, so `&amp;` is not eaten by a shorter prefix. */
    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&quot;" to "\"", "&apos;" to "'",
        "&lt;" to "<", "&gt;" to ">", "&mdash;" to "—", "&ndash;" to "–",
        "&hellip;" to "…", "&lsquo;" to "'", "&rsquo;" to "'",
        "&ldquo;" to "“", "&rdquo;" to "”"
    )
    private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")

    fun extract(html: String): List<Paragraph> {
        val body = SKIPPED_ELEMENT.replace(html, " ")

        // Heading spans are recorded by position before the tags are stripped, because after
        // stripping there is no way to tell a chapter title from the sentence following it.
        val headingRanges = buildList {
            HEADING_OPEN.findAll(body).forEach { open ->
                val close = HEADING_CLOSE.find(body, open.range.last)
                add(open.range.last..(close?.range?.first ?: (open.range.last + 200)))
            }
        }

        val result = mutableListOf<Paragraph>()
        var cursor = 0
        fun flush(until: Int) {
            if (until <= cursor) return
            val slice = body.substring(cursor, until)
            val text = unescape(TAG.replace(slice, " ")).replace(WHITESPACE, " ").trim()
            if (text.isNotEmpty()) {
                val midpoint = (cursor + until) / 2
                result += Paragraph(text, headingRanges.any { midpoint in it })
            }
            cursor = until
        }

        BLOCK_BOUNDARY.findAll(body).forEach { match ->
            flush(match.range.first)
            cursor = match.range.last + 1
        }
        flush(body.length)
        return result
    }

    fun unescape(text: String): String {
        if (!text.contains('&')) return text
        var result = text
        ENTITIES.forEach { (entity, replacement) -> result = result.replace(entity, replacement) }
        return NUMERIC_ENTITY.replace(result) { match ->
            val radix = if (match.groupValues[1].isEmpty()) 10 else 16
            val code = match.groupValues[2].toIntOrNull(radix)
            // Anything outside the basic planes is likelier to be junk than a real character.
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
        }
    }
}
