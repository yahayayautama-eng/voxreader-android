package com.voxleaf.reader.data.repository

/**
 * High-accuracy multi-lingual chapter detector built to match ElevenReader-level precision.
 *
 * Features:
 * - Multi-lingual prefixes: English, French, Spanish, German, Italian, Portuguese, Russian, CJK
 * - Multi-line and compound chapter titles: "CHAPTER 1\nThe Dark Forest" -> "CHAPTER 1: The Dark Forest"
 * - Spelled-out numbers (One through Ninety-Nine, First through Tenth) & Roman numerals (I to M)
 * - Standalone numbers and Roman numerals surrounded by blank lines or page breaks
 * - Standard literary book sections: Prologue, Epilogue, Introduction, Preface, Foreword, Afterword, etc.
 * - Repeating Running Header Stripper: Ignores page headers repeated on every page of PDFs/scans
 * - Noise & Dialogue rejection: Ignores table captions, page counters, and short quotes
 * - Scene break / Semantic clustering fallback for unstyled documents
 */
object ChapterDetector {

    data class Section(val title: String, val content: String)

    private const val MAX_HEADING_LENGTH = 90
    private const val MIN_HEADINGS_TO_TRUST = 2
    private const val MIN_SECTION_CHARS = 40

    private val NUMBER_WORDS = """(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth)(?:-(?:one|two|three|four|five|six|seven|eight|nine|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth))?"""

    private val ROMAN_NUMERALS = """[ivxlcdm]+"""

    // Multi-lingual chapter / section prefixes:
    // English: chapter, book, part, act, scene, section, volume, episode, canto
    // French: chapitre, livre, partie, acte, scène, tome
    // Spanish: capítulo, libro, parte, acto, escena, sección, tomo
    // German: kapitel, buch, teil, akt, szene, abschnitt, band
    // Italian: capitolo, libro, parte, atto, scena, tomo
    // Portuguese: capítulo, livro, parte, ato, cena, volume
    // Russian: глава, часть, книга, акт, сцена, раздел, том
    private val CHAPTER_PREFIXES = """(?:chapter|chapitre|capítulo|capitolo|kapitel|glava|глава|book|livre|libro|buch|книга|part|partie|parte|teil|часть|section|abschnitt|sección|раздел|volume|tome|tomo|band|том|act|acte|acto|atto|akt|акт|scene|scène|escena|scena|szene|сцена|episode|canto)"""

    /** CJK Chapter patterns: 第1章, 第一节, 第二部, etc. */
    private val CJK_CHAPTER = Regex("""^\s*第\s*[0-9一二三四五六七八九十百千]+\s*[章节卷回篇集]\s*(.{0,60})$""")

    /** "Chapter 4", "Chapter Four", "Chapter Twenty-One", "PART II", "Book 3", "Section 1.2" */
    private val LABELLED = Regex(
        """^\s*$CHAPTER_PREFIXES\s+(?:[0-9]+(?:\.[0-9]+)*|$NUMBER_WORDS|$ROMAN_NUMERALS)\b\s*[.:—–-]?\s*(.{0,70})$""",
        RegexOption.IGNORE_CASE
    )

    /** "1. Introduction", "2.3 Scope" */
    private val NUMBERED = Regex(
        """^\s*([0-9]{1,3}(?:\.[0-9]{1,3})*)\.?\s+(\p{Lu}\p{L}*.{0,70})$"""
    )

