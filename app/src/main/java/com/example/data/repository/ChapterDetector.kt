package com.example.data.repository

/**
 * Finds real chapter breaks in extracted document text.
 *
 * Importers used to emit one section per PDF page or per paragraph, which turned a book into
 * "Page 1 … Page 38" — accurate to the file, useless as a table of contents. This looks for the
 * headings a human would recognise and only claims a split when it finds several, so documents
 * with no structure fall back to the caller's chunking rather than getting invented chapters.
 */
object ChapterDetector {

    data class Section(val title: String, val content: String)

    private const val MAX_HEADING_LENGTH = 80
    private const val MIN_HEADINGS_TO_TRUST = 2
    private const val MIN_SECTION_CHARS = 40

    /** "Chapter 4", "PART II", "Section 3" — the explicit, unambiguous form. */
    private val LABELLED = Regex(
        """^\s*(chapter|chapitre|part|section|book|act|episode)\s+([0-9]+|[ivxlcdm]+)\b\s*[.:—–-]?\s*(.{0,60})$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * "1. Introduction", "2.3 Scope" — numbered outline headings. Capped at 3 digits per segment so a
     * stray 4-digit year at a line start (a common PDF-extraction artifact, e.g. a running header like
     * "1994 " bleeding into the text flow) doesn't get mistaken for a chapter number.
     */
    private val NUMBERED = Regex("""^\s*([0-9]{1,3}(?:\.[0-9]{1,3})*)\.?\s+(\p{L}.{0,60})$""")

    /** A short ALL-CAPS line standing alone, the most common styled heading in exported PDFs. */
    private val ALL_CAPS = Regex("""^\s*([\p{Lu}][\p{Lu}0-9 .,'&:()\-]{2,60})\s*$""")

    /**
     * True for an explicit chapter label like "Chapter 4" or "PART II", as opposed to any old styled
     * line. Books frequently mark illustration captions as headings too, so callers choosing a title
     * from several candidate headings need to tell the two apart.
     */
    fun isChapterLabel(line: String): Boolean = LABELLED.matches(line.trim())

    fun split(text: String): List<Section>? {
        val lines = text.lines()
        val headings = lines.indices.filter { isHeading(lines, it) }
        if (headings.size < MIN_HEADINGS_TO_TRUST) return null

        val sections = mutableListOf<Section>()

        // Anything before the first heading is real content (a preface, an abstract), so keep it.
        val preamble = lines.take(headings.first()).joinToString("\n").trim()
        if (preamble.length >= MIN_SECTION_CHARS) {
            sections += Section("Opening", preamble)
        }

        headings.forEachIndexed { index, lineIndex ->
            val end = headings.getOrNull(index + 1) ?: lines.size
            val body = lines.subList(lineIndex + 1, end).joinToString("\n").trim()
            if (body.isBlank()) return@forEachIndexed
            sections += Section(cleanTitle(lines[lineIndex]), body)
        }

        return sections.takeIf { it.size >= MIN_HEADINGS_TO_TRUST }
    }

    private fun isHeading(lines: List<String>, index: Int): Boolean {
        val line = lines[index].trim()
        if (line.isEmpty() || line.length > MAX_HEADING_LENGTH) return false
        if (line.none { it.isLetter() }) return false
        // Real headings don't end in sentence punctuation; that's a wrapped body line.
        if (line.endsWith(",") || line.endsWith(";")) return false

        if (LABELLED.matches(line) || NUMBERED.matches(line)) return true

        // An all-caps line only counts as a heading when it stands apart from the text around it.
        val isolated = lines.getOrNull(index - 1)?.isBlank() != false &&
            lines.getOrNull(index + 1)?.isBlank() != false
        return isolated && ALL_CAPS.matches(line)
    }

    private fun cleanTitle(raw: String): String {
        val line = raw.trim().trimEnd('.', ':', '—', '–', '-').trim()
        return if (line.length <= MAX_HEADING_LENGTH) line else line.take(MAX_HEADING_LENGTH).trim()
    }
}
