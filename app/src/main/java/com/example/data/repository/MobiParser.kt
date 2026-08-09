package com.example.data.repository

import java.io.File

/**
 * Reader for Kindle's MOBI / AZW / AZW3 (KF8) container.
 *
 * Ported from the format handling in foliate-js `mobi.js` (MIT, © John Factotum) — read as a
 * specification and rewritten in Kotlin; no code was copied. There is no maintained Android library
 * for these formats, and the alternative (rendering in a WebView) can't give us plain text to speak.
 *
 * A MOBI file is a PalmDB: a header, a record-offset table, then opaque records. Record 0 carries the
 * PalmDOC and MOBI headers; the next `numTextRecords` records hold the book text, compressed with
 * either PalmDOC LZ77 or a Huffman scheme, and the rest are images and indexes.
 *
 * ponytail: the INDX/TAGX/CNCX index parser is not ported — that is the bulk of the original and its
 * only payoff here is author-written chapter labels. Sections are recovered from the markup instead
 * (see [splitSections]). Port `getIndexData` if NCX-quality titles ever matter.
 */
object MobiParser {

    class ParseException(message: String) : Exception(message)

    data class MobiChapter(val title: String?, val paragraphs: List<HtmlTextExtractor.Paragraph>)

    data class MobiResult(
        val title: String?,
        val author: String?,
        val chapters: List<MobiChapter>,
        val coverBytes: ByteArray?
    )

    private const val MAX_RECORDS = 65_536
    private const val MAX_DECOMPRESSED_TEXT_BYTES = 50 * 1024 * 1024

    fun parse(file: File): MobiResult {
        val bytes = try {
            file.readBytes()
        } catch (_: OutOfMemoryError) {
            throw ParseException("This Kindle book is too large to open on this device.")
        }
        if (bytes.size < 78) throw ParseException("Not a valid Kindle book file.")

        val palm = readPalmDb(bytes)
        val record0 = palm.record(0)
        val headers = readHeaders(record0)

        if (headers.palmdocEncryption != 0) {
            throw ParseException("This Kindle book is DRM-protected and cannot be opened.")
        }

        // A "combo" file holds an old MOBI6 book and a KF8 one back to back; the KF8 half is better
        // structured, so prefer it when the boundary record says it exists.
        val boundary = headers.exth[EXTH_BOUNDARY]?.toLongOrNull()
        val useKf8From = if (headers.version < 8 && boundary != null && boundary < 0xFFFFFFFFL) {
            boundary.toInt().takeIf { it in 1 until palm.recordCount }
        } else {
            null
        }
        val start = useKf8From ?: 0
        val active = if (useKf8From != null) readHeaders(palm.record(useKf8From)) else headers

        val charset = when (active.encoding) {
            65001 -> Charsets.UTF_8
            else -> charsetOrLatin1("windows-1252")
        }
        val decompress = decompressorFor(active, palm, start)
        // Every text record decompresses independently, but the markup runs across their seams, so
        // the book is reassembled into one byte stream before anything is parsed out of it.
        val assembled = ByteBuf(maxSize = MAX_DECOMPRESSED_TEXT_BYTES)
        for (index in 1..active.numTextRecords) {
            val recordIndex = start + index
            if (recordIndex >= palm.recordCount) break
            val raw = removeTrailingEntries(palm.record(recordIndex), active.trailingFlags)
            assembled.append(decompress(raw))
        }
        val text = String(assembled.toByteArray(), charset)

        val chapters = splitSections(text)
        if (chapters.isEmpty()) throw ParseException("No readable text found in this Kindle book.")

        return MobiResult(
            title = active.exth[EXTH_TITLE] ?: active.internalTitle,
            author = active.exth[EXTH_CREATOR],
            chapters = chapters,
            coverBytes = readCover(palm, active, start)
        )
    }

    // ---- PalmDB container -------------------------------------------------------------------

    private class PalmDb(private val bytes: ByteArray, private val offsets: IntArray) {
        val recordCount: Int get() = offsets.size

        fun record(index: Int): ByteArray {
            if (index !in offsets.indices) throw ParseException("Kindle book is missing a record.")
            val from = offsets[index].coerceIn(0, bytes.size)
            val to = offsets.getOrNull(index + 1)?.coerceIn(from, bytes.size) ?: bytes.size
            return bytes.copyOfRange(from, to)
        }
    }