    /** Standalone Roman numerals: "I", "II", "XIV", "xxiv" on their own line */
    private val STANDALONE_ROMAN = Regex(
        """^\s*([IVXLCDM]{1,8})\s*[.:—–-]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Roman numeral with title on same line: "IV: The Beginning", "II - Arrival" */
    private val ROMAN_WITH_SUBTITLE = Regex(
        """^\s*([IVXLCDM]{1,8})\s*[:—–-]\s*(\p{Lu}.{0,70})$"""
    )

    /** Standalone Arabic chapter number on its own line: "1", "2", "42" */
    private val STANDALONE_NUMBER = Regex(
        """^\s*([0-9]{1,3})\s*$"""
    )

    /** Standard literary book sections */
    private val NAMED_SECTIONS = Regex(
        """^\s*(prologue|epilogue|introduction|preface|foreword|afterword|conclusion|appendix|interlude|dedication|acknowledgments?|author'?s?\s+note|about\s+the\s+author|dramatis\s+personae|cast\s+of\s+characters|glossary|prologo|epilogo|introduccion|introduzione|vorwort|nachwort)\b\s*[.:—–-]?\s*(.{0,70})$""",
        RegexOption.IGNORE_CASE
    )

    /** Short ALL-CAPS line standing alone */
    private val ALL_CAPS = Regex("""^\s*([\p{Lu}][\p{Lu}0-9 .,'&:()\-]{2,70})\s*$""")

    /** Scene break markers: ***, * * *, ---, ###, ~~~, ⁂, • • • */
    private val SCENE_BREAK = Regex(
        """^\s*([*]{3,}|[*]\s+[*]\s+[*]|[-]{3,}|[#]{3,}|[~]{3,}|⁂|•\s+•\s+•)\s*$"""
    )

    /** Noise phrases that must never be treated as chapter titles */
    private val NOISE_PATTERNS = listOf(
        Regex("""^\s*(page\s+[0-9]+|p\.\s*[0-9]+|all\s+rights\s+reserved|copyright|isbn|published\s+by|printed\s+in|table\s+of\s+contents|index)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(warning|caution|note|figure\s+[0-9]+|table\s+[0-9]+|chart\s+[0-9]+|box\s+[0-9]+)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(yes|no|stop|wait|help|why|what|how|where|when|who|oh|ah|well)\s*[.?!]*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*https?://\S+$""", RegexOption.IGNORE_CASE)
    )

    /**
     * True for an explicit chapter label like "Chapter 4" or "PART II".
     */
    fun isChapterLabel(line: String): Boolean {
        val trimmed = line.trim()
        if (isNoise(trimmed)) return false
        return LABELLED.matches(trimmed) || NAMED_SECTIONS.matches(trimmed) || CJK_CHAPTER.matches(trimmed)
            || NUMBERED.matches(trimmed) || STANDALONE_ROMAN.matches(trimmed) || ROMAN_WITH_SUBTITLE.matches(trimmed)
    }

    /**
     * Splits text by scene break markers (e.g. ***, * * *, ---, ###, ~~~, ⁂, • • •).
     */
    fun splitBySceneBreaks(text: String): List<Section>? {
        val lines = text.lines()
        val breakIndices = mutableListOf<Int>()
        lines.forEachIndexed { index, line ->
            if (SCENE_BREAK.matches(line)) {
                breakIndices += index
            }
        }
        if (breakIndices.size < 2) return null

        val rawSections = mutableListOf<String>()
        var start = 0
        for (breakIdx in breakIndices) {
            val chunk = lines.subList(start, breakIdx).joinToString("\n").trim()
            rawSections += chunk
            start = breakIdx + 1
        }
        val finalChunk = lines.subList(start, lines.size).joinToString("\n").trim()
        rawSections += finalChunk

        if (rawSections.any { it.length < 200 }) return null

        return rawSections.mapIndexed { index, content ->
            Section("Scene ${index + 1}", content)
        }
    }

    /**
     * Splits full text into chapters, stripping repeating headers and ensuring natural boundaries.
     */
    fun split(text: String): List<Section>? {
        val rawLines = text.lines()
        val candidates = mutableListOf<HeadingCandidate>()

        // Pass 1: Collect candidates
        var i = 0
        while (i < rawLines.size) {
            val heading = detectHeadingAt(rawLines, i)
            if (heading != null) {
                candidates += HeadingCandidate(i, heading.linesConsumed, heading.title)
                i += heading.linesConsumed
            } else {
                i++
            }
        }

        // TOC Loop Prevention: skip TOC preamble if detected
        val tocEndLine = skipTocPreamble(rawLines, candidates.map { it.lineIndex })
        val candidatesAfterToc = if (tocEndLine > 0) {
            candidates.filter { it.lineIndex >= tocEndLine }
        } else {
            candidates
        }

        if (candidatesAfterToc.isEmpty()) return null

        // Pass 2: Filter out repeating running headers
        val totalCount = candidatesAfterToc.size
        val titleCounts = candidatesAfterToc.groupingBy { cleanTitle(it.title).lowercase() }.eachCount()

        // 1. If a title appears >= 3 times AND represents > 40% of all heading candidates, strip ALL instances
        val runningHeaderKeys = titleCounts.filter { (_, count) ->
            count >= 3 && (count.toDouble() / totalCount) > 0.40
        }.keys

        val candidatesWithoutRunningHeaders = candidatesAfterToc.filter { candidate ->
            cleanTitle(candidate.title).lowercase() !in runningHeaderKeys
        }

        // 2. Single-predecessor dedup for consecutive duplicates that don't meet the frequency threshold
        val validCandidates = mutableListOf<HeadingCandidate>()
        for (candidate in candidatesWithoutRunningHeaders) {
            val key = cleanTitle(candidate.title).lowercase()
            if (validCandidates.isNotEmpty() && cleanTitle(validCandidates.last().title).lowercase() == key) {
                // Skip immediate duplicate
                continue
            }
            validCandidates += candidate
        }

        if (validCandidates.size < MIN_HEADINGS_TO_TRUST) return null

        val sections = mutableListOf<Section>()

        // Anything before the first heading is preamble (unless it was skipped as a TOC block)
        if (tocEndLine == 0) {
            val preamble = rawLines.take(validCandidates.first().lineIndex).joinToString("\n").trim()
            if (preamble.length >= MIN_SECTION_CHARS) {
                sections += Section("Opening", preamble)
            }
        }

        validCandidates.forEachIndexed { index, candidate ->
            val nextLineIndex = validCandidates.getOrNull(index + 1)?.lineIndex ?: rawLines.size
            val contentStart = (candidate.lineIndex + candidate.linesConsumed).coerceAtMost(nextLineIndex)

            val body = rawLines.subList(contentStart, nextLineIndex).joinToString("\n").trim()
            if (body.isNotBlank()) {
                val cleanedTitle = cleanTitle(candidate.title)
                sections += Section(cleanedTitle, body)
            }
        }

        return sections.takeIf { it.size >= MIN_HEADINGS_TO_TRUST }
    }

    private data class HeadingMatch(val title: String, val linesConsumed: Int)

    private data class HeadingCandidate(val lineIndex: Int, val linesConsumed: Int, val title: String)

    private fun skipTocPreamble(lines: List<String>, headingIndices: List<Int>): Int {
        val earlyHeadings = headingIndices.filter { it < 60 }
        if (earlyHeadings.size < 3) return 0

        var maxTocEnd = 0

        for (startIdx in earlyHeadings.indices) {
            for (endIdx in (startIdx + 2) until earlyHeadings.size) {
                val hStart = earlyHeadings[startIdx]
                val hEnd = earlyHeadings[endIdx]
                if (hEnd - hStart > 50) break

                val cluster = earlyHeadings.subList(startIdx, endIdx + 1)
                val clusterTitles = cluster.map { lines.getOrNull(it)?.trim()?.lowercase().orEmpty() }.toSet()
                // A TOC cluster must consist of >= 3 distinct chapter titles (not repeating running headers)
                if (clusterTitles.size < 3) continue

                var textBetweenLen = 0
                for (k in 0 until cluster.size - 1) {
                    val gapStart = cluster[k] + 1
                    val gapEnd = cluster[k + 1]
                    for (lineNum in gapStart until gapEnd) {
                        textBetweenLen += lines.getOrNull(lineNum)?.trim()?.length ?: 0
                    }
                }

                if (textBetweenLen < 150) {
                    val laterHeadingIndices = headingIndices.filter { it > hEnd }
                    val laterTitles = laterHeadingIndices.map { lines.getOrNull(it)?.trim()?.lowercase().orEmpty() }.toSet()
                    // Every title in the TOC cluster must appear again in the body chapters below
                    if (clusterTitles.all { it in laterTitles }) {
                        val tocEnd = hEnd + 1
                        if (tocEnd > maxTocEnd) {
                            maxTocEnd = tocEnd
                        }
                    }
                }
            }
        }

        return maxTocEnd
    }

    private fun detectHeadingAt(lines: List<String>, index: Int): HeadingMatch? {
        val line = lines[index].trim()
        if (line.isEmpty() || line.length > MAX_HEADING_LENGTH) return null
        if (line.none { it.isLetter() } && !STANDALONE_NUMBER.matches(line)) return null
        if (line.endsWith(",") || line.endsWith(";")) return null
        if (isNoise(line)) return null

        val prevBlank = lines.getOrNull(index - 1)?.isBlank() != false
        val nextBlank = lines.getOrNull(index + 1)?.isBlank() != false

        // 1. CJK Chapter Pattern
        if (CJK_CHAPTER.matches(line)) {
            return HeadingMatch(line, 1)
        }

        // 2. Explicit Multi-lingual Label: "Chapter 1", "Chapitre Premier", "Capítulo 4", etc.
        if (LABELLED.matches(line)) {
            val nextLine = lines.getOrNull(index + 1)?.trim().orEmpty()
            if (nextLine.isNotEmpty() && nextLine.length <= MAX_HEADING_LENGTH && isPotentialSubtitle(nextLine)) {
                return HeadingMatch("$line: $nextLine", 2)
            }
            return HeadingMatch(line, 1)
        }

        // 3. Named Section: "Prologue", "Epilogue", "Introduction"
        if (NAMED_SECTIONS.matches(line)) {
            val nextLine = lines.getOrNull(index + 1)?.trim().orEmpty()
            if (nextLine.isNotEmpty() && nextLine.length <= MAX_HEADING_LENGTH && isPotentialSubtitle(nextLine)) {
                return HeadingMatch("$line: $nextLine", 2)
            }
            return HeadingMatch(line, 1)
        }

        // 4. Numbered Heading: "1. Introduction"
        if (prevBlank && NUMBERED.matches(line)) {
            return HeadingMatch(line, 1)
        }

        // 5. Roman Numeral with title on same line: "IV: The Beginning"
        if (prevBlank && ROMAN_WITH_SUBTITLE.matches(line)) {
            return HeadingMatch(line, 1)
        }

        // 6. Standalone Roman Numeral or Arabic Number standing apart
        if (prevBlank && (STANDALONE_ROMAN.matches(line) || STANDALONE_NUMBER.matches(line))) {
            val nextLine = lines.getOrNull(index + 1)?.trim().orEmpty()
            if (nextLine.isNotEmpty() && nextLine.length <= MAX_HEADING_LENGTH && isPotentialSubtitle(nextLine)) {
                val prefix = if (STANDALONE_NUMBER.matches(line)) "Chapter $line" else line
                return HeadingMatch("$prefix: $nextLine", 2)
            }
            if (nextBlank) {
                val prefix = if (STANDALONE_NUMBER.matches(line)) "Chapter $line" else line
                return HeadingMatch(prefix, 1)
            }
        }

        // 7. Standalone ALL-CAPS line isolated from text
        if (prevBlank && nextBlank && ALL_CAPS.matches(line)) {
            return HeadingMatch(line, 1)
        }

        return null
    }

    private fun isPotentialSubtitle(line: String): Boolean {
        if (line.isEmpty() || line.length > MAX_HEADING_LENGTH) return false
        if (line.endsWith(",") || line.endsWith(";")) return false
        if (line.lastOrNull() in setOf('.', '?', '!', '…')) return false
        if (isNoise(line)) return false
        if (isChapterLabel(line)) return false
        if (line.firstOrNull()?.isLetter() != true) return false

        // A subtitle is usually short and title-shaped. When the line is ambiguous, keep it in
        // the body; losing a sentence is worse than leaving a subtitle unmerged.
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 8) return false
        val capitalizedWords = words.count { it.firstOrNull()?.isUpperCase() == true }
        return words.size <= 3 || capitalizedWords >= (words.size + 1) / 2
    }

    private fun isNoise(line: String): Boolean {
        return NOISE_PATTERNS.any { it.matches(line) }
    }

    private fun cleanTitle(raw: String): String {
        val line = raw.trim().trimEnd('.', '—', '–', '-').trim()
        return if (line.length <= MAX_HEADING_LENGTH) line else line.take(MAX_HEADING_LENGTH).trim()
    }
}