    private fun readPalmDb(bytes: ByteArray): PalmDb {
        val type = string(bytes, 60, 4)
        val creator = string(bytes, 64, 4)
        if (type + creator != "BOOKMOBI") throw ParseException("Not a Kindle book file.")

        val count = u16(bytes, 76)
        if (count == 0 || count > MAX_RECORDS) throw ParseException("Kindle book has no readable records.")
        if (78 + count * 8 > bytes.size) throw ParseException("Kindle book is damaged.")

        val offsets = IntArray(count) { u32(bytes, 78 + it * 8).toInt() }
        return PalmDb(bytes, offsets)
    }

    // ---- Headers ----------------------------------------------------------------------------

    private class Headers(
        val compression: Int,
        val numTextRecords: Int,
        val palmdocEncryption: Int,
        val encoding: Int,
        val version: Int,
        val resourceStart: Int,
        val huffcdic: Int,
        val numHuffcdic: Int,
        val trailingFlags: Int,
        val internalTitle: String?,
        val exth: Map<Int, String>
    )

    private fun readHeaders(record0: ByteArray): Headers {
        if (record0.size < 132 || string(record0, 16, 4) != "MOBI") {
            throw ParseException("Kindle book is missing its MOBI header.")
        }
        val mobiLength = u32(record0, 20).toInt()
        val encoding = u32(record0, 28).toInt()
        val titleOffset = u32(record0, 84).toInt()
        val titleLength = u32(record0, 88).toInt()
        val exthFlag = u32(record0, 128).toInt()

        val charset = if (encoding == 65001) Charsets.UTF_8 else charsetOrLatin1("windows-1252")
        val internalTitle = if (titleOffset > 0 && titleLength > 0 && titleOffset + titleLength <= record0.size) {
            String(record0, titleOffset, titleLength, charset).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        // The trailing-flags field only exists in longer MOBI headers; older books stop before it.
        val trailingFlags = if (record0.size >= 244) u32(record0, 240).toInt() else 0

        val exth = if (exthFlag and 0x40 != 0) {
            readExth(record0, mobiLength + 16, charset)
        } else {
            emptyMap()
        }

        return Headers(
            compression = u16(record0, 0),
            numTextRecords = u16(record0, 8),
            palmdocEncryption = u16(record0, 12),
            encoding = encoding,
            version = u32(record0, 36).toInt(),
            resourceStart = u32(record0, 108).toInt(),
            huffcdic = u32(record0, 112).toInt(),
            numHuffcdic = u32(record0, 116).toInt(),
            trailingFlags = trailingFlags,
            internalTitle = internalTitle,
            exth = exth
        )
    }

    private const val EXTH_CREATOR = 100
    private const val EXTH_BOUNDARY = 121
    private const val EXTH_COVER_OFFSET = 201
    private const val EXTH_THUMBNAIL_OFFSET = 202
    private const val EXTH_TITLE = 503

    /** Only the few records VoxLeaf shows are decoded; the rest of the ~40 types are skipped. */
    private fun readExth(record0: ByteArray, offset: Int, charset: java.nio.charset.Charset): Map<Int, String> {
        if (offset + 12 > record0.size || string(record0, offset, 4) != "EXTH") return emptyMap()
        val count = u32(record0, offset + 8).toInt()
        val result = mutableMapOf<Int, String>()
        var cursor = offset + 12
        repeat(count.coerceAtMost(1024)) {
            if (cursor + 8 > record0.size) return result
            val type = u32(record0, cursor).toInt()
            val length = u32(record0, cursor + 4).toInt()
            if (length < 8 || cursor + length > record0.size) return result
            val data = record0.copyOfRange(cursor + 8, cursor + length)
            val value = when (type) {
                // These three are numbers stored big-endian, not text.
                EXTH_BOUNDARY, EXTH_COVER_OFFSET, EXTH_THUMBNAIL_OFFSET ->
                    if (data.size >= 4) u32(data, 0).toString() else null
                else -> String(data, charset).trim().takeIf { it.isNotEmpty() }
            }
            // First occurrence wins: repeated types are additional authors or subjects.
            if (value != null) result.putIfAbsent(type, value)
            cursor += length
        }
        return result
    }

    private fun readCover(palm: PalmDb, headers: Headers, start: Int): ByteArray? {
        val offset = sequenceOf(EXTH_COVER_OFFSET, EXTH_THUMBNAIL_OFFSET)
            .mapNotNull { headers.exth[it]?.toLongOrNull() }
            .firstOrNull { it < 0xFFFFFFFFL }
            ?: return null
        val index = start + headers.resourceStart + offset.toInt()
        return try {
            palm.record(index).takeIf { it.size > 4 && looksLikeImage(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()
        return jpeg || png || gif
    }

    // ---- Decompression ----------------------------------------------------------------------

    private fun decompressorFor(headers: Headers, palm: PalmDb, start: Int): (ByteArray) -> ByteArray =
        when (headers.compression) {
            1 -> { raw -> raw }
            2 -> { raw -> decompressPalmDoc(raw) }
            17480 -> huffCdicDecompressor(headers, palm, start)
            else -> throw ParseException("This Kindle book uses an unsupported compression format.")
        }

    /**
     * PalmDOC LZ77. Byte values select between literals, a run of literals, a back-reference into
     * what has already been written, and the "space plus character" shorthand.
     */
    internal fun decompressPalmDoc(
        input: ByteArray,
        maxOutputBytes: Int = MAX_DECOMPRESSED_TEXT_BYTES
    ): ByteArray {
        val out = ByteBuf(maxSize = maxOutputBytes)
        var i = 0
        while (i < input.size) {
            val byte = input[i].toInt() and 0xFF
            when {
                byte == 0 -> out.add(0)
                byte <= 8 -> {
                    // The next `byte` bytes are literal.
                    var copied = 0
                    while (copied < byte && i + 1 + copied < input.size) {
                        out.add(input[i + 1 + copied])
                        copied++
                    }
                    i += byte
                }
                byte <= 0x7F -> out.add(byte.toByte())
                byte <= 0xBF -> {
                    if (i + 1 >= input.size) break
                    val pair = (byte shl 8) or (input[i + 1].toInt() and 0xFF)
                    i++
                    val distance = (pair and 0x3FFF) ushr 3
                    val length = (pair and 0x7) + 3
                    if (distance == 0 || distance > out.size) break
                    repeat(length) { out.add(out.at(out.size - distance)) }
                }
                // High bit set: a space followed by the character with that bit cleared.
                else -> {
                    out.add(' '.code.toByte())
                    out.add((byte xor 0x80).toByte())
                }
            }
            i++
        }
        return out.toByteArray()
    }

    /**
     * HUFF/CDIC: a Huffman table in one record plus dictionaries of byte sequences in the following
     * ones. Entries can themselves be compressed, so decoding recurses and caches the expansion.
     */
    private fun huffCdicDecompressor(headers: Headers, palm: PalmDb, start: Int): (ByteArray) -> ByteArray {
        val huff = palm.record(start + headers.huffcdic)
        if (huff.size < 16 || string(huff, 0, 4) != "HUFF") throw ParseException("Kindle book has a damaged HUFF record.")
        val offset1 = u32(huff, 8).toInt()
        val offset2 = u32(huff, 12).toInt()

        // Indexed by the top byte of the next 32 bits.
        val table1 = Array(256) { i ->
            val x = u32(huff, offset1 + i * 4)
            longArrayOf(x and 0x80, x and 0x1F, x ushr 8)
        }
        // Indexed by code length, 1..32.
        val table2 = Array(33) { i ->
            if (i == 0) longArrayOf(0, 0)
            else longArrayOf(u32(huff, offset2 + (i - 1) * 8), u32(huff, offset2 + (i - 1) * 8 + 4))
        }

        val dictValues = ArrayList<ByteArray>()
        val dictExpanded = ArrayList<Boolean>()
        val dictExpanding = ArrayList<Boolean>()
        var dictionaryBytes = 0L
        for (i in 1 until headers.numHuffcdic) {
            val record = palm.record(start + headers.huffcdic + i)
            if (record.size < 16 || string(record, 0, 4) != "CDIC") {
                throw ParseException("Kindle book has a damaged CDIC record.")
            }
            val cdicLength = u32(record, 4).toInt()
            val numEntries = u32(record, 8).toInt()
            val codeLength = u32(record, 12).toInt()
            if (codeLength !in 0..20 || numEntries < dictValues.size) {
                throw ParseException("Kindle book has invalid CDIC dictionary metadata.")
            }
            // `numEntries` counts the whole dictionary, so this record holds only part of it.
            val n = minOf(1 shl codeLength, numEntries - dictValues.size)
            val body = record.copyOfRange(cdicLength.coerceIn(0, record.size), record.size)
            for (j in 0 until n) {
                if (j * 2 + 2 > body.size) break
                val offset = u16(body, j * 2)
                if (offset + 2 > body.size) break
                val x = u16(body, offset)
                val length = x and 0x7FFF
                val end = (offset + 2 + length).coerceAtMost(body.size)
                dictValues += body.copyOfRange(offset + 2, end)
                dictExpanded += (x and 0x8000) != 0
                dictExpanding += false
                dictionaryBytes += (end - offset - 2)
                if (dictionaryBytes > MAX_DECOMPRESSED_TEXT_BYTES) {
                    throw ParseException("Kindle book expands beyond the supported text size.")
                }
            }
        }

        fun decompress(input: ByteArray): ByteArray {
            val out = ByteBuf(maxSize = MAX_DECOMPRESSED_TEXT_BYTES)
            val bitLength = input.size.toLong() * 8
            var i = 0L
            while (i < bitLength) {
                val bits = read32Bits(input, i)
                val entry = table1[(bits ushr 24).toInt() and 0xFF]
                var codeLength = entry[1].toInt()
                var value = entry[2]
                if (entry[0] == 0L) {
                    while (codeLength <= 32 && (bits ushr (32 - codeLength)) < table2[codeLength][0]) {
                        codeLength++
                    }
                    if (codeLength > 32) break
                    value = table2[codeLength][1]
                }
                i += codeLength
                if (i > bitLength) break

                val code = (value - (bits ushr (32 - codeLength))).toInt()
                if (code !in dictValues.indices) break
                if (!dictExpanded[code]) {
                    if (dictExpanding[code]) throw ParseException("Kindle book has a cyclic HUFF dictionary.")
                    dictExpanding[code] = true
                    val oldSize = dictValues[code].size
                    val expanded = try {
                        decompress(dictValues[code])
                    } finally {
                        dictExpanding[code] = false
                    }
                    dictionaryBytes += expanded.size - oldSize
                    if (dictionaryBytes > MAX_DECOMPRESSED_TEXT_BYTES) {
                        throw ParseException("Kindle book expands beyond the supported text size.")
                    }
                    dictValues[code] = expanded
                    dictExpanded[code] = true
                }
                out.append(dictValues[code])
            }
            return out.toByteArray()
        }
        return ::decompress
    }

    /** Reads the 32 bits starting at an arbitrary bit position, zero-padded past the end. */
    private fun read32Bits(bytes: ByteArray, from: Long): Long {
        val startByte = (from ushr 3).toInt()
        val end = from + 32
        val endByte = (end ushr 3).toInt()
        var bits = 0L
        for (i in startByte..endByte) {
            bits = (bits shl 8) or (bytes.getOrElse(i) { 0 }.toLong() and 0xFF)
        }
        return (bits ushr (8 - (end and 7).toInt())) and 0xFFFFFFFFL
    }

    /**
     * Text records can carry trailing metadata (link offsets, and a multibyte-overlap byte) that is
     * not part of the compressed stream. Feeding those to the decompressor produces garbage at the
     * end of every record, so they come off first.
     */
    internal fun removeTrailingEntries(record: ByteArray, trailingFlags: Int): ByteArray {
        var end = record.size
        val numEntries = Integer.bitCount(trailingFlags ushr 1)
        repeat(numEntries) {
            val length = varLenFromEnd(record, end)
            if (length <= 0 || length > end) return@repeat
            end -= length
        }
        if (trailingFlags and 1 != 0 && end > 0) {
            end -= (record[end - 1].toInt() and 0x3) + 1
        }
        return record.copyOfRange(0, end.coerceIn(0, record.size))
    }

    /** Variable-length integer written backwards from `end`; the high bit marks the first byte. */
    private fun varLenFromEnd(bytes: ByteArray, end: Int): Int {
        var value = 0
        val from = (end - 4).coerceAtLeast(0)
        for (i in from until end) {
            val byte = bytes[i].toInt() and 0xFF
            if (byte and 0x80 != 0) value = 0
            value = (value shl 7) or (byte and 0x7F)
        }
        return value
    }

    // ---- Section splitting ------------------------------------------------------------------

    private val PAGE_BREAK = Regex("""<\s*(?:mbp:)?pagebreak[^>]*>""", RegexOption.IGNORE_CASE)
    private val HTML_START = Regex("""<html[\s>]""", RegexOption.IGNORE_CASE)
    private val TITLE_TAG = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * Recovers chapters from the reassembled markup.
     *
     * KF8 stores one complete `<html>` document per section, so their starts are the chapter breaks —
     * this is what lets us skip porting the skeleton/fragment index. Older MOBI6 books are one long
     * document instead and mark breaks with `<mbp:pagebreak>`. A book that uses neither comes back as
     * a single chapter and falls through to the importer's heading detection.
     */
    internal fun splitSections(text: String): List<MobiChapter> {
        val htmlStarts = HTML_START.findAll(text).map { it.range.first }.toList()
        val boundaries = if (htmlStarts.size > 1) {
            htmlStarts
        } else {
            listOf(0) + PAGE_BREAK.findAll(text).map { it.range.first }.toList()
        }

        return boundaries.mapIndexedNotNull { index, from ->
            val to = boundaries.getOrNull(index + 1) ?: text.length
            if (to <= from) return@mapIndexedNotNull null
            val chunk = text.substring(from, to)
            val paragraphs = HtmlTextExtractor.extract(chunk)
            if (paragraphs.none { it.text.isNotBlank() }) return@mapIndexedNotNull null
            val headings = paragraphs.filter { it.isHeading }.map { it.text }
            MobiChapter(
                // Real books mark illustration captions as headings too, and those often sit above the
                // chapter title, so an explicit "Chapter II" label outranks merely-being-first. A
                // `<title>` is the last resort: it usually just repeats the book's name.
                title = headings.firstOrNull { ChapterDetector.isChapterLabel(it) }
                    ?: headings.firstOrNull()
                    ?: TITLE_TAG.find(chunk)?.groupValues?.get(1)?.let { HtmlTextExtractor.unescape(it).trim() }
                        ?.takeIf { it.isNotEmpty() },
                paragraphs = paragraphs
            )
        }
    }

    // ---- Byte helpers -----------------------------------------------------------------------

    /** Growable byte buffer that also allows reading back what was written, for LZ77 references. */
    private class ByteBuf(initial: Int = 8192, private val maxSize: Int = MAX_DECOMPRESSED_TEXT_BYTES) {
        private var buffer = ByteArray(minOf(initial, maxSize.coerceAtLeast(1)))
        var size = 0
            private set

        fun add(byte: Byte) {
            ensure(size + 1)
            buffer[size++] = byte
        }

        fun append(bytes: ByteArray) {
            if (bytes.size > maxSize - size) tooLarge()
            ensure(size + bytes.size)
            bytes.copyInto(buffer, size)
            size += bytes.size
        }

        fun at(index: Int): Byte = if (index in 0 until size) buffer[index] else 0

        fun toByteArray(): ByteArray = buffer.copyOfRange(0, size)

        private fun ensure(capacity: Int) {
            if (capacity < 0 || capacity > maxSize) tooLarge()
            if (capacity <= buffer.size) return
            var next = buffer.size * 2
            while (next < capacity) next = minOf(maxSize, next * 2)
            buffer = buffer.copyOf(next)
        }

        private fun tooLarge(): Nothing =
            throw ParseException("Kindle book expands beyond the supported text size.")
    }

    private fun charsetOrLatin1(name: String) = try {
        charset(name)
    } catch (_: Exception) {
        Charsets.ISO_8859_1
    }

    private fun string(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset < 0 || offset + length > bytes.size) return ""
        return String(bytes, offset, length, Charsets.US_ASCII)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun u32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
